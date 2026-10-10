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

    // ---------------------------------------------------------------- крылья: многослойные золотые перья

    private static final net.minecraft.util.Identifier[] TEX = new net.minecraft.util.Identifier[8];
    private static final net.minecraft.util.Identifier[] GLOW = new net.minecraft.util.Identifier[8];
    static {
        for (int i = 0; i < 8; i++) {
            TEX[i] = new net.minecraft.util.Identifier("vestments", "textures/entity/wing_feathers_d" + i + ".png");
            GLOW[i] = new net.minecraft.util.Identifier("vestments", "textures/entity/wing_feathers_glow_d" + i + ".png");
        }
    }
    private static final float SCALE = 0.8f;
    /** Тиков на рассыпание крыльев в конце. */
    static final int DISSOLVE = 30;
    /** «Кость» переднего края: плечо R0 → локоть E → запястье Wr → кончик H (плоскость крыла: u наружу, v вверх). */
    private static final float[][] BONE = { { 0.5f, 0f }, { 9f, 1.8f }, { 17f, 3.8f }, { 22.5f, 5.6f } };
    private static final float[] SEG = { 0f, 0.4f, 0.78f, 1f };
    /** Толщина кости по сегментам: в плоскости крыла и поперёк (пиксели). */
    private static final float[] BONE_W = { 1.5f, 1.3f, 1.1f }, BONE_D = { 1.15f, 1.0f, 0.85f };
    /**
     * Перо: {корень u, корень v, угол°, длина, ширина, вид (0 маховое, 1 второстепенное, 2 кроющее, 3 мелкое),
     * слой (0 нижний … 2 верхний), изгиб кончика°, поворот вокруг стержня°, сегмент кости (0 плечо, 1 предплечье, 2 кисть)}.
     */
    private static final float[][] FEATHERS = build();

    private static float[] along(float t) {
        for (int i = 0; i < 3; i++) {
            if (t <= SEG[i + 1]) {
                float k = (t - SEG[i]) / (SEG[i + 1] - SEG[i]);
                return new float[] { BONE[i][0] + (BONE[i + 1][0] - BONE[i][0]) * k, BONE[i][1] + (BONE[i + 1][1] - BONE[i][1]) * k };
            }
        }
        return BONE[3];
    }

    private static int segOf(float t) {
        return t < SEG[1] ? 0 : t < SEG[2] ? 1 : 2;
    }

    private static void add(java.util.List<float[]> f, float t, float dv, float ang, float len, float w, int kind, int layer,
                            float curl, float twist) {
        float[] r = along(t);
        f.add(new float[] { r[0], r[1] + dv, ang, len, w, kind, layer, curl, twist, segOf(Math.min(t, 0.999f)) });
    }

    private static float[][] build() {
        java.util.List<float[]> f = new java.util.ArrayList<>();
        // слой 0: второстепенные вдоль руки — висят вниз зазубренными клинками, у корня короче
        for (int i = 0; i <= 10; i++) {
            float k = i / 10f, t = 0.04f + 0.74f * k;
            add(f, t, -0.6f, -98 + 26 * k, 10.5f + 7f * (float) Math.sin(Math.min(1f, k * 1.4f) * Math.PI / 2), 4.8f, 1, 0, -4, 6);
        }
        // слой 0: маховые веером с кисти — от «вниз-наружу» до почти горизонтали
        for (int i = 0; i <= 7; i++) {
            float k = i / 7f, t = 0.8f + 0.2f * k;
            add(f, t, -0.4f, -66 + 60 * k, 18f + 3f * (float) Math.sin(k * Math.PI * 0.9), 4.2f, 0, 0, -6 + 4 * k, 10);
        }
        // слой 1: большие кроющие — вторая «волна» зубцов
        for (int i = 0; i <= 9; i++) {
            float k = i / 9f, t = 0.06f + 0.88f * k;
            add(f, t, -0.2f, -92 + 50 * k, 8f + 3f * k, 4.4f, 2, 1, -3, 6);
        }
        // слой 2: мелкие кроющие у самой кости
        for (int i = 0; i <= 11; i++) {
            float k = i / 11f, t = 0.03f + 0.95f * k;
            add(f, t, 0.3f, -88 + 50 * k, 3.6f, 3.6f, 3, 2, 0, 4);
        }
        // пара «игл» над костью на кончике крыла
        add(f, 1f, 0.6f, 14, 8.5f, 3f, 0, 1, 0, 6);
        add(f, 0.97f, 0.7f, 30, 5.5f, 2.6f, 0, 1, 0, 6);
        return f.toArray(new float[0][]);
    }

    /** Фаза и сглаженные скорость/подъём для взмахов. */
    private static final class FlapState {
        float phase, speed, climb, ground;
        long nanos;
    }

    private static final Map<Integer, FlapState> FLAP = new HashMap<>();

    /** 0 — крылья целы, 1 — полностью рассыпались. */
    static float dissolve(Entity e, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long[] t = ACTIVE.get(e.getId());
        if (t == null || mc.world == null) return 1f;
        float now = mc.world.getTime() + td;
        return MathHelper.clamp((now - (t[1] - DISSOLVE)) / DISSOLVE, 0f, 1f);
    }

    static final class Feature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
        Feature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity p,
                           float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
            long[] act = ACTIVE.get(p.getId());
            if (act == null || p.isInvisible()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            float now = mc.world.getTime() + tickDelta;
            float o = MathHelper.clamp((now - act[0]) / 10f, 0f, 1f);          // раскрытие в начале
            float dis = dissolve(p, tickDelta);
            if (dis >= 1f || o <= 0.001f) return;

            // ---- взмахи как у птицы: быстрый мощный взмах вниз с полностью раскрытым крылом,
            // медленный подъём со сложенной кистью; на скорости — парение; на земле — крылья сложены
            FlapState fs = FLAP.computeIfAbsent(p.getId(), k -> new FlapState());
            long nn = System.nanoTime();
            float dt = fs.nanos == 0 ? 0 : Math.min(0.1f, (nn - fs.nanos) / 1e9f);
            fs.nanos = nn;
            double vx = p.getX() - p.prevX, vz = p.getZ() - p.prevZ, vy = p.getY() - p.prevY;
            float sp = (float) Math.sqrt(vx * vx + vz * vz);
            float kk = 1f - (float) Math.exp(-4f * dt);
            fs.speed += (MathHelper.clamp(sp / 0.55f, 0f, 1f) - fs.speed) * kk;
            fs.climb += (MathHelper.clamp((float) vy / 0.3f, -1f, 1f) - fs.climb) * kk;
            fs.ground += ((p.isOnGround() ? 1f : 0f) - fs.ground) * kk;
            float climb = Math.max(0, fs.climb), dive = Math.max(0, -fs.climb);
            float glide = MathHelper.clamp(fs.speed * 1.4f - 0.4f - climb, 0f, 1f) * (1 - fs.ground);
            // плавные величественные взмахи (как на референсе): кость ведёт, кисть и маховые догоняют волной;
            // при наборе высоты — чаще и шире, в скольжении — почти неподвижно, на земле — полураскрыты и «дышат»
            float hz = MathHelper.lerp(glide, 0.55f + 0.75f * climb, 0.3f);
            fs.phase = (fs.phase + hz * dt) % 1f;
            float A = MathHelper.lerp(glide, 16f + 22f * climb, 4f) * (1 - dive * 0.6f);
            A = MathHelper.lerp(fs.ground, A, 5f);
            float bias = MathHelper.lerp(glide, 3f, 6f) - dive * 16f;
            bias = MathHelper.lerp(fs.ground, bias, -10f);
            float ph = fs.phase * MathHelper.TAU;
            float s0 = A * MathHelper.sin(ph) + bias;
            float s1 = 1.25f * A * MathHelper.sin(ph - 0.5f) + bias * 1.1f;
            float s2 = 1.5f * A * MathHelper.sin(ph - 1.0f) + bias * 1.2f;
            float sh = s0, elbow = s1 - s0 - 14f * fs.ground, wrist = s2 - s1 - 24f * fs.ground;
            float sweep = MathHelper.lerp(fs.ground, glide * 0.5f + dive * 0.6f, 0.4f);
            // раскрытие в начале
            sh = MathHelper.lerp(o, -40f, sh);
            elbow = MathHelper.lerp(o, -70f, elbow);
            wrist = MathHelper.lerp(o, -110f, wrist);

            int step = Math.min(7, (int) (dis * 8));
            matrices.push();
            getContextModel().body.rotate(matrices);
            MatrixStack.Entry en = matrices.peek();
            VertexConsumer halo = vcp.getBuffer(FxSystem.layer());
            drawHalo(halo, en.getPositionMatrix(), o * (1 - dis));
            float[] ang = { (float) Math.toRadians(sh), (float) Math.toRadians(elbow), (float) Math.toRadians(wrist) };
            VertexConsumer solid = vcp.getBuffer(net.minecraft.client.render.RenderLayer.getEntityCutoutNoCull(TEX[step]));
            draw(solid, en, ang, sweep, light, false);
            VertexConsumer glow = vcp.getBuffer(net.minecraft.client.render.RenderLayer.getEyes(GLOW[step]));
            draw(glow, en, ang, sweep, light, true);
            matrices.pop();
        }

        /** Мягкое золотое сияние за спиной. */
        private static void drawHalo(VertexConsumer vc, Matrix4f mat, float o) {
            if (o <= 0.01f) return;
            float r = 1.2f * o, a = 0.26f * o;
            Vec3d c = new Vec3d(0, 0.25, 0.2);
            float[] rgb = { a, a * 0.78f, a * 0.4f, a, a * 0.78f, a * 0.4f, a, a * 0.78f, a * 0.4f, a, a * 0.78f, a * 0.4f };
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

        private static double[] rot(double x, double y, double a) {
            double c = Math.cos(a), s = Math.sin(a);
            return new double[] { x * c - y * s, x * s + y * c };
        }

        /** ang: {плечо, локоть, запястье} — повороты сегментов кости в плоскости крыла (радианы). */
        private static void draw(VertexConsumer vc, MatrixStack.Entry en, float[] ang, float sweep, int light, boolean glow) {
            Matrix4f mat = en.getPositionMatrix();
            org.joml.Matrix3f nm = en.getNormalMatrix();
            // суставы после поворотов: плечо → локоть → запястье
            double a0 = ang[0], a1 = ang[0] + ang[1], a2 = a1 + ang[2];
            double[] e1 = rot(BONE[1][0] - BONE[0][0], BONE[1][1] - BONE[0][1], a0);
            double[] E = { BONE[0][0] + e1[0], BONE[0][1] + e1[1] };
            double[] w1 = rot(BONE[2][0] - BONE[1][0], BONE[2][1] - BONE[1][1], a1);
            double[] Wr = { E[0] + w1[0], E[1] + w1[1] };
            double[][] origin = { BONE[0] == null ? null : new double[] { BONE[0][0], BONE[0][1] }, E, Wr };
            double[][] rest = { { BONE[0][0], BONE[0][1] }, { BONE[1][0], BONE[1][1] }, { BONE[2][0], BONE[2][1] } };
            double[] segA = { a0, a1, a2 };
            for (int side = -1; side <= 1; side += 2) {
                Vec3d u = new Vec3d(-side * Math.cos(sweep), 0, 0.3 + Math.sin(sweep)).normalize();
                Vec3d v = new Vec3d(0, -1, 0.28).normalize();
                Vec3d n = u.crossProduct(v).normalize();
                if (n.z < 0) n = n.multiply(-1);
                Vec3d root = new Vec3d(-side * 1.0, 2.2, 2.8);
                int idx = 0;
                for (float[] f : FEATHERS) {
                    idx++;
                    int sg = (int) f[9];
                    double[] r = rot(f[0] - rest[sg][0], f[1] - rest[sg][1], segA[sg]);
                    double qu = origin[sg][0] + r[0], qv = origin[sg][1] + r[1];
                    double a = Math.toRadians(f[2]) + segA[sg];
                    double a2b = a + Math.toRadians(f[7]);
                    double L = f[3], w = f[4] / 2, tw = Math.toRadians(f[8]);
                    Vec3d base = root.add(u.multiply(qu)).add(v.multiply(qv)).add(n.multiply(f[6] * 0.17 + idx * 0.004 + (glow ? 0.012 : 0)));
                    Vec3d d1 = u.multiply(Math.cos(a)).add(v.multiply(Math.sin(a)));
                    Vec3d d2 = u.multiply(Math.cos(a2b)).add(v.multiply(Math.sin(a2b)));
                    Vec3d h1 = u.multiply(-Math.sin(a)).add(v.multiply(Math.cos(a))).multiply(Math.cos(tw)).add(n.multiply(Math.sin(tw))).multiply(w);
                    Vec3d h2 = u.multiply(-Math.sin(a2b)).add(v.multiply(Math.cos(a2b))).multiply(Math.cos(tw)).add(n.multiply(Math.sin(tw))).multiply(w);
                    Vec3d mid = base.add(d1.multiply(L * 0.55));
                    Vec3d tip = mid.add(d2.multiply(L * 0.45));
                    float u0 = f[5] * 0.125f, u1 = u0 + 0.125f;
                    vtx(vc, mat, nm, base.subtract(h1), u0, 0, n, light, glow);
                    vtx(vc, mat, nm, base.add(h1), u1, 0, n, light, glow);
                    vtx(vc, mat, nm, mid.add(h2), u1, 0.55f, n, light, glow);
                    vtx(vc, mat, nm, mid.subtract(h2), u0, 0.55f, n, light, glow);
                    vtx(vc, mat, nm, mid.subtract(h2), u0, 0.55f, n, light, glow);
                    vtx(vc, mat, nm, mid.add(h2), u1, 0.55f, n, light, glow);
                    vtx(vc, mat, nm, tip.add(h2), u1, 1, n, light, glow);
                    vtx(vc, mat, nm, tip.subtract(h2), u0, 1, n, light, glow);
                }
                // кость переднего края: три бруска с рунными линиями
                double[] H = rot(BONE[3][0] - BONE[2][0], BONE[3][1] - BONE[2][1], a2);
                double[][] J = { { BONE[0][0], BONE[0][1] }, E, Wr, { Wr[0] + H[0], Wr[1] + H[1] } };
                double inflate = glow ? 0.03 : 0;
                for (int i = 0; i < 3; i++) {
                    double du = J[i + 1][0] - J[i][0], dv = J[i + 1][1] - J[i][1], l = Math.max(1e-3, Math.hypot(du, dv));
                    double ext = i == 0 ? 0.2 : 0.6;
                    Vec3d dir = u.multiply(du / l).add(v.multiply(dv / l));
                    Vec3d perp = u.multiply(-dv / l).add(v.multiply(du / l));
                    Vec3d c = root.add(u.multiply((J[i][0] + J[i + 1][0]) / 2)).add(v.multiply((J[i][1] + J[i + 1][1]) / 2))
                            .add(n.multiply(0.6));
                    box(vc, mat, nm, c, dir.multiply(l / 2 + ext + inflate), perp.multiply(BONE_W[i] + inflate),
                            n.multiply(BONE_D[i] + inflate), 0.5f, 0f, 1f, 0.25f, light, glow, 1f);
                }
            }
            // золотая накладка на спине, из которой растут крылья (в пикселях модели, без масштаба крыльев)
            double k = 1.0 / SCALE, inf = glow ? 0.03 : 0;
            box(vc, mat, nm, new Vec3d(0, 3.0 * k, 2.8 * k), new Vec3d((3.0 + inf) * k, 0, 0), new Vec3d(0, (2.5 + inf) * k, 0),
                    new Vec3d(0, 0, (0.8 + inf) * k), 0.5f, 0.25f, 0.75f, 0.75f, light, glow, 0f);
        }

        /**
         * Брусок: центр c, полуоси ax (вдоль), ay, az. Боковые грани вдоль ax берут текстуру [u0..u1]×[v0..v1];
         * если along = 0, задняя грань (+az) — эта текстура, остальные — торцевая заливка.
         */
        private static void box(VertexConsumer vc, Matrix4f mat, org.joml.Matrix3f nm, Vec3d c, Vec3d ax, Vec3d ay, Vec3d az,
                                float u0, float v0, float u1, float v1, int light, boolean glow, float along) {
            Vec3d[] faceN = { ay, ay.multiply(-1), az, az.multiply(-1) };
            Vec3d[] faceS = { az, az, ay, ay };
            for (int f = 0; f < 4; f++) {
                Vec3d nn = faceN[f], ss = faceS[f];
                Vec3d o = c.add(nn);
                boolean main = along > 0.5f || f == 2;
                float a0 = main ? u0 : 0.75f, a1 = main ? u1 : 0.875f, b0 = main ? v0 : 0.25f, b1 = main ? v1 : 0.5f;
                Vec3d n1 = nn.normalize();
                vtx(vc, mat, nm, o.subtract(ax).subtract(ss), a0, b0, n1, light, glow);
                vtx(vc, mat, nm, o.add(ax).subtract(ss), a1, b0, n1, light, glow);
                vtx(vc, mat, nm, o.add(ax).add(ss), a1, b1, n1, light, glow);
                vtx(vc, mat, nm, o.subtract(ax).add(ss), a0, b1, n1, light, glow);
            }
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Vec3d o = c.add(ax.multiply(sgn)), n1 = ax.normalize().multiply(sgn);
                vtx(vc, mat, nm, o.subtract(ay).subtract(az), 0.75f, 0.25f, n1, light, glow);
                vtx(vc, mat, nm, o.add(ay).subtract(az), 0.875f, 0.25f, n1, light, glow);
                vtx(vc, mat, nm, o.add(ay).add(az), 0.875f, 0.5f, n1, light, glow);
                vtx(vc, mat, nm, o.subtract(ay).add(az), 0.75f, 0.5f, n1, light, glow);
            }
        }
    }

    /** Под крыльями иногда срывается светящееся перо; при исчезании крылья рассыпаются золотой пылью. */
    static void shed(MinecraftClient mc) {
        if (mc.world == null) return;
        net.minecraft.util.math.random.Random R = mc.world.random;
        for (Map.Entry<Integer, long[]> en : ACTIVE.entrySet()) {
            Entity e = mc.world.getEntityById(en.getKey());
            if (e == null) continue;
            float dis = dissolve(e, 0);
            boolean dust = dis > 0f && dis < 1f;
            if (!dust && mc.world.getTime() % 5 != 0) continue;
            double yaw = Math.toRadians(e.getYaw());
            int n = dust ? 14 : 1;
            for (int i = 0; i < n; i++) {
                double side = R.nextBoolean() ? 1 : -1;
                double rx = -Math.cos(yaw) * side, rz = -Math.sin(yaw) * side;
                double bx = Math.sin(yaw) * 0.35, bz = -Math.cos(yaw) * 0.35;
                double far = 0.3 + R.nextDouble() * 1.9;
                double x = e.getX() + rx * far + bx, y = e.getY() + 1.0 + R.nextDouble() * 1.3, z = e.getZ() + rz * far + bz;
                if (dust) {
                    // «пиксели» крыла: квадратные золотые искры уносит вверх и в стороны
                    FxSystem.spawn(FxSystem.GLINT, x, y, z)
                            .vel(rx * 0.02 + (R.nextDouble() - 0.5) * 0.03, 0.02 + R.nextDouble() * 0.04, rz * 0.02 + (R.nextDouble() - 0.5) * 0.03)
                            .gravity(-0.001f).drag(0.95f).size(0.07f + R.nextFloat() * 0.05f, 0f)
                            .color(1f, 0.78f + R.nextFloat() * 0.15f, 0.3f).life(18 + R.nextInt(18)).fade(0f, 0.5f);
                } else {
                    FxSystem.spawn(FxSystem.FEATHER, x, y, z)
                            .vel((R.nextDouble() - 0.5) * 0.02, -0.01, (R.nextDouble() - 0.5) * 0.02)
                            .gravity(0.0015f).drag(0.97f).rot((float) (R.nextDouble() * 6), 0.05f)
                            .size(0.12f, 0.06f).color(1f, 0.8f, 0.35f).life(60).fade(0.1f, 0.4f);
                }
            }
        }
    }
}
