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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Ленты Посоха Света как настоящая ткань: каждая лента — цепочка звеньев с физикой (верле-интеграция).
 * Точки лент живут в мировой ориентации (относительно игрока), поэтому по инерции отстают при
 * повороте камеры, взмахе посоха и движении игрока, а гравитация всегда тянет вниз.
 * Единицы — пиксели модели (16 на блок).
 */
@Environment(EnvType.CLIENT)
public final class StaffRibbons {
    private static final Identifier TEXTURE = new Identifier(Vestments.MOD_ID, "textures/item/staff_of_light_3d.png");
    private static final int SEGMENTS = 7;
    private static final float SEGMENT_LENGTH = 1.0f;     // пикселей модели
    private static final float HALF_WIDTH = 0.42f;
    private static final float GRAVITY = 110f;            // пикс/с² — ткань «парит», падает мягче
    private static final float AIR_DRAG = 5.0f;           // сильное сопротивление воздуха — плавные движения
    private static final float BEND = 0.35f;              // жёсткость на изгиб (0..1) — без резких изломов
    private static final float MAX_SPEED = 60f;           // пикс/с — ограничение рывков
    private static final float STEP = 1f / 120f;
    /** Какую долю поворота и движения камеры ленты повторяют сразу (остальное — инерция). 0.7 = на 70% мягче. */
    private static final float FOLLOW_CAMERA = 0.7f;
    private static final float[][] ANCHORS = { { 6.5f, 18.6f, 8.0f }, { 9.5f, 18.6f, 8.0f } };
    // область ленты на текстуре 64x64
    private static final float U0 = 44f / 64f, U1 = 48f / 64f, V0 = 0f, V1 = 38f / 64f;

    private static final Map<Long, Chain> CHAINS = new HashMap<>();
    private static long lastCleanup;

    private static final class Chain {
        /** Точки в мировых координатах относительно камеры, в пикселях (16 на блок). */
        final Vector3f[] pos = new Vector3f[SEGMENTS + 1];
        final Vector3f[] prev = new Vector3f[SEGMENTS + 1];
        Vec3d lastCameraPos;
        final Quaternionf lastViewToWorld = new Quaternionf();
        long lastNanos;
        long lastUsed;

