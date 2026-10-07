package com.bleid.vestments.client;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.classes.PlayerClasses;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Экран выбора класса при первом входе в мир. Закрыть без выбора нельзя. */
@Environment(EnvType.CLIENT)
public class ClassSelectScreen extends Screen {
    private static final int CARD_W = 150, CARD_H = 190, GAP = 16, ICON = 64;

    public ClassSelectScreen() {
        super(Text.translatable("screen.vestments.class_select"));
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PlayerClasses.OPEN_SELECT,
                (client, handler, buf, responseSender) -> client.execute(() -> client.setScreen(new ClassSelectScreen())));
    }

    private int cardX(int index) {
        int n = PlayerClasses.CLASSES.size();
        int total = n * CARD_W + (n - 1) * GAP;
        return (width - total) / 2 + index * (CARD_W + GAP);
    }

    private int cardY() {
        return (height - CARD_H) / 2 + 10;
    }

    @Override
    protected void init() {
        for (int i = 0; i < PlayerClasses.CLASSES.size(); i++) {
            String id = PlayerClasses.CLASSES.get(i);
            int x = cardX(i), y = cardY();
            addDrawableChild(ButtonWidget.builder(Text.translatable("screen.vestments.choose"), b -> choose(id))
                    .dimensions(x + 15, y + CARD_H - 28, CARD_W - 30, 20).build());
        }
    }

    private void choose(String id) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(id);
        ClientPlayNetworking.send(PlayerClasses.CHOOSE, buf);
        close();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        ctx.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.GOLD, Formatting.BOLD),
                width / 2, cardY() - 28, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.vestments.class_select_hint"),
                width / 2, cardY() - 16, 0xAAAAAA);

        for (int i = 0; i < PlayerClasses.CLASSES.size(); i++) {
            String id = PlayerClasses.CLASSES.get(i);
            int x = cardX(i), y = cardY();
            boolean hover = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
            // карточка: рамка и фон
            ctx.fill(x - 1, y - 1, x + CARD_W + 1, y + CARD_H + 1, hover ? 0xFFFFD24A : 0xFFB8860B);
            ctx.fill(x, y, x + CARD_W, y + CARD_H, 0xE0201810);
            // иконка класса
            Identifier icon = new Identifier(Vestments.MOD_ID, "textures/gui/class_" + id + ".png");
            ctx.drawTexture(icon, x + (CARD_W - ICON) / 2, y + 10, 0, 0, ICON, ICON, ICON, ICON);
            // название и описание
            ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("class.vestments." + id)
                    .formatted(Formatting.GOLD, Formatting.BOLD), x + CARD_W / 2, y + ICON + 18, 0xFFFFFF);
            List<net.minecraft.text.OrderedText> lines = textRenderer.wrapLines(
                    Text.translatable("class.vestments." + id + ".desc"), CARD_W - 16);
            int ly = y + ICON + 32;
            for (var line : lines) {
                ctx.drawText(textRenderer, line, x + 8, ly, 0xFFDDDDDD, false);
                ly += 10;
            }
        }
        super.render(ctx, mouseX, mouseY, delta);
    }
}
