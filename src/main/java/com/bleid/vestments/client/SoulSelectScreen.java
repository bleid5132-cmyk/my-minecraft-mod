package com.bleid.vestments.client;

import com.bleid.vestments.patriarch.Mana;
import com.bleid.vestments.patriarch.SoulAllies;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * Выбор душ для ульты «Сонм душ»: показываются все враждебные мобы, которых священник одолел;
 * можно отметить до трёх видов. «Готово» отправляет выбор на сервер.
 */
@Environment(EnvType.CLIENT)
public class SoulSelectScreen extends Screen {
    private static final int COLS = 8, CELL = 28, PW = 276;
    private final List<String> killed;
    private final Set<String> chosen = new LinkedHashSet<>();
    private final long opened = Util.getMeasuringTimeMs();
    private int scroll;

    public SoulSelectScreen(List<String> killed, List<String> chosen) {
        super(Text.translatable("screen.vestments.souls"));
        this.killed = killed;
        for (String c : chosen) if (killed.contains(c) && this.chosen.size() < Mana.MAX_CHOSEN) this.chosen.add(c);
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SoulAllies.OPEN_SELECT, (client, handler, buf, sender) -> {
            int n = buf.readVarInt();
            List<String> k = new ArrayList<>();
            for (int i = 0; i < n; i++) k.add(buf.readString());
            int m = buf.readVarInt();
            List<String> c = new ArrayList<>();
            for (int i = 0; i < m; i++) c.add(buf.readString());
            client.execute(() -> client.setScreen(new SoulSelectScreen(k, c)));
        });
    }

    private int rows() { return Math.max(1, Math.min(4, (killed.size() + COLS - 1) / COLS)); }
    private int ph() { return 96 + rows() * (CELL + 4) + 30; }
    private int px() { return (width - PW) / 2; }
    private int py() { return (height - ph()) / 2; }
    private int gridX() { return px() + (PW - COLS * CELL) / 2; }
    private int gridY() { return py() + 70; }
    private int maxScroll() { return Math.max(0, (killed.size() + COLS - 1) / COLS - rows()); }

    @Override
    protected void init() {
        int y = py() + ph() - 28;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.vestments.souls_done"), b -> done())
                .dimensions(width / 2 - 50, y, 100, 20).build());
    }

    private void done() {
        if (chosen.isEmpty()) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(chosen.size());
        for (String s : chosen) buf.writeString(s);
        ClientPlayNetworking.send(SoulAllies.CHOOSE, buf);
        close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int hovered(double mx, double my) {
        int gx = gridX(), gy = gridY();
        for (int r = 0; r < rows(); r++) {
            for (int c = 0; c < COLS; c++) {
                int i = (r + scroll) * COLS + c;
                if (i >= killed.size()) return -1;
                int x = gx + c * CELL, y = gy + r * (CELL + 4);
                if (mx >= x && mx < x + CELL - 2 && my >= y && my < y + CELL - 2) return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = hovered(mx, my);
        if (i >= 0 && button == 0) {
            String id = killed.get(i);
            if (chosen.contains(id)) chosen.remove(id);
            else if (chosen.size() < Mana.MAX_CHOSEN) chosen.add(id);
            else return true;
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.6f, 0.6f));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        scroll = MathHelper.clamp(scroll - (int) Math.signum(amount), 0, maxScroll());
        return true;
    }

    private static EntityType<?> type(String id) {
        Identifier ident = Identifier.tryParse(id);
        return ident == null ? null : Registries.ENTITY_TYPE.get(ident);
    }

    private static ItemStack icon(EntityType<?> t) {
        SpawnEggItem egg = t == null ? null : SpawnEggItem.forEntity(t);
        return new ItemStack(egg != null ? egg : Items.SOUL_LANTERN);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        float a = MathHelper.clamp((Util.getMeasuringTimeMs() - opened) / 180f, 0f, 1f);
        int x = px(), y = py() + (int) ((1 - a) * 8), ph = ph();
        ctx.fillGradient(x, y, x + PW, y + ph, 0xF00C1420, 0xF0182A3C);
        int frame = 0xFF8FC8FF;
        ctx.fill(x, y, x + PW, y + 1, frame); ctx.fill(x, y + ph - 1, x + PW, y + ph, frame);
        ctx.fill(x, y, x + 1, y + ph, frame); ctx.fill(x + PW - 1, y, x + PW, y + ph, frame);
        ctx.fill(x + 3, y + 3, x + PW - 3, y + 4, 0x808FC8FF);

        int cx = x + PW / 2;
        center(ctx, Text.literal("✦ ").append(title).append(" ✦").formatted(Formatting.BOLD), cx, y + 10, 0xFFBFE2FF);
        center(ctx, Text.translatable("screen.vestments.souls_hint", Mana.MAX_CHOSEN), cx, y + 24, 0xFFD6E4F0);
        center(ctx, Text.translatable("screen.vestments.souls_count", chosen.size(), Mana.MAX_CHOSEN), cx, y + 38,
                chosen.isEmpty() ? 0xFFFF9090 : 0xFF8FC8FF);
        ctx.fill(x + 14, y + 52, x + PW - 14, y + 53, 0x608FC8FF);

        int gx = gridX(), gy = gridY() + (y - py());
        int hov = hovered(mx, my);
        for (int r = 0; r < rows(); r++) {
            for (int c = 0; c < COLS; c++) {
                int i = (r + scroll) * COLS + c;
                if (i >= killed.size()) break;
                String id = killed.get(i);
                boolean sel = chosen.contains(id);
                int sx = gx + c * CELL, sy = gy + r * (CELL + 4);
                int fr = sel ? 0xFF8FC8FF : i == hov ? 0xFFBFE2FF : 0xFF34506A;
                ctx.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, fr);
                ctx.fill(sx + 1, sy + 1, sx + CELL - 3, sy + CELL - 3, sel ? 0xFF2A4A6A : i == hov ? 0xFF22384E : 0xFF101C28);
                ctx.drawItem(icon(type(id)), sx + 5, sy + 5);
                if (sel) {
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(0, 0, 300);
                    ctx.drawText(textRenderer, "✔", sx + CELL - 10, sy + 1, 0xFF8FFFB0, true);
                    ctx.getMatrices().pop();
                }
            }
        }
        if (maxScroll() > 0) {
            String s = (scroll + 1) + "/" + (maxScroll() + 1);
            ctx.drawText(textRenderer, s, x + PW - 14 - textRenderer.getWidth(s), y + 56, 0xFF6A8AA6, false);
        }
        super.render(ctx, mx, my, delta);
        if (hov >= 0) {
            EntityType<?> t = type(killed.get(hov));
            List<Text> tip = new ArrayList<>();
            tip.add(t != null ? t.getName().copy().formatted(Formatting.AQUA) : Text.literal(killed.get(hov)));
            tip.add(Text.translatable(chosen.contains(killed.get(hov))
                    ? "screen.vestments.souls_unpick" : "screen.vestments.souls_pick").formatted(Formatting.GRAY));
            ctx.drawTooltip(textRenderer, tip, mx, my);
        }
    }

    private void center(DrawContext ctx, Text t, int cx, int y, int color) {
        ctx.drawText(textRenderer, t, cx - textRenderer.getWidth(t) / 2, y, color, true);
    }
}
