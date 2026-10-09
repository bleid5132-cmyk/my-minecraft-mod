package com.bleid.vestments.client.combat;

import com.bleid.vestments.paladin.PaladinShieldItem;
import com.bleid.vestments.paladin.PaladinSwordItem;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

/**
 * «Живое» тело игрока по образцу Epic Fight (кривые сняты с их анимаций walk/run/idle).
 * Работает для всех игроков (с оружием и без), руки и ноги сгибаются в локтях и коленях (bendy-lib):
 *  • покой без оружия — перенос веса с ноги на ногу, дыхание, расслабленные согнутые руки;
 *  • покой паладина — боевая стойка: корпус развёрнут щитом вперёд, меч у бедра, щит у груди;
 *  • ходьба — колено сгибается в переносе ноги, руки качаются с мягким локтем, таз покачивается;
 *  • бег — наклон вперёд, высокий замах голени назад, руки работают согнутыми в локтях;
 *  • в воздухе — колени поджаты, руки раскинуты для равновесия.
 * Состояния смешиваются плавно (по реальному времени), удары накладываются поверх.
 */
@Environment(EnvType.CLIENT)
public final class Locomotion {
    public static final class Loco {
        public float bob, lean, turn;                      // корень: блоки, градусы, градусы (вправо +)
        public float rP, rY, rR, lP, lY, lR;               // руки (правая/левая), радианы модели
        public float body, rLeg, lLeg, rLegR, lLegR;
        public float roll;                                 // покачивание таза, градусы
        public float eR, eL, kR, kL;                       // сгиб локтей и коленей, радианы (bendy-lib)
        public boolean armR = true, armL = true;           // можно ли трогать руку (не ест, не блокирует)
    }

    private static final class State {
        long nanos;
        float move, run, air;
        long frameNanos;
        Loco cached;
    }

    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();

    private Locomotion() { }

    public static boolean qualifies(LivingEntity e) {
        if (!(e instanceof PlayerEntity p)) return false;
        if (p.isSpectator() || p.isSleeping() || p.hasVehicle() || p.isFallFlying() || p.isSwimming()
                || p.isInSneakingPose() || p.getPose() != EntityPose.STANDING || p.getAbilities().flying) return false;
        return true;
    }

    /** Последняя вычисленная поза (в этом кадре), для setAngles. */
    public static Loco cached(LivingEntity e) {
        State s = STATES.get(e);
        if (s == null || s.cached == null || System.nanoTime() - s.frameNanos > 100_000_000L) return null;
        return s.cached;
    }

    private static float approach(float cur, float target, float rate, float dt) {
        float k = 1f - (float) Math.exp(-rate * dt);
        return cur + (target - cur) * k;
    }

