package com.bleid.vestments;

import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Предмет облачения: в описании показывает свою способность и условие полного комплекта. */
public class VestmentArmorItem extends ArmorItem {
    private final String abilityKey;

    public VestmentArmorItem(ArmorMaterial material, Type type, Settings settings, String abilityKey) {
        super(material, type, settings);
        this.abilityKey = abilityKey;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable(abilityKey).formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.vestments.full_set").formatted(Formatting.GRAY, Formatting.ITALIC));
    }
}
