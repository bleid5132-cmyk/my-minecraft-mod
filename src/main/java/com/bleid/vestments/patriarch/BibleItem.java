package com.bleid.vestments.patriarch;

import com.bleid.vestments.SmallTooltipData;
import com.bleid.vestments.service.Ranks;
import com.bleid.vestments.service.ServicePoints;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.item.TooltipData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Библия: прочитавший священник любой степени навсегда получает способности своей степени (Z, V, B). */
public class BibleItem extends Item {
    public BibleItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return TypedActionResult.success(stack, true);
        if (!ServicePoints.canServe(sp)) {
            sp.sendMessage(Text.translatable("message.vestments.bible_only_priest").formatted(Formatting.RED), true);
            return TypedActionResult.fail(stack);
        }
        if (Mana.learned(sp.getServer(), sp.getUuid())) {
            sp.sendMessage(Text.translatable("message.vestments.book_already").formatted(Formatting.GRAY), true);
            return TypedActionResult.fail(stack);
        }
        Mana.learn(sp.getServer(), sp.getUuid());
        Mana.sync(sp);
        world.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1f, 1f);
        world.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1f, 1f);
        sp.sendMessage(Text.translatable("message.vestments.bible_learned").formatted(Formatting.GOLD), false);
        if (BibleAbilities.unlocked(ServicePoints.rank(sp)) >= 3 && Mana.chosen(sp.getServer(), sp.getUuid()).isEmpty()) {
            SoulAllies.openSelection(sp);
        }
        if (!sp.getAbilities().creativeMode) stack.decrement(1);
        return TypedActionResult.success(stack, false);
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        List<Text> lines = new ArrayList<>();
        for (int d = 0; d < 3; d++) {
            lines.add(Text.translatable(Ranks.degreeKey(d)).formatted(Formatting.YELLOW));
            for (int s = 0; s < 3; s++) {
                String id = BibleAbilities.abilityAt(d, s);
                lines.add(Text.literal("  ").append(Text.translatable("ability.vestments." + id))
                        .append(Text.literal(" — ").append(Text.translatable(Ranks.nameKey(BibleAbilities.unlockRank(d, s)))))
                        .formatted(s == 2 ? Formatting.LIGHT_PURPLE : Formatting.GOLD));
            }
        }
        lines.add(Text.translatable("tooltip.vestments.book_use").formatted(Formatting.GRAY));
        return Optional.of(new SmallTooltipData(lines));
    }
}
