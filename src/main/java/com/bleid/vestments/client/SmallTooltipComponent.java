package com.bleid.vestments.client;

import com.bleid.vestments.SmallTooltipData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/** Описание предметов мода уменьшенным шрифтом. */
@Environment(EnvType.CLIENT)
public final class SmallTooltipComponent implements TooltipComponent {
    private static final float SCALE = 0.7f;
    private static final int LINE = 10;
    private final SmallTooltipData data;

    public SmallTooltipComponent(SmallTooltipData data) {
        this.data = data;
    }

    public static void register() {
        TooltipComponentCallback.EVENT.register(d -> d instanceof SmallTooltipData small ? new SmallTooltipComponent(small) : null);
    }

    @Override
    public int getHeight() {
        return MathHelper.ceil(data.lines().size() * LINE * SCALE) + 2;
    }

    @Override
    public int getWidth(TextRenderer textRenderer) {
        int max = 0;
        for (Text line : data.lines()) max = Math.max(max, textRenderer.getWidth(line));
        return MathHelper.ceil(max * SCALE);
    }

    @Override
    public void drawItems(TextRenderer textRenderer, int x, int y, DrawContext context) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(SCALE, SCALE, 1f);
        int ly = 0;
        for (Text line : data.lines()) {
            context.drawText(textRenderer, line, 0, ly, 0xFFFFFFFF, true);
            ly += LINE;
        }
        context.getMatrices().pop();
    }
}
