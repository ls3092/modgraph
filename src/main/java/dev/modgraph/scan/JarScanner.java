package dev.modgraph.scan;

import dev.modgraph.model.FailureCode;
import dev.modgraph.model.LoaderFamily;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * 枚举目录下的全部 jar，并为每个文件产出一条 {@link ScannedJar}。
 *
 * <p>单个文件的失败不中断扫描：无法读出 SHA-256 记 {@code IO_ERROR}，
 * 不是有效 zip 记 {@code NOT_A_ZIP}，其余读取异常记 {@code IO_ERROR}，
 * 程序异常记 {@code INTERNAL_ERROR}。失败文件同样产出记录并计入总数。</p>
 */
public final class JarScanner {

    private static final Logger LOG = LoggerFactory.getLogger(JarScanner.class);

    private static final int BUFFER_SIZE = 8192;

    public List<ScannedJar> scan(final Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        final List<Path> jars;
        try (Stream<Path> paths = Files.walk(root)) {
            jars = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }

        final List<ScannedJar> scanned = new ArrayList<>(jars.size());
        for (final Path jar : jars) {
            scanned.add(scanOne(jar));
        }
        return List.copyOf(scanned);
    }

    private static ScannedJar scanOne(final Path jar) {
        final String sha256;
        try {
            sha256 = sha256(jar);
        } catch (final IOException e) {
            return failed(jar, "", FailureCode.IO_ERROR,
                    "读取文件计算 SHA-256 失败：" + messageOf(e), e);
        }

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            final List<String> names = new ArrayList<>();
            final Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
            return new ScannedJar(jar, okCandidate(jar, sha256), List.copyOf(names));
        } catch (final ZipException e) {
            return failed(jar, sha256, FailureCode.NOT_A_ZIP, messageOf(e), e);
        } catch (final IOException e) {
            return failed(jar, sha256, FailureCode.IO_ERROR, messageOf(e), e);
        } catch (final RuntimeException e) {
            return failed(jar, sha256, FailureCode.INTERNAL_ERROR,
                    "这是程序异常不是数据问题：" + e.getClass().getName() + ": " + messageOf(e), e);
        }
    }

    private static String sha256(final Path file) throws IOException {
        final MessageDigest digest = newSha256Digest();
        try (InputStream input = Files.newInputStream(file)) {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static ModCandidate okCandidate(final Path jar, final String sha256) {
        return candidate(jar, sha256, Status.OK, null, null);
    }

    private static ScannedJar failed(final Path jar, final String sha256, final FailureCode code,
            final String message, final Throwable cause) {
        final String sourceFile = jar.getFileName().toString();
        if (cause == null) {
            LOG.error("解析失败 {}: {}，原因：{}", sourceFile, code, message);
        } else {
            LOG.error("解析失败 {}: {}，原因：{}", sourceFile, code, message, cause);
        }
        return new ScannedJar(jar, candidate(jar, sha256, Status.FAILED, code, message), List.of());
    }

    private static ModCandidate candidate(final Path jar, final String sha256, final Status status,
            final FailureCode code, final String message) {
        return new ModCandidate(
                jar.getFileName().toString(),
                sha256,
                LoaderFamily.UNKNOWN,
                status,
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
}
