package com.dreamyao.nhtranslationupdate.manifest;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class UpdateManifest {

    public int schemaVersion;
    public String minecraftVersion;
    public Map<String, PackRelease> packs;

    public void validate() {
        if (schemaVersion != 3) throw new IllegalArgumentException("Unsupported manifest schema: " + schemaVersion);
        if (!"1.7.10".equals(minecraftVersion)) {
            throw new IllegalArgumentException("Manifest is not for Minecraft 1.7.10");
        }
        if (packs == null || packs.isEmpty()) throw new IllegalArgumentException("Missing GTNH pack releases");

        for (Map.Entry<String, PackRelease> entry : packs.entrySet()) {
            String packVersion = entry.getKey();
            if (packVersion == null || !packVersion.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,79}")) {
                throw new IllegalArgumentException("Invalid GTNH pack version: " + packVersion);
            }
            if (entry.getValue() == null) throw new IllegalArgumentException("Missing release for GTNH " + packVersion);
            entry.getValue()
                .validate(packVersion);
        }
    }

    public PackRelease select(String packVersion) {
        if (packVersion == null || packs == null) return null;
        PackRelease exact = packs.get(packVersion);
        if (exact != null) return exact;
        for (Map.Entry<String, PackRelease> entry : packs.entrySet()) {
            if (packVersion.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    public static final class PackRelease {

        public String release;
        public List<Artifact> artifacts;

        private void validate(String packVersion) {
            if (release == null || release.trim()
                .isEmpty()) throw new IllegalArgumentException("Missing release for GTNH " + packVersion);
            if (artifacts == null || artifacts.isEmpty()) {
                throw new IllegalArgumentException("Missing artifacts for GTNH " + packVersion);
            }

            Set<String> ids = new HashSet<>();
            for (Artifact artifact : artifacts) {
                if (artifact == null) throw new IllegalArgumentException("Missing artifact for GTNH " + packVersion);
                artifact.validate();
                if (!ids.add(artifact.id)) throw new IllegalArgumentException("Duplicate artifact id: " + artifact.id);
            }
        }

        public Artifact translationArtifact() {
            if (artifacts == null) return null;
            for (Artifact artifact : artifacts) {
                if (artifact != null && "translation".equals(artifact.kind)) return artifact;
            }
            return null;
        }
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
            if (url == null || url.trim()
                .isEmpty()) throw new IllegalArgumentException("Missing URL for " + id);
            if (sha256 == null || !sha256.matches("(?i)[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid SHA-256 for " + id);
            }
            sha256 = sha256.toLowerCase(Locale.ROOT);
            if (size <= 0) throw new IllegalArgumentException("Invalid size for " + id);
        }
    }
}
