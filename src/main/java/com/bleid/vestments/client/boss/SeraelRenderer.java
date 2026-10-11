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
import net.minecraft.client.render.VertexConsumer;
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

    /** Жёсткость пружин по суставам: предплечья и кисти мягче (запаздывают — удар «хлещет»), крылья ещё мягче. */
    private static final float[] OMEGA = { 19, 19, 19, 14, 14, 19, 19, 19, 19, 19, 19, 18, 18, 13, 12, 18, 18, 13, 12, 11, 11, 11, 19, 19, 17, 18 };

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
        HALOS.remove(id);
        TRAILS.remove(id);
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
        p.armRX = -8f; p.armRZ = -10f; p.armRY = -84f; p.foreR = -26f - 3f * br; p.handR = -14f - 2f * br;
        p.armLX = 2f; p.armLZ = 10f + 2f * br; p.foreL = -36f - 4f * br; p.handL = -10f;
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
            wk.armLX = 22f * sR; wk.foreL = -34f - 26f * Math.max(0f, -sR);
            wk.armRX = -16f - 8f * sL; wk.foreR = -42f - 10f * Math.max(0f, sL);
            wk.wingY = 22f; wk.wingZ = 4f + 4f * MathHelper.sin(ph * 0.5f);
            p.blend(wk, mv);
        }

        // ---- атаки
        int a = e.attack();
        if (a > 0 && a < SeraelEntity.DURATION.length) {
            float tk = e.age - e.clientAttackStart + td;
            float k = MathHelper.clamp(tk / SeraelEntity.DURATION[a], 0f, 1f);
            Pose base = p.copy();
            base.armRY = 0f;
            p = base.copy();
            switch (a) {
                case SeraelEntity.SWING -> {
                    // одноручный широкий взмах: рука вытянута горизонтально, клинок продолжает её линию и смотрит
                    // на противника; замах вправо-назад, удар справа налево, левая рука уходит назад для равновесия
                    Pose wind = key(base, q -> { q.lift = -3f; q.torsoY = -34f; q.torsoX = -2f; q.thR = 8f; q.shR = 34f; q.thL = -28f; q.shL = 36f;
                        q.armRX = -62f; q.armRY = -84f; q.armRZ = 0f; q.foreR = -62f; q.handR = 64f;
                        q.armLX = -55f; q.armLZ = 28f; q.foreL = -74f; q.handL = 0f; q.wingZ = 24f; q.wingY = -6f; });
                    Pose hit = key(base, q -> { q.lift = -3.5f; q.torsoY = 44f; q.torsoX = 10f; q.thR = -32f; q.shR = 30f; q.thL = 10f; q.shL = 26f;
                        q.armRX = -62f; q.armRY = 72f; q.armRZ = 0f; q.foreR = -2f; q.handR = 50f;
                        q.armLX = 35f; q.armLZ = 46f; q.foreL = -58f; q.wingZ = 10f; q.wingY = 30f; });
                    p = base.copy();
                    p.blend(wind, sm(tk / 14f));
                    p.blend(hit, sm((tk - 14f) / 5f));
                    p.blend(base, sm((tk - 30f) / 14f));
                }
                case SeraelEntity.SLASH -> {
                    // одноручный удар сверху: клинок продолжает руку — над головой назад, затем вниз-вперёд на противника;
                    // левая рука выставлена вперёд-в сторону и на ударе уходит назад для равновесия
                    Pose up = key(base, q -> { q.torsoX = -10f; q.torsoY = -10f; q.armRX = -160f; q.armRZ = -6f; q.foreR = -82f; q.handR = 46f;
                        q.armLX = -40f; q.armLZ = 34f; q.foreL = -62f; q.handL = 0f; q.thR = -10f; q.shR = 20f; q.wingZ = 22f; });
                    Pose down = key(base, q -> { q.lift = -4f; q.torsoX = 26f; q.torsoY = 6f; q.armRX = -50f; q.armRZ = 0f; q.foreR = -4f; q.handR = 52f;
                        q.armLX = 30f; q.armLZ = 26f; q.foreL = -56f; q.thR = -42f; q.shR = 46f; q.thL = 22f; q.shL = 24f; q.wingZ = -4f; q.wingY = 26f; });
                    p = base.copy();
                    p.blend(up, sm(tk / 9f));
                    p.blend(down, sm((tk - 9f) / 3f));
                    p.blend(base, sm((tk - 13f) / 11f));
                }
                case SeraelEntity.PETALS -> {
                    Pose sky = key(base, q -> { q.torsoX = -12f; q.headX = -30f; q.armRX = -172f; q.armRZ = -4f; q.foreR = -28f; q.handR = -12f;
                        q.armLX = -170f; q.armLZ = 6f; q.foreL = -24f; q.wingZ = 30f; q.wingY = -8f; q.lift = 0.5f; q.shR = 8f; q.shL = 8f; });
                    Pose plunge = key(base, q -> { q.lift = -9f; q.torsoX = 30f; q.headX = 10f; q.armRX = -40f; q.foreR = -38f; q.handR = 62f;
                        q.armLX = -46f; q.armLZ = -10f; q.foreL = -54f; q.thR = -62f; q.shR = 92f; q.thL = 14f; q.shL = 70f; q.wingZ = 26f; q.wingY = 10f; });
                    p = base.copy();
                    p.blend(sky, sm(tk / 12f));
                    p.blend(plunge, sm((tk - 45f) / 4f));
                    p.blend(base, sm((tk - 62f) / 10f));
                }
                case SeraelEntity.HALO -> {
                    Pose open = key(base, q -> { q.armRZ = -74f; q.armLZ = 74f; q.armRX = -10f; q.armLX = -10f; q.foreR = -38f; q.foreL = -38f;
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
                        q.headX = 36f + sway; q.armRX = 18f; q.foreR = -28f; q.handR = 30f; q.armLX = 24f; q.foreL = -30f; q.wingZ = -28f + sway; q.wingY = 20f; });
                    p = base.copy();
                    p.blend(slump, env(k, 0.1f, 0.2f));
                }
                case SeraelEntity.DASH -> {
                    // рывок на крыльях: замах крыльями, мощный взмах вниз — и она низко летит к цели,
                    // ноги вместе вытянуты назад, корпус лёг вперёд, клинок отведён для удара
                    Pose prep = key(base, q -> { q.lift = -3f; q.torsoX = 16f; q.thR = -18f; q.shR = 46f; q.thL = 10f; q.shL = 40f;
                        q.armRX = 22f; q.armRZ = -10f; q.foreR = -52f; q.handR = -16f; q.armLX = -16f; q.foreL = -70f;
                        q.wingZ = 48f; q.wingY = -12f; q.wingX = -6f; });
                    Pose glide = key(base, q -> { q.lift = 7f; q.torsoX = 50f; q.headX = -32f; q.thR = 24f; q.shR = 12f; q.thL = 28f; q.shL = 16f;
                        q.thRZ = 2f; q.thLZ = -2f; q.armRX = 40f; q.armRZ = -14f; q.foreR = -30f; q.handR = -24f; q.armLX = 26f; q.armLZ = 16f; q.foreL = -48f;
                        q.wingZ = -14f; q.wingY = 58f; q.wingX = 8f; });
                    Pose land = key(base, q -> { q.lift = -3f; q.torsoX = 20f; q.thR = -24f; q.shR = 50f; q.thL = 10f; q.shL = 40f;
                        q.armRX = 18f; q.foreR = -20f; q.handR = -20f; q.armLX = -10f; q.foreL = -34f; q.wingZ = 22f; q.wingY = 18f; });
                    p = base.copy();
                    p.blend(prep, sm(tk / 3f));
                    p.blend(glide, sm((tk - 3f) / 3f));
                    p.blend(land, sm((tk - 11f) / 4f));
                }
                case SeraelEntity.BACKSTEP -> {
                    // отскок назад взмахом крыльев: ноги поджаты, клинок в защите
                    Pose jump = key(base, q -> { q.lift = 1f; q.torsoX = -14f; q.headX = 8f; q.thR = -46f; q.shR = 84f; q.thL = -30f; q.shL = 92f;
                        q.armRX = -54f; q.foreR = -72f; q.handR = 0f; q.armLX = -30f; q.armLZ = 26f; q.foreL = -78f;
                        q.wingZ = 44f; q.wingY = -26f; q.wingX = -10f; });
                    Pose land = key(base, q -> { q.lift = -4f; q.torsoX = 12f; q.thR = -30f; q.shR = 60f; q.thL = 8f; q.shL = 46f;
                        q.armRX = -30f; q.foreR = -34f; q.armLX = 0f; q.armLZ = 20f; q.wingZ = 14f; q.wingY = 6f; });
                    p = base.copy();
                    p.blend(jump, sm(tk / 3f));
                    p.blend(land, sm((tk - 9f) / 4f));
                }
                case SeraelEntity.EVADE -> {
                    // уворот вбок на крыльях: корпус кренится в сторону рывка, ноги поджаты, клинок в защите
                    float yr = e.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
                    double lv = Math.cos(yr) * dx + Math.sin(yr) * dz;
                    float side = lv >= 0 ? 1f : -1f;
                    Pose dodge = key(base, q -> { q.lift = 5f; q.torsoZ = -22f * side; q.torsoX = 10f; q.headX = -6f; q.headY = 10f * side;
                        q.thR = -34f; q.shR = 74f; q.thL = -24f; q.shL = 80f; q.thRZ = -6f * side; q.thLZ = -6f * side;
                        q.armRX = -44f; q.armRZ = -10f; q.foreR = -70f; q.handR = 22f; q.armLX = -18f; q.armLZ = 34f; q.foreL = -66f;
                        q.wingZ = -10f + 46f * (1f - sm(tk / 4f)); q.wingY = 34f; q.wingX = 6f; });
                    p = base.copy();
                    p.blend(dodge, sm(tk / 2f));
                    p.blend(base, sm((tk - 8f) / 4f));
                }
                case SeraelEntity.ROAR -> {
                    float tr = 3f * MathHelper.sin(t * 2.4f);
                    Pose roar = key(base, q -> { q.torsoX = -18f; q.headX = -40f; q.armRZ = -60f; q.armLZ = 60f; q.armRX = -20f; q.armLX = -20f;
                        q.foreR = -62f; q.foreL = -62f; q.wingZ = 42f + tr; q.wingY = -12f; q.thRZ = -9f; q.thLZ = 9f; q.haloScale = 1.3f; });
                    p = base.copy();
                    p.blend(roar, env(k, 0.22f, 0.25f));
                }
                default -> { }
            }
        }

        // ---- поражение: берёт клинок двумя руками, поднимает над головой, опускается на колени, вонзает клинок
        // в землю перед собой и только потом каменеет снизу вверх; нимб срывается и падает рядом
        float st = e.statue() ? e.age - e.clientStatueStart + td : -1f;
        float stoneY = -1e9f, crumbleY = 1e9f;
        if (st >= 0) {
            Pose raise = p.copy();
            raise.lift = 0f; raise.torsoX = -8f; raise.torsoY = 0f; raise.torsoZ = 0f; raise.headX = -18f; raise.headY = 0f; raise.pitch = 0f;
            raise.thR = -8f; raise.shR = 14f; raise.thL = 4f; raise.shL = 12f; raise.thRZ = -2f; raise.thLZ = 2f;
            raise.armRX = -150f; raise.armRY = 0f; raise.armRZ = 22f; raise.foreR = -20f; raise.handR = -130f;
            raise.armLX = -150f; raise.armLZ = -22f; raise.foreL = -20f; raise.handL = 0f; raise.wingZ = 30f; raise.wingY = -6f; raise.wingX = 0f;
            Pose kneel = raise.copy();
            kneel.lift = -23f; kneel.thR = -6f; kneel.shR = 92f; kneel.thL = -2f; kneel.shL = 92f;
            kneel.torsoX = 14f; kneel.headX = 30f; kneel.armRX = -72f; kneel.armRZ = 24f; kneel.foreR = -28f; kneel.handR = -222f;
            kneel.armLX = -72f; kneel.armLZ = -24f; kneel.foreL = -28f; kneel.handL = 0f; kneel.wingZ = -18f; kneel.wingY = 26f; kneel.wingX = 4f;
            p.blend(raise, sm(st / 10f));
            p.blend(kneel, sm((st - 12f) / 7f));
            stoneY = (st - 32f) / 80f * 136f - 6f;
            if (e.crumble() > 0) crumbleY = 132f - (e.age - e.clientCrumbleStart + td) / 50f * 140f;
        }

        // ---- сглаживание: каждый сустав догоняет целевую позу пружиной (без рывков между позами и атаками)
        Smooth sp = SMOOTH.computeIfAbsent(e.getId(), id -> new Smooth());
        float[] target = p.arr();
        if (sp.x == null) { sp.x = target.clone(); sp.v = new float[target.length]; }
        long now = System.nanoTime();
        float dts = sp.nanos == 0 ? 0f : Math.min(0.1f, (now - sp.nanos) / 1e9f);
        sp.nanos = now;
        if (net.minecraft.client.MinecraftClient.getInstance().isPaused()) dts = 0f;
        float zeta = 0.78f;
        int steps = Math.max(1, (int) Math.ceil(dts / 0.005f));
        float h = dts / steps;
        for (int si = 0; si < steps; si++) {
            for (int i = 0; i < target.length; i++) {
                float w = OMEGA[i];
                float acc = w * w * (target[i] - sp.x[i]) - 2f * zeta * w * sp.v[i];
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
        if (HAIR1 >= 0) hair(e, HAIR.computeIfAbsent(e.getId(), id -> new Hair()), p, t, bodyYaw0, rot, st > 70f);
        Matrix4f[] mats = m.pose(rot, sc);
        if (st >= 30f) fallingHalo(e, mats, -p.lift);

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
        m.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX)), false, light, overlay, mats, dim, dim, dim, stoneY, crumbleY);
        if (stoneY > -100f) m.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(STONE)), true, light, overlay, mats, 1f, 1f, 1f, stoneY, crumbleY);

        ms.pop();
        trail(e, td, ms, vcp);
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

    private static void clampHair(Hair h, int i, float minX, float maxX, float minZ, float maxZ) {
        if (h.ax[i] < minX) { h.ax[i] = minX; if (h.vx[i] < 0) h.vx[i] = 0; }
        if (h.ax[i] > maxX) { h.ax[i] = maxX; if (h.vx[i] > 0) h.vx[i] = 0; }
        if (h.az[i] < minZ) { h.az[i] = minZ; if (h.vz[i] < 0) h.vz[i] = 0; }
        if (h.az[i] > maxZ) { h.az[i] = maxZ; if (h.vz[i] > 0) h.vz[i] = 0; }
    }

    // ---------------------------------------------------------------- нимб падает при окаменении

    static final class HaloFall {
        Vector3f pos, vel;
        float ax, az, wx, wz, spin;
        long nanos;
    }

    private static final Map<Integer, HaloFall> HALOS = new HashMap<>();

    private static void fallingHalo(SeraelEntity e, Matrix4f[] mats, float ground) {
        int hi = HALO;
        if (hi < 0) return;
        HaloFall f = HALOS.computeIfAbsent(e.getId(), id -> new HaloFall());
        VoxelModel.Part hp = model.parts.get(hi);
        long now = System.nanoTime();
        if (f.pos == null) {
            f.pos = mats[hi].transformPosition(new Vector3f(hp.pivot));
            f.vel = new Vector3f(-26f, 52f, -34f);
            f.wx = 210f; f.wz = 150f;
            f.nanos = now;
        }
        float dt = Math.min(0.05f, (now - f.nanos) / 1e9f);
        f.nanos = now;
        if (net.minecraft.client.MinecraftClient.getInstance().isPaused()) dt = 0f;
        int steps = Math.max(1, (int) Math.ceil(dt / 0.005f));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            f.vel.y -= 256f * h;
            f.pos.add(f.vel.x * h, f.vel.y * h, f.vel.z * h);
            f.ax += f.wx * h; f.az += f.wz * h; f.spin += f.wx * 0.3f * h;
            if (f.pos.y < ground + 1f) {
                f.pos.y = ground + 1f;
                f.vel.y = Math.abs(f.vel.y) > 14f ? -f.vel.y * 0.35f : 0f;
                f.vel.x *= 0.55f; f.vel.z *= 0.55f;
                f.wx *= 0.45f; f.wz *= 0.45f;
            }
            if (f.vel.y == 0f && f.pos.y <= ground + 1.01f) {
                // ложится плашмя
                f.ax += (Math.round(f.ax / 180f) * 180f - f.ax) * Math.min(1f, 10f * h);
                f.az += (Math.round(f.az / 180f) * 180f - f.az) * Math.min(1f, 10f * h);
                f.wx *= 0.9f; f.wz *= 0.9f; f.vel.x *= 0.9f; f.vel.z *= 0.9f;
            }
        }
        Matrix4f mt = new Matrix4f().translate(f.pos).rotateX((float) Math.toRadians(f.ax)).rotateZ((float) Math.toRadians(f.az))
                .rotateY((float) Math.toRadians(f.spin)).translate(-hp.pivot.x, -hp.pivot.y, -hp.pivot.z);
        for (int i = 0; i < model.parts.size(); i++) {
            VoxelModel.Part q = model.parts.get(i);
            if (i == hi || q.parent == hi) mats[i] = mt;
        }
    }

    // ---------------------------------------------------------------- световой след рывка и уворота

    private static final Identifier TRAIL_TEX = new Identifier("vestments", "textures/entity/serael_trail.png");

    static final class Trail {
        final java.util.ArrayDeque<double[]> pts = new java.util.ArrayDeque<>();   // x, y, z, время (с)
    }

    private static final Map<Integer, Trail> TRAILS = new HashMap<>();
    private static final float TRAIL_LIFE = 0.55f;

    private static void trail(SeraelEntity e, float td, MatrixStack ms, VertexConsumerProvider vcp) {
        Trail tr = TRAILS.computeIfAbsent(e.getId(), id -> new Trail());
        double now = System.nanoTime() / 1e9;
        double ex = MathHelper.lerp(td, e.prevX, e.getX()), ey = MathHelper.lerp(td, e.prevY, e.getY()), ez = MathHelper.lerp(td, e.prevZ, e.getZ());
        int a = e.attack();
        float tk = e.age - e.clientAttackStart + td;
        boolean on = a == SeraelEntity.DASH && tk > 2.5f && tk < 13f || a == SeraelEntity.EVADE && tk < 10f || a == SeraelEntity.BACKSTEP && tk > 1.5f && tk < 11f
                || a == SeraelEntity.DIVE && tk > 39f && tk < 60f;
        if (on) {
            double[] last = tr.pts.peekLast();
            if (last == null || Math.abs(last[0] - ex) + Math.abs(last[1] - ey) + Math.abs(last[2] - ez) > 0.12) tr.pts.addLast(new double[] { ex, ey, ez, now });
        }
        while (!tr.pts.isEmpty() && now - tr.pts.peekFirst()[3] > TRAIL_LIFE) tr.pts.pollFirst();
        if (tr.pts.size() < 2) return;
        // хвост к текущему положению — без разрыва между следом и телом
        java.util.List<double[]> list = new java.util.ArrayList<>(tr.pts);
        if (on) list.add(new double[] { ex, ey, ez, now });
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(TRAIL_TEX));
        Matrix4f pm = ms.peek().getPositionMatrix();
        for (int i = 0; i + 1 < list.size(); i++) {
            double[] p0 = list.get(i), p1 = list.get(i + 1);
            float a0 = (float) Math.max(0, 1 - (now - p0[3]) / TRAIL_LIFE), a1 = (float) Math.max(0, 1 - (now - p1[3]) / TRAIL_LIFE);
            a0 *= a0; a1 *= a1;
            float x0 = (float) (p0[0] - ex), y0 = (float) (p0[1] - ey), z0 = (float) (p0[2] - ez);
            float x1 = (float) (p1[0] - ex), y1 = (float) (p1[1] - ey), z1 = (float) (p1[2] - ez);
            // вертикальная «стена» света по силуэту и горизонтальная лента на уровне крыльев — видно с любой стороны
            band(vc, pm, x0, y0 + 0.3f, z0, x0, y0 + 5.6f, z0, x1, y1 + 5.6f, z1, x1, y1 + 0.3f, z1, a0, a1);
            float dx = x1 - x0, dz = z1 - z0, l = (float) Math.sqrt(dx * dx + dz * dz);
            if (l > 1e-4f) {
                float nx = -dz / l * 2.6f, nz = dx / l * 2.6f;
                band(vc, pm, x0 - nx, y0 + 4.3f, z0 - nz, x0 + nx, y0 + 4.3f, z0 + nz, x1 + nx, y1 + 4.3f, z1 + nz, x1 - nx, y1 + 4.3f, z1 - nz, a0, a1);
            }
        }
    }

    private static void band(VertexConsumer vc, Matrix4f pm, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float a0, float a1) {
        // от алого у хвоста к белому золоту у тела
        float r0 = 1f * a0, g0 = (0.22f + 0.5f * a0) * a0, b0 = (0.18f + 0.3f * a0) * a0;
        float r1 = 1f * a1, g1 = (0.22f + 0.5f * a1) * a1, b1 = (0.18f + 0.3f * a1) * a1;
        vc.vertex(pm, ax, ay, az).color(r0, g0, b0, a0).texture(0f, 0f).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 1, 0).next();
        vc.vertex(pm, bx, by, bz).color(r0, g0, b0, a0).texture(0f, 1f).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 1, 0).next();
        vc.vertex(pm, cx, cy, cz).color(r1, g1, b1, a1).texture(1f, 1f).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 1, 0).next();
        vc.vertex(pm, dx, dy, dz).color(r1, g1, b1, a1).texture(1f, 0f).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 1, 0).next();
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
        // волосы не проходят сквозь тело: коса не заходит вперёд за спину, пряди — внутрь к груди
        float hx = p.headX;
        clampHair(h, 0, 4f - hx, 80f, -18f, 18f);
        clampHair(h, 1, 6f - hx - h.ax[0], 70f, -18f, 18f);
        clampHair(h, 2, 8f - hx - h.ax[0] - h.ax[1], 70f, -18f, 18f);
        clampHair(h, 3, -35f - hx, 6f - hx, -30f, 0f);
        clampHair(h, 4, -35f - hx, 6f - hx, 0f, 30f);
        rot[HAIR1].set(h.ax[0], 0, h.az[0]);
        rot[HAIR2].set(h.ax[1], 0, h.az[1]);
        rot[HAIR3].set(h.ax[2], 0, h.az[2]);
        rot[LOCK_R].set(h.ax[3], 0, h.az[3]);
        rot[LOCK_L].set(h.ax[4], 0, h.az[4]);
    }
}
