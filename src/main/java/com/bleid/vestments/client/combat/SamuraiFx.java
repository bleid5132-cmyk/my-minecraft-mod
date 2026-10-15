package com.bleid.vestments.client.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

/** Клиентские эффекты приёмов катаны (по событиям fx из moves.json): послеобразы рывка, ветер, тряска камеры. */
@Environment(EnvType.CLIENT)
public final class SamuraiFx {
    private SamuraiFx() { }

    static void tick(MinecraftClient mc, LivingEntity le, CombatClient.Playback p) {
        for (Object[] f : p.clip.fx) {
            float t = (Float) f[0];
            if (!(p.prevT < t && p.t >= t)) continue;
            String type = (String) f[1];
            boolean me = le == mc.player;
            Vec3d c = le.getPos().add(0, 1.0, 0);
            switch (type) {
                case "afterimage", "wind" -> {
                    for (int i = 0; i < 8; i++) {
                        mc.world.addParticle(ParticleTypes.CLOUD, c.x + (Math.random() - 0.5) * 0.5, c.y + (Math.random() - 0.5) * 1.4,
                                c.z + (Math.random() - 0.5) * 0.5, 0, 0.01, 0);
                    }
                }
                case "flash", "spark" -> { if (me) CameraShake.add(0.15f); }
                case "detonate", "big_wave", "lightning" -> { if (me) CameraShake.add(0.6f); }
                case "rocks", "fire_cone" -> { if (me) CameraShake.add(0.35f); }
                default -> { }
            }
        }
    }
}
