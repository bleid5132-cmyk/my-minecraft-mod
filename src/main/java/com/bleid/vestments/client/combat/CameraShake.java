package com.bleid.vestments.client.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

/** Тряска камеры: сила складывается, гаснет за ~0.35 с, частота высокая и неровная. */
@Environment(EnvType.CLIENT)
public final class CameraShake {
    private static float strength;
    private static long start = 0, last = 0;

    private CameraShake() { }

    // толчок камеры при попадании: быстрый рывок по направлению удара и возврат (градусы)
    private static float kYaw, kPitch, kRoll;
    private static long kickStart;

    public static void kick(float yaw, float pitch, float roll) {
        kYaw = yaw;
        kPitch = pitch;
        kRoll = roll;
        kickStart = System.nanoTime();
    }

    /** Форма толчка: пик через ~45 мс, затем плавный возврат. */
    private static float kickShape() {
        if (kickStart == 0) return 0f;
        float t = (System.nanoTime() - kickStart) / 1e9f;
        if (t > 0.6f) return 0f;
        float k = 22f;
        return (float) (t * k * Math.exp(1 - t * k));
    }

    /** Наклон камеры от толчка (градусы). */
    public static float kickRoll() {
        return kRoll * kickShape();
    }

    public static void add(float s) {
        if (s <= 0) return;
        strength = Math.min(1.5f, Math.max(strength * decay(), 0) + s);
        start = System.nanoTime();
        last = start;
    }

    private static float decay() {
        float t = (System.nanoTime() - start) / 1e9f;
        return (float) Math.exp(-t * 9.0);
    }

    /** Смещение yaw/pitch в градусах или null. */
    public static float[] offset() {
        float ks = kickShape();
        float[] sh = shake();
        if (ks == 0f) return sh;
        float[] o = sh == null ? new float[2] : sh;
        o[0] += kYaw * ks;
        o[1] += kPitch * ks;
        return o;
    }

    private static float[] shake() {
        if (strength <= 0.001f) return null;
        if (MinecraftClient.getInstance().options.getPerspective() == null) return null;
        float a = strength * decay();
        if (a < 0.01f) { strength = 0; return null; }
        float t = (System.nanoTime() - start) / 1e9f;
        float yaw = (float) (Math.sin(t * 61) + Math.sin(t * 37 + 1.3) * 0.6) * a * 1.6f;
        float pitch = (float) (Math.cos(t * 53) + Math.sin(t * 29 + 0.7) * 0.6) * a * 1.3f;
        return new float[] { yaw, pitch };
    }
}
