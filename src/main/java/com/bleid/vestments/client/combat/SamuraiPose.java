package com.bleid.vestments.client.combat;

import com.bleid.vestments.client.bend.ArmIk;
import com.bleid.vestments.paladin.combat.Clip;
import com.bleid.vestments.samurai.KatanaItem;
import com.bleid.vestments.samurai.SamuraiMoves;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Позы самурая: стойка тюдан (катана в двух руках, пока она в руке), ключевые позы (иай, ното,
 * парирование, медитация, рывок) и смешивание приёма со стойкой. Числа — из moves.json (как в 3D-превью).
 */
@Environment(EnvType.CLIENT)
public final class SamuraiPose {
    private static final Vector3f POLE_R = new Vector3f(-0.4f, 0.35f, 1f), POLE_L = new Vector3f(0.4f, 0.35f, 1f);

    private SamuraiPose() { }

    /** Поза без приёма у того, кто держит катану: стойка (или катана в ножнах — руки свободны). */
    public static CombatPose.Pose base(LivingEntity e, float tickDelta) {
        if (!(e.getMainHandStack().getItem() instanceof KatanaItem k)) return null;
        if (CombatClient.isSheathed(e)) return sheathedIdle(e);
        Clip stance = SamuraiMoves.clip(k.spec.id, "stance");
        if (stance == null) return null;
        CombatPose.Pose p = new CombatPose.Pose();
        p.clip = stance;
        float t = (e.age + tickDelta) / 20f * CombatClient.SPEED;
        p.time = t;
        keyed(e, p, stance, t, false);
        // стоя на месте ноги и корпус — из стойки, при ходьбе — от ходьбы
        float walk = e.limbAnimator.getSpeed(tickDelta);
        p.w = SamuraiMoves.FP.containsKey("stance") || stanceEdited() ? MathHelper.clamp(1f - walk * 3f, 0f, 1f) : 0f;
        p.armW = 1f;         // руки — в стойке
        return p;
    }

    /** Катана в ножнах: руки свободны, предмет не рисуется в руке. */
    public static CombatPose.Pose sheathedIdle(LivingEntity e) {
        Clip stance = e.getMainHandStack().getItem() instanceof KatanaItem k ? SamuraiMoves.clip(k.spec.id, "stance")
                : SamuraiMoves.clip("bokken", "stance");
        CombatPose.Pose p = new CombatPose.Pose();
        p.clip = stance;
        p.w = 0f;
        p.armW = 0f;
        p.draw = new DrawPose.Out();
        p.draw.inHand = false;
        p.blade = null;
        return p;
    }

    public static CombatPose.Pose keyed(LivingEntity e, CombatPose.Pose p, CombatClient.Playback pb, float t) {
        boolean forceSheath = SamuraiMoves.nameOf(pb.clip).equals("iai") && pb.startedSheathed;
        keyed(e, p, pb.clip, t, forceSheath);
        return p;
    }

    /** Ключевая поза: кисти по обратной кинематике, клинок задан явно (всё в повороте корпуса). */
    static void keyed(LivingEntity e, CombatPose.Pose p, Clip c, float t, boolean forceSheath) {
        float L = c.length;
        float u = c.loop ? (t % L) / L : MathHelper.clamp(t / L, 0f, 1f);
        SamuraiMoves.Key k = SamuraiMoves.keyAt(c.keys, u);
        Matrix3f R = new Matrix3f().rotationY(k.body);
        Vector3f sh = R.transform(v(k.sh)), osh = R.transform(v(k.osh));
        Vector3f hand = R.transform(v(k.hand)), off = R.transform(v(k.off));
        float[] a = new float[3];
        p.mainElbow = ArmIk.solve(new Vector3f(hand).sub(sh), v(k.poleR), a);
        p.armPitch = a[0]; p.armYaw = a[1]; p.armRoll = a[2];
        p.offElbow = ArmIk.solve(new Vector3f(off).sub(osh), v(k.poleL), a);
        p.offPitch = a[0]; p.offYaw = a[1]; p.offRoll = a[2];
        p.shX = sh.x; p.shY = sh.y; p.shZ = sh.z;
        p.offShX = osh.x; p.offShY = osh.y; p.offShZ = osh.z;
        p.bodyYaw = k.body;
        p.rLeg = k.legs[0];
        p.lLeg = k.legs[1];
        p.rLegYaw = k.legYaw[0]; p.lLegYaw = k.legYaw[1];
        p.rLegRoll = k.legRoll[0]; p.lLegRoll = k.legRoll[1];
        p.kneeR = Math.max(0, k.knees[0]);
        p.kneeL = Math.max(0, k.knees[1]);
        p.headPitch = k.head;
        if (c.lean != null) p.lean = c.lean.at(t);
        p.spin = c.spin.at(t);
        p.lift = c.lift.at(t);
        DrawPose.Out d = new DrawPose.Out();
        d.dir.set(R.transform(v(k.dir))).normalize();
        d.normal.set(R.transform(v(k.normal))).normalize();
        // рукоять — в середине кисти (ось руки Майнкрафта на пиксель сбоку), плюс сдвиг катаны от кисти
        // и положение катаны в кулаке, как в «Мастерской анимаций»
        Vector3f fist = new Matrix4f().translate(sh).rotateZ(p.armRoll).rotateY(p.armYaw).rotateX(p.armPitch)
                .translate(0, 4f, 0).rotateX(-p.mainElbow).translate(0, -4f, 0).transformPosition(new Vector3f(-1f, 9f, 0f));
        Vector3f bx = new Vector3f(d.dir).cross(d.normal).normalize();
        float[] g = SamuraiMoves.grip;
        d.hand.set(fist).add(R.transform(v(k.katOff)))
                .add(new Vector3f(bx).mul(g[0])).add(new Vector3f(d.dir).mul(g[1])).add(new Vector3f(d.normal).mul(g[2]));
        d.shoulder.set(sh);
        d.offHand.set(off);
        d.offShoulder.set(osh);
        d.kneeR = p.kneeR;
        d.kneeL = p.kneeL;
        d.inHand = !forceSheath && c.inHandAt(u);
        if (forceSheath && u >= 0.2f) d.inHand = c.inHandAt(u);
        p.draw = d;
    }

