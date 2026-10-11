package com.bleid.vestments.client.boss;

import com.bleid.vestments.boss.SeraelEntity;
import java.util.HashMap;
import java.util.Map;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Рендер Сераэль: воксельная модель с суставами (колени, локти, запястья), юбка — симулируемая ткань.
 * Анимации: боевая стойка с дыханием, шаг со сгибом коленей, поза для каждой атаки.
 */
@Environment(EnvType.CLIENT)
public class SeraelRenderer extends EntityRenderer<SeraelEntity> {
    private static final Identifier TEX = new Identifier("vestments", "textures/entity/serael.png");
    private static final Identifier DRESS = new Identifier("vestments", "textures/entity/serael_dress.png");
    private static final Identifier MODEL = new Identifier("vestments", "models/entity/serael.vxm");
    private static VoxelModel model;
    private static int TORSO, THIGH_R, SHIN_R, THIGH_L, SHIN_L, HEAD, HALO, ARM_R, FORE_R, HAND_R, ARM_L, FORE_L, HAND_L, WING_R, WING_L;
    private static final Map<Integer, ClothSkirt> SKIRTS = new HashMap<>();

    public SeraelRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        shadowRadius = 1.4f;
        shadowOpacity = 0.7f;
    }

    static VoxelModel model() {
        if (model == null) {
            model = VoxelModel.load(MODEL);
            TORSO = model.index("torso"); THIGH_R = model.index("thigh_r"); SHIN_R = model.index("shin_r");
            THIGH_L = model.index("thigh_l"); SHIN_L = model.index("shin_l"); HEAD = model.index("head"); HALO = model.index("halo");
            ARM_R = model.index("arm_r"); FORE_R = model.index("fore_r"); HAND_R = model.index("hand_r");
            ARM_L = model.index("arm_l"); FORE_L = model.index("fore_l"); HAND_L = model.index("hand_l");
            WING_R = model.index("wing_r"); WING_L = model.index("wing_l");
        }
        return model;
    }

    static void forget(int id) {
        SKIRTS.remove(id);
    }

    @Override
    public Identifier getTexture(SeraelEntity e) {
        return TEX;
    }

    private static float sm(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    private static float env(float k, float in, float out) {
        return Math.min(sm(k / in), sm((1 - k) / out));
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }

    /** Поза: углы суставов в градусах (x — вперёд/назад, y — поворот, z — в сторону). */
    static final class Pose {
        float torsoX, torsoY, torsoZ, headX, headY;
        float thR, shR, thL, shL, thRZ, thLZ;
        float armRX, armRZ, foreR, handR, armLX, armLZ, foreL, handL;
        float wingZ, wingY, wingX, haloScale = 1f, lift;

        /** Плавно приблизить к позе цели с весом w. */
        void blend(Pose o, float w) {
            torsoX = lerp(w, torsoX, o.torsoX); torsoY = lerp(w, torsoY, o.torsoY); torsoZ = lerp(w, torsoZ, o.torsoZ);
            headX = lerp(w, headX, o.headX); headY = lerp(w, headY, o.headY);
            thR = lerp(w, thR, o.thR); shR = lerp(w, shR, o.shR); thL = lerp(w, thL, o.thL); shL = lerp(w, shL, o.shL);
            thRZ = lerp(w, thRZ, o.thRZ); thLZ = lerp(w, thLZ, o.thLZ);
            armRX = lerp(w, armRX, o.armRX); armRZ = lerp(w, armRZ, o.armRZ); foreR = lerp(w, foreR, o.foreR); handR = lerp(w, handR, o.handR);
            armLX = lerp(w, armLX, o.armLX); armLZ = lerp(w, armLZ, o.armLZ); foreL = lerp(w, foreL, o.foreL); handL = lerp(w, handL, o.handL);
            wingZ = lerp(w, wingZ, o.wingZ); wingY = lerp(w, wingY, o.wingY); wingX = lerp(w, wingX, o.wingX);
            haloScale = lerp(w, haloScale, o.haloScale); lift = lerp(w, lift, o.lift);
        }

        Pose copy() {
            Pose p = new Pose();
            p.blend(this, 1f);
            return p;
        }
    }

    /** Ключевая поза: заполняется лямбдой. */
    private static Pose key(Pose base, java.util.function.Consumer<Pose> f) {
        Pose p = base.copy();
        f.accept(p);
        return p;
    }

    @Override
    public void render(SeraelEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        VoxelModel m = model();
        if (m.parts.isEmpty()) return;
        float t = e.age + td;
        double dx = e.getX() - e.prevX, dz = e.getZ() - e.prevZ;
        float mv = MathHelper.clamp((float) Math.sqrt(dx * dx + dz * dz) / 0.22f, 0f, 1f);

        // ---- боевая стойка: колени чуть согнуты, клинок опущен, крылья дышат
        Pose p = new Pose();
        float br = MathHelper.sin(t * 0.06f);
        p.lift = -0.9f + 0.3f * br;
        p.torsoX = 3f + 1.5f * br; p.torsoY = 6f;
        p.thR = -14f; p.shR = 22f; p.thL = 6f; p.shL = 18f; p.thRZ = -3f; p.thLZ = 4f;
        p.armRX = -12f; p.armRZ = -4f; p.foreR = -26f; p.handR = 8f - 2f * br;
        p.armLX = 4f; p.armLZ = 9f + 2f * br; p.foreL = -22f; p.handL = -6f;
        p.wingZ = 7f + 6f * MathHelper.sin(t * 0.07f); p.wingY = 12f + 3f * MathHelper.sin(t * 0.05f + 1f); p.wingX = 0f;
        p.headY = -MathHelper.clamp(MathHelper.wrapDegrees(MathHelper.lerpAngleDegrees(td, e.prevHeadYaw, e.headYaw)
                - MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw)), -35f, 35f);
        p.headX = MathHelper.clamp(MathHelper.lerp(td, e.prevPitch, e.getPitch()), -30f, 30f) - 2f * br;

        // ---- шаг: бедро вперёд-назад, колено сгибается на выносе ноги, таз качается, рука в такт
        if (mv > 0.01f) {
            float ph = t * 0.32f;
            Pose wk = p.copy();
            float sR = MathHelper.sin(ph), sL = MathHelper.sin(ph + MathHelper.PI);
            wk.thR = -28f * sR - 6f; wk.thL = -28f * sL - 6f;
            wk.shR = 12f + 48f * Math.max(0f, MathHelper.sin(ph + 1.9f)); wk.shL = 12f + 48f * Math.max(0f, MathHelper.sin(ph + MathHelper.PI + 1.9f));
            wk.thRZ = -2f; wk.thLZ = 2f;
            wk.lift = -1.2f + 1.1f * Math.abs(MathHelper.cos(ph));
            wk.torsoX = 7f; wk.torsoY = 6f - 6f * sR; wk.torsoZ = 2f * sR;
            wk.armLX = 20f * sR; wk.foreL = -24f - 14f * Math.max(0f, sR);
            wk.armRX = -16f - 6f * sL; wk.foreR = -30f;
            wk.wingY = 22f; wk.wingZ = 4f + 4f * MathHelper.sin(ph * 0.5f);
            p.blend(wk, mv);
        }

        // ---- атаки
        int a = e.attack();
        if (a > 0 && a < SeraelEntity.DURATION.length) {
            float tk = e.age - e.clientAttackStart + td;
            float k = MathHelper.clamp(tk / SeraelEntity.DURATION[a], 0f, 1f);
            Pose base = p;
            switch (a) {
                case SeraelEntity.SWING -> {
                    // замах вправо двумя руками, широкий горизонтальный удар справа налево, проводка, возврат
                    Pose wind = key(base, q -> { q.lift = -3f; q.torsoY = -38f; q.torsoX = -2f; q.thR = 8f; q.shR = 34f; q.thL = -28f; q.shL = 36f;
                        q.armRX = -74f; q.armRZ = -34f; q.foreR = -38f; q.handR = -24f; q.armLX = -58f; q.armLZ = -24f; q.foreL = -62f; q.handL = -10f;
                        q.wingZ = 24f; q.wingY = -6f; });
                    Pose hit = key(base, q -> { q.lift = -3.5f; q.torsoY = 46f; q.torsoX = 10f; q.thR = -32f; q.shR = 30f; q.thL = 10f; q.shL = 26f;
                        q.armRX = -78f; q.armRZ = 32f; q.foreR = -8f; q.handR = -6f; q.armLX = -66f; q.armLZ = 40f; q.foreL = -30f;
                        q.wingZ = 10f; q.wingY = 30f; });
                    p = base.copy();
                    p.blend(wind, sm(tk / 14f));
                    p.blend(hit, sm((tk - 14f) / 5f));
                    p.blend(base, sm((tk - 30f) / 14f));
                }
                case SeraelEntity.SLASH -> {
                    Pose up = key(base, q -> { q.torsoX = -10f; q.armRX = -168f; q.foreR = -42f; q.handR = -24f; q.armLX = -152f; q.foreL = -52f;
                        q.thR = -10f; q.shR = 20f; q.wingZ = 22f; });
                    Pose down = key(base, q -> { q.lift = -4f; q.torsoX = 26f; q.armRX = -58f; q.foreR = -6f; q.handR = 14f; q.armLX = -52f; q.foreL = -20f;
                        q.thR = -42f; q.shR = 46f; q.thL = 22f; q.shL = 24f; q.wingZ = -4f; q.wingY = 26f; });
                    p = base.copy();
                    p.blend(up, sm(tk / 9f));
                    p.blend(down, sm((tk - 9f) / 3f));
                    p.blend(base, sm((tk - 13f) / 11f));
                }
                case SeraelEntity.PETALS -> {
                    Pose sky = key(base, q -> { q.torsoX = -12f; q.headX = -30f; q.armRX = -176f; q.armRZ = -4f; q.foreR = -14f; q.handR = -12f;
                        q.armLX = -170f; q.armLZ = 6f; q.foreL = -24f; q.wingZ = 30f; q.wingY = -8f; q.lift = 0.5f; q.shR = 8f; q.shL = 8f; });
                    Pose plunge = key(base, q -> { q.lift = -9f; q.torsoX = 30f; q.headX = 10f; q.armRX = -42f; q.foreR = -10f; q.handR = 44f;
                        q.armLX = -46f; q.armLZ = -10f; q.foreL = -26f; q.thR = -62f; q.shR = 92f; q.thL = 14f; q.shL = 70f; q.wingZ = 26f; q.wingY = 10f; });
                    p = base.copy();
                    p.blend(sky, sm(tk / 12f));
                    p.blend(plunge, sm((tk - 45f) / 4f));
                    p.blend(base, sm((tk - 62f) / 10f));
                }
                case SeraelEntity.HALO -> {
                    Pose open = key(base, q -> { q.armRZ = -74f; q.armLZ = 74f; q.armRX = -10f; q.armLX = -10f; q.foreR = -16f; q.foreL = -16f;
                        q.headX = -34f; q.torsoX = -14f; q.wingZ = 36f; q.wingY = -10f; q.lift = 0.5f; q.shR = 10f; q.shL = 10f; });
                    p = base.copy();
                    p.blend(open, env(k, 0.25f, 0.2f));
                    p.haloScale = tk < 30 ? 1f + 0.22f * MathHelper.sin(tk * (0.4f + tk * 0.02f)) * sm(tk / 8f)
                            : 1f + 1.6f * sm((tk - 30f) / 4f) * (1 - sm((tk - 36f) / 10f));
                }
                case SeraelEntity.DIVE -> {
                    float flap = MathHelper.sin(tk * 0.9f);
                    Pose rise = key(base, q -> { q.wingZ = 10f + 40f * flap; q.wingX = -10f * flap; q.thR = -24f; q.shR = 70f; q.thL = -10f; q.shL = 64f;
                        q.armRX = -120f; q.foreR = -30f; q.armLX = -30f; q.foreL = -40f; q.torsoX = 8f; });
                    Pose hover = key(base, q -> { q.wingZ = 28f + 8f * MathHelper.sin(tk * 0.3f); q.wingY = -8f; q.thR = -16f; q.shR = 46f; q.thL = -6f; q.shL = 40f;
                        q.armRX = -162f; q.foreR = -34f; q.handR = -30f; q.armLX = -150f; q.foreL = -44f; q.headX = 14f; q.torsoX = -6f; });
                    Pose dive = key(base, q -> { q.torsoX = 54f; q.wingY = 60f; q.wingZ = -10f; q.thR = 34f; q.shR = 14f; q.thL = 40f; q.shL = 20f;
                        q.armRX = -150f; q.foreR = -10f; q.handR = 10f; q.armLX = -140f; q.foreL = -20f; q.headX = -20f; });
                    Pose land = key(base, q -> { q.lift = -14f; q.torsoX = 28f; q.thR = -84f; q.shR = 112f; q.thL = -6f; q.shL = 104f;
                        q.armRX = -60f; q.foreR = -10f; q.handR = 62f; q.armLX = -40f; q.armLZ = 20f; q.foreL = -20f; q.wingZ = 34f; q.wingY = 6f; q.headX = 18f; });
                    p = base.copy();
                    p.blend(rise, sm(tk / 6f));
                    p.blend(hover, sm((tk - 22f) / 6f));
                    p.blend(dive, sm((tk - 40f) / 4f));
                    p.blend(land, sm((tk - 58f) / 4f));
                    p.blend(base, sm((tk - 80f) / 18f));
                }
                case SeraelEntity.BARRIER -> {
                    Pose kneel = key(base, q -> { q.lift = -10f; q.thR = -72f; q.shR = 104f; q.thL = 2f; q.shL = 96f; q.torsoX = 14f; q.headX = 20f;
                        q.armRX = -52f; q.foreR = -30f; q.handR = 72f; q.armLX = -56f; q.armLZ = -20f; q.foreL = -42f; q.handL = 10f;
                        q.wingY = -64f; q.wingZ = -4f + 2f * MathHelper.sin(t * 0.4f); q.wingX = 6f; });
                    p = base.copy();
                    p.blend(kneel, env(k, 0.08f, 0.06f));
                }
                case SeraelEntity.STUNNED -> {
                    float sway = MathHelper.sin(t * 0.25f) * 3f;
                    Pose slump = key(base, q -> { q.lift = -9f; q.thR = -62f; q.shR = 96f; q.thL = -22f; q.shL = 72f; q.torsoX = 34f; q.torsoZ = sway;
                        q.headX = 36f + sway; q.armRX = 18f; q.foreR = -6f; q.handR = 30f; q.armLX = 24f; q.foreL = -10f; q.wingZ = -28f + sway; q.wingY = 20f; });
                    p = base.copy();
                    p.blend(slump, env(k, 0.1f, 0.2f));
                }
                case SeraelEntity.ROAR -> {
                    float tr = 3f * MathHelper.sin(t * 2.4f);
                    Pose roar = key(base, q -> { q.torsoX = -18f; q.headX = -40f; q.armRZ = -60f; q.armLZ = 60f; q.armRX = -20f; q.armLX = -20f;
                        q.foreR = -34f; q.foreL = -34f; q.wingZ = 42f + tr; q.wingY = -12f; q.thRZ = -9f; q.thLZ = 9f; q.haloScale = 1.3f; });
                    p = base.copy();
                    p.blend(roar, env(k, 0.22f, 0.25f));
                }
                default -> { }
            }
        }

        // ---- смерть: падает на колени, запрокидывает голову, крылья опадают
        float death = e.deathTime > 0 ? MathHelper.clamp((e.deathTime + td) / 30f, 0f, 1f) : 0f;
        if (death > 0) {
            Pose d = key(p, q -> { q.lift = -16f; q.thR = -88f; q.shR = 120f; q.thL = -84f; q.shL = 118f; q.torsoX = 8f; q.headX = -42f;
                q.armRX = -14f; q.foreR = -6f; q.armLX = 10f; q.wingZ = -32f; q.wingY = 24f; });
            p.blend(d, death);
        }

        // ---- сборка поворотов по частям
        int n = m.parts.size();
        Vector3f[] rot = new Vector3f[n];
        float[] sc = new float[n];
        for (int i = 0; i < n; i++) { rot[i] = new Vector3f(); sc[i] = 1f; }
        rot[TORSO].set(p.torsoX, p.torsoY, p.torsoZ);
        rot[THIGH_R].set(p.thR, 0, p.thRZ);
        rot[SHIN_R].set(p.shR, 0, 0);
        rot[THIGH_L].set(p.thL, 0, p.thLZ);
        rot[SHIN_L].set(p.shL, 0, 0);
        rot[HEAD].set(p.headX, p.headY, 0);
        rot[HALO].set(0, t * 1.6f, 0);
        sc[HALO] = p.haloScale;
        rot[ARM_R].set(p.armRX, 0, p.armRZ);
        rot[FORE_R].set(p.foreR, 0, 0);
        rot[HAND_R].set(p.handR, 0, 0);
        rot[ARM_L].set(p.armLX, 0, p.armLZ);
        rot[FORE_L].set(p.foreL, 0, 0);
        rot[HAND_L].set(p.handL, 0, 0);
        rot[WING_L].set(p.wingX, p.wingY, p.wingZ);
        rot[WING_R].set(p.wingX, -p.wingY, -p.wingZ);
        Matrix4f[] mats = m.pose(rot, sc);

        float bodyYaw = MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw);
        final float lift = p.lift;
        ms.push();
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        ms.scale(1 / 16f, 1 / 16f, 1 / 16f);
        ms.translate(0, lift, 0);
        int overlay = LivingEntityRenderer.getOverlay(e, 0f);
        float dim = 1f - 0.35f * death;
        m.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX)), light, overlay, mats, dim, dim, dim);

        // ---- юбка-ткань
        ClothSkirt skirt = SKIRTS.computeIfAbsent(e.getId(), id -> new ClothSkirt());
        double ex = MathHelper.lerp(td, e.prevX, e.getX()), ey = MathHelper.lerp(td, e.prevY, e.getY()), ez = MathHelper.lerp(td, e.prevZ, e.getZ());
        float cy = MathHelper.cos(bodyYaw * MathHelper.RADIANS_PER_DEGREE), sy = MathHelper.sin(bodyYaw * MathHelper.RADIANS_PER_DEGREE);
        ClothSkirt.Frame fr = new ClothSkirt.Frame() {
            @Override
            public void toWorld(float x, float y, float z, double[] out) {
                // поворот на −yaw вокруг Y: (x, z) → (x·cos + z·(−sin)… ) как в ms.multiply
                double lx = x / 16.0, ly = (y + lift) / 16.0, lz = z / 16.0;
                out[0] = ex + lx * cy - lz * sy;
                out[1] = ey + ly;
                out[2] = ez + lx * sy + lz * cy;
            }

            @Override
            public void toLocal(double x, double y, double z, float[] out) {
                double wx = x - ex, wy = y - ey, wz = z - ez;
                out[0] = (float) ((wx * cy + wz * sy) * 16);
                out[1] = (float) (wy * 16 - lift);
                out[2] = (float) ((-wx * sy + wz * cy) * 16);
            }
        };
        float[][] caps = {
                cap(mats[TORSO], -3.2f, 47f, 0, 3.2f, 47f, 0, 7.4f),
                cap(mats[TORSO], 0, 49f, 0, 0, 56f, 0, 6.5f),
                cap(mats[THIGH_R], -4.2f, 46f, 0, -4.7f, 26.5f, 0.8f, 5.6f),
                cap(mats[THIGH_L], 4.2f, 46f, 0, 4.7f, 26.5f, 0.8f, 5.6f),
                cap(mats[SHIN_R], -4.7f, 26.5f, 0.8f, -4.9f, 7f, -0.4f, 3.9f),
                cap(mats[SHIN_L], 4.7f, 26.5f, 0.8f, 4.9f, 7f, -0.4f, 3.9f),
        };
        float wind = 0.6f + 1.6f * mv + (a == SeraelEntity.DIVE || a == SeraelEntity.ROAR || a == SeraelEntity.HALO ? 2.5f : 0f);
        if (!net.minecraft.client.MinecraftClient.getInstance().isPaused()) skirt.simulate(fr, mats[TORSO], caps, t / 20f, wind);
        skirt.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(DRESS)), light, overlay, dim, dim, dim);
        ms.pop();
        super.render(e, yaw, td, ms, vcp, light);
    }

    private static float[] cap(Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float r) {
        Vector3f a = m.transformPosition(ax, ay, az, new Vector3f()), b = m.transformPosition(bx, by, bz, new Vector3f());
        return new float[] { a.x, a.y, a.z, b.x, b.y, b.z, r };
    }
}
