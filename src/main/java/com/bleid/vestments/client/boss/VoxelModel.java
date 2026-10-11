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
import net.minecraft.util.math.RotationAxis;
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
        final List<Integer> children = new ArrayList<>();

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
    private final List<Integer> roots = new ArrayList<>();

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
            for (int i = 0; i < m.parts.size(); i++) {
                int p = m.parts.get(i).parent;
                if (p >= 0) m.parts.get(p).children.add(i);
                else m.roots.add(i);
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

    /**
     * Рисует модель. rot[i] — углы части (градусы: x, y, z; порядок Y·X·Z), scale[i] — масштаб части,
     * color — множитель цвета обычных частей (вспышка урона и т.п.).
     */
    public void render(MatrixStack ms, VertexConsumer vc, int light, int overlay, Vector3f[] rot, float[] scale,
                       float r, float g, float b) {
        for (int i : roots) renderPart(i, ms, vc, light, overlay, rot, scale, r, g, b);
    }

    private void renderPart(int i, MatrixStack ms, VertexConsumer vc, int light, int overlay, Vector3f[] rot, float[] scale,
                            float r, float g, float b) {
        Part p = parts.get(i);
        ms.push();
        Vector3f a = rot[i];
        float s = scale[i];
        if (a.lengthSquared() > 0 || s != 1f) {
            ms.translate(p.pivot.x, p.pivot.y, p.pivot.z);
            if (a.y != 0) ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(a.y));
            if (a.x != 0) ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(a.x));
            if (a.z != 0) ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(a.z));
            if (s != 1f) ms.scale(s, s, s);
            ms.translate(-p.pivot.x, -p.pivot.y, -p.pivot.z);
        }
        MatrixStack.Entry e = ms.peek();
        Matrix4f pm = e.getPositionMatrix();
        Matrix3f nm = e.getNormalMatrix();
        int li = p.glow ? 0xF000F0 : light;
        float cr = p.glow ? 1f : r, cg = p.glow ? 1f : g, cb = p.glow ? 1f : b;
        float[] v = p.v;
        byte[] n = p.n;
        for (int q = 0, o = 0; q < n.length / 3; q++) {
            float nx = n[q * 3], ny = n[q * 3 + 1], nz = n[q * 3 + 2];
            for (int c = 0; c < 4; c++, o += 5) {
                vc.vertex(pm, v[o], v[o + 1], v[o + 2]).color(cr, cg, cb, 1f).texture(v[o + 3], v[o + 4])
                        .overlay(overlay).light(li).normal(nm, nx, ny, nz).next();
            }
        }
        for (int c : p.children) renderPart(c, ms, vc, light, overlay, rot, scale, r, g, b);
        ms.pop();
    }
}
