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
    private static final Identifier STONE = new Identifier("vestments", "textures/entity/serael_stone.png");
    private static final Identifier MODEL = new Identifier("vestments", "models/entity/serael.vxm");
    private static VoxelModel model;
    private static int TORSO, THIGH_R, SHIN_R, THIGH_L, SHIN_L, HEAD, HALO, ARM_R, FORE_R, HAND_R, ARM_L, FORE_L, HAND_L, WING_R, WING_L;
    private static final Map<Integer, Hair> HAIR = new HashMap<>();
    private static final Map<Integer, Smooth> SMOOTH = new HashMap<>();

    static final class Smooth {
        float[] x, v;
        long nanos;
    }
    private static int HAIR1, HAIR2, HAIR3, LOCK_R, LOCK_L;

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
            HAIR1 = model.index("hair1"); HAIR2 = model.index("hair2"); HAIR3 = model.index("hair3");
            LOCK_R = model.index("lock_r"); LOCK_L = model.index("lock_l");
        }
        return model;
    }

    static void forget(int id) {
        HAIR.remove(id);
        SMOOTH.remove(id);
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
        float wingZ, wingY, wingX, haloScale = 1f, lift, pitch, armRY;

        /** Плавно приблизить к позе цели с весом w. */
        void blend(Pose o, float w) {
            torsoX = lerp(w, torsoX, o.torsoX); torsoY = lerp(w, torsoY, o.torsoY); torsoZ = lerp(w, torsoZ, o.torsoZ);
            headX = lerp(w, headX, o.headX); headY = lerp(w, headY, o.headY);
            thR = lerp(w, thR, o.thR); shR = lerp(w, shR, o.shR); thL = lerp(w, thL, o.thL); shL = lerp(w, shL, o.shL);
            thRZ = lerp(w, thRZ, o.thRZ); thLZ = lerp(w, thLZ, o.thLZ);
            armRX = lerp(w, armRX, o.armRX); armRZ = lerp(w, armRZ, o.armRZ); foreR = lerp(w, foreR, o.foreR); handR = lerp(w, handR, o.handR);
            armLX = lerp(w, armLX, o.armLX); armLZ = lerp(w, armLZ, o.armLZ); foreL = lerp(w, foreL, o.foreL); handL = lerp(w, handL, o.handL);
            wingZ = lerp(w, wingZ, o.wingZ); wingY = lerp(w, wingY, o.wingY); wingX = lerp(w, wingX, o.wingX);
            haloScale = lerp(w, haloScale, o.haloScale); lift = lerp(w, lift, o.lift); pitch = lerp(w, pitch, o.pitch); armRY = lerp(w, armRY, o.armRY);
        }

        float[] arr() {
            return new float[] { torsoX, torsoY, torsoZ, headX, headY, thR, shR, thL, shL, thRZ, thLZ, armRX, armRZ, foreR, handR,
                    armLX, armLZ, foreL, handL, wingZ, wingY, wingX, haloScale, lift, pitch, armRY };
        }

        void set(float[] a) {
            torsoX = a[0]; torsoY = a[1]; torsoZ = a[2]; headX = a[3]; headY = a[4]; thR = a[5]; shR = a[6]; thL = a[7]; shL = a[8];
            thRZ = a[9]; thLZ = a[10]; armRX = a[11]; armRZ = a[12]; foreR = a[13]; handR = a[14]; armLX = a[15]; armLZ = a[16];
            foreL = a[17]; handL = a[18]; wingZ = a[19]; wingY = a[20]; wingX = a[21]; haloScale = a[22]; lift = a[23]; pitch = a[24]; armRY = a[25];
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
                    // одноручный широкий взмах: рука вытянута горизонтально, клинок продолжает её линию и смотрит
                    // на противника; замах вправо-назад, удар справа налево, левая рука уходит назад для равновесия
                    Pose wind = key(base, q -> { q.lift = -3f; q.torsoY = -34f; q.torsoX = -2f; q.thR = 8f; q.shR = 34f; q.thL = -28f; q.shL = 36f;
                        q.armRX = -88f; q.armRY = -80f; q.armRZ = 0f; q.foreR = -6f; q.handR = 52f;
                        q.armLX = -55f; q.armLZ = 28f; q.foreL = -40f; q.handL = 0f; q.wingZ = 24f; q.wingY = -6f; });
                    Pose hit = key(base, q -> { q.lift = -3.5f; q.torsoY = 44f; q.torsoX = 10f; q.thR = -32f; q.shR = 30f; q.thL = 10f; q.shL = 26f;
                        q.armRX = -90f; q.armRY = 72f; q.armRZ = 0f; q.foreR = 0f; q.handR = 52f;
                        q.armLX = 35f; q.armLZ = 46f; q.foreL = -25f; q.wingZ = 10f; q.wingY = 30f; });
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
                    // клинок впереди, тело продолжает линию клинка: руки над головой, ноги вместе, крылья назад вдоль тела,
                    // всё тело разворачивается по направлению полёта (вверх — при взлёте, к цели — в пике)
                    float flap = MathHelper.sin(tk * 0.9f);
                    double vx = e.getX() - e.prevX, vy = e.getY() - e.prevY, vz = e.getZ() - e.prevZ;
                    float yr = e.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
                    double vf = -Math.sin(yr) * vx + Math.cos(yr) * vz;
                    float vp = (float) Math.toDegrees(Math.atan2(vf, vy));
                    boolean fast = vf * vf + vy * vy > 0.09;
                    Pose spear = key(base, q -> { q.torsoX = 0f; q.torsoY = 0f; q.headX = -12f; q.headY = 0f;
                        q.armRX = -178f; q.armRZ = 7f; q.foreR = 0f; q.handR = 52f; q.armLX = -176f; q.armLZ = -10f; q.foreL = -6f; q.handL = 0f;
                        q.thR = 4f; q.shR = 6f; q.thL = 7f; q.shL = 9f; q.thRZ = 3f; q.thLZ = -3f; q.lift = 0f; });
                    Pose rise = key(spear, q -> { q.wingZ = 14f + 42f * flap; q.wingX = -12f * flap; q.wingY = 10f; q.pitch = 0f; });
                    Pose hover = key(spear, q -> { q.wingZ = 30f + 8f * MathHelper.sin(tk * 0.3f); q.wingY = -6f; q.pitch = 72f; q.headX = -30f; });
                    Pose dive = key(spear, q -> { q.wingZ = -32f; q.wingY = 58f; q.wingX = 6f; q.pitch = fast ? MathHelper.clamp(vp, 30f, 172f) : 110f; q.headX = -24f; });
                    Pose land = key(base, q -> { q.lift = -14f; q.torsoX = 28f; q.thR = -84f; q.shR = 112f; q.thL = -6f; q.shL = 104f; q.pitch = 0f;
                        q.armRX = -60f; q.foreR = -10f; q.handR = 62f; q.armLX = -40f; q.armLZ = 20f; q.foreL = -20f; q.wingZ = 34f; q.wingY = 6f; q.headX = 18f; });
                    p = base.copy();
                    p.blend(rise, sm(tk / 6f));
                    p.blend(hover, sm((tk - 22f) / 8f));
                    p.blend(dive, sm((tk - 39f) / 3f));
                    p.blend(land, sm((tk - 58f) / 3f));
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
                case SeraelEntity.DASH -> {
                    // низкий рывок: корпус вперёд, крылья назад, клинок отведён для удара
                    Pose crouch = key(base, q -> { q.lift = -3.5f; q.torsoX = 22f; q.thR = -20f; q.shR = 56f; q.thL = 14f; q.shL = 48f;
                        q.armRX = 24f; q.armRZ = -10f; q.foreR = -20f; q.handR = -30f; q.armLX = -20f; q.foreL = -40f; q.wingZ = 22f; q.wingY = 20f; });
                    float run = MathHelper.sin(tk * 1.4f);
                    Pose rush = key(base, q -> { q.lift = -2f; q.torsoX = 38f; q.headX = -24f; q.thR = -44f * run - 10f; q.shR = 40f + 30f * Math.max(0f, run);
                        q.thL = 44f * run - 10f; q.shL = 40f + 30f * Math.max(0f, -run); q.armRX = 38f; q.armRZ = -16f; q.foreR = -14f; q.handR = -36f;
                        q.armLX = 30f; q.armLZ = 12f; q.foreL = -30f; q.wingY = 64f; q.wingZ = -6f; q.wingX = 8f; });
                    p = base.copy();
                    p.blend(crouch, sm(tk / 3f));
                    p.blend(rush, sm((tk - 3f) / 2.5f));
                    p.blend(crouch, sm((tk - 11f) / 4f));
                }
                case SeraelEntity.BACKSTEP -> {
                    // отскок назад взмахом крыльев: ноги поджаты, клинок в защите
                    Pose jump = key(base, q -> { q.lift = 1f; q.torsoX = -14f; q.headX = 8f; q.thR = -46f; q.shR = 84f; q.thL = -30f; q.shL = 92f;
                        q.armRX = -54f; q.foreR = -40f; q.handR = -10f; q.armLX = -30f; q.armLZ = 26f; q.foreL = -50f;
                        q.wingZ = 44f; q.wingY = -26f; q.wingX = -10f; });
                    Pose land = key(base, q -> { q.lift = -4f; q.torsoX = 12f; q.thR = -30f; q.shR = 60f; q.thL = 8f; q.shL = 46f;
                        q.armRX = -30f; q.foreR = -34f; q.armLX = 0f; q.armLZ = 20f; q.wingZ = 14f; q.wingY = 6f; });
                    p = base.copy();
                    p.blend(jump, sm(tk / 3f));
                    p.blend(land, sm((tk - 9f) / 4f));
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

        // ---- поражение: опускается на колени, вонзает клинок перед собой и каменеет от ног к нимбу
        float st = e.statue() ? e.age - e.clientStatueStart + td : -1f;
        float stoneY = -1e9f;
        if (st >= 0) {
            Pose kneel = new Pose();
            kneel.lift = -23f; kneel.thR = -6f; kneel.shR = 92f; kneel.thL = -2f; kneel.shL = 92f; kneel.thRZ = -2f; kneel.thLZ = 2f;
            kneel.torsoX = 12f; kneel.headX = 32f; kneel.armRX = -44f; kneel.foreR = -18f; kneel.handR = 70f;
            kneel.armLX = -46f; kneel.armLZ = -16f; kneel.foreL = -30f; kneel.wingZ = -18f; kneel.wingY = 26f; kneel.wingX = 4f;
            p.blend(kneel, sm(st / 18f));
            stoneY = (st - 16f) / 40f * 130f - 6f;
        }

        // ---- сглаживание: каждый сустав догоняет целевую позу пружиной (без рывков между позами и атаками)
        Smooth sp = SMOOTH.computeIfAbsent(e.getId(), id -> new Smooth());
        float[] target = p.arr();
        if (sp.x == null) { sp.x = target.clone(); sp.v = new float[target.length]; }
        long now = System.nanoTime();
        float dts = sp.nanos == 0 ? 0f : Math.min(0.1f, (now - sp.nanos) / 1e9f);
        sp.nanos = now;
        if (net.minecraft.client.MinecraftClient.getInstance().isPaused()) dts = 0f;
        float om = 19f, zeta = 0.78f;
        int steps = Math.max(1, (int) Math.ceil(dts / 0.005f));
        float h = dts / steps;
        for (int st = 0; st < steps; st++) {
            for (int i = 0; i < target.length; i++) {
                float acc = om * om * (target[i] - sp.x[i]) - 2f * zeta * om * sp.v[i];
                sp.v[i] += acc * h;
                sp.x[i] += sp.v[i] * h;
            }
        }
        p.set(sp.x);

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
        rot[HALO].set(0, (st >= 0 ? Math.min(t, e.clientStatueStart + 50) : t) * 1.6f, 0);
        sc[HALO] = p.haloScale;
        rot[ARM_R].set(p.armRX, p.armRY, p.armRZ);
        rot[FORE_R].set(p.foreR, 0, 0);
        rot[HAND_R].set(p.handR, 0, 0);
        rot[ARM_L].set(p.armLX, 0, p.armLZ);
        rot[FORE_L].set(p.foreL, 0, 0);
        rot[HAND_L].set(p.handL, 0, 0);
        rot[WING_L].set(p.wingX, p.wingY, p.wingZ);
        rot[WING_R].set(p.wingX, -p.wingY, -p.wingZ);
        float bodyYaw0 = MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw);
        if (HAIR1 >= 0) hair(e, HAIR.computeIfAbsent(e.getId(), id -> new Hair()), p, t, bodyYaw0, rot, st > 45f);
        Matrix4f[] mats = m.pose(rot, sc);

        float bodyYaw = MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw);
        final float lift = p.lift;
        ms.push();
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        ms.scale(1 / 16f, 1 / 16f, 1 / 16f);
        ms.translate(0, lift, 0);
        if (Math.abs(p.pitch) > 0.01f) {
            ms.translate(0, 55, 0);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.pitch));
            ms.translate(0, -55, 0);
        }
        int overlay = LivingEntityRenderer.getOverlay(e, 0f);
        float dim = 1f;
        m.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX)), false, light, overlay, mats, dim, dim, dim, stoneY);
        if (stoneY > -100f) m.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(STONE)), true, light, overlay, mats, 1f, 1f, 1f, stoneY);

        ms.pop();
        super.render(e, yaw, td, ms, vcp, light);
    }

    /**
     * Лёгкая физика волос: пружины с затуханием для трёх звеньев косы и двух прядей. Волосы свисают вниз
     * независимо от наклона головы и тела, отлетают назад при движении, вбок — при поворотах и шаге в сторону.
     */
    static final class Hair {
        final float[] ax = new float[5], az = new float[5], vx = new float[5], vz = new float[5];
        long nanos;
        float prevYaw = Float.NaN;
    }

    private static void hair(SeraelEntity e, Hair h, Pose p, float t, float bodyYaw, Vector3f[] rot, boolean frozen) {
        long now = System.nanoTime();
        float dt = h.nanos == 0 ? 0f : Math.min(0.05f, (now - h.nanos) / 1e9f);
        h.nanos = now;
        if (net.minecraft.client.MinecraftClient.getInstance().isPaused() || frozen) dt = 0f;
        float y = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        double vx = e.getX() - e.prevX, vy = e.getY() - e.prevY, vz = e.getZ() - e.prevZ;
        float fwd = (float) (-Math.sin(y) * vx + Math.cos(y) * vz), left = (float) (Math.cos(y) * vx + Math.sin(y) * vz);
        float yawRate = Float.isNaN(h.prevYaw) ? 0f : MathHelper.wrapDegrees(bodyYaw - h.prevYaw) / Math.max(dt * 20f, 1e-3f);
        h.prevYaw = bodyYaw;
        float dynX = MathHelper.clamp(fwd * 70f + Math.max(0f, (float) -vy) * 45f, -10f, 75f);
        float dynZ = MathHelper.clamp(-left * 60f - yawRate * 1.6f, -45f, 45f);
        float comp = -(p.torsoX + p.headX) * 0.9f - p.pitch * 0.6f;
        float[] tx = { comp + dynX + 3f * MathHelper.sin(t * 0.05f), dynX * 0.35f + 4f * MathHelper.sin(t * 0.07f + 1f), dynX * 0.3f + 5f * MathHelper.sin(t * 0.09f + 2f),
                comp * 0.8f + dynX * 0.3f, comp * 0.8f + dynX * 0.3f };
        float[] tz = { dynZ, dynZ * 0.4f + 3f * MathHelper.sin(t * 0.06f), dynZ * 0.35f + 3f * MathHelper.sin(t * 0.08f + 1f), dynZ * 0.4f - 4f, dynZ * 0.4f + 4f };
        float[] k = { 70f, 48f, 36f, 60f, 60f };
        for (int i = 0; i < 5; i++) {
            float c = 2f * (float) Math.sqrt(k[i]) * 0.35f;
            int steps = Math.max(1, (int) Math.ceil(dt / 0.008f));
            float sd = dt / steps;
            for (int s = 0; s < steps; s++) {
                h.vx[i] += (k[i] * (tx[i] - h.ax[i]) - c * h.vx[i]) * sd;
                h.vz[i] += (k[i] * (tz[i] - h.az[i]) - c * h.vz[i]) * sd;
                h.ax[i] += h.vx[i] * sd;
                h.az[i] += h.vz[i] * sd;
            }
        }
        rot[HAIR1].set(h.ax[0], 0, h.az[0]);
        rot[HAIR2].set(h.ax[1], 0, h.az[1]);
        rot[HAIR3].set(h.ax[2], 0, h.az[2]);
        rot[LOCK_R].set(h.ax[3], 0, h.az[3]);
        rot[LOCK_L].set(h.ax[4], 0, h.az[4]);
    }
}
