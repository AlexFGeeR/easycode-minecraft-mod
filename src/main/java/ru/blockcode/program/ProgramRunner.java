package ru.blockcode.program;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Пошаговый интерпретатор программы из блоков.
 *
 * Программа приходит из редактора как JSON-дерево:
 * <pre>{ "main": [операторы...], "procs": { "имя": [операторы...] } }</pre>
 * Интерпретатор не использует рекурсию для операторов: состояние хранится в стеке кадров,
 * поэтому выполнение можно приостановить после любого действия агента и продолжить
 * на следующем тике — так дети видят, как агент строит блок за блоком.
 */
public class ProgramRunner {

    public static final int MAX_ACTIONS = 20_000;       // действий агента за одну программу
    public static final long MAX_TOTAL_OPS = 5_000_000; // всего шагов интерпретатора
    public static final int OPS_PER_TICK = 50_000;      // чтобы бесконечный цикл не подвесил сервер
    public static final int MAX_CALL_DEPTH = 200;

    private final AgentWorld world;
    private final Map<String, List<JsonObject>> procs = new HashMap<>();
    private final Map<String, Object> vars = new HashMap<>();
    private final Deque<Frame> stack = new ArrayDeque<>();
    private final Random random = new Random();

    private int actions;
    private long totalOps;
    private boolean finished;

