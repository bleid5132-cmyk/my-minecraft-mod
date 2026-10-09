package com.bleid.vestments.client.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector3f;

/**
 * Выхватывание меча из ножен (как в Epic Fight): рука тянется через корпус к рукояти на левом бедре,
 * левая рука придерживает ножны, клинок вытягивается вдоль ножен, широким росчерком уходит за плечо
 * и резко встаёт в боевую стойку, после чего тело «оседает».
 *
 * Всё задано ключами в пространстве модели (пиксели; x — влево, y — вниз, z — назад) для правши:
 * положение хвата меча, направление клинка, его плоскость, плечи, вторая рука и колени.
 * Между ключами — гладкий сплайн Эрмита (без остановок, кроме момента хвата рукояти).
 */
@Environment(EnvType.CLIENT)
public final class DrawPose {
    /** Ножны (в пространстве корпуса): устье, направление клинка в ножнах, плоскость клинка. */
    public static final Vector3f SHEATH_MOUTH = new Vector3f(4.6f, 10.5f, -2.9f);
    public static final Vector3f SHEATH_DIR = new Vector3f(0.45f, 0.62f, 0.64f).normalize();
    public static final Vector3f SHEATH_NORMAL = ortho(new Vector3f(1, 0, 0), SHEATH_DIR);
    /** От гарды до середины рукояти (пиксели модели, с учётом масштаба меча 0.85). */
    public static final float GRIP_FROM_GUARD = 1.96f;
    /** Доля приёма, когда ладонь сомкнулась на рукояти (до этого меч в ножнах). */
    public static final float GRAB = 0.27f;
    /** Доля приёма, когда клинок вышел из ножен (звон). */
    public static final float CLEAR = 0.5f;

    public static final class Out {
        public final Vector3f hand = new Vector3f(), dir = new Vector3f(), normal = new Vector3f();
        public final Vector3f shoulder = new Vector3f(), offHand = new Vector3f(), offShoulder = new Vector3f();
        public float kneeR, kneeL;
        public boolean inHand;
    }

    private static final class Key {
        final float u;
        final boolean hold;
        final Vector3f[] v;       // hand, dir, normal, shoulder, offHand, offShoulder, (knees as x,y)

        Key(float u, boolean hold, Vector3f... v) {
            this.u = u;
            this.hold = hold;
            this.v = v;
        }
    }

    private static final int N = 7;
    private static final Key[] PLAIN, SHIELD;

    static {
        Vector3f grip = new Vector3f(SHEATH_MOUTH).sub(new Vector3f(SHEATH_DIR).mul(GRIP_FROM_GUARD));
        Vector3f pulled = new Vector3f(grip).sub(new Vector3f(SHEATH_DIR).mul(8f)).add(0, -0.4f, 0);
        Vector3f hold = new Vector3f(SHEATH_MOUTH).add(new Vector3f(SHEATH_DIR).mul(2.5f)).add(0.8f, 0, 0);
        Vector3f hold2 = new Vector3f(SHEATH_MOUTH).add(new Vector3f(SHEATH_DIR).mul(1.5f)).add(0.9f, 0, 0);
        Vector3f dPull = new Vector3f(0.55f, 0.35f, 0.76f).normalize();
        Vector3f dUp = new Vector3f(-0.25f, -0.8f, 0.55f).normalize();
        Vector3f dGuard = new Vector3f(0.12f, -0.42f, -0.9f).normalize();
        Vector3f dRest = new Vector3f(0.05f, -0.3f, -0.95f).normalize();
        Vector3f x = new Vector3f(1, 0, 0);
        Key k0 = new Key(0f, false, v(-5.5f, 11f, -1f), SHEATH_DIR, SHEATH_NORMAL, v(-5, 2, 0), v(5.5f, 11f, -0.5f), v(5, 2, 0), v(0.1f, 0.1f, 0));
        Key k1 = new Key(GRAB, true, grip, SHEATH_DIR, SHEATH_NORMAL, v(-3f, 3f, -2.8f), hold, v(4.8f, 2.6f, -0.4f), v(0.38f, 0.52f, 0));
        Key k2 = new Key(0.47f, false, pulled, dPull, ortho(x, dPull), v(-4f, 2.2f, -1.6f), hold2, v(4.8f, 2.6f, -0.4f), v(0.3f, 0.45f, 0));
        Key k3 = new Key(0.62f, false, v(-6f, -1.5f, -6.5f), dUp, ortho(x, dUp), v(-5.2f, 1.4f, -0.6f), v(8.5f, 8f, 1.5f), v(5, 2, 0), v(0.12f, 0.22f, 0));
        Key k4p = new Key(0.8f, false, v(-3f, 7f, -7.5f), dGuard, ortho(x, dGuard), v(-5f, 2f, -0.6f), v(2.5f, 8f, -4.5f), v(5, 2, 0), v(0.5f, 0.32f, 0));
        Key k4s = new Key(0.8f, false, v(-3f, 7f, -7.5f), dGuard, ortho(x, dGuard), v(-5f, 2f, -0.6f), v(3.5f, 7.5f, -6.5f), v(5, 2, 0), v(0.5f, 0.32f, 0));
        Key k5p = new Key(1f, false, v(-5f, 9f, -4.5f), dRest, ortho(x, dRest), v(-5, 2, 0), v(3f, 9f, -3.5f), v(5, 2, 0), v(0.28f, 0.38f, 0));
        Key k5s = new Key(1f, false, v(-5f, 9f, -4.5f), dRest, ortho(x, dRest), v(-5, 2, 0), v(3.2f, 8f, -5.8f), v(5, 2, 0), v(0.28f, 0.38f, 0));
        PLAIN = new Key[] { k0, k1, k2, k3, k4p, k5p };
        SHIELD = new Key[] { k0, k1, k2, k3, k4s, k5s };
    }

