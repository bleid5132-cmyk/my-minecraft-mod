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
        public float ground = 1f;                          // 1 — стоит на земле (стопы прижимаются к земле)
    }

    private static final class State {
        long nanos;
        float move, run, air, rise;
        float fwdSpeed, accelLean, bank, prevBodyYaw = Float.NaN;
        float land, airVy;
        boolean wasAir;
        long frameNanos;
        Loco cached, smooth;
    }

    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();

    /** Доля наклона, которую берёт на себя грудь (изгиб в пояснице); остальное — наклон таза. */
    public static final float CHEST_LEAN = 0.6f;
    /** Грудь отклоняется против покачивания таза — голова и плечи остаются ровнее. */
    public static final float CHEST_COUNTER_ROLL = 0.7f;

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
        boolean onGround = e.isOnGround() || e.isTouchingWater() || e.isClimbing();
        // скорость по смещению (у чужих игроков velocity на клиенте не обновляется)
        double vx = e.getX() - e.prevX, vy = e.getY() - e.prevY, vz = e.getZ() - e.prevZ;
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, e.prevBodyYaw, e.bodyYaw);
        double yr = Math.toRadians(bodyYaw);
        float fwd = (float) (vx * -Math.sin(yr) + vz * Math.cos(yr));
        float hSpeed = (float) Math.sqrt(vx * vx + vz * vz);

        s.move = approach(s.move, MathHelper.clamp(limbSpeed * 1.6f, 0f, 1f), 10f, dt);
        s.run = approach(s.run, e.isSprinting() && limbSpeed > 0.2f ? 1f : 0f, 6f, dt);
        s.air = approach(s.air, onGround ? 0f : 1f, 12f, dt);
        s.rise = approach(s.rise, MathHelper.clamp((float) (vy + 0.08) / 0.3f, 0f, 1f), 8f, dt);
        // разгон/торможение — корпус наклоняется вперёд/назад
        if (dt > 0) {
            float acc = (fwd - s.fwdSpeed) / dt;
            s.fwdSpeed = approach(s.fwdSpeed, fwd, 12f, dt);
            s.accelLean = approach(s.accelLean, MathHelper.clamp(acc * 9f, -7f, 8f), 6f, dt);
            // вираж на поворотах
            if (!Float.isNaN(s.prevBodyYaw)) {
                float rate = MathHelper.wrapDegrees(bodyYaw - s.prevBodyYaw) / dt;
                float spN = MathHelper.clamp(hSpeed / 0.22f, 0f, 1f);
                s.bank = approach(s.bank, MathHelper.clamp(rate * 0.03f * spN, -7f, 7f), 7f, dt);
            }
        }
        s.prevBodyYaw = bodyYaw;
        // приземление: колени пружинят, тело проседает (сильнее после высокого прыжка)
        if (!onGround) {
            s.airVy = Math.min(s.airVy, (float) vy);
            s.wasAir = true;
        } else if (s.wasAir) {
            s.wasAir = false;
            if (s.airVy < -0.15f) s.land = Math.max(s.land, MathHelper.clamp(-s.airVy / 0.7f, 0.35f, 1f));
            s.airVy = 0;
        }
        s.land = approach(s.land, 0f, 5.5f, dt);

        PlayerEntity p = (PlayerEntity) e;
        boolean sword = p.getMainHandStack().getItem() instanceof PaladinSwordItem;
        boolean shield = p.getOffHandStack().getItem() instanceof PaladinShieldItem;
        boolean armed = sword || shield;
        boolean holding = !p.getMainHandStack().isEmpty();
        boolean rightHanded = p.getMainArm() == net.minecraft.util.Arm.RIGHT;

        float ph = limbPos * 0.6662f;
        float c = MathHelper.cos(ph), sinP = MathHelper.sin(ph), sn = Math.abs(sinP);
        float amp = MathHelper.clamp(0.45f + limbSpeed * 0.9f, 0.45f, 1f);     // медленно — короче шаг
        // перенос ноги (идёт вперёд): колено сгибается сильнее всего в середине переноса;
        // опора: лёгкий сгиб при постановке стопы (амортизация)
        float swR = Math.max(0f, -sinP), swL = Math.max(0f, sinP);
        float kR = swR * (float) Math.sqrt(swR), kL = swL * (float) Math.sqrt(swL);
        float stR = swL, stL = swR;
        // рука, ушедшая вперёд, сгибается в локте сильнее
        float fR = Math.max(0f, -c), fL = Math.max(0f, c);
        float age = e.age + tickDelta;
        float breath = MathHelper.sin(age * 0.09f);
        float shift = MathHelper.sin(age * 0.035f + e.getId());          // перенос веса, ~9 с
        float shiftR = Math.max(0f, shift), shiftL = Math.max(0f, -shift);
        float sway = MathHelper.sin(age * 0.05f + 1.7f);                  // лёгкое покачивание оружия в стойке

        // ---------------- покой
        Loco I = new Loco();
        if (armed) {                              // боевая стойка паладина
            I.bob = -0.004f * (1f + breath);          // дыхание: только чуть «оседает», не отрывая стоп
            I.turn = -16f + 1.5f * sway;
            I.lean = 3f + 0.6f * breath;
            I.roll = 0.8f * shift;
            I.body = 0.06f + 0.02f * sway;
            // передняя (левая) нога: бедро вперёд, голень вертикальна — стопа стоит плашмя;
            // задняя (правая): отставлена назад, пятка чуть приподнята
            I.kR = 0.12f + 0.02f * breath + 0.04f * shiftL; I.kL = 0.22f + 0.03f * breath + 0.04f * shiftR;
            I.rLeg = 0.16f; I.lLeg = -I.kL; I.rLegR = 0.06f; I.lLegR = -0.07f;
        } else {                                  // расслабленно: вес то на одной, то на другой ноге
            I.bob = -0.003f * (1f + breath);
            I.turn = 3f * shift;
            I.lean = 1.2f + 0.5f * breath;
            I.roll = 1.8f * shift;
            I.body = 0.05f * shift;
            I.kR = 0.04f + 0.24f * shiftL; I.kL = 0.04f + 0.24f * shiftR;
            // согнутое колено уходит вперёд, голень почти вертикальна — подошва на земле
            I.rLeg = -I.kR * 0.9f + 0.02f * shift; I.lLeg = -I.kL * 0.9f - 0.02f * shift;
            I.rLegR = 0.035f + 0.02f * shiftL; I.lLegR = -0.035f - 0.02f * shiftR;
        }
        if (sword) {
            I.rP = -0.16f + 0.03f * breath + 0.03f * sway; I.rY = -0.12f; I.rR = 0.14f; I.eR = 0.34f + 0.03f * breath;
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

        // ---------------- ходьба (Epic Fight: бедро −8…+39°, корпус против таза, проседание на двойной опоре)
        Loco W = new Loco();
        W.bob = 0f;              // высоту таза задаёт постановка стоп (см. footDrop)
        W.lean = 4.5f;
        W.turn = (armed ? -6f : 0f) - 4f * c * amp;           // таз поворачивается за выносимой ногой
        W.roll = 1.6f * sinP * amp;
        W.rLeg = (-0.12f - 0.52f * c) * amp;
        W.lLeg = (-0.12f + 0.52f * c) * amp;
        W.kR = 0.08f + (0.95f * kR + 0.14f * stR) * amp;
        W.kL = 0.08f + (0.95f * kL + 0.14f * stL) * amp;
        W.body = (armed ? 0.22f : 0.17f) * c * amp;           // плечи — навстречу тазу
        if (sword) {
            W.rP = -0.24f + 0.22f * c * amp - 0.1f * fR; W.rY = 0.16f * c * amp - 0.08f; W.rR = 0.12f; W.eR = 0.36f + 0.18f * fR;
        } else {
            W.rP = 0.46f * c * amp - 0.06f; W.rY = 0.08f * c; W.rR = 0.06f + 0.03f * fR;
            W.eR = 0.2f + 0.45f * fR * amp + (holding ? 0.12f : 0f);
        }
        if (shield) {
            W.lP = -0.3f - 0.08f * c; W.lY = 0.32f + 0.1f * c; W.lR = -0.08f; W.eL = 0.82f;
        } else {
            W.lP = -0.46f * c * amp - 0.06f; W.lY = 0.08f * c; W.lR = -0.06f - 0.03f * fL; W.eL = 0.2f + 0.45f * fL * amp;
        }

        // ---------------- бег (Epic Fight: наклон 20–35°, колено до −72°, руки работают локтями)
        Loco R = new Loco();
        R.bob = 0f;
        R.lean = armed ? 22f : 16f;
        R.turn = -6f * c;
        R.roll = 1.4f * sinP;
        R.rLeg = -0.25f - 0.88f * c;
        R.lLeg = -0.25f + 0.88f * c;
        R.kR = 0.2f + 1.55f * kR + 0.25f * stR;
        R.kL = 0.2f + 1.55f * kL + 0.25f * stL;
        R.body = 0.24f * c;
        if (sword) {
            R.rP = 0.42f + 0.34f * c; R.rY = -0.18f; R.rR = 0.36f; R.eR = 0.5f + 0.2f * fR;
        } else {
            R.rP = -0.1f + 0.85f * c; R.rY = 0.1f; R.rR = 0.12f; R.eR = 1.3f + 0.3f * fR;
        }
        if (shield) {
            R.lP = -0.55f - 0.12f * c; R.lY = 0.45f; R.lR = -0.12f; R.eL = 0.95f;
        } else {
            R.lP = -0.1f - 0.85f * c; R.lY = -0.1f; R.lR = -0.12f; R.eL = 1.3f + 0.3f * fL;
        }

        // ---------------- прыжок: толчок (колено вверх, руки вразнобой) → падение (ноги тянутся вниз, руки в стороны)
        Loco J = new Loco();
        J.bob = 0f; J.lean = 5f; J.turn = armed ? -6f : 0f; J.body = -0.08f;
        J.rLeg = -0.95f; J.lLeg = 0.32f; J.rLegR = 0.06f; J.lLegR = -0.06f;
        J.kR = 1.4f; J.kL = 0.55f;
        J.rP = sword ? 0.05f : 0.45f; J.rY = 0f; J.rR = sword ? 0.4f : 0.2f; J.eR = sword ? 0.4f : 0.7f;
        J.lP = shield ? -0.55f : -0.7f; J.lY = shield ? 0.4f : 0.1f; J.lR = -0.2f; J.eL = shield ? 0.9f : 0.75f;
        Loco F = new Loco();
        F.bob = 0f; F.lean = -2f; F.turn = armed ? -8f : 0f;
        F.rLeg = -0.38f; F.lLeg = -0.08f; F.rLegR = 0.1f; F.lLegR = -0.1f;
        F.kR = 0.55f; F.kL = 0.3f;
        F.rP = sword ? 0.1f : -0.35f; F.rY = 0f; F.rR = sword ? 0.55f : 0.75f; F.eR = sword ? 0.4f : 0.5f;
        F.lP = shield ? -0.6f : -0.35f; F.lY = shield ? 0.4f : 0f; F.lR = shield ? -0.3f : -0.75f; F.eL = shield ? 0.9f : 0.5f;
        Loco A = mix(F, J, s.rise);

        Loco out = mix(mix(mix(I, W, s.move), R, s.run * s.move), A, s.air);
        // разгон, вираж, приземление — поверх любой позы
        out.lean += s.accelLean * (1f - s.air) + 6f * s.land;
        out.roll += s.bank * (1f - s.air);
        out.bob -= 0.02f * s.land;
        out.kR += 1.05f * s.land;
        out.kL += 0.9f * s.land;
        out.rLeg -= 0.25f * s.land;
        out.lLeg -= 0.15f * s.land;
        out.rR += 0.25f * s.land;
        out.lR -= 0.25f * s.land;
        out.eR += 0.25f * s.land;
        out.eL += 0.25f * s.land;
        if (!rightHanded) {        // левша: зеркалим руки
            float rP = out.rP, rY = out.rY, rR = out.rR, eR = out.eR;
            out.rP = out.lP; out.rY = -out.lY; out.rR = -out.lR; out.eR = out.eL;
            out.lP = rP; out.lY = -rY; out.lR = -rR; out.eL = eR;
            out.turn = -out.turn;
            out.body = -out.body;
            out.roll = -out.roll;
        }
        // мягкое сглаживание всей позы — никаких рывков при смене состояний и предметов
        if (s.smooth == null || dt <= 0) s.smooth = out;
        else s.smooth = mix(s.smooth, out, 1f - (float) Math.exp(-24f * dt), true);
        Loco res = s.smooth;
        res.armR = true;
        res.armL = true;
        res.ground = 1f - s.air;
        if (p.isUsingItem()) {
            boolean main = p.getActiveHand() == Hand.MAIN_HAND;
            boolean rightActive = main == rightHanded;
            if (rightActive) res.armR = false; else res.armL = false;
        }
        s.cached = res;
        s.frameNanos = now;
        return res;
    }

    /**
     * Насколько стопа поднялась над землёй (пиксели модели) при наклоне бедра th и сгибе колена k:
     * бедро и голень по 6 пикселей, голень отклонена от вертикали на th + k.
     */
    public static float footDrop(float th, float k) {
        // самая нижняя точка стопы — край подошвы (стопа 4 пикселя в глубину, наклонена вместе с голенью)
        float phi = th + k;
        return Math.max(0f, 12f - 6f * MathHelper.cos(th) - 6f * MathHelper.cos(phi) - 2f * Math.abs(MathHelper.sin(phi)));
    }

    private static Loco mix(Loco a, Loco b, float t) {
        return mix(a, b, t, false);
    }

    private static Loco mix(Loco a, Loco b, float t, boolean always) {
        if (!always && t <= 0.001f) return a;
        if (!always && t >= 0.999f) return b;
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
