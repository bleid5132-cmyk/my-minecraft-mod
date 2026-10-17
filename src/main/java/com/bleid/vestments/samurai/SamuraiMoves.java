package com.bleid.vestments.samurai;

import com.bleid.vestments.paladin.combat.Clip;
import com.bleid.vestments.paladin.combat.Moveset;
import com.bleid.vestments.paladin.combat.Movesets;
import com.bleid.vestments.paladin.combat.Strike;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Приёмы самурая из assets/vestments/samurai/moves.json — того же файла, что строит 3D-превью.
 * Для каждой катаны собирается свой Moveset (серия, разбег, прыжок, особый приём, выхватывание, ното)
 * и копии общих приёмов класса (иай, парирование, шаг ветра, медитация, буря лепестков).
 */
public final class SamuraiMoves {
    /** Ключевая поза (пиксели модели: x — влево, y — вниз, z — назад). */
    public static final class Key {
        public float u;
        public boolean hold;
        public float[] hand, dir, normal, sh, osh, off, knees, legs;
        public float body, head;
        /** Из «Мастерской анимаций»: куда смотрят локти, поворот и отвод ног, сдвиг катаны от кисти. */
        public float[] poleR = { -0.4f, 0.35f, 1f }, poleL = { 0.4f, 0.35f, 1f }, legYaw = { 0, 0 }, legRoll = { 0, 0 }, katOff = { 0, 0, 0 };
    }

    /** Ключ вида от первого лица (экран: x, y от −1 до 1, глубина в пикселях). */
    public static final class FpKey {
        public float u;
        public boolean hold;
        public float[] hand, tip, off, edge;
    }

    public static final Map<String, List<FpKey>> FP = new HashMap<>();
    /** Положение катаны в кисти (пиксели вдоль осей клинка), общее для всех приёмов. */
    public static float[] grip = { 0, 0, 0 };

    public static final class KatanaSpec {
        public String id;
        public int rank;
        public float dmg, speed, reach;
        public String skill;
        public int cd, dur;
        public float[] trail, core;
    }

    public static final Map<String, List<Key>> KEYSETS = new HashMap<>();
    public static final Map<String, KatanaSpec> KATANAS = new LinkedHashMap<>();
    /** Приёмы по катане: id катаны → имя приёма → приём. */
    private static final Map<String, Map<String, Clip>> BY_KATANA = new HashMap<>();
    public static float gripGap = 4f;
    public static float[] sheathMouth, sheathDir, sheathNormal;
    private static JsonObject data;

    private SamuraiMoves() { }

