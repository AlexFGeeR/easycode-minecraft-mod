package ru.blockcode.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Полноэкранный редактор: страница Blockly во встроенном Chromium (MCEF).
 * Браузер создаётся один раз и переживает закрытие экрана — программа не теряется.
 */
public class EditorScreen extends Screen {

    private static MCEFBrowser browser;

    public EditorScreen() {
        super(Component.literal("BlockCode"));
    }

    @Override
    protected void init() {
        super.init();
        if (!MCEF.isInitialized()) return; // MCEF ещё скачивает/запускает Chromium
        if (browser == null) {
            EditorBridge.install();
            browser = MCEF.createBrowser(BlockCodeClient.editorUrl(), false);
        }
        browser.resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        browser.setFocus(true);
    }

    @Override
    public void tick() {
        if (browser == null && MCEF.isInitialized()) init();
    }

    private int px(double guiCoord) {
        return (int) (guiCoord * minecraft.getWindow().getGuiScale());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (browser == null) {
            renderBackground(g);
            g.drawCenteredString(font, "Готовлю редактор…", width / 2, height / 2 - 10, 0xFFFFFF);
            g.drawCenteredString(font, "При первом запуске скачивается встроенный браузер — это займёт минуту.",
                    width / 2, height / 2 + 4, 0xAAAAAA);
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, browser.getRenderer().getTextureID());
        Tesselator t = Tesselator.getInstance();
        BufferBuilder b = t.getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.vertex(0, height, 0).uv(0f, 1f).color(255, 255, 255, 255).endVertex();
        b.vertex(width, height, 0).uv(1f, 1f).color(255, 255, 255, 255).endVertex();
        b.vertex(width, 0, 0).uv(1f, 0f).color(255, 255, 255, 255).endVertex();
        b.vertex(0, 0, 0).uv(0f, 0f).color(255, 255, 255, 255).endVertex();
        t.end();
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableDepthTest();
    }

    // ---------- ввод передаём в браузер ----------

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (browser == null) return super.mouseClicked(x, y, button);
        browser.sendMousePress(px(x), px(y), button);
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (browser == null) return super.mouseReleased(x, y, button);
        browser.sendMouseRelease(px(x), px(y), button);
        browser.setFocus(true);
        return true;
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (browser != null) browser.sendMouseMove(px(x), px(y));
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (browser == null) return super.mouseDragged(x, y, button, dx, dy);
        browser.sendMouseMove(px(x), px(y));
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (browser == null) return super.mouseScrolled(x, y, delta);
        browser.sendMouseWheel(px(x), px(y), delta, 0);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || browser == null) return super.keyPressed(keyCode, scanCode, modifiers);
        browser.sendKeyPress(keyCode, scanCode, modifiers);
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (browser == null) return super.keyReleased(keyCode, scanCode, modifiers);
        browser.sendKeyRelease(keyCode, scanCode, modifiers);
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (browser == null || c == 0) return super.charTyped(c, modifiers);
        browser.sendKeyTyped(c, modifiers);
        browser.setFocus(true);
        return true;
    }

    @Override
    public void removed() {
        if (browser != null) browser.setFocus(false);
        super.removed();
    }
}
