package dev.modgraph.parse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import dev.modgraph.model.Constraint;
import dev.modgraph.model.FailureCode;
import dev.modgraph.model.LoaderFamily;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Ordering;
import dev.modgraph.model.Side;
import dev.modgraph.model.Status;
import dev.modgraph.scan.ScannedJar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public final class FabricMetadataParser {

    private static final Logger LOG = LoggerFactory.getLogger(FabricMetadataParser.class);

    private static final String FABRIC_MOD_JSON = "fabric.mod.json";
    private static final String QUILT_MOD_JSON = "quilt.mod.json";
    private static final String UTF8_BOM = "\uFEFF";

    private static final Set<String> KNOWN_FIELDS = Set.of(
            "schemaVersion",
            "id",
            "version",
            "name",
            "environment",
            "depends",
            "recommends",
            "breaks",
            "provides",
            "entrypoints",
            "mixins",
            "jars"
    );

    public ModCandidate parse(final ScannedJar scannedJar) {
        Objects.requireNonNull(scannedJar, "scannedJar");

        if (scannedJar.candidate().status() == Status.FAILED) {
            return scannedJar.candidate();
        }

        final LoaderFamily loaderFamily = loaderFamily(scannedJar.entryNames());
        if (!scannedJar.entryNames().contains(FABRIC_MOD_JSON)) {
            return failure(scannedJar, loaderFamily, FailureCode.NO_METADATA,
                    noMetadataMessage(scannedJar.entryNames()));
        }

        try {
            return parseMetadata(scannedJar, loaderFamily);
        } catch (final MetadataFailure e) {
            return failure(scannedJar, loaderFamily, e.code(), e.getMessage());
        } catch (final JsonParseException e) {
            return failure(scannedJar, loaderFamily, FailureCode.MALFORMED_JSON, messageOf(e));
        } catch (final ZipException e) {
            return failure(scannedJar, loaderFamily, FailureCode.NOT_A_ZIP, messageOf(e));
        } catch (final IOException e) {
            return failure(scannedJar, loaderFamily, FailureCode.IO_ERROR, messageOf(e));
        } catch (final RuntimeException e) {
            return internalFailure(scannedJar, loaderFamily, e);
        }
    }

    private static ModCandidate parseMetadata(final ScannedJar scannedJar, final LoaderFamily loaderFamily)
            throws IOException {
        final ModCandidate base = scannedJar.candidate();
        try (ZipFile zip = new ZipFile(scannedJar.jarPath().toFile())) {
            final ZipEntry entry = zip.getEntry(FABRIC_MOD_JSON);
            if (entry == null) {
                throw new MetadataFailure(FailureCode.NO_METADATA,
                        noMetadataMessage(scannedJar.entryNames()));
            }

            final JsonObject root = parseJson(readMetadata(zip, entry));
            requireSchemaVersion(root);

            final String modId = requiredString(root, "id");
            final String version = requiredString(root, "version");
            final String name = optionalString(root, "name");
            final Side side = side(root);
            final List<Constraint> depends = constraints(root, "depends", true);
            final List<Constraint> recommends = constraints(root, "recommends", false);
            final List<Constraint> breaks = constraints(root, "breaks", true);
            final List<String> provides = stringList(root, "provides");
            final List<String> entrypoints = entrypointKeys(root);
            final List<String> mixinConfigs = mixinConfigs(root);
            final List<String> nestedJars = nestedJars(root);
            final List<String> unknownFieldNames = unknownFields(root);

            if (!unknownFieldNames.isEmpty()) {
                LOG.warn("{} 含未定义字段：{}", base.sourceFile(), unknownFieldNames);
            }

            return new ModCandidate(
                    base.sourceFile(),
                    base.sha256(),
                    loaderFamily,
                    Status.OK,
                    modId,
                    version,
                    name,
                    side,
                    depends,
                    recommends,
                    breaks,
                    provides,
                    entrypoints,
                    mixinConfigs,
                    List.of(),
                    false,
                    nestedJars,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    null,
                    unknownFieldNames
            );
        }
    }

    private static String readMetadata(final ZipFile zip, final ZipEntry entry) throws IOException {
        final String text;
        try (InputStream input = zip.getInputStream(entry)) {
            text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final ZipException e) {
            throw new MetadataFailure(FailureCode.IO_ERROR,
                    "读取 zip 条目 " + entry.getName() + " 失败（条目数据损坏）：" + messageOf(e));
        }
        return text.startsWith(UTF8_BOM) ? text.substring(UTF8_BOM.length()) : text;
    }

    private static String noMetadataMessage(final List<String> entryNames) {
        final String base = "未找到根目录 " + FABRIC_MOD_JSON;
        if (entryNames.stream().anyMatch(FabricMetadataParser::looksLikeNestedJarEntry)) {
            return base + "；检测到内层 jar（jar-in-jar 嵌套），本步不扫描嵌套内容";
        }
        return base + "；若该 jar 仅包含 META-INF/ 或内层 jar，可能是 jar-in-jar 嵌套打包，本步不扫描嵌套内容";
    }

    private static boolean looksLikeNestedJarEntry(final String entryName) {
        return entryName.startsWith("META-INF/jars/")
                || entryName.startsWith("META-INF/jarjar/")
                || entryName.toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static JsonObject parseJson(final String json) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            final JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonObject()) {
                throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                        FABRIC_MOD_JSON + " 的根节点不是 JSON 对象");
            }
            return root.getAsJsonObject();
        }
    }

    private static void requireSchemaVersion(final JsonObject root) {
        final JsonElement element = root.get("schemaVersion");
        if (element == null || element.isJsonNull()) {
            throw new MetadataFailure(FailureCode.MISSING_REQUIRED_FIELD,
                    "缺少必需字段 schemaVersion");
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                    "字段 schemaVersion 必须是数字");
        }
        if (element.getAsInt() < 1) {
            throw new MetadataFailure(FailureCode.MISSING_REQUIRED_FIELD,
                    "schemaVersion 必须大于等于 1");
        }
    }

    private static String requiredString(final JsonObject root, final String field) {
        final JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            throw new MetadataFailure(FailureCode.MISSING_REQUIRED_FIELD,
                    "缺少必需字段 " + field);
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                    "字段 " + field + " 必须是字符串");
        }
        final String value = element.getAsString();
        if (value.isBlank()) {
            throw new MetadataFailure(FailureCode.MISSING_REQUIRED_FIELD,
                    "字段 " + field + " 为空");
        }
        return value;
    }

    private static String optionalString(final JsonObject root, final String field) {
        final JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                    "字段 " + field + " 必须是字符串");
        }
        return element.getAsString();
    }

    private static Side side(final JsonObject root) {
        final String environment = optionalString(root, "environment");
        if (environment == null) {
            return null;
        }
        return switch (environment) {
            case "*" -> Side.BOTH;
            case "client" -> Side.CLIENT;
            case "server" -> Side.SERVER;
            default -> Side.UNKNOWN;
        };
    }

    private static List<Constraint> constraints(final JsonObject root, final String field,
            final boolean required) {
        final JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonObject()) {
            throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                    "字段 " + field + " 必须是对象");
        }

        final List<Constraint> result = new ArrayList<>();
        for (final Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            result.add(new Constraint(
                    entry.getKey(),
                    versionRanges(entry.getValue(), field + "." + entry.getKey()),
                    required,
                    Ordering.NONE,
                    Side.UNKNOWN
            ));
        }
        return List.copyOf(result);
    }

    private static List<String> versionRanges(final JsonElement element, final String context) {
        if (element == null || element.isJsonNull()) {
            throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                    "字段 " + context + " 缺少版本区间");
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return List.of(element.getAsString());
        }
        if (element.isJsonArray()) {
            final List<String> result = new ArrayList<>();
            for (final JsonElement item : element.getAsJsonArray()) {
                if (item == null || !item.isJsonPrimitive()
                        || !item.getAsJsonPrimitive().isString()) {
                    throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                            "字段 " + context + " 的版本区间必须是字符串");
                }
                result.add(item.getAsString());
            }
            return List.copyOf(result);
        }
        throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                "字段 " + context + " 的版本区间必须是字符串或字符串数组");
    }

    private static List<String> stringList(final JsonObject root, final String field) {
        final JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                    "字段 " + field + " 必须是数组");
        }

        final List<String> result = new ArrayList<>();
        for (final JsonElement item : element.getAsJsonArray()) {
            if (item == null || !item.isJsonPrimitive()
                    || !item.getAsJsonPrimitive().isString()) {
                throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                        "字段 " + field + " 的元素必须是字符串");
            }
            result.add(item.getAsString());
        }
        return List.copyOf(result);
    }

    private static List<String> entrypointKeys(final JsonObject root) {
        final JsonElement element = root.get("entrypoints");
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonObject()) {
            throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                    "字段 entrypoints 必须是对象");
        }
        return List.copyOf(element.getAsJsonObject().keySet());
    }

    private static List<String> mixinConfigs(final JsonObject root) {
        final JsonElement element = root.get("mixins");
        if (element == null || element.isJsonNull()) {
            return List.of();
        }

        final List<String> result = new ArrayList<>();
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            result.add(element.getAsString());
        } else if (element.isJsonArray()) {
            for (final JsonElement item : element.getAsJsonArray()) {
                addMixinConfig(item, result);
            }
        } else if (element.isJsonObject()) {
            addMixinConfig(element, result);
        } else {
            throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                    "字段 mixins 必须是字符串、对象或数组");
        }
        return List.copyOf(result);
    }

    private static void addMixinConfig(final JsonElement element, final List<String> result) {
        if (element == null || element.isJsonNull()) {
            throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                    "mixins 条目为空");
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            result.add(element.getAsString());
            return;
        }
        if (element.isJsonObject()) {
            final JsonElement config = element.getAsJsonObject().get("config");
            if (config == null || !config.isJsonPrimitive()
                    || !config.getAsJsonPrimitive().isString()) {
                throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                        "mixins 条目的 config 必须是字符串");
            }
            result.add(config.getAsString());
            return;
        }
        throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                "mixins 条目必须是字符串或含 config 的对象");
    }

    private static List<String> nestedJars(final JsonObject root) {
        final JsonElement element = root.get("jars");
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                    "字段 jars 必须是数组");
        }

        final List<String> result = new ArrayList<>();
        for (final JsonElement item : element.getAsJsonArray()) {
            if (item == null || !item.isJsonObject()) {
                throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                        "jars 的元素必须是对象");
            }
            final JsonElement file = item.getAsJsonObject().get("file");
            if (file == null || !file.isJsonPrimitive()
                    || !file.getAsJsonPrimitive().isString()) {
                throw new MetadataFailure(FailureCode.MALFORMED_JSON,
                        "jars 元素的 file 必须是字符串");
            }
            result.add(file.getAsString());
        }
        return List.copyOf(result);
    }

    private static List<String> unknownFields(final JsonObject root) {
        final List<String> result = new ArrayList<>();
        for (final String key : root.keySet()) {
            if (!KNOWN_FIELDS.contains(key)) {
                result.add(key);
            }
        }
        return List.copyOf(result);
    }

    private static LoaderFamily loaderFamily(final List<String> entryNames) {
        if (entryNames.contains(QUILT_MOD_JSON)) {
            return LoaderFamily.QUILT;
        }
        if (entryNames.contains(FABRIC_MOD_JSON)) {
            return LoaderFamily.FABRIC;
        }
        return LoaderFamily.UNKNOWN;
    }

    private static ModCandidate internalFailure(final ScannedJar scannedJar,
            final LoaderFamily loaderFamily, final RuntimeException exception) {
        final ModCandidate base = scannedJar.candidate();
        final String exceptionClass = exception.getClass().getName();
        final String message = "这是程序异常不是数据问题：" + exceptionClass + ": "
                + messageOf(exception);
        LOG.error("解析失败 {}: INTERNAL_ERROR，程序异常不是数据问题，异常类：{}，原因：{}",
                base.sourceFile(), exceptionClass, messageOf(exception), exception);
        return failedCandidate(scannedJar, loaderFamily, FailureCode.INTERNAL_ERROR, message);
    }

    private static ModCandidate failure(final ScannedJar scannedJar, final LoaderFamily loaderFamily,
            final FailureCode code, final String message) {
        final ModCandidate base = scannedJar.candidate();
        LOG.error("解析失败 {}: {}，原因：{}", base.sourceFile(), code, message);
        return failedCandidate(scannedJar, loaderFamily, code, message);
    }

    private static ModCandidate failedCandidate(final ScannedJar scannedJar,
            final LoaderFamily loaderFamily, final FailureCode code, final String message) {
        final ModCandidate base = scannedJar.candidate();
        return new ModCandidate(
                base.sourceFile(),
                base.sha256(),
                loaderFamily,
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
                List.of()
        );
    }

    private static String messageOf(final Throwable throwable) {
        final String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }

    private static final class MetadataFailure extends RuntimeException {

        private final FailureCode code;

        private MetadataFailure(final FailureCode code, final String message) {
            super(message);
            this.code = Objects.requireNonNull(code, "code");
        }

        private FailureCode code() {
            return code;
        }
    }
}
