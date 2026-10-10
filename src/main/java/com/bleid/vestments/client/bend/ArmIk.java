package com.bleid.vestments.client.bend;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Двухзвенная обратная кинематика руки/ноги (плечо → локоть → кисть) в пространстве модели
 * (пиксели; x — влево, y — вниз, z — назад). Возвращает углы части (pitch, yaw, roll) и сгиб сустава
 * в том же смысле, что и {@link Bends}: предплечье сгибается к -Z локальной системы руки.
 */
@Environment(EnvType.CLIENT)
public final class ArmIk {
    /** Плечо → локоть, локоть → хват (центр кулака). */
    public static final float UPPER = 4f, LOWER = 6f;

    private ArmIk() { }

    /**
     * @param rel  кисть относительно плеча (пиксели модели)
     * @param pole куда «смотрит» локоть (назад-вниз-наружу)
     * @param out  {pitch, yaw, roll}
     * @return сгиб локтя (радианы, ≥ 0)
     */
    public static float solve(Vector3f rel, Vector3f pole, float[] out) {
        float l1 = UPPER, l2 = LOWER;
        float d = rel.length();
        Vector3f t = d < 1e-4f ? new Vector3f(0, 1, 0) : new Vector3f(rel).div(d);
        // не даём локтю сложиться сильнее ~125° — иначе изогнутая рука выглядит сломанной
        d = Math.max(5.0f, Math.min(l1 + l2 - 0.02f, d));
        float cosA = (l1 * l1 + d * d - l2 * l2) / (2 * l1 * d);
        cosA = Math.max(-1f, Math.min(1f, cosA));
        float sinA = (float) Math.sqrt(1 - cosA * cosA);
        Vector3f b = new Vector3f(pole).sub(new Vector3f(t).mul(t.dot(pole)));
        if (b.lengthSquared() < 1e-6f) {
            b = new Vector3f(0, 0, 1).sub(new Vector3f(t).mul(t.z));
            if (b.lengthSquared() < 1e-6f) b = new Vector3f(1, 0, 0);
        }
        b.normalize();
        Vector3f elbow = new Vector3f(t).mul(l1 * cosA).add(new Vector3f(b).mul(l1 * sinA));
        Vector3f u = new Vector3f(elbow).div(l1);
        Vector3f f = new Vector3f(t).mul(d).sub(elbow).normalize();
        float cosE = Math.max(-1f, Math.min(1f, u.dot(f)));
        float bend = (float) Math.acos(cosE);
        Vector3f z = new Vector3f(f).sub(new Vector3f(u).mul(cosE)).negate();
        if (z.lengthSquared() < 1e-6f) z = new Vector3f(b).sub(new Vector3f(u).mul(u.dot(b)));
        z.normalize();
        Vector3f x = new Vector3f(u).cross(z).normalize();
        euler(x, u, z, out);
        return bend;
    }

    /** Углы части по столбцам её системы координат (как у ModelPart: Rz·Ry·Rx). */
    public static void euler(Vector3f x, Vector3f y, Vector3f z, float[] out) {
        Matrix3f m = new Matrix3f().setColumn(0, x).setColumn(1, y).setColumn(2, z);
        Vector3f e = m.getEulerAnglesZYX(new Vector3f());
        out[0] = e.x;
        out[1] = e.y;
        out[2] = e.z;
    }
}
