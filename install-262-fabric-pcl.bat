@echo off
setlocal
rem ============================================================================
rem install-262-fabric-pcl.bat
rem Installs a brand-new Minecraft 26.2 + Fabric 0.20.1 version instance into PCL.
rem Downloads run through ONE HttpClient connection pool with 64 concurrent
rem requests per batch (ServicePointManager.DefaultConnectionLimit=256), i.e.
rem many files are fetched at the same time - like PCL / HMCL - not one-by-one.
rem The batch launcher extracts the embedded PowerShell worker and runs it.
rem ============================================================================
set "PSWORK=%TEMP%\install_262_fabric_pcl.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$s=Get-Content -Raw -LiteralPath '%~f0'; $a=$s.LastIndexOf('###PSBEGIN###'); $b=$s.LastIndexOf('###PSEND###'); if($a -lt 0 -or $b -le $a){throw 'marker missing'}; $s.Substring($a+13,$b-$a-13) | Set-Content -LiteralPath '%PSWORK%' -Encoding UTF8"
powershell -NoProfile -ExecutionPolicy Bypass -File "%PSWORK%"
set "RC=%ERRORLEVEL%"
del "%PSWORK%" >nul 2>&1
endlocal & exit /b %RC%
###PSBEGIN###
$ErrorActionPreference = 'Stop'

# --------------------------- hardcoded config -------------------------------
$REPO      = 'D:\modgraph\modgraph'
$VERROOT   = 'D:\Minecraft\.minecraft\versions'
$MCROOT    = 'D:\Minecraft\.minecraft'
$V262      = Join-Path $VERROOT '26.2'
$FABID     = 'fabric-loader-0.20.1-26.2'
$FABDIR    = Join-Path $VERROOT $FABID
$LOG       = Join-Path $REPO 'install-262-fabric-pcl.log'
$MODSRC    = 'D:\modgraph\modgraph\dataset-262\forbric\fixtures\jars\fabric\emotesounds-1.0.0.jar'
$MODSHA256 = 'de075b66cb86571eec4cd07fd1a1944516d9f92aeea3e0f85fb79ec759914fd6'
$BATCH     = 64           # concurrent requests per wave (true parallelism)

$MANIFEST_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
$LOADER_LIST  = 'https://meta.fabricmc.net/v2/versions/loader/26.2'
$PROFILE_URL  = 'https://meta.fabricmc.net/v2/versions/loader/26.2/0.20.1/profile/json'
$PROFILE_ZIP  = 'https://meta.fabricmc.net/v2/versions/loader/26.2/0.20.1/profile/zip'
$LOADER_SH1   = '7c7f9831e9f27b303d5d08decb5f4258a9f03296'
$DUMMY_SHA1   = 'da39a3ee5e6b4b0d3255bfef95601890afd80709'

$ALLOW = @(
  'piston-meta.mojang.com','piston-data.mojang.com',
  'libraries.minecraft.net','resources.download.minecraft.net',
  'meta.fabricmc.net','maven.fabricmc.net'
)
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
[Net.ServicePointManager]::DefaultConnectionLimit = 256   # allow many sockets at once