    public ProgramRunner(JsonObject program, AgentWorld world) {
        this.world = world;
        if (program == null || !program.has("main")) throw new ProgramError("Пустая программа");
        if (program.has("procs") && program.get("procs").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : program.getAsJsonObject("procs").entrySet()) {
                procs.put(e.getKey(), list(e.getValue()));
            }
        }
        stack.push(new Frame(list(program.get("main")), 0));
    }

    public boolean isFinished() { return finished; }
    public int actionsDone() { return actions; }

    /**
     * Выполнять программу, пока не будет сделано {@code maxActions} действий агента
     * или не закончится бюджет шагов на этот тик.
     * @return true, если программа ещё не закончилась
     */
    public boolean run(int maxActions) {
        int done = 0;
        int ops = 0;
        while (!finished && done < maxActions && ops < OPS_PER_TICK) {
            ops++;
            if (++totalOps > MAX_TOTAL_OPS) {
                throw new ProgramError("Программа работает слишком долго — может быть, где-то бесконечный цикл?");
            }
            if (step()) done++;
        }
        return !finished;
    }

    // ------------------------------------------------------------------ операторы

    /** Выполнить один оператор. true — агент сделал действие. */
    private boolean step() {
        Frame f = stack.peek();
        if (f == null) { finished = true; return false; }
        if (f.pc >= f.body.size()) {
            if (f.loopAgain()) f.pc = 0; else stack.pop();
            return false;
        }
        JsonObject s = f.body.get(f.pc++);
        String t = str(s, "t");
        switch (t) {
            case "move": {
                int n = (int) Math.floor(num(eval(s.get("n"))));
                if (n <= 0) return false;
                // каждый шаг — отдельное действие: вставляем «повторить n раз: шаг»
                JsonObject one = new JsonObject();
                one.addProperty("t", "step1");
                one.addProperty("dir", str(s, "dir"));
                List<JsonObject> body = new ArrayList<>();
                body.add(one);
                push(new RepeatFrame(body, n));
                return false;
            }
            case "step1":
                world.move(dir(s));
                return action();
            case "turn":
                world.turn("LEFT".equals(str(s, "dir")));
                return action();
            case "place":
                world.place(dir(s), str(s, "block"));
                return action();
            case "destroy":
                world.destroy(dir(s));
                return action();
            case "tp":
                world.teleportToPlayer();
                return action();
            case "say":
                world.say(text(eval(s.get("v"))));
                return false;
            case "repeat": {
                int n = (int) Math.floor(num(eval(s.get("n"))));
                List<JsonObject> body = list(s.get("body"));
                if (n > 0 && !body.isEmpty()) push(new RepeatFrame(body, n));
                return false;
            }
            case "for": {
                String var = str(s, "var");
                double from = num(eval(s.get("from")));
                double to = num(eval(s.get("to")));
                double by = Math.abs(num(eval(s.get("by"))));
                if (by == 0) by = 1;
                if (from > to) by = -by;
                vars.put(var, from);
                List<JsonObject> body = list(s.get("body"));
                if (!body.isEmpty()) push(new ForFrame(body, var, to, by));
                else vars.put(var, to);
                return false;
            }
            case "while": {
                boolean until = s.has("until") && s.get("until").getAsBoolean();
                JsonElement cond = s.get("cond");
                List<JsonObject> body = list(s.get("body"));
                if (truthy(eval(cond)) != until) push(new WhileFrame(body, cond, until));
                return false;
            }
            case "if": {
                for (JsonElement b : arr(s.get("branches"))) {
                    JsonObject br = b.getAsJsonObject();
                    if (truthy(eval(br.get("cond")))) {
                        push(new Frame(list(br.get("body")), 0));
                        return false;
                    }
                }
                if (s.has("else") && s.get("else").isJsonArray()) push(new Frame(list(s.get("else")), 0));
                return false;
            }
            case "set":
                vars.put(str(s, "var"), eval(s.get("v")));
                return false;
            case "change": {
                String var = str(s, "var");
                vars.put(var, num(vars.getOrDefault(var, 0.0)) + num(eval(s.get("v"))));
                return false;
            }
            case "call": {
                String name = str(s, "name");
                List<JsonObject> body = procs.get(name);
                if (body == null) throw new ProgramError("Нет функции «" + name + "»");
                push(new Frame(body, 0));
                return false;
            }
            default:
                throw new ProgramError("Неизвестный блок: " + t);
        }
    }

    private boolean action() {
        if (++actions > MAX_ACTIONS) throw new ProgramError("Слишком много действий агента (больше " + MAX_ACTIONS + ")");
        return true;
    }

    private void push(Frame f) {
        if (stack.size() >= MAX_CALL_DEPTH) throw new ProgramError("Слишком глубокая вложенность — функция вызывает сама себя?");
        stack.push(f);
    }

    // ------------------------------------------------------------------ выражения

    private Object eval(JsonElement el) {
        if (el == null || el.isJsonNull()) return 0.0;
        if (el.isJsonPrimitive()) {
            if (el.getAsJsonPrimitive().isNumber()) return el.getAsDouble();
            if (el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean();
            return el.getAsString();
        }
        JsonObject e = el.getAsJsonObject();
        switch (str(e, "t")) {
            case "num":  return e.get("v").getAsDouble();
            case "bool": return e.get("v").getAsBoolean();
            case "text": return e.get("v").getAsString();
            case "var":  return vars.getOrDefault(str(e, "name"), 0.0);
            case "not":  return !truthy(eval(e.get("a")));
            case "detect": return world.detect(dir(e));
            case "rand": {
                int a = (int) Math.round(num(eval(e.get("from"))));
                int b = (int) Math.round(num(eval(e.get("to"))));
                if (a > b) { int x = a; a = b; b = x; }
                return (double) (a + random.nextInt(b - a + 1));
            }
            case "arith": {
                double a = num(eval(e.get("a"))), b = num(eval(e.get("b")));
                switch (str(e, "op")) {
                    case "ADD": return a + b;
                    case "MINUS": return a - b;
                    case "MULTIPLY": return a * b;
                    case "DIVIDE":
                        if (b == 0) throw new ProgramError("Деление на ноль");
                        return a / b;
                    case "POWER": return Math.pow(a, b);
                    case "MOD":
                        if (b == 0) throw new ProgramError("Деление на ноль");
                        return a - b * Math.floor(a / b); // как % в Python
                    default: throw new ProgramError("Неизвестная операция");
                }
            }
            case "cmp": {
                Object a = eval(e.get("a")), b = eval(e.get("b"));
                String op = str(e, "op");
                if (op.equals("EQ") || op.equals("NEQ")) {
                    boolean eq = (a instanceof String || b instanceof String)
                            ? text(a).equals(text(b))
                            : num(a) == num(b);
                    return op.equals("EQ") == eq;
                }
                double x = num(a), y = num(b);
                switch (op) {
                    case "LT": return x < y;
                    case "LTE": return x <= y;
                    case "GT": return x > y;
                    case "GTE": return x >= y;
                    default: throw new ProgramError("Неизвестное сравнение");
                }
            }
            case "logic": {
                boolean and = "AND".equals(str(e, "op"));
                boolean a = truthy(eval(e.get("a")));
                if (and && !a) return false;
                if (!and && a) return true;
                return truthy(eval(e.get("b")));
            }
            default:
                throw new ProgramError("Неизвестный блок-значение: " + str(e, "t"));
        }
    }

    // ------------------------------------------------------------------ кадры стека

    private static class Frame {
        final List<JsonObject> body;
        int pc;
        Frame(List<JsonObject> body, int pc) { this.body = body; this.pc = pc; }
        boolean loopAgain() { return false; }
    }

    private static final class RepeatFrame extends Frame {
        int left;
        RepeatFrame(List<JsonObject> body, int times) { super(body, 0); this.left = times; }
        @Override boolean loopAgain() { return --left > 0; }
    }

    private final class ForFrame extends Frame {
        final String var; final double to, by;
        ForFrame(List<JsonObject> body, String var, double to, double by) {
            super(body, 0); this.var = var; this.to = to; this.by = by;
            // пустой диапазон (например, от 1 до 1 с шагом 1 — выполнится один раз, как в Blockly)
        }
        @Override boolean loopAgain() {
            double next = num(vars.getOrDefault(var, 0.0)) + by;
            if (by > 0 ? next <= to : next >= to) { vars.put(var, next); return true; }
            return false;
        }
    }

    private final class WhileFrame extends Frame {
        final JsonElement cond; final boolean until;
        WhileFrame(List<JsonObject> body, JsonElement cond, boolean until) { super(body, 0); this.cond = cond; this.until = until; }
        @Override boolean loopAgain() { return truthy(eval(cond)) != until; }
    }

    // ------------------------------------------------------------------ helpers

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static AgentWorld.Dir dir(JsonObject o) {
        try { return AgentWorld.Dir.valueOf(str(o, "dir")); }
        catch (IllegalArgumentException ex) { throw new ProgramError("Неизвестное направление: " + str(o, "dir")); }
    }

    private static JsonArray arr(JsonElement e) {
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private static List<JsonObject> list(JsonElement e) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement x : arr(e)) if (x.isJsonObject()) out.add(x.getAsJsonObject());
        return out;
    }

    static double num(Object v) {
        if (v instanceof Double) return (Double) v;
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof Boolean) return ((Boolean) v) ? 1 : 0;
        try { return Double.parseDouble(String.valueOf(v).trim()); }
        catch (NumberFormatException ex) { throw new ProgramError("«" + v + "» — это не число"); }
    }

    static boolean truthy(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        return v != null && !String.valueOf(v).isEmpty();
    }

    static String text(Object v) {
        if (v instanceof Double) {
            double d = (Double) v;
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        }
        if (v instanceof Boolean) return ((Boolean) v) ? "True" : "False";
        return String.valueOf(v);
    }
}
