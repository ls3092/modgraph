@echo off
setlocal
rem ============================================================================
rem install-m2-entry.bat
rem Freeze of the M2 first-cut steps into ONE idempotent, rollback-able flow:
rem   gradle jar -> copy jar into .minecraft/libraries -> backup <id>.json
rem   -> append library entry -> set mainClass -> file-level self-check
rem Double-clicking twice: library is NOT appended twice, .bak-pre-m2 is NOT overwritten.
rem Rollback:  install-m2-entry.bat rollback
rem            (copies .bak-pre-m2 back over <id>.json -> official Fabric)
rem This script NEVER starts Minecraft, PCL, or any game JVM.
rem ============================================================================
set "PSWORK=%TEMP%\install_m2_entry.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$s=Get-Content -Raw -LiteralPath '%~f0'; $a=$s.LastIndexOf('###PSBEGIN###'); $b=$s.LastIndexOf('###PSEND###'); if($a -lt 0 -or $b -le $a){throw 'marker missing'}; $s.Substring($a+13,$b-$a-13) | Set-Content -LiteralPath '%PSWORK%' -Encoding UTF8"
powershell -NoProfile -ExecutionPolicy Bypass -File "%PSWORK%" %*
set "RC=%ERRORLEVEL%"
del "%PSWORK%" >nul 2>&1
endlocal & exit /b %RC%
###PSBEGIN###
param([switch]$Rollback)
$ErrorActionPreference = 'Stop'

# ------------------------------ config --------------------------------------
$REPO       = 'D:\modgraph\modgraph'
$MCROOT     = 'D:\Minecraft\.minecraft'
$VERROOT    = Join-Path $MCROOT 'versions'
$FABID      = 'fabric-loader-0.20.1-26.2'
$FABDIR     = Join-Path $VERROOT $FABID
$JSON       = Join-Path $FABDIR ($FABID + '.json')
$BAK        = $JSON + '.bak-pre-m2'
$LIBDIR     = Join-Path $MCROOT 'libraries\dev\modgraph\modgraph\0.1.0-m2'
$DESTJAR    = Join-Path $LIBDIR 'modgraph-0.1.0-m2.jar'
$BUILDJAR   = Join-Path $REPO 'build\libs\modgraph.jar'
$GRADLELOG  = Join-Path $REPO 'install-m2-entry-gradle.log'
$LOG        = Join-Path $REPO 'install-m2-entry.log'
$PARENTJSON = Join-Path $VERROOT '26.2\26.2.json'

$MAIN_OLD   = 'net.fabricmc.loader.impl.launch.knot.KnotClient'
$MAIN_NEW   = 'dev.modgraph.m2.M2Entry'
$LIB_NAME   = 'dev.modgraph:modgraph:0.1.0-m2'
$LIB_URL    = 'https://maven.fabricmc.net/'
# library 七字段：字段集合与顺序沿用上一轮 A/C 模板原文
# name / url / md5 / sha1 / sha256 / sha512 / size；真实值为上一轮编译产物，不造新字段。
$LIB_JSON   = '{"name":"dev.modgraph:modgraph:0.1.0-m2","url":"https://maven.fabricmc.net/","md5":"0ef5cef7b8af38ab89c3d0656906c7fb","sha1":"e57fa4abf5b5405b1ef624fca0130f2c3ba674dc","sha256":"bb29dbf439ebb4c006d5a5d8b61ae3c543c62c71b621786ada928396a419c54c","sha512":"aadacf54106f381186a8bd5bb0758943d67f825b46d5c3265010e42b0fee1c95c64f328f1afae8fdb003e0b4d7fcf1abe9298594adc95ef9f9814d44f6012a8c","size":127153}'
$JAR_SHA256 = 'bb29dbf439ebb4c006d5a5d8b61ae3c543c62c71b621786ada928396a419c54c'
$JAR_SIZE   = 127153
$BAK_SHA256 = 'ad6913df9e38bd979181ad2b0f3ecdcf83c1dcafa2e2ba00762539992878808a'
$LIT_OLD    = '"mainClass":"' + $MAIN_OLD + '"'
$LIT_NEW    = '"mainClass":"' + $MAIN_NEW + '"'

