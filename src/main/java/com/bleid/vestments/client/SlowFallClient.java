package com.bleid.vestments.client;

import com.bleid.vestments.SetBonus;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Сандалии паломника: лёгкое плавное падение. Движение игрока считает клиент, поэтому скорость
 * падения ограничиваем здесь. Падение остаётся быстрым, но не разгоняется сильно.
 */
@Environment(EnvType.CLIENT)
public final class SlowFallClient {
    /** Предельная скорость падения, блоков за тик (обычно до ~3.9). */
    private static final double MAX_FALL_SPEED = 0.45;
    /** Насколько мягко подтягиваемся к пределу (0..1). */
    private static final double EASE = 0.35;

    private SlowFallClient() { }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null || !SetBonus.hasFullSet(player)) return;
            if (player.isOnGround() || player.getAbilities().flying || player.isFallFlying()
                    || player.isTouchingWater() || player.isInLava() || player.isClimbing()) return;
            Vec3d v = player.getVelocity();
            if (v.y < -MAX_FALL_SPEED) {
                double y = v.y + (-MAX_FALL_SPEED - v.y) * EASE;   // плавно, без рывка
                player.setVelocity(v.x, y, v.z);
            }
        });
    }
}
