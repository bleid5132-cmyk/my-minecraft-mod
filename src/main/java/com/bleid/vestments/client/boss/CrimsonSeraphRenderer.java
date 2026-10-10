package com.bleid.vestments.client.boss;

import com.bleid.vestments.boss.CrimsonSeraphEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Рендер Багрового Серафима по воксельной модели из постройки игрока. Двигаются ноги, руки, голова,
 * крылья (и нимб); у каждой атаки — своя поза, в покое серафим парит и медленно взмахивает крыльями.
 */
@Environment(EnvType.CLIENT)
public class CrimsonSeraphRenderer extends EntityRenderer<CrimsonSeraphEntity> {
    private static final Identifier TEX = new Identifier("vestments", "textures/entity/crimson_seraph.png");
    private static final Identifier MODEL = new Identifier("vestments", "models/entity/crimson_seraph.vxm");
    /** Блоков на воксель: модель 138 вокселей = 10 блоков. */
    private static final float S = 10f / 138f;
    private static VoxelModel model;
    private static int TORSO, LEG_F, LEG_B, HEAD, HALO, ARM_A, ARM_B, WING_A, WING_B;

    public CrimsonSeraphRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        shadowRadius = 2.2f;
        shadowOpacity = 0.6f;
    }

    private static VoxelModel model() {
        if (model == null) {
            model = VoxelModel.load(MODEL, TEX);
            TORSO = model.index("torso"); LEG_F = model.index("leg_f"); LEG_B = model.index("leg_b");
            HEAD = model.index("head"); HALO = model.index("halo"); ARM_A = model.index("arm_a"); ARM_B = model.index("arm_b");
            WING_A = model.index("wing_a"); WING_B = model.index("wing_b");
        }
        return model;
    }

    @Override
    public Identifier getTexture(CrimsonSeraphEntity e) {
        return TEX;
    }

    private static float smooth(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    /** Огибающая позы атаки: плавно входит, держится, плавно выходит. */
    private static float env(float k, float in, float out) {
        return Math.min(smooth(k / in), smooth((1 - k) / out));
    }

    @Override
    public void render(CrimsonSeraphEntity e, float yaw, float td, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        VoxelModel m = model();
        if (m.parts.isEmpty()) return;
        int n = m.parts.size();
        Vector3f[] rot = new Vector3f[n];
        for (int i = 0; i < n; i++) rot[i] = new Vector3f();

        float t = e.age + td;
        double dx = e.getX() - e.prevX, dz = e.getZ() - e.prevZ;
        float mv = MathHelper.clamp((float) Math.sqrt(dx * dx + dz * dz) / 0.35f, 0f, 1f);
        float bob = MathHelper.sin(t * 0.07f) * 0.25f;
        float ph = t * (0.11f + 0.08f * mv);

        // ---- покой и полёт
        float wingRoll = 4f + 10f * (1 + mv * 0.8f) * MathHelper.sin(ph);
        float wingYaw = 4f * MathHelper.sin(ph + 1.2f) + 8f * mv;
        float lean = 9f * mv;
        float legF = -4f + 4f * MathHelper.sin(t * 0.05f) + 16f * mv + 7f * mv * MathHelper.sin(t * 0.2f);
        float legB = 3f - 4f * MathHelper.sin(t * 0.05f) + 16f * mv - 7f * mv * MathHelper.sin(t * 0.2f);
        float armRoll = 3f * MathHelper.sin(t * 0.06f), armPitch = 12f * mv * MathHelper.sin(t * 0.2f) + 10f * mv;
        float headYaw = MathHelper.clamp(MathHelper.wrapDegrees(MathHelper.lerpAngleDegrees(td, e.prevHeadYaw, e.headYaw)
                - MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw)), -50f, 50f);
        float headPitch = MathHelper.clamp(MathHelper.lerp(td, e.prevPitch, e.getPitch()), -35f, 35f);
        float tremble = 0f, haloGlow = 0f;

        // ---- атаки
        int a = e.attack();
        if (a != CrimsonSeraphEntity.IDLE && a < CrimsonSeraphEntity.DURATION.length) {
            float k = MathHelper.clamp((e.age - e.clientAttackStart + td) / CrimsonSeraphEntity.DURATION[a], 0f, 1f);
            switch (a) {
                case CrimsonSeraphEntity.SWEEP -> {
                    float wind = smooth(k / 0.43f), hit = smooth((k - 0.43f) / 0.17f), back = smooth((k - 0.65f) / 0.35f);
                    float r = MathHelper.lerp(hit, 34f * wind, -30f), y = MathHelper.lerp(hit, 36f * wind, -42f);
                    wingRoll = MathHelper.lerp(back, r, wingRoll);
                    wingYaw = MathHelper.lerp(back, y, wingYaw);
                    lean += (14f * hit - 6f * wind) * (1 - back);
                    armRoll += 25f * wind * (1 - back);
                }
                case CrimsonSeraphEntity.VOLLEY -> {
                    float w = env(k, 0.25f, 0.2f);
                    armPitch = MathHelper.lerp(w, armPitch, -72f);
                    armRoll = MathHelper.lerp(w, armRoll, -12f);
                    wingRoll = MathHelper.lerp(w, wingRoll, 26f);
                    wingYaw = MathHelper.lerp(w, wingYaw, -8f);
                    tremble = 3f * w;
                }
                case CrimsonSeraphEntity.JUDGEMENT -> {
                    float w = env(k, 0.2f, 0.18f);
                    armRoll = MathHelper.lerp(w, armRoll, 78f);
                    armPitch = MathHelper.lerp(w, armPitch, -18f);
                    headPitch = MathHelper.lerp(w, headPitch, -28f);
                    wingRoll = MathHelper.lerp(w, wingRoll, 32f);
                    lean -= 6f * w;
                    haloGlow = w;
                    tremble = k > 0.6f && k < 0.75f ? 2.5f : 0f;
                }
                case CrimsonSeraphEntity.DIVE -> {
                    float wind = env(k, 0.3f, 0.2f) * (1 - smooth((k - 0.28f) / 0.08f));
                    float dive = smooth((k - 0.28f) / 0.08f) * smooth((1 - k) / 0.3f);
                    wingRoll = MathHelper.lerp(wind, wingRoll, 36f);
                    wingRoll = MathHelper.lerp(dive, wingRoll, -14f);
                    wingYaw = MathHelper.lerp(dive, wingYaw, 52f);
                    lean += 34f * dive - 8f * wind;
                    legF += 30f * dive;
                    legB += 34f * dive;
                    armPitch = MathHelper.lerp(dive, armPitch, 45f);
                }
                case CrimsonSeraphEntity.ROAR -> {
                    float w = env(k, 0.25f, 0.25f);
                    armRoll = MathHelper.lerp(w, armRoll, 55f);
                    armPitch = MathHelper.lerp(w, armPitch, -10f);
                    headPitch = MathHelper.lerp(w, headPitch, -32f);
                    wingRoll = MathHelper.lerp(w, wingRoll, 42f);
                    wingYaw = MathHelper.lerp(w, wingYaw, -10f);
                    lean -= 10f * w;
                    tremble = 4f * w;
                    haloGlow = w;
                }
                default -> { }
            }
        }
        if (tremble > 0) {
            wingRoll += tremble * MathHelper.sin(t * 2.3f);
            armRoll += tremble * 0.5f * MathHelper.sin(t * 2.9f);
        }

        // ---- смерть: замирает, запрокидывается и опускается
        float death = e.deathTime > 0 ? MathHelper.clamp((e.deathTime + td) / 70f, 0f, 1f) : 0f;
        if (death > 0) {
            lean -= 18f * death;
            headPitch = MathHelper.lerp(death, headPitch, -40f);
            wingRoll = MathHelper.lerp(death, wingRoll, -20f) + 3f * MathHelper.sin(t * 3f) * death;
            armRoll = MathHelper.lerp(death, armRoll, 30f);
        }

        rot[TORSO].set(lean, 0, 0);
        rot[LEG_F].set(legF, 0, 0);
        rot[LEG_B].set(legB, 0, 0);
        rot[HEAD].set(headPitch, headYaw, 0);
        rot[HALO].set(0, t * 1.2f, 0);
        rot[ARM_A].set(armPitch, 0, armRoll);
        rot[ARM_B].set(armPitch, 0, -armRoll);
        rot[WING_A].set(0, wingYaw, wingRoll);
        rot[WING_B].set(0, -wingYaw, -wingRoll);

        // ---- матрицы частей
        matrices.push();
        matrices.translate(0, bob - death * 1.2f, 0);
        float bodyYaw = MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        matrices.scale(S, S, S);
        Matrix4f base = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(matrices.peek().getPositionMatrix());
        matrices.pop();
        Matrix4f[] local = new Matrix4f[n], mats = new Matrix4f[n];
        for (int i = 0; i < n; i++) {
            VoxelModel.Part p = m.parts.get(i);
            Matrix4f l = p.parent >= 0 && local[p.parent] != null ? new Matrix4f(local[p.parent]) : new Matrix4f();
            Vector3f r = rot[i];
            l.translate(p.pivot)
                    .rotate(new Quaternionf().rotateY(r.y * MathHelper.RADIANS_PER_DEGREE)
                            .rotateX(r.x * MathHelper.RADIANS_PER_DEGREE).rotateZ(r.z * MathHelper.RADIANS_PER_DEGREE))
                    .translate(-p.pivot.x, -p.pivot.y, -p.pivot.z);
            local[i] = l;
            mats[i] = new Matrix4f(base).mul(l);
        }

        // ---- свет: блок/небо в точке, вспышка при уроне
        float bl = LightmapTextureManager.getBlockLightCoordinates(light) / 15f;
        float sl = LightmapTextureManager.getSkyLightCoordinates(light) / 15f;
        float sky = e.getWorld() != null ? 1f - e.getWorld().getAmbientDarkness() / 11f : 1f;
        float br = 0.28f + 0.72f * Math.max(bl, sl * sky);
        float cr = br, cg = br, cb = br;
        if (e.hurtTime > 0 || death > 0) { cg *= 0.55f; cb *= 0.55f; }
        m.draw(mats, cr, cg, cb, HALO);
        super.render(e, yaw, td, matrices, vcp, light);
    }
}
