package ru.blockcode.client;

import com.cinemamod.mcef.MCEF;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import ru.blockcode.BlockCodeMod;
import ru.blockcode.Net;

/**
 * Мост «страница редактора → игра». На странице вызывается
 * window.cefQuery({request: JSON, onSuccess, onFailure}), сюда приходит строка JSON.
 */
public final class EditorBridge {

    private static boolean installed;

    private EditorBridge() {}

    /** Подключить обработчик до создания браузера, чтобы cefQuery появился на странице. */
    public static void install() {
        if (installed) return;
        CefMessageRouter router = CefMessageRouter.create();
        router.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
                                   boolean persistent, CefQueryCallback callback) {
                try {
                    String resp = handle(request);
                    callback.success(resp == null ? "" : resp);
                } catch (Exception e) {
                    BlockCodeMod.LOG.warn("Запрос редактора не выполнен: {}", e.toString());
                    callback.failure(0, e.getMessage() == null ? e.toString() : e.getMessage());
                }
                return true;
            }
        }, true);
        MCEF.getClient().getHandle().addMessageRouter(router);
        installed = true;
    }

    private static String handle(String request) throws Exception {
        JsonObject req = JsonParser.parseString(request).getAsJsonObject();
        String cmd = req.get("cmd").getAsString();
        Minecraft mc = Minecraft.getInstance();
        switch (cmd) {
            case "run": {
                byte[] data = Net.compress(req.get("program").toString());
                if (data.length > Net.MAX_COMPRESSED) throw new IllegalStateException("Программа слишком большая");
                int speed = req.has("speed") ? req.get("speed").getAsInt() : 3;
                requireServerMod();
                mc.execute(() -> {
                    FriendlyByteBuf buf = PacketByteBufs.create();
                    buf.writeVarInt(speed);
                    buf.writeByteArray(data);
                    ClientPlayNetworking.send(Net.RUN, buf);
                    mc.setScreen(null); // закрываем редактор, чтобы смотреть на агента
                });
                return "ok";
            }
            case "stop":
                requireServerMod();
                sendEmpty(mc, Net.STOP);
                return "ok";
            case "agentHere":
                requireServerMod();
                sendEmpty(mc, Net.AGENT_HERE);
                mc.execute(() -> mc.setScreen(null));
                return "ok";
            case "close":
                mc.execute(() -> mc.setScreen(null));
                return "ok";
            case "save":
                Projects.save(req.get("name").getAsString(), req.get("data").getAsString());
                return "ok";
            case "load":
                return Projects.load(req.get("name").getAsString());
            case "delete":
                Projects.delete(req.get("name").getAsString());
                return "ok";
            case "list":
                return Projects.list();
            default:
                throw new IllegalArgumentException("Неизвестная команда " + cmd);
        }
    }

    private static void requireServerMod() {
        if (!ClientPlayNetworking.canSend(Net.RUN)) {
            throw new IllegalStateException("На этом сервере не установлен мод BlockCode");
        }
    }

    private static void sendEmpty(Minecraft mc, ResourceLocation id) {
        mc.execute(() -> ClientPlayNetworking.send(id, PacketByteBufs.empty()));
    }
}
