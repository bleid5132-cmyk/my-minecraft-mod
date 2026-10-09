package com.bleid.vestments.paladin;

import com.bleid.vestments.SmallTooltipData;
import com.bleid.vestments.classes.ClassItem;
import com.bleid.vestments.service.RankView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.item.TooltipData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * Щит паладина: блокирует как обычный щит. Особые свойства (при достигнутом звании):
 *   рекрут+          — «Отпор»: заблокированного врага отбрасывает;
 *   лорд+            — «Парирование»: блок в первые полсекунды оглушает врага;
 *   генерал          — «Свет щита»: блок лечит и возвращает часть урона.
 */
public class PaladinShieldItem extends Item implements ClassItem {
    public final int rank;
    private final Item repair;

    public PaladinShieldItem(Settings settings, int rank, Item repair) {
        super(settings);
        this.rank = rank;
        this.repair = repair;
    }

    @Override
    public String requiredClass() {
        return "paladin";
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BLOCK;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public int getEnchantability() {
        return 12;
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return ingredient.isOf(repair) || super.canRepair(stack, ingredient);
    }

    public boolean bash() { return rank >= 1; }
    public boolean parry() { return rank >= 3; }
    public boolean radiant() { return rank >= 5; }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable("tooltip.vestments.class_only", Text.translatable("class.vestments.paladin"))
                .formatted(RankView.clientPaladin() ? Formatting.DARK_AQUA : Formatting.RED));
        lines.add(Text.translatable("tooltip.vestments.shield.block").formatted(Formatting.GRAY));
        if (bash()) lines.add(Text.translatable("tooltip.vestments.shield.bash").formatted(Formatting.GOLD));
        if (parry()) lines.add(Text.translatable("tooltip.vestments.shield.parry").formatted(Formatting.GOLD));
        if (radiant()) lines.add(Text.translatable("tooltip.vestments.shield.radiant").formatted(Formatting.GOLD));
        boolean ok = RankView.clientPaladinRank() >= rank;
        lines.add(Text.translatable("tooltip.vestments.requires_rank", Text.translatable(PaladinRanks.nameKey(rank)))
                .formatted(ok ? Formatting.DARK_AQUA : Formatting.RED));
        return Optional.of(new SmallTooltipData(lines));
    }
}
