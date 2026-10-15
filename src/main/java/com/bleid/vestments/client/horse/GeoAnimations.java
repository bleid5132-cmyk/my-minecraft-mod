package com.bleid.vestments.client.horse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/** Анимации Blockbench (.animation.json): ключи с плавным сплайном Катмулла — Рома, оси пересчитаны в наши. */
@Environment(EnvType.CLIENT)
public final class GeoAnimations {
    public static final int ROT = 0, POS = 1, SCL = 2;

    public static final class Channel {
        final int bone, kind;
        final float[] t;      // время ключей
        final float[] v;      // по 3 значения на ключ

        Channel(int bone, int kind, float[] t, float[] v) {
            this.bone = bone; this.kind = kind; this.t = t; this.v = v;
        }
    }

    public static final class Anim {
        public final float length;
        public final boolean loop;
        final List<Channel> channels = new ArrayList<>();

        Anim(float length, boolean loop) {
            this.length = length; this.loop = loop;
        }

        /**
         * Добавляет позу на времени time с весом w: повороты и сдвиги — w·значение, масштаб — 1 + w·(s − 1).
         * mode override: значения анимации заменяют уже накопленные (lerp на w) — для щипания травы, свечки, прыжка.
         */
        public void apply(float time, float w, boolean override, Pose p) {
            if (w <= 0f) return;
            float t = loop ? (float) (time - Math.floor(time / length) * length) : Math.min(Math.max(time, 0f), length);
            float[] tmp = new float[3];
            for (Channel c : channels) {
                sample(c, t, tmp);
                float[] dst = c.kind == ROT ? p.rot : c.kind == POS ? p.pos : p.scl;
                int o = c.bone * 3;
                for (int k = 0; k < 3; k++) {
                    if (c.kind == SCL) dst[o + k] = override ? dst[o + k] + (tmp[k] - dst[o + k]) * w : dst[o + k] + (tmp[k] - 1f) * w;
                    else dst[o + k] = override ? dst[o + k] + (tmp[k] - dst[o + k]) * w : dst[o + k] + tmp[k] * w;
                }
            }
        }

        private void sample(Channel c, float t, float[] out) {
            int n = c.t.length;
            if (n == 1 || t <= c.t[0]) { copy(c, 0, out); return; }
            if (t >= c.t[n - 1]) { copy(c, n - 1, out); return; }
            int i = 0;
            while (i < n - 2 && t > c.t[i + 1]) i++;
            float t0 = c.t[i], t1 = c.t[i + 1];
            float u = t1 > t0 ? (t - t0) / (t1 - t0) : 0f;
            int i0 = i > 0 ? i - 1 : (loop && n > 2 ? n - 2 : 0);
            int i3 = i + 2 < n ? i + 2 : (loop && n > 2 ? 1 : n - 1);
            for (int k = 0; k < 3; k++) {
                float p0 = c.v[i0 * 3 + k], p1 = c.v[i * 3 + k], p2 = c.v[(i + 1) * 3 + k], p3 = c.v[i3 * 3 + k];
                out[k] = 0.5f * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u);
            }
        }

        private static void copy(Channel c, int i, float[] out) {
            out[0] = c.v[i * 3]; out[1] = c.v[i * 3 + 1]; out[2] = c.v[i * 3 + 2];
        }
    }

    /** Поза: на кость по 3 значения поворота (градусы), сдвига (пиксели) и масштаба. */
    public static final class Pose {
        public final float[] rot, pos, scl;

        public Pose(int bones) {
            rot = new float[bones * 3]; pos = new float[bones * 3]; scl = new float[bones * 3];
            java.util.Arrays.fill(scl, 1f);
        }
    }

    public final Map<String, Anim> anims = new HashMap<>();

    public Anim get(String name) {
        return anims.get(name);
    }

    public static GeoAnimations load(Identifier file, GeoModel model, String prefix) {
        GeoAnimations g = new GeoAnimations();
        try (Reader r = new InputStreamReader(MinecraftClient.getInstance().getResourceManager().open(file), StandardCharsets.UTF_8)) {
            JsonObject all = JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject("animations");
            for (Map.Entry<String, JsonElement> e : all.entrySet()) {
                JsonObject a = e.getValue().getAsJsonObject();
                boolean loop = a.has("loop") && a.get("loop").getAsBoolean();
                Anim anim = new Anim(a.get("animation_length").getAsFloat(), loop);
                if (a.has("bones")) for (Map.Entry<String, JsonElement> be : a.getAsJsonObject("bones").entrySet()) {
                    int bi = model.index(be.getKey());
                    if (bi < 0) continue;
                    JsonObject chs = be.getValue().getAsJsonObject();
                    for (String ch : new String[] { "rotation", "position", "scale" }) {
                        if (!chs.has(ch)) continue;
                        int kind = ch.equals("rotation") ? ROT : ch.equals("position") ? POS : SCL;
                        anim.channels.add(channel(bi, kind, chs.get(ch)));
                    }
                }
                String name = e.getKey().startsWith(prefix) ? e.getKey().substring(prefix.length()) : e.getKey();
                g.anims.put(name, anim);
            }
        } catch (Exception e) {
            com.bleid.vestments.Vestments.LOGGER.error("Не удалось загрузить анимации {}", file, e);
        }
        return g;
    }

    private static float[] vec(JsonElement el) {
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            el = o.has("vector") ? o.get("vector") : o.has("post") ? o.get("post") : o.get("pre");
        }
        if (el.isJsonPrimitive()) { float f = el.getAsFloat(); return new float[] { f, f, f }; }
        var a = el.getAsJsonArray();
        return new float[] { num(a.get(0)), num(a.get(1)), num(a.get(2)) };
    }

    private static float num(JsonElement e) {
        try { return e.getAsFloat(); } catch (Exception ex) { return 0f; }
    }

    private static Channel channel(int bone, int kind, JsonElement el) {
        List<float[]> keys = new ArrayList<>();
        if (el.isJsonObject() && !el.getAsJsonObject().has("vector")) {
            for (Map.Entry<String, JsonElement> k : el.getAsJsonObject().entrySet()) {
                float[] v = vec(k.getValue());
                keys.add(new float[] { Float.parseFloat(k.getKey()), v[0], v[1], v[2] });
            }
        } else {
            float[] v = vec(el);
            keys.add(new float[] { 0, v[0], v[1], v[2] });
        }
        keys.sort((a, b) -> Float.compare(a[0], b[0]));
        float[] t = new float[keys.size()], v = new float[keys.size() * 3];
        for (int i = 0; i < keys.size(); i++) {
            float[] k = keys.get(i);
            t[i] = k[0];
            // из осей Bedrock в наши: поворот (x, −y, −z), сдвиг (x, y, −z)
            if (kind == ROT) { v[i * 3] = k[1]; v[i * 3 + 1] = -k[2]; v[i * 3 + 2] = -k[3]; }
            else if (kind == POS) { v[i * 3] = k[1]; v[i * 3 + 1] = k[2]; v[i * 3 + 2] = -k[3]; }
            else { v[i * 3] = k[1]; v[i * 3 + 1] = k[2]; v[i * 3 + 2] = k[3]; }
        }
        return new Channel(bone, kind, t, v);
    }
}
