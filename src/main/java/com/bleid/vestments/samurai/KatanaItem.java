package com.bleid.vestments.samurai;

import com.bleid.vestments.SmallTooltipData;
import com.bleid.vestments.paladin.PaladinSwordItem;
import com.bleid.vestments.service.RankView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.item.TooltipData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Катана самурая. ЛКМ — серия из четырёх ударов двумя руками (кэса, ёко, кириагэ, цуки),
 * на бегу — удар с разбега, в прыжке — сверху, R — особый приём катаны.
 */
public class KatanaItem extends PaladinSwordItem {
    public final SamuraiMoves.KatanaSpec spec;

    public KatanaItem(Settings settings, SamuraiMoves.KatanaSpec spec, Item repair) {
        super(settings, 0, spec.dmg, spec.speed, spec.skill == null ? "none" : spec.skill.substring(2), spec.cd * 20, spec.dur, repair);
        this.spec = spec;
    }

    @Override
    public String requiredClass() {
        return "samurai";
    }

    public boolean hasSkill() {
        return spec.skill != null;
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable("tooltip.vestments.class_only", Text.translatable("class.vestments.samurai"))
                .formatted("samurai".equals(RankView.clientClass) ? Formatting.DARK_AQUA : Formatting.RED));
        lines.add(Text.translatable("tooltip.vestments.katana.combo").formatted(Formatting.YELLOW));
        if (hasSkill()) {
            lines.add(Text.translatable("tooltip.vestments.sword.skill", Text.translatable("skill.vestments.katana." + spec.id),
                    spec.cd).formatted(Formatting.GOLD));
        } else {
            lines.add(Text.translatable("tooltip.vestments.katana.passive_only").formatted(Formatting.GOLD));
        }
        lines.add(Text.translatable("tooltip.vestments.katana." + spec.id).formatted(Formatting.GRAY));
        return Optional.of(new SmallTooltipData(lines));
    }
}
