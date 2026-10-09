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
    }

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
        Matrix3f m = new Matrix3f().setColumn(0, X).setColumn(1, Y).setColumn(2, Z);
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
        p.rLeg = -0.5f * stance;
        p.lLeg = 0.42f * stance;
        return p;
    }
}
