package ru.blockcode;

import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Каналы связи клиент → сервер. Программа сжимается: лимит пакета в 1.20.1 — 32 КБ. */
public final class Net {
    public static final ResourceLocation RUN = new ResourceLocation(BlockCodeMod.MOD_ID, "run");
    public static final ResourceLocation STOP = new ResourceLocation(BlockCodeMod.MOD_ID, "stop");
    public static final ResourceLocation AGENT_HERE = new ResourceLocation(BlockCodeMod.MOD_ID, "agent_here");

    public static final int MAX_COMPRESSED = 30_000;
    public static final int MAX_JSON = 1_000_000;

    private Net() {}

    public static byte[] compress(String json) {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(json.getBytes(StandardCharsets.UTF_8));
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return out.toByteArray();
    }

    public static String decompress(byte[] data) throws DataFormatException {
        Inflater inf = new Inflater();
        inf.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        try {
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) throw new DataFormatException("обрезанные данные");
                out.write(buf, 0, n);
                if (out.size() > MAX_JSON) throw new DataFormatException("слишком большая программа");
            }
        } finally {
            inf.end();
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
