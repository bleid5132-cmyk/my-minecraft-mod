package com.bleid.vestments.client.boss;

import static com.bleid.vestments.boss.BellRingerEntity.*;

import com.bleid.vestments.boss.BellRingerEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Рендер Звонаря: воксельная модель с суставами, пружинные позы атак, цепь-гарпун,
 * колокол на орбите при погребальном размахе, смерть (колени, молот, цепи, колокол, пепел).
 */
@Environment(EnvType.CLIENT)
public class BellRingerRenderer extends EntityRenderer<BellRingerEntity> {
    private static final Identifier TEX = new Identifier("vestments", "textures/entity/bell_ringer.png");
    private static final Identifier ASH = new Identifier("vestments", "textures/entity/bell_ringer_ash.png");
    static final Identifier CHAIN_TEX = new Identifier("vestments", "textures/entity/bell_chain.png");
    private static final Identifier MODEL = new Identifier("vestments", "models/entity/bell_ringer.vxm");
    private static VoxelModel model;
    private static int TORSO, THIGH_R, SHIN_R, THIGH_L, SHIN_L, HEAD, HEAD_G, ARM_R, FORE_R, HAND_R, HAMMER, ARM_L, FORE_L, HAND_L, BELL, BELL_G;
    static final Springs SPRINGS = new Springs();

    // индексы позы
    private static final int TX = 0, TY = 1, TZ = 2, HX = 3, HY = 4, THR = 5, SHR = 6, THL = 7, SHL = 8,
            ARX = 9, ARY = 10, ARZ = 11, FR = 12, HR = 13, ALX = 14, ALY = 15, ALZ = 16, FL = 17, HL = 18,
            HAMX = 19, BX = 20, BZ = 21, LIFT = 22, N = 23;
    private static final float[] OMEGA = { 15, 15, 15, 16, 16, 16, 16, 16, 16, 17, 17, 17, 14, 14, 17, 17, 17, 14, 14, 12, 9, 9, 15 };

    private static final Vector3f BELL_REST = new Vector3f(0, 97, 15);
    /** Тело слеплено в старых пропорциях и увеличено в 1,4 раза — смещения таза умножаются на это же. */
    private static final float BODY = 1.4f;

