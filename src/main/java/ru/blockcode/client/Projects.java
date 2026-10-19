package ru.blockcode.client;

import com.google.gson.JsonArray;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Проекты учеников: config/blockcode/projects/<имя>.json — их удобно собрать или раздать. */
public final class Projects {

    public static final String AUTOSAVE = "_автосохранение";

    private Projects() {}

    private static Path dir() throws IOException {
        Path d = EditorFiles.baseDir().resolve("projects");
        Files.createDirectories(d);
        return d;
    }

    static String clean(String name) {
        String n = name == null ? "" : name.replaceAll("[^\\p{L}\\p{N} _\\-]", "").trim();
        if (n.length() > 60) n = n.substring(0, 60);
        return n.isEmpty() ? "без_названия" : n;
    }

    public static void save(String name, String data) throws IOException {
        Files.writeString(dir().resolve(clean(name) + ".json"), data, StandardCharsets.UTF_8);
    }

    public static String load(String name) throws IOException {
        Path p = dir().resolve(clean(name) + ".json");
        return Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : "";
    }

    public static void delete(String name) throws IOException {
        Files.deleteIfExists(dir().resolve(clean(name) + ".json"));
    }

    public static String list() throws IOException {
        JsonArray arr = new JsonArray();
        try (Stream<Path> s = Files.list(dir())) {
            s.map(p -> p.getFileName().toString())
             .filter(n -> n.endsWith(".json"))
             .map(n -> n.substring(0, n.length() - 5))
             .filter(n -> !n.equals(AUTOSAVE))
             .sorted(String.CASE_INSENSITIVE_ORDER)
             .forEach(arr::add);
        }
        return arr.toString();
    }
}
