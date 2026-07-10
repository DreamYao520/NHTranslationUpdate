package com.dreamyao.nhtranslationupdate.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

public final class IOUtil {

    private IOUtil() {}

    public static void atomicCopy(Path source, Path target) throws IOException {
        Files.createDirectories(
            target.toAbsolutePath()
                .normalize()
                .getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".nhtranslationupdate.tmp");
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
        atomicMove(temporary, target);
    }

    public static void atomicWriteUtf8(Path target, String content) throws IOException {
        Files.createDirectories(
            target.toAbsolutePath()
                .normalize()
                .getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".nhtranslationupdate.tmp");
        Files.write(temporary, content.getBytes(StandardCharsets.UTF_8));
        atomicMove(temporary, target);
    }

    public static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new DeleteFailure(exception);
                    }
                });
        } catch (DeleteFailure failure) {
            throw failure.cause;
        }
    }

    private static final class DeleteFailure extends RuntimeException {

        private final IOException cause;

        private DeleteFailure(IOException cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