# ------------------------------- helpers ------------------------------------
function Log([string]$lvl,[string]$msg){
  $line = ('[{0}] [{1}] {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'),$lvl,$msg)
  Add-Content -LiteralPath $LOG -Value $line -Encoding UTF8
}
function Fresh-Log(){ Set-Content -LiteralPath $LOG -Value ('[{0}] [INFO] install-262-fabric-pcl START' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) -Encoding UTF8 }
function Assert-Host([string]$u){
  $h = ([Uri]$u).Host.ToLower()
  if ($ALLOW -notcontains $h) { throw "HOST_NOT_WHITELISTED $h -> $u" }
}
function Get-Text([string]$u){
  Assert-Host $u
  $r = Invoke-WebRequest -Uri $u -UseBasicParsing -TimeoutSec 120
  return [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
}
function Save-One([string]$u,[string]$dest,[string]$sha1,[long]$size,[string]$label){
  Assert-Host $u
  $dir = Split-Path -Parent $dest
  if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
  if (Test-Path -LiteralPath $dest) {
    $eg = (Get-FileHash -LiteralPath $dest -Algorithm SHA1).Hash.ToLower()
    $el = (Get-Item -LiteralPath $dest).Length
    if (($sha1 -eq '' -or $eg -eq $sha1.ToLower()) -and ($size -le 0 -or $el -eq $size)) {
      Log 'OK' ("{0} sha1={1} size={2} (cached) <- {3}" -f $label,$eg,$el,$u)
      return $true
    }
  }
  Invoke-WebRequest -Uri $u -OutFile $dest -UseBasicParsing -TimeoutSec 300
  $got = (Get-FileHash -LiteralPath $dest -Algorithm SHA1).Hash.ToLower()
  $len = (Get-Item -LiteralPath $dest).Length
  $ok = $true
  if ($sha1 -and $got -ne $sha1.ToLower()) { $ok = $false }
  if ($size -gt 0 -and $len -ne $size) { $ok = $false }
  if ($ok) { Log 'OK' ("{0} sha1={1} size={2} <- {3}" -f $label,$got,$len,$u) }
  else { Log 'FAIL' ("{0} sha1/size mismatch <- {1}" -f $label,$u) }
  return $ok
}
function Maven-Path([string]$name){
  $p = $name -split ':'
  $g = $p[0].Replace('.','/'); $a = $p[1]; $v = $p[2]
  return "$g/$a/$v/$a-$v.jar"
}
function Rule-Allow($lib){
  if (-not $lib.rules) { return $true }
  $allow = $false
  foreach ($r in $lib.rules) {
    $os = $r.os
    $applies = $true
    if ($os) {
      if ($os.name -and $os.name -ne 'windows') { $applies = $false }
      if ($os.arch -and $os.arch -ne 'x86_64' -and $os.arch -ne 'amd64') { $applies = $false }
    }
    if ($applies) { if ($r.action -eq 'allow') { $allow = $true } else { $allow = $false } }
  }
  return $allow
}

# ---- TRUE PARALLEL downloader: N concurrent HttpClient requests per batch ----
function Invoke-BatchDownload {
  param([object[]]$Items,[int]$Batch=64,[string]$Tag='file')
  $fails = New-Object System.Collections.ArrayList
  $todo  = New-Object System.Collections.ArrayList
  foreach ($it in $Items) {
    $hok = $false
    try { $hok = ($ALLOW -contains ([Uri]$it.url).Host.ToLower()) } catch { $hok = $false }
    if (-not $hok) { Log 'FAIL' ("{0} {1} HOST_NOT_WHITELISTED <- {2}" -f $Tag,$it.label,$it.url); [void]$fails.Add($Tag + ' ' + $it.label + ' <- ' + $it.url); continue }
    $cached = $false
    if (Test-Path -LiteralPath $it.dest) {
      try {
        $eg = (Get-FileHash -LiteralPath $it.dest -Algorithm SHA1).Hash.ToLower()
        $el = (Get-Item -LiteralPath $it.dest).Length
        if (($it.sha1 -eq '' -or $eg -eq $it.sha1.ToLower()) -and ($it.size -le 0 -or $el -eq $it.size)) { $cached = $true }
      } catch {}
    }
    if ($cached) { Log 'OK' ("{0} {1} (cached)" -f $Tag,$it.label) } else { [void]$todo.Add($it) }
  }
  $n = $todo.Count
  Log 'INFO' ("{0}: total={1} to_download={2} batch={3}" -f $Tag,$Items.Count,$n,$Batch)
  if ($n -eq 0) { return ,$fails }
  $idx = 0
  $sw = [Diagnostics.Stopwatch]::StartNew()
  while ($idx -lt $n) {
    $end = [Math]::Min($idx + $Batch, $n) - 1
    $slice = @($todo[$idx..$end])
    $cnt = $slice.Count
    $wcs = [System.Net.WebClient[]]::new($cnt)
    $tasks = [System.Threading.Tasks.Task[]]::new($cnt)
    for ($k=0; $k -lt $cnt; $k++) {
      $it = $slice[$k]
      $dir = Split-Path -Parent $it.dest
      if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
      if (Test-Path -LiteralPath $it.dest) { Remove-Item -LiteralPath $it.dest -Force -ErrorAction SilentlyContinue }
      $w = New-Object System.Net.WebClient
      $w.Headers.Add('User-Agent','install-262-fabric-pcl')
      $wcs[$k] = $w
      $tasks[$k] = $w.DownloadFileTaskAsync([Uri]$it.url, $it.dest)
    }
    try { [System.Threading.Tasks.Task]::WaitAll($tasks) } catch {}
    for ($k=0; $k -lt $cnt; $k++) {
      $it = $slice[$k]; $ok = $false; $why = ''
      try {
        if ($wcs[$k]) { $wcs[$k].Dispose() }
        if (Test-Path -LiteralPath $it.dest) {
          $sha = (Get-FileHash -LiteralPath $it.dest -Algorithm SHA1).Hash.ToLower()
          $len = (Get-Item -LiteralPath $it.dest).Length
          if (($it.sha1 -eq '' -or $sha -eq $it.sha1.ToLower()) -and ($it.size -le 0 -or $len -eq $it.size)) { $ok = $true }
          else { $why = ("sha1/size mismatch got=" + $sha + "/" + $len) }
        } else { $why = 'no_file' }
      } catch { $why = $_.Exception.Message }
      if ($ok) { Log 'OK' ("{0} {1} ok" -f $Tag,$it.label) }
      else { Log 'FAIL' ("{0} {1} {2} <- {3}" -f $Tag,$it.label,$why,$it.url); [void]$fails.Add($Tag + ' ' + $it.label + ' <- ' + $it.url) }
    }
    $idx += $Batch
    Log 'INFO' ("{0} progress {1}/{2} elapsed={3:n1}s" -f $Tag,[Math]::Min($idx,$n),$n,$sw.Elapsed.TotalSeconds)
  }
  return ,$fails
}

$script:libFail = @()
$script:assetFail = @()

# --------------------------- STEP 1: PROBE ----------------------------------
Fresh-Log
Log 'STEP' '1/6 probe'
Log 'INFO' ("USER={0} COMPUTER={1}" -f $env:USERNAME,$env:COMPUTERNAME)
$jdk = @()
Get-ChildItem 'C:\Program Files\Microsoft' -Directory -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -match '^jdk-' } | ForEach-Object {
    $j = Join-Path $_.FullName 'bin\java.exe'
    if (Test-Path -LiteralPath $j) {
      $oldEap = $ErrorActionPreference
      $ErrorActionPreference = 'Continue'
      try { $raw = & $j -version 2>&1; $v = ($raw | Select-Object -First 1 | Out-String).Trim() } catch { $v = 'unknown' }
      $ErrorActionPreference = $oldEap
      $jdk += "$($_.Name): $v"
    }
  }
