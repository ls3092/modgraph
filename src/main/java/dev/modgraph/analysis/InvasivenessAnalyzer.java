package dev.modgraph.analysis;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import dev.modgraph.model.FailureCode;
import dev.modgraph.model.Invasiveness;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.ModType;
import dev.modgraph.model.Status;
import dev.modgraph.model.SupportPolicy;
import dev.modgraph.scan.ScannedJar;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 描述符级侵入深度评级与 mixin 目标索引。
 *
 * <p>只读取 zip entry 名、元数据文本和声明的 mixin 配置 JSON；不读取 class 文件内容，
 * 不读取 {@code @Mixin} 注解，不引入 ASM。</p>
 */
public final class InvasivenessAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(InvasivenessAnalyzer.class);

    private static final String UTF8_BOM = "\uFEFF";
    private static final Pattern MIXIN_ENTRY_PATTERN = Pattern.compile("(^|/)mixins\\.[^/]*\\.json$");
    private static final Pattern TRANSFORMER_PATTERN = Pattern.compile("(?m)^\\s*transformer\\s*=");
    private static final List<String> TARGET_SUFFIXES =
            List.of("MixinPlugin", "Mixin", "Accessor", "Invoker");

    private static final Set<String> KNOWN_RENDER_IDS = normalized(
            "sodium", "iris", "oculus", "optifine", "rubidium", "embeddium",
            "sodiumextra", "reeses-sodium-options", "dynamiclights", "lambdynamiclights");
    private static final Set<String> KNOWN_LIBRARIES = normalized(
            "fabric-api", "architectury", "architectury-api", "cloth-config", "geckolib",
            "owo-lib", "konkrete", "kotlinforforge", "forge", "neoforge", "yacl");
    private static final Set<String> SHADER_LOADERS = normalized("iris", "oculus", "optifine");
    private static final Set<String> OPTIMIZERS = normalized(
            "sodium", "rubidium", "embeddium", "lithium", "ferritecore", "krypton",
            "modernfix", "entityculling");
    private static final Set<String> HACK_CLIENTS = normalized(
            "meteor-client", "wurst", "impact", "future", "liquidbounce", "athena",
            "rusherhack", "bleachhack");

    /**
     * 对一个已解析成功的候选做描述符级分析；失败候选原样返回。
     */
    public ModCandidate analyze(final ScannedJar scannedJar, final ModCandidate candidate) {
        if (candidate.status() != Status.OK) {
            return candidate;
        }
        try (ZipFile zip = new ZipFile(scannedJar.jarPath().toFile())) {
            final MixinData mixinData =
                    readMixinData(zip, scannedJar.entryNames(), candidate);
            final List<String> signals = new ArrayList<>();
            final Invasiveness invasiveness =
                    detectInvasiveness(zip, scannedJar.entryNames(), candidate, mixinData, signals);
            final ModType modType = classifyModType(candidate);
            final SupportPolicy supportPolicy = supportPolicy(candidate, invasiveness, modType);

            if (invasiveness == Invasiveness.RENDER || invasiveness == Invasiveness.CORE) {
                LOG.warn("侵入深度 {} 于 {}，信号：{}",
                        invasiveness, candidate.sourceFile(), signals);
            }
            if (supportPolicy == SupportPolicy.EXCLUDED) {
                LOG.warn("排除模组 {}（{}），不做兼容性承诺",
                        candidate.sourceFile(), candidate.modId());
            }

            return copyWithAnalysis(
                    candidate, mixinData.targets(), modType, invasiveness, signals, supportPolicy);
        } catch (MixinDataException e) {
            return failed(candidate, e.code, e.getMessage());
        } catch (IOException e) {
            return failed(candidate, FailureCode.IO_ERROR,
                    "读取注入描述符失败：" + e.getMessage());
        } catch (RuntimeException e) {
            return failed(candidate, FailureCode.INTERNAL_ERROR,
                    internalMessage(e));
        }
    }

    private static Invasiveness detectInvasiveness(
            final ZipFile zip,
            final List<String> entryNames,
            final ModCandidate candidate,
            final MixinData mixinData,
            final List<String> signals) throws IOException {
        final String normalizedId = normalizeId(candidate.modId());
        if (KNOWN_RENDER_IDS.contains(normalizedId)) {
            signals.add("knownRenderId:" + candidate.modId());
            return Invasiveness.RENDER;
        }

        final String fabricText = readText(zip, "fabric.mod.json");
        if (fabricText != null && fabricText.contains("net.minecraft.client.render")) {
            signals.add("entrypoint:net.minecraft.client.render");
            return Invasiveness.RENDER;
        }
        if (!mixinData.renderConfigs().isEmpty()) {
            for (String config : mixinData.renderConfigs()) {
                signals.add("mixinConfigRender:" + config);
            }
            return Invasiveness.RENDER;
        }

        boolean core = false;
        if (entryNames.contains("META-INF/coremods.json")) {
            signals.add("coremods:META-INF/coremods.json");
            core = true;
        }
        final String neoforgeText = readText(zip, "META-INF/neoforge.mods.toml");
        final String forgeText = readText(zip, "META-INF/mods.toml");
        if (containsTransformer(fabricText) || containsTransformer(neoforgeText)
                || containsTransformer(forgeText)) {
            signals.add("metadata:transformer");
            core = true;
        }
        final String asmEntry = firstAsmEntry(entryNames);
        if (asmEntry != null) {
            signals.add("asmEntry:" + asmEntry);
            core = true;
        }
        if (core) {
            return Invasiveness.CORE;
        }

        if (!candidate.mixinConfigs().isEmpty()) {
            for (String config : candidate.mixinConfigs()) {
                signals.add("mixinConfig:" + config);
            }
            return Invasiveness.MIXIN;
        }
        final String mixinEntry = firstMixinEntry(entryNames);
        if (mixinEntry != null) {
            signals.add("mixinEntry:" + mixinEntry);
            return Invasiveness.MIXIN;
        }

        return Invasiveness.API;
    }

    private static MixinData readMixinData(
            final ZipFile zip,
            final List<String> entryNames,
            final ModCandidate candidate) throws IOException {
        final LinkedHashSet<String> targets = new LinkedHashSet<>();
        final List<String> renderConfigs = new ArrayList<>();
        for (String config : candidate.mixinConfigs()) {
            final String text = findConfigText(zip, entryNames, config);
            if (text == null) {
                throw new MixinDataException(FailureCode.IO_ERROR,
                        "声明的 mixin 配置不存在或不可读：" + config);
            }
            final String cleanText = stripBom(text);
            if (cleanText.contains("net.minecraft.client.render")) {
                renderConfigs.add(config);
            }
            readTargetsFromConfig(config, cleanText, targets);
        }
        if (!candidate.mixinConfigs().isEmpty()) {
            final String mixinEntry = firstMixinEntry(entryNames);
            if (mixinEntry != null && !candidate.mixinConfigs().contains(mixinEntry)) {
                final String text = readText(zip, mixinEntry);
                if (text != null) {
                    readTargetsFromConfig(mixinEntry, stripBom(text), targets);
                }
            }
        }
        return new MixinData(List.copyOf(targets), List.copyOf(renderConfigs));
    }

    private static void readTargetsFromConfig(
            final String config,
            final String text,
            final LinkedHashSet<String> targets) throws IOException {
        final JsonElement root;
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.LENIENT);
            root = JsonParser.parseReader(reader);
        } catch (JsonParseException | IllegalStateException e) {
            throw new MixinDataException(FailureCode.MALFORMED_JSON,
                    "mixin 配置不是合法 JSON：" + config + "：" + e.getMessage());
        }
        if (!root.isJsonObject()) {
            throw new MixinDataException(FailureCode.MALFORMED_JSON,
                    "mixin 配置根节点不是 JSON 对象：" + config);
        }
        final JsonObject object = root.getAsJsonObject();
        for (String key : List.of("mixins", "client", "server")) {
            final JsonElement element = object.get(key);
            if (element == null || !element.isJsonArray()) {
                continue;
            }
            final JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                    addTarget(item.getAsString(), targets);
                }
            }
        }
    }

    private static void addTarget(final String className, final LinkedHashSet<String> targets) {
        final int dot = className.lastIndexOf('.');
        final String simple = dot >= 0 ? className.substring(dot + 1) : className;

        // 嵌套类（含 $）先按 $ 切分，每段独立剥前缀 Mixin 和后缀
        // Mixin / MixinPlugin / Accessor / Invoker，再拼回去；
        // 例：VertexMultiConsumerMixin$DoubleMixin -> VertexMultiConsumer$Double。
        final String target = simple.indexOf('$') >= 0
                ? stripEachSegment(simple)
                : stripAffixes(simple);
        if (target != null && !target.isEmpty()) {
            targets.add(target);
        }
    }

    private static String stripEachSegment(final String simple) {
        final String[] segments = simple.split("\\$");
        final StringBuilder target = new StringBuilder();
        boolean inferred = false;
        for (int i = 0; i < segments.length; i++) {
            final String stripped = stripAffixes(segments[i]);
            inferred = inferred || stripped != null;
            if (i > 0) {
                target.append('$');
            }
            target.append(stripped == null ? segments[i] : stripped);
        }
        return inferred ? target.toString() : null;
    }

    private static String stripAffixes(final String name) {
        String target = name;
        boolean inferred = false;

        // SPEC §2.4：真实数据同时存在前缀式和后缀式命名，两条都要做。
        if (target.startsWith("Mixin") && target.length() > "Mixin".length()) {
            target = target.substring("Mixin".length());
            inferred = true;
        }
        for (String suffix : TARGET_SUFFIXES) {
            if (target.length() > suffix.length() && target.endsWith(suffix)) {
                target = target.substring(0, target.length() - suffix.length());
                inferred = true;
                break;
            }
        }
        return inferred ? target : null;
    }

    private static String findConfigText(
            final ZipFile zip,
            final List<String> entryNames,
            final String config) throws IOException {
        final String normalized = config.startsWith("/") ? config.substring(1) : config;
        final String direct = findEntry(entryNames, normalized);
        if (direct != null) {
            return readText(zip, direct);
        }
        for (String entry : entryNames) {
            if (!isNestedJar(entry)) {
                continue;
            }
            final byte[] bytes = readBytes(zip, entry);
            if (bytes == null) {
                continue;
            }
            final String nested = findInNestedJar(bytes, normalized);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static String findInNestedJar(final byte[] jarBytes, final String config) throws IOException {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                final String name = entry.getName();
                if (name.equals(config) || name.endsWith("/" + config)) {
                    return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    private static String findEntry(final List<String> entryNames, final String name) {
        for (String entry : entryNames) {
            if (entry.equals(name) || entry.endsWith("/" + name)) {
                return entry;
            }
        }
        return null;
    }

    private static String readText(final ZipFile zip, final String name) throws IOException {
        final byte[] bytes = readBytes(zip, name);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(final ZipFile zip, final String name) throws IOException {
        final ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            return null;
        }
        try (InputStream input = zip.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static boolean containsTransformer(final String text) {
        return text != null && TRANSFORMER_PATTERN.matcher(text).find();
    }

    private static String firstAsmEntry(final List<String> entryNames) {
        for (String entry : entryNames) {
            if (entry.toLowerCase(Locale.ROOT).contains("/asm/")) {
                return entry;
            }
        }
        return null;
    }

    private static String firstMixinEntry(final List<String> entryNames) {
        for (String entry : entryNames) {
            if (MIXIN_ENTRY_PATTERN.matcher(entry).find()) {
                return entry;
            }
        }
        return null;
    }

    private static boolean isNestedJar(final String entry) {
        final String lower = entry.toLowerCase(Locale.ROOT);
        return (entry.startsWith("META-INF/jars/") || entry.startsWith("META-INF/jarjar/"))
                && lower.endsWith(".jar");
    }

    private static ModType classifyModType(final ModCandidate candidate) {
        final String id = normalizeId(candidate.modId());
        if (!candidate.provides().isEmpty() || KNOWN_LIBRARIES.contains(id)) {
            return ModType.LIBRARY;
        }
        if (SHADER_LOADERS.contains(id)) {
            return ModType.SHADER_LOADER;
        }
        if (OPTIMIZERS.contains(id)) {
            return ModType.OPTIMIZER;
        }
        if (HACK_CLIENTS.contains(id)) {
            return ModType.HACK_CLIENT;
        }
        return ModType.NORMAL;
    }

    private static SupportPolicy supportPolicy(
            final ModCandidate candidate,
            final Invasiveness invasiveness,
            final ModType modType) {
        final String id = normalizeId(candidate.modId());
        if (modType == ModType.HACK_CLIENT || "optifine".equals(id)) {
            return SupportPolicy.EXCLUDED;
        }
        if (invasiveness == Invasiveness.RENDER || invasiveness == Invasiveness.CORE) {
            return SupportPolicy.EXCLUDED;
        }
        return SupportPolicy.SUPPORTED;
    }

    private static ModCandidate copyWithAnalysis(
            final ModCandidate candidate,
            final List<String> targets,
            final ModType modType,
            final Invasiveness invasiveness,
            final List<String> signals,
            final SupportPolicy supportPolicy) {
        return new ModCandidate(
                candidate.sourceFile(),
                candidate.sha256(),
                candidate.loaderFamily(),
                candidate.status(),
                candidate.modId(),
                candidate.version(),
                candidate.name(),
                candidate.side(),
                candidate.depends(),
                candidate.recommends(),
                candidate.breaks(),
                candidate.provides(),
                candidate.entrypoints(),
                candidate.mixinConfigs(),
                targets,
                true,
                candidate.nestedJars(),
                modType,
                invasiveness,
                signals,
                supportPolicy,
                candidate.failureCode(),
                candidate.failureMessage(),
                candidate.unknownFields());
    }

    private static ModCandidate failed(
            final ModCandidate candidate,
            final FailureCode code,
            final String message) {
        LOG.error("解析失败 {}: {}，原因：{}", candidate.sourceFile(), code, message);
        return new ModCandidate(
                candidate.sourceFile(),
                candidate.sha256(),
                candidate.loaderFamily(),
                Status.FAILED,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                null,
                null,
                List.of(),
                null,
                code,
                message,
                List.of());
    }

    private static String internalMessage(final RuntimeException exception) {
        return "这是程序异常不是数据问题：" + exception.getClass().getName()
                + ": " + exception.getMessage();
    }

    private static String stripBom(final String text) {
        return text.startsWith(UTF8_BOM) ? text.substring(1) : text;
    }

    private static String normalizeId(final String id) {
        if (id == null) {
            return "";
        }
        return id.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private static Set<String> normalized(final String... values) {
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(normalizeId(value));
        }
        return Set.copyOf(result);
    }

    private record MixinData(List<String> targets, List<String> renderConfigs) {
        private MixinData {
            targets = List.copyOf(targets);
            renderConfigs = List.copyOf(renderConfigs);
        }
    }

    private static final class MixinDataException extends RuntimeException {
        private final FailureCode code;

        private MixinDataException(final FailureCode code, final String message) {
            super(message);
            this.code = code;
        }
    }
}