    /** Приём поверх стойки: руки — полностью (вес приёма между стойкой и приёмом), ноги и корпус — с весом приёма. */
    public static CombatPose.Pose blend(CombatPose.Pose base, CombatPose.Pose c, float t) {
        if (c == null) return base;
        if (base == null) return c;
        float w = c.w;
        CombatPose.Pose r = c;
        float ab = base.arms();
        // руки: от позы стойки к позе приёма
        r.armPitch = lerpA(w, base.armPitch * ab, c.armPitch);
        r.armYaw = lerpA(w, base.armYaw * ab, c.armYaw);
        r.armRoll = lerpA(w, base.armRoll * ab, c.armRoll);
        r.offPitch = lerpA(w, base.offPitch * ab, c.offPitch);
        r.offYaw = lerpA(w, base.offYaw * ab, c.offYaw);
        r.offRoll = lerpA(w, base.offRoll * ab, c.offRoll);
        r.mainElbow = MathHelper.lerp(w, base.mainElbow * ab, c.mainElbow);
        r.offElbow = MathHelper.lerp(w, base.offElbow * ab, c.offElbow);
        r.headPitch = MathHelper.lerp(w, base.headPitch, c.headPitch);
        r.armW = ab > 0.5f ? 1f : w;
        if (base.draw != null && ab > 0.5f) {
            if (c.draw != null) {
                // обе позы по ключам: смешиваем кисти, клинок и плечи
                DrawPose.Out d = new DrawPose.Out();
                d.hand.set(base.draw.hand).lerp(c.draw.hand, w);
                d.dir.set(base.draw.dir).lerp(c.draw.dir, w).normalize();
                d.normal.set(base.draw.normal).lerp(c.draw.normal, w).normalize();
                d.shoulder.set(base.draw.shoulder).lerp(c.draw.shoulder, w);
                d.offHand.set(base.draw.offHand).lerp(c.draw.offHand, w);
                d.offShoulder.set(base.draw.offShoulder).lerp(c.draw.offShoulder, w);
                d.kneeR = c.draw.kneeR;
                d.kneeL = c.draw.kneeL;
                d.inHand = w > 0.5f ? c.draw.inHand : base.draw.inHand;
                r.draw = d;
                r.shX = d.shoulder.x; r.shY = d.shoulder.y; r.shZ = d.shoulder.z;
                r.offShX = d.offShoulder.x; r.offShY = d.offShoulder.y; r.offShZ = d.offShoulder.z;
                r.bodyYaw = MathHelper.lerp(w, base.bodyYaw, c.bodyYaw);
            } else {
                // удар: катана держится по руке; в начале и в конце — плавно из хвата стойки
                r.baseDraw = base.draw;
                r.baseW = 1f - w;
                r.bodyYaw = MathHelper.lerp(w, base.bodyYaw, c.bodyYaw);
            }
        }
        r.w = w;
        return r;
    }

    private static float lerpA(float t, float a, float b) {
        return a + MathHelper.wrapDegrees((float) Math.toDegrees(b - a)) * (float) (Math.PI / 180.0) * t;
    }

    private static Boolean edited;
    /** Стойку переделали в «Мастерской анимаций» (в ключах есть её поля). */
    private static boolean stanceEdited() {
        if (edited == null) {
            java.util.List<SamuraiMoves.Key> ks = SamuraiMoves.KEYSETS.get("stance");
            edited = ks != null && !ks.isEmpty() && (ks.get(0).legYaw[0] != 0 || ks.get(0).legYaw[1] != 0 || ks.get(0).katOff[0] != 0);
        }
        return edited;
    }

    private static Vector3f v(float[] a) {
        return new Vector3f(a[0], a[1], a[2]);
    }
}
