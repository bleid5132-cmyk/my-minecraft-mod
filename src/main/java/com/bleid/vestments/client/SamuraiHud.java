package com.bleid.vestments.client;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.client.combat.CombatClient;
import com.bleid.vestments.samurai.KatanaItem;
import com.bleid.vestments.samurai.SamuraiServer;
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
 * Самурай на экране: полоса ки над здоровьем (с отметками «Пути меча» — сколько ударов до крита)
 * и способности G H J K N + особый приём катаны (R) в правом нижнем углу.
 */
@Environment(EnvType.CLIENT)
public final class SamuraiHud {
    private static final int SIZE = 22, GAP = 4, MARGIN = 6;
    private static final Identifier[] ICONS = new Identifier[5];
    private static final int[] LEFT = new int[5], TOTAL = { 1, 1, 1, 1, 1 };
    private static float ki, need = 100f, shownKi;
    private static int streak, charges = 2;

    static {
        for (int i = 0; i < 5; i++) ICONS[i] = new Identifier(Vestments.MOD_ID, "textures/gui/sam_" + SamuraiServer.NAMES[i] + ".png");
    }

    private SamuraiHud() { }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SamuraiServer.SYNC, (client, handler, buf, sender) -> {
            float k = buf.readFloat(), n = buf.readFloat();
            int st = buf.readVarInt();
            int[] l = new int[5], t = new int[5];
            int ch = 2;
            for (int i = 0; i < 5; i++) {
                l[i] = buf.readVarInt();
                t[i] = buf.readVarInt();
                if (i == SamuraiServer.WIND) ch = buf.readVarInt();
            }
            int chf = ch;
            client.execute(() -> {
                ki = k; need = n; streak = st; charges = chf;
                System.arraycopy(l, 0, LEFT, 0, 5);
                System.arraycopy(t, 0, TOTAL, 0, 5);
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.isPaused()) return;
            for (int i = 0; i < 5; i++) if (LEFT[i] > 0) LEFT[i]--;
            shownKi += (ki - shownKi) * 0.3f;
        });
        HudRenderCallback.EVENT.register(SamuraiHud::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.options.hudHidden || p.isSpectator() || !RankView.clientSamurai()) return;
        TextRenderer font = mc.textRenderer;
        int W = ctx.getScaledWindowWidth(), H = ctx.getScaledWindowHeight();

        // ---- полоса ки над сердцами
        int bw = 120, bx = W / 2 - bw / 2, by = H - 62 - (p.isCreative() ? -14 : 0);
        ctx.fill(bx - 1, by - 1, bx + bw + 1, by + 6, 0xFF1A0D0A);
        ctx.fill(bx, by, bx + bw, by + 5, 0xFF3A1A14);
        float f = MathHelper.clamp(shownKi / 100f, 0f, 1f);
        int fill = (int) (bw * f);
        boolean full = ki >= need - 0.01f;
        int col = full ? (((p.age / 4) % 2 == 0) ? 0xFFFF8FB8 : 0xFFFFC2D6) : 0xFFD8364A;
        ctx.fill(bx, by, bx + fill, by + 5, col);
        ctx.fill(bx, by, bx + fill, by + 1, 0x60FFFFFF);
        if (need < 100f) {                      // доспех сёгуна: ульта за половину ки
            int nx = bx + (int) (bw * need / 100f);
            ctx.fill(nx, by - 2, nx + 1, by + 7, 0xFFFFD24A);
        }
        String label = Text.translatable("hud.vestments.ki").getString() + " " + MathHelper.floor(ki);
        ctx.drawText(font, label, bx - font.getWidth(label) - 4, by - 1, 0xFFFFC2C2, true);
        // «Путь меча»: точки — удары подряд до крита
        for (int i = 0; i < 4; i++) {
            int dx = bx + bw + 4 + i * 5;
            ctx.fill(dx, by, dx + 3, by + 4, i < streak ? 0xFFFFD24A : 0xFF4A3A30);
        }

        // ---- способности
        int cell = SIZE + GAP;
        int left = W - MARGIN - 3 * cell + GAP;
        int lift = left < W / 2 + 95 ? 26 : 0;
        int yBottom = H - MARGIN - SIZE - lift, yTop = yBottom - SIZE - 11;
        boolean katana = p.getMainHandStack().getItem() instanceof KatanaItem;
        for (int slot = 0; slot < 6; slot++) {
            int x = left + (slot % 3) * cell, y = slot < 3 ? yTop : yBottom;
            if (slot == 5) {
                ItemStack held = p.getMainHandStack();
                if (!(held.getItem() instanceof KatanaItem k)) continue;
                frame(ctx, x, y, 0xFF8A2A2A);
                ctx.drawItem(held, x + 3, y + 3);
                if (!k.hasSkill()) {
                    ctx.fill(x, y, x + SIZE, y + SIZE, 0x90000000);
                } else {
                    float cd = p.getItemCooldownManager().getCooldownProgress(k, tickDelta);
                    if (cd > 0) cooldown(ctx, font, x, y, cd, MathHelper.ceil(cd * k.skillCooldown / 20f));
                    key(ctx, font, CombatClient.skillKey(), x, y, cd <= 0);
                }
                continue;
            }
            int i = slot;
            int border = 0xFF5A1A1A;
            if (i == SamuraiServer.ULT && full) border = ((p.age / 5) % 2 == 0) ? 0xFFFF8FB8 : 0xFFFFD24A;
            frame(ctx, x, y, border);
            ctx.drawTexture(ICONS[i], x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
            boolean ready = katana;
            if (i == SamuraiServer.WIND) {
                if (charges <= 0 && LEFT[i] > 0) cooldown(ctx, font, x, y, MathHelper.clamp((LEFT[i] - tickDelta) / TOTAL[i], 0f, 1f),
                        MathHelper.ceil(LEFT[i] / 20f));
                ctx.drawText(font, String.valueOf(charges), x + SIZE - 6, y + SIZE - 8, charges > 0 ? 0xFFFFFFFF : 0xFFAAAAAA, true);
                ready &= charges > 0;
            } else if (LEFT[i] > 0) {
                cooldown(ctx, font, x, y, MathHelper.clamp((LEFT[i] - tickDelta) / TOTAL[i], 0f, 1f), MathHelper.ceil(LEFT[i] / 20f));
                ready = false;
            } else if (i == SamuraiServer.ULT && !full) {
                int dark = MathHelper.ceil(SIZE * (1f - MathHelper.clamp(ki / need, 0f, 1f)));
                ctx.fill(x, y, x + SIZE, y + dark, 0x90000000);
                ready = false;
            }
            if (!katana) ctx.fill(x, y, x + SIZE, y + SIZE, 0x70000000);
            key(ctx, font, PaladinKeys.binding(i), x, y, ready);
        }
    }

    private static void frame(DrawContext ctx, int x, int y, int color) {
        ctx.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, color);
        ctx.fill(x, y, x + SIZE, y + SIZE, 0xC0180808);
    }

    private static void cooldown(DrawContext ctx, TextRenderer font, int x, int y, float f, int sec) {
        int dark = MathHelper.ceil(SIZE * f);
        ctx.fill(x, y, x + SIZE, y + dark, 0xB0000000);
        String s = String.valueOf(sec);
        ctx.drawText(font, s, x + SIZE / 2 - font.getWidth(s) / 2, y + SIZE / 2 - 4, 0xFFFFFFFF, true);
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
}