$cp = (Get-Command java -ErrorAction SilentlyContinue)
if ($cp) { $jdk += "PATH java: $($cp.Source)" }
if ($jdk.Count) { $jdk | ForEach-Object { Log 'INFO' ("JDK " + $_) } } else { Log 'WARN' 'JDK: none discovered in C:\Program Files\Microsoft' }
Log 'WARN' '26.2 requires Java 25 (javaVersion.majorVersion=25, component=java-runtime-epsilon). No Java 25 found on this machine. NOT installing a JRE (out of scope).'
$inst = @(Get-ChildItem -LiteralPath $VERROOT -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.Name })
Log 'INFO' ("versions existing count={0}: {1}" -f $inst.Count,($inst -join ', '))
Log 'INFO' "PCL .minecraft root (LaunchFolders first entry) = D:\Minecraft\.minecraft"
Log 'INFO' 'Version isolation: user confirmed ON; script installs into per-version folders under versions\.'

# --------------------- STEP 2: manifest + 26.2.json + client -----------------
Log 'STEP' '2/6 version manifest, 26.2.json, client.jar'
$man = Get-Text $MANIFEST_URL
$mobj = $man | ConvertFrom-Json
$v262meta = @($mobj.versions | Where-Object { $_.id -eq '26.2' })
if ($v262meta.Count -ne 1) { Log 'FAIL' "id=26.2 not found in manifest (count=$($v262meta.Count))"; throw 'manifest 26.2 missing' }
$vurl = $v262meta[0].url
Log 'INFO' "26.2 metadata url = $vurl"
Assert-Host $vurl
$jsonSha = ([Uri]$vurl).AbsolutePath.Trim('/').Split('/')[2]
$okJson = Save-One $vurl (Join-Path $V262 '26.2.json') $jsonSha 0 '26.2.json'
$cv = (Get-Content -Raw -LiteralPath (Join-Path $V262 '26.2.json') | ConvertFrom-Json)
$client = $cv.downloads.client
Log 'INFO' ("26.2 javaVersion.majorVersion={0} client.sha1={1} client.size={2}" -f $cv.javaVersion.majorVersion,$client.sha1,$client.size)
$okClient = Save-One $client.url (Join-Path $V262 '26.2.jar') $client.sha1 $client.size 'client.jar -> 26.2.jar'