        void reset(Vector3f anchor) {
            for (int i = 0; i <= SEGMENTS; i++) {
                pos[i] = new Vector3f(anchor).add(0, -i * SEGMENT_LENGTH, 0);
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
        Matrix4f pose = entry.getPositionMatrix();
        Matrix4f invPose = new Matrix4f(pose).invert();

        // Матрица посоха переводит модель в пространство вида (начало — камера).
        // Мир = поворот вида обратно + позиция камеры. Так крепление ленты по-настоящему
        // перемещается в мире, когда игрок поворачивает камеру или двигается.
        Quaternionf worldToView = new Quaternionf()
                .rotateX(camera.getPitch() * MathHelper.RADIANS_PER_DEGREE)
                .rotateY((camera.getYaw() + 180f) * MathHelper.RADIANS_PER_DEGREE);
        Quaternionf viewToWorld = new Quaternionf(worldToView).conjugate();
        Vec3d camPos = camera.getPos();

        long now = System.nanoTime();
        long baseKey = (entity != null ? entity.getId() : -1L) * 4L + (firstPerson ? 2 : 0);
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        for (int r = 0; r < ANCHORS.length; r++) {
            Vector3f anchorView = pose.transformPosition(new Vector3f(ANCHORS[r][0], ANCHORS[r][1], ANCHORS[r][2]).div(16f));
            Vector3f anchor = viewToWorld.transform(anchorView).mul(16f);

            Chain chain = CHAINS.computeIfAbsent(baseKey + r, k -> new Chain());
            if (chain.lastCameraPos == null || chain.pos[0] == null
                    || chain.lastCameraPos.squaredDistanceTo(camPos) > 4.0 || now - chain.lastUsed > 1_000_000_000L) {
                chain.reset(anchor);                     // первый кадр, телепорт или долгий перерыв
                chain.lastNanos = now;
                chain.lastCameraPos = camPos;
                chain.lastViewToWorld.set(viewToWorld);
            }
            // часть поворота камеры ленты повторяют вместе с ней — реакция на обзор мягче
            Quaternionf delta = new Quaternionf(viewToWorld).mul(new Quaternionf(chain.lastViewToWorld).conjugate());
            Quaternionf follow = new Quaternionf().slerp(delta, FOLLOW_CAMERA);
            chain.lastViewToWorld.set(viewToWorld);
            for (int i = 1; i <= SEGMENTS; i++) {
                follow.transform(chain.pos[i]);
                follow.transform(chain.prev[i]);
            }
            // камера сдвинулась — сдвигаем систему отсчёта (и pos, и prev, чтобы не добавить скорость)
            Vec3d moved = camPos.subtract(chain.lastCameraPos);
            chain.lastCameraPos = camPos;
            Vector3f shift = new Vector3f((float) moved.x, (float) moved.y, (float) moved.z).mul(-16f * (1f - FOLLOW_CAMERA));
            for (int i = 0; i <= SEGMENTS; i++) {
                chain.pos[i].add(shift);
                chain.prev[i].add(shift);
            }
            simulate(chain, anchor, now);
            chain.lastUsed = now;
            draw(entry, invPose, buffer, chain, worldToView, light, overlay);
        }

        if (now - lastCleanup > 5_000_000_000L) {   // забываем ленты, которые давно не рисовались
            lastCleanup = now;
            for (Iterator<Chain> it = CHAINS.values().iterator(); it.hasNext(); ) {
                if (now - it.next().lastUsed > 10_000_000_000L) it.remove();
            }
        }
    }

    /** Шаг физики: крепление плавно идёт к новому месту, остальная ткань тянется за ним по инерции. */
    private static void simulate(Chain c, Vector3f anchor, long now) {
        float dt = Math.min((now - c.lastNanos) / 1_000_000_000f, 0.1f);
        c.lastNanos = now;
        int steps = Math.max(1, MathHelper.ceil(dt / STEP));
        float h = dt / steps;
        float keep = (float) Math.exp(-AIR_DRAG * h);
        float maxStep = MAX_SPEED * h;
        Vector3f anchorPrev = new Vector3f(c.pos[0]);
        for (int s = 0; s < steps; s++) {
            float t = (s + 1f) / steps;
            c.pos[0].set(anchorPrev).lerp(anchor, t);
            c.prev[0].set(c.pos[0]);
            for (int i = 1; i <= SEGMENTS; i++) {
                Vector3f p = c.pos[i];
                Vector3f v = new Vector3f(p).sub(c.prev[i]).mul(keep);
                float sp = v.length();
                if (sp > maxStep) v.mul(maxStep / sp);
                c.prev[i].set(p);
                p.add(v).add(0, -GRAVITY * h * h, 0);
            }
            for (int iter = 0; iter < 6; iter++) {
                // длина звеньев — ткань не растягивается
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
                // изгиб — точка через одну держит расстояние, лента гнётся плавной дугой
                for (int i = 0; i + 2 <= SEGMENTS; i++) {
                    Vector3f a = c.pos[i], b = c.pos[i + 2];
                    Vector3f d = new Vector3f(b).sub(a);
                    float len = d.length();
                    float rest = SEGMENT_LENGTH * 2f;
                    if (len < 1e-5f || len >= rest) continue;
                    float diff = (len - rest) / len * BEND;
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

    private static void draw(MatrixStack.Entry entry, Matrix4f invPose, VertexConsumer buffer, Chain c,
                             Quaternionf worldToView, int light, int overlay) {
        Matrix4f pose = entry.getPositionMatrix();
        Matrix3f normal = entry.getNormalMatrix();
        Vector3f[] p = new Vector3f[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            Vector3f view = worldToView.transform(new Vector3f(c.pos[i]).div(16f));
            p[i] = invPose.transformPosition(view).mul(16f);      // обратно в пиксели модели
        }
        for (int i = 0; i < SEGMENTS; i++) {
            Vector3f a = p[i], b = p[i + 1];
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
