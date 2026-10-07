package com.bleid.vestments.patriarch;

import com.bleid.vestments.SmallTooltipData;
import com.bleid.vestments.service.RankView;
import com.bleid.vestments.service.Ranks;
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

/** Книга Патриарха: прочитавший Патриарх навсегда получает три способности (клавиши Z, X, C). */
public class PatriarchBookItem extends Item {
    public PatriarchBookItem(Settings settings) {
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
        if (!RankView.has(sp, PatriarchAbilities.REQUIRED_RANK)) {
            sp.sendMessage(Text.translatable("message.vestments.book_only_patriarch").formatted(Formatting.RED), true);
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
        sp.sendMessage(Text.translatable("message.vestments.book_learned").formatted(Formatting.GOLD), false);
        if (!sp.getAbilities().creativeMode) stack.decrement(1);
        return TypedActionResult.success(stack, false);
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        List<Text> lines = new ArrayList<>();
        for (int i = 0; i < PatriarchAbilities.COUNT; i++) {
            String id = PatriarchAbilities.IDS[i];
            lines.add(Text.translatable("ability.vestments." + id).formatted(Formatting.GOLD));
            lines.add(Text.translatable("tooltip.vestments." + id, (int) PatriarchAbilities.MANA[i],
                    PatriarchAbilities.COOLDOWN[i] / 20).formatted(Formatting.GRAY));
        }
        lines.add(Text.translatable("tooltip.vestments.book_use").formatted(Formatting.YELLOW));
        boolean ok = RankView.clientRank() >= PatriarchAbilities.REQUIRED_RANK;
        lines.add(Text.translatable("tooltip.vestments.requires_rank", Text.translatable(Ranks.nameKey(PatriarchAbilities.REQUIRED_RANK)))
                .formatted(ok ? Formatting.DARK_AQUA : Formatting.RED));
        return Optional.of(new SmallTooltipData(lines));
    }
}
