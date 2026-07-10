package com.dreamyao.nhtranslationupdate.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

public final class PathPolicy {

    private PathPolicy() {}

    public static String safeRelative(String zipName) {
        if (zipName == null || zipName.isEmpty()
            || zipName.indexOf('\0') >= 0
            || zipName.indexOf('\\') >= 0
            || zipName.startsWith("/")
            || zipName.contains(":")) {
            throw new IllegalArgumentException("Unsafe archive path: " + zipName);
        }
        Path path = Paths.get(zipName)
            .normalize();
        if (path.isAbsolute() || path.getNameCount() == 0 || path.startsWith("..")) {
            throw new IllegalArgumentException("Unsafe archive path: " + zipName);
        }
        String normalized = path.toString()
            .replace('\\', '/');
        if (normalized.equals("..") || normalized.startsWith("../")) {
            throw new IllegalArgumentException("Unsafe archive path: " + zipName);
        }
        return normalized;
    }

    public static boolean isAllowed(String relative, List<String> allowedRoots) {
        String candidate = relative.replace('\\', '/')
            .toLowerCase(Locale.ROOT);
        for (String root : allowedRoots) {
            String allowed = root.replace('\\', '/')
                .toLowerCase(Locale.ROOT);
            if (candidate.equals(allowed) || candidate.startsWith(allowed + "/")) return true;
        }
        return false;
    }

    public static Path resolveInside(Path root, String relative) {
        Path normalizedRoot = root.toAbsolutePath()
            .normalize();
        Path resolved = normalizedRoot.resolve(relative)
            .normalize();
        if (!resolved.startsWith(normalizedRoot)) throw new IllegalArgumentException("Path escaped root: " + relative);
        return resolved;
    }
}
