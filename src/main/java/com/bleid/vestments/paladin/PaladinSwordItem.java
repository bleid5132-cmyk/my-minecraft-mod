package com.bleid.vestments.paladin;

import com.bleid.vestments.SmallTooltipData;
import com.bleid.vestments.classes.ClassItem;
import com.bleid.vestments.paladin.combat.Moveset;
import com.bleid.vestments.paladin.combat.Movesets;
import com.bleid.vestments.service.RankView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.item.TooltipData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Меч паладина. ЛКМ — серия ударов в стиле этого меча (см. combat/Movesets),
 * на бегу — удар с разбега, в прыжке — удар сверху, клавиша R — особый приём (нужно звание).
 */
public class PaladinSwordItem extends SwordItem implements ClassItem {
    public final int rank;
    public final String skill;
    public final int skillCooldown;

    public PaladinSwordItem(Settings settings, int rank, double damage, double speed, String skill, int skillCooldown,
                            int durability, Item repair) {
        super(material(damage, durability, repair), (int) Math.floor(damage - 1), (float) (speed - 4.0), settings);
        this.rank = rank;
        this.skill = skill;
        this.skillCooldown = skillCooldown;
    }

    /** Материал меча: дробная часть урона уходит в материал, целая — в сам меч. */
    private static ToolMaterial material(double damage, int durability, Item repair) {
        float frac = (float) ((damage - 1) - Math.floor(damage - 1));
        return new ToolMaterial() {
            @Override public int getDurability() { return durability; }
            @Override public float getMiningSpeedMultiplier() { return 1.5f; }
            @Override public float getAttackDamage() { return frac; }
            @Override public int getMiningLevel() { return 2; }
            @Override public int getEnchantability() { return 15; }
            @Override public Ingredient getRepairIngredient() { return Ingredient.ofItems(repair); }
        };
    }

    @Override
    public String requiredClass() {
        return "paladin";
    }

    public String swordId() {
        return Registries.ITEM.getId(this).getPath();
    }

    public Moveset moveset() {
        return Movesets.of(swordId());
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable("tooltip.vestments.class_only", Text.translatable("class.vestments.paladin"))
                .formatted(RankView.clientPaladin() ? Formatting.DARK_AQUA : Formatting.RED));
        Moveset m = moveset();
        if (m != null) {
            lines.add(Text.translatable("tooltip.vestments.sword.combo", m.combo.length).formatted(Formatting.YELLOW));
        }
        lines.add(Text.translatable("tooltip.vestments.sword.skill", Text.translatable("skill.vestments." + skill),
                skillCooldown / 20).formatted(Formatting.GOLD));
        lines.add(Text.translatable("tooltip.vestments.skill." + skill).formatted(Formatting.GRAY));
        boolean ok = RankView.clientPaladinRank() >= rank;
        lines.add(Text.translatable("tooltip.vestments.requires_rank", Text.translatable(PaladinRanks.nameKey(rank)))
                .formatted(ok ? Formatting.DARK_AQUA : Formatting.RED));
        return Optional.of(new SmallTooltipData(lines));
    }
}
