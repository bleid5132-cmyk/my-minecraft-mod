package com.bleid.vestments.client.combat;

import com.bleid.vestments.paladin.combat.Blade;
import com.bleid.vestments.paladin.combat.Clip;
import com.bleid.vestments.paladin.combat.Strike;
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

        // текущий взмах приёма и его окно (от конца предыдущего до конца этого)
        Strike cur = null;
        float tStart = 0f;
        for (Strike k : pb.clip.strikes) {
            cur = k;
            if (t < k.rec) break;
            tStart = k.rec;
        }
        Vec3d d = p.blade.dir(), n = p.blade.normal();

        // ---- тело само «работает» за клинком: закрутка корпуса в замах, раскрутка в удар,
        // наклон за высотой клинка, проседание в момент контакта (по образцу Epic Fight / souls-like)
        float imp = 0f, coil = 0f;
        if (cur != null) {
            boolean spinning = cur.sweep() > 200f;
            float autoK = spinning ? 0f : 1f;
            p.spin += (float) MathHelper.clamp(d.y * 20.0, -20.0, 20.0) * autoK;
            p.lean += (float) (-d.z * 7.0) * autoK;
            float dc = (t - cur.contact + 0.02f) / (cur.heavy ? 0.12f : 0.09f);
            imp = (float) Math.exp(-dc * dc) * (cur.heavy ? 1.5f : 1f);
            if (t > tStart && t < cur.pre) coil = Clip.smooth((t - tStart) / Math.max(0.05f, cur.pre - tStart));
            else if (t >= cur.pre && t < cur.contact) coil = 1f - Clip.smooth((t - cur.pre) / Math.max(0.03f, cur.contact - cur.pre));
            p.lift -= 0.045f * imp + 0.02f * coil;
            p.lean += 4f * imp;
        }

        // корень (в пространстве setupTransforms): поворот вправо = Y(-spin), наклон вперёд = X(-lean)
        Quaternionf root = new Quaternionf()
                .rotateY((float) Math.toRadians(-p.spin * p.w))
                .rotateX((float) Math.toRadians(-p.lean * p.w));
        Quaternionf inv = new Quaternionf(root).conjugate();
        Vector3f dS = inv.transform(new Vector3f((float) d.y, (float) d.z, (float) -d.x));
        Vector3f nS = inv.transform(new Vector3f((float) n.y, (float) n.z, (float) -n.x));
        // модель: x влево, y вниз, z назад
        Vector3f Y = new Vector3f(-dS.x, -dS.y, dS.z).normalize();
        Vector3f Z = new Vector3f(-nS.x, -nS.y, nS.z);
        Z.sub(new Vector3f(Y).mul(Z.dot(Y))).normalize();
        Vector3f X = new Vector3f(Y).cross(Z).normalize();
        Matrix3f fore = new Matrix3f().setColumn(0, X).setColumn(1, Y).setColumn(2, Z);
        // предплечье = рука·Rx(-локоть) должно лечь вдоль клинка → плечо = клинок·Rx(локоть)
        float elbow = STRIKE_ELBOW + 0.25f * coil - 0.15f * imp;      // в замахе рука собирается, в ударе — выпрямляется
        Matrix3f m = new Matrix3f(fore).mul(new Matrix3f().rotationX(elbow));
        p.mainElbow = elbow;
        Vector3f eul = m.getEulerAnglesZYX(new Vector3f());
        p.armPitch = eul.x;
        p.armYaw = eul.y;
        p.armRoll = eul.z;
        if (pb.hitstop > 0) {                    // отдача клинка в момент попадания — мелкая дрожь
            float j = (float) Math.sin(System.nanoTime() / 1e9 * 90.0);
            p.armPitch += 0.03f * j;
            p.armRoll += 0.02f * j;
        }

        // корпус доворачивается за клинком
        float twist = (float) Math.atan2(dS.x, -dS.z);
        p.bodyYaw = MathHelper.clamp(twist * 0.5f, -0.75f, 0.75f);

        // ---- вторая рука
        boolean shield = e.getOffHandStack().getItem() instanceof com.bleid.vestments.paladin.PaladinShieldItem;
        boolean free = e.getOffHandStack().isEmpty();
        float swing = (float) Math.sin(MathHelper.clamp(t / Math.max(0.05f, pb.clip.length), 0, 1) * Math.PI);
        if (shield) {
            // щит в защите; на мощных ударах — отводится назад-вбок для закрутки корпуса
            p.offPitch = -0.45f + 0.25f * swing;
            p.offYaw = 0.35f - 0.25f * swing;
            p.offRoll = -0.12f - 0.2f * swing;
            p.offElbow = 0.55f;
            if (cur != null && cur.twoHand) {
                float tw = twoWeight(cur, t, tStart);
                p.offPitch += 0.55f * tw;
                p.offYaw -= 0.45f * tw;
                p.offRoll -= 0.4f * tw;
                p.offElbow += 0.35f * tw;
            }
        } else {
            // свободная рука уравновешивает удар: уходит в сторону, противоположную клинку
            p.offPitch = -0.25f + 0.3f * imp + (float) d.y * 0.25f;
            p.offYaw = 0.15f;
            p.offRoll = -0.3f - 0.3f * imp - 0.15f * coil;
            p.offElbow = 0.65f + 0.3f * coil;
        }
        if (!pb.clip.offPitch.isEmpty()) {
            p.offPitch = pb.clip.offPitch.at(t) + 0.3f;
            p.offYaw = pb.clip.offYaw.at(t);
            p.offRoll = pb.clip.offRoll.at(t);
            p.offElbow = 0.55f;
        }
        if (free && cur != null && cur.twoHand) {
            // хват двумя руками: вторая кисть ложится на рукоять под первой
            float tw = twoWeight(cur, t, tStart);
            if (tw > 0.001f) {
                float by = p.bodyYaw;
                Vector3f pm = new Vector3f(-MathHelper.cos(by) * 5f, 2f, MathHelper.sin(by) * 5f);
                Vector3f po = new Vector3f(MathHelper.cos(by) * 5f, 2f, -MathHelper.sin(by) * 5f);
                Vector3f elbowPos = new Vector3f(-1f, 4f, 0f).mul(m).add(pm);
                Vector3f hand = new Vector3f(0f, 6f, 0f).mul(fore).add(elbowPos);
                Vector3f side = new Vector3f(po).sub(pm);
                side.sub(new Vector3f(Y).mul(side.dot(Y)));
                if (side.lengthSquared() > 1e-4f) side.normalize();
                Vector3f grip = new Vector3f(hand).sub(new Vector3f(Y).mul(2.2f)).add(new Vector3f(side).mul(2.0f));
                float[] a = new float[3];
                float oe = com.bleid.vestments.client.bend.ArmIk.solve(grip.sub(po), POLE_L, a);
                p.offPitch = lerpAngle(tw, p.offPitch, a[0]);
                p.offYaw = lerpAngle(tw, p.offYaw, a[1]);
                p.offRoll = lerpAngle(tw, p.offRoll, a[2]);
                p.offElbow = MathHelper.lerp(tw, p.offElbow, oe);
            }
        }

        // ---- ноги: выпад по дорожке стойки, колени пружинят в замахе и в момент удара
        p.rLeg = -0.5f * stance;
        p.lLeg = 0.42f * stance;
        float bounce = 0.2f * coil + 0.4f * imp;
        p.kneeR = 0.18f + 0.35f * Math.max(0f, stance) + 0.2f * Math.max(0f, -stance) + bounce;
        p.kneeL = 0.18f + 0.35f * Math.max(0f, -stance) + 0.2f * Math.max(0f, stance) + bounce * 0.8f;
        return p;
    }

    /** Доля позы выхватывания: при убирании в ножны — обратный ход. */
    public static float drawU(Clip c, float t) {
        float u = MathHelper.clamp(t / c.length, 0f, 1f);
        return c.reverse ? 1f - u : u;
    }

    /** Насколько вторая рука держит рукоять: входит за время замаха, выходит после довода. */
    private static float twoWeight(Strike k, float t, float tStart) {
        float in = Clip.smooth((t - tStart) / Math.max(0.06f, (k.pre - tStart) * 0.7f));
        float out = 1f - Clip.smooth((t - k.rec) / 0.2f);
        return Math.min(in, out);
    }

    private static float lerpAngle(float t, float a, float b) {
        return a + MathHelper.wrapDegrees((float) Math.toDegrees(b - a)) * (float) (Math.PI / 180.0) * t;
    }

    private static Pose sheath(LivingEntity e, Pose p, CombatClient.Playback pb, float t) {
        boolean right = e.getMainArm() == net.minecraft.util.Arm.RIGHT;
        boolean shield = e.getOffHandStack().getItem() instanceof com.bleid.vestments.paladin.PaladinShieldItem;
        float u = drawU(pb.clip, t);
        DrawPose.Out d = DrawPose.at(u, right, shield, new DrawPose.Out());
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
        // шаг в выпад к концу: правая (ведущая) нога вперёд
        float st = u < 0.7f ? 0f : Math.min(1f, (u - 0.7f) / 0.15f);
        p.rLeg = -0.32f * st + 0.08f;    // зеркалится для левши в BipedEntityModelCombatMixin
        p.lLeg = 0.26f * st - 0.1f;
        return p;
    }
}
