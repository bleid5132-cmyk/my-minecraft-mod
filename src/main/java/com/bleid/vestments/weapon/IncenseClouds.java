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

/** Молитвенный покров — золотое сияние, которое идёт за священником. */
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
        // богатый вариант (архиерейский посох) — с рунным кругом
        com.bleid.vestments.fx.Fx.play(player.getServerWorld(), "veil", player.getPos(), player, a.seconds() * 20,
                (float) a.radius(), a.weakness() ? 1f : 0f, 0f);
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
        if (c.age % 20 == 10) {
            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5f, 1.8f);
        }
        if (c.age % 20 != 0) return;

        Box box = new Box(center, center).expand(r, 2.5, r);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
            if (e.squaredDistanceTo(center) > r * r) continue;
            if (e == p || !StaffOfLightItem.canHit(e)) {          // свои, мирные, нейтральные — лечим
                if (e.getHealth() < e.getMaxHealth()) {
                    e.heal(c.a.healPerSec() * c.power);
                    com.bleid.vestments.fx.Fx.play(world, "heal_small", e, 0, 0);
                }
            } else {                                             // враждебные — дым мешает
                if (c.a.slowAmp() >= 0) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, c.a.slowAmp(), true, true));
                    if (c.a.weakness()) e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, true, true));
                }
                if (c.a.undeadDps() > 0 && e.getGroup() == EntityGroup.UNDEAD) {
                    e.damage(world.getDamageSources().indirectMagic(p, p), c.a.undeadDps() * c.power);
                    com.bleid.vestments.fx.Fx.play(world, "smite_small", e, 0, 0);
                }
            }
        }
    }
}
