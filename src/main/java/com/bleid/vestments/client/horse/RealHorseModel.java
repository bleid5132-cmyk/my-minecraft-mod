package com.bleid.vestments.client.horse;

import com.bleid.vestments.client.boss.VoxelModel;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Реалистичная лошадь вместо ванильной: своя воксельная модель (ближняя/дальняя детализация), аллюры по скорости
 * (шаг, рысь, кентер, карьер с плавными переходами), покой (уши, хвост, дыхание, взгляд), щипание травы,
 * свечка (на дыбы), открытый рот, поджатые ноги в прыжке. Поведение лошади — полностью ванильное.
 */
@Environment(EnvType.CLIENT)
public class RealHorseModel extends EntityModel<HorseEntity> {
    static final Identifier HI = new Identifier("vestments", "models/entity/horse/horse_hi.vxm");
    static final Identifier LO = new Identifier("vestments", "models/entity/horse/horse_lo.vxm");
    static final Identifier TACK = new Identifier("vestments", "models/entity/horse/tack.vxm");
    static final Identifier ARMOR = new Identifier("vestments", "models/entity/horse/armor.vxm");
    /** Во сколько раз модель больше «сырой»: спина под седлом — на высоте ванильной посадки всадника. */
    static final float SCALE = 1.08f;
    static final float LOD_DIST = 22f;

    static VoxelModel hi, lo, tack, armor;
    static HorseGaits gaits;
    static int BODY, NECK, HEAD, JAW, EAR_L, EAR_R, MANE, TAIL1, TAIL2, TAIL3;
    static int[] UP = new int[4], MID = new int[4], LOW = new int[4];     // FL FR HL HR
    static int COUNT;

    static void ensure() {
        if (hi != null) return;
        hi = VoxelModel.load(HI);
        lo = VoxelModel.load(LO);
        tack = VoxelModel.load(TACK);
        armor = VoxelModel.load(ARMOR);
        COUNT = hi.parts.size();
        BODY = hi.index("body"); NECK = hi.index("neck"); HEAD = hi.index("head"); JAW = hi.index("jaw");
        EAR_L = hi.index("ear_l"); EAR_R = hi.index("ear_r"); MANE = hi.index("mane");
        TAIL1 = hi.index("tail1"); TAIL2 = hi.index("tail2"); TAIL3 = hi.index("tail3");
        String[] legs = { "fl", "fr", "hl", "hr" };
        for (int i = 0; i < 4; i++) {
            UP[i] = hi.index(legs[i] + "_up"); MID[i] = hi.index(legs[i] + "_mid"); LOW[i] = hi.index(legs[i] + "_low");
        }
        gaits = HorseGaits.load(new HorseGaits.VoxelIndex() {
            @Override
            public int index(String name) {
                return hi.index(name);
            }

            @Override
            public int count() {
                return COUNT;
            }
        });
    }

    /** Состояние анимации конкретной лошади (живёт, пока жива сущность на клиенте). */
    static final class State {
        float last = Float.NaN, speed, gait, phase, move, air, swish, swishT, earL, earR, earLs, earRs, earT, ridden;
        long seed;
    }

    static final Map<HorseEntity, State> STATES = new WeakHashMap<>();

    // результат setAngles — его же используют слои сбруи и брони
    Matrix4f[] mats;
    boolean far, baby;
    float rearAngle;

    static boolean far(HorseEntity e) {
        var cam = MinecraftClient.getInstance().gameRenderer.getCamera();
        return cam != null && cam.getPos().squaredDistanceTo(e.getX(), e.getY(), e.getZ()) > LOD_DIST * LOD_DIST;
    }

