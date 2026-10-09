package com.bleid.vestments.client.fx;

import com.bleid.vestments.Vestments;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Собственная система частиц эффектов способностей. Всё рисуется аддитивным свечением
 * (как светящиеся глаза паука): спрайты из одного атласа, плавное движение, кривые размера,
 * цвета и прозрачности, вращение, орбиты вокруг сущностей, плоские круги на земле,
 * столпы света и вытянутые по скорости искры.
 */
@Environment(EnvType.CLIENT)
public final class FxSystem {
    public static final Identifier ATLAS = new Identifier(Vestments.MOD_ID, "textures/fx/atlas.png");
    private static final int MAX = 6000;

    // спрайты атласа 4x4
    public static final int ORB = 0, GLINT = 1, STAR = 2, RING = 3, RUNES = 4, SUNBURST = 5, BEAM = 6, FEATHER = 7,
            FLAME = 8, HEART = 9, CROSS = 10, SNOW = 11, SHOCK = 12, SMOKE = 13, BUBBLE = 14, STREAK = 15,
            TRAIL = 16, SLASH = 17, CRACK = 18, FLARE = 19;
    /** Атлас 4 столбца × 5 строк. */
    static final float CU = 0.25f, CV = 0.2f;

    public enum Mode { BILLBOARD, STRETCH, FLAT, BEAM }

    private static final List<P> PARTICLES = new ArrayList<>();
    private static final List<P> PENDING = new ArrayList<>();
    private static final List<Task> TASKS = new ArrayList<>();

    /** Повторяющееся действие эффекта: вызывается каждый тик, пока возвращает true. */
    public interface Task {
        boolean tick(int age);
    }

    private static final class TaskState {
        final Task task;
        int age;
        TaskState(Task t) { task = t; }
    }

    private static final List<TaskState> TASK_STATES = new ArrayList<>();
    private static final List<TaskState> NEW_TASKS = new ArrayList<>();

