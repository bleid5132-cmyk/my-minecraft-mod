package com.bleid.vestments.client.horse;

import com.google.gson.JsonArray;
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
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Модель из кубов в формате Blockbench (.geo.json, box UV). Внутри — наша система осей: x — лево модели, y — вверх,
 * z — вперёд, единицы — пиксели; из Bedrock: позиция (x, y, −z), поворот (x, −y, −z), порядок Z·Y·X.
 */
@Environment(EnvType.CLIENT)
public final class GeoModel {
    public static final class Bone {
        public final String name;
        public final int parent;
        public final float px, py, pz, rx, ry, rz;
        float[] v = new float[0];   // на вершину: x y z u v
        byte[] n = new byte[0];     // на квад: нормаль

        Bone(String name, int parent, float px, float py, float pz, float rx, float ry, float rz) {
            this.name = name; this.parent = parent; this.px = px; this.py = py; this.pz = pz; this.rx = rx; this.ry = ry; this.rz = rz;
        }
    }

    public final List<Bone> bones = new ArrayList<>();
    public final Map<String, Integer> index = new HashMap<>();

    public static GeoModel load(Identifier file) {
        GeoModel m = new GeoModel();
        try (Reader r = new InputStreamReader(MinecraftClient.getInstance().getResourceManager().open(file), StandardCharsets.UTF_8)) {
            JsonObject g = JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            JsonObject desc = g.getAsJsonObject("description");
            float tw = desc.get("texture_width").getAsFloat(), th = desc.get("texture_height").getAsFloat();
            JsonArray arr = g.getAsJsonArray("bones");
            List<JsonObject> raw = new ArrayList<>();
            for (JsonElement e : arr) raw.add(e.getAsJsonObject());
            for (JsonObject b : raw) m.index.put(b.get("name").getAsString(), m.index.size());
            for (JsonObject b : raw) {
                float[] p = vec(b, "pivot"), rot = vec(b, "rotation");
                int parent = b.has("parent") ? m.index.getOrDefault(b.get("parent").getAsString(), -1) : -1;
                Bone bone = new Bone(b.get("name").getAsString(), parent, p[0], p[1], -p[2], rot[0], -rot[1], -rot[2]);
                if (b.has("cubes")) {
                    List<float[]> verts = new ArrayList<>();
                    List<byte[]> norms = new ArrayList<>();
                    for (JsonElement ce : b.getAsJsonArray("cubes")) cube(ce.getAsJsonObject(), tw, th, verts, norms);
                    bone.v = new float[verts.size() * 20];
                    bone.n = new byte[norms.size() * 3];
                    for (int i = 0; i < verts.size(); i++) System.arraycopy(verts.get(i), 0, bone.v, i * 20, 20);
                    for (int i = 0; i < norms.size(); i++) System.arraycopy(norms.get(i), 0, bone.n, i * 3, 3);
                }
                m.bones.add(bone);
            }
        } catch (Exception e) {
            com.bleid.vestments.Vestments.LOGGER.error("Не удалось загрузить модель {}", file, e);
        }
        return m;
    }

    private static float[] vec(JsonObject o, String k) {
        if (!o.has(k)) return new float[3];
        JsonArray a = o.getAsJsonArray(k);
        return new float[] { a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat() };
    }

    /** Грани куба с развёрткой box UV (как в build-скрипте модели: одинаковая раскладка на рисунке и в игре). */
    private static void cube(JsonObject c, float tw, float th, List<float[]> verts, List<byte[]> norms) {
        float[] o = vec(c, "origin"), s = vec(c, "size");
        JsonArray uv = c.getAsJsonArray("uv");
        float u = uv.get(0).getAsFloat(), v = uv.get(1).getAsFloat();
        float x0 = o[0], x1 = o[0] + s[0], y0 = o[1], y1 = o[1] + s[1], z0 = -(o[2] + s[2]), z1 = -o[2];
        int W = (int) Math.ceil(s[0] - 1e-6), H = (int) Math.ceil(s[1] - 1e-6), D = (int) Math.ceil(s[2] - 1e-6);
        // имя: начало, ось u, ось v, прямоугольник развёртки, нормаль
        face(verts, norms, tw, th, x0, y1, z1, x1 - x0, 0, 0, 0, y0 - y1, 0, u + D, v + D, W, H, 0, 0, 1);
        face(verts, norms, tw, th, x1, y1, z0, x0 - x1, 0, 0, 0, y0 - y1, 0, u + 2 * D + W, v + D, W, H, 0, 0, -1);
        face(verts, norms, tw, th, x1, y1, z1, 0, 0, z0 - z1, 0, y0 - y1, 0, u, v + D, D, H, 1, 0, 0);
        face(verts, norms, tw, th, x0, y1, z0, 0, 0, z1 - z0, 0, y0 - y1, 0, u + D + W, v + D, D, H, -1, 0, 0);
        face(verts, norms, tw, th, x0, y1, z1, x1 - x0, 0, 0, 0, 0, z0 - z1, u + D, v, W, D, 0, 1, 0);
        face(verts, norms, tw, th, x0, y0, z0, x1 - x0, 0, 0, 0, 0, z1 - z0, u + D + W, v, W, D, 0, -1, 0);
    }

