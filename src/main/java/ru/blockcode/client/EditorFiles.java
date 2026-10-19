package ru.blockcode.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Страница редактора лежит внутри мода. При запуске игры она копируется в
 * config/blockcode/editor и открывается во встроенном браузере как обычный файл,
 * поэтому интернет и отдельный сервер не нужны.
 */
public final class EditorFiles {

    private static final String ROOT = "/assets/blockcode/editor/";
    private static final String[] OWN_FILES = {"index.html", "editor.css", "editor.js", "blocks.js", "examples.js"};

    private EditorFiles() {}

    public static Path baseDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("blockcode");
    }

    /** @return file:// адрес index.html */
    public static String extract() throws IOException {
        Path dir = baseDir().resolve("editor");
        List<String> files = new ArrayList<>(List.of(OWN_FILES));
        try (InputStream list = EditorFiles.class.getResourceAsStream(ROOT + "blockly/files.txt")) {
            if (list == null) throw new IOException("в моде нет Blockly (blockly/files.txt) — соберите мод через gradlew build");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(list, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) if (!line.isBlank()) files.add("blockly/" + line.trim());
            }
        }
        for (String f : files) {
            try (InputStream in = EditorFiles.class.getResourceAsStream(ROOT + f)) {
                if (in == null) throw new IOException("нет файла " + f);
                Path out = dir.resolve(f);
                Files.createDirectories(out.getParent());
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return dir.resolve("index.html").toUri().toString();
    }
}