    private FxSystem() { }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FxSystem::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            PARTICLES.clear();
            PENDING.clear();
            TASK_STATES.clear();
            NEW_TASKS.clear();
        });
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> render(ctx.matrixStack(), ctx.consumers(), ctx.camera(), ctx.tickDelta()));
    }

    // ---------------------------------------------------------------- API

    public static P spawn(int sprite, double x, double y, double z) {
        P p = new P(sprite, x, y, z);
        PENDING.add(p);
        return p;
    }

    public static void task(Task t) {
        NEW_TASKS.add(new TaskState(t));
    }

    // ---------------------------------------------------------------- частица

    public static final class P {
        final int sprite;
        Mode mode = Mode.BILLBOARD;
        Entity follow;                  // если задана — координаты относительно неё
        double x, y, z, px, py, pz;     // локальные (или мировые, если follow == null)
        double vx, vy, vz;
        float drag = 1f, gravity;
        int age, life = 20, delay;
        float size0 = 0.2f, size1 = 0.2f;
        boolean easeSize;               // размер меняется с замедлением (быстро в начале)
        float r0 = 1, g0 = 1, b0 = 1, r1 = 1, g1 = 1, b1 = 1;
        float alpha = 1f, fadeIn = 0.1f, fadeOut = 0.35f;
        float pulseAmp, pulseFreq, pulsePhase;
        float rot, prevRot, spin;
        float stretch = 4f;             // длина вытянутой искры в размерах
        float height = 10f;             // высота столпа
        float height1 = -1f;
        // орбита
        boolean orbit;
        double ang, rad, angVel, radVel, oy, oyVel;

        P(int sprite, double x, double y, double z) {
            this.sprite = sprite;
            this.x = this.px = x;
            this.y = this.py = y;
            this.z = this.pz = z;
        }

        public P mode(Mode m) { mode = m; return this; }
        public P follow(Entity e) {
            follow = e;
            if (e != null && !orbit) { x -= e.getX(); y -= e.getY(); z -= e.getZ(); px = x; py = y; pz = z; }
            return this;
        }
        /** Следовать за сущностью; координаты при создании — смещение относительно неё. */
        public P followLocal(Entity e) {
            follow = e;
            return this;
        }
        public P vel(double vx, double vy, double vz) { this.vx = vx; this.vy = vy; this.vz = vz; return this; }
        public P drag(float d) { drag = d; return this; }
        public P gravity(float g) { gravity = g; return this; }
        public P life(int l) { life = Math.max(1, l); return this; }
        public P delay(int d) { delay = d; return this; }
        public P size(float s) { size0 = size1 = s; return this; }
        public P size(float s0, float s1) { size0 = s0; size1 = s1; return this; }
        public P ease() { easeSize = true; return this; }
        public P color(float r, float g, float b) { r0 = r1 = r; g0 = g1 = g; b0 = b1 = b; return this; }
        public P color(float[] c) { return color(c[0], c[1], c[2]); }
        public P colors(float[] c0, float[] c1) {
            r0 = c0[0]; g0 = c0[1]; b0 = c0[2]; r1 = c1[0]; g1 = c1[1]; b1 = c1[2];
            return this;
        }
        public P alpha(float a) { alpha = a; return this; }
        public P fade(float in, float out) { fadeIn = in; fadeOut = out; return this; }
        public P pulse(float amp, float freq) { pulseAmp = amp; pulseFreq = freq; pulsePhase = (float) (Math.random() * 6.28); return this; }
        public P rot(float r, float s) { rot = prevRot = r; spin = s; return this; }
        public P stretch(float s) { mode = Mode.STRETCH; stretch = s; return this; }
        public P height(float h) { height = h; return this; }
        public P height(float h0, float h1) { height = h0; height1 = h1; return this; }
        /** Движение по окружности вокруг точки (x, y, z) или вокруг follow. */
        public P orbit(double ang, double rad, double angVel, double radVel, double yOff, double yVel) {
            orbit = true;
            this.ang = ang; this.rad = rad; this.angVel = angVel; this.radVel = radVel; this.oy = yOff; this.oyVel = yVel;
            return this;
        }
    }

    // ---------------------------------------------------------------- такт

    private static void tick(MinecraftClient client) {
        if (client.world == null) {
            PARTICLES.clear();
            TASK_STATES.clear();
            return;
        }
        if (client.isPaused()) return;
        TASK_STATES.addAll(NEW_TASKS);
        NEW_TASKS.clear();
        for (Iterator<TaskState> it = TASK_STATES.iterator(); it.hasNext(); ) {
            TaskState s = it.next();
            boolean keep;
            try {
                keep = s.task.tick(s.age++);
            } catch (Exception e) {
                keep = false;
            }
            if (!keep) it.remove();
        }
        PARTICLES.addAll(PENDING);
        PENDING.clear();
        while (PARTICLES.size() > MAX) PARTICLES.remove(0);
        for (Iterator<P> it = PARTICLES.iterator(); it.hasNext(); ) {
            P p = it.next();
            if (p.delay > 0) { p.delay--; continue; }
            if (p.follow != null && p.follow.isRemoved()) {      // сущность пропала — остаёмся на её последнем месте
                Entity f = p.follow;
                if (p.orbit) { p.x = f.getX(); p.y = f.getY(); p.z = f.getZ(); }
                else { p.x += f.getX(); p.y += f.getY(); p.z += f.getZ(); }
                p.follow = null;
            }
            p.px = p.x; p.py = p.y; p.pz = p.z;
            p.prevRot = p.rot;
            if (++p.age >= p.life) { it.remove(); continue; }
            p.rot += p.spin;
            if (p.orbit) {
                p.ang += p.angVel;
                p.rad += p.radVel;
                p.oy += p.oyVel;
            } else {
                p.x += p.vx; p.y += p.vy; p.z += p.vz;
                p.vx *= p.drag; p.vy *= p.drag; p.vz *= p.drag;
                p.vy -= p.gravity;
            }
        }
    }

    // ---------------------------------------------------------------- отрисовка

    private static void render(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, float td) {
        if (PARTICLES.isEmpty() || consumers == null) return;
        Vec3d cam = camera.getPos();
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEyes(ATLAS));
        Matrix4f mat = matrices.peek().getPositionMatrix();
        Quaternionf camRot = camera.getRotation();
        for (P p : PARTICLES) {
            if (p.delay > 0) continue;
            float t = MathHelper.clamp((p.age + td) / p.life, 0f, 1f);
            // мировые координаты
            double bx = 0, by = 0, bz = 0;
            if (p.follow != null) {
                bx = MathHelper.lerp(td, p.follow.prevX, p.follow.getX());
                by = MathHelper.lerp(td, p.follow.prevY, p.follow.getY());
                bz = MathHelper.lerp(td, p.follow.prevZ, p.follow.getZ());
            }
            double wx, wy, wz;
            if (p.orbit) {
                double a = p.ang + p.angVel * td, r = p.rad + p.radVel * td;
                double cx = p.follow != null ? bx : p.x, cy = p.follow != null ? by : p.y, cz = p.follow != null ? bz : p.z;
                wx = cx + Math.cos(a) * r;
                wy = cy + p.oy + p.oyVel * td;
                wz = cz + Math.sin(a) * r;
            } else {
                wx = bx + MathHelper.lerp(td, p.px, p.x);
                wy = by + MathHelper.lerp(td, p.py, p.y);
                wz = bz + MathHelper.lerp(td, p.pz, p.z);
            }
            // прозрачность: появление, затухание, мерцание
            float a = p.alpha;
            if (p.fadeIn > 0 && t < p.fadeIn) a *= t / p.fadeIn;
            if (p.fadeOut > 0 && t > 1 - p.fadeOut) a *= (1 - t) / p.fadeOut;
            if (p.pulseAmp > 0) a *= 1f - p.pulseAmp * (0.5f + 0.5f * MathHelper.sin((p.age + td) * p.pulseFreq + p.pulsePhase));
            if (a <= 0.003f) continue;
            float st = p.easeSize ? 1f - (1f - t) * (1f - t) * (1f - t) : t;
            float size = MathHelper.lerp(st, p.size0, p.size1);
            float r = MathHelper.lerp(t, p.r0, p.r1) * a, g = MathHelper.lerp(t, p.g0, p.g1) * a, b = MathHelper.lerp(t, p.b0, p.b1) * a;
            float x = (float) (wx - cam.x), y = (float) (wy - cam.y), z = (float) (wz - cam.z);
            float u0 = (p.sprite % 4) * CU + 0.001f, v0 = (p.sprite / 4) * CV + 0.001f;
            float u1 = u0 + CU - 0.002f, v1 = v0 + CV - 0.002f;
            float roll = MathHelper.lerp(td, p.prevRot, p.rot);
            switch (p.mode) {
                case FLAT -> {
                    float c = MathHelper.cos(roll) * size, s = MathHelper.sin(roll) * size;
                    quad(vc, mat,
                            x - c + s, y, z - s - c, u0, v0,
                            x + c + s, y, z + s - c, u1, v0,
                            x + c - s, y, z + s + c, u1, v1,
                            x - c - s, y, z - s + c, u0, v1, r, g, b, r, g, b);
                }
                case BEAM -> {
                    float h = p.height1 >= 0 ? MathHelper.lerp(st, p.height, p.height1) : p.height;
                    float dx = -x, dz = -z;
                    float len = MathHelper.sqrt(dx * dx + dz * dz);
                    if (len < 1e-3f) { dx = 1; dz = 0; len = 1; }
                    float sx = -dz / len * size, sz = dx / len * size;
                    quad(vc, mat,
                            x - sx, y + h, z - sz, u0, v0,
                            x + sx, y + h, z + sz, u1, v0,
                            x + sx, y, z + sz, u1, v1,
                            x - sx, y, z - sz, u0, v1, 0, 0, 0, r, g, b);
                }
                case STRETCH -> {
                    Vec3d v = new Vec3d(p.vx, p.vy, p.vz);
                    if (p.orbit) v = new Vec3d(-Math.sin(p.ang), 0, Math.cos(p.ang)).multiply(p.angVel);
                    double vl = v.length();
                    if (vl < 1e-4) { billboard(vc, mat, camRot, x, y, z, size, roll, u0, v0, u1, v1, r, g, b); break; }
                    Vec3d dir = v.multiply(1 / vl);
                    Vec3d toCam = new Vec3d(-x, -y, -z);
                    Vec3d side = dir.crossProduct(toCam);
                    double sl = side.length();
                    if (sl < 1e-4) { billboard(vc, mat, camRot, x, y, z, size, roll, u0, v0, u1, v1, r, g, b); break; }
                    side = side.multiply(size / sl);
                    Vec3d along = dir.multiply(size * p.stretch);
                    quad(vc, mat,
                            (float) (x - along.x + side.x), (float) (y - along.y + side.y), (float) (z - along.z + side.z), u0, v0,
                            (float) (x + along.x + side.x), (float) (y + along.y + side.y), (float) (z + along.z + side.z), u1, v0,
                            (float) (x + along.x - side.x), (float) (y + along.y - side.y), (float) (z + along.z - side.z), u1, v1,
                            (float) (x - along.x - side.x), (float) (y - along.y - side.y), (float) (z - along.z - side.z), u0, v1,
                            r, g, b, r, g, b);
                }
                default -> billboard(vc, mat, camRot, x, y, z, size, roll, u0, v0, u1, v1, r, g, b);
            }
        }
    }

    // ---------------------------------------------------------------- рисование для других рендеров

    public static RenderLayer layer() {
        return RenderLayer.getEyes(ATLAS);
    }

    /** Светящийся спрайт, повёрнутый к камере (координаты относительно камеры). */
    public static void drawSprite(VertexConsumer vc, Matrix4f mat, Quaternionf camRot, int sprite, float x, float y, float z,
                                  float size, float roll, float r, float g, float b) {
        float u0 = (sprite % 4) * CU + 0.001f, v0 = (sprite / 4) * CV + 0.001f;
        billboard(vc, mat, camRot, x, y, z, size, roll, u0, v0, u0 + CU - 0.002f, v0 + CV - 0.002f, r, g, b);
    }

    /** Светящаяся полоса от a до b шириной width, повёрнутая к камере (координаты относительно камеры). */
    public static void drawStrip(VertexConsumer vc, Matrix4f mat, int sprite, Vec3d a, Vec3d b, float width,
                                 float r, float g, float bl, float r2, float g2, float b2) {
        Vec3d axis = b.subtract(a);
        Vec3d mid = a.add(b).multiply(0.5);
        Vec3d side = axis.crossProduct(mid.multiply(-1));
        double sl = side.length();
        if (sl < 1e-5) return;
        side = side.multiply(width / sl);
        float u0 = (sprite % 4) * CU + 0.001f, v0 = (sprite / 4) * CV + 0.001f, u1 = u0 + CU - 0.002f, v1 = v0 + CV - 0.002f;
        // текстура луча: поперёк — u, вдоль — v
        quad(vc, mat,
                (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z), u0, v0,
                (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z), u1, v0,
                (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z), u1, v1,
                (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z), u0, v1,
                r, g, bl, r2, g2, b2);
    }

    private static void billboard(VertexConsumer vc, Matrix4f mat, Quaternionf camRot, float x, float y, float z, float size,
                                  float roll, float u0, float v0, float u1, float v1, float r, float g, float b) {
        Quaternionf q = roll != 0 ? new Quaternionf(camRot).rotateZ(roll) : camRot;
        Vector3f c0 = new Vector3f(-1, 1, 0).rotate(q).mul(size).add(x, y, z);
        Vector3f c1 = new Vector3f(1, 1, 0).rotate(q).mul(size).add(x, y, z);
        Vector3f c2 = new Vector3f(1, -1, 0).rotate(q).mul(size).add(x, y, z);
        Vector3f c3 = new Vector3f(-1, -1, 0).rotate(q).mul(size).add(x, y, z);
        quad(vc, mat, c0.x, c0.y, c0.z, u0, v0, c1.x, c1.y, c1.z, u1, v0, c2.x, c2.y, c2.z, u1, v1, c3.x, c3.y, c3.z, u0, v1,
                r, g, b, r, g, b);
    }

    /** Двусторонний четырёхугольник: верхние две вершины цветом (tr,tg,tb), нижние — (r,g,b). */
    /**
     * Двусторонний четырёхугольник со своими цветами в каждой вершине; uv — доли 0..1 внутри ячейки спрайта.
     * Вершины по кругу: 0-1-2-3 (координаты относительно камеры).
     */
    public static void drawQuad(VertexConsumer vc, Matrix4f m, int sprite, Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3,
                                float[] uv, float[] rgb) {
        float cu = (sprite % 4) * CU + 0.001f, cv = (sprite / 4) * CV + 0.001f, su = CU - 0.002f, sv = CV - 0.002f;
        Vec3d[] p = { p0, p1, p2, p3 };
        int[] order = { 0, 1, 2, 3, 3, 2, 1, 0 };
        for (int i : order) {
            vert(vc, m, (float) p[i].x, (float) p[i].y, (float) p[i].z, cu + uv[i * 2] * su, cv + uv[i * 2 + 1] * sv,
                    rgb[i * 3], rgb[i * 3 + 1], rgb[i * 3 + 2]);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float z0, float u0, float v0,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             float tr, float tg, float tb, float r, float g, float b) {
        vert(vc, m, x0, y0, z0, u0, v0, tr, tg, tb);
        vert(vc, m, x1, y1, z1, u1, v1, tr, tg, tb);
        vert(vc, m, x2, y2, z2, u2, v2, r, g, b);
        vert(vc, m, x3, y3, z3, u3, v3, r, g, b);
        vert(vc, m, x3, y3, z3, u3, v3, r, g, b);
        vert(vc, m, x2, y2, z2, u2, v2, r, g, b);
        vert(vc, m, x1, y1, z1, u1, v1, tr, tg, tb);
        vert(vc, m, x0, y0, z0, u0, v0, tr, tg, tb);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, float r, float g, float b) {
        vc.vertex(m, x, y, z).color(Math.min(1f, r), Math.min(1f, g), Math.min(1f, b), 1f).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0f, 1f, 0f).next();
    }
}
