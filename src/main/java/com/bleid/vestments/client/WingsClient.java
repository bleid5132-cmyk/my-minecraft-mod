package com.bleid.vestments.client;

import com.bleid.vestments.client.fx.FxSystem;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * «Ангельские крылья» паладина: светящиеся крылья из перьев за спиной (медленно взмахивают),
 * свой игрок под крыльями плавно парит — падение замедлено, с Shift можно спуститься.
 */
@Environment(EnvType.CLIENT)
public final class WingsClient {
    private static final Map<Integer, long[]> ACTIVE = new HashMap<>();   // id → {начало, конец} (тики мира)

    private WingsClient() { }

    public static void start(Entity e, int dur) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        long now = mc.world.getTime();
        ACTIVE.put(e.getId(), new long[] { now, now + dur });
        if (e == mc.player && mc.player.isOnGround()) {
            mc.player.addVelocity(0, 0.55, 0);                  // взмах — и паладин поднимается над землёй
        }
    }

    /** 0..1 — насколько раскрыты крылья (плавно появляются и исчезают). */
    public static float open(Entity e, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long[] t = ACTIVE.get(e.getId());
        if (t == null || mc.world == null) return 0f;
        float now = mc.world.getTime() + td;
        if (now > t[1]) return 0f;
        float in = MathHelper.clamp((now - t[0]) / 8f, 0f, 1f), out = MathHelper.clamp((t[1] - now) / 12f, 0f, 1f);
        return Math.min(in, out);
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.world == null) { ACTIVE.clear(); return; }
            long now = mc.world.getTime();
            ACTIVE.values().removeIf(t -> now > t[1] + 2);
            FLAP.keySet().retainAll(ACTIVE.keySet());
            shed(mc);
            ClientPlayerEntity me = mc.player;
            if (me != null && ACTIVE.containsKey(me.getId()) && !me.isOnGround() && !me.isSneaking() && !me.isTouchingWater()
                    && !me.getAbilities().flying) {
                Vec3d v = me.getVelocity();
                if (v.y < -0.035) me.setVelocity(v.x, -0.035, v.z);          // парение
            }
        });
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerEntityRenderer pr) helper.register(new Feature(pr));
        });
    }

    // ---------------------------------------------------------------- крылья: многослойные орлиные перья

    private static final net.minecraft.util.Identifier TEX = new net.minecraft.util.Identifier("vestments", "textures/entity/wing_feathers.png");
    private static final net.minecraft.util.Identifier GLOW = new net.minecraft.util.Identifier("vestments", "textures/entity/wing_feathers_glow.png");
    private static final float SCALE = 0.74f;
    /**
     * Перо в плоскости крыла: {корень u, корень v, угол°, длина, ширина, вид (0 маховое, 1 второстепенное,
     * 2 кроющее, 3 мелкое кроющее), слой (0 — нижний, 4 — верхний), изгиб кончика°, поворот вокруг стержня°}.
     */
    private static final float[][] FEATHERS = build();

    private static float[] along(float t) {
        // «кости» крыла: плечо R0 → локоть E → запястье Wr → кисть H
        float[][] pts = { { 1.5f, 0f }, { 9.5f, 8.5f }, { 18f, 13f }, { 22f, 14.5f } };
        float[] seg = { 0f, 0.42f, 0.86f, 1f };
        for (int i = 0; i < 3; i++) {
            if (t <= seg[i + 1]) {
                float k = (t - seg[i]) / (seg[i + 1] - seg[i]);
                return new float[] { pts[i][0] + (pts[i + 1][0] - pts[i][0]) * k, pts[i][1] + (pts[i + 1][1] - pts[i][1]) * k };
            }
        }
        return pts[3];
    }

    private static void add(java.util.List<float[]> f, float[] r, float dv, float ang, float len, float w, int kind, int layer,
                            float curl, float twist) {
        f.add(new float[] { r[0], r[1] + dv, ang, len, w, kind, layer, curl, twist });
    }

    private static float[][] build() {
        java.util.List<float[]> f = new java.util.ArrayList<>();
        // слой 0: второстепенные маховые вдоль «руки» и первостепенные «пальцы» на кисти
        for (int i = 0; i <= 13; i++) {
            float t = i / 13f * 0.86f;
            add(f, along(t), 0, -98 + 50 * (t / 0.86f), 18.5f + 4f * (t / 0.86f), 5.4f, 1, 0, -10, 14);
        }
        for (int i = 0; i <= 9; i++) {
            float k = i / 9f;
            float len = 23f + 6f * (float) Math.sin(k * Math.PI * 0.85);
            add(f, along(0.86f + 0.14f * (1 - k)), 0, 42 - 92 * k, len, 5.0f, 0, 0, -12 + 6 * k, 18);
        }
        // слой 1: большие кроющие над второстепенными и кроющие кисти
        for (int i = 0; i <= 13; i++) {
            float t = i / 13f * 0.86f;
            add(f, along(t), -0.4f, -95 + 52 * (t / 0.86f), 10.5f + 1.5f * (t / 0.86f), 5.2f, 2, 1, -6, 10);
        }
        for (int i = 0; i <= 5; i++) {
            float k = i / 5f;
            add(f, along(0.86f + 0.14f * (1 - k)), -0.3f, 30 - 70 * k, 11f, 4.6f, 2, 1, -5, 10);
        }
        // слой 2: средние кроющие
        for (int i = 0; i <= 12; i++) {
            float t = i / 12f;
            add(f, along(t * 0.98f), 0.2f, -92 + 60 * t, 6.5f + 1f * t, 4.8f, 3, 2, -3, 8);
        }
        // слой 3: мелкие кроющие по переднему краю и крылышко (alula) у запястья
        for (int i = 0; i <= 12; i++) {
            float t = i / 12f;
            add(f, along(t * 0.98f), 0.9f, -85 + 60 * t, 3.8f, 4.2f, 3, 3, 0, 6);
        }
        for (int i = 0; i < 3; i++) add(f, along(0.86f), 0.6f, 70 - 12 * i, 7.5f - i, 3.2f, 0, 3, -6, 12);
        // слой 4: лопаточные перья у спины
        for (int i = 0; i <= 4; i++) {
            float t = i / 4f * 0.22f;
            add(f, along(t), 0.3f, -104 + 8 * i, 12.5f - i, 5.2f, 2, 4, -4, 8);
        }
        return f.toArray(new float[0][]);
    }

    /** Состояние полёта для взмахов: фаза, сглаженные скорость и подъём. */
    private static final class FlapState {
        float phase, speed, climb;
        long nanos;
    }

    private static final Map<Integer, FlapState> FLAP = new HashMap<>();

    static final class Feature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
        Feature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity p,
                           float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
            float o = open(p, tickDelta);
            if (o <= 0.001f || p.isInvisible()) return;
            // взмахи: зависание — мощные медленные удары крыльями, быстрый полёт — парение с лёгкой дрожью
            FlapState fs = FLAP.computeIfAbsent(p.getId(), k -> new FlapState());
            long now = System.nanoTime();
            float dt = fs.nanos == 0 ? 0 : Math.min(0.1f, (now - fs.nanos) / 1e9f);
            fs.nanos = now;
            double vx = p.getX() - p.prevX, vz = p.getZ() - p.prevZ, vy = p.getY() - p.prevY;
            float sp = (float) Math.sqrt(vx * vx + vz * vz);
            float kk = 1f - (float) Math.exp(-5f * dt);
            fs.speed += (MathHelper.clamp(sp / 0.5f, 0f, 1f) - fs.speed) * kk;
            fs.climb += (MathHelper.clamp((float) vy / 0.3f, -1f, 1f) - fs.climb) * kk;
            boolean ground = p.isOnGround();
            float rate = ground ? 1.4f : MathHelper.lerp(fs.speed, 4.2f, 1.6f) + Math.max(0, fs.climb) * 2.5f;
            fs.phase += rate * dt;
            float amp = ground ? 5f : MathHelper.lerp(fs.speed, 26f, 7f) + Math.max(0, fs.climb) * 10f;
            float ph = fs.phase;
            float flapDeg = amp * (MathHelper.sin(ph) + 0.25f * MathHelper.sin(2 * ph)) + (ground ? -6f : 4f);
            float sweep = ground ? 0.25f : fs.speed * 0.55f;          // на скорости крылья заводятся назад
            float spread = ground ? 0.82f : 1f;

            matrices.push();
            getContextModel().body.rotate(matrices);
            MatrixStack.Entry en = matrices.peek();
            VertexConsumer halo = vcp.getBuffer(FxSystem.layer());
            drawHalo(halo, en.getPositionMatrix(), o);
            VertexConsumer solid = vcp.getBuffer(net.minecraft.client.render.RenderLayer.getEntityCutoutNoCull(TEX));
            draw(solid, en, o * spread, flapDeg, sweep, light, false);
            VertexConsumer glow = vcp.getBuffer(net.minecraft.client.render.RenderLayer.getEyes(GLOW));
            draw(glow, en, o * spread, flapDeg, sweep, light, true);
            matrices.pop();
        }

        /** Мягкое сияние за спиной. */
        private static void drawHalo(VertexConsumer vc, Matrix4f mat, float o) {
            float r = 1.1f * o, a = 0.22f * o;
            Vec3d c = new Vec3d(0, 0.3, 0.2);
            float[] rgb = { a, a * 0.9f, a * 0.65f, a, a * 0.9f, a * 0.65f, a, a * 0.9f, a * 0.65f, a, a * 0.9f, a * 0.65f };
            FxSystem.drawQuad(vc, mat, FxSystem.ORB, c.add(-r, -r, 0), c.add(r, -r, 0), c.add(r, r, 0), c.add(-r, r, 0),
                    new float[] { 0, 0, 1, 0, 1, 1, 0, 1 }, rgb);
        }

        private static void vtx(VertexConsumer vc, Matrix4f mat, org.joml.Matrix3f nm, Vec3d pt, float u, float v, Vec3d n,
                                int light, boolean glow) {
            Vec3d q = pt.multiply(SCALE / 16.0);
            vc.vertex(mat, (float) q.x, (float) q.y, (float) q.z).color(255, 255, 255, 255).texture(u, v)
                    .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(glow ? 0xF000F0 : light)
                    .normal(nm, (float) n.x, (float) n.y, (float) n.z).next();
        }

        private static void draw(VertexConsumer vc, MatrixStack.Entry en, float o, float flapDeg, float sweep, int light, boolean glow) {
            Matrix4f mat = en.getPositionMatrix();
            org.joml.Matrix3f nm = en.getNormalMatrix();
            float raise = (float) Math.toRadians(flapDeg) - (1 - o) * 1.3f;     // складывание при появлении/исчезании
            float sc = 0.25f + 0.75f * o;
            float cr = MathHelper.cos(raise), sr = MathHelper.sin(raise);
            for (int side = -1; side <= 1; side += 2) {
                // плоскость крыла: «наружу» (с заводом назад) и «вверх»; модель: +X влево, −Y вверх, +Z назад
                Vec3d u = new Vec3d(-side * Math.cos(sweep), 0, 0.3 + Math.sin(sweep)).normalize();
                Vec3d v = new Vec3d(0, -1, 0.28).normalize();
                Vec3d n = u.crossProduct(v).normalize();
                if (n.z < 0) n = n.multiply(-1);
                Vec3d root = new Vec3d(-side * 1.0, 2.2, 2.4);
                int idx = 0;
                for (float[] f : FEATHERS) {
                    idx++;
                    float ru = f[0] * sc, rv = f[1] * sc;
                    double qu = ru * cr - rv * sr, qv = ru * sr + rv * cr;
                    double a = Math.toRadians(f[2]) + raise - (1 - o) * Math.toRadians(75);
                    double a2 = a + Math.toRadians(f[7]);
                    double L = f[3] * (0.5 + 0.5 * o), w = f[4] / 2, tw = Math.toRadians(f[8]);
                    Vec3d base = root.add(u.multiply(qu)).add(v.multiply(qv)).add(n.multiply(f[6] * 0.17 + idx * 0.004 + (glow ? 0.012 : 0)));
                    Vec3d d1 = u.multiply(Math.cos(a)).add(v.multiply(Math.sin(a)));
                    Vec3d d2 = u.multiply(Math.cos(a2)).add(v.multiply(Math.sin(a2)));
                    // поперёк пера, с поворотом вокруг стержня — перья ложатся черепицей
                    Vec3d h1 = u.multiply(-Math.sin(a)).add(v.multiply(Math.cos(a))).multiply(Math.cos(tw)).add(n.multiply(Math.sin(tw))).multiply(w);
                    Vec3d h2 = u.multiply(-Math.sin(a2)).add(v.multiply(Math.cos(a2))).multiply(Math.cos(tw)).add(n.multiply(Math.sin(tw))).multiply(w);
                    Vec3d mid = base.add(d1.multiply(L * 0.55));
                    Vec3d tip = mid.add(d2.multiply(L * 0.45));
                    float u0 = f[5] * 0.25f, u1 = u0 + 0.25f;
                    vtx(vc, mat, nm, base.subtract(h1), u0, 0, n, light, glow);
                    vtx(vc, mat, nm, base.add(h1), u1, 0, n, light, glow);
                    vtx(vc, mat, nm, mid.add(h2), u1, 0.55f, n, light, glow);
                    vtx(vc, mat, nm, mid.subtract(h2), u0, 0.55f, n, light, glow);
                    vtx(vc, mat, nm, mid.subtract(h2), u0, 0.55f, n, light, glow);
                    vtx(vc, mat, nm, mid.add(h2), u1, 0.55f, n, light, glow);
                    vtx(vc, mat, nm, tip.add(h2), u1, 1, n, light, glow);
                    vtx(vc, mat, nm, tip.subtract(h2), u0, 1, n, light, glow);
                }
            }
        }
    }

    /** Под крыльями иногда срывается светящееся перо. */
    static void shed(MinecraftClient mc) {
        if (mc.world == null || mc.world.getTime() % 5 != 0) return;
        for (Map.Entry<Integer, long[]> en : ACTIVE.entrySet()) {
            Entity e = mc.world.getEntityById(en.getKey());
            if (e == null || open(e, 0) < 0.5f) continue;
            double yaw = Math.toRadians(e.getYaw());
            double side = mc.world.random.nextBoolean() ? 1 : -1;
            double rx = -Math.cos(yaw) * side, rz = -Math.sin(yaw) * side;
            double bx = Math.sin(yaw) * 0.4, bz = -Math.cos(yaw) * 0.4;
            double far = 0.6 + mc.world.random.nextDouble() * 1.6;
            FxSystem.spawn(FxSystem.FEATHER, e.getX() + rx * far + bx, e.getY() + 1.2 + mc.world.random.nextDouble() * 0.9,
                            e.getZ() + rz * far + bz)
                    .vel((mc.world.random.nextDouble() - 0.5) * 0.02, -0.01, (mc.world.random.nextDouble() - 0.5) * 0.02)
                    .gravity(0.0015f).drag(0.97f).rot((float) (mc.world.random.nextDouble() * 6), 0.05f)
                    .size(0.12f, 0.06f).color(1f, 0.95f, 0.8f).life(60).fade(0.1f, 0.4f);
        }
    }
}
