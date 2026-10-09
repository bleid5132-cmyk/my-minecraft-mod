package com.bleid.vestments.paladin.combat;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Положение клинка во времени — общее для сервера (хитбоксы) и клиента (руки, след, вид от первого лица).
 * Локальные координаты: x — вперёд, y — вправо, z — вверх (относительно взгляда игрока).
 */
public final class Blade {
    /** Покой: клинок вперёд-вверх, плашмя к бокам. */
    static final Vec3d REST_DIR = new Vec3d(0.72, 0.12, 0.68).normalize();
    static final Vec3d REST_N = new Vec3d(0, 1, 0);
    public static final double INNER = 0.42;   // от плеча до рукояти

    private Blade() { }

    public record State(Vec3d dir, Vec3d normal, double reach) { }

    static Vec3d dirAt(Strike s, double aDeg) {
        double a = Math.toRadians(aDeg), r = Math.toRadians(s.roll);
        return new Vec3d(Math.cos(a), Math.sin(a) * Math.cos(r), Math.sin(a) * Math.sin(r));
    }

    static Vec3d normalOf(Strike s) {
        double r = Math.toRadians(s.roll);
        return new Vec3d(0, -Math.sin(r), Math.cos(r));
    }

    static double windAngle(Strike s) {
        return s.a0 + (s.a0 - s.a1) * 0.07;
    }

    static double followAngle(Strike s) {
        return s.a1 + (s.a1 - s.a0) * 0.1;      // = перелёт 0.16 − возврат 0.06
    }

    static double easeOut(double x, double p) {
        x = MathHelper.clamp(x, 0, 1);
        return 1 - Math.pow(1 - x, p);
    }

    static double easeInOut(double x) {
        x = MathHelper.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    static Vec3d mix(Vec3d a, Vec3d b, double t) {
        Vec3d v = a.multiply(1 - t).add(b.multiply(t));
        double l = v.length();
        return l < 1e-6 ? b : v.multiply(1 / l);
    }

    static Vec3d ortho(Vec3d n, Vec3d d) {
        Vec3d v = n.subtract(d.multiply(n.dotProduct(d)));
        double l = v.length();
        if (l < 1e-5) {
            v = new Vec3d(0, 0, 1).subtract(d.multiply(d.z));
            l = v.length();
            if (l < 1e-5) return new Vec3d(0, 1, 0);
        }
        return v.multiply(1 / l);
    }

    /** Состояние клинка в момент t приёма c; len — длина оружия (вылет кончика от плеча). */
    public static State at(Clip c, double t, double len) {
        Vec3d prevDir = REST_DIR, prevN = REST_N;
        double prevReach = len, prevT = 0;
        for (Strike s : c.strikes) {
            double r0 = s.reach0 > 0 ? s.reach0 : len, r1 = s.reach1 > 0 ? s.reach1 : len;
            Vec3d n = normalOf(s);
            Vec3d wind = dirAt(s, windAngle(s));
            if (t < s.antic) {                       // подход: из предыдущего положения в замах
                double x = easeInOut((t - prevT) / Math.max(1e-3, s.antic - prevT));
                return done(mix(prevDir, wind, x), mix(prevN, n, x), prevReach + (r0 - prevReach) * x);
            }
            if (t < s.pre) {                         // замах: держим, лёгкая дрожь напряжения
                double x = (t - s.antic) / Math.max(1e-3, s.pre - s.antic);
                return done(dirAt(s, windAngle(s) + (s.a0 - s.a1) * 0.02 * x), n, r0);
            }
            if (t < s.contact) {                     // удар
                double x = (t - s.pre) / Math.max(1e-3, s.contact - s.pre);
                double e = s.smooth ? easeInOut(x) : s.heavy ? easeOut(x, 1.8) : easeOut(x, 2.6);
                double a = windAngle(s) + (s.a1 - windAngle(s)) * e;
                // в середине дуги рука вытягивается сильнее (клинок «хлещет»), к концу снова собирается
                double bulge = s.thrust() ? 0 : 0.06 * len * Math.sin(Math.PI * e);
                return done(dirAt(s, a), n, r0 + (r1 - r0) * e + bulge);
            }
            if (t < s.rec) {                         // довод
                double xr = (t - s.contact) / Math.max(1e-3, s.rec - s.contact);
                double x = easeOut(xr, 2);
                double rr = s.thrust() ? r1 + (len - r1) * x * 0.5 : r1;
                // довод с перелётом и лёгким возвратом (инерция клинка), заканчивается в followAngle
                double k1 = easeOut(Math.min(1, xr / 0.55), 2.2);
                double k2 = easeInOut((xr - 0.55) / 0.45);
                double over = (s.a1 - s.a0) * (0.16 * k1 - 0.06 * k2);
                return done(dirAt(s, s.a1 + over), n, rr);
            }
            prevDir = dirAt(s, followAngle(s));
            prevN = n;
            prevReach = s.thrust() ? (r1 + len) * 0.5 : r1;
            prevT = s.rec;
        }
        double x = easeInOut((t - prevT) / Math.max(1e-3, c.length - prevT));
        return done(mix(prevDir, REST_DIR, x), mix(prevN, REST_N, x), prevReach + (len - prevReach) * x);
    }

    private static State done(Vec3d d, Vec3d n, double reach) {
        return new State(d, ortho(n, d), reach);
    }

    // ---------------------------------------------------------------- мир

    /** Базис взгляда (вперёд, вправо, вверх) по повороту и наклону; наклон смягчён. */
    public static Vec3d[] frame(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double p = Math.toRadians(MathHelper.clamp(pitchDeg * 0.5f, -25f, 25f));
        Vec3d f = new Vec3d(-Math.sin(yaw) * Math.cos(p), -Math.sin(p), Math.cos(yaw) * Math.cos(p));
        Vec3d r = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3d u = r.crossProduct(f);
        return new Vec3d[] { f, r, u };
    }

    public static Vec3d toWorld(Vec3d local, Vec3d[] fr) {
        return fr[0].multiply(local.x).add(fr[1].multiply(local.y)).add(fr[2].multiply(local.z));
    }

    /** Плечо, от которого считается дуга: чуть правее центра груди. */
    public static Vec3d shoulder(Vec3d feet, Vec3d[] fr, double height) {
        return feet.add(0, height * 0.7, 0).add(fr[1].multiply(0.22));
    }
}
