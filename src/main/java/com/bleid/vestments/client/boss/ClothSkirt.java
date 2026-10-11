package com.bleid.vestments.client.boss;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Юбка как настоящая ткань: сетка частиц (Верле) с растяжением, сдвигом и изгибом, верхний ряд пришит к поясу,
 * ткань обтекает бёдра и ноги (капсулы), инерция — в мировых координатах, поэтому подол отстаёт при беге,
 * разлетается в развороте и полёте и колышется от ветра.
 */
@Environment(EnvType.CLIENT)
public final class ClothSkirt {
    static final int C = 26, R = 8;
    /** Покой: позиции в вокселях модели (до позы). */
    private final float[] rest = new float[C * R * 3];
    /** Мировые координаты (блоки) относительно якоря. */
    private final double[] pos = new double[C * R * 3], prev = new double[C * R * 3];
    private final float[] local = new float[C * R * 3];
    private double ax, ay, az;
    private boolean init;
    private long nanos;
    private double acc;
    private final float[] lenV = new float[C * R], lenH = new float[C * R], lenS = new float[C * R], lenB = new float[C * R];

    ClothSkirt() {
        for (int i = 0; i < R; i++) {
            for (int c = 0; c < C; c++) {
                double th = 2 * Math.PI * c / C;
                double hem = 36.0 + 2.6 * Math.sin(5 * th) + 1.4 * Math.sin(11 * th + 1.3);
                double t = (double) i / (R - 1);
                double y = 57.2 - (57.2 - hem) * t;
                double rx = 6.9 + 7.0 * Math.pow(t, 0.85), rz = 4.9 + 5.6 * Math.pow(t, 0.85);
                int k = (i * C + c) * 3;
                rest[k] = (float) (rx * Math.sin(th));
                rest[k + 1] = (float) y;
                rest[k + 2] = (float) (rz * Math.cos(th));
            }
        }
        for (int i = 0; i < R; i++) {
            for (int c = 0; c < C; c++) {
                int id = i * C + c;
                if (i + 1 < R) lenV[id] = dist(rest, id, id + C);
                lenH[id] = dist(rest, id, i * C + (c + 1) % C);
                if (i + 1 < R) lenS[id] = dist(rest, id, (i + 1) * C + (c + 1) % C);
                if (i + 2 < R) lenB[id] = dist(rest, id, id + 2 * C);
            }
        }
    }

    private static float dist(float[] a, int i, int j) {
        float dx = a[i * 3] - a[j * 3], dy = a[i * 3 + 1] - a[j * 3 + 1], dz = a[i * 3 + 2] - a[j * 3 + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / 16f;
    }

    /** Преобразование «воксели модели → мир (блоки)»: поворот тела, масштаб, подъём. */
    public interface Frame {
        void toWorld(float x, float y, float z, double[] out);
        void toLocal(double x, double y, double z, float[] out);
    }

    /**
     * Шаг симуляции. torso — матрица торса (к ней пришит пояс), caps — капсулы тела в вокселях модели
     * после позы: {ax, ay, az, bx, by, bz, r} ...
     */
    void simulate(Frame f, Matrix4f torso, float[][] caps, float time, float wind) {
        long now = System.nanoTime();
        double dt = nanos == 0 ? 0 : Math.min(0.1, (now - nanos) / 1e9);
        nanos = now;
        double[] w = new double[3];
        Vector3f v = new Vector3f();
        if (!init) {
            for (int k = 0; k < C * R; k++) {
                torso.transformPosition(rest[k * 3], rest[k * 3 + 1], rest[k * 3 + 2], v);
                f.toWorld(v.x, v.y, v.z, w);
                pos[k * 3] = prev[k * 3] = w[0];
                pos[k * 3 + 1] = prev[k * 3 + 1] = w[1];
                pos[k * 3 + 2] = prev[k * 3 + 2] = w[2];
            }
            init = true;
        }
        // пояс: если тело «прыгнуло» (телепорт) — сбрасываем ткань
        torso.transformPosition(rest[0], rest[1], rest[2], v);
        f.toWorld(v.x, v.y, v.z, w);
        double jump = Math.abs(w[0] - pos[0]) + Math.abs(w[1] - pos[1]) + Math.abs(w[2] - pos[2]);
        if (jump > 4) { init = false; simulate(f, torso, caps, time, wind); return; }

        // капсулы — в мир
        double[][] cw = new double[caps.length][7];
        for (int i = 0; i < caps.length; i++) {
            float[] cp = caps[i];
            f.toWorld(cp[0], cp[1], cp[2], w);
            cw[i][0] = w[0]; cw[i][1] = w[1]; cw[i][2] = w[2];
            f.toWorld(cp[3], cp[4], cp[5], w);
            cw[i][3] = w[0]; cw[i][4] = w[1]; cw[i][5] = w[2];
            cw[i][6] = cp[6] / 16f;
        }
        acc += dt;
        final double h = 1 / 90.0;
        int steps = 0;
        while (acc >= h && steps < 9) {
            acc -= h;
            steps++;
            // пришитый пояс
            for (int c = 0; c < C; c++) {
                torso.transformPosition(rest[c * 3], rest[c * 3 + 1], rest[c * 3 + 2], v);
                f.toWorld(v.x, v.y, v.z, w);
                int k = c * 3;
                prev[k] = pos[k]; prev[k + 1] = pos[k + 1]; prev[k + 2] = pos[k + 2];
                pos[k] = w[0]; pos[k + 1] = w[1]; pos[k + 2] = w[2];
            }
            // Верле: инерция + тяжесть + лёгкий ветер
            double g = 15 * h * h, damp = 0.985;
            for (int k = C; k < C * R; k++) {
                int o = k * 3;
                double x = pos[o], y = pos[o + 1], z = pos[o + 2];
                double c = (k % C) * 0.7;
                double wx = wind * Math.sin(time * 1.7 + c) * h * h * 2.5, wz = wind * Math.cos(time * 1.3 + c * 1.3) * h * h * 2.5;
                pos[o] = x + (x - prev[o]) * damp + wx;
                pos[o + 1] = y + (y - prev[o + 1]) * damp - g;
                pos[o + 2] = z + (z - prev[o + 2]) * damp + wz;
                prev[o] = x; prev[o + 1] = y; prev[o + 2] = z;
            }
            for (int it = 0; it < 5; it++) {
                for (int i = 0; i < R; i++) {
                    for (int c = 0; c < C; c++) {
                        int id = i * C + c;
                        if (i + 1 < R) link(id, id + C, lenV[id], 1f);
                        link(id, i * C + (c + 1) % C, lenH[id], 0.9f);
                        if (i + 1 < R) link(id, (i + 1) * C + (c + 1) % C, lenS[id], 0.5f);
                        if (i + 2 < R) link(id, id + 2 * C, lenB[id], 0.25f);
                    }
                }
                for (int k = C; k < C * R; k++) collide(k, cw);
            }
        }
        for (int k = 0; k < C * R; k++) {
            float[] o = new float[3];
            f.toLocal(pos[k * 3], pos[k * 3 + 1], pos[k * 3 + 2], o);
            local[k * 3] = o[0]; local[k * 3 + 1] = o[1]; local[k * 3 + 2] = o[2];
        }
    }

    private void link(int a, int b, float len, float stiff) {
        int oa = a * 3, ob = b * 3;
        double dx = pos[ob] - pos[oa], dy = pos[ob + 1] - pos[oa + 1], dz = pos[ob + 2] - pos[oa + 2];
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-6) return;
        double diff = (d - len) / d * stiff;
        boolean pa = a < C, pb = b < C;
        double ka = pa ? 0 : (pb ? 1 : 0.5), kb = pb ? 0 : (pa ? 1 : 0.5);
        pos[oa] += dx * diff * ka; pos[oa + 1] += dy * diff * ka; pos[oa + 2] += dz * diff * ka;
        pos[ob] -= dx * diff * kb; pos[ob + 1] -= dy * diff * kb; pos[ob + 2] -= dz * diff * kb;
    }

