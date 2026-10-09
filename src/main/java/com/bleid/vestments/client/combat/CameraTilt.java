package com.bleid.vestments.client.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Лёгкий наклон камеры в сторону бега: при движении вбок камера кренится туда же,
 * на поворотах во время бега — «закладывает вираж». Плавно, по реальному времени.
 */
@Environment(EnvType.CLIENT)
public final class CameraTilt {
    private static float roll;
    private static long last;
    private static float prevYaw;

    private CameraTilt() { }

    /** Текущий крен камеры (градусы, + — вправо). */
    public static float roll(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        long now = System.nanoTime();
        float dt = last == 0 ? 0f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        float target = 0f;
        if (p != null && !p.isSpectator() && !p.isFallFlying() && !p.getAbilities().flying && !p.hasVehicle()) {
            Vec3d v = p.getVelocity();
            float yaw = p.getYaw(tickDelta);
            double yr = Math.toRadians(yaw);
            double lateral = v.x * -Math.cos(yr) + v.z * -Math.sin(yr);
            double speed = Math.sqrt(v.x * v.x + v.z * v.z);
            target = (float) MathHelper.clamp(lateral / 0.26, -1, 1) * 2.4f;
            // вираж: поворот головы во время бега
            float dYaw = dt > 0 ? MathHelper.wrapDegrees(yaw - prevYaw) / dt : 0f;
            target += MathHelper.clamp(dYaw * 0.004f, -1f, 1f) * (float) MathHelper.clamp(speed / 0.2, 0, 1) * 1.5f;
            prevYaw = yaw;
        }
        float k = 1f - (float) Math.exp(-7f * dt);
        roll += (target - roll) * k;
        return roll + CameraShake.kickRoll();
    }
}
