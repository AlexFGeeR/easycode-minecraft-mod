package ru.blockcode;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.blockcode.program.ProgramError;
import ru.blockcode.program.ProgramRunner;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** У каждого игрока свой агент и не больше одной работающей программы. */
public final class AgentSessions {

    private static final Map<UUID, MinecraftAgent> AGENTS = new HashMap<>();
    private static final Map<UUID, Running> RUNNING = new HashMap<>();

    private AgentSessions() {}

    /** Скорость из редактора: сколько тиков ждать между действиями (0 = очень быстро). */
    private static final class Running {
        final ProgramRunner runner;
        final int delay;
        int wait;
        Running(ProgramRunner runner, int delay) { this.runner = runner; this.delay = delay; }
    }

    public static MinecraftAgent agentFor(ServerPlayer player) {
        MinecraftAgent a = AGENTS.computeIfAbsent(player.getUUID(), id -> new MinecraftAgent(player.getServer(), id));
        a.ensureSpawned();
        return a;
    }

    public static void start(ServerPlayer player, JsonObject program, int speed) {
        MinecraftAgent agent = agentFor(player);
        RUNNING.put(player.getUUID(), new Running(new ProgramRunner(program, agent), Math.max(0, Math.min(20, speed))));
    }

    public static boolean stop(ServerPlayer player) {
        return RUNNING.remove(player.getUUID()) != null;
    }

    public static void forget(ServerPlayer player) {
        RUNNING.remove(player.getUUID());
        // агент остаётся в мире и найдётся по метке, когда игрок вернётся
        AGENTS.remove(player.getUUID());
    }

    public static void clear() {
        RUNNING.clear();
        AGENTS.clear();
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Running>> it = RUNNING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Running> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Running r = e.getValue();
            if (player == null) { it.remove(); continue; }
            if (r.wait > 0) { r.wait--; continue; }
            try {
                agentFor(player);
                boolean more = r.runner.run(r.delay == 0 ? 20 : 1);
                r.wait = r.delay;
                if (!more) {
                    it.remove();
                    player.sendSystemMessage(Component.literal("✔ Готово! Действий агента: " + r.runner.actionsDone()).withStyle(ChatFormatting.GREEN));
                }
            } catch (ProgramError err) {
                it.remove();
                player.sendSystemMessage(Component.literal("Ошибка в программе: " + err.getMessage()).withStyle(ChatFormatting.RED));
            } catch (RuntimeException ex) {
                it.remove();
                player.sendSystemMessage(Component.literal("Внутренняя ошибка BlockCode: " + ex).withStyle(ChatFormatting.RED));
                BlockCodeMod.LOG.error("Ошибка выполнения программы", ex);
            }
        }
    }
}
