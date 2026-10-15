package com.bleid.vestments.client.horse;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Посадка всадника на лошади мода: ноги охватывают бочку, колени согнуты, руки держат повод у холки.
 * Поза меняется по аллюру лошади: на шаге — спокойная посадка с покачиванием, на рыси — облегчение
 * (всадник привстаёт в такт), на кентере — посыл корпусом, на галопе — полевая посадка в стременах.
 */
@Environment(EnvType.CLIENT)
public final class RiderPose {
    private RiderPose() { }

    public static final class Pose {
        public float legPitch, legRoll, legOut, knee, armPitch, armRoll, elbow, chest, rise, sway;
    }

    public static HorseEntity horse(Entity e) {
        return e != null && e.getVehicle() instanceof HorseEntity h && !h.isBaby() ? h : null;
    }

    private static float sm(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    /** Поза для всадника этой лошади (null — сидит не на нашей лошади или она ещё не нарисована). */
    public static Pose compute(LivingEntity rider) {
        HorseEntity h = horse(rider);
        if (h == null) return null;
        RealHorseModel.State s = RealHorseModel.state(h);
        if (s == null) return null;
        float m = s.move * (1f - s.swim);
        float g = s.gait;                                               // 0 шаг · 1 рысь · 2 кентер · 3 галоп
        float wTrot = m * Math.max(0f, 1f - Math.abs(g - 1f));
        float wCanter = m * Math.max(0f, 1f - Math.abs(g - 2f));
        float wGallop = m * sm(g - 2f);
        float wWalk = m * Math.max(0f, 1f - g);
        float ph = (float) (s.phase - Math.floor(s.phase)) * MathHelper.TAU;
        Pose p = new Pose();
        // облегчение на рыси: привстать на один диагональ, сесть на другой
        float post = Math.max(0f, MathHelper.sin(ph));
        p.rise = wTrot * post * 0.085f + wGallop * 0.05f;
        p.chest = 0.04f + wWalk * 0.03f + wTrot * (0.12f + post * 0.05f) + wCanter * (0.2f + 0.06f * MathHelper.sin(ph + 0.6f))
                + wGallop * 0.42f + s.rear * 0.35f + s.swim * 0.15f;
        // бёдра разведены шире бочки и седельных крыльев, голень висит вдоль бока
        p.legOut = 4.0f;
        p.legPitch = -0.55f - wGallop * 0.25f + wWalk * 0.05f * MathHelper.sin(ph * 2f) - s.swim * 0.3f;
        p.legRoll = 0.32f + 0.03f * wCanter * MathHelper.sin(ph);
        p.knee = 1.0f + wGallop * 0.4f - wTrot * post * 0.4f + s.swim * 0.2f;
        // руки идут за головой лошади: на кентере и галопе — заметный посыл
        p.armPitch = -0.45f - p.chest * 0.4f + (wCanter * 0.12f + wGallop * 0.16f) * MathHelper.sin(ph - 0.5f) - s.rear * 0.4f;
        p.armRoll = 0.12f;
        p.elbow = 0.7f - wGallop * 0.2f;
        p.sway = wWalk * 2.2f * MathHelper.sin(ph) + wCanter * 1.5f * MathHelper.sin(ph + 1f);
        return p;
    }
}