    private void collide(int k, double[][] caps) {
        int o = k * 3;
        for (double[] c : caps) {
            double bx = c[3] - c[0], by = c[4] - c[1], bz = c[5] - c[2];
            double px = pos[o] - c[0], py = pos[o + 1] - c[1], pz = pos[o + 2] - c[2];
            double l2 = bx * bx + by * by + bz * bz;
            double t = l2 > 1e-9 ? Math.max(0, Math.min(1, (px * bx + py * by + pz * bz) / l2)) : 0;
            double qx = px - bx * t, qy = py - by * t, qz = pz - bz * t;
            double d = Math.sqrt(qx * qx + qy * qy + qz * qz), r = c[6] + 0.03;
            if (d < r && d > 1e-6) {
                double s = (r - d) / d;
                pos[o] += qx * s; pos[o + 1] += qy * s; pos[o + 2] += qz * s;
            }
        }
    }

    /** Рисует ткань (в кадре модели: воксели) двусторонними четырёхугольниками. */
    void render(MatrixStack ms, VertexConsumer vc, int light, int overlay, float r, float g, float b) {
        Matrix4f pm = ms.peek().getPositionMatrix();
        Matrix3f nm = ms.peek().getNormalMatrix();
        Vector3f n = new Vector3f();
        for (int i = 0; i + 1 < R; i++) {
            for (int c = 0; c < C; c++) {
                int a = i * C + c, bq = i * C + (c + 1) % C, cq = (i + 1) * C + (c + 1) % C, d = (i + 1) * C + c;
                float ux = local[bq * 3] - local[a * 3], uy = local[bq * 3 + 1] - local[a * 3 + 1], uz = local[bq * 3 + 2] - local[a * 3 + 2];
                float vx = local[d * 3] - local[a * 3], vy = local[d * 3 + 1] - local[a * 3 + 1], vz = local[d * 3 + 2] - local[a * 3 + 2];
                n.set(uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx);
                if (n.lengthSquared() > 1e-8f) n.normalize();
                n.negate();
                float u0 = (float) c / C, u1 = (float) (c + 1) / C, v0 = (float) i / (R - 1), v1 = (float) (i + 1) / (R - 1);
                vert(vc, pm, nm, a, u0, v0, n, light, overlay, r, g, b);
                vert(vc, pm, nm, bq, u1, v0, n, light, overlay, r, g, b);
                vert(vc, pm, nm, cq, u1, v1, n, light, overlay, r, g, b);
                vert(vc, pm, nm, d, u0, v1, n, light, overlay, r, g, b);
            }
        }
    }

    private void vert(VertexConsumer vc, Matrix4f pm, Matrix3f nm, int k, float u, float v, Vector3f n, int light, int overlay,
                      float r, float g, float b) {
        vc.vertex(pm, local[k * 3], local[k * 3 + 1], local[k * 3 + 2]).color(r, g, b, 1f).texture(u, v).overlay(overlay).light(light)
                .normal(nm, n.x, n.y, n.z).next();
    }
}
