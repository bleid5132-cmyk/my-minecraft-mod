package com.bleid.vestments.paladin.combat;

import java.util.ArrayList;
import java.util.List;

/**
 * Боевой приём: один или несколько ударов (Strike) и «пластика» тела — наклон, вращение корпуса,
 * подскок, стойка ног, шаги вперёд. Время в секундах от начала приёма.
 */
public final class Clip {
    public final String id;
    public final float length;
    public final float transition;
    public final List<Strike> strikes = new ArrayList<>();
    public final Track lean = new Track();     // наклон вперёд, градусы
    public final Track spin = new Track();     // поворот всего тела вправо, градусы
    public final Track lift = new Track();     // подъём тела (визуально), блоки
    public final Track stance = new Track();   // стойка ног: 1 — выпад правой вперёд, -1 — левой
    public final List<float[]> steps = new ArrayList<>();   // {t0, t1, блоков вперёд}
    public float leapAt = -1, leapVy, slamAt = -1, slamVy;   // прыжок и падение (свой игрок)
    public boolean skill;
    public boolean lockMove = true;
    /** Только визуальный приём (доставание меча/щита): без сервера, не мешает ходить и бить. */
    public boolean visualOnly;
    /** Двигается только вторая рука (щит), рука с мечом и ноги не трогаются. */
    public boolean offOnly;
    /** Выхватывание из ножен: руки по обратной кинематике (DrawPose), меч появляется в руке при хвате. */
    public boolean sheath;
    // своя дорожка для второй руки (радианы модели); пустые — обычная защитная стойка
    public final Track offPitch = new Track(), offYaw = new Track(), offRoll = new Track();

    public Clip(String id, float length, float transition) {
        this.id = id;
        this.length = length;
        this.transition = transition;
    }

    public Clip strike(Strike s) {
        strikes.add(s);
        return this;
    }

    public Clip step(float t0, float t1, float dist) {
        steps.add(new float[] { t0, t1, dist });
        return this;
    }

    public Clip leap(float at, float vy, float slamAt, float slamVy) {
        this.leapAt = at; this.leapVy = vy; this.slamAt = slamAt; this.slamVy = slamVy;
        return this;
    }

    /** С этого момента можно начать следующий удар серии. */
    public float cancelTime() {
        if (visualOnly) return 0f;
        if (skill) return length;
        float r = 0;
        for (Strike s : strikes) r = Math.max(r, s.rec);
        return Math.min(r, length);
    }

    /** До какого момента ноги «заняты» (движение замедлено). */
    public float moveLockUntil() {
        return lockMove && !visualOnly ? cancelTime() : 0;
    }

    /** Блендинг с обычной позой: плавный вход и выход. */
    public float weight(float t) {
        float in = transition <= 0 ? 1f : Math.min(1f, t / transition);
        float out = Math.min(1f, Math.max(0f, (length - t) / 0.16f));
        return smooth(Math.min(in, out));
    }

    public static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3 - 2 * x);
    }

    /** Ключевые значения с плавной (косинусной) интерполяцией. */
    public static final class Track {
        private final List<float[]> keys = new ArrayList<>();

        public Track key(float t, float v) {
            keys.add(new float[] { t, v });
            keys.sort((a, b) -> Float.compare(a[0], b[0]));
            return this;
        }

        public boolean isEmpty() {
            return keys.isEmpty();
        }

        public float at(float t) {
            if (keys.isEmpty()) return 0f;
            if (t <= keys.get(0)[0]) return keys.get(0)[1];
            for (int i = 0; i < keys.size() - 1; i++) {
                float[] a = keys.get(i), b = keys.get(i + 1);
                if (t <= b[0]) {
                    float x = (t - a[0]) / Math.max(1e-4f, b[0] - a[0]);
                    return a[1] + (b[1] - a[1]) * smooth(x);
                }
            }
            return keys.get(keys.size() - 1)[1];
        }
    }
}
