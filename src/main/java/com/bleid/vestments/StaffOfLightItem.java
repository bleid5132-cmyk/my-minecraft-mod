package com.bleid.vestments;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.List;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Посох Света: бьёт врагов на 3 сердца, а игроков и мирных мобов вместо урона лечит на 3 сердца.
 * Лечение, как и урон, зависит от перезарядки удара — спамом не вылечить.
 */
public class StaffOfLightItem extends Item {
    private static final double ATTACK_DAMAGE = 6.0;     // 3 сердца (вместе с 1 базовым у игрока)
    private static final double ATTACK_SPEED = 1.3;      // ударов в секунду
    private static final float HEAL_AMOUNT = 6.0f;       // 3 сердца

    private final Multimap<EntityAttribute, EntityAttributeModifier> modifiers;

    public StaffOfLightItem(Settings settings) {
        super(settings);
        this.modifiers = ImmutableMultimap.<EntityAttribute, EntityAttributeModifier>builder()
                .put(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(ATTACK_DAMAGE_MODIFIER_ID,
                        "Staff of Light damage", ATTACK_DAMAGE - 1.0, EntityAttributeModifier.Operation.ADDITION))
                .put(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(ATTACK_SPEED_MODIFIER_ID,
                        "Staff of Light speed", ATTACK_SPEED - 4.0, EntityAttributeModifier.Operation.ADDITION))
                .build();
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getAttributeModifiers(slot);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.damage(1, attacker, e -> e.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public int getEnchantability() {
        return 15;
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return ingredient.isOf(net.minecraft.item.Items.GOLD_INGOT) || super.canRepair(stack, ingredient);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.vestments.staff_of_light.damage").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.vestments.staff_of_light.heal").formatted(Formatting.YELLOW));
    }

    /** Кого посох лечит, а не ранит: игроков и мирных существ. */
    public static boolean isHealTarget(Entity entity) {
        return entity instanceof PlayerEntity
                || entity instanceof PassiveEntity       // животные, жители, торговцы
                || entity instanceof WaterCreatureEntity // рыбы, кальмары, дельфины
                || entity instanceof AmbientEntity;      // летучие мыши
    }

    public static void registerEvents() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(Vestments.STAFF_OF_LIGHT) || !isHealTarget(entity) || player.isSpectator()) {
                return ActionResult.PASS;
            }
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
                return ActionResult.PASS;
            }
            // удар не наносит урона: на клиенте отменяем атаку и шлём её на сервер, на сервере лечим
            float charge = player.getAttackCooldownProgress(0.5f);
            player.resetLastAttackedTicks();
            if (!world.isClient && world instanceof ServerWorld server) {
                float heal = HEAL_AMOUNT * (0.2f + 0.8f * charge * charge);
                target.heal(heal);
                server.spawnParticles(ParticleTypes.HEART, target.getX(), target.getBodyY(0.9), target.getZ(),
                        3, 0.35, 0.25, 0.35, 0.0);
                server.spawnParticles(ParticleTypes.END_ROD, target.getX(), target.getBodyY(0.5), target.getZ(),
                        8, 0.3, 0.5, 0.3, 0.03);
                world.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.4f);
                if (!player.getAbilities().creativeMode) {
                    stack.damage(1, player, p -> p.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND));
                }
            }
            return ActionResult.SUCCESS;
        });
    }
}
