package com.bleid.vestments.client.horse;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Лошадь из кубов (модель и анимации в формате Blockbench) вместо ванильной. Аллюр выбирается по настоящей скорости
 * (шаг → рысь → кентер → галоп с плавными переходами), поверх — покой, щипание травы, свечка, прыжок, плавание,
 * взмахи хвостом, встряхивание головой, взгляд. Поведение лошади остаётся ванильным.
 */
@Environment(EnvType.CLIENT)
public class RealHorseModel extends EntityModel<HorseEntity> {
    static final Identifier GEO = new Identifier("vestments", "geo/horse.geo.json");
    static final Identifier TACK_GEO = new Identifier("vestments", "geo/horse_tack.geo.json");
    static final Identifier ARMOR_GEO = new Identifier("vestments", "geo/horse_armor.geo.json");
    static final Identifier ANIMS = new Identifier("vestments", "animations/horse.animation.json");

    /** Модель в пропорциях SWEM (холка 32 px); масштаб даёт холку ~1,75 блока при росте игрока 1,8. */
    static final float SCALE = 0.875f;
    static GeoModel model, tack, armor;
    static int[] tackMap, armorMap;
    static GeoAnimations anims;
    static GeoAnimations.Anim[] GAITS;
    static GeoAnimations.Anim IDLE, GRAZE, REAR, JUMP_UP, JUMP_DOWN, SWIM, SWISH, HEADSHAKE;
    static int NECK, HEAD, JAW, BODY, MANE;
    static final int[] LOCKS = new int[7];
    /** Длина шага цикла каждого аллюра в блоках (при этой длине анимация идёт в своём темпе). */
    static final float[] STRIDE = { 2.6f, 3.6f, 5.3f, 6.6f };

    static void ensure() {
        if (model != null) return;
        model = GeoModel.load(GEO);
        tack = GeoModel.load(TACK_GEO);
        armor = GeoModel.load(ARMOR_GEO);
        tackMap = tack.mapTo(model);
        armorMap = armor.mapTo(model);
        anims = GeoAnimations.load(ANIMS, model, "animation.horse.");
        GAITS = new GeoAnimations.Anim[] { anims.get("walk"), anims.get("trot"), anims.get("canter"), anims.get("gallop") };
        IDLE = anims.get("idle"); GRAZE = anims.get("graze"); REAR = anims.get("rear");
        JUMP_UP = anims.get("jump_up"); JUMP_DOWN = anims.get("jump_down"); SWIM = anims.get("swim");
        SWISH = anims.get("swish"); HEADSHAKE = anims.get("headshake");
        NECK = model.index("neck"); HEAD = model.index("head"); JAW = model.index("jaw"); BODY = model.index("body"); MANE = model.index("mane");
        for (int i = 0; i < LOCKS.length; i++) LOCKS[i] = model.index("mane" + (i + 1));
    }

    static final class State {
        final float[] lockA = new float[7], lockV = new float[7], flowA = new float[7], flowV = new float[7];
        float lastNeckYaw = Float.NaN, lastBob = Float.NaN, lastBodyYaw = Float.NaN;
        float last = Float.NaN, speed, gait, phase, move, swim, up, down, swishT = -1, swishNext = 80, shakeT = -1, shakeNext = 400;
        long seed;

        float rnd() {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            return ((seed >>> 33) & 0xFFFFFF) / (float) 0x1000000;
        }
    }

    static final Map<HorseEntity, State> STATES = new WeakHashMap<>();

    Matrix4f[] mats;
    boolean baby;

    private static float approach(float cur, float target, float rate, float dt) {
        return cur + (target - cur) * (1f - (float) Math.exp(-dt * rate));
    }