    public static synchronized void load() {
        if (data != null) return;
        try (Reader r = new InputStreamReader(SamuraiMoves.class.getResourceAsStream("/assets/vestments/samurai/moves.json"),
                StandardCharsets.UTF_8)) {
            data = JsonParser.parseReader(r).getAsJsonObject();
        } catch (Exception e) {
            throw new RuntimeException("samurai moves.json", e);
        }
        gripGap = data.get("gripGap").getAsFloat();
        JsonObject sh = data.getAsJsonObject("sheath");
        sheathMouth = vec(sh.get("mouth"));
        sheathDir = vec(sh.get("dir"));
        sheathNormal = vec(sh.get("normal"));
        if (data.has("grip")) {
            JsonObject g = data.getAsJsonObject("grip");
            grip = new float[] { g.get("x").getAsFloat(), g.get("y").getAsFloat(), g.get("z").getAsFloat() };
        }
        if (data.has("fp") && data.getAsJsonObject("fp").has("keys")) {
            JsonObject fk = data.getAsJsonObject("fp").getAsJsonObject("keys");
            for (String name : fk.keySet()) {
                List<FpKey> list = new ArrayList<>();
                for (JsonElement el : fk.getAsJsonArray(name)) {
                    JsonObject o = el.getAsJsonObject();
                    FpKey k = new FpKey();
                    k.u = o.get("u").getAsFloat();
                    k.hold = o.get("hold").getAsBoolean();
                    k.hand = vec(o.get("hand"));
                    k.tip = vec(o.get("tip"));
                    k.off = !o.has("off") || o.get("off").isJsonNull() ? null : vec(o.get("off"));
                    k.edge = vec(o.get("edge"));
                    list.add(k);
                }
                if (list.size() >= 2) FP.put(name, list);
            }
        }
        JsonObject ks = data.getAsJsonObject("keysets");
        for (String name : ks.keySet()) {
            List<Key> list = new ArrayList<>();
            for (JsonElement el : ks.getAsJsonArray(name)) {
                JsonObject o = el.getAsJsonObject();
                Key k = new Key();
                k.u = o.get("u").getAsFloat();
                k.hold = o.get("hold").getAsBoolean();
                k.hand = vec(o.get("hand")); k.dir = vec(o.get("dir")); k.normal = vec(o.get("normal"));
                k.sh = vec(o.get("sh")); k.osh = vec(o.get("osh")); k.off = vec(o.get("off"));
                k.knees = vec(o.get("knees")); k.legs = vec(o.get("legs"));
                k.body = o.get("body").getAsFloat(); k.head = o.get("head").getAsFloat();
                if (o.has("poleR")) k.poleR = vec(o.get("poleR"));
                if (o.has("poleL")) k.poleL = vec(o.get("poleL"));
                if (o.has("legYaw")) k.legYaw = vec(o.get("legYaw"));
                if (o.has("legRoll")) k.legRoll = vec(o.get("legRoll"));
                if (o.has("katOff")) k.katOff = vec(o.get("katOff"));
                list.add(k);
            }
            KEYSETS.put(name, list);
        }
        JsonObject kat = data.getAsJsonObject("katanas");
        for (String id : kat.keySet()) {
            JsonObject o = kat.getAsJsonObject(id);
            KatanaSpec s = new KatanaSpec();
            s.id = id;
            s.rank = o.get("rank").getAsInt();
            s.dmg = o.get("dmg").getAsFloat();
            s.speed = o.get("speed").getAsFloat();
            s.reach = o.get("reach").getAsFloat();
            s.skill = o.get("skill").isJsonNull() ? null : o.get("skill").getAsString();
            s.cd = o.get("cd").getAsInt();
            s.dur = o.get("dur").getAsInt();
            s.trail = vec(o.get("trail"));
            s.core = vec(o.get("core"));
            KATANAS.put(id, s);
        }
    }

    /** Собрать приёмы всех катан (после load). */
    public static void buildMovesets() {
        load();
        JsonObject clips = data.getAsJsonObject("clips");
        for (KatanaSpec k : KATANAS.values()) {
            Map<String, Clip> mine = new HashMap<>();
            for (String name : clips.keySet()) {
                Clip c = clip(k.id + "/" + name, clips.getAsJsonObject(name));
                mine.put(name, c);
                Movesets.CLIPS.put(c.id, c);
            }
            BY_KATANA.put(k.id, mine);
            Moveset m = new Moveset(k.id, Math.max(0, (k.rank - 1) / 2), k.reach)
                    .trail(k.trail, k.core, 0.55f + 0.03f * k.rank, 0.1f + 0.006f * k.rank, 0.9f + 0.03f * k.rank, k.rank >= 6);
            JsonArray combo = data.getAsJsonArray("combo");
            m.combo = new Clip[combo.size()];
            for (int i = 0; i < combo.size(); i++) m.combo[i] = mine.get(combo.get(i).getAsString());
            m.dash = mine.get("dash");
            m.air = mine.get("air");
            m.skill = k.skill == null ? null : mine.get(k.skill);
            m.draw = mine.get("draw");
            m.sheathe = mine.get("sheathe");
            Movesets.BY_SWORD.put(k.id, m);
        }
    }