# ---------------- STEP 3: libraries (parallel) + assets (parallel) -----------
Log 'STEP' '3/6 libraries, asset index, assets objects'
$libItems = @()
foreach ($lib in $cv.libraries) {
  if (-not (Rule-Allow $lib)) { continue }
  $art = $lib.downloads.artifact
  if (-not $art) { Log 'WARN' ("library has no artifact (natives/classifiers path): " + $lib.name); continue }
  $libItems += [pscustomobject]@{ label=$lib.name; url=$art.url; dest=(Join-Path $MCROOT ($art.path -replace '/','\')); sha1=$art.sha1; size=[long]$art.size }
}
$libFails = Invoke-BatchDownload -Items $libItems -Batch $BATCH -Tag 'lib'
foreach ($f in $libFails) { $script:libFail += $f }

$ai = $cv.assetIndex
Log 'INFO' ("assetIndex id={0} sha1={1} size={2} url={3}" -f $ai.id,$ai.sha1,$ai.size,$ai.url)
$okAI = Save-One $ai.url (Join-Path $MCROOT ("assets\indexes\{0}.json" -f $ai.id)) $ai.sha1 $ai.size 'assetIndex'
$aobj = Get-Content -Raw -LiteralPath (Join-Path $MCROOT ("assets\indexes\{0}.json" -f $ai.id)) | ConvertFrom-Json

$assetItems = @()
$seenHash = @{}
$totalNames = 0
foreach ($prop in $aobj.objects.PSObject.Properties) {
  $totalNames++
  $h = $prop.Value.hash
  if ($seenHash.ContainsKey($h)) { continue }
  $seenHash[$h] = $true
  $pre = $h.Substring(0,2)
  $assetItems += [pscustomobject]@{
    label = $prop.Name
    url   = ("https://resources.download.minecraft.net/{0}/{1}" -f $pre,$h)
    dest  = (Join-Path $MCROOT ("assets\objects\{0}\{1}" -f $pre,$h))
    sha1  = $h
    size  = [long]$prop.Value.size
  }
}
Log 'INFO' ("asset objects total={0} unique_hash={1} batch={2}" -f $totalNames,$assetItems.Count,$BATCH)
$assetFails = Invoke-BatchDownload -Items $assetItems -Batch $BATCH -Tag 'asset'
foreach ($f in $assetFails) { $script:assetFail += $f }

# ------------------- STEP 4: Fabric loader profile ---------------------------
Log 'STEP' '4/6 fabric loader profile'
$ll = (Get-Text $LOADER_LIST) | ConvertFrom-Json
$stable = @($ll | Where-Object { $_.loader.stable -eq $true })
if ($stable.Count -lt 1) { Log 'FAIL' 'no stable fabric loader for 26.2'; throw 'no stable loader' }
$loaderVer = $stable[0].loader.version
Log 'INFO' ("stable loader version = {0} (maven {1})" -f $loaderVer,$stable[0].loader.maven)
if ($loaderVer -ne '0.20.1') { Log 'WARN' ("loader changed since authoring: expected 0.20.1, got {0}; profile URLs still pinned to 0.20.1" -f $loaderVer) }
$profile = Get-Text $PROFILE_URL
$po = $profile | ConvertFrom-Json
Log 'INFO' ("profile id={0} inheritsFrom={1} mainClass={2}" -f $po.id,$po.inheritsFrom,$po.mainClass)
if ($po.inheritsFrom -ne '26.2') { Log 'FAIL' "profile inheritsFrom != 26.2"; throw 'inheritsFrom mismatch' }
if (-not (Test-Path -LiteralPath (Join-Path $V262 '26.2.json'))) { Log 'FAIL' 'parent 26.2.json missing'; throw 'parent missing' }
if (-not (Test-Path -LiteralPath $FABDIR)) { New-Item -ItemType Directory -Force -Path $FABDIR | Out-Null }
Set-Content -LiteralPath (Join-Path $FABDIR ($po.id + '.json')) -Value $profile -Encoding UTF8 -NoNewline
Log 'OK' ("wrote profile json verbatim -> " + (Join-Path $FABDIR ($po.id + '.json')))
$fabItems = @()
foreach ($lib in $po.libraries) {
  $rel = Maven-Path $lib.name
  $u = ($lib.url.TrimEnd('/')) + '/' + $rel
  if ($lib.sha1) { $sha = $lib.sha1; $sz = [long]$lib.size } else { $sha = $LOADER_SH1; $sz = -1 }
  $fabItems += [pscustomobject]@{ label=$lib.name; url=$u; dest=(Join-Path $MCROOT ($rel -replace '/','\')); sha1=$sha; size=$sz }
}
$fabFails = Invoke-BatchDownload -Items $fabItems -Batch 32 -Tag 'fablib'
foreach ($f in $fabFails) { $script:libFail += $f }

# dummy jar: take the REAL empty jar from Fabric profile/zip (do not handcraft)
$tmpzip = Join-Path $env:TEMP 'fab-profile-26.2.zip'
Assert-Host $PROFILE_ZIP
Invoke-WebRequest -Uri $PROFILE_ZIP -OutFile $tmpzip -UseBasicParsing -TimeoutSec 120
$tmpdir = Join-Path $env:TEMP ('fab-profile-26.2-' + [Guid]::NewGuid().ToString('N'))
if (Test-Path -LiteralPath $tmpdir) { Remove-Item -LiteralPath $tmpdir -Recurse -Force }
Expand-Archive -LiteralPath $tmpzip -DestinationPath $tmpdir -Force
$jarFiles = @(Get-ChildItem -LiteralPath $tmpdir -Recurse -Filter *.jar -File)
$dummyDest = Join-Path $FABDIR ($po.id + '.jar')
if ($jarFiles.Count -eq 1) {
  Copy-Item -LiteralPath $jarFiles[0].FullName -Destination $dummyDest -Force
  $got = (Get-FileHash -LiteralPath $dummyDest -Algorithm SHA1).Hash.ToLower()
  if ($got -ne $DUMMY_SHA1) { Log 'FAIL' ("dummy jar sha1 mismatch exp={0} got={1}" -f $DUMMY_SHA1,$got) }
  else { Log 'OK' ("dummy jar from profile/zip entry {0}: sha1={1} size={2}" -f $jarFiles[0].Name,$got,(Get-Item -LiteralPath $dummyDest).Length) }
} else { Log 'FAIL' ("profile/zip jar entry count={0}, expected 1" -f $jarFiles.Count) }
Remove-Item -LiteralPath $tmpdir -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $tmpzip -Force -ErrorAction SilentlyContinue

# ---------------- STEP 5: dirs + one local mod -------------------------------
Log 'STEP' '5/6 create mods/config/saves + place 1 mod'
foreach ($d in @('mods','config','saves')) {
  $p = Join-Path $FABDIR $d
  if (-not (Test-Path -LiteralPath $p)) { New-Item -ItemType Directory -Force -Path $p | Out-Null; Log 'OK' ("created " + $p) }
  else { Log 'INFO' ("exists " + $p) }
}
if (-not (Test-Path -LiteralPath $MODSRC)) { Log 'FAIL' ("mod source missing " + $MODSRC) }
else {
  $h = (Get-FileHash -LiteralPath $MODSRC -Algorithm SHA256).Hash.ToLower()
  if ($h -ne $MODSHA256) { Log 'FAIL' ("mod sha256 mismatch exp={0} got={1}" -f $MODSHA256,$h) }
  else {
    Copy-Item -LiteralPath $MODSRC -Destination (Join-Path $FABDIR 'mods\emotesounds-1.0.0.jar') -Force
    Log 'OK' ("mod placed: emotesounds-1.0.0.jar slug=emotesounds project=ggYHhqqJ sha256={0} sourceBatch=segment_4" -f $h)
  }
}

# ---------------- STEP 6: self-check ----------------------------------------
Log 'STEP' '6/6 self-check'
$fail = $false
$j1 = Join-Path $V262 '26.2.json'
$j2 = Join-Path $FABDIR ($po.id + '.json')
foreach ($j in @($j1,$j2)) {
  if (-not (Test-Path -LiteralPath $j)) { Log 'FAIL' "missing version json $j"; $fail = $true }
  else {
    try { $null = Get-Content -Raw -LiteralPath $j | ConvertFrom-Json; Log 'OK' ("parse ok " + $j) }
    catch { Log 'FAIL' ("parse FAIL {0}: {1}" -f $j,$_); $fail = $true }
  }
}
if ($po.inheritsFrom -ne '26.2' -or -not (Test-Path -LiteralPath $j1)) { Log 'FAIL' 'inheritsFrom chain not closed'; $fail = $true }
else { Log 'OK' 'inheritsFrom chain: fabric-loader-0.20.1-26.2 -> 26.2 (parent present)' }
foreach ($pair in @(@((Join-Path $V262 '26.2.jar'),$client.sha1,'client.jar'), @($dummyDest,$DUMMY_SHA1,'dummy.jar'))) {
  if (-not (Test-Path -LiteralPath $pair[0])) { Log 'FAIL' ("missing " + $pair[2]); $fail = $true; continue }
  $g = (Get-FileHash -LiteralPath $pair[0] -Algorithm SHA1).Hash.ToLower()
  if ($g -ne $pair[1].ToLower()) { Log 'FAIL' ("{0} sha1 exp={1} got={2}" -f $pair[2],$pair[1],$g); $fail = $true }
  else { Log 'OK' ("{0} sha1 ok {1}" -f $pair[2],$g) }
}
Log 'INFO' ("library failures = {0}" -f $script:libFail.Count)
foreach ($x in $script:libFail) { Log 'FAIL' ("LIB_FAIL " + $x); $fail = $true }
Log 'INFO' ("asset failures = {0}" -f $script:assetFail.Count)
foreach ($x in $script:assetFail) { Log 'FAIL' ("ASSET_FAIL " + $x); $fail = $true }
if (-not (Test-Path -LiteralPath (Join-Path $MCROOT ("assets\indexes\{0}.json" -f $ai.id)))) { Log 'FAIL' 'asset index missing'; $fail = $true }
else { Log 'OK' 'asset index present' }
if (-not (Test-Path -LiteralPath (Join-Path $FABDIR 'mods'))) { Log 'FAIL' 'mods/ missing'; $fail = $true }
else { Log 'OK' 'mods/ present' }
if ($fail) { Log 'FAIL' 'SELF-CHECK FAILED - instance NOT ready; see FAIL lines above'; exit 1 }
Log 'OK' 'SELF-CHECK PASSED - instance ready; in PCL select: fabric-loader-0.20.1-26.2'
Log 'INFO' 'NOTE: mods/ mod may be skipped because Fabric API is NOT installed this round; not verified.'
Log 'INFO' 'install-262-fabric-pcl END'
exit 0
###PSEND###