# ------------------------------ helpers -------------------------------------
function Log([string]$lvl,[string]$msg){
  $line = ('[{0}] [{1}] {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'),$lvl,$msg)
  Add-Content -LiteralPath $LOG -Value $line -Encoding UTF8
}
function Fail([string]$msg){ Log 'FAIL' $msg; throw $msg }
function Sha256([string]$path){ return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Get-JsonText([string]$path){
  $b = [System.IO.File]::ReadAllBytes($path)
  $hasBom = ($b.Length -ge 3 -and $b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF)
  $s = [System.Text.Encoding]::UTF8.GetString($b)
  if ($hasBom) { $s = $s.TrimStart([char]0xFEFF) }
  return @{ text = $s; bom = $hasBom }
}
function Set-JsonText([string]$path,[string]$text,[bool]$bom){
  $enc = New-Object System.Text.UTF8Encoding($bom)
  [System.IO.File]::WriteAllText($path, $text, $enc)
}

# ------------------------------ rollback ------------------------------------
if ($Rollback) {
  Log 'RUN' 'install-m2-entry ROLLBACK: restore official Fabric from .bak-pre-m2'
  if (-not (Test-Path -LiteralPath $BAK)) { Fail "backup missing: $BAK" }
  $bh = Sha256 $BAK
  if ($bh -ne $BAK_SHA256) { Fail "backup sha256 mismatch exp=$BAK_SHA256 got=$bh (refuse to restore tampered backup)" }
  Copy-Item -LiteralPath $BAK -Destination $JSON -Force
  $r = Get-JsonText $JSON
  $o = $r.text | ConvertFrom-Json
  if ($o.mainClass -ne $MAIN_OLD) { Fail "after rollback mainClass != $MAIN_OLD" }
  $cnt = @($o.libraries).Count
  if ($cnt -ne 7) { Fail "after rollback libraries count=$cnt expected 7" }
  foreach ($l in $o.libraries) { if ($l.name -eq $LIB_NAME) { Fail 'after rollback modgraph library still present' } }
  if ($o.id -ne $FABID -or $o.inheritsFrom -ne '26.2') { Fail 'after rollback id/inheritsFrom mismatch' }
  Log 'OK' ("ROLLBACK DONE: {0} restored; mainClass={1}; libraries={2}" -f $JSON,$MAIN_OLD,$cnt)
  Log 'INFO' 'libraries\dev\modgraph\... jar left on disk (not on classpath once json restored); may be deleted manually.'
  Log 'INFO' 'install-m2-entry END (rollback)'
  exit 0
}

# ------------------------------ install -------------------------------------
Log 'RUN' 'install-m2-entry START (idempotent; no game / no PCL / no game JVM)'

# STEP 1: gradle jar (NOT gradle test)
Log 'STEP' '1/6 gradle jar --offline --no-daemon'
if (-not (Test-Path -LiteralPath $REPO)) { Fail "repo missing: $REPO" }
Push-Location $REPO
$oldEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
  & gradle jar --offline --no-daemon 2>&1 | Out-File -LiteralPath $GRADLELOG -Encoding utf8
  $rc = $LASTEXITCODE
} finally { $ErrorActionPreference = $oldEap; Pop-Location }
if ($rc -ne 0) { Fail "gradle jar failed rc=$rc; see $GRADLELOG" }
if (-not (Test-Path -LiteralPath $BUILDJAR)) { Fail "build jar missing: $BUILDJAR" }
$h = Sha256 $BUILDJAR
if ($h -ne $JAR_SHA256) {
  Fail "jar sha256 changed exp=$JAR_SHA256 got=$h ; library template is pinned to last round - update constants deliberately, do not blind-write"
}
Log 'OK' ("gradle jar ok: {0} size={1} sha256={2}" -f $BUILDJAR,(Get-Item -LiteralPath $BUILDJAR).Length,$h)

