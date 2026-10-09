package com.bleid.vestments.paladin.combat;

/**
 * Один взмах (по образцу Epic Fight): фазы antic (замах) → pre (начало удара, хитбокс включается)
 * → contact (хитбокс выключается) → rec (с этого момента можно следующий удар).
 *
 * Траектория клинка — дуга в плоскости, наклонённой на roll градусов вокруг направления взгляда:
 * roll 0 — горизонтальный взмах, 90 — вертикальный (сверху вниз), 45 — диагональ.
 * Угол a: 0 — вперёд, +90 — вправо (при roll 0) или вверх (при roll 90). Клинок идёт от a0 к a1.
 * Если a0 == a1 — это укол: меняется вылет клинка reach0 → reach1.
 */
public final class Strike {
    public final float antic, pre, contact, rec;
    public final float roll, a0, a1;
    public float reach0 = -1, reach1 = -1;   // -1 — длина оружия
    public float width = 0.45f;
    public float dmg = 1f, kb = 0.4f;
    public int maxTargets = 3;
    public int hitstop = 0;
    public float shake = 0f;
    public String event;                     // особое действие в момент contact
    public boolean heavy;                    // тяжёлый удар — разгон равномернее
    public boolean smooth;                   // плавное движение (разгон и торможение), для показных движений

    public Strike(float antic, float pre, float contact, float rec, float roll, float a0, float a1) {
        this.antic = antic;
        this.pre = pre;
        this.contact = contact;
        this.rec = rec;
        this.roll = roll;
        this.a0 = a0;
        this.a1 = a1;
    }

    public Strike reach(float r0, float r1) { reach0 = r0; reach1 = r1; return this; }
    public Strike dmg(float d) { dmg = d; return this; }
    public Strike kb(float k) { kb = k; return this; }
    public Strike width(float w) { width = w; return this; }
    public Strike targets(int n) { maxTargets = n; return this; }
    public Strike hitstop(int t) { hitstop = t; return this; }
    public Strike shake(float s) { shake = s; return this; }
    public Strike event(String e) { event = e; return this; }
    public Strike heavy() { heavy = true; return this; }
    public Strike smooth() { smooth = true; return this; }

    public boolean thrust() {
        return a0 == a1;
    }
}
