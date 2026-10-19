package ru.blockcode.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ru.blockcode.BlockCodeMod;

/** Клиент: клавиша C открывает редактор блоков прямо в игре. */
public class BlockCodeClient implements ClientModInitializer {

    public static KeyMapping openKey;
    private static String editorUrl;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.blockcode.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "category.blockcode"));

        try {
            editorUrl = EditorFiles.extract();
        } catch (Exception e) {
            BlockCodeMod.LOG.error("Не удалось распаковать файлы редактора", e);
        }

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) openEditor(mc);
        });
    }

    public static String editorUrl() { return editorUrl; }

    private static void openEditor(Minecraft mc) {
        if (mc.player == null || mc.screen != null) return;
        if (!FabricLoader.getInstance().isModLoaded("mcef")) {
            mc.player.displayClientMessage(Component.literal(
                    "Для редактора BlockCode нужен мод MCEF — установите его вместе с BlockCode.").withStyle(ChatFormatting.RED), false);
            return;
        }
        if (editorUrl == null) {
            mc.player.displayClientMessage(Component.literal(
                    "Файлы редактора не найдены — подробности в логе игры.").withStyle(ChatFormatting.RED), false);
            return;
        }
        mc.setScreen(new EditorScreen());
    }
}
