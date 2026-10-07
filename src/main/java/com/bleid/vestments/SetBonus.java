package com.bleid.vestments;

import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import com.bleid.vestments.gear.GearSet;
import com.bleid.vestments.gear.RankGear;
import com.bleid.vestments.service.RankView;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Способности облачения санов. Работают, только когда надеты все 4 части одного сана и сан достигнут:
 * шлем — Регенерация I (у Патриарха постоянно, у остальных — короткими вспышками);
 * грудь — лечение игроков рядом; поножи — скорость на песке душ;
 * ботинки — хождение по воде (само движение делает клиент, см. client/WaterWalkClient;
 * здесь — отмена урона от падения на воду). Числа — в gear/RankGear.
 */
public final class SetBonus {
    private static final UUID SOUL_SAND_SPEED_ID = UUID.fromString("6b1f5c2e-6a54-4c39-9d4e-0f7d2b1a9c02");

    private SetBonus() { }

    /** Действующий комплект: полный и сан достигнут; иначе null. */
    public static GearSet activeSet(LivingEntity entity) {
        GearSet set = RankGear.fullSet(entity);
        if (set == null || !(entity instanceof PlayerEntity p) || !RankView.has(p, set.rank)) return null;
        return set;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tick(player);
            }
        });

        // хождение по воде: приземление на воду, по которой идёшь, не наносит урона от падения
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof PlayerEntity) || !source.isOf(DamageTypes.FALL)) return true;
            GearSet set = activeSet(entity);
            if (set == null || set.waterWalkSec <= 0) return true;
            return !overWater(entity);
        });
    }

    private static void tick(ServerPlayerEntity player) {
        GearSet set = player.isAlive() ? activeSet(player) : null;

        // скорость на песке душ
        double bonus = set != null && onSoulSand(player) ? set.soulSand : 0.0;
        setModifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SOUL_SAND_SPEED_ID,
                "Vestments soul sand speed", bonus, EntityAttributeModifier.Operation.MULTIPLY_TOTAL, bonus > 0);

        if (set == null) return;

        // Регенерация I
        if (set.regenEvery < 0) {
            StatusEffectInstance regen = player.getStatusEffect(StatusEffects.REGENERATION);
            if (regen == null || (regen.getAmplifier() == 0 && regen.getDuration() <= 40)) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 220, 0, true, false, true));
            }
        } else if (set.regenEvery > 0 && player.age % set.regenEvery == 0) {
            StatusEffectInstance regen = player.getStatusEffect(StatusEffects.REGENERATION);
            if (regen == null || regen.getDuration() < set.regenFor) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, set.regenFor, 0, true, false, true));
            }
        }

        // лечение игроков рядом (и себя)
        if (set.auraInterval > 0 && player.age % set.auraInterval == 0) {
            ServerWorld world = player.getServerWorld();
            double r = set.auraRadius;
            for (PlayerEntity other : world.getPlayers()) {
                if (!other.isAlive() || other.isSpectator()) continue;
                if (other.squaredDistanceTo(player) > r * r) continue;
                if (other.getHealth() >= other.getMaxHealth()) continue;
                other.heal(set.auraHeal);
                world.spawnParticles(ParticleTypes.HEART, other.getX(), other.getY() + other.getHeight() + 0.3,
                        other.getZ(), 2, 0.3, 0.1, 0.3, 0.0);
            }
        }
    }

    /** Под ногами (или на уровне ног) вода. */
    private static boolean overWater(LivingEntity entity) {
        BlockPos pos = entity.getBlockPos();
        return entity.getWorld().getFluidState(pos).isIn(net.minecraft.registry.tag.FluidTags.WATER)
                || entity.getWorld().getFluidState(pos.down()).isIn(net.minecraft.registry.tag.FluidTags.WATER);
    }

    private static boolean onSoulSand(PlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        return player.getWorld().getBlockState(pos).isOf(Blocks.SOUL_SAND)
                || player.getWorld().getBlockState(pos.down()).isOf(Blocks.SOUL_SAND);
    }

    private static void setModifier(PlayerEntity player, EntityAttribute attribute, UUID id, String name,
                                    double value, EntityAttributeModifier.Operation operation, boolean active) {
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance == null) return;
        EntityAttributeModifier present = instance.getModifier(id);
        if (present != null && (!active || present.getValue() != value)) {
            instance.removeModifier(id);
            present = null;
        }
        if (active && present == null) {
            instance.addTemporaryModifier(new EntityAttributeModifier(id, name, value, operation));
        }
    }
}
