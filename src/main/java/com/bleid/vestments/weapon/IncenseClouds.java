package com.bleid.vestments.weapon;

import com.bleid.vestments.StaffOfLightItem;
import com.bleid.vestments.Vestments;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/** Облака ладана, которые идут за священником. */
public final class IncenseClouds {
    private static final class Cloud {
        final UUID owner;
        final IncenseAbility a;
        final float power;
        int age;

        Cloud(UUID owner, IncenseAbility a, float power) {
            this.owner = owner;
            this.a = a;
            this.power = power;
        }
    }

    private static final List<Cloud> CLOUDS = new ArrayList<>();

    private IncenseClouds() { }

    public static void start(ServerPlayerEntity player, IncenseAbility a, float power) {
        CLOUDS.removeIf(c -> c.owner.equals(player.getUuid()));
        CLOUDS.add(new Cloud(player.getUuid(), a, power));
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Iterator<Cloud> it = CLOUDS.iterator(); it.hasNext(); ) {
                Cloud c = it.next();
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(c.owner);
                if (p == null || !p.isAlive() || c.age++ >= c.a.seconds() * 20) {
                    it.remove();
                    continue;
                }
                tick(p.getServerWorld(), p, c);
            }
        });
    }

    private static void tick(ServerWorld world, ServerPlayerEntity p, Cloud c) {
        Vec3d center = p.getPos();
        double r = c.a.radius();
        Random rnd = world.getRandom();
        // дым ладана: медленные клубы по кругу и золотые искры
        if (c.age % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2, dist = Math.sqrt(rnd.nextDouble()) * r;
                double x = center.x + Math.cos(ang) * dist, z = center.z + Math.sin(ang) * dist;
                world.spawnParticles(ParticleTypes.CLOUD, x, center.y + 0.2 + rnd.nextDouble() * 1.4, z,
                        0, (rnd.nextDouble() - 0.5) * 0.02, 0.015, (rnd.nextDouble() - 0.5) * 0.02, 1.0);
            }
            world.spawnParticles(Vestments.SERVICE_ORB, center.x, center.y + 1.0, center.z, 2, r * 0.4, 0.6, r * 0.4, 0.0);
        }
        if (c.age % 20 == 10) {
            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.PLAYERS, 0.6f, 1.4f);
        }
        if (c.age % 20 != 0) return;

        Box box = new Box(center, center).expand(r, 2.5, r);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
            if (e.squaredDistanceTo(center) > r * r) continue;
            if (e == p || !StaffOfLightItem.canHit(e)) {          // свои, мирные, нейтральные — лечим
                if (e.getHealth() < e.getMaxHealth()) {
                    e.heal(c.a.healPerSec() * c.power);
                    world.spawnParticles(ParticleTypes.HEART, e.getX(), e.getBodyY(1.0) + 0.3, e.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
                }
            } else {                                             // враждебные — дым мешает
                if (c.a.slowAmp() >= 0) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, c.a.slowAmp(), true, true));
                    if (c.a.weakness()) e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, true, true));
                }
                if (c.a.undeadDps() > 0 && e.getGroup() == EntityGroup.UNDEAD) {
                    e.damage(world.getDamageSources().indirectMagic(p, p), c.a.undeadDps() * c.power);
                    world.spawnParticles(ParticleTypes.SMALL_FLAME, e.getX(), e.getBodyY(0.6), e.getZ(), 4, 0.25, 0.4, 0.25, 0.01);
                }
            }
        }
    }
}
