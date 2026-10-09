package com.bleid.vestments.client.combat;

import com.bleid.vestments.paladin.PaladinArmorItem;
import com.bleid.vestments.paladin.PaladinShieldItem;
import com.bleid.vestments.paladin.PaladinSwordItem;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

/**
 * Стойка, ходьба и бег паладина по образцу Epic Fight (кривые сняты с их анимаций walk/run/idle):
 *  • покой — боевая стойка: корпус развёрнут щитом вперёд (~16°), меч опущен у бедра, лёгкое дыхание;
 *  • ходьба — шаг меньше ванильного, корпус качается навстречу ногам (±11°), тело «проседает»
 *    в момент постановки стопы, щит держится у груди, меч покачивается у бедра;
 *  • бег — наклон вперёд ~18°, широкий шаг, глубокое проседание, щит выставлен вперёд,
 *    меч отведён назад-вниз, как у бегущего рыцаря;
 *  • в воздухе — ноги поджаты, щит прикрывает.
 * Состояния смешиваются плавно (по реальному времени), удары накладываются поверх.
 */
@Environment(EnvType.CLIENT)
public final class Locomotion {
    public static final class Loco {
        public float bob, lean, turn;                      // корень: блоки, градусы, градусы (вправо +)
        public float rP, rY, rR, lP, lY, lR;               // руки (правая/левая), радианы модели
        public float body, rLeg, lLeg, rLegR, lLegR;
        public float rLift, lLift;                         // подъём бедра при сгибе колена, пиксели модели
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
        return p.getMainHandStack().getItem() instanceof PaladinSwordItem
                || p.getOffHandStack().getItem() instanceof PaladinShieldItem
                || p.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof PaladinArmorItem;
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
        boolean rightHanded = p.getMainArm() == net.minecraft.util.Arm.RIGHT;

        float ph = limbPos * 0.6662f;
        float c = MathHelper.cos(ph), sn = Math.abs(MathHelper.sin(ph));
        // «сгиб колена»: нога, которая идёт вперёд (в переносе), чуть поднимается, стопа отстаёт назад
        float kR = Math.max(0f, -MathHelper.sin(ph)), kL = Math.max(0f, MathHelper.sin(ph));
        // «сгиб локтя»: рука, ушедшая вперёд, сгибается сильнее
        float fR = Math.max(0f, -c), fL = Math.max(0f, c);
        float age = e.age + tickDelta;
        float breath = MathHelper.sin(age * 0.09f);

        // ---------------- покой: боевая стойка
        Loco I = new Loco();
        I.bob = 0.012f * breath - 0.01f;
        I.turn = -16f;
        I.lean = 2f;
        I.rP = sword ? -0.22f + 0.03f * breath : 0.02f * breath;
        I.rY = sword ? -0.12f : 0f;
        I.rR = sword ? 0.14f : 0.06f;
        I.lP = shield ? -0.58f + 0.02f * breath : -0.12f;
        I.lY = shield ? 0.5f : 0.15f;
        I.lR = shield ? -0.1f : -0.08f;
        I.body = 0.06f;
        I.rLeg = 0.2f; I.lLeg = -0.16f; I.rLegR = 0.06f; I.lLegR = -0.07f;
        I.rLift = 0.35f; I.lLift = 0.5f;          // колени чуть согнуты

        // ---------------- ходьба
        Loco W = new Loco();
        W.bob = -0.05f * (1f - sn) + 0.012f;
        W.lean = 5f;
        W.turn = -6f;
        W.rLeg = -0.55f * c + 0.2f * kR;
        W.lLeg = 0.55f * c + 0.2f * kL;
        W.rLift = 1.4f * kR;
        W.lLift = 1.4f * kL;
        W.body = 0.2f * c;
        W.rP = sword ? -0.28f + 0.24f * c - 0.1f * fR : 0.42f * c - 0.12f - 0.22f * fR;
        W.rY = sword ? 0.16f * c - 0.08f : 0.1f * c;
        W.rR = sword ? 0.12f : 0.05f;
        W.lP = shield ? -0.58f - 0.1f * c : -0.42f * c - 0.12f - 0.22f * fL;
        W.lY = shield ? 0.45f + 0.12f * c : 0.1f * c;
        W.lR = shield ? -0.1f : -0.05f;

        // ---------------- бег
        Loco R = new Loco();
        R.bob = -0.1f * (1f - sn) + 0.02f;
        R.lean = 18f;
        R.turn = 0f;
        R.rLeg = -0.95f * c + 0.38f * kR;
        R.lLeg = 0.95f * c + 0.38f * kL;
        R.rLift = 3.0f * kR;
        R.lLift = 3.0f * kL;
        R.body = 0.14f * c;
        R.rP = sword ? 0.5f + 0.32f * c : -0.3f + 0.85f * c - 0.4f * fR;
        R.rY = sword ? -0.18f : 0.1f;
        R.rR = sword ? 0.38f : 0.1f;
        R.lP = shield ? -0.98f - 0.14f * c : -0.3f - 0.85f * c - 0.4f * fL;
        R.lY = shield ? 0.58f : -0.1f;
        R.lR = shield ? -0.14f : -0.1f;

        // ---------------- в воздухе
        Loco A = new Loco();
        A.bob = 0f; A.lean = 6f; A.turn = -8f;
        A.rLeg = -0.55f; A.lLeg = 0.2f; A.rLegR = 0.08f; A.lLegR = -0.08f;
        A.rLift = 2.2f; A.lLift = 1.2f;
        A.body = 0.05f;
        A.rP = sword ? 0.15f : -0.4f; A.rY = 0f; A.rR = sword ? 0.45f : 0.25f;
        A.lP = shield ? -0.95f : -0.4f; A.lY = shield ? 0.5f : 0f; A.lR = -0.25f;

        Loco out = mix(mix(mix(I, W, s.move), R, s.run * s.move), A, s.air);
        if (!rightHanded) {        // левша: зеркалим руки
            float rP = out.rP, rY = out.rY, rR = out.rR;
            out.rP = out.lP; out.rY = -out.lY; out.rR = -out.lR;
            out.lP = rP; out.lY = -rY; out.lR = -rR;
            out.turn = -out.turn;
            out.body = -out.body;
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
        o.rLift = MathHelper.lerp(t, a.rLift, b.rLift); o.lLift = MathHelper.lerp(t, a.lLift, b.lLift);
        return o;
    }
}
