package com.dreamyao.nhtranslationupdate.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.dreamyao.nhtranslationupdate.util.IOUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

final class LegacyResourcePackCleanup {

    private static final String LEGACY_PACK = "NHTranslationUpdate.zip";
    private static final String OPTION = "resourcePacks:";
    private static final Gson GSON = new Gson();

    private LegacyResourcePackCleanup() {}

    static boolean run(Path gameDirectory) throws IOException {
        boolean changed = Files.deleteIfExists(
            gameDirectory.resolve("resourcepacks")
                .resolve(LEGACY_PACK));
        Path options = gameDirectory.resolve("options.txt");
        if (!Files.isRegularFile(options)) return changed;

        List<String> lines = Files.readAllLines(options, StandardCharsets.UTF_8);
        List<String> updated = new ArrayList<>(lines.size());
        boolean optionChanged = false;
        for (String line : lines) {
            if (!line.startsWith(OPTION)) {
                updated.add(line);
                continue;
            }
            try {
                JsonElement parsed = new JsonParser().parse(line.substring(OPTION.length()));
                if (!parsed.isJsonArray()) {
                    updated.add(line);
                    continue;
                }
                JsonArray kept = new JsonArray();
                for (JsonElement item : parsed.getAsJsonArray()) {
                    if (item.isJsonPrimitive() && item.getAsString()
                        .equalsIgnoreCase(LEGACY_PACK)) {
                        optionChanged = true;
                    } else {
                        kept.add(item);
                    }
                }
                updated.add(OPTION + GSON.toJson(kept));
            } catch (RuntimeException ignored) {
                updated.add(line);
            }
        }
        if (optionChanged) {
            IOUtil.atomicWriteUtf8(options, String.join("\n", updated) + "\n");
        }
        return changed || optionChanged;
    }
}
