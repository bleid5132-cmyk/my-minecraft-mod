package com.bleid.vestments.client;

import com.bleid.vestments.altar.HolyAltarBlock;
import com.bleid.vestments.service.Ranks;
import com.bleid.vestments.service.RankView;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Окно Священного алтаря в стиле шкалы служения: что можно пожертвовать церкви и сколько
 * очков служения даёт каждый дар. ЛКМ по дару — пожертвовать 1 шт., Shift+ЛКМ — все такие предметы.
 */
@Environment(EnvType.CLIENT)
public class AltarScreen extends Screen {
    private static final int COLS = 8, CELL = 26, PW = 268, PH = 172;
    private final BlockPos altar;
    private final long opened = Util.getMeasuringTimeMs();

    public AltarScreen(BlockPos altar) {
        super(Text.translatable("screen.vestments.altar"));
        this.altar = altar;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HolyAltarBlock.OPEN, (client, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> client.setScreen(new AltarScreen(pos)));
        });
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int px() { return (width - PW) / 2; }
    private int py() { return (height - PH) / 2; }
    private int gridX() { return px() + (PW - COLS * CELL) / 2; }
    private int gridY() { return py() + 70; }

    private int count(HolyAltarBlock.Offering o) {
        if (client == null || client.player == null) return 0;
        int n = 0;
        var inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (!st.isEmpty() && o.matches(st)) n += st.getCount();
        }
        return n;
    }

    private int hovered(double mx, double my) {
        int gx = gridX(), gy = gridY();
        for (int i = 0; i < HolyAltarBlock.OFFERINGS.size(); i++) {
            int x = gx + (i % COLS) * CELL, y = gy + (i / COLS) * (CELL + 8);
            if (mx >= x && mx < x + CELL - 2 && my >= y && my < y + CELL - 2) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = hovered(mx, my);
        if (i >= 0 && button == 0 && RankView.clientPriest && count(HolyAltarBlock.OFFERINGS.get(i)) > 0) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(altar);
            buf.writeVarInt(i);
            buf.writeBoolean(hasShiftDown());
            ClientPlayNetworking.send(HolyAltarBlock.DONATE, buf);
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, 0.6f));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        float a = MathHelper.clamp((Util.getMeasuringTimeMs() - opened) / 180f, 0f, 1f);
        int x = px(), y = py() + (int) ((1 - a) * 8);
        // фон и золотая рамка — как у шкалы служения
        ctx.fillGradient(x, y, x + PW, y + PH, 0xF01A1008, 0xF0301A0C);
        int gold = 0xFFC9A13B;
        ctx.fill(x, y, x + PW, y + 1, gold); ctx.fill(x, y + PH - 1, x + PW, y + PH, gold);
        ctx.fill(x, y, x + 1, y + PH, gold); ctx.fill(x + PW - 1, y, x + PW, y + PH, gold);
        ctx.fill(x + 3, y + 3, x + PW - 3, y + 4, 0x80C9A13B);
        ctx.fill(x + 3, y + PH - 4, x + PW - 3, y + PH - 3, 0x80C9A13B);

        int cx = x + PW / 2;
        center(ctx, Text.literal("✦ ").append(title).append(" ✦").formatted(Formatting.BOLD), cx, y + 10, 0xFFFFD24A);
        center(ctx, Text.translatable("screen.vestments.altar_hint"), cx, y + 24, 0xFFE8D9B0);
        boolean priest = RankView.clientPriest;
        if (priest) {
            int pts = ServiceClient.displayed();
            int rank = Ranks.rankFor(pts);
            center(ctx, Text.translatable("screen.vestments.altar_status", Text.translatable(Ranks.nameKey(rank)), pts),
                    cx, y + 38, 0xFFB89A6A);
        } else {
            center(ctx, Text.translatable("message.vestments.altar_not_priest"), cx, y + 38, 0xFFFF7070);
        }
        ctx.fill(x + 14, y + 52, x + PW - 14, y + 53, 0x60C9A13B);

        // дары
        int gx = gridX(), gy = gridY();
        int hov = hovered(mx, my);
        List<HolyAltarBlock.Offering> list = HolyAltarBlock.OFFERINGS;
        for (int i = 0; i < list.size(); i++) {
            HolyAltarBlock.Offering o = list.get(i);
            int sx = gx + (i % COLS) * CELL, sy = gy + (i / COLS) * (CELL + 8);
            int have = count(o);
            boolean can = priest && have > 0;
            int frame = i == hov && can ? 0xFFFFD24A : can ? 0xFF8A6A2A : 0xFF3E3020;
            ctx.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, frame);
            ctx.fill(sx + 1, sy + 1, sx + CELL - 3, sy + CELL - 3, i == hov && can ? 0xFF4A3418 : 0xFF22160A);
            ItemStack icon = new ItemStack(o.item());
            ctx.drawItem(icon, sx + 4, sy + 4);
            if (have > 0) ctx.drawItemInSlot(textRenderer, icon, sx + 4, sy + 4, String.valueOf(Math.min(have, 999)));
            if (!can) ctx.fill(sx + 1, sy + 1, sx + CELL - 3, sy + CELL - 3, 0x90000000);
            String val = "+" + o.points();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(sx + (CELL - 2) / 2f, sy + CELL - 1, 300);
            ctx.getMatrices().scale(0.7f, 0.7f, 1f);
            ctx.drawText(textRenderer, val, -textRenderer.getWidth(val) / 2, 0, can ? 0xFFFFD24A : 0xFF8C7A5A, true);
            ctx.getMatrices().pop();
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, y + PH - 16, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1f);
        Text help = Text.translatable("screen.vestments.altar_help");
        ctx.drawText(textRenderer, help, -textRenderer.getWidth(help) / 2, 0, 0xFF9C8A6A, false);
        ctx.getMatrices().pop();

        if (hov >= 0) {
            HolyAltarBlock.Offering o = list.get(hov);
            List<Text> tip = new ArrayList<>();
            tip.add(o.item() == net.minecraft.item.Items.CANDLE
                    ? Text.translatable("screen.vestments.altar_any_candle") : new ItemStack(o.item()).getName());
            tip.add(Text.translatable("screen.vestments.altar_value", o.points()).formatted(Formatting.GOLD));
            int have = count(o);
            tip.add(Text.translatable("screen.vestments.altar_have", have, have * o.points()).formatted(Formatting.GRAY));
            ctx.drawTooltip(textRenderer, tip, mx, my);
        }
        super.render(ctx, mx, my, delta);
    }

    private void center(DrawContext ctx, Text t, int cx, int y, int color) {
        ctx.drawText(textRenderer, t, cx - textRenderer.getWidth(t) / 2, y, color, true);
    }
}
