package ru.blockcode.program;

import java.util.HashMap;
import java.util.Map;

/** Простой «мир» для тестов: земля на y = -1, агент стартует в (0,0,0) лицом на юг (+z). */
public class FakeWorld implements AgentWorld {
    public final Map<String, String> blocks = new HashMap<>();
    public int x, y, z, facing = 2; // 0=N(-z) 1=E(+x) 2=S(+z) 3=W(-x)
    public final StringBuilder chat = new StringBuilder();

    private static final int[][] H = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    public static String key(int x, int y, int z) { return x + "," + y + "," + z; }

    public boolean solid(int x, int y, int z) { return y < 0 || blocks.containsKey(key(x, y, z)); }

    private int[] target(Dir d) {
        int f;
        switch (d) {
            case UP: return new int[]{x, y + 1, z};
            case DOWN: return new int[]{x, y - 1, z};
            case FORWARD: f = facing; break;
            case BACK: f = (facing + 2) % 4; break;
            case LEFT: f = (facing + 3) % 4; break;
            default: f = (facing + 1) % 4;
        }
        return new int[]{x + H[f][0], y, z + H[f][1]};
    }

    @Override public boolean move(Dir d) {
        int[] t = target(d);
        if (solid(t[0], t[1], t[2])) return false;
        x = t[0]; y = t[1]; z = t[2];
        return true;
    }
    @Override public void turn(boolean left) { facing = (facing + (left ? 3 : 1)) % 4; }
    @Override public boolean place(Dir d, String id) {
        int[] t = target(d);
        if (solid(t[0], t[1], t[2])) return false;
        blocks.put(key(t[0], t[1], t[2]), id);
        return true;
    }
    @Override public boolean destroy(Dir d) {
        int[] t = target(d);
        return blocks.remove(key(t[0], t[1], t[2])) != null;
    }
    @Override public boolean detect(Dir d) { int[] t = target(d); return solid(t[0], t[1], t[2]); }
    @Override public void teleportToPlayer() { x = 0; y = 0; z = 0; facing = 2; }
    @Override public void say(String text) { chat.append(text).append('\n'); }
}
