package com.bleid.vestments.client.combat;

import com.bleid.vestments.paladin.combat.Blade;
import com.bleid.vestments.paladin.combat.Clip;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Поза тела во время приёма. Рука с мечом решается «обратной кинематикой» от положения клинка:
 * рука вытянута вдоль клинка, плоскость клинка лежит в плоскости взмаха — поэтому рука, меч,
 * след и хитбокс всегда совпадают. Корпус доворачивается за клинком, всё тело наклоняется,
 * вращается и подпрыгивает по дорожкам приёма; ноги встают в выпад.
 */
@Environment(EnvType.CLIENT)
public final class CombatPose {
    public static final class Pose {
        public float w;
        public float armPitch, armYaw, armRoll;     // рука с мечом (модель, радианы)
        public float offPitch, offYaw, offRoll;     // вторая рука (щит)
        public float bodyYaw, rLeg, lLeg;
        public float lean, spin, lift;              // корень: градусы, градусы, блоки
        public Blade.State blade;
        public float time;
        public Clip clip;
        // сгибы (bendy-lib): локти руки с мечом и второй руки, колени
        public float mainElbow, offElbow, kneeR, kneeL;
        // выхватывание из ножен: руки по обратной кинематике, меч — по ключам
        public DrawPose.Out draw;
        public float shX, shY, shZ, offShX, offShY, offShZ;
    }

    /** Локоть руки с мечом во время ударов: рука чуть согнута, предплечье продолжает клинок. */
    private static final float STRIKE_ELBOW = 0.32f;
    private static final Vector3f POLE_R = new Vector3f(-0.4f, 0.35f, 1f), POLE_L = new Vector3f(0.4f, 0.35f, 1f);

    private CombatPose() { }

    private static float wrapDeg(float d) {
        d %= 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    public static Pose compute(LivingEntity e, float tickDelta) {
        CombatClient.Playback pb = CombatClient.get(e);
        if (pb == null) return null;
        Pose p = new Pose();
        float t = CombatClient.time(pb, tickDelta);
        p.time = t;
        p.clip = pb.clip;
        p.w = pb.clip.weight(t);
        if (p.w <= 0.001f) return null;
        p.blade = Blade.at(pb.clip, t, pb.ms.length);
        p.lean = pb.clip.lean.at(t);
        p.spin = wrapDeg(pb.clip.spin.at(t));
        p.lift = pb.clip.lift.at(t);
        float stance = pb.clip.stance.at(t);
        if (pb.clip.sheath) return sheath(e, p, pb, t);

        // корень (в пространстве setupTransforms): поворот вправо = Y(-spin), наклон вперёд = X(-lean)
        Quaternionf root = new Quaternionf()
                .rotateY((float) Math.toRadians(-p.spin * p.w))
                .rotateX((float) Math.toRadians(-p.lean * p.w));
        Quaternionf inv = new Quaternionf(root).conjugate();
        Vec3d d = p.blade.dir(), n = p.blade.normal();
        Vector3f dS = inv.transform(new Vector3f((float) d.y, (float) d.z, (float) -d.x));
        Vector3f nS = inv.transform(new Vector3f((float) n.y, (float) n.z, (float) -n.x));
        // модель: x влево, y вниз, z назад
        Vector3f Y = new Vector3f(-dS.x, -dS.y, dS.z).normalize();
        Vector3f Z = new Vector3f(-nS.x, -nS.y, nS.z);
        Z.sub(new Vector3f(Y).mul(Z.dot(Y))).normalize();
        Vector3f X = new Vector3f(Y).cross(Z).normalize();
        // предплечье = рука·Rx(-локоть) должно лечь вдоль клинка → плечо = клинок·Rx(локоть)
        Matrix3f m = new Matrix3f().setColumn(0, X).setColumn(1, Y).setColumn(2, Z)
                .mul(new Matrix3f().rotationX(STRIKE_ELBOW));
        p.mainElbow = STRIKE_ELBOW;
        Vector3f eul = m.getEulerAnglesZYX(new Vector3f());
        p.armPitch = eul.x;
        p.armYaw = eul.y;
        p.armRoll = eul.z;

        // корпус доворачивается за клинком
        float twist = (float) Math.atan2(dS.x, -dS.z);
        p.bodyYaw = MathHelper.clamp(twist * 0.35f, -0.6f, 0.6f);
        // щит/вторая рука — в защитной стойке, слегка отводится при ударе
        float swing = (float) Math.sin(MathHelper.clamp(t / Math.max(0.05f, pb.clip.length), 0, 1) * Math.PI);
        p.offPitch = -0.75f + 0.25f * swing;
        p.offYaw = 0.35f - 0.25f * swing;
        p.offRoll = -0.12f - 0.2f * swing;
        if (!pb.clip.offPitch.isEmpty()) {
            p.offPitch = pb.clip.offPitch.at(t);
            p.offYaw = pb.clip.offYaw.at(t);
            p.offRoll = pb.clip.offRoll.at(t);
        }
        // сгиб локтя второй руки: плечо приподнимаем меньше, предплечье выносит щит вперёд
        p.offElbow = 0.55f;
        p.offPitch += 0.3f;
        p.rLeg = -0.5f * stance;
        p.lLeg = 0.42f * stance;
        p.kneeR = 0.18f + 0.35f * Math.max(0f, stance) + 0.2f * Math.max(0f, -stance);
        p.kneeL = 0.18f + 0.35f * Math.max(0f, -stance) + 0.2f * Math.max(0f, stance);
        return p;
    }

    private static Pose sheath(LivingEntity e, Pose p, CombatClient.Playback pb, float t) {
        boolean right = e.getMainArm() == net.minecraft.util.Arm.RIGHT;
        boolean shield = e.getOffHandStack().getItem() instanceof com.bleid.vestments.paladin.PaladinShieldItem;
        DrawPose.Out d = DrawPose.at(t / pb.clip.length, right, shield, new DrawPose.Out());
        p.draw = d;
        float[] a = new float[3];
        // рука с мечом (плечо — в main, вторая — в off; правша/левша уже учтены в позах)
        Vector3f rel = new Vector3f(d.hand).sub(d.shoulder);
        p.mainElbow = com.bleid.vestments.client.bend.ArmIk.solve(rel, right ? POLE_R : POLE_L, a);
        p.armPitch = a[0]; p.armYaw = a[1]; p.armRoll = a[2];
        Vector3f orel = new Vector3f(d.offHand).sub(d.offShoulder);
        p.offElbow = com.bleid.vestments.client.bend.ArmIk.solve(orel, right ? POLE_L : POLE_R, a);
        p.offPitch = a[0]; p.offYaw = a[1]; p.offRoll = a[2];
        p.shX = d.shoulder.x; p.shY = d.shoulder.y; p.shZ = d.shoulder.z;
        p.offShX = d.offShoulder.x; p.offShY = d.offShoulder.y; p.offShZ = d.offShoulder.z;
        p.kneeR = d.kneeR; p.kneeL = d.kneeL;
        p.bodyYaw = 0;
        float u = t / pb.clip.length;
        // шаг в выпад к концу: правая (ведущая) нога вперёд
        float st = u < 0.62f ? 0f : Math.min(1f, (u - 0.62f) / 0.18f);
        p.rLeg = -0.32f * st + 0.08f;    // зеркалится для левши в BipedEntityModelCombatMixin
        p.lLeg = 0.26f * st - 0.1f;
        return p;
    }
}
