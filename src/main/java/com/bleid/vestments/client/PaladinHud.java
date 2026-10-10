package com.bleid.vestments.client;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.client.combat.CombatClient;
import com.bleid.vestments.paladin.PaladinAbilities;
import com.bleid.vestments.paladin.PaladinSwordItem;
import com.bleid.vestments.service.RankView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Способности паладина в правом нижнем углу: G H J K N и особый приём оружия (R).
 * Перезарядка — затемнение сверху вниз и секунды; недоступно по званию — красная метка и звание;
 * активная аура — рамка её цвета; крылья — золотая рамка с оставшимися секундами.
 * Левее — маленький значок пассивного «Мученичества» (у генерала).
 */
@Environment(EnvType.CLIENT)
public final class PaladinHud {
    private static final int SIZE = 22, GAP = 4, MARGIN = 6;
    private static final int[] RANK = { 1, 2, 3, 4, 5 };
    private static final Identifier[] ICONS = new Identifier[5];
    private static final Identifier MARTYR = new Identifier(Vestments.MOD_ID, "textures/gui/pal_martyr.png");
    private static final int[] AURA_COLOR = { 0, 0xFF6FA8FF, 0xFF7CFF6E, 0xFFFF5040 };

    private static final int[] left = new int[5], total = { 1, 1, 1, 1, 1 };
    private static int aura, martyrLeft, martyrTotal = 1, wingsLeft;

    static {
        for (int i = 0; i < 5; i++) {
            ICONS[i] = new Identifier(Vestments.MOD_ID, "textures/gui/pal_" + PaladinAbilities.NAMES[i] + ".png");
        }
    }

    private PaladinHud() { }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PaladinAbilities.SYNC, (client, handler, buf, sender) -> {
            int[] l = new int[5], t = new int[5];
            for (int i = 0; i < 5; i++) { l[i] = buf.readVarInt(); t[i] = buf.readVarInt(); }
            int a = buf.readVarInt(), ml = buf.readVarInt(), mt = buf.readVarInt(), wl = buf.readVarInt();
            client.execute(() -> {
                System.arraycopy(l, 0, left, 0, 5);
                System.arraycopy(t, 0, total, 0, 5);
                aura = a; martyrLeft = ml; martyrTotal = Math.max(1, mt); wingsLeft = wl;
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.isPaused()) return;
            for (int i = 0; i < 5; i++) if (left[i] > 0) left[i]--;
            if (martyrLeft > 0) martyrLeft--;
            if (wingsLeft > 0) wingsLeft--;
        });
        HudRenderCallback.EVENT.register(PaladinHud::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.options.hudHidden || p.isSpectator() || !RankView.clientPaladin()) return;
        TextRenderer font = mc.textRenderer;
        int rank = RankView.clientPaladinRank();
        int y = ctx.getScaledWindowHeight() - SIZE - MARGIN;
        int x = ctx.getScaledWindowWidth() - MARGIN - SIZE;

        // особый приём оружия (R) — справа
        ItemStack held = p.getMainHandStack();
        if (held.getItem() instanceof PaladinSwordItem w) {
            frame(ctx, x, y, 0xFF8A6A2A);
            ctx.drawItem(held, x + 3, y + 3);
            boolean ok = rank >= w.rank;
            float cd = p.getItemCooldownManager().getCooldownProgress(w, tickDelta);
            if (!ok) locked(ctx, font, x, y, w.rank);
            else if (cd > 0) cooldown(ctx, font, x, y, cd, MathHelper.ceil(cd * w.skillCooldown / 20f));
            key(ctx, font, CombatClient.skillKey(), x, y, ok && cd <= 0);
            x -= SIZE + GAP + 4;
        }

        // магия паладина: справа налево N K J H G
        for (int i = 4; i >= 0; i--) {
            boolean ok = rank >= RANK[i];
            int border = 0xFF5A3A1A;
            if (i == PaladinAbilities.AURA && aura != 0) border = AURA_COLOR[aura];
            if (i == PaladinAbilities.WINGS && wingsLeft > 0) border = 0xFFFFD24A;
            frame(ctx, x, y, border);
            ctx.drawTexture(ICONS[i], x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
            if (!ok) {
                locked(ctx, font, x, y, RANK[i]);
            } else if (i == PaladinAbilities.WINGS && wingsLeft > 0) {
                center(ctx, font, String.valueOf(MathHelper.ceil(wingsLeft / 20f)), x + SIZE / 2, y + SIZE / 2 - 4, 0xFFFFF4C0);
            } else if (left[i] > 0 && i != PaladinAbilities.AURA) {
                float f = MathHelper.clamp((left[i] - tickDelta) / total[i], 0f, 1f);
                cooldown(ctx, font, x, y, f, MathHelper.ceil(left[i] / 20f));
            }
            if (i == PaladinAbilities.AURA && ok && aura != 0) {
                ctx.fill(x + SIZE - 6, y + SIZE - 6, x + SIZE - 1, y + SIZE - 1, AURA_COLOR[aura]);   // цвет активной ауры
            }
            key(ctx, font, PaladinKeys.binding(i), x, y, ok && (left[i] <= 0 || i == PaladinAbilities.AURA));
            x -= SIZE + GAP;
        }

        // пассивное «Мученичество» — маленький значок слева
        if (rank >= 5) {
            int s = 16, mx = x + SIZE - s, my = y + SIZE - s;
            ctx.drawTexture(MARTYR, mx, my, 0, 0, s, s, s, s);
            if (martyrLeft > 0) {
                int dark = MathHelper.ceil(s * MathHelper.clamp(martyrLeft / (float) martyrTotal, 0f, 1f));
                ctx.fill(mx, my, mx + s, my + dark, 0xB0000000);
            }
        }
    }

    private static void frame(DrawContext ctx, int x, int y, int color) {
        ctx.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, color);
        ctx.fill(x, y, x + SIZE, y + SIZE, 0xC0180C08);
    }

    private static void cooldown(DrawContext ctx, TextRenderer font, int x, int y, float f, int sec) {
        int dark = MathHelper.ceil(SIZE * f);
        ctx.fill(x, y, x + SIZE, y + dark, 0xB0000000);
        center(ctx, font, String.valueOf(sec), x + SIZE / 2, y + SIZE / 2 - 4, 0xFFFFFFFF);
    }

    private static void locked(DrawContext ctx, TextRenderer font, int x, int y, int rank) {
        ctx.fill(x, y, x + SIZE, y + SIZE, 0xB0300000);
        center(ctx, font, "✖", x + SIZE / 2, y + SIZE / 2 - 4, 0xFFFF6060);
    }

    private static void key(DrawContext ctx, TextRenderer font, KeyBinding kb, int x, int y, boolean ready) {
        if (kb == null) return;
        Text t = kb.getBoundKeyLocalizedText();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + SIZE / 2.0f, y - 7.0f, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1f);
        ctx.drawText(font, t, -font.getWidth(t) / 2, 0, ready ? 0xFFFFD24A : 0xFFAAAAAA, true);
        ctx.getMatrices().pop();
    }

    private static void center(DrawContext ctx, TextRenderer font, String s, int cx, int y, int color) {
        ctx.drawText(font, s, cx - font.getWidth(s) / 2, y, color, true);
    }
}