    private static void face(List<float[]> verts, List<byte[]> norms, float tw, float th, float ox, float oy, float oz,
                             float ax, float ay, float az, float bx, float by, float bz, float fu, float fv, float fw, float fh,
                             int nx, int ny, int nz) {
        float[] q = new float[20];
        // обход: (0,0) → (0,1) → (1,1) → (1,0) — против часовой стрелки, если смотреть снаружи
        float[][] st = { { 0, 0 }, { 0, 1 }, { 1, 1 }, { 1, 0 } };
        boolean flip = cross(ax, ay, az, bx, by, bz, nx, ny, nz);
        for (int i = 0; i < 4; i++) {
            float[] k = st[flip ? 3 - i : i];
            q[i * 5] = ox + ax * k[0] + bx * k[1];
            q[i * 5 + 1] = oy + ay * k[0] + by * k[1];
            q[i * 5 + 2] = oz + az * k[0] + bz * k[1];
            q[i * 5 + 3] = (fu + fw * k[0]) / tw;
            q[i * 5 + 4] = (fv + fh * k[1]) / th;
        }
        verts.add(q);
        norms.add(new byte[] { (byte) nx, (byte) ny, (byte) nz });
    }

    /** true, если обход (a, затем b) смотрит против нормали — тогда порядок вершин разворачивается. */
    private static boolean cross(float ax, float ay, float az, float bx, float by, float bz, int nx, int ny, int nz) {
        // для обхода (0,0)→(0,1)→(1,1) нормаль = b × a
        float cx = by * az - bz * ay, cy = bz * ax - bx * az, cz = bx * ay - by * ax;
        return cx * nx + cy * ny + cz * nz < 0;
    }

    public int index(String name) {
        return index.getOrDefault(name, -1);
    }

    /** Матрицы костей: родитель · T(сдвиг) · T(опора) · Rz·Ry·Rx · S · T(−опора); углы — покой + анимация (градусы). */
    public Matrix4f[] pose(float[] rot, float[] pos, float[] scl) {
        Matrix4f[] out = new Matrix4f[bones.size()];
        for (int i = 0; i < bones.size(); i++) {
            Bone b = bones.get(i);
            Matrix4f m = b.parent >= 0 ? new Matrix4f(out[b.parent]) : new Matrix4f();
            m.translate(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]);
            m.translate(b.px, b.py, b.pz);
            float x = b.rx + rot[i * 3], y = b.ry + rot[i * 3 + 1], z = b.rz + rot[i * 3 + 2];
            if (z != 0) m.rotateZ((float) Math.toRadians(z));
            if (y != 0) m.rotateY((float) Math.toRadians(y));
            if (x != 0) m.rotateX((float) Math.toRadians(x));
            if (scl[i * 3] != 1 || scl[i * 3 + 1] != 1 || scl[i * 3 + 2] != 1) m.scale(scl[i * 3], scl[i * 3 + 1], scl[i * 3 + 2]);
            m.translate(-b.px, -b.py, -b.pz);
            out[i] = m;
        }
        return out;
    }

    /** Рисует модель по матрицам костей другой модели с тем же скелетом (map — индекс кости здесь → индекс там). */
    public void render(MatrixStack ms, VertexConsumer vc, int light, int overlay, Matrix4f[] mats, int[] map, float r, float g, float b, float a) {
        for (int i = 0; i < bones.size(); i++) {
            Bone bone = bones.get(i);
            if (bone.n.length == 0) continue;
            int mi = map == null ? i : map[i];
            if (mi < 0) continue;
            ms.push();
            MatrixStack.Entry e = ms.peek();
            e.getPositionMatrix().mul(mats[mi]);
            e.getNormalMatrix().mul(new Matrix3f(mats[mi]).normal());
            Matrix4f pm = e.getPositionMatrix();
            Matrix3f nm = e.getNormalMatrix();
            float[] v = bone.v;
            byte[] n = bone.n;
            for (int q = 0, o = 0; q < n.length / 3; q++, o += 20) {
                float nx = n[q * 3], ny = n[q * 3 + 1], nz = n[q * 3 + 2];
                for (int c = 0, oo = o; c < 4; c++, oo += 5) {
                    vc.vertex(pm, v[oo], v[oo + 1], v[oo + 2]).color(r, g, b, a).texture(v[oo + 3], v[oo + 4])
                            .overlay(overlay).light(light).normal(nm, nx, ny, nz).next();
                }
            }
            ms.pop();
        }
    }

    /** Сопоставление костей этой модели с костями другой по именам. */
    public int[] mapTo(GeoModel other) {
        int[] m = new int[bones.size()];
        for (int i = 0; i < m.length; i++) m[i] = other.index(bones.get(i).name);
        return m;
    }
}
