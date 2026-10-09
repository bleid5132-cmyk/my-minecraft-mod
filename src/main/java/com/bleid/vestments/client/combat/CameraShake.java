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
