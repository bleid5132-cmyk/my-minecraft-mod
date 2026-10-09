package com.bleid.vestments.classes;

import com.bleid.vestments.service.RankView;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;

/**
 * Предметы классов: поднять и носить в инвентаре может любой, но надеть броню или применить
 * оружие/щит — только игрок нужного класса. Чужая броня сама снимается в инвентарь.
 */
public final class ClassRules {
    private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private ClassRules() { }

    public static boolean allowed(PlayerEntity player, ItemStack stack) {
        if (!(stack.getItem() instanceof ClassItem ci)) return true;
        return ci.requiredClass().equals(RankView.classOf(player));
    }

    public static Text deny(ItemStack stack) {
        String cls = stack.getItem() instanceof ClassItem ci ? ci.requiredClass() : "";
        return Text.translatable("message.vestments.wrong_class", Text.translatable("class.vestments." + cls))
                .formatted(Formatting.RED);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 5 != 0) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                for (EquipmentSlot slot : ARMOR) {
                    ItemStack st = p.getEquippedStack(slot);
                    if (st.isEmpty() || allowed(p, st)) continue;
                    ItemStack copy = st.copy();
                    p.equipStack(slot, ItemStack.EMPTY);
                    if (!p.getInventory().insertStack(copy)) p.dropItem(copy, false);
                    p.sendMessage(deny(copy), true);
                }
            }
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            ItemStack st = player.getStackInHand(hand);
            if (allowed(player, st) || player.isSpectator()) return ActionResult.PASS;
            if (world.isClient) player.sendMessage(deny(st), true);
            return ActionResult.FAIL;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack st = player.getStackInHand(hand);
            if (allowed(player, st) || player.isSpectator()) return TypedActionResult.pass(st);
            if (world.isClient) player.sendMessage(deny(st), true);
            return TypedActionResult.fail(st);
        });
    }
}