    private static float sm(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    private static float noise1(float t, long seed) {
        // гладкий одномерный шум: сумма двух синусов с «случайными» частотами
        float a = (seed & 255) / 255f, b = ((seed >> 8) & 255) / 255f;
        return MathHelper.sin(t * (0.37f + 0.2f * a) + 6.28f * b) * 0.6f + MathHelper.sin(t * (0.91f + 0.3f * b) + 6.28f * a) * 0.4f;
    }

    @Override
    public void animateModel(HorseEntity e, float limbAngle, float limbDistance, float tickDelta) {
    }

    @Override
    public void setAngles(HorseEntity e, float limbAngle, float limbDistance, float t, float headYaw, float headPitch) {
        ensure();
        far = far(e);
        baby = e.isBaby();
        State s = STATES.computeIfAbsent(e, k -> {
            State n = new State();
            n.seed = k.getUuid().getLeastSignificantBits();
            n.phase = (n.seed & 1023) / 1023f;
            return n;
        });
        float dt = Float.isNaN(s.last) ? 0f : MathHelper.clamp(t - s.last, 0f, 4f);
        s.last = t;
        float tickDelta = t - (int) t;

        // ---- скорость: смещение за такт вдоль корпуса (назад — отрицательная)
        double dx = e.getX() - e.prevX, dz = e.getZ() - e.prevZ;
        float yaw = MathHelper.lerp(tickDelta, e.prevBodyYaw, e.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
        double fwd = -MathHelper.sin(yaw) * dx + MathHelper.cos(yaw) * dz;
        float hs = (float) Math.sqrt(dx * dx + dz * dz);
        float signed = fwd < -0.01 ? -hs : hs;
        float k = 1f - (float) Math.exp(-dt * 0.35f);
        s.speed += (signed - s.speed) * k;
        float sp = Math.abs(s.speed);
        // аллюр — непрерывное число 0..3: шаг, рысь, кентер, карьер
        float target = sp < 0.075f ? 0f : sp < 0.13f ? (sp - 0.075f) / 0.055f : sp < 0.2f ? 1f + (sp - 0.13f) / 0.07f
                : Math.min(3f, 2f + (sp - 0.2f) / 0.08f);
        s.gait += (target - s.gait) * (1f - (float) Math.exp(-dt * 0.15f));
        int g0 = Math.min(2, (int) s.gait);
        float gf = s.gait - g0;
        float stride = MathHelper.lerp(gf, gaits.stride[g0], gaits.stride[g0 + 1]);
        s.phase += dt * s.speed / stride;
        s.move += (MathHelper.clamp((sp - 0.006f) / 0.03f, 0f, 1f) - s.move) * (1f - (float) Math.exp(-dt * 0.4f));
        boolean jumping = !e.isOnGround() && !e.isTouchingWater() && e.hasPassengers() && e.getVelocity().y > -0.6;
        s.air += ((jumping ? 1f : 0f) - s.air) * (1f - (float) Math.exp(-dt * 0.5f));
        s.ridden += ((e.hasPassengers() ? 1f : 0f) - s.ridden) * (1f - (float) Math.exp(-dt * 0.1f));

        Vector3f[] rot = new Vector3f[COUNT];
        float[] sc = new float[COUNT];
        for (int i = 0; i < COUNT; i++) { rot[i] = new Vector3f(); sc[i] = 1f; }
        float[] g = new float[COUNT * 3];
        float bob = 0f;
        if (s.move > 0.001f) {
            bob += gaits.sample(g0, s.phase, (1 - gf) * s.move, g);
            bob += gaits.sample(g0 + 1, s.phase, gf * s.move, g);
        }
        for (int i = 0; i < COUNT; i++) rot[i].set(g[i * 3], g[i * 3 + 1], g[i * 3 + 2]);

        // ---- покой: дыхание, взгляд, уши, хвост
        float still = 1f - s.move;
        float breath = MathHelper.sin(t * 0.09f);
        rot[BODY].x += 0.4f * breath * still;
        rot[NECK].x += (2.5f + 2f * noise1(t * 0.05f, s.seed)) * still - 6f * s.ridden * still;
        rot[HEAD].x += 1.5f * noise1(t * 0.07f, s.seed >> 3) * still;
        float hy = MathHelper.clamp(MathHelper.wrapDegrees(headYaw), -60f, 60f);
        rot[NECK].y += hy * 0.45f;
        rot[HEAD].y += hy * 0.35f;
        rot[NECK].x += MathHelper.clamp(headPitch, -30f, 40f) * 0.35f;
        rot[HEAD].x += MathHelper.clamp(headPitch, -30f, 40f) * 0.3f;
        // уши: время от времени поворачиваются по отдельности
        s.earT -= dt;
        if (s.earT <= 0) {
            long r = s.seed = s.seed * 6364136223846793005L + 1442695040888963407L;
            s.earT = 20 + ((r >>> 33) % 80);
            int which = (int) ((r >>> 20) & 3);
            float a = ((r >>> 40) % 50) - 20f;
            if (which != 1) s.earL = a;
            if (which != 2) s.earR = which == 0 ? -a * 0.5f : a;
        }
        float ek = 1f - (float) Math.exp(-dt * 0.25f);
        s.earLs += (s.earL - s.earLs) * ek;
        s.earRs += (s.earR - s.earRs) * ek;
        rot[EAR_L].y += s.earLs;
        rot[EAR_R].y -= s.earRs;
        rot[EAR_L].x -= Math.abs(s.earLs) * 0.3f;
        rot[EAR_R].x -= Math.abs(s.earRs) * 0.3f;
        // хвост: редкие взмахи от мух
        s.swishT -= dt;
        if (s.swishT <= 0) {
            long r = s.seed = s.seed * 6364136223846793005L + 1442695040888963407L;
            s.swishT = 40 + ((r >>> 33) % 140);
            s.swish = 1f;
        }
        s.swish = Math.max(0f, s.swish - dt / 22f);
        float sw = MathHelper.sin((1f - s.swish) * 3.1416f * 3f) * sm(s.swish * 3f) * still;
        rot[TAIL1].y += 22f * sw;
        rot[TAIL2].y += 16f * sw;
        rot[TAIL3].y += 10f * sw;
        rot[TAIL2].x += 2f * breath * still;

        // ---- щипание травы, рот, свечка (ванильные таймеры лошади)
        float graze = e.getEatingGrassAnimationProgress(tickDelta);
        float rear = e.getAngryAnimationProgress(tickDelta);
        float mouth = e.getEatingAnimationProgress(tickDelta);
        float gr = graze * (1 - rear);
        rot[NECK].x = MathHelper.lerp(gr, rot[NECK].x, 62f);
        rot[NECK].y *= 1 - gr;
        rot[HEAD].x = MathHelper.lerp(gr, rot[HEAD].x, 34f + 4f * MathHelper.sin(t * 0.9f));
        rot[HEAD].y *= 1 - gr;
        rot[JAW].x += 12f * gr * Math.max(0f, MathHelper.sin(t * 0.9f)) + 24f * mouth;
        rot[EAR_L].x -= 8f * gr;
        rot[EAR_R].x -= 8f * gr;
        // свечка: корпус поднимается вокруг задних копыт, задние ноги остаются отвесно, передние поджаты
        rearAngle = 48f * rear;
        for (int i = 2; i < 4; i++) {
            rot[UP[i]].x = MathHelper.lerp(rear, rot[UP[i]].x, rearAngle * 0.7f);
            rot[MID[i]].x = MathHelper.lerp(rear, rot[MID[i]].x, rearAngle * 0.15f);
            rot[LOW[i]].x = MathHelper.lerp(rear, rot[LOW[i]].x, rearAngle * 0.15f);
        }
        float paw = MathHelper.sin(t * 0.55f);
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? 1f : -1f;
            rot[UP[i]].x = MathHelper.lerp(rear, rot[UP[i]].x, -40f + 14f * paw * side);
            rot[MID[i]].x = MathHelper.lerp(rear, rot[MID[i]].x, 95f - 10f * paw * side);
            rot[LOW[i]].x = MathHelper.lerp(rear, rot[LOW[i]].x, -10f);
        }
        rot[NECK].x = MathHelper.lerp(rear, rot[NECK].x, 22f);
        rot[HEAD].x = MathHelper.lerp(rear, rot[HEAD].x, -14f);
        rot[TAIL1].x += 10f * rear;
        // прыжок: ноги поджаты, шея вытянута
        float a = s.air * (1 - rear);
        if (a > 0.001f) {
            for (int i = 0; i < 2; i++) {
                rot[UP[i]].x = MathHelper.lerp(a, rot[UP[i]].x, -46f);
                rot[MID[i]].x = MathHelper.lerp(a, rot[MID[i]].x, 100f);
                rot[LOW[i]].x = MathHelper.lerp(a, rot[LOW[i]].x, -20f);
            }
            for (int i = 2; i < 4; i++) {
                rot[UP[i]].x = MathHelper.lerp(a, rot[UP[i]].x, 34f);
                rot[MID[i]].x = MathHelper.lerp(a, rot[MID[i]].x, -46f);
                rot[LOW[i]].x = MathHelper.lerp(a, rot[LOW[i]].x, 52f);
            }
            rot[NECK].x = MathHelper.lerp(a, rot[NECK].x, 14f);
            rot[TAIL1].x = MathHelper.lerp(a, rot[TAIL1].x, 30f);
        }
        if (baby) sc[HEAD] = 1.35f;

        mats = (far ? lo : hi).pose(rot, sc);
        // подъём корпуса при аллюре и свечка вокруг задних копыт — общая матрица поверх всех частей
        Matrix4f root = new Matrix4f().translate(0, bob, 0);
        if (rearAngle > 0) root.translate(0, 2.6f, -11.6f).rotateX((float) Math.toRadians(-rearAngle)).translate(0, -2.6f, 11.6f);
        for (int i = 0; i < COUNT; i++) mats[i] = new Matrix4f(root).mul(mats[i]);
    }

    /** Из ванильной системы координат модели (y вниз, вперёд −z) — в систему вокселей (1/16, y вверх, вперёд +z). */
    void enter(MatrixStack ms) {
        ms.translate(0, 1.501, 0);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
        float s = SCALE / 16f * (baby ? 0.55f : 1f);
        ms.scale(s, s, s);
    }

    @Override
    public void render(MatrixStack ms, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        if (mats == null) return;
        ms.push();
        enter(ms);
        (far ? lo : hi).render(ms, vc, light, overlay, mats, r, g, b);
        ms.pop();
    }

    /** Нарисовать другую модель (сбруя, броня) с теми же матрицами частей. */
    void renderWith(VoxelModel m, MatrixStack ms, VertexConsumer vc, int light, int overlay, float r, float g, float b) {
        if (mats == null) return;
        ms.push();
        enter(ms);
        m.render(ms, vc, light, overlay, mats, r, g, b);
        ms.pop();
    }
}
