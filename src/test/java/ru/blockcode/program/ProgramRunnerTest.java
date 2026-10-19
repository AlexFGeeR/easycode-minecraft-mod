package ru.blockcode.program;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class ProgramRunnerTest {

    static JsonObject load(String name) throws Exception {
        try (var in = ProgramRunnerTest.class.getResourceAsStream("/" + name)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    static FakeWorld runAll(JsonObject program) {
        FakeWorld w = new FakeWorld();
        ProgramRunner r = new ProgramRunner(program, w);
        int guard = 0;
        while (r.run(1)) if (++guard > 1_000_000) fail("не закончилась");
        return w;
    }

    /** Пример «домик 5×5» из редактора: стены высотой 4 с дверным проёмом и плоская крыша. */
    @Test void houseExample() throws Exception {
        FakeWorld w = runAll(load("house.json"));
        // Агент сначала отходит на 2 вперёд (на юг): угол дома в (0, 0, 2)
        int walls = 0, roof = 0;
        for (int dx = 0; dx <= 4; dx++) for (int dz = 2; dz <= 6; dz++) {
            boolean edge = dx == 0 || dx == 4 || dz == 2 || dz == 6;
            for (int y = 0; y < 4; y++) {
                boolean door = (y < 2) && dx == 0 && dz == 4; // проём на первой стене, 3-я клетка
                boolean has = w.blocks.containsKey(FakeWorld.key(dx, y, dz));
                assertEquals(edge && !door, has, "стена " + dx + "," + y + "," + dz);
                if (has) walls++;
            }
            assertTrue(w.blocks.containsKey(FakeWorld.key(dx, 4, dz)), "крыша " + dx + "," + dz);
            roof++;
        }
        assertEquals(16 * 4 - 2, walls);
        assertEquals(25, roof);
        assertEquals(walls + roof, w.blocks.size());
    }

    @Test void infiniteLoopIsStopped() {
        JsonObject p = JsonParser.parseString(
            "{\"main\":[{\"t\":\"while\",\"cond\":{\"t\":\"bool\",\"v\":true},\"body\":[{\"t\":\"set\",\"var\":\"a\",\"v\":1}]}]}")
            .getAsJsonObject();
        ProgramRunner r = new ProgramRunner(p, new FakeWorld());
        ProgramError e = assertThrows(ProgramError.class, () -> { while (r.run(1)) { } });
        assertTrue(e.getMessage().contains("бесконечный"));
    }

    @Test void recursionIsLimited() {
        JsonObject p = JsonParser.parseString(
            "{\"main\":[{\"t\":\"call\",\"name\":\"f\"}],\"procs\":{\"f\":[{\"t\":\"call\",\"name\":\"f\"}]}}")
            .getAsJsonObject();
        ProgramRunner r = new ProgramRunner(p, new FakeWorld());
        assertThrows(ProgramError.class, () -> { while (r.run(1)) { } });
    }

    @Test void forCountsDownAndSays() {
        JsonObject p = JsonParser.parseString(
            "{\"main\":[{\"t\":\"for\",\"var\":\"i\",\"from\":{\"t\":\"num\",\"v\":3},\"to\":{\"t\":\"num\",\"v\":1}," +
            "\"by\":{\"t\":\"num\",\"v\":1},\"body\":[{\"t\":\"say\",\"v\":{\"t\":\"var\",\"name\":\"i\"}}]}]}")
            .getAsJsonObject();
        FakeWorld w = runAll(p);
        assertEquals("3\n2\n1\n", w.chat.toString());
    }

    @Test void oneActionPerRunCall() throws Exception {
        FakeWorld w = new FakeWorld();
        ProgramRunner r = new ProgramRunner(load("house.json"), w);
        r.run(1);
        assertEquals(1, r.actionsDone());
    }
}
