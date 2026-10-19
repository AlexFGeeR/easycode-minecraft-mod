package ru.blockcode;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Серверная часть: принимает программы от игроков и выполняет их агентами. */
public class BlockCodeMod implements ModInitializer {
    public static final String MOD_ID = "blockcode";
    public static final Logger LOG = LoggerFactory.getLogger("BlockCode");

    @Override
    public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(Net.RUN, (server, player, handler, buf, sender) -> {
            int speed = buf.readVarInt();
            byte[] data = buf.readByteArray(Net.MAX_COMPRESSED + 1024);
            server.execute(() -> startProgram(player, speed, data));
        });
        ServerPlayNetworking.registerGlobalReceiver(Net.STOP, (server, player, handler, buf, sender) ->
                server.execute(() -> {
                    if (AgentSessions.stop(player)) player.sendSystemMessage(Component.literal("■ Программа остановлена").withStyle(ChatFormatting.YELLOW));
                }));
        ServerPlayNetworking.registerGlobalReceiver(Net.AGENT_HERE, (server, player, handler, buf, sender) ->
                server.execute(() -> AgentSessions.agentFor(player).teleportToPlayer()));

        // Не даём отдать агенту-аллею предмет (иначе он начнёт его держать)
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                entity.getTags().contains(MinecraftAgent.TAG) ? InteractionResult.FAIL : InteractionResult.PASS);

        ServerTickEvents.END_SERVER_TICK.register(AgentSessions::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> AgentSessions.forget(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> AgentSessions.clear());

        LOG.info("BlockCode загружен");
    }

    private static void startProgram(ServerPlayer player, int speed, byte[] data) {
        try {
            JsonObject program = JsonParser.parseString(Net.decompress(data)).getAsJsonObject();
            AgentSessions.start(player, program, speed);
            player.sendSystemMessage(Component.literal("▶ Программа запущена").withStyle(ChatFormatting.GREEN));
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            player.sendSystemMessage(Component.literal("Не удалось запустить программу: " + msg).withStyle(ChatFormatting.RED));
            LOG.warn("Ошибка запуска программы от {}", player.getGameProfile().getName(), e);
        }
    }
}
