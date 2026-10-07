package com.bleid.vestments.client;

import com.bleid.vestments.Vestments;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Ленты Посоха Света как настоящая ткань: каждая лента — цепочка звеньев с физикой
 * (верле-интеграция): гравитация всегда тянет вниз в мире, как бы ни был повёрнут посох,
 * встречный воздух при движении отдувает ленты назад, затухание успокаивает раскачку.
 * Считается в пространстве модели предмета (единицы — пиксели модели).
 */
@Environment(EnvType.CLIENT)
public final class StaffRibbons {
    private static final Identifier TEXTURE = new Identifier(Vestments.MOD_ID, "textures/item/staff_of_light_3d.png");
    private static final int SEGMENTS = 7;
    private static final float SEGMENT_LENGTH = 1.0f;     // пикселей модели
    private static final float HALF_WIDTH = 0.42f;
    private static final float GRAVITY = 150f;            // пикс/с² (≈ 9.4 м/с²)
    private static final float AIR_DRAG = 2.6f;           // затухание скорости, 1/с
    private static final float WIND_FACTOR = 1.6f;        // насколько сильно отдувает при движении
    private static final float STEP = 1f / 120f;
    private static final float[][] ANCHORS = { { 6.5f, 18.6f, 8.0f }, { 9.5f, 18.6f, 8.0f } };
    // область ленты на текстуре 64x64
    private static final float U0 = 44f / 64f, U1 = 48f / 64f, V0 = 0f, V1 = 38f / 64f;

    private static final Map<Long, Chain> CHAINS = new HashMap<>();
    private static long lastCleanup;

    private static final class Chain {
        final Vector3f[] pos = new Vector3f[SEGMENTS + 1];
        final Vector3f[] prev = new Vector3f[SEGMENTS + 1];
        long lastNanos;
        long lastUsed;

        Chain(float[] anchor, Vector3f down) {
            for (int i = 0; i <= SEGMENTS; i++) {
                pos[i] = new Vector3f(anchor[0], anchor[1], anchor[2]).add(new Vector3f(down).mul(i * SEGMENT_LENGTH));
                prev[i] = new Vector3f(pos[i]);
            }
        }
    }

    private StaffRibbons() { }

    /** Вызывается из ItemRendererMixin, когда матрица — пространство модели посоха (0..1 на блок). */
    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay,
                              LivingEntity entity, boolean firstPerson) {
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client.gameRenderer.getCamera();
        MatrixStack.Entry entry = matrices.peek();

        // мир → вид → пространство модели (обратный поворот = транспонированная матрица нормалей)
        Quaternionf worldToView = new Quaternionf()
                .rotateX(camera.getPitch() * MathHelper.RADIANS_PER_DEGREE)
                .rotateY((camera.getYaw() + 180f) * MathHelper.RADIANS_PER_DEGREE);
        Matrix3f viewToItem = new Matrix3f(entry.getNormalMatrix()).transpose();
        Vector3f down = viewToItem.transform(worldToView.transform(new Vector3f(0, -1, 0))).normalize();

        Vector3f wind = new Vector3f();
        if (entity != null) {
            Vector3f vel = new Vector3f((float) (entity.getX() - entity.prevX), (float) (entity.getY() - entity.prevY),
                    (float) (entity.getZ() - entity.prevZ)).mul(20f * 16f);           // пикс/с
            wind = viewToItem.transform(worldToView.transform(vel.negate()));
        }

        long now = System.nanoTime();
        long baseKey = (entity != null ? entity.getId() : -1L) * 4L + (firstPerson ? 2 : 0);
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        for (int r = 0; r < ANCHORS.length; r++) {
            final int ribbon = r;
            Chain chain = CHAINS.computeIfAbsent(baseKey + r, k -> new Chain(ANCHORS[ribbon], down));
            simulate(chain, ANCHORS[r], down, wind, now);
            chain.lastUsed = now;
            draw(entry, buffer, chain, light, overlay);
        }

        if (now - lastCleanup > 5_000_000_000L) {   // забываем ленты, которые давно не рисовались
            lastCleanup = now;
            for (Iterator<Chain> it = CHAINS.values().iterator(); it.hasNext(); ) {
                if (now - it.next().lastUsed > 10_000_000_000L) it.remove();
            }
        }
    }

    private static void simulate(Chain c, float[] anchor, Vector3f down, Vector3f wind, long now) {
        float dt = c.lastNanos == 0 ? STEP : Math.min((now - c.lastNanos) / 1_000_000_000f, 0.1f);
        c.lastNanos = now;
        if (dt <= 0f) return;
        Vector3f accel = new Vector3f(down).mul(GRAVITY).add(new Vector3f(wind).mul(WIND_FACTOR));
        int steps = Math.max(1, MathHelper.ceil(dt / STEP));
        float h = dt / steps;
        float keep = (float) Math.exp(-AIR_DRAG * h);
        for (int s = 0; s < steps; s++) {
            c.pos[0].set(anchor[0], anchor[1], anchor[2]);
            c.prev[0].set(c.pos[0]);
            for (int i = 1; i <= SEGMENTS; i++) {
                Vector3f p = c.pos[i];
                Vector3f v = new Vector3f(p).sub(c.prev[i]).mul(keep);
                c.prev[i].set(p);
                p.add(v).add(new Vector3f(accel).mul(h * h));
            }
            for (int iter = 0; iter < 4; iter++) {          // ткань не растягивается
                for (int i = 0; i < SEGMENTS; i++) {
                    Vector3f a = c.pos[i], b = c.pos[i + 1];
                    Vector3f d = new Vector3f(b).sub(a);
                    float len = d.length();
                    if (len < 1e-5f) continue;
                    float diff = (len - SEGMENT_LENGTH) / len;
                    if (i == 0) {
                        b.sub(d.mul(diff));
                    } else {
                        d.mul(diff * 0.5f);
                        a.add(d);
                        b.sub(d);
                    }
                }
            }
        }
    }

    private static void draw(MatrixStack.Entry entry, VertexConsumer buffer, Chain c, int light, int overlay) {
        Matrix4f pose = entry.getPositionMatrix();
        Matrix3f normal = entry.getNormalMatrix();
        for (int i = 0; i < SEGMENTS; i++) {
            Vector3f a = c.pos[i], b = c.pos[i + 1];
            Vector3f dir = new Vector3f(b).sub(a);
            if (dir.lengthSquared() < 1e-6f) continue;
            dir.normalize();
            Vector3f side = new Vector3f(dir).cross(0, 0, 1);
            if (side.lengthSquared() < 1e-4f) side.set(1, 0, 0);
            side.normalize().mul(HALF_WIDTH);
            Vector3f n = new Vector3f(side).cross(dir).normalize();
            float va = V0 + (V1 - V0) * i / SEGMENTS;
            float vb = V0 + (V1 - V0) * (i + 1) / SEGMENTS;
            vertex(buffer, pose, normal, new Vector3f(a).sub(side), U0, va, n, light, overlay);
            vertex(buffer, pose, normal, new Vector3f(a).add(side), U1, va, n, light, overlay);
            vertex(buffer, pose, normal, new Vector3f(b).add(side), U1, vb, n, light, overlay);
            vertex(buffer, pose, normal, new Vector3f(b).sub(side), U0, vb, n, light, overlay);
        }
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Matrix3f normal, Vector3f p, float u, float v,
                               Vector3f n, int light, int overlay) {
        buffer.vertex(pose, p.x / 16f, p.y / 16f, p.z / 16f)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(normal, n.x, n.y, n.z)
                .next();
    }
}
