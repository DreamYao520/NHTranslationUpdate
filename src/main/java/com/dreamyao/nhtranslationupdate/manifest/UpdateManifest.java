package com.dreamyao.nhtranslationupdate.manifest;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class UpdateManifest {

    public int schemaVersion;
    public String release;
    public String minecraftVersion;
    public List<String> packVersions;
    public List<Artifact> artifacts;

    public void validate() {
        if (schemaVersion != 2) throw new IllegalArgumentException("Unsupported manifest schema: " + schemaVersion);
        if (!"1.7.10".equals(minecraftVersion)) {
            throw new IllegalArgumentException("Manifest is not for Minecraft 1.7.10");
        }
        if (release == null || release.trim().isEmpty()) throw new IllegalArgumentException("Missing release");
        if (artifacts == null || artifacts.isEmpty()) throw new IllegalArgumentException("Missing artifacts");

        Set<String> ids = new HashSet<>();
        for (Artifact artifact : artifacts) {
            artifact.validate();
            if (!ids.add(artifact.id)) throw new IllegalArgumentException("Duplicate artifact id: " + artifact.id);
        }
    }

    public boolean supportsPackVersion(String packVersion) {
        if (packVersion == null || packVersion.trim().isEmpty() || packVersions == null || packVersions.isEmpty()) {
            return true;
        }
        for (String supported : packVersions) {
            if (packVersion.equalsIgnoreCase(supported.trim())) return true;
        }
        return false;
    }

    public static final class Artifact {

        public String id;
        public String kind;
        public String url;
        public String sha256;
        public long size;
        public boolean required = true;

        private void validate() {
            if (id == null || !id.matches("[A-Za-z0-9._-]{1,80}")) {
                throw new IllegalArgumentException("Invalid artifact id: " + id);
            }
            kind = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
            if (!"translation".equals(kind)) {
                throw new IllegalArgumentException("Invalid artifact kind: " + kind + " (expected translation)");
            }
            if (url == null || url.trim().isEmpty()) throw new IllegalArgumentException("Missing URL for " + id);
            if (sha256 == null || !sha256.matches("(?i)[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid SHA-256 for " + id);
            }
            sha256 = sha256.toLowerCase(Locale.ROOT);
            if (size <= 0) throw new IllegalArgumentException("Invalid size for " + id);
        }
    }
}