# STEP 2: copy jar into .minecraft/libraries
Log 'STEP' '2/6 copy jar into .minecraft/libraries/dev/modgraph/modgraph/0.1.0-m2'
if (-not (Test-Path -LiteralPath $LIBDIR)) { New-Item -ItemType Directory -Force -Path $LIBDIR | Out-Null; Log 'OK' "created $LIBDIR" }
Copy-Item -LiteralPath $BUILDJAR -Destination $DESTJAR -Force
$dh = Sha256 $DESTJAR
if ($dh -ne $JAR_SHA256) { Fail "dest jar sha256 mismatch exp=$JAR_SHA256 got=$dh" }
Log 'OK' ("jar in libraries: {0} sha256={1}" -f $DESTJAR,$dh)

# STEP 3: backup <id>.json (never overwrite an existing .bak-pre-m2)
Log 'STEP' '3/6 backup <id>.json -> .bak-pre-m2 (keep golden copy)'
if (-not (Test-Path -LiteralPath $JSON)) { Fail "json missing: $JSON" }
if (Test-Path -LiteralPath $BAK) {
  $bh = Sha256 $BAK
  if ($bh -ne $BAK_SHA256) { Fail "existing .bak-pre-m2 sha256 mismatch exp=$BAK_SHA256 got=$bh (do NOT overwrite)" }
  Log 'INFO' (".bak-pre-m2 already exists, kept untouched: {0}" -f $BAK)
} else {
  $probe = Get-JsonText $JSON
  $po = $probe.text | ConvertFrom-Json
  if ($po.mainClass -ne $MAIN_OLD) { Fail "cannot create golden backup: json already modified (mainClass=$($po.mainClass))" }
  foreach ($l in $po.libraries) { if ($l.name -eq $LIB_NAME) { Fail 'cannot create golden backup: modgraph library already present' } }
  Copy-Item -LiteralPath $JSON -Destination $BAK -Force
  $bh = Sha256 $BAK
  if ($bh -ne $BAK_SHA256) { Fail "backup sha256 mismatch exp=$BAK_SHA256 got=$bh" }
  Log 'OK' ("backup created: {0} sha256={1}" -f $BAK,$bh)
}

# STEP 4: append library entry (idempotent)
Log 'STEP' '4/6 append library entry (idempotent, 7 fields verbatim)'
$r = Get-JsonText $JSON
$txt = $r.text
$o = $txt | ConvertFrom-Json
if (-not $txt.TrimEnd().EndsWith(']}')) { Fail 'json layout unexpected: libraries is not the last array' }
$hasLib = $false
foreach ($l in $o.libraries) { if ($l.name -eq $LIB_NAME) { $hasLib = $true } }
if ($hasLib) {
  Log 'INFO' ("library already present ({0}), skip append" -f $LIB_NAME)
} else {
  $close = $txt.LastIndexOf(']')
  if ($close -lt 0) { Fail 'cannot locate libraries array close bracket' }
  $newTxt = $txt.Substring(0,$close) + ',' + $LIB_JSON + $txt.Substring($close)
  $null = $newTxt | ConvertFrom-Json
  Set-JsonText $JSON $newTxt $r.bom
  Log 'OK' ("library appended: {0}" -f $LIB_NAME)
}

# STEP 5: set mainClass (idempotent)
Log 'STEP' '5/6 set mainClass (idempotent)'
$r = Get-JsonText $JSON
$txt = $r.text
$o = $txt | ConvertFrom-Json
if ($o.mainClass -eq $MAIN_NEW) {
  Log 'INFO' ("mainClass already {0}, skip" -f $MAIN_NEW)
} elseif ($o.mainClass -eq $MAIN_OLD) {
  if (-not $txt.Contains($LIT_OLD)) { Fail 'literal mainClass not found; refuse blind replace' }
  $newTxt = $txt.Replace($LIT_OLD,$LIT_NEW)
  $null = $newTxt | ConvertFrom-Json
  Set-JsonText $JSON $newTxt $r.bom
  Log 'OK' ("mainClass {0} -> {1}" -f $MAIN_OLD,$MAIN_NEW)
} else {
  Fail "unknown mainClass $($o.mainClass); refuse to clobber"
}