    public static Clip clip(String katanaId, String name) {
        Map<String, Clip> m = BY_KATANA.get(katanaId);
        return m == null ? null : m.get(name);
    }

    /** Имя приёма без катаны: "tetsu/iai" → "iai". */
    public static String nameOf(Clip c) {
        int i = c.id.indexOf('/');
        return i < 0 ? c.id : c.id.substring(i + 1);
    }

    private static Clip clip(String id, JsonObject o) {
        Clip c = new Clip(id, o.get("length").getAsFloat(), o.get("trans").getAsFloat());
        c.katana = true;
        c.skill = o.get("skill").getAsBoolean();
        c.visualOnly = o.get("visual").getAsBoolean();
        c.lockMove = o.get("lockMove").getAsBoolean();
        c.loop = o.get("loop").getAsBoolean();
        for (JsonElement el : o.getAsJsonArray("strikes")) {
            JsonObject s = el.getAsJsonObject();
            Strike k = new Strike(f(s, "antic", 0), f(s, "pre", 0), f(s, "contact", 0), f(s, "rec", 0), f(s, "roll", 0),
                    f(s, "a0", 0), f(s, "a1", 0));
            if (s.has("reach0")) k.reach(f(s, "reach0", -1), f(s, "reach1", -1));
            k.dmg(f(s, "dmg", 1)).kb(f(s, "kb", 0.4f)).width(f(s, "width", 0.45f)).targets((int) f(s, "targets", 3))
                    .hitstop((int) f(s, "hitstop", 0)).shake(f(s, "shake", 0));
            if (s.has("event")) k.event(s.get("event").getAsString());
            if (s.has("heavy") && s.get("heavy").getAsBoolean()) k.heavy();
            if (s.has("smooth") && s.get("smooth").getAsBoolean()) k.smooth();
            k.edge = (int) f(s, "edge", 0);
            k.two();
            c.strike(k);
        }
        for (JsonElement el : o.getAsJsonArray("steps")) {
            JsonArray a = el.getAsJsonArray();
            c.step(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
        }
        for (JsonElement el : o.getAsJsonArray("warps")) {
            JsonArray a = el.getAsJsonArray();
            c.warps.add(new float[] { a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat() });
        }
        for (JsonElement el : o.getAsJsonArray("fx")) {
            JsonArray a = el.getAsJsonArray();
            c.fx.add(new Object[] { a.get(0).getAsFloat(), a.get(1).getAsString(), a.get(2).getAsJsonObject() });
        }
        JsonObject tr = o.getAsJsonObject("tracks");
        track(tr, "lean", c.lean);
        track(tr, "spin", c.spin);
        track(tr, "lift", c.lift);
        track(tr, "stance", c.stance);
        track(tr, "head", c.head);
        if (o.has("keys")) {
            c.keys = o.get("keys").getAsString();
            c.sheath = true;                 // руки по ключам, след и звуки — свои
        }
        if (o.has("grab")) {
            JsonArray g = o.getAsJsonArray("grab");
            c.grab = new float[][] { { g.get(0).getAsFloat(), g.get(1).getAsFloat() } };
        }
        String n = id.substring(id.indexOf('/') + 1);
        c.endsSheathed = n.equals("sheathe") || n.equals("ult_end") || n.equals("meditate");
        if (n.equals("sheathe")) c.reverse = true;       // как у паладина: меч уходит в ножны
        return c;
    }

    private static void track(JsonObject tr, String name, Clip.Track t) {
        if (!tr.has(name)) return;
        for (JsonElement el : tr.getAsJsonArray(name)) {
            JsonArray a = el.getAsJsonArray();
            t.key(a.get(0).getAsFloat(), a.get(1).getAsFloat());
        }
    }

    private static float f(JsonObject o, String k, float def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsFloat() : def;
    }

    private static float[] vec(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        float[] r = new float[a.size()];
        for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsFloat();
        return r;
    }

    // ---------------------------------------------------------------- ключевые позы

    /** Поза набора в долю u: сплайн Эрмита между ключами (как DrawPose), в ключах «hold» — остановка. */
    public static Key keyAt(String set, float u) {
        List<Key> ks = KEYSETS.get(set);
        u = Math.max(0f, Math.min(1f, u));
        int i = 0;
        while (i < ks.size() - 2 && u > ks.get(i + 1).u) i++;
        Key a = ks.get(i), b = ks.get(i + 1);
        Key pa = i > 0 ? ks.get(i - 1) : null, nb = i + 2 < ks.size() ? ks.get(i + 2) : null;
        float dt = b.u - a.u, x = (u - a.u) / Math.max(1e-4f, dt), x2 = x * x, x3 = x2 * x;
        float h00 = 2 * x3 - 3 * x2 + 1, h10 = x3 - 2 * x2 + x, h01 = -2 * x3 + 3 * x2, h11 = x3 - x2;
        Key o = new Key();
        o.hand = herm(pa, a, b, nb, k -> k.hand, h00, h10, h01, h11, dt);
        o.dir = norm(herm(pa, a, b, nb, k -> k.dir, h00, h10, h01, h11, dt));
        o.normal = herm(pa, a, b, nb, k -> k.normal, h00, h10, h01, h11, dt);
        o.sh = herm(pa, a, b, nb, k -> k.sh, h00, h10, h01, h11, dt);
        o.osh = herm(pa, a, b, nb, k -> k.osh, h00, h10, h01, h11, dt);
        o.off = herm(pa, a, b, nb, k -> k.off, h00, h10, h01, h11, dt);
        o.knees = herm(pa, a, b, nb, k -> k.knees, h00, h10, h01, h11, dt);
        o.legs = herm(pa, a, b, nb, k -> k.legs, h00, h10, h01, h11, dt);
        o.body = herm(pa, a, b, nb, k -> new float[] { k.body }, h00, h10, h01, h11, dt)[0];
        o.head = herm(pa, a, b, nb, k -> new float[] { k.head }, h00, h10, h01, h11, dt)[0];
        o.poleR = herm(pa, a, b, nb, k -> k.poleR, h00, h10, h01, h11, dt);
        o.poleL = herm(pa, a, b, nb, k -> k.poleL, h00, h10, h01, h11, dt);
        o.legYaw = herm(pa, a, b, nb, k -> k.legYaw, h00, h10, h01, h11, dt);
        o.legRoll = herm(pa, a, b, nb, k -> k.legRoll, h00, h10, h01, h11, dt);
        o.katOff = herm(pa, a, b, nb, k -> k.katOff, h00, h10, h01, h11, dt);
        // плоскость клинка — перпендикулярно к направлению
        float d = o.normal[0] * o.dir[0] + o.normal[1] * o.dir[1] + o.normal[2] * o.dir[2];
        o.normal = norm(new float[] { o.normal[0] - o.dir[0] * d, o.normal[1] - o.dir[1] * d, o.normal[2] - o.dir[2] * d });
        return o;
    }

    private interface F { float[] get(Key k); }

    private static float[] herm(Key pa, Key a, Key b, Key nb, F f, float h00, float h10, float h01, float h11, float dt) {
        float[] va = f.get(a), vb = f.get(b), r = new float[va.length];
        for (int j = 0; j < r.length; j++) {
            float ma = (pa == null || a.hold) ? 0 : (vb[j] - f.get(pa)[j]) / Math.max(1e-4f, b.u - pa.u);
            float mb = (nb == null || b.hold) ? 0 : (f.get(nb)[j] - va[j]) / Math.max(1e-4f, nb.u - a.u);
            r[j] = va[j] * h00 + ma * h10 * dt + vb[j] * h01 + mb * h11 * dt;
        }
        return r;
    }

    public static float[] norm(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1e-6f ? new float[] { 0, 1, 0 } : new float[] { v[0] / l, v[1] / l, v[2] / l };
    }
}