    private DrawPose() { }

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    static Vector3f ortho(Vector3f n, Vector3f d) {
        Vector3f r = new Vector3f(n).sub(new Vector3f(d).mul(n.dot(d)));
        if (r.lengthSquared() < 1e-6f) r = new Vector3f(0, 0, 1).sub(new Vector3f(d).mul(d.z));
        return r.normalize();
    }

    /** Поза в момент u ∈ [0,1]; right — правша; shield — во второй руке щит. */
    public static Out at(float u, boolean right, boolean shield, Out o) {
        Key[] ks = shield ? SHIELD : PLAIN;
        u = Math.max(0f, Math.min(1f, u));
        int i = 0;
        while (i < ks.length - 2 && u > ks[i + 1].u) i++;
        Key a = ks[i], b = ks[i + 1];
        Key pa = i > 0 ? ks[i - 1] : null, nb = i + 2 < ks.length ? ks[i + 2] : null;
        float dt = b.u - a.u;
        float x = (u - a.u) / Math.max(1e-4f, dt);
        float x2 = x * x, x3 = x2 * x;
        float h00 = 2 * x3 - 3 * x2 + 1, h10 = x3 - 2 * x2 + x, h01 = -2 * x3 + 3 * x2, h11 = x3 - x2;
        Vector3f[] res = new Vector3f[N];
        for (int c = 0; c < N; c++) {
            Vector3f ma = tangent(pa, a, b, c), mb = tangent(a, b, nb, c);
            res[c] = new Vector3f(a.v[c]).mul(h00).add(new Vector3f(ma).mul(h10 * dt))
                    .add(new Vector3f(b.v[c]).mul(h01)).add(new Vector3f(mb).mul(h11 * dt));
        }
        o.hand.set(res[0]);
        o.dir.set(res[1]).normalize();
        o.normal.set(ortho(res[2], o.dir));
        o.shoulder.set(res[3]);
        o.offHand.set(res[4]);
        o.offShoulder.set(res[5]);
        o.kneeR = Math.max(0, res[6].x);
        o.kneeL = Math.max(0, res[6].y);
        o.inHand = u >= GRAB;
        if (!right) {
            mirror(o.hand); mirror(o.dir); mirror(o.normal);
            mirror(o.shoulder); mirror(o.offHand); mirror(o.offShoulder);
            float k = o.kneeR; o.kneeR = o.kneeL; o.kneeL = k;
        }
        return o;
    }

    private static void mirror(Vector3f v) {
        v.x = -v.x;
    }

    /** Наклон кривой в ключе k (единицы в долю приёма); в начале, конце и в момент хвата — ноль. */
    private static Vector3f tangent(Key prev, Key k, Key next, int c) {
        if (prev == null || next == null || k.hold) return new Vector3f();
        return new Vector3f(next.v[c]).sub(prev.v[c]).div(Math.max(1e-4f, next.u - prev.u));
    }

    /** Ножны для левши/правши: устье, направление, плоскость (в пространстве корпуса). */
    public static Vector3f[] sheath(boolean right) {
        Vector3f m = new Vector3f(SHEATH_MOUTH), d = new Vector3f(SHEATH_DIR), n = new Vector3f(SHEATH_NORMAL);
        if (!right) { mirror(m); mirror(d); mirror(n); }
        return new Vector3f[] { m, d, n };
    }
}