# STEP 6: file-level self-check (NOT a game test)
Log 'STEP' '6/6 self-check (file-level only; NOT a game launch)'
$script:failCount = 0
function Check([bool]$ok,[string]$name,[string]$detail){
  if ($ok) { Log 'PASS' ("{0} | {1}" -f $name,$detail) }
  else { Log 'FAIL' ("{0} | {1}" -f $name,$detail); $script:failCount++ }
}
$r = Get-JsonText $JSON
$o = $null
try { $o = $r.text | ConvertFrom-Json } catch { Log 'FAIL' 'json_parseable | unparseable'; Log 'OVERALL=FAIL'; exit 1 }
$libs = @($o.libraries)
$entry = @($libs | Where-Object { $_.name -eq $LIB_NAME })
Check $true 'json_parseable' ("id={0} mainClass={1} libs={2}" -f $o.id,$o.mainClass,$libs.Count)
$bh = if (Test-Path -LiteralPath $BAK) { Sha256 $BAK } else { 'MISSING' }
Check ($bh -eq $BAK_SHA256) 'backup_exists' ("bak_sha256={0}" -f $bh)
$jarExists = Test-Path -LiteralPath $DESTJAR
Check ($jarExists -and $entry.Count -eq 1) 'jar_in_libraries' ("jar_exists={0} lib_entry_count={1}" -f $jarExists,$entry.Count)
$diskHash = if ($jarExists) { Sha256 $DESTJAR } else { 'MISSING' }
$diskSize = if ($jarExists) { (Get-Item -LiteralPath $DESTJAR).Length } else { -1 }
$fieldsMatch = $false
if ($entry.Count -eq 1) {
  $e = $entry[0]
  $fieldsMatch = ($e.name -eq $LIB_NAME -and $e.url -eq $LIB_URL -and
    $e.md5 -eq '0ef5cef7b8af38ab89c3d0656906c7fb' -and
    $e.sha1 -eq 'e57fa4abf5b5405b1ef624fca0130f2c3ba674dc' -and
    $e.sha256 -eq $JAR_SHA256 -and
    $e.sha512 -eq 'aadacf54106f381186a8bd5bb0758943d67f825b46d5c3265010e42b0fee1c95c64f328f1afae8fdb003e0b4d7fcf1abe9298594adc95ef9f9814d44f6012a8c' -and
    [long]$e.size -eq $JAR_SIZE)
}
Check ($fieldsMatch -and $diskHash -eq $JAR_SHA256 -and $diskSize -eq $JAR_SIZE) 'jar_sha256_matches_json' ("json={0} disk={1} size={2} fields7={3}" -f $(if($entry.Count -eq 1){$entry[0].sha256}else{'MISSING'}),$diskHash,$diskSize,$fieldsMatch)
Check ($o.inheritsFrom -eq '26.2' -and $o.id -eq $FABID) 'inheritsFrom_26.2' ("value={0} id={1}" -f $o.inheritsFrom,$o.id)
$classOk = $false
if ($jarExists) {
  try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($DESTJAR)
    try { $classOk = ($null -ne $zip.GetEntry('dev/modgraph/m2/M2Entry.class')) } finally { $zip.Dispose() }
  } catch { $classOk = $false }
}
Check $classOk 'own_class_locatable' ("class_in_jar={0} mainClass={1}" -f $classOk,$MAIN_NEW)
Check (Test-Path -LiteralPath $PARENTJSON) 'parent_json_exists' $PARENTJSON

if ($script:failCount -gt 0) { Log 'FAIL' ("OVERALL=FAIL ({0}/7 failed)" -f $script:failCount); exit 1 }
Log 'OK' 'OVERALL=PASS (7/7)'
Log 'INFO' 'This is a FILE-LEVEL self-check, NOT a game launch. Game test is explicitly deferred by the user.'
Log 'INFO' 'install-m2-entry END'
exit 0
###PSEND###
