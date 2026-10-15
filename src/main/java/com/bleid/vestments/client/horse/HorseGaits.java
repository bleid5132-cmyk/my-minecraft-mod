package com.bleid.vestments.client.horse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/** Аллюры (шаг, рысь, галоп-кентер, карьер): по 24 кадра на цикл; кадр — углы частей и подъём корпуса. */
@Environment(EnvType.CLIENT)
final class HorseGaits {
    static final String[] ORDER = { "walk", "trot", "canter", "gallop" };
    static final Identifier FILE = new Identifier("vestments", "animations/horse_gaits.json");

    /** [аллюр][кадр][часть*3 + ось] */
    final float[][][] rot = new float[4][][];
    final float[][] bob = new float[4][];
    final float[] stride = new float[4];
    int frames;

    static HorseGaits load(VoxelIndex idx) {
        HorseGaits g = new HorseGaits();
        try (Reader r = new InputStreamReader(MinecraftClient.getInstance().getResourceManager().open(FILE), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (int a = 0; a < 4; a++) {
                JsonObject gait = root.getAsJsonObject(ORDER[a]);
                g.stride[a] = gait.get("stride").getAsFloat();
                JsonArray fr = gait.getAsJsonArray("frames");
                g.frames = fr.size();
                g.rot[a] = new float[g.frames][idx.count() * 3];
                g.bob[a] = new float[g.frames];
                for (int f = 0; f < g.frames; f++) {
                    JsonObject o = fr.get(f).getAsJsonObject();
                    g.bob[a][f] = o.get("bob").getAsFloat();
                    for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("rot").entrySet()) {
                        int i = idx.index(e.getKey());
                        if (i < 0) continue;
                        JsonArray v = e.getValue().getAsJsonArray();
                        for (int c = 0; c < 3; c++) g.rot[a][f][i * 3 + c] = v.get(c).getAsFloat();
                    }
                }
            }
        } catch (Exception e) {
            com.bleid.vestments.Vestments.LOGGER.error("Не удалось загрузить аллюры лошади", e);
            g.frames = 0;
        }
        return g;
    }

    interface VoxelIndex {
        int index(String name);

        int count();
    }

    /** Добавляет в out[часть*3+ось] позу аллюра a на фазе phase (0..1) с весом w; возвращает подъём корпуса·w. */
    float sample(int a, float phase, float w, float[] out) {
        if (frames == 0 || w <= 0f) return 0f;
        float p = (phase - (float) Math.floor(phase)) * frames;
        int f0 = (int) p % frames, f1 = (f0 + 1) % frames;
        float t = p - (int) p;
        float[] r0 = rot[a][f0], r1 = rot[a][f1];
        for (int i = 0; i < out.length && i < r0.length; i++) out[i] += (r0[i] + (r1[i] - r0[i]) * t) * w;
        return (bob[a][f0] + (bob[a][f1] - bob[a][f0]) * t) * w;
    }
}
