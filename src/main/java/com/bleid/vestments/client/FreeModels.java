package com.bleid.vestments.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Модели оружия из «Кузницы оружия» с поворотом деталей на любой угол по трём осям.
 * Обычная модель item/&lt;id&gt;.json даёт текстуру и положение в руке; детали берутся из
 * assets/vestments/free_models/&lt;id&gt;.json и собираются здесь, в обход проверки углов Minecraft.
 */
@Environment(EnvType.CLIENT)
public final class FreeModels {
    private FreeModels() { }

    // грани: углы (доли по осям) и место на текстуре (a — по горизонтали, b — сверху вниз)
    private static final float[][][] FACES = {
        {{1, 1, 0, 0, 0}, {0, 1, 0, 1, 0}, {0, 0, 0, 1, 1}, {1, 0, 0, 0, 1}},   // north
        {{0, 1, 1, 0, 0}, {1, 1, 1, 1, 0}, {1, 0, 1, 1, 1}, {0, 0, 1, 0, 1}},   // south
        {{1, 1, 1, 0, 0}, {1, 1, 0, 1, 0}, {1, 0, 0, 1, 1}, {1, 0, 1, 0, 1}},   // east
        {{0, 1, 0, 0, 0}, {0, 1, 1, 1, 0}, {0, 0, 1, 1, 1}, {0, 0, 0, 0, 1}},   // west
        {{0, 1, 0, 0, 0}, {1, 1, 0, 1, 0}, {1, 1, 1, 1, 1}, {0, 1, 1, 0, 1}},   // up
        {{0, 0, 1, 0, 0}, {1, 0, 1, 1, 0}, {1, 0, 0, 1, 1}, {0, 0, 0, 0, 1}},   // down
    };
    private static final String[] FACE_NAMES = {"north", "south", "east", "west", "up", "down"};
    private static final float[][] NORMALS = {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    public static void attach(ModelLoadingPlugin.Context context) {
        context.modifyModelAfterBake().register((model, ctx) -> {
            Identifier id = ctx.id();
            if (model == null || !"vestments".equals(id.getNamespace()) || !id.getPath().startsWith("item/")) return model;
            String name = id.getPath().substring(5);
            try {
                Optional<Resource> res = MinecraftClient.getInstance().getResourceManager()
                        .getResource(new Identifier("vestments", "free_models/" + name + ".json"));
                if (res.isEmpty()) return model;
                JsonObject root;
                try (Reader r = res.get().getReader()) { root = JsonParser.parseReader(r).getAsJsonObject(); }
                return new Baked(model, build(root, model.getParticleSprite()));
            } catch (Exception e) {
                com.bleid.vestments.Vestments.LOGGER.warn("free model {} failed: {}", name, e.toString());
                return model;
            }
        });
    }

    private static float[] vec(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        float[] v = new float[a.size()];
        for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsFloat();
        return v;
    }

    /** Поворот как в редакторе: сначала X, потом Y, потом Z (градусы) вокруг origin. */
    private static float[] rot(float[] p, float[] ang, float[] o) {
        double x = p[0] - o[0], y = p[1] - o[1], z = p[2] - o[2];
        double a = Math.toRadians(ang[0]), c = Math.cos(a), s = Math.sin(a);
        double y1 = y * c - z * s, z1 = y * s + z * c; y = y1; z = z1;
        a = Math.toRadians(ang[1]); c = Math.cos(a); s = Math.sin(a);
        double x1 = x * c + z * s; z1 = -x * s + z * c; x = x1; z = z1;
        a = Math.toRadians(ang[2]); c = Math.cos(a); s = Math.sin(a);
        x1 = x * c - y * s; y1 = x * s + y * c; x = x1; y = y1;
        return new float[] {(float) x + o[0], (float) y + o[1], (float) z + o[2]};
    }

    private static Direction facing(float[] n) {
        float ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
        if (ax >= ay && ax >= az) return n[0] > 0 ? Direction.EAST : Direction.WEST;
        if (ay >= az) return n[1] > 0 ? Direction.UP : Direction.DOWN;
        return n[2] > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static List<BakedQuad> build(JsonObject root, Sprite sprite) {
        List<BakedQuad> out = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("elements")) {
            JsonObject e = el.getAsJsonObject();
            float[] f = vec(e.get("from")), t = vec(e.get("to")), side = vec(e.get("side")), cap = vec(e.get("cap"));
            float[] ang = {0, 0, 0}, org = {8, 8, 8};
            if (e.has("rot") && e.get("rot").isJsonObject()) {
                JsonObject r = e.getAsJsonObject("rot");
                ang = new float[] {r.has("x") ? r.get("x").getAsFloat() : 0, r.has("y") ? r.get("y").getAsFloat() : 0, r.has("z") ? r.get("z").getAsFloat() : 0};
                org = vec(r.get("origin"));
            }
            boolean shade = !(e.has("glow") && e.get("glow").getAsBoolean());
            // форма: сдвиги восьми углов (индекс = x + 2y + 4z), до поворота
            float[][] D = new float[8][];
            if (e.has("d") && e.get("d").isJsonArray()) {
                JsonArray da = e.getAsJsonArray("d");
                for (int i = 0; i < 8 && i < da.size(); i++) if (da.get(i).isJsonArray()) D[i] = vec(da.get(i));
            }
            for (int fi = 0; fi < 6; fi++) {
                float[] uv = fi >= 4 ? cap : side;
                if (e.has("faces") && e.getAsJsonObject("faces").has(FACE_NAMES[fi])) uv = vec(e.getAsJsonObject("faces").get(FACE_NAMES[fi]));
                float[][] P = new float[4][];
                float[][] T = new float[4][];
                for (int v = 0; v < 4; v++) {
                    float[] k = FACES[fi][v];
                    float[] p = {f[0] + (t[0] - f[0]) * k[0], f[1] + (t[1] - f[1]) * k[1], f[2] + (t[2] - f[2]) * k[2]};
                    float[] dd = D[(int) k[0] + 2 * (int) k[1] + 4 * (int) k[2]];
                    if (dd != null) { p[0] += dd[0]; p[1] += dd[1]; p[2] += dd[2]; }
                    P[v] = rot(p, ang, org);
                    T[v] = new float[] {uv[0] + (uv[2] - uv[0]) * k[3], uv[1] + (uv[3] - uv[1]) * k[4]};
                }
                float[] n = rot(NORMALS[fi], ang, new float[] {0, 0, 0});
                // обход против часовой стрелки, если смотреть снаружи
                float ax = P[1][0] - P[0][0], ay = P[1][1] - P[0][1], az = P[1][2] - P[0][2];
                float bx = P[2][0] - P[0][0], by = P[2][1] - P[0][1], bz = P[2][2] - P[0][2];
                float cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
                if (cx * n[0] + cy * n[1] + cz * n[2] < 0) {
                    float[] tp = P[1]; P[1] = P[3]; P[3] = tp;
                    float[] tt = T[1]; T[1] = T[3]; T[3] = tt;
                }
                // нормаль — по настоящей (возможно, наклонённой) грани
                {
                    float ux = P[1][0] - P[0][0], uy = P[1][1] - P[0][1], uz = P[1][2] - P[0][2];
                    float vx = P[2][0] - P[0][0], vy = P[2][1] - P[0][1], vz = P[2][2] - P[0][2];
                    float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                    float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (l > 1e-6f) n = new float[] {nx / l, ny / l, nz / l};
                }
                int[] data = new int[32];
                int packed = ((int) (n[0] * 127) & 0xFF) | (((int) (n[1] * 127) & 0xFF) << 8) | (((int) (n[2] * 127) & 0xFF) << 16);
                for (int v = 0; v < 4; v++) {
                    int b = v * 8;
                    data[b] = Float.floatToRawIntBits(P[v][0] / 16f);
                    data[b + 1] = Float.floatToRawIntBits(P[v][1] / 16f);
                    data[b + 2] = Float.floatToRawIntBits(P[v][2] / 16f);
                    data[b + 3] = -1;
                    data[b + 4] = Float.floatToRawIntBits(sprite.getFrameU(T[v][0]));
                    data[b + 5] = Float.floatToRawIntBits(sprite.getFrameV(T[v][1]));
                    data[b + 6] = 0;
                    data[b + 7] = packed;
                }
                out.add(new BakedQuad(data, -1, facing(n), sprite, shade));
            }
        }
        return out;
    }

    /** Обёртка: детали — свои, всё остальное (положение в руке, частицы) — от обычной модели. */
    private static final class Baked implements BakedModel {
        private final BakedModel base;
        private final List<BakedQuad> quads;

        Baked(BakedModel base, List<BakedQuad> quads) { this.base = base; this.quads = quads; }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, Random random) {
            return face == null ? quads : Collections.emptyList();
        }
        @Override public boolean useAmbientOcclusion() { return base.useAmbientOcclusion(); }
        @Override public boolean hasDepth() { return base.hasDepth(); }
        @Override public boolean isSideLit() { return base.isSideLit(); }
        @Override public boolean isBuiltin() { return false; }
        @Override public Sprite getParticleSprite() { return base.getParticleSprite(); }
        @Override public ModelTransformation getTransformation() { return base.getTransformation(); }
        @Override public ModelOverrideList getOverrides() { return base.getOverrides(); }
    }
}
