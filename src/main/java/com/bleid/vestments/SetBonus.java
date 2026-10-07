package com.bleid.vestments;

import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Способности облачения. Работают, только когда надеты все 4 предмета:
 * воротник — Регенерация I; мантия — сопротивление отбрасыванию и лечение игроков рядом;
 * поножи — +50% скорости на песке душ; сандалии — плавное падение (вдвое меньше урона от падения,
 * само замедление падения делает клиент, см. client/SlowFallClient).
 */
public final class SetBonus {
    private static final UUID KNOCKBACK_ID = UUID.fromString("6b1f5c2e-6a54-4c39-9d4e-0f7d2b1a9c01");
    private static final UUID SOUL_SAND_SPEED_ID = UUID.fromString("6b1f5c2e-6a54-4c39-9d4e-0f7d2b1a9c02");

    private static final double KNOCKBACK_RESISTANCE = 0.6;   // 60%
    private static final double SOUL_SAND_SPEED = 0.5;        // +50%
    private static final int HEAL_INTERVAL = 60;               // 3 секунды
    private static final float HEAL_AMOUNT = 1.0f;             // полсердечка
    private static final double HEAL_RADIUS = 3.0;
    private static final float FALL_DAMAGE_MULTIPLIER = 0.5f;

    private static boolean reapplyingFallDamage = false;

    private SetBonus() { }

    public static boolean hasFullSet(LivingEntity entity) {
        return isWearing(entity, EquipmentSlot.HEAD, Vestments.COLLAR)
                && isWearing(entity, EquipmentSlot.CHEST, Vestments.PHELONION)
                && isWearing(entity, EquipmentSlot.LEGS, Vestments.PODRIZNIK)
                && isWearing(entity, EquipmentSlot.FEET, Vestments.BOOTS);
    }

    private static boolean isWearing(LivingEntity entity, EquipmentSlot slot, Item item) {
        return entity.getEquippedStack(slot).isOf(item);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tick(player);
            }
        });

        // Сандалии паломника: урон от падения вдвое меньше
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (reapplyingFallDamage || !(entity instanceof PlayerEntity) || !source.isOf(DamageTypes.FALL)
                    || !hasFullSet(entity)) {
                return true;
            }
            float reduced = amount * FALL_DAMAGE_MULTIPLIER;
            if (reduced >= 0.5f) {
                reapplyingFallDamage = true;
                try {
                    entity.damage(source, reduced);
                } finally {
                    reapplyingFallDamage = false;
                }
            }
            return false; // исходный (полный) урон отменяем
        });
    }

    private static void tick(ServerPlayerEntity player) {
        boolean full = hasFullSet(player) && player.isAlive();

        // Мантия: сопротивление отбрасыванию
        setModifier(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK_ID,
                "Vestments knockback resistance", KNOCKBACK_RESISTANCE,
                EntityAttributeModifier.Operation.ADDITION, full);

        // Поножи: +50% скорости на песке душ
        setModifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SOUL_SAND_SPEED_ID,
                "Vestments soul sand speed", SOUL_SAND_SPEED,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL, full && onSoulSand(player));

        if (!full) return;

        // Воротник: Регенерация I. Продлеваем заранее, чтобы таймер эффекта шёл и лечение срабатывало.
        StatusEffectInstance regen = player.getStatusEffect(StatusEffects.REGENERATION);
        if (regen == null || (regen.getAmplifier() == 0 && regen.getDuration() <= 40)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 220, 0, true, false, true));
        }

        // Мантия: раз в 3 секунды лечит игроков рядом на полсердечка
        if (player.age % HEAL_INTERVAL == 0) {
            ServerWorld world = player.getServerWorld();
            for (PlayerEntity other : world.getPlayers()) {
                if (other == player || !other.isAlive() || other.isSpectator()) continue;
                if (other.squaredDistanceTo(player) > HEAL_RADIUS * HEAL_RADIUS) continue;
                if (other.getHealth() >= other.getMaxHealth()) continue;
                other.heal(HEAL_AMOUNT);
                world.spawnParticles(ParticleTypes.HEART, other.getX(), other.getY() + other.getHeight() + 0.3,
                        other.getZ(), 2, 0.3, 0.1, 0.3, 0.0);
            }
        }
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
        boolean present = instance.getModifier(id) != null;
        if (active && !present) {
            instance.addTemporaryModifier(new EntityAttributeModifier(id, name, value, operation));
        } else if (!active && present) {
            instance.removeModifier(id);
        }
    }
}
