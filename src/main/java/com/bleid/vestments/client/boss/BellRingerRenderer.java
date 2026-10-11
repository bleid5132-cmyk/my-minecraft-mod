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
    private static int TORSO, THIGH_R, SHIN_R, THIGH_L, SHIN_L, HEAD, ARM_R, FORE_R, HAND_R, HAMMER, ARM_L, FORE_L, HAND_L, BELL, BELL_G;
    static final Springs SPRINGS = new Springs();

    // индексы позы
    private static final int TX = 0, TY = 1, TZ = 2, HX = 3, HY = 4, THR = 5, SHR = 6, THL = 7, SHL = 8,
            ARX = 9, ARY = 10, ARZ = 11, FR = 12, HR = 13, ALX = 14, ALY = 15, ALZ = 16, FL = 17, HL = 18,
            HAMX = 19, BX = 20, BZ = 21, LIFT = 22, N = 23;
    private static final float[] OMEGA = { 15, 15, 15, 16, 16, 16, 16, 16, 16, 17, 17, 17, 14, 14, 17, 17, 17, 14, 14, 12, 9, 9, 15 };

    private static final Vector3f BELL_REST = new Vector3f(0, 55, -18);

    public BellRingerRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        shadowRadius = 1.5f;
        shadowOpacity = 0.8f;
    }

    static VoxelModel model() {
        if (model == null) {
            model = VoxelModel.load(MODEL);
            TORSO = model.index("torso"); THIGH_R = model.index("thigh_r"); SHIN_R = model.index("shin_r");
            THIGH_L = model.index("thigh_l"); SHIN_L = model.index("shin_l"); HEAD = model.index("head");
            ARM_R = model.index("arm_r"); FORE_R = model.index("fore_r"); HAND_R = model.index("hand_r"); HAMMER = model.index("hammer");
            ARM_L = model.index("arm_l"); FORE_L = model.index("fore_l"); HAND_L = model.index("hand_l");
            BELL = model.index("bell"); BELL_G = model.index("bell#g");
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

    @Override
    public void render(BellRingerEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        VoxelModel m = model();
        if (m.parts.isEmpty()) return;
        float t = e.age + td;
        float[] p = new float[N];
        boolean dying = e.dying();
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
        float lp = e.limbAnimator.getPos(td), mv = dying ? 0 : MathHelper.clamp(e.limbAnimator.getSpeed(td) * 2f, 0f, 1f);
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

        if (!dying) attackPose(e, a, k, p, sp);
        else dyingPose(d, p);

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
        rot[HEAD].set(p[HX], p[HY], 0);
        rot[ARM_R].set(p[ARX], p[ARY], p[ARZ]);
        rot[FORE_R].set(p[FR], 0, 0);
        rot[HAND_R].set(p[HR], 0, 0);
        rot[HAMMER].set(p[HAMX], 0, 0);
        rot[ARM_L].set(p[ALX], p[ALY], p[ALZ]);
        rot[FORE_L].set(p[FL], 0, 0);
        rot[HAND_L].set(p[HL], 0, 0);
        rot[BELL].set(p[BX], 0, p[BZ]);
        Matrix4f[] mats = m.pose(rot, sc);
        float lift = p[LIFT];

        // погребальный размах: колокол уходит на орбиту
        Matrix4f bellM = null;
        if (a == SWING && k >= 6 && k <= 116) {
            float u = k < 100 ? sm((k - 6) / 10f) : 1f - sm((k - 104) / 12f);
            float r = swingRadius(k) * 16f, phi = swingAngle(k);
            Vector3f orbit = new Vector3f(r * MathHelper.sin(phi), SWING_Y * 16f - lift, -r * MathHelper.cos(phi));
            Vector3f c = new Vector3f(BELL_REST).lerp(orbit, u);
            bellM = new Matrix4f().translate(c).rotateY(-phi * u).rotateX((float) Math.toRadians(38 * u * sm(r / 60f)))
                    .translate(-BELL_REST.x, -BELL_REST.y, -BELL_REST.z);
        }
        // смерть: молот выпадает, колокол падает за спину
        if (dying) {
            if (d >= 11) {
                float s = sm((d - 11) / 6f);
                s = s * s;
                Vector3f piv = m.parts.get(HAMMER).pivot;
                Vector3f hand = mats[HAMMER].transformPosition(new Vector3f(piv));
                Vector3f g = new Vector3f(-16, 5 - lift, 15);
                float bounce = d > 17 ? MathHelper.sin((d - 17) * 0.9f) * 6f * Math.max(0, 1 - (d - 17) / 10f) : 0;
                mats[HAMMER] = new Matrix4f().translate(new Vector3f(hand).lerp(g, s)).rotateX((float) Math.toRadians(-88 * s + bounce))
                        .translate(-piv.x, -piv.y, -piv.z);
            }
            if (d >= D_BELL) {
                float s = MathHelper.clamp((d - D_BELL) / 4.5f, 0f, 1f);
                s = s * s;
                float wob = d > D_BELL + 4.5f ? MathHelper.sin((d - D_BELL - 4.5f) * 0.7f) * 7f * Math.max(0, 1 - (d - D_BELL - 4.5f) / 18f) : 0;
                float dy = -(34 + lift) * s, dz = -8 * s;
                bellM = new Matrix4f().translate(0, dy, dz).translate(0, 34, -18).rotateX((float) Math.toRadians(wob))
                        .translate(0, -34, 18);
            }
        }
        if (bellM != null) {
            mats[BELL] = bellM;
            if (BELL_G >= 0) mats[BELL_G] = bellM;
        }

        // ---- пепел: тело сереет снизу вверх и осыпается сверху, остаются ряса, молот и колокол
        float stoneY = -1e9f, crumbleY = 1e9f;
        boolean[] exempt = new boolean[n], hide = new boolean[n];
        if (dying) {
            exempt[BELL] = true;
            if (BELL_G >= 0) exempt[BELL_G] = true;
            exempt[HAMMER] = true;
            if (d >= D_ASH) stoneY = -6 + 90 * sm((d - D_ASH) / 9f);
            if (d >= D_ASH + 6) crumbleY = 80 - 72 * sm((d - D_ASH - 6) / 16f);
            if (d >= 150) crumbleY = 8 - 10 * sm((d - 150) / 50f);
            if (d >= D_SPAWN + 1) {
                hide[BELL] = true;
                if (BELL_G >= 0) hide[BELL_G] = true;
            }
            if (d >= 190) hide[HAMMER] = true;
        }

        ms.push();
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        ms.scale(1 / 16f, 1 / 16f, 1 / 16f);
        ms.translate(0, lift, 0);
        int overlay = dying ? OverlayTexture.DEFAULT_UV : LivingEntityRenderer.getOverlay(e, 0f);
        m.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX)), false, light, overlay, mats, 1f, 1f, 1f, stoneY, crumbleY, hide, exempt);
        if (stoneY > -100f) m.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(ASH)), true, light, overlay, mats, 1f, 1f, 1f, stoneY, crumbleY, hide, exempt);
        ms.pop();

        // ---- цепи
        Matrix4f ent = new Matrix4f().rotateY((float) Math.toRadians(-bodyYaw)).scale(1 / 16f).translate(0, lift, 0);
        Vector3f hand = new Matrix4f(ent).mul(mats[HAND_L]).transformPosition(new Vector3f(14, 22, 13));
        if (!dying && a == CHAINS && e.chainState() != 0) {
            Vec3d c = e.chainPos().subtract(e.getLerpedPos(td));
            drawChain(ms, vcp, hand, new Vector3f((float) c.x, (float) c.y, (float) c.z), light, 0.11f);
        }
        if (bellM != null && a == SWING) {
            Vector3f top = new Matrix4f(ent).mul(bellM).transformPosition(new Vector3f(0, 76, -18));
            drawChain(ms, vcp, hand, top, light, 0.12f);
        }
        super.render(e, yaw, td, ms, vcp, light);
    }

    private void attackPose(BellRingerEntity e, int a, float k, float[] p, Springs.State sp) {
        switch (a) {
            case SMASH -> {
                float w1 = sm(k / 12f) * (1 - sm((k - 13) / 4f));
                mix(p, w1, TX, -6, TY, -15, ARX, -175, ARZ, 10, FR, -42, ALX, -165, ALZ, -24, FL, -38, LIFT, -2, HX, -16, HAMX, 0);
                float w2 = sm((k - 13) / 4f) * (1 - sm((k - 32) / 16f));
                mix(p, w2, TX, 30, TY, 5, ARX, -68, ARZ, 6, FR, -5, ALX, -60, ALZ, -22, FL, -12, LIFT, -5,
                        THR, -25, SHR, 30, THL, -12, SHL, 18, HX, -22, HAMX, 0);
            }
            case TOLL -> {
                float w1 = sm(k / 10f) * (1 - sm((k - 9) / 4f));
                mix(p, w1, TY, 20, ARX, -130, FR, -22, TX, -2, HX, -12);
                float w2 = sm((k - 9) / 4f) * (1 - sm((k - 22) / 20f));
                mix(p, w2, TX, -12, TY, -10, ARX, -206, ARZ, 8, FR, -62, HX, 12, ALX, 20, ALZ, 20);
                if (k > 14) {
                    float q = k - 14;
                    p[BX] += 18 * MathHelper.sin(q * 0.55f) * (float) Math.exp(-q * 0.07f);
                    p[BZ] += 5 * MathHelper.sin(q * 0.8f) * (float) Math.exp(-q * 0.1f);
                }
            }
            case CHAINS -> {
                int st = e.chainState();
                float w1 = sm(k / 8f) * (1 - sm((k - 9) / 3f));
                mix(p, w1, ALX, 48, ALZ, 16, FL, -32, TY, 26, TX, 2);
                float w2 = sm((k - 9) / 3f);
                if (st == 2) {
                    float pull = MathHelper.sin(k * 0.9f) * 6;
                    mix(p, w2, ALX, -55 + pull, ALZ, -4, FL, -62, TX, -8, TY, 14, LIFT, -2, THL, -22, SHL, 20, HX, -6);
                } else {
                    float back = st == 0 ? sm((k - 24) / 14f) : 0;
                    mix(p, w2 * (1 - back), ALX, -100, ALZ, -6, FL, -4, TY, -20, TX, 14, HX, -14);
                }
            }
            case SWING -> {
                float u = sm(k / 12f) * (1 - sm((k - 102) / 12f));
                float phi = swingAngle(k);
                float dz = -MathHelper.cos(phi), dx = MathHelper.sin(phi);
                mix(p, u, ALX, -180 + 28 * dz, ALZ, 28 * dx, ALY, 0, FL, -6, TX, -6, TY, 10 * dx, HX, -24,
                        ARX, -8, ARZ, -12, FR, -14, LIFT, -2, THL, -12, SHL, 14, THR, -6, SHR, 8);
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
                mix(p, air, ARX, -172, FR, -44, ALX, -150, ALZ, -20, FL, -40, THR, -34, SHR, 52, THL, -20, SHL, 40, TX, -8, HX, -18);
                if (sp.mark >= 0) {
                    float w = sm((k - sp.mark) / 2f) * (1 - sm((k - sp.mark - 6) / 10f));
                    mix(p, w, TX, 30, ARX, -68, FR, -5, ALX, -60, ALZ, -22, FL, -12, LIFT, -7, THR, -30, SHR, 36, THL, -30, SHL, 36, HX, -22);
                }
            }
            default -> { }
        }
    }

    private void dyingPose(float d, float[] p) {
        float kneel = sm(d / 12f);
        mix(p, kneel, LIFT, -16, THR, -84, SHR, 92, THL, -84, SHL, 92, TX, 26, HX, 22, ARX, -22, ALX, -20, ARZ, -4, ALZ, 4,
                FR, -8, FL, -8, TY, 0, TZ, 0, BX, 0, BZ, 0);
        if (d >= 25 && d <= 62) {
            float q = (d - 25) % 10;
            float jolt = q < 4 ? MathHelper.sin(q / 4f * MathHelper.PI) : 0;
            p[TX] += 5 * jolt;
            p[BX] += 6 * jolt * MathHelper.sin(d * 2.3f);
            p[BZ] += 4 * jolt;
        }
        float up = sm((d - 40) / 14f);
        mix(p, up, TX, 2, HX, -28, ARZ, -34, ALZ, 34, ARX, -12, ALX, -12, FR, -18, FL, -18);
        if (d > D_ASH) {
            float sag = sm((d - D_ASH) / 18f);
            mix(p, sag, TX, 18, HX, 30, ARZ, -8, ALZ, 8);
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
        return e.getLerpedPos(td).add(wx, o[1], wz);
    }

    @Override
    public boolean shouldRender(BellRingerEntity e, net.minecraft.client.render.Frustum f, double x, double y, double z) {
        return e.attack() == SWING || super.shouldRender(e, f, x, y, z);
    }
}
