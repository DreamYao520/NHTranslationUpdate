package com.dreamyao.nhtranslationupdate.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class IOUtil {

    private IOUtil() {}

    public static void atomicWriteUtf8(Path target, String content) throws IOException {
        atomicWrite(target, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void atomicWrite(Path target, byte[] content) throws IOException {
        Files.createDirectories(
            target.toAbsolutePath()
                .normalize()
                .getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".nhtranslationupdate.tmp");
        Files.write(temporary, content);
        atomicMove(temporary, target);
    }

    public static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
