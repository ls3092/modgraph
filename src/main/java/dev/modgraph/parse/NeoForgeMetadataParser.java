package dev.modgraph.parse;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlParser;
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
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public final class NeoForgeMetadataParser {

    private static final Logger LOG = LoggerFactory.getLogger(NeoForgeMetadataParser.class);

    private static final String NEOFORGE_METADATA = "META-INF/neoforge.mods.toml";
    private static final String FORGE_METADATA = "META-INF/mods.toml";
    private static final String UTF8_BOM = "\uFEFF";

    private static final Set<String> KNOWN_TOP_LEVEL_FIELDS = Set.of(
            "mods",
            "dependencies",
            "jarJar",
            "mixins",
            "modLoader",
            "loaderVersion"
    );

    public ModCandidate parse(final ScannedJar scannedJar) {
        Objects.requireNonNull(scannedJar, "scannedJar");

        if (scannedJar.candidate().status() == Status.FAILED) {
            return scannedJar.candidate();
        }

        final LoaderFamily loaderFamily = loaderFamily(scannedJar.entryNames());
        final String metadataName = metadataName(loaderFamily);
        if (metadataName == null) {
            return failure(scannedJar, loaderFamily, FailureCode.NO_METADATA,
                    noMetadataMessage(scannedJar.entryNames()));
        }

        try {
            return parseMetadata(scannedJar, loaderFamily, metadataName);
        } catch (final MetadataFailure e) {
            return failure(scannedJar, loaderFamily, e.code(), e.getMessage());
        } catch (final ParsingException e) {
            return failure(scannedJar, loaderFamily, FailureCode.MALFORMED_TOML, messageOf(e));
        } catch (final ZipException e) {
            return failure(scannedJar, loaderFamily, FailureCode.NOT_A_ZIP, messageOf(e));
        } catch (final IOException e) {
            return failure(scannedJar, loaderFamily, FailureCode.IO_ERROR, messageOf(e));
        } catch (final RuntimeException e) {
            return internalFailure(scannedJar, loaderFamily, e);
        }
    }

    private static ModCandidate parseMetadata(final ScannedJar scannedJar, final LoaderFamily loaderFamily,
            final String metadataName) throws IOException {
        final ModCandidate base = scannedJar.candidate();
        try (ZipFile zip = new ZipFile(scannedJar.jarPath().toFile())) {
            final ZipEntry entry = zip.getEntry(metadataName);
            if (entry == null) {
                throw new MetadataFailure(FailureCode.NO_METADATA,
                        noMetadataMessage(scannedJar.entryNames()));
            }

            final Config root = parseToml(readMetadata(zip, entry));
            final List<Config> mods = configList(root.valueMap().get("mods"), "mods", true);
            if (mods.isEmpty()) {
                throw new MetadataFailure(FailureCode.MISSING_REQUIRED_FIELD,
                        "缺少必需字段 mods");
            }
            if (mods.size() > 1) {
                LOG.warn("{} 声明了 {} 个 [[mods]] 条目，只取第一条", base.sourceFile(), mods.size());
            }

            final Config firstMod = mods.getFirst();
            final String modId = requiredString(firstMod, "modId");
            final String version = requiredString(firstMod, "version");
            final String name = optionalString(firstMod, "displayName");
            final Side side = sideOfMod(firstMod);
            final List<Constraint> depends = new ArrayList<>();
            final List<Constraint> recommends = new ArrayList<>();
            final List<Constraint> breaks = new ArrayList<>();
            for (final Config dependency : dependencies(root, modId)) {
                final DependencyKind kind = dependencyKind(dependency);
                final Constraint constraint = new Constraint(
                        requiredString(dependency, "modId"),
                        versionRanges(dependency),
                        kind.required,
                        ordering(dependency),
                        side(dependency)
                );
                switch (kind) {
                    case REQUIRED -> depends.add(constraint);
                    case OPTIONAL -> recommends.add(constraint);
                    case INCOMPATIBLE -> breaks.add(constraint);
                }
            }

            final List<String> provides = stringList(firstMod.valueMap().get("provides"),
                    "provides", false);
            final List<String> mixinConfigs = mixinConfigs(root);
            final List<String> nestedJars = nestedJars(root);
            final List<String> unknownFieldNames = unknownFields(root, modId);

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
                    List.copyOf(depends),
                    List.copyOf(recommends),
                    List.copyOf(breaks),
                    provides,
                    List.of(),
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

    private static Config parseToml(final String text) {
        return new TomlParser().parse(new StringReader(text));
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

    private static List<Config> dependencies(final Config root, final String modId) {
        final Object nested = root.valueMap().get("dependencies");
        if (nested != null) {
            if (!(nested instanceof Config dependencies)) {
                throw new MetadataFailure(FailureCode.MALFORMED_TOML,
                        "字段 dependencies 必须是表");
            }
            final Object forMod = dependencies.valueMap().get(modId);
            if (forMod != null) {
                return configList(forMod, "dependencies." + modId, true);
            }
        }

        final Object quotedKey = root.valueMap().get("dependencies." + modId);
        if (quotedKey != null) {
            return configList(quotedKey, "dependencies." + modId, true);
        }

        return List.of();
    }

    private static DependencyKind dependencyKind(final Config dependency) {
        final String type = optionalString(dependency, "type");
        if (type != null) {
            return switch (type.trim().toUpperCase(Locale.ROOT)) {
                case "REQUIRED" -> DependencyKind.REQUIRED;
                case "OPTIONAL" -> DependencyKind.OPTIONAL;
                case "INCOMPATIBLE" -> DependencyKind.INCOMPATIBLE;
                default -> throw new MetadataFailure(FailureCode.MALFORMED_TOML,
                        "依赖字段 type 的值未知：" + type);
            };
        }

        final Boolean required = optionalBoolean(dependency, "required");
        if (required != null) {
            return required ? DependencyKind.REQUIRED : DependencyKind.OPTIONAL;
        }

        final Boolean mandatory = optionalBoolean(dependency, "mandatory");
        if (mandatory != null) {
            return mandatory ? DependencyKind.REQUIRED : DependencyKind.OPTIONAL;
        }

        return DependencyKind.REQUIRED;
    }

    private static List<String> versionRanges(final Config dependency) {
        final Object value = dependency.valueMap().get("versionRange");
        if (value == null) {
            return List.of();
        }
        if (value instanceof String range) {
            return List.of(range);
        }
        if (value instanceof List<?> ranges) {
            final List<String> result = new ArrayList<>();
            for (final Object item : ranges) {
                if (!(item instanceof String range)) {
                    throw new MetadataFailure(FailureCode.MALFORMED_TOML,
                            "依赖字段 versionRange 必须是字符串或字符串数组");
                }
                result.add(range);
            }
            return List.copyOf(result);
        }
        throw new MetadataFailure(FailureCode.MALFORMED_TOML,
                "依赖字段 versionRange 必须是字符串或字符串数组");
    }

    private static Ordering ordering(final Config dependency) {
        final String value = optionalString(dependency, "ordering");
        if (value == null) {
            return Ordering.NONE;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "NONE" -> Ordering.NONE;
            case "BEFORE" -> Ordering.BEFORE;
            case "AFTER" -> Ordering.AFTER;
            default -> throw new MetadataFailure(FailureCode.MALFORMED_TOML,
                    "依赖字段 ordering 的值不认识：" + value);
        };
    }

    private static Side side(final Config config) {
        final String value = optionalString(config, "side");
        if (value == null) {
            return Side.UNKNOWN;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "BOTH" -> Side.BOTH;
            case "CLIENT" -> Side.CLIENT;
            case "SERVER" -> Side.SERVER;
            default -> throw new MetadataFailure(FailureCode.MALFORMED_TOML,
                    "依赖字段 side 的值不认识：" + value);
        };
    }

    private static Side sideOfMod(final Config mod) {
        final String displayTest = optionalString(mod, "displayTest");
        if (displayTest == null) {
            return null;
        }
        return "NORMAL".equalsIgnoreCase(displayTest.trim()) ? Side.BOTH : Side.UNKNOWN;
    }

    private static List<String> mixinConfigs(final Config root) {
        final List<Config> mixins = configList(root.valueMap().get("mixins"), "mixins", false);
        final List<String> result = new ArrayList<>();
        for (final Config mixin : mixins) {
            result.add(requiredString(mixin, "config"));
        }
        return List.copyOf(result);
    }

    private static List<String> nestedJars(final Config root) {
        final List<Config> jarJar = configList(root.valueMap().get("jarJar"), "jarJar", false);
        final List<String> result = new ArrayList<>();
        for (final Config nested : jarJar) {
            final String path = optionalString(nested, "path");
            final String jar = optionalString(nested, "jar");
            if (path != null) {
                result.add(path);
            } else if (jar != null) {
                result.add(jar);
            } else {
                throw new MetadataFailure(FailureCode.MALFORMED_TOML,
                        "jarJar 条目缺少 path 或 jar");
            }
        }
        return List.copyOf(result);
    }

    private static List<String> unknownFields(final Config root, final String modId) {
        final List<String> unknown = new ArrayList<>();
        final String quotedDependencyKey = "dependencies." + modId;
        for (final Config.Entry entry : root.entrySet()) {
            final String key = entry.getKey();
            if (KNOWN_TOP_LEVEL_FIELDS.contains(key) || key.equals(quotedDependencyKey)) {
                continue;
            }
            unknown.add(key);
        }
        return List.copyOf(unknown);
    }

    private static List<Config> configList(final Object value, final String field,
            final boolean required) {
        if (value == null) {
            if (required) {
                throw new MetadataFailure(FailureCode.MISSING_REQUIRED_FIELD,
                        "缺少必需字段 " + field);
            }
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new MetadataFailure(FailureCode.MALFORMED_TOML,
                    "字段 " + field + " 必须是表数组");
        }

        final List<Config> result = new ArrayList<>();
        for (final Object item : list) {
            if (!(item instanceof Config config)) {
                throw new MetadataFailure(FailureCode.MALFORMED_TOML,
                        "字段 " + field + " 的元素必须是表");
            }
            result.add(config);
        }
        return List.copyOf(result);
    }

    private static String requiredString(final Config config, final String field) {
        final Object value = config.valueMap().get(field);
        if (value == null) {
            throw new MetadataFailure(FailureCode.MISSING_REQUIRED_FIELD,
                    "缺少必需字段 " + field);
        }
        if (!(value instanceof String text)) {
            throw new MetadataFailure(FailureCode.MALFORMED_TOML,
                    "字段 " + field + " 必须是字符串");
        }
        if (text.isBlank()) {
            throw new MetadataFailure(FailureCode.MISSING_REQUIRED_FIELD,
                    "字段 " + field + " 为空");
        }
        return text;
    }

    private static String optionalString(final Config config, final String field) {
        final Object value = config.valueMap().get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new MetadataFailure(FailureCode.MALFORMED_TOML,
                    "字段 " + field + " 必须是字符串");
        }
        return text;
    }

    private static Boolean optionalBoolean(final Config config, final String field) {
        final Object value = config.valueMap().get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Boolean bool)) {
            throw new MetadataFailure(FailureCode.MALFORMED_TOML,
                    "字段 " + field + " 必须是布尔值");
        }
        return bool;
    }

    private static List<String> stringList(final Object value, final String field,
            final boolean required) {
        if (value == null) {
            if (required) {
                throw new MetadataFailure(FailureCode.MISSING_REQUIRED_FIELD,
                        "缺少必需字段 " + field);
            }
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new MetadataFailure(FailureCode.MALFORMED_TOML,
                    "字段 " + field + " 必须是字符串数组");
        }

        final List<String> result = new ArrayList<>();
        for (final Object item : list) {
            if (!(item instanceof String text)) {
                throw new MetadataFailure(FailureCode.MALFORMED_TOML,
                        "字段 " + field + " 的元素必须是字符串");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private static LoaderFamily loaderFamily(final List<String> entryNames) {
        if (entryNames.contains(NEOFORGE_METADATA)) {
            return LoaderFamily.NEOFORGE;
        }
        if (entryNames.contains(FORGE_METADATA)) {
            return LoaderFamily.FORGE;
        }
        return LoaderFamily.UNKNOWN;
    }

    private static String metadataName(final LoaderFamily loaderFamily) {
        return switch (loaderFamily) {
            case NEOFORGE -> NEOFORGE_METADATA;
            case FORGE -> FORGE_METADATA;
            default -> null;
        };
    }

    private static String noMetadataMessage(final List<String> entryNames) {
        final String base = "未找到 " + NEOFORGE_METADATA + " 或 " + FORGE_METADATA;
        if (entryNames.stream().anyMatch(NeoForgeMetadataParser::looksLikeNestedJarEntry)) {
            return base + "；检测到内层 jar（jar-in-jar 嵌套），本步不扫描嵌套内容";
        }
        return base + "；若该 jar 仅包含 META-INF/ 或内层 jar，可能是 jar-in-jar 嵌套打包，本步不扫描嵌套内容";
    }

    private static boolean looksLikeNestedJarEntry(final String entryName) {
        return entryName.startsWith("META-INF/jars/")
                || entryName.startsWith("META-INF/jarjar/")
                || entryName.toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static ModCandidate failure(final ScannedJar scannedJar, final LoaderFamily loaderFamily,
            final FailureCode code, final String message) {
        final ModCandidate base = scannedJar.candidate();
        LOG.error("解析失败 {}: {}，原因：{}", base.sourceFile(), code, message);
        return failedCandidate(scannedJar, loaderFamily, code, message);
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

    private enum DependencyKind {
        REQUIRED(true),
        OPTIONAL(false),
        INCOMPATIBLE(true);

        private final boolean required;

        DependencyKind(final boolean required) {
            this.required = required;
        }
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