    private static float sm(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    /** Аллюр 0..3 по скорости (блоков за такт) с зонами плавного перехода. */
    private static float gaitOf(float sp) {
        if (sp < 0.10f) return 0f;
        if (sp < 0.14f) return sm((sp - 0.10f) / 0.04f);
        if (sp < 0.22f) return 1f;
        if (sp < 0.27f) return 1f + sm((sp - 0.22f) / 0.05f);
        if (sp < 0.40f) return 2f;
        if (sp < 0.46f) return 2f + sm((sp - 0.40f) / 0.06f);
        return 3f;
    }

    @Override
    public void setAngles(HorseEntity e, float limbAngle, float limbDistance, float t, float headYaw, float headPitch) {
        ensure();
        baby = e.isBaby();
        State s = STATES.computeIfAbsent(e, k -> {
            State n = new State();
            n.seed = k.getUuid().getLeastSignificantBits();
            n.phase = n.rnd();
            n.swishNext = 40 + n.rnd() * 200;
            n.shakeNext = 200 + n.rnd() * 800;
            return n;
        });
        float dt = Float.isNaN(s.last) ? 0f : MathHelper.clamp(t - s.last, 0f, 5f);   // такты
        s.last = t;
        float tickDelta = t - (int) t;

        // скорость вдоль корпуса (назад — отрицательная)
        double dx = e.getX() - e.prevX, dz = e.getZ() - e.prevZ;
        float yaw = MathHelper.lerp(tickDelta, e.prevBodyYaw, e.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
        double fwd = -MathHelper.sin(yaw) * dx + MathHelper.cos(yaw) * dz;
        float hs = (float) Math.sqrt(dx * dx + dz * dz);
        float signed = fwd < -0.01 ? -hs : hs;
        s.speed = approach(s.speed, signed, 0.35f, dt);
        float sp = Math.abs(s.speed);
        s.gait = approach(s.gait, gaitOf(sp), 0.12f, dt);
        s.move = approach(s.move, MathHelper.clamp((sp - 0.008f) / 0.03f, 0f, 1f), 0.35f, dt);
        int g0 = Math.min(2, (int) s.gait);
        float gf = s.gait - g0;
        // скорость фазы: по длине шага, но не дальше 0,6…1,6 от авторского темпа анимации
        float rate = 0f;
        for (int k = 0; k < 2; k++) {
            int gi = g0 + k;
            float w = k == 0 ? 1 - gf : gf;
            float natural = 1f / (GAITS[gi].length * 20f);
            float r = MathHelper.clamp(sp / STRIDE[gi], natural * 0.6f, natural * 1.6f);
            rate += w * r;
        }
        s.phase += dt * rate * Math.signum(s.speed);

        boolean inWater = e.isTouchingWater() && !e.isOnGround();
        s.swim = approach(s.swim, inWater ? 1f : 0f, 0.2f, dt);
        boolean air = !e.isOnGround() && !e.isTouchingWater() && e.hasPassengers();
        double vy = e.getY() - e.prevY;
        s.up = approach(s.up, air && vy > 0.02 ? 1f : 0f, 0.45f, dt);
        s.down = approach(s.down, air && vy <= 0.02 ? 1f : 0f, 0.3f, dt);

        GeoAnimations.Pose p = new GeoAnimations.Pose(model.bones.size());
        float still = 1f - s.move;
        IDLE.apply(t / 20f, still * (1 - s.swim), false, p);
        float loco = s.move * (1 - s.swim);
        for (int k = 0; k < 2; k++) {
            GeoAnimations.Anim a = GAITS[g0 + k];
            a.apply(s.phase * a.length, loco * (k == 0 ? 1 - gf : gf), false, p);
        }
        SWIM.apply(t / 20f, s.swim, false, p);

        // одиночные: взмах хвостом от мух, встряхивание головой (только в покое)
        s.swishNext -= dt;
        if (s.swishT < 0 && s.swishNext <= 0) { s.swishT = 0; s.swishNext = 60 + s.rnd() * 220; }
        if (s.swishT >= 0) {
            s.swishT += dt / 20f;
            if (s.swishT > SWISH.length) s.swishT = -1;
            else SWISH.apply(s.swishT, 1f - s.move * 0.5f, false, p);
        }
        s.shakeNext -= dt * still;
        if (s.shakeT < 0 && s.shakeNext <= 0 && still > 0.9f) { s.shakeT = 0; s.shakeNext = 300 + s.rnd() * 900; }
        if (s.shakeT >= 0) {
            s.shakeT += dt / 20f;
            if (s.shakeT > HEADSHAKE.length) s.shakeT = -1;
            else HEADSHAKE.apply(s.shakeT, still, false, p);
        }

        // взгляд
        float hy = MathHelper.clamp(MathHelper.wrapDegrees(headYaw), -70f, 70f);
        float hp = MathHelper.clamp(headPitch, -35f, 45f);
        p.rot[NECK * 3 + 1] += hy * 0.45f;
        p.rot[HEAD * 3 + 1] += hy * 0.35f;
        p.rot[NECK * 3] += hp * 0.3f;
        p.rot[HEAD * 3] += hp * 0.35f;

        // ванильные таймеры: щипание травы, свечка, жевание
        float graze = e.getEatingGrassAnimationProgress(tickDelta);
        float rear = e.getAngryAnimationProgress(tickDelta);
        float mouth = e.getEatingAnimationProgress(tickDelta);
        GRAZE.apply(t / 20f, sm(graze) * (1 - rear), true, p);
        float jump = Math.max(s.up, s.down) * (1 - rear);
        if (jump > 0.001f) {
            float wu = s.up / Math.max(1e-3f, s.up + s.down);
            JUMP_UP.apply(0, jump * wu, true, p);
            JUMP_DOWN.apply(0, jump * (1 - wu), true, p);
        }
        REAR.apply(t / 20f, sm(rear), true, p);
        p.rot[JAW * 3] += 22f * mouth;
        mane(s, p, e, dt, Math.max(0f, s.speed), t);
        if (baby) {
            int h = HEAD * 3;
            p.scl[h] *= 1.3f; p.scl[h + 1] *= 1.3f; p.scl[h + 2] *= 1.3f;
        }
        mats = model.pose(p.rot, p.pos, p.scl);
    }

    /**
     * Грива — короткий гребень посередине шеи из 7 пучков на шарнирах. На бегу пучки ложатся назад, к холке
     * (тем сильнее, чем быстрее), по ним бежит волна и их подбрасывает тряска; от резких поворотов головы и корпуса
     * они качаются вбок. Пучки только наклоняются от основания, поэтому сквозь шею не проходят.
     */
    private static void mane(State s, GeoAnimations.Pose p, HorseEntity e, float dt, float sp, float t) {
        if (LOCKS[0] < 0 || dt <= 0f) return;
        float neckYaw = p.rot[NECK * 3 + 1] + p.rot[HEAD * 3 + 1] * 0.5f;
        float bodyYaw = e.bodyYaw;
        float bob = p.pos[BODY * 3 + 1] + p.rot[NECK * 3] * 0.05f;
        if (Float.isNaN(s.lastNeckYaw)) { s.lastNeckYaw = neckYaw; s.lastBodyYaw = bodyYaw; s.lastBob = bob; }
        // поворот влево в нашей системе — +y; рост bodyYaw в игре — поворот вправо
        float yawRate = (neckYaw - s.lastNeckYaw - MathHelper.wrapDegrees(bodyYaw - s.lastBodyYaw)) / dt;   // град/такт
        float bobRate = (bob - s.lastBob) / dt;
        s.lastNeckYaw = neckYaw; s.lastBodyYaw = bodyYaw; s.lastBob = bob;
        float lay = MathHelper.clamp(sp * 140f, 0f, 70f);                     // насколько пучки ложатся назад
        int steps = Math.max(1, (int) Math.ceil(dt / 0.5f));
        float h = dt / steps;
        for (int i = 0; i < LOCKS.length; i++) {
            float k = 0.35f + 0.03f * i, damp = 0.22f;
            float wave = lay > 1f ? MathHelper.sin(t * 1.1f - i * 0.8f) * (3f + lay * 0.12f) : 0f;
            float backT = lay + wave;
            for (int n = 0; n < steps; n++) {
                // назад: flowA ≥ 0 — наклон к холке; тряска подбрасывает пучки вверх (уменьшает наклон)
                float fa = -k * (s.flowA[i] - backT) - damp * s.flowV[i] - Math.abs(bobRate) * 6f * k;
                s.flowV[i] += fa * h;
                s.flowA[i] = MathHelper.clamp(s.flowA[i] + s.flowV[i] * h, -15f, 80f);
                // вбок: шея уходит влево — пучок по инерции отклоняется вправо, и наоборот
                float sa = -k * s.lockA[i] - damp * s.lockV[i] + yawRate * 0.8f * k;   // +y наклоняет верх пучка вправо (−x)
                s.lockV[i] += sa * h;
                s.lockA[i] = MathHelper.clamp(s.lockA[i] + s.lockV[i] * h, -40f, 40f);
            }
            int b = LOCKS[i] * 3;
            p.rot[b] -= s.flowA[i];
            p.rot[b + 1] += s.lockA[i];
        }
    }

    /** Из ванильной системы координат модели (y вниз, вперёд −z) — в нашу (пиксели, y вверх, вперёд +z). */
    void enter(MatrixStack ms) {
        ms.translate(0, 1.501, 0);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
        float s = SCALE / 16f * (baby ? 0.5f : 1f);
        ms.scale(s, s, s);
    }

    @Override
    public void render(MatrixStack ms, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        if (mats == null) return;
        ms.push();
        enter(ms);
        model.render(ms, vc, light, overlay, mats, null, r, g, b, a);
        ms.pop();
    }

    void renderTack(MatrixStack ms, VertexConsumer vc, int light, int overlay) {
        if (mats == null) return;
        ms.push();
        enter(ms);
        tack.render(ms, vc, light, overlay, mats, tackMap, 1f, 1f, 1f, 1f);
        ms.pop();
    }

    void renderArmor(MatrixStack ms, VertexConsumer vc, int light, int overlay, float r, float g, float b) {
        if (mats == null) return;
        ms.push();
        enter(ms);
        armor.render(ms, vc, light, overlay, mats, armorMap, r, g, b, 1f);
        ms.pop();
    }
}
