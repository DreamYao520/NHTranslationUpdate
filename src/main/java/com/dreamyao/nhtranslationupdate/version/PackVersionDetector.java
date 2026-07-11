package com.dreamyao.nhtranslationupdate.version;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PackVersionDetector {

    private static final Pattern MAIN_MENU_VERSION = Pattern.compile("(?i)^\\s*GTNH\\s+(.+?)(?:\\s+\\(|\\s*$)");
    private static final Pattern DREAMCRAFT_VERSION = Pattern
        .compile("(?i)^\\s*S:(?:ModPackVersion|PackVersion)\\s*=\\s*(.*?)\\s*$");
    private static final Pattern SAFE_VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,79}");

    private PackVersionDetector() {}

    public static Result detect(Path gameDirectory, String configuredVersion) {
        String override = normalize(configuredVersion);
        if (override != null) return new Result(override, "nhtranslationupdate.properties");

        List<Candidate> candidates = Arrays.asList(
            new Candidate(
                gameDirectory.resolve("config/txloader/load/mainmenu/version.txt"),
                MAIN_MENU_VERSION,
                "GTNH main-menu version marker"),
            new Candidate(
                gameDirectory.resolve("config/GTNewHorizons/dreamcraft.cfg"),
                DREAMCRAFT_VERSION,
                "GTNH dreamcraft.cfg"),
            new Candidate(gameDirectory.resolve("config/dreamcraft.cfg"), DREAMCRAFT_VERSION, "legacy dreamcraft.cfg"));

        for (Candidate candidate : candidates) {
            String version = read(candidate.path, candidate.pattern);
            if (version != null) return new Result(version, candidate.description);
        }
        return null;
    }

    private static String read(Path file, Pattern pattern) {
        if (!Files.isRegularFile(file)) return null;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String version = normalize(matcher.group(1));
                    if (version != null) return version;
                }
            }
        } catch (IOException ignored) {
            // The caller logs a single useful message if no marker can be read.
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String version = value.trim();
        if (version.length() >= 2 && version.startsWith("\"") && version.endsWith("\"")) {
            version = version.substring(1, version.length() - 1)
                .trim();
        }
        return SAFE_VERSION.matcher(version)
            .matches() ? version : null;
    }

    private static final class Candidate {

        private final Path path;
        private final Pattern pattern;
        private final String description;

        private Candidate(Path path, Pattern pattern, String description) {
            this.path = path;
            this.pattern = pattern;
            this.description = description;
        }
    }

    public static final class Result {

        public final String version;
        public final String source;

        private Result(String version, String source) {
            this.version = version;
            this.source = source;
        }
    }
}
