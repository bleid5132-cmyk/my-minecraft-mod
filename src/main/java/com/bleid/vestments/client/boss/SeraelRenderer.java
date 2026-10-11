package com.bleid.vestments.client.boss;

import com.bleid.vestments.boss.SeraelEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

/**
 * Рендер Сераэль по воксельной модели. Анимации: ходьба, дыхание крыльев, взгляд, вращение нимба
 * и отдельная поза для каждой атаки.
 */
@Environment(EnvType.CLIENT)
public class SeraelRenderer extends EntityRenderer<SeraelEntity> {
    private static final Identifier TEX = new Identifier("vestments", "textures/entity/serael.png");
    private static final Identifier MODEL = new Identifier("vestments", "models/entity/serael.vxm");
    private static VoxelModel model;
    private static int TORSO, LEG_R, LEG_L, HEAD, HALO, ARM_R, FORE_R, SWORD, ARM_L, FORE_L, WING_R, WING_L;

    public SeraelRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        shadowRadius = 1.4f;
        shadowOpacity = 0.7f;
    }

    static VoxelModel model() {
        if (model == null) {
            model = VoxelModel.load(MODEL);
            TORSO = model.index("torso"); LEG_R = model.index("leg_r"); LEG_L = model.index("leg_l"); HEAD = model.index("head");
            HALO = model.index("halo"); ARM_R = model.index("arm_r"); FORE_R = model.index("fore_r"); SWORD = model.index("sword");
            ARM_L = model.index("arm_l"); FORE_L = model.index("fore_l"); WING_R = model.index("wing_r"); WING_L = model.index("wing_l");
        }
        return model;
    }

    @Override
    public Identifier getTexture(SeraelEntity e) {
        return TEX;
    }

    private static float sm(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    /** Вход за [0..in], выход за [1-out..1]. */
    private static float env(float k, float in, float out) {
        return Math.min(sm(k / in), sm((1 - k) / out));
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }

    /** Поза: углы в градусах. */
    static final class Pose {
        float torsoX, torsoY, headX, headY, legR, legL, armRX, armRZ, foreR, armLX, armLZ, foreL, wingZ, wingY, swordX, haloScale = 1f, lift;
    }

    @Override
    public void render(SeraelEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        VoxelModel m = model();
        if (m.parts.isEmpty()) return;
        float t = e.age + td;
        double dx = e.getX() - e.prevX, dz = e.getZ() - e.prevZ;
        float mv = MathHelper.clamp((float) Math.sqrt(dx * dx + dz * dz) / 0.22f, 0f, 1f);
        Pose p = new Pose();

        // ---- покой и ходьба
        float walk = t * 0.32f;
        p.legR = -22f * mv * MathHelper.sin(walk);
        p.legL = 22f * mv * MathHelper.sin(walk);
        p.torsoX = 4f * mv + 1.5f * MathHelper.sin(t * 0.06f);
        p.lift = Math.abs(MathHelper.cos(walk)) * 0.8f * mv;
        p.armRX = -6f;
        p.foreR = -10f - 3f * MathHelper.sin(t * 0.06f);
        p.armLX = 6f - 14f * mv * MathHelper.sin(walk);
        p.armLZ = 7f + 2f * MathHelper.sin(t * 0.05f);
        p.foreL = -12f;
        p.wingZ = 6f + 6f * MathHelper.sin(t * 0.07f);
        p.wingY = 10f * mv + 3f * MathHelper.sin(t * 0.05f + 1f);
        p.headY = -MathHelper.clamp(MathHelper.wrapDegrees(MathHelper.lerpAngleDegrees(td, e.prevHeadYaw, e.headYaw)
                - MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw)), -35f, 35f);
        p.headX = MathHelper.clamp(MathHelper.lerp(td, e.prevPitch, e.getPitch()), -30f, 30f);

        // ---- атаки
        int a = e.attack();
        if (a > 0 && a < SeraelEntity.DURATION.length) {
            float tick = e.age - e.clientAttackStart + td;
            float k = MathHelper.clamp(tick / SeraelEntity.DURATION[a], 0f, 1f);
            switch (a) {
                case SeraelEntity.SWING -> {
                    float w = sm(tick / 14f), s = sm((tick - 14f) / 5f), r = sm((tick - 30f) / 14f);
                    float tw = lerp(s, -42f * w, 48f), az = lerp(s, -38f * w, 26f), lz = lerp(s, -8f * w, 36f);
                    p.torsoY = lerp(r, tw, 0);
                    p.armRX = lerp(r, -72f * w, p.armRX);
                    p.armRZ = lerp(r, az, 0);
                    p.foreR = lerp(r, -22f * w, p.foreR);
                    p.armLX = lerp(r, -64f * w, p.armLX);
                    p.armLZ = lerp(r, lz, p.armLZ);
                    p.foreL = lerp(r, -42f * w, p.foreL);
                    p.torsoX += 10f * s * (1 - r);
                    p.wingZ += 14f * w * (1 - r);
                }
                case SeraelEntity.SLASH -> {
                    float w = sm(tick / 9f), s = sm((tick - 9f) / 3f), r = sm((tick - 13f) / 11f);
                    float ax = lerp(s, -165f * w, -45f);
                    p.armRX = lerp(r, ax, p.armRX);
                    p.foreR = lerp(r, -18f * w, p.foreR);
                    p.armLX = lerp(r, lerp(s, -150f * w, -50f), p.armLX);
                    p.armLZ = lerp(r, -6f, p.armLZ);
                    p.foreL = lerp(r, -30f, p.foreL);
                    p.torsoX += (22f * s - 8f * w) * (1 - r);
                    p.legR += -10f * s * (1 - r);
                }
                case SeraelEntity.PETALS -> {
                    float w = env(k, 0.18f, 0.15f), thrust = sm((tick - 46f) / 4f) * (1 - sm((tick - 64f) / 8f));
                    p.armRX = lerp(w, p.armRX, lerp(thrust, -172f, -70f));
                    p.foreR = lerp(w, p.foreR, -8f);
                    p.armLX = lerp(w, p.armLX, lerp(thrust, -160f, -40f));
                    p.armLZ = lerp(w, p.armLZ, 14f);
                    p.headX = lerp(w, p.headX, lerp(thrust, -26f, 10f));
                    p.wingZ = lerp(w, p.wingZ, 24f);
                    p.wingY = lerp(w, p.wingY, -6f);
                }
                case SeraelEntity.HALO -> {
                    float w = env(k, 0.25f, 0.2f);
                    p.armRZ = lerp(w, 0, -68f);
                    p.armLZ = lerp(w, p.armLZ, 68f);
                    p.armRX = lerp(w, p.armRX, -12f);
                    p.armLX = lerp(w, p.armLX, -12f);
                    p.headX = lerp(w, p.headX, -32f);
                    p.wingZ = lerp(w, p.wingZ, 30f);
                    p.torsoX -= 8f * w;
                    float pulse = tick < 30 ? 1f + 0.22f * MathHelper.sin(tick * (0.4f + tick * 0.02f)) * w
                            : 1f + 1.6f * sm((tick - 30f) / 4f) * (1 - sm((tick - 36f) / 10f));
                    p.haloScale = pulse;
                }
                case SeraelEntity.DIVE -> {
                    float rise = 1 - sm((tick - 22f) / 6f), hover = sm((tick - 22f) / 6f) * (1 - sm((tick - 40f) / 4f));
                    float dive = sm((tick - 40f) / 4f) * (1 - sm((tick - 62f) / 8f)), land = sm((tick - 62f) / 6f) * (1 - sm((tick - 78f) / 20f));
                    float flap = MathHelper.sin(tick * 0.9f);
                    p.wingZ = 10f + 34f * flap * rise + (24f + 8f * MathHelper.sin(tick * 0.3f)) * hover - 14f * dive + 20f * land;
                    p.wingY = 52f * dive - 8f * hover;
                    p.legR += 24f * (rise + hover) + 40f * dive - 26f * land;
                    p.legL += 30f * (rise + hover) + 46f * dive + 10f * land;
                    p.torsoX += 52f * dive + 26f * land - 6f * hover;
                    p.armRX = lerp(Math.max(hover, dive), p.armRX, -150f);
                    p.armRX = lerp(land, p.armRX, -40f);
                    p.foreR = lerp(Math.max(hover, dive), p.foreR, -20f);
                    p.armLX = lerp(Math.max(hover, dive), p.armLX, -140f);
                    p.armLX = lerp(land, p.armLX, -40f);
                    p.foreL = lerp(Math.max(hover, dive), p.foreL, -30f);
                    p.headX -= 14f * hover;
                    p.lift -= 6f * land;
                }
                case SeraelEntity.BARRIER -> {
                    float w = env(k, 0.08f, 0.06f);
                    p.armRX = lerp(w, p.armRX, -40f);
                    p.foreR = lerp(w, p.foreR, -26f);
                    p.armLX = lerp(w, p.armLX, -48f);
                    p.armLZ = lerp(w, p.armLZ, -18f);
                    p.foreL = lerp(w, p.foreL, -34f);
                    p.wingY = lerp(w, p.wingY, -56f);
                    p.wingZ = lerp(w, p.wingZ, -4f + 2f * MathHelper.sin(t * 0.4f));
                    p.headX = lerp(w, p.headX, 14f);
                    p.torsoX += 8f * w;
                    p.legR -= 12f * w;
                }
                case SeraelEntity.STUNNED -> {
                    float w = env(k, 0.1f, 0.2f), sway = MathHelper.sin(t * 0.25f) * 3f;
                    p.torsoX += 30f * w;
                    p.headX = lerp(w, p.headX, 34f + sway);
                    p.armRX = lerp(w, p.armRX, 18f);
                    p.foreR = lerp(w, p.foreR, -4f);
                    p.armLX = lerp(w, p.armLX, 22f);
                    p.wingZ = lerp(w, p.wingZ, -26f + sway);
                    p.legR += -26f * w;
                    p.legL += 8f * w;
                    p.lift -= 5f * w;
                }
                case SeraelEntity.ROAR -> {
                    float w = env(k, 0.22f, 0.25f), tr = 3f * w * MathHelper.sin(t * 2.4f);
                    p.armRZ = lerp(w, 0, -52f);
                    p.armLZ = lerp(w, p.armLZ, 52f);
                    p.headX = lerp(w, p.headX, -36f);
                    p.wingZ = lerp(w, p.wingZ, 40f) + tr;
                    p.wingY = lerp(w, p.wingY, -10f);
                    p.torsoX -= 12f * w;
                    p.haloScale = 1f + 0.3f * w;
                }
                default -> { }
            }
        }

        // ---- смерть: оседает на колени и запрокидывает голову
        float death = e.deathTime > 0 ? MathHelper.clamp((e.deathTime + td) / 30f, 0f, 1f) : 0f;
        if (death > 0) {
            p.legR = lerp(death, p.legR, -70f);
            p.legL = lerp(death, p.legL, -20f);
            p.torsoX = lerp(death, p.torsoX, 10f);
            p.headX = lerp(death, p.headX, -40f);
            p.armRX = lerp(death, p.armRX, -20f);
            p.armLX = lerp(death, p.armLX, 10f);
            p.wingZ = lerp(death, p.wingZ, -30f);
            p.lift = lerp(death, p.lift, -16f);
        }

        int n = m.parts.size();
        Vector3f[] rot = new Vector3f[n];
        float[] sc = new float[n];
        for (int i = 0; i < n; i++) { rot[i] = new Vector3f(); sc[i] = 1f; }
        rot[TORSO].set(p.torsoX, p.torsoY, 0);
        rot[LEG_R].set(p.legR, 0, 0);
        rot[LEG_L].set(p.legL, 0, 0);
        rot[HEAD].set(p.headX, p.headY, 0);
        rot[HALO].set(0, t * 1.6f, 0);
        sc[HALO] = p.haloScale;
        rot[ARM_R].set(p.armRX, 0, p.armRZ);
        rot[FORE_R].set(p.foreR, 0, 0);
        rot[ARM_L].set(p.armLX, 0, p.armLZ);
        rot[FORE_L].set(p.foreL, 0, 0);
        rot[WING_L].set(0, p.wingY, p.wingZ);
        rot[WING_R].set(0, -p.wingY, -p.wingZ);

        ms.push();
        float bodyYaw = MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        ms.scale(1 / 16f, 1 / 16f, 1 / 16f);
        ms.translate(0, p.lift, 0);
        int overlay = LivingEntityRenderer.getOverlay(e, 0f);
        float dim = 1f - 0.35f * death;
        m.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX)), light, overlay, rot, sc, dim, dim, dim);
        ms.pop();
        super.render(e, yaw, td, ms, vcp, light);
    }
}
