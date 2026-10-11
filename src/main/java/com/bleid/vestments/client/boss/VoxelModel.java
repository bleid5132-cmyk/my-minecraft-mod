package com.bleid.vestments.client.boss;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Воксельная модель из .vxm (v3): части с опорными точками и иерархией; цвета вокселей — в атласе.
 * Светящиеся воксели вынесены в подчасти (glow) — рисуются на полной яркости.
 * Координаты в вокселях (1/16 блока): X — её лево, Y — вверх, Z — вперёд.
 */
@Environment(EnvType.CLIENT)
public final class VoxelModel {
    public static final class Part {
        public final String name;
        public final int parent;
        public final Vector3f pivot;
        public final boolean glow;
        final float[] v;      // на вершину: x y z u v
        final byte[] n;       // на квад: нормаль

        Part(String name, int parent, Vector3f pivot, boolean glow, float[] v, byte[] n) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.glow = glow;
            this.v = v;
            this.n = n;
        }
    }

    public final List<Part> parts = new ArrayList<>();

    public static VoxelModel load(Identifier file) {
        VoxelModel m = new VoxelModel();
        try (InputStream in = MinecraftClient.getInstance().getResourceManager().open(file);
             DataInputStream d = new DataInputStream(new BufferedInputStream(in))) {
            d.readFully(new byte[4]);
            int count = d.readInt();
            float aw = d.readInt(), ah = d.readInt();
            for (int i = 0; i < count; i++) {
                String name = d.readUTF();
                int parent = d.readInt();
                Vector3f piv = new Vector3f(d.readFloat(), d.readFloat(), d.readFloat());
                boolean glow = d.readByte() != 0;
                int q = d.readInt();
                float[] v = new float[q * 20];
                byte[] n = new byte[q * 3];
                int o = 0;
                for (int k = 0; k < q; k++) {
                    n[k * 3] = d.readByte();
                    n[k * 3 + 1] = d.readByte();
                    n[k * 3 + 2] = d.readByte();
                    for (int c = 0; c < 4; c++) {
                        v[o++] = d.readShort() / 2f;
                        v[o++] = d.readShort() / 2f;
                        v[o++] = d.readShort() / 2f;
                        v[o++] = (d.readUnsignedShort() + 0f) / aw;
                        v[o++] = (d.readUnsignedShort() + 0f) / ah;
                    }
                }
                m.parts.add(new Part(name, parent, piv, glow, v, n));
            }
        } catch (Exception e) {
            com.bleid.vestments.Vestments.LOGGER.error("Не удалось загрузить модель {}", file, e);
        }
        return m;
    }

    public int index(String name) {
        for (int i = 0; i < parts.size(); i++) if (parts.get(i).name.equals(name)) return i;
        return -1;
    }

    /** Матрицы частей в пространстве модели: родитель · T(опора) · Ry·Rx·Rz · S · T(−опора). */
    public Matrix4f[] pose(Vector3f[] rot, float[] scale) {
        Matrix4f[] out = new Matrix4f[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            Matrix4f m = p.parent >= 0 && out[p.parent] != null ? new Matrix4f(out[p.parent]) : new Matrix4f();
            Vector3f a = rot[i];
            float s = scale[i];
            if (a.lengthSquared() > 0 || s != 1f) {
                m.translate(p.pivot);
                if (a.y != 0) m.rotateY((float) Math.toRadians(a.y));
                if (a.x != 0) m.rotateX((float) Math.toRadians(a.x));
                if (a.z != 0) m.rotateZ((float) Math.toRadians(a.z));
                if (s != 1f) m.scale(s);
                m.translate(-p.pivot.x, -p.pivot.y, -p.pivot.z);
            }
            out[i] = m;
        }
        return out;
    }

    /** Рисует все части по готовым матрицам; color — множитель цвета обычных частей. */
    public void render(MatrixStack ms, VertexConsumer vc, int light, int overlay, Matrix4f[] mats, float r, float g, float b) {
        render(ms, vc, false, light, overlay, mats, r, g, b, -1e9f, 1e9f);
    }

    /**
     * С окаменением: грани ниже высоты stoneY (в покое модели) — каменные и не светятся; так камень «ползёт» от ног
     * к нимбу. Два прохода: stonePass = false — живые грани, true — каменные (вызывающий подаёт буфер каменной текстуры).
     */
    public void render(MatrixStack ms, VertexConsumer vc, boolean stonePass, int light, int overlay, Matrix4f[] mats,
                       float r, float g, float b, float stoneY, float crumbleY) {
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            boolean halo = p.name.startsWith("halo");      // нимб не каменеет — он падает
            if (halo && stonePass) continue;
            ms.push();
            MatrixStack.Entry e = ms.peek();
            e.getPositionMatrix().mul(mats[i]);
            e.getNormalMatrix().mul(new Matrix3f(mats[i]).normal());
            Matrix4f pm = e.getPositionMatrix();
            Matrix3f nm = e.getNormalMatrix();
            float[] v = p.v;
            byte[] n = p.n;
            for (int q = 0, o = 0; q < n.length / 3; q++, o += 20) {
                float nx = n[q * 3], ny = n[q * 3 + 1], nz = n[q * 3 + 2];
                float fy = (v[o + 1] + v[o + 6] + v[o + 11] + v[o + 16]) * 0.25f;
                if (fy > crumbleY && !halo) continue;          // статуя рассыпается сверху вниз
                boolean st = !halo && fy < stoneY;
                if (st != stonePass) continue;
                VertexConsumer out = vc;
                boolean gl = p.glow && !st;
                int li = gl ? 0xF000F0 : light;
                float cr = gl ? 1f : r, cg = gl ? 1f : g, cb = gl ? 1f : b;
                for (int c = 0, oo = o; c < 4; c++, oo += 5) {
                    out.vertex(pm, v[oo], v[oo + 1], v[oo + 2]).color(cr, cg, cb, 1f).texture(v[oo + 3], v[oo + 4])
                            .overlay(overlay).light(li).normal(nm, nx, ny, nz).next();
                }
            }
            ms.pop();
        }
    }
}