    public BellRingerRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        shadowRadius = 2.0f;
        shadowOpacity = 0.8f;
    }

    static VoxelModel model() {
        if (model == null) {
            model = VoxelModel.load(MODEL);
            TORSO = model.index("torso"); THIGH_R = model.index("thigh_r"); SHIN_R = model.index("shin_r");
            THIGH_L = model.index("thigh_l"); SHIN_L = model.index("shin_l"); HEAD = model.index("head");
            ARM_R = model.index("arm_r"); FORE_R = model.index("fore_r"); HAND_R = model.index("hand_r"); HAMMER = model.index("hammer");
            ARM_L = model.index("arm_l"); FORE_L = model.index("fore_l"); HAND_L = model.index("hand_l");
            BELL = model.index("bell"); BELL_G = model.index("bell#g"); HEAD_G = model.index("head#g");
        }
        return model;
    }

    @Override
    public Identifier getTexture(BellRingerEntity e) {
        return TEX;
    }

    static float sm(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    private static void mix(float[] p, float w, float... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) {
            int idx = (int) kv[i];
            p[idx] = p[idx] + (kv[i + 1] - p[idx]) * w;
        }
    }

    /** Сглаженное смешивание двух матриц (сдвиг — линейно, поворот — по сфере, масштаб — линейно). */
    static Matrix4f blend(Matrix4f a, Matrix4f b, float t) {
        if (t <= 0) return new Matrix4f(a);
        if (t >= 1) return new Matrix4f(b);
        Vector3f ta = a.getTranslation(new Vector3f()), tb = b.getTranslation(new Vector3f());
        Vector3f sa = a.getScale(new Vector3f()), sb = b.getScale(new Vector3f());
        Quaternionf qa = a.getNormalizedRotation(new Quaternionf()), qb = b.getNormalizedRotation(new Quaternionf());
        return new Matrix4f().translationRotateScale(ta.lerp(tb, t), qa.slerp(qb, t), sa.lerp(sb, t));
    }

    /** Молот за спиной (в системе торса). */
    private static Matrix4f backHammer(Matrix4f torso, Vector3f piv) {
        return new Matrix4f(torso).translate(-12, 54, -17).rotateZ((float) Math.toRadians(160)).translate(-piv.x, -piv.y, -piv.z);
    }

    /** Профиль колокола-головы (радиус в покое на высоте y, единицы модели) — для столкновений висячей цепи. */
    static float bellRadius(float y) {
        float tb = MathHelper.clamp((y - 80) / 34f, 0, 1);
        float prof = 0.66f + 0.34f * (float) Math.pow(1 - MathHelper.clamp(tb / 0.4f, 0, 1), 2) - 0.05f * MathHelper.clamp((tb - 0.4f) / 0.4f, 0, 1);
        float q = MathHelper.clamp(tb - 0.8f, 0, 1) / 0.2f;
        float dome = (float) Math.sqrt(MathHelper.clamp(1 - q * q, 0, 1));
        return 17 * prof * (0.78f + 0.22f * dome) + 1.6f * MathHelper.clamp((83 - y) / 3f, 0, 1);
    }

    /** Цепь на кончике колокола: точки в мировых координатах, верле с ограничениями длины и обтеканием колокола. */
    static final class Dangle {
        static final int N = 7;
        static final float SEG = 0.2f;
        final double[] x = new double[N * 3], px = new double[N * 3];
        boolean init;
        long nanos;
    }
    static final java.util.Map<Integer, Dangle> DANGLE = new java.util.HashMap<>();

    private static void dangle(BellRingerEntity e, Dangle dg, Vec3d origin, Matrix4f bellWorld, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        Vector3f crown = new Matrix4f(bellWorld).transformPosition(new Vector3f(0, 123, 15));
        double ax = origin.x + crown.x, ay = origin.y + crown.y, az = origin.z + crown.z;
        if (!dg.init) {
            for (int i = 0; i < Dangle.N; i++) {
                dg.x[i * 3] = dg.px[i * 3] = ax + i * 0.05;
                dg.x[i * 3 + 1] = dg.px[i * 3 + 1] = ay - i * Dangle.SEG * 0.3;
                dg.x[i * 3 + 2] = dg.px[i * 3 + 2] = az - i * 0.12;
            }
            dg.init = true;
            dg.nanos = 0;
        }
        long now = System.nanoTime();
        float dts = dg.nanos == 0 ? 0f : Math.min(0.1f, (now - dg.nanos) / 1e9f);
        dg.nanos = now;
        if (net.minecraft.client.MinecraftClient.getInstance().isPaused()) dts = 0;
        Matrix4f inv = new Matrix4f(bellWorld).invert();
        int steps = (int) Math.ceil(dts / (1 / 120f));
        float h = steps > 0 ? dts / steps : 0;
        for (int s = 0; s < steps; s++) {
            for (int i = 1; i < Dangle.N; i++) {
                for (int c = 0; c < 3; c++) {
                    double cur = dg.x[i * 3 + c], v = (cur - dg.px[i * 3 + c]) * 0.985;
                    dg.px[i * 3 + c] = cur;
                    dg.x[i * 3 + c] = cur + v + (c == 1 ? -16.0 * h * h : 0);
                }
            }
            dg.x[0] = ax; dg.x[1] = ay; dg.x[2] = az;
            for (int it = 0; it < 4; it++) {
                for (int i = 0; i + 1 < Dangle.N; i++) {
                    int a = i * 3, b = a + 3;
                    double dx = dg.x[b] - dg.x[a], dy = dg.x[b + 1] - dg.x[a + 1], dz = dg.x[b + 2] - dg.x[a + 2];
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-9, diff = (len - Dangle.SEG) / len;
                    double wa = i == 0 ? 0 : 0.5, wb = i == 0 ? 1 : 0.5;
                    dg.x[a] += dx * diff * wa; dg.x[a + 1] += dy * diff * wa; dg.x[a + 2] += dz * diff * wa;
                    dg.x[b] -= dx * diff * wb; dg.x[b + 1] -= dy * diff * wb; dg.x[b + 2] -= dz * diff * wb;
                }
                // обтекание колокола: точку внутри профиля выталкиваем наружу (в системе колокола)
                for (int i = 1; i < Dangle.N; i++) {
                    Vector3f l = inv.transformPosition(new Vector3f((float) (dg.x[i * 3] - origin.x), (float) (dg.x[i * 3 + 1] - origin.y),
                            (float) (dg.x[i * 3 + 2] - origin.z)));
                    if (l.y < 78 || l.y > 120) continue;
                    float rx = l.x, rz = l.z - 15, rr = (float) Math.sqrt(rx * rx + rz * rz);
                    float lim = l.y > 114.5f ? 3.2f : bellRadius(l.y) + 1.2f;
                    if (l.y > 113 && l.y < 116.5f && rr < bellRadius(114) + 1f && rr > 3.2f) l.y = 116.5f;
                    else if (rr < lim) {
                        if (rr < 1e-3f) { rx = 1; rr = 1; }
                        l.x = rx / rr * lim; l.z = 15 + rz / rr * lim;
                    } else continue;
                    Vector3f w = new Matrix4f(bellWorld).transformPosition(l);
                    dg.x[i * 3] = origin.x + w.x; dg.x[i * 3 + 1] = origin.y + w.y; dg.x[i * 3 + 2] = origin.z + w.z;
                }
            }
        }
        for (int i = 0; i + 1 < Dangle.N; i++) {
            Vector3f a = new Vector3f((float) (dg.x[i * 3] - origin.x), (float) (dg.x[i * 3 + 1] - origin.y), (float) (dg.x[i * 3 + 2] - origin.z));
            Vector3f b = new Vector3f((float) (dg.x[i * 3 + 3] - origin.x), (float) (dg.x[i * 3 + 4] - origin.y), (float) (dg.x[i * 3 + 5] - origin.z));
            drawChain(ms, vcp, a, b, light, 0.07f);
        }
    }

    @Override
    public void render(BellRingerEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        VoxelModel m = model();
        if (m.parts.isEmpty()) return;
        float t = e.age + td;
        float[] p = new float[N];
        boolean dying = e.dying();
        // ---- спящий страж и пробуждение: Безмолвный Колокол стоит на арене; тело собирается под ним из звука
        int ws = dying ? W_AWAKE : e.wake();
        boolean waking = ws == W_WAKING;
        float w = waking ? e.age - e.clientWakeStart + td : 0f;
        if (ws != W_AWAKE) {
            if (ws == W_DORMANT || w < WK_FORM) {
                float by = MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw);
                ms.push();
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-by));
                ms.scale(1 / 16f, 1 / 16f, 1 / 16f);
                float hover = 30 + 24 * sm(w / WK_RISE);
                float robe = waking ? 1 - sm((w - 4) / 14f) : 1f;
                float tremble = !waking ? 0 : w < WK_RISE ? 0.5f : 0.3f + 0.9f * sm((w - WK_RISE) / (float) (WK_FORM - WK_RISE));
                SilentBellRenderer.renderGuard(ms, vcp, light, t, hover, robe, waking && w >= 6, w - 6, tremble);
                ms.pop();
            }
            if (ws == W_DORMANT || w < WK_RISE) {
                Dangle dg0 = DANGLE.get(e.getId());
                if (dg0 != null) dg0.init = false;
                super.render(e, yaw, td, ms, vcp, light);
                return;
            }
        }
        float d = dying ? e.age - e.clientDyingStart + td : -1f;
        int a = dying ? IDLE : e.attack();
        float k = e.age - e.clientAttackStart + td;
        Springs.State sp = SPRINGS.get(e.getId());

        // ---- покой: сгорбленная стойка, тяжёлое дыхание
        float br = MathHelper.sin(t * 0.07f);
        p[TX] = 6 + br * 1.5f;
        p[HX] = -8 - br;
        p[ARX] = -6 + br * 1.5f; p[ARZ] = -5; p[FR] = -10;
        p[ALX] = -4 + br; p[ALZ] = 6; p[FL] = -14;
        p[BX] = br * 1.2f;
        float bodyYaw = MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(td, e.prevHeadYaw, e.headYaw);
        p[HY] = -MathHelper.clamp(MathHelper.wrapDegrees(headYaw - bodyYaw), -45, 45);
        p[HX] += MathHelper.clamp(e.getPitch(td), -30, 30) * 0.5f;

        // ---- тяжёлый шаг
        float lp = e.limbAnimator.getPos(td), mv = dying || waking ? 0 : MathHelper.clamp(e.limbAnimator.getSpeed(td) * 2f, 0f, 1f);
        float ph = lp * 0.55f;
        p[THR] = MathHelper.sin(ph) * 24 * mv;
        p[THL] = -p[THR];
        p[SHR] = Math.max(0, -MathHelper.sin(ph)) * 26 * mv;
        p[SHL] = Math.max(0, MathHelper.sin(ph)) * 26 * mv;
        p[ARX] += -p[THR] * 0.35f;
        p[ALX] += p[THR] * 0.35f;
        p[TZ] = MathHelper.sin(ph) * 3 * mv;
        p[LIFT] = -Math.abs(MathHelper.sin(ph)) * 1.6f * mv;
        p[BX] += MathHelper.cos(ph * 2) * 5 * mv;
        p[BZ] = MathHelper.sin(ph) * 4 * mv;
        p[HAMX] = -MathHelper.sin(ph) * 6 * mv;

        if (waking) wakePose(w, p);
        else if (!dying) attackPose(e, a, k, p, sp);
        else dyingPose(d, p);

        p[LIFT] *= BODY;
        Springs.step(sp, p, OMEGA, 0.8f);

        // ---- повороты частей
        int n = m.parts.size();
        Vector3f[] rot = new Vector3f[n];
        float[] sc = new float[n];
        for (int i = 0; i < n; i++) { rot[i] = new Vector3f(); sc[i] = 1f; }
        rot[TORSO].set(p[TX], p[TY], p[TZ]);
        rot[THIGH_R].set(p[THR], 0, 0);
        rot[SHIN_R].set(p[SHR], 0, 0);
        rot[THIGH_L].set(p[THL], 0, 0);
        rot[SHIN_L].set(p[SHL], 0, 0);
        rot[ARM_R].set(p[ARX], p[ARY], p[ARZ]);
        rot[FORE_R].set(p[FR], 0, 0);
        rot[HAND_R].set(p[HR], 0, 0);
        rot[HAMMER].set(p[HAMX], 0, 0);
        rot[ARM_L].set(p[ALX], p[ALY], p[ALZ]);
        rot[FORE_L].set(p[FL], 0, 0);
        rot[HAND_L].set(p[HL], 0, 0);
        rot[BELL].set(p[BX] + p[HX] * 0.35f, p[HY], p[BZ]);         // колокол — это голова: следует за взглядом
        Matrix4f[] mats = m.pose(rot, sc);
        float lift = p[LIFT];
        Vector3f hpiv = m.parts.get(HAMMER).pivot;

        Matrix4f bellM = null;
        boolean held = false;                                         // колокол в руках на цепи
        if (a == SWING) {
            // молот за спину и обратно
            Matrix4f back = backHammer(mats[TORSO], hpiv);
            if (k >= 6 && k < SW_CATCH + 4) mats[HAMMER] = blend(mats[HAMMER], back, sm((k - 6) / 4f));
            else if (k >= SW_CATCH + 4) mats[HAMMER] = blend(back, mats[HAMMER], sm((k - SW_CATCH - 4) / 4f));
            Matrix4f lifted = new Matrix4f().translate(0, 32 * sm((k - SW_LIFT) / 8f), 0).mul(mats[BELL]);
            if (k >= SW_LIFT && k < 28) bellM = lifted;
            else if (k >= 28 && k <= SW_TOSS + 16 + 3) {
                Matrix4f orbit = orbitMatrix(Math.min(k, SW_TOSS), lift);
                if (k < 38) bellM = blend(new Matrix4f().translate(0, 32, 0).mul(mats[BELL]), orbitMatrix(38, lift), sm((k - 28) / 10f));
                else if (k <= SW_TOSS) bellM = orbit;
                else {
                    // подброс: по дуге вверх, полный переворот, и обратно на плечи
                    float u = MathHelper.clamp((k - SW_TOSS) / 16f, 0, 1);
                    Vector3f p0 = orbit.getTranslation(new Vector3f()), p2 = new Vector3f(0, 97, 15), p1 = new Vector3f(0, 250 - lift, 15);
                    Vector3f pos = new Vector3f(p0).mul((1 - u) * (1 - u)).add(new Vector3f(p1).mul(2 * u * (1 - u))).add(new Vector3f(p2).mul(u * u));
                    Matrix4f fly = new Matrix4f().translate(pos).rotateX((float) Math.toRadians(360 * sm(u)))
                            .translate(-BELL_REST.x, -BELL_REST.y, -BELL_REST.z);
                    bellM = k < SW_TOSS + 16 ? fly : blend(fly, mats[BELL], sm((k - SW_TOSS - 16) / 3f));
                }
                held = k >= 30 && k <= SW_TOSS;
            }
        }
        // смерть: молот выпадает, колокол отделяется, растёт и накрывает тело
        if (dying) {
            if (d >= 11) {
                float s = sm((d - 11) / 6f);
                s = s * s;
                Vector3f hand = mats[HAMMER].transformPosition(new Vector3f(hpiv));
                Vector3f g = new Vector3f(-22, 7 - lift, 21);
                float bounce = d > 17 ? MathHelper.sin((d - 17) * 0.9f) * 6f * Math.max(0, 1 - (d - 17) / 10f) : 0;
                mats[HAMMER] = new Matrix4f().translate(new Vector3f(hand).lerp(g, s)).rotateX((float) Math.toRadians(-88 * s + bounce))
                        .translate(-hpiv.x, -hpiv.y, -hpiv.z);
            }
            if (d >= D_RISE) {
                bellM = coverMatrix(d, lift);
                if (d < D_RISE + 4) bellM = blend(mats[BELL], bellM, sm((d - D_RISE) / 4f));
            }
        }
        // пробуждение: огромный колокол сжимается и опускается на плечи
        if (waking && w >= WK_FORM) {
            Matrix4f big = new Matrix4f().translate(0, 54 - lift, 0).scale(1.62f, 2.33f, 1.62f).translate(0, -80, -15);
            bellM = blend(big, mats[BELL], sm((w - WK_FORM) / (float) (WK_HEAD - WK_FORM)));
        }
        if (bellM != null) {
            mats[BELL] = bellM;
            for (int i : new int[] { BELL_G, HEAD, HEAD_G }) if (i >= 0) mats[i] = bellM;
        }

        // ---- пепел: тело сереет и осыпается уже под колоколом
        float stoneY = -1e9f, crumbleY = 1e9f;
        boolean[] exempt = new boolean[n], hide = new boolean[n];
        if (dying) {
            for (int i : new int[] { BELL, BELL_G, HEAD, HEAD_G }) if (i >= 0) exempt[i] = true;
            if (d >= D_ASH) stoneY = -6 + 126 * sm((d - D_ASH) / 8f);
            if (d >= D_ASH + 6) crumbleY = 112 - 118 * sm((d - D_ASH - 6) / 24f);
            if (d >= D_COVER + 1) for (int i : new int[] { BELL, BELL_G, HEAD, HEAD_G }) if (i >= 0) hide[i] = true;
        }
        if (waking) {
            // тело проступает снизу вверх серым пеплом, затем окрашивается сверху вниз
            for (int i : new int[] { BELL, BELL_G, HEAD, HEAD_G }) if (i >= 0) exempt[i] = true;
            crumbleY = -6 + 124 * sm((w - WK_RISE) / (float) (WK_FORM - WK_RISE));
            stoneY = w < WK_FORM ? 1e9f : 120 - 126 * sm((w - WK_FORM) / 16f);
            if (w < WK_FORM) for (int i : new int[] { BELL, BELL_G, HEAD, HEAD_G }) if (i >= 0) hide[i] = true;
        }

        ms.push();
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        ms.scale(1 / 16f, 1 / 16f, 1 / 16f);
        ms.translate(0, lift, 0);
        int overlay = dying || waking ? OverlayTexture.DEFAULT_UV : LivingEntityRenderer.getOverlay(e, 0f);
        m.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX)), false, light, overlay, mats, 1f, 1f, 1f, stoneY, crumbleY, hide, exempt);
        if (stoneY > -100f) m.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(ASH)), true, light, overlay, mats, 1f, 1f, 1f, stoneY, crumbleY, hide, exempt);
        ms.pop();

        // ---- цепи: гарпун и висячая цепь с кончика колокола, на размахе — колокол на цепи в руках
        Matrix4f ent = new Matrix4f().rotateY((float) Math.toRadians(-bodyYaw)).scale(1 / 16f).translate(0, lift, 0);
        Matrix4f bellWorld = new Matrix4f(ent).mul(mats[BELL]);
        Vector3f crown = new Matrix4f(bellWorld).transformPosition(new Vector3f(0, 123, 15));
        Dangle dg = DANGLE.computeIfAbsent(e.getId(), id -> new Dangle());
        boolean shooting = !dying && a == CHAINS && e.chainState() != 0;
        if (shooting) {
            Vec3d c = e.chainPos().subtract(e.getLerpedPos(td));
            drawChain(ms, vcp, crown, new Vector3f((float) c.x, (float) c.y, (float) c.z), light, 0.11f);
            dg.init = false;
        } else if (held) {
            Vector3f hl = new Matrix4f(ent).mul(mats[HAND_L]).transformPosition(new Vector3f(19.6f, 30, 17.5f));
            Vector3f hr = new Matrix4f(ent).mul(mats[HAND_R]).transformPosition(new Vector3f(-19.6f, 30, 17.5f));
            drawChain(ms, vcp, hl.add(hr).mul(0.5f), crown, light, 0.11f);
            dg.init = false;
        } else if (waking && w < WK_FORM) {
            dg.init = false;
        } else if (!(dying && d >= D_COVER + 1)) {
            dangle(e, dg, e.getLerpedPos(td), bellWorld, ms, vcp, light);
        }
        super.render(e, yaw, td, ms, vcp, light);
    }

    /** Колокол на орбите погребального размаха (в системе модели). */
    private static Matrix4f orbitMatrix(float k, float lift) {
        float r = swingRadius(k) * 16f, phi = swingAngle(k);
        float tilt = 40 * sm((r / 16f - SWING_R0) / (SWING_R - SWING_R0));
        return new Matrix4f().translate(r * MathHelper.sin(phi), SWING_Y * 16f - lift + 10, -r * MathHelper.cos(phi))
                .rotateY(-phi).rotateX((float) Math.toRadians(tilt)).translate(-BELL_REST.x, -BELL_REST.y, -BELL_REST.z);
    }

    /** Смерть: колокол поднимается над телом, растёт до размеров Безмолвного Колокола и накрывает его. */
    private static Matrix4f coverMatrix(float d, float lift) {
        float rise = sm((d - D_RISE) / 14f), grow = sm((d - D_RISE - 2) / 16f);
        float slam = MathHelper.clamp((d - 68) / 4f, 0, 1);
        slam = slam * slam;
        float hover = d > 60 && d < 68 ? MathHelper.sin((d - 60) * 0.8f) * 2f : 0;
        float by = MathHelper.lerp(rise, 80, 175 - lift) + hover;
        by = MathHelper.lerp(slam, by, -lift);
        float bz = MathHelper.lerp(rise, 15, 0);
        float sx = MathHelper.lerp(grow, 1f, 1.62f), sy = MathHelper.lerp(grow, 1f, 2.33f);
        float wob = d > 72 ? 0 : MathHelper.sin(d * 0.35f) * 6 * rise * (1 - slam);
        return new Matrix4f().translate(0, by, bz).rotateY((float) Math.toRadians(360 * rise)).rotateX((float) Math.toRadians(wob))
                .scale(sx, sy, sx).translate(0, -80, -15);
    }

    private void attackPose(BellRingerEntity e, int a, float k, float[] p, Springs.State sp) {
        switch (a) {
            case SMASH -> {
                float w1 = sm(k / 12f) * (1 - sm((k - 13) / 4f));
                mix(p, w1, TX, -6, TY, -15, ARX, -175, ARZ, 10, FR, -42, ALX, -40, ALZ, 35, FL, -30, LIFT, -2, HX, -16, HAMX, 0);
                float w2 = sm((k - 13) / 4f) * (1 - sm((k - 32) / 16f));
                mix(p, w2, TX, 30, TY, 5, ARX, -60, ARZ, 6, FR, -5, ALX, -30, ALZ, 28, FL, -20, LIFT, -5,
                        THR, -25, SHR, 30, THL, -12, SHL, 18, HX, -22, HAMX, 0);
            }
            case TOLL -> {
                float w1 = sm(k / 9f) * (1 - sm((k - 9) / 4f));
                mix(p, w1, TX, -6, TY, -25, ALX, 1.8f, ALY, 11, ALZ, 37, FL, -143.5f, HX, -10, ARX, -20, ARZ, -14);
                float w2 = sm((k - 9) / 4f) * (1 - sm((k - 22) / 20f));
                mix(p, w2, TX, 14, TY, -16, ALX, 1.9f, ALY, -1, ALZ, 38, FL, -160, HX, 6, ARX, -14, ARZ, -18, LIFT, -2);
                if (k > 9 && k <= 14) p[BX] += 22 * sm((k - 9) / 4f);
                if (k > 14) {
                    float q = k - 14;
                    p[BX] += 22 * MathHelper.cos(q * 0.55f) * (float) Math.exp(-q * 0.06f);
                    p[BZ] += 6 * MathHelper.sin(q * 0.8f) * (float) Math.exp(-q * 0.1f);
                }
            }
            case CHAINS -> {
                // склоняет колокол-голову, целясь кончиком, — цепь вылетает с кончика; при подтягивании — рывок головой назад
                int st = e.chainState();
                float aim = sm((k - 2) / 7f);
                if (st == 2) {
                    float pull = MathHelper.sin(k * 0.9f) * 6;
                    mix(p, aim, BX, 18 + pull, TX, -10, TY, 0, LIFT, -3, THL, -24, SHL, 22, THR, 10, SHR, 12, ARX, -30, ARZ, -24, ALX, -30, ALZ, 24, FL, -50, FR, -40);
                } else {
                    float back = st == 0 && k > 14 ? sm((k - 26) / 14f) : 0;
                    mix(p, aim * (1 - back), BX, 58, HX, 0, TX, 16, LIFT, -2, ARX, -20, ARZ, -18, ALX, -20, ALZ, 18, FL, -30, THR, -16, SHR, 20);
                }
            }
            case SWING -> {
                // молот за спину → двумя руками снимает колокол → раскручивает на цепи → подкидывает и ловит головой → молот в руку
                float stow = sm(k / 5f) * (1 - sm((k - 10) / 4f));
                mix(p, stow, ARX, -175.9f, ARY, -17, ARZ, -8.5f, FR, -111.7f, TX, 4);
                float grab = sm((k - 10) / 6f) * (1 - sm((k - SW_LIFT) / 5f));
                mix(p, grab, ALX, -22.2f, ALY, -11.7f, ALZ, 42.1f, FL, -150, ARX, -18.2f, ARY, 8.8f, ARZ, -61.7f, FR, -150, TX, 0, HX, 0);
                float lift = sm((k - SW_LIFT) / 5f) * (1 - sm((k - 28) / 6f));
                mix(p, lift, ALX, -112.1f, ALY, 12.5f, ALZ, -16.1f, FL, -99.5f, ARX, -112.1f, ARY, -12.5f, ARZ, 16.1f, FR, -99.5f, TX, -4, HX, 0);
                float phi = swingAngle(k), dx = MathHelper.sin(phi), dz = -MathHelper.cos(phi);
                float spin = sm((k - 28) / 6f) * (1 - sm((k - SW_TOSS) / 3f));
                float lean = k >= 38 ? 22 * sm((k - 38) / 8f) : 0;
                mix(p, spin, ALX, -154.7f + lean * dz, ALY, 15.2f, ALZ, -21.8f + lean * dx, FL, 0, ARX, -154.7f + lean * dz, ARY, -15.2f,
                        ARZ, 21.8f + lean * dx, FR, 0, TX, -4, TY, 8 * dx, HX, 0, LIFT, -2, THL, -12, SHL, 14, THR, -6, SHR, 8);
                float toss = sm((k - SW_TOSS) / 3f) * (1 - sm((k - SW_TOSS - 6) / 5f));
                mix(p, toss, ALX, -168, ALY, 10, ALZ, -12, FL, -10, ARX, -168, ARY, -10, ARZ, 12, FR, -10, TX, -10, LIFT, 2, THR, 0, SHR, 0, THL, 0, SHL, 0);
                float catchW = sm((k - SW_TOSS - 6) / 5f) * (1 - sm((k - SW_CATCH) / 4f));
                mix(p, catchW, ALX, -112.1f, ALY, 12.5f, ALZ, -16.1f, FL, -99.5f, ARX, -112.1f, ARY, -12.5f, ARZ, 16.1f, FR, -99.5f, TX, 2);
                if (k >= SW_CATCH && k < SW_CATCH + 8) p[LIFT] -= 4 * MathHelper.sin((k - SW_CATCH) / 8f * MathHelper.PI);   // тяжесть пойманного колокола
                float retrieve = sm((k - SW_CATCH) / 4f) * (1 - sm((k - SW_CATCH - 6) / 4f));
                if (k >= SW_CATCH && k < SW_CATCH + 10) mix(p, retrieve, ARX, -175.9f, ARY, -17, ARZ, -8.5f, FR, -111.7f);
            }
            case LEAP -> {
                if (e.attack() == LEAP && sp.markAttack != e.clientAttackStart) {
                    sp.markAttack = e.clientAttackStart;
                    sp.mark = -1f;
                }
                if (sp.mark < 0 && k > 16 && e.isOnGround()) sp.mark = k;
                float w0 = sm(k / 8f) * (1 - sm((k - 10) / 3f));
                mix(p, w0, LIFT, -9, THR, -50, SHR, 62, THL, -50, SHL, 62, TX, 24, ARX, -40, ALX, -36, HX, -24);
                float air = sm((k - 10) / 4f) * (sp.mark < 0 ? 1 : 1 - sm((k - sp.mark) / 2f));
                mix(p, air, ARX, -172, FR, -44, ALX, -60, ALZ, 40, FL, -30, THR, -34, SHR, 52, THL, -20, SHL, 40, TX, -8, HX, -18);
                if (sp.mark >= 0) {
                    float w = sm((k - sp.mark) / 2f) * (1 - sm((k - sp.mark - 6) / 10f));
                    mix(p, w, TX, 30, ARX, -60, FR, -5, ALX, -30, ALZ, 28, FL, -20, LIFT, -7, THR, -30, SHR, 36, THL, -30, SHL, 36, HX, -22);
                }
            }
            default -> { }
        }
    }

    /** Пробуждение: тело собирается на коленях, поднимается и ревёт. */
    private void wakePose(float w, float[] p) {
        float kneel = 1 - sm((w - 50) / 18f);
        mix(p, kneel, LIFT, -16, THR, -84, SHR, 92, THL, -84, SHL, 92, TX, 26, HX, 22, ARX, -22, ALX, -20, ARZ, -4, ALZ, 4,
                FR, -8, FL, -8, TY, 0, TZ, 0, BX, 0, BZ, 0);
        float roar = sm((w - WK_ROAR + 3) / 4f) * (1 - sm((w - WK_END + 6) / 8f));
        mix(p, roar, TX, -10, HX, -28, ARZ, -35, ALZ, 35, ARX, -30, ALX, -30, FR, -20, FL, -20);
    }

    private void dyingPose(float d, float[] p) {
        float kneel = sm(d / 12f);
        mix(p, kneel, LIFT, -16, THR, -84, SHR, 92, THL, -84, SHL, 92, TX, 26, HX, 22, ARX, -22, ALX, -20, ARZ, -4, ALZ, 4,
                FR, -8, FL, -8, TY, 0, TZ, 0, BX, 0, BZ, 0);
        if (d >= 20 && d <= 48) {
            float q = (d - 20) % 8;
            float jolt = q < 4 ? MathHelper.sin(q / 4f * MathHelper.PI) : 0;
            p[TX] += 5 * jolt;
            p[BX] += 6 * jolt * MathHelper.sin(d * 2.3f);
            p[BZ] += 4 * jolt;
        }
        // колокол уходит вверх — тело смотрит ему вслед и опускается, раскинув руки
        float up = sm((d - 44) / 12f);
        mix(p, up, TX, -6, HX, -30, ARZ, -40, ALZ, 40, ARX, -30, ALX, -30, FR, -24, FL, -24);
        if (d > D_COVER - 4) {
            float sag = sm((d - D_COVER + 4) / 14f);
            mix(p, sag, TX, 22, ARZ, -8, ALZ, 8, ARX, -10, ALX, -10);
        }
    }

    /** Цепь из чередующихся звеньев между точками a и b (относительно сущности, в блоках). */
    static void drawChain(MatrixStack ms, VertexConsumerProvider vcp, Vector3f a, Vector3f b, int light, float w) {
        Vector3f dir = new Vector3f(b).sub(a);
        float len = dir.length();
        if (len < 1e-3f) return;
        dir.div(len);
        Vector3f up = Math.abs(dir.y) > 0.95f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f s1 = new Vector3f(dir).cross(up).normalize().mul(w);
        Vector3f s2 = new Vector3f(dir).cross(s1).normalize().mul(w);
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(CHAIN_TEX));
        MatrixStack.Entry en = ms.peek();
        Matrix4f pm = en.getPositionMatrix();
        Matrix3f nm = en.getNormalMatrix();
        float link = w * 3.2f;
        int cnt = Math.max(1, (int) Math.ceil(len / link));
        for (int i = 0; i < cnt; i++) {
            float t0 = Math.max(0, i * link - w * 0.5f), t1 = Math.min(len, (i + 1) * link + w * 0.5f);
            Vector3f p0 = new Vector3f(dir).mul(t0).add(a), p1 = new Vector3f(dir).mul(t1).add(a);
            Vector3f s = (i & 1) == 0 ? s1 : s2;
            Vector3f nrm = new Vector3f(dir).cross(s).normalize();
            quad(vc, pm, nm, p0, p1, s, nrm, light);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f pm, Matrix3f nm, Vector3f p0, Vector3f p1, Vector3f s, Vector3f n, int light) {
        int ov = OverlayTexture.DEFAULT_UV;
        vc.vertex(pm, p0.x - s.x, p0.y - s.y, p0.z - s.z).color(1f, 1f, 1f, 1f).texture(0, 1).overlay(ov).light(light).normal(nm, n.x, n.y, n.z).next();
        vc.vertex(pm, p0.x + s.x, p0.y + s.y, p0.z + s.z).color(1f, 1f, 1f, 1f).texture(1, 1).overlay(ov).light(light).normal(nm, n.x, n.y, n.z).next();
        vc.vertex(pm, p1.x + s.x, p1.y + s.y, p1.z + s.z).color(1f, 1f, 1f, 1f).texture(1, 0).overlay(ov).light(light).normal(nm, n.x, n.y, n.z).next();
        vc.vertex(pm, p1.x - s.x, p1.y - s.y, p1.z - s.z).color(1f, 1f, 1f, 1f).texture(0, 0).overlay(ov).light(light).normal(nm, n.x, n.y, n.z).next();
    }

    /** Мировая позиция колокола при размахе (для эффектов). */
    static Vec3d swingBell(BellRingerEntity e, float k, float td) {
        double[] o = swingOffset(k);
        float y = (float) Math.toRadians(MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw));
        double lx = o[0], lz = o[2];
        // локально: x — лево, z — вперёд; мир: поворот на -yaw вокруг Y
        double wx = lx * Math.cos(y) - lz * Math.sin(y);
        double wz = lx * Math.sin(y) + lz * Math.cos(y);
        return e.getLerpedPos(td).add(wx, o[1] + 10 / 16.0, wz);
    }

    @Override
    public boolean shouldRender(BellRingerEntity e, net.minecraft.client.render.Frustum f, double x, double y, double z) {
        return e.attack() == SWING || super.shouldRender(e, f, x, y, z);
    }
}
