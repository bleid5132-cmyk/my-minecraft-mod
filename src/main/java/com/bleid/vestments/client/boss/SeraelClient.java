package com.bleid.vestments.client.boss;

import com.bleid.vestments.boss.SeraelEntity;
import com.bleid.vestments.client.fx.FxSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/** Клиентские эффекты Сераэль: вихрь пепла и алых лепестков, щит тёмной энергии, пылающие крылья в полёте. */
@Environment(EnvType.CLIENT)
public final class SeraelClient {
    private SeraelClient() { }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ClientWorld w = mc.world;
            if (w == null || mc.player == null || mc.isPaused()) return;
            for (Entity en : w.getEntities()) {
                if (!(en instanceof SeraelEntity e) || e.squaredDistanceTo(mc.player) > 80 * 80) continue;
                tick(w, e);
            }
        });
    }

    private static void tick(ClientWorld w, SeraelEntity e) {
        Random R = w.random;
        double cx = e.getX(), cy = e.getY(), cz = e.getZ();
        boolean dead = e.deathTime > 0;
        // вихрь лепестков и пепла
        for (int i = 0; i < (dead ? 1 : 3); i++) {
            double ang = R.nextDouble() * Math.PI * 2, r = 2.4 + R.nextDouble() * 2.4, y = cy + 0.3 + R.nextDouble() * 5.5;
            double x = cx + Math.cos(ang) * r, z = cz + Math.sin(ang) * r;
            double tx = -Math.sin(ang) * 0.09, tz = Math.cos(ang) * 0.09;
            if (i == 0) {
                FxSystem.spawn(FxSystem.FEATHER, x, y, z).vel(tx, 0.01 + R.nextDouble() * 0.02, tz).gravity(0.0004f).drag(0.97f)
                        .rot((float) (R.nextDouble() * 6.28), 0.12f).size(0.1f, 0.06f)
                        .color(0.78f + R.nextFloat() * 0.15f, 0.04f, 0.08f).life(36 + R.nextInt(20)).fade(0.15f, 0.4f);
            } else {
                w.addParticle(R.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH, x, y, z, tx, 0.02, tz);
            }
        }
        // щит «Демонического барьера»
        float ch = e.shieldCharge();
        if (ch >= 0) {
            int pts = 16;
            for (int i = 0; i < pts; i++) {
                double u = R.nextDouble() * 2 - 1, th = R.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u), r = 3.3;
                double x = cx + s * Math.cos(th) * r, y = cy + 2.8 + u * r * 1.05, z = cz + s * Math.sin(th) * r;
                w.addParticle(R.nextFloat() < ch ? SeraelEntity.VOIDP : SeraelEntity.DARK, x, y, z, 0, 0, 0);
            }
            if (R.nextFloat() < 0.3f + ch) {
                w.addParticle(ParticleTypes.REVERSE_PORTAL, cx + R.nextGaussian() * 1.5, cy + 2 + R.nextDouble() * 3, cz + R.nextGaussian() * 1.5, 0, 0.05, 0);
            }
        }
        // пылающие крылья при взлёте и пике
        if (e.attack() == SeraelEntity.DIVE && e.age - e.clientAttackStart < 64) {
            float yaw = e.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
            double lx = Math.cos(yaw), lz = Math.sin(yaw), fx = -Math.sin(yaw), fz = Math.cos(yaw);
            for (int i = 0; i < 8; i++) {
                double side = R.nextBoolean() ? 1 : -1, span = 0.9 + R.nextDouble() * 2.8, up = 3.8 + R.nextDouble() * 3.0, back = -1.0 - R.nextDouble();
                double x = cx + lx * side * span + fx * back, z = cz + lz * side * span + fz * back;
                w.addParticle(i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, x, cy + up, z, 0, 0.02, 0);
            }
        }
        // светящиеся глаза и нимб — редкие золотые искры
        if (!dead && R.nextInt(4) == 0) {
            w.addParticle(SeraelEntity.GOLD, cx + R.nextGaussian() * 0.4, cy + 6.3, cz + R.nextGaussian() * 0.4, 0, 0, 0);
        }
    }
}