    /** Вычислить позу (вызывается из setupTransforms, один раз за кадр на сущность). */
    public static Loco compute(LivingEntity e, float tickDelta) {
        if (!qualifies(e)) {
            STATES.remove(e);
            return null;
        }
        State s = STATES.computeIfAbsent(e, k -> new State());
        long now = System.nanoTime();
        float dt = s.nanos == 0 ? 0f : Math.min(0.1f, (now - s.nanos) / 1e9f);
        s.nanos = now;
        float limbPos = e.limbAnimator.getPos(tickDelta);
        float limbSpeed = Math.min(1f, e.limbAnimator.getSpeed(tickDelta));
        s.move = approach(s.move, MathHelper.clamp(limbSpeed * 1.6f, 0f, 1f), 10f, dt);
        s.run = approach(s.run, e.isSprinting() && limbSpeed > 0.2f ? 1f : 0f, 6f, dt);
        s.air = approach(s.air, e.isOnGround() || e.isTouchingWater() ? 0f : 1f, 9f, dt);

        PlayerEntity p = (PlayerEntity) e;
        boolean sword = p.getMainHandStack().getItem() instanceof PaladinSwordItem;
        boolean shield = p.getOffHandStack().getItem() instanceof PaladinShieldItem;
        boolean holding = !p.getMainHandStack().isEmpty();
        boolean rightHanded = p.getMainArm() == net.minecraft.util.Arm.RIGHT;

        float ph = limbPos * 0.6662f;
        float c = MathHelper.cos(ph), sn = Math.abs(MathHelper.sin(ph));
        // нога в переносе (идёт вперёд) — колено сгибается сильнее всего в середине переноса
        float kR = Math.max(0f, -MathHelper.sin(ph)), kL = Math.max(0f, MathHelper.sin(ph));
        // рука, ушедшая вперёд, сгибается в локте сильнее
        float fR = Math.max(0f, -c), fL = Math.max(0f, c);
        float age = e.age + tickDelta;
        float breath = MathHelper.sin(age * 0.09f);
        float shift = MathHelper.sin(age * 0.035f + e.getId());          // перенос веса, ~9 с
        float shiftR = Math.max(0f, shift), shiftL = Math.max(0f, -shift);

        // ---------------- покой
        Loco I = new Loco();
        if (sword || shield) {                    // боевая стойка паладина
            I.bob = 0.012f * breath - 0.015f;
            I.turn = -16f;
            I.lean = 2f;
            I.roll = 0.6f * shift;
            I.body = 0.06f;
            I.rLeg = 0.2f; I.lLeg = -0.22f; I.rLegR = 0.06f; I.lLegR = -0.07f;
            I.kR = 0.28f + 0.03f * breath; I.kL = 0.38f + 0.03f * breath;
        } else {                                  // расслабленно: вес то на одной, то на другой ноге
            I.bob = 0.008f * breath - 0.005f * (shiftR + shiftL);
            I.turn = 3f * shift;
            I.lean = 1.2f + 0.4f * breath;
            I.roll = 1.8f * shift;
            I.body = 0.05f * shift;
            I.rLeg = 0.05f * shift - 0.02f; I.lLeg = -0.05f * shift - 0.02f;
            I.rLegR = 0.035f + 0.02f * shiftL; I.lLegR = -0.035f - 0.02f * shiftR;
            I.kR = 0.06f + 0.24f * shiftL; I.kL = 0.06f + 0.24f * shiftR;
        }
        if (sword) {
            I.rP = -0.16f + 0.03f * breath; I.rY = -0.12f; I.rR = 0.14f; I.eR = 0.32f + 0.02f * breath;
        } else if (holding) {
            I.rP = -0.14f + 0.02f * breath; I.rY = -0.04f; I.rR = 0.06f; I.eR = 0.38f + 0.03f * breath;
        } else {
            I.rP = 0.04f * breath + 0.03f * shift; I.rY = 0.04f; I.rR = 0.075f + 0.015f * breath;
            I.eR = 0.2f + 0.05f * breath + 0.06f * shiftL;
        }
        if (shield) {
            I.lP = -0.3f + 0.02f * breath; I.lY = 0.36f; I.lR = -0.08f; I.eL = 0.8f + 0.03f * breath;
        } else {
            I.lP = 0.04f * breath - 0.03f * shift; I.lY = -0.04f; I.lR = -0.075f - 0.015f * breath;
            I.eL = 0.2f + 0.05f * breath + 0.06f * shiftR;
        }

        // ---------------- ходьба
        Loco W = new Loco();
        W.bob = -0.045f * (1f - sn) + 0.01f;
        W.lean = 4.5f;
        W.turn = sword || shield ? -6f : 0f;
        W.roll = 1.4f * MathHelper.sin(ph);
        W.rLeg = -0.1f - 0.5f * c;
        W.lLeg = -0.1f + 0.5f * c;
        W.kR = 0.1f + 0.85f * kR;
        W.kL = 0.1f + 0.85f * kL;
        W.body = (sword || shield ? 0.2f : 0.14f) * c;
        if (sword) {
            W.rP = -0.24f + 0.24f * c - 0.1f * fR; W.rY = 0.16f * c - 0.08f; W.rR = 0.12f; W.eR = 0.34f + 0.16f * fR;
        } else {
            W.rP = 0.42f * c - 0.06f; W.rY = 0.06f * c; W.rR = 0.06f; W.eR = 0.22f + 0.38f * fR + (holding ? 0.12f : 0f);
        }
        if (shield) {
            W.lP = -0.3f - 0.08f * c; W.lY = 0.32f + 0.1f * c; W.lR = -0.08f; W.eL = 0.82f;
        } else {
            W.lP = -0.42f * c - 0.06f; W.lY = 0.06f * c; W.lR = -0.06f; W.eL = 0.22f + 0.38f * fL;
        }

        // ---------------- бег
        Loco R = new Loco();
        R.bob = -0.09f * (1f - sn) + 0.02f;
        R.lean = sword || shield ? 18f : 13f;
        R.turn = 0f;
        R.roll = 1.2f * MathHelper.sin(ph);
        R.rLeg = -0.22f - 0.85f * c;
        R.lLeg = -0.22f + 0.85f * c;
        R.kR = 0.25f + 1.45f * kR;
        R.kL = 0.25f + 1.45f * kL;
        R.body = 0.14f * c;
        if (sword) {
            R.rP = 0.45f + 0.32f * c; R.rY = -0.18f; R.rR = 0.38f; R.eR = 0.45f;
        } else {
            R.rP = -0.05f + 0.8f * c; R.rY = 0.08f; R.rR = 0.12f; R.eR = 1.25f + 0.25f * fR;
        }
        if (shield) {
            R.lP = -0.55f - 0.12f * c; R.lY = 0.45f; R.lR = -0.12f; R.eL = 0.9f;
        } else {
            R.lP = -0.05f - 0.8f * c; R.lY = -0.08f; R.lR = -0.12f; R.eL = 1.25f + 0.25f * fL;
        }

        // ---------------- в воздухе
        Loco A = new Loco();
        A.bob = 0f; A.lean = 6f; A.turn = sword || shield ? -8f : 0f;
        A.rLeg = -0.6f; A.lLeg = 0.15f; A.rLegR = 0.08f; A.lLegR = -0.08f;
        A.kR = 1.2f; A.kL = 0.55f;
        A.body = 0.05f;
        A.rP = sword ? 0.15f : -0.35f; A.rY = 0f; A.rR = sword ? 0.45f : 0.4f; A.eR = sword ? 0.35f : 0.55f;
        A.lP = shield ? -0.6f : -0.35f; A.lY = shield ? 0.4f : 0f; A.lR = -0.4f; A.eL = shield ? 0.85f : 0.55f;

        Loco out = mix(mix(mix(I, W, s.move), R, s.run * s.move), A, s.air);
        if (!rightHanded) {        // левша: зеркалим руки
            float rP = out.rP, rY = out.rY, rR = out.rR, eR = out.eR;
            out.rP = out.lP; out.rY = -out.lY; out.rR = -out.lR; out.eR = out.eL;
            out.lP = rP; out.lY = -rY; out.lR = -rR; out.eL = eR;
            out.turn = -out.turn;
            out.body = -out.body;
            out.roll = -out.roll;
        }
        if (p.isUsingItem()) {
            boolean main = p.getActiveHand() == Hand.MAIN_HAND;
            boolean rightActive = main == rightHanded;
            if (rightActive) out.armR = false; else out.armL = false;
        }
        s.cached = out;
        s.frameNanos = now;
        return out;
    }

