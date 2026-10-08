package com.bleid.vestments.weapon;

import com.bleid.vestments.SmallTooltipData;
import com.bleid.vestments.service.RankView;
import com.bleid.vestments.service.Ranks;
import com.bleid.vestments.service.ServicePoints;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.item.TooltipData;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Богослужебное оружие сана: бьёт только враждебных мобов, на ПКМ — способность сана
 * (нужен достигнутый сан), с перезарядкой. В руке — 3D-модель (item/<id>_3d), в инвентаре — иконка.
 */
public class LiturgicalWeaponItem extends Item {
    public final int rank;
    public final WeaponAbility ability;
    public final int cooldownTicks;
    /** Сколько маны тратит способность. */
    public final int manaCost;
    public final Identifier hudIcon;
    private final double damage, speed;
    private final Multimap<EntityAttribute, EntityAttributeModifier> modifiers;

    public LiturgicalWeaponItem(Settings settings, int rank, double damage, double speed, WeaponAbility ability,
                                int cooldownTicks, int manaCost, Identifier hudIcon) {
        super(settings);
        this.rank = rank;
        this.damage = damage;
        this.speed = speed;
        this.ability = ability;
        this.cooldownTicks = cooldownTicks;
        this.manaCost = manaCost;
        this.hudIcon = hudIcon;
        this.modifiers = ImmutableMultimap.<EntityAttribute, EntityAttributeModifier>builder()
                .put(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(ATTACK_DAMAGE_MODIFIER_ID,
                        "Weapon damage", damage - 1.0, EntityAttributeModifier.Operation.ADDITION))
                .put(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(ATTACK_SPEED_MODIFIER_ID,
                        "Weapon speed", speed - 4.0, EntityAttributeModifier.Operation.ADDITION))
                .build();
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getAttributeModifiers(slot);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (player.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.pass(stack);
        if (!RankView.has(player, rank)) {
            if (world.isClient) {
                player.sendMessage(Text.translatable("message.vestments.rank_required",
                        Text.translatable(Ranks.nameKey(rank))).formatted(Formatting.RED), true);
            }
            return TypedActionResult.fail(stack);
        }
        if (!world.isClient && player instanceof ServerPlayerEntity sp) {
            if (!com.bleid.vestments.patriarch.Mana.spend(sp, manaCost)) {
                sp.sendMessage(Text.translatable("message.vestments.no_mana").formatted(Formatting.AQUA), true);
                return TypedActionResult.fail(stack);
            }
            com.bleid.vestments.patriarch.Mana.sync(sp);
            ability.activate((ServerWorld) world, sp, ServicePoints.power(sp));
            player.getItemCooldownManager().set(this, cooldownTicks);
            if (!player.getAbilities().creativeMode) {
                stack.damage(1, player, p -> p.sendToolBreakStatus(hand));
            }
        }
        return TypedActionResult.success(stack, world.isClient);
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
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable("tooltip.vestments.staff_of_light.peace").formatted(Formatting.YELLOW));
        lines.add(Text.translatable("tooltip.vestments.weapon.use", Text.translatable(ability.nameKey()),
                cooldownTicks / 20).formatted(Formatting.GOLD));
        lines.addAll(ability.describe());
        lines.add(Text.translatable("tooltip.vestments.weapon.mana", manaCost).formatted(Formatting.AQUA));
        boolean ok = RankView.clientRank() >= rank;
        lines.add(Text.translatable("tooltip.vestments.requires_rank", Text.translatable(Ranks.nameKey(rank)))
                .formatted(ok ? Formatting.DARK_AQUA : Formatting.RED));
        return Optional.of(new SmallTooltipData(lines));
    }
}
