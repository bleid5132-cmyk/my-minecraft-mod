package com.bleid.vestments;

import java.util.List;
import net.minecraft.client.item.TooltipData;
import net.minecraft.text.Text;

/** Строки описания предмета, которые рисуются уменьшенным шрифтом (см. client/SmallTooltipComponent). */
public record SmallTooltipData(List<Text> lines) implements TooltipData { }