    private static Loco mix(Loco a, Loco b, float t) {
        if (t <= 0.001f) return a;
        if (t >= 0.999f) return b;
        Loco o = new Loco();
        o.bob = MathHelper.lerp(t, a.bob, b.bob);
        o.lean = MathHelper.lerp(t, a.lean, b.lean);
        o.turn = MathHelper.lerp(t, a.turn, b.turn);
        o.rP = MathHelper.lerp(t, a.rP, b.rP); o.rY = MathHelper.lerp(t, a.rY, b.rY); o.rR = MathHelper.lerp(t, a.rR, b.rR);
        o.lP = MathHelper.lerp(t, a.lP, b.lP); o.lY = MathHelper.lerp(t, a.lY, b.lY); o.lR = MathHelper.lerp(t, a.lR, b.lR);
        o.body = MathHelper.lerp(t, a.body, b.body);
        o.rLeg = MathHelper.lerp(t, a.rLeg, b.rLeg); o.lLeg = MathHelper.lerp(t, a.lLeg, b.lLeg);
        o.rLegR = MathHelper.lerp(t, a.rLegR, b.rLegR); o.lLegR = MathHelper.lerp(t, a.lLegR, b.lLegR);
        o.roll = MathHelper.lerp(t, a.roll, b.roll);
        o.eR = MathHelper.lerp(t, a.eR, b.eR); o.eL = MathHelper.lerp(t, a.eL, b.eL);
        o.kR = MathHelper.lerp(t, a.kR, b.kR); o.kL = MathHelper.lerp(t, a.kL, b.kL);
        return o;
    }
}
