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
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Посох Света: бьёт враждебных мобов на 3 сердца. По игрокам, мирным и нейтральным мобам удар не проходит.
 */
public class StaffOfLightItem extends Item {
    private static final double ATTACK_DAMAGE = 6.0;     // 3 сердца (вместе с 1 базовым у игрока)
    private static final double ATTACK_SPEED = 1.3;      // ударов в секунду

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
        tooltip.add(Text.translatable("tooltip.vestments.staff_of_light.peace").formatted(Formatting.YELLOW));
    }

    /**
     * Кого посох может бить: только враждебных мобов. Игроки, мирные (животные, жители, рыбы,
     * летучие мыши), нейтральные (волки, эндермены, пчёлы, зомби-пиглины...) и големы — нельзя.
     */
    public static boolean canHit(Entity entity) {
        if (!(entity instanceof LivingEntity)) return true;          // кристаллы края и прочие не-существа
        if (entity instanceof PlayerEntity) return false;
        if (entity instanceof Angerable) return false;                // нейтральные
        return entity instanceof Monster;                             // враждебные
    }

    public static void registerEvents() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!player.getStackInHand(hand).isOf(Vestments.STAFF_OF_LIGHT) || player.isSpectator() || canHit(entity)) {
                return ActionResult.PASS;
            }
            // удар по игроку, мирному или нейтральному мобу не проходит
            if (world.isClient) {
                player.sendMessage(Text.translatable("message.vestments.staff_refuses").formatted(Formatting.YELLOW), true);
            }
            return ActionResult.FAIL;
        });
    }
}
