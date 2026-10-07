package com.bleid.vestments.client;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.service.Ranks;
import com.bleid.vestments.service.ServicePoints;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Клиент очков служения: принимает огоньки и итог с сервера, ведёт «показанное» значение
 * (растёт по мере касания огоньков), рисует шкалу служения при зажатой клавише I,
 * всплывающее «+N» и заставку повышения в сане.
 */
@Environment(EnvType.CLIENT)
public final class ServiceClient {
    private static KeyBinding panelKey;

    private static boolean synced;
    private static boolean hasClass;
    private static int serverPoints;
    private static int pending;          // очки в летящих огоньках, ещё не долетевших до игрока
    private static int shownRank;

    private static int recentGain;
    private static long recentGainAt;
    private static float panelAnim;      // 0 — закрыта, 1 — открыта
    private static long lastFrame;
    private static float shownBar;       // плавное заполнение полосы, 0..1

    private ServiceClient() { }

    public static void register() {
        panelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vestments.service_panel", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.vestments"));

        ParticleFactoryRegistry.getInstance().register(Vestments.SERVICE_ORB, ServiceOrbParticle.Factory::new);

        ClientPlayNetworking.registerGlobalReceiver(ServicePoints.SYNC, (client, handler, buf, sender) -> {
            boolean priest = buf.readBoolean();
            int total = buf.readInt();
            client.execute(() -> {
                hasClass = priest;
                serverPoints = total;
                com.bleid.vestments.service.RankView.clientPriest = priest;
                com.bleid.vestments.service.RankView.clientPoints = total;
                pending = 0;
                shownRank = Ranks.rankFor(total);
                synced = true;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ServicePoints.ORBS, (client, handler, buf, sender) -> {
            int entityId = buf.readInt();
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            int amount = buf.readInt();
            int total = buf.readInt();
            client.execute(() -> spawnOrbs(client, entityId, x, y, z, amount, total));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            synced = false;
            hasClass = false;
            serverPoints = 0;
            com.bleid.vestments.service.RankView.clientPriest = false;
            com.bleid.vestments.service.RankView.clientPoints = 0;
            pending = 0;
            shownBar = 0;
        });

        HudRenderCallback.EVENT.register(ServiceClient::renderHud);
    }

    private static void spawnOrbs(MinecraftClient client, int entityId, double x, double y, double z, int amount, int total) {
        if (client.world == null) return;
        Entity target = client.world.getEntityById(entityId);
        boolean mine = total >= 0 && client.player != null && target == client.player;
        if (mine) {
            serverPoints = total;
            com.bleid.vestments.service.RankView.clientPriest = true;
            com.bleid.vestments.service.RankView.clientPoints = total;
            pending += amount;
            synced = true;
        }
        if (target == null) {
            if (mine) credit(amount);
            return;
        }
        int orbs = MathHelper.clamp(amount, 1, 12);
        int base = amount / orbs, extra = amount % orbs;
        for (int i = 0; i < orbs; i++) {
            int value = base + (i < extra ? 1 : 0);
            client.particleManager.addParticle(new ServiceOrbParticle(client.world, x, y, z, target, value, mine));
        }
    }

    /** Огонёк долетел — очки «зашли» в шкалу. */
    static void credit(int value) {
        pending = Math.max(0, pending - value);
        recentGain += value;
        recentGainAt = Util.getMeasuringTimeMs();
        int rank = Ranks.rankFor(displayed());
        if (synced && rank > shownRank) {
            shownRank = rank;
            celebrate(rank);
        }
    }

    static int displayed() {
        return Math.max(0, serverPoints - pending);
    }

    private static void celebrate(int rank) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.inGameHud.setTitleTicks(10, 60, 20);
        client.inGameHud.setTitle(Text.translatable(Ranks.nameKey(rank)).formatted(Formatting.GOLD, Formatting.BOLD));
        client.inGameHud.setSubtitle(Text.translatable("title.vestments.rank_up",
                Text.translatable(Ranks.degreeKey(Ranks.degreeOf(rank)))).formatted(Formatting.YELLOW));
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f));
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_BELL_USE, 0.9f, 0.7f));
    }

    // ---------------------------------------------------------------- HUD

    private static void renderHud(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        long now = Util.getMeasuringTimeMs();
        float dt = lastFrame == 0 ? 0f : Math.min((now - lastFrame) / 1000f, 0.1f);
        lastFrame = now;

        boolean open = panelKey.isPressed() && client.currentScreen == null;
        panelAnim = MathHelper.clamp(panelAnim + (open ? dt * 7f : -dt * 9f), 0f, 1f);

        int points = displayed();
        int rank = Ranks.rankFor(points);
        float target = progress(points, rank);
        shownBar += (target - shownBar) * Math.min(1f, dt * 6f);
        if (Math.abs(target - shownBar) < 0.001f) shownBar = target;

        renderGain(ctx, client.textRenderer, now);
        if (panelAnim > 0f) renderPanel(ctx, client.textRenderer, points, rank);
    }

    private static float progress(int points, int rank) {
        if (Ranks.isMax(rank)) return 1f;
        int from = Ranks.THRESHOLD[rank], to = Ranks.THRESHOLD[rank + 1];
        return MathHelper.clamp((points - from) / (float) (to - from), 0f, 1f);
    }

    /** «+N очков служения» над хотбаром, гаснет через 2 секунды после последнего огонька. */
    private static void renderGain(DrawContext ctx, TextRenderer font, long now) {
        if (recentGain <= 0) return;
        long since = now - recentGainAt;
        if (since > 2200) {
            recentGain = 0;
            return;
        }
        float a = since < 1600 ? 1f : 1f - (since - 1600) / 600f;
        int alpha = MathHelper.clamp((int) (a * 255), 5, 255);
        Text t = Text.translatable("hud.vestments.service_gain", recentGain);
        int x = ctx.getScaledWindowWidth() / 2 - font.getWidth(t) / 2;
        int y = ctx.getScaledWindowHeight() - 72;
        ctx.drawText(font, t, x, y, (alpha << 24) | 0xFFD24A, true);
    }

    private static int withAlpha(int argb, float a) {
        int al = MathHelper.clamp((int) (((argb >>> 24) & 0xFF) * a), 0, 255);
        if (al < 5) al = 5;
        return (al << 24) | (argb & 0xFFFFFF);
    }

    private static void renderPanel(DrawContext ctx, TextRenderer font, int points, int rank) {
        float a = panelAnim * panelAnim * (3 - 2 * panelAnim);   // плавное появление
        int w = 260, h = 196;
        int cx = ctx.getScaledWindowWidth() / 2;
        int x = cx - w / 2;
        int y = ctx.getScaledWindowHeight() / 2 - h / 2 - 10 + (int) ((1 - a) * 12);

        var m = ctx.getMatrices();
        m.push();
        m.translate(0, 0, 200);

        // фон и золотая рамка
        ctx.fillGradient(x, y, x + w, y + h, withAlpha(0xE01A1008, a), withAlpha(0xE0301A0C, a));
        int gold = withAlpha(0xFFC9A13B, a);
        ctx.fill(x, y, x + w, y + 1, gold);
        ctx.fill(x, y + h - 1, x + w, y + h, gold);
        ctx.fill(x, y, x + 1, y + h, gold);
        ctx.fill(x + w - 1, y, x + w, y + h, gold);
        ctx.fill(x + 3, y + 3, x + w - 3, y + 4, withAlpha(0x80C9A13B, a));
        ctx.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, withAlpha(0x80C9A13B, a));

        if (!synced || !hasClass) {
            drawCentered(ctx, font, Text.translatable("hud.vestments.no_class"), cx, y + h / 2 - 4, withAlpha(0xFFAAAAAA, a));
            m.pop();
            return;
        }

        int degree = Ranks.degreeOf(rank);
        int inDegree = rank - Ranks.degreeStart(degree) + 1;
        int ty = y + 10;
        drawCentered(ctx, font, Text.translatable("hud.vestments.service_title"), cx, ty, withAlpha(0xFFB89A6A, a));
        ty += 14;
        drawCentered(ctx, font, Text.translatable("hud.vestments.degree",
                Text.literal(roman(degree + 1)), Text.translatable(Ranks.degreeKey(degree))), cx, ty, withAlpha(0xFFF2E6C9, a));
        ty += 14;

        // крупное название сана
        Text name = Text.translatable(Ranks.nameKey(rank)).formatted(Formatting.BOLD);
        m.push();
        m.translate(cx, ty, 0);
        m.scale(1.6f, 1.6f, 1f);
        ctx.drawText(font, name, -font.getWidth(name) / 2, 0, withAlpha(0xFFFFD24A, a), true);
        m.pop();
        ty += 18;
        drawCentered(ctx, font, Text.translatable("hud.vestments.rank_in_degree", inDegree, Ranks.degreeSize(degree)),
                cx, ty, withAlpha(0xFFB89A6A, a));
        ty += 14;

        // полоса прогресса
        int bw = 220, bh = 9, bx = cx - bw / 2;
        ctx.fill(bx - 1, ty - 1, bx + bw + 1, ty + bh + 1, withAlpha(0xFF6B5222, a));
        ctx.fill(bx, ty, bx + bw, ty + bh, withAlpha(0xFF120B05, a));
        int fw = (int) (bw * shownBar);
        if (fw > 0) {
            ctx.fillGradient(bx, ty, bx + fw, ty + bh, withAlpha(0xFFFFE27A, a), withAlpha(0xFFC08A1E, a));
            // бегущий блик
            float t = (Util.getMeasuringTimeMs() % 2400L) / 2400f;
            int sx = bx + (int) (t * (fw + 30)) - 15;
            int s0 = Math.max(bx, sx), s1 = Math.min(bx + fw, sx + 10);
            if (s1 > s0) ctx.fill(s0, ty, s1, ty + bh, withAlpha(0x60FFFFFF, a));
            ctx.fill(bx, ty, bx + fw, ty + 1, withAlpha(0x90FFF6C8, a));
        }
        ty += bh + 4;
        Text pts = Ranks.isMax(rank)
                ? Text.translatable("hud.vestments.points_max", points)
                : Text.translatable("hud.vestments.points", points, Ranks.THRESHOLD[rank + 1]);
        drawCentered(ctx, font, pts, cx, ty, withAlpha(0xFFF2E6C9, a));
        ty += 11;
        Text next = Ranks.isMax(rank)
                ? Text.translatable("hud.vestments.max_rank")
                : Text.translatable("hud.vestments.next_rank", Text.translatable(Ranks.nameKey(rank + 1)));
        drawCentered(ctx, font, next, cx, ty, withAlpha(0xFF9C8A6A, a));
        ty += 15;

        // лестница санов: три столбца по степеням
        ctx.fill(x + 12, ty - 4, x + w - 12, ty - 3, withAlpha(0x60C9A13B, a));
        int colW = (w - 16) / 3;
        float s = 0.62f;
        for (int d = 0; d < 3; d++) {
            int colX = x + 8 + d * colW + colW / 2;
            m.push();
            m.translate(colX, ty, 0);
            m.scale(s, s, 1f);
            Text head = Text.literal(roman(d + 1) + " · ").append(Text.translatable(Ranks.degreeKey(d)));
            ctx.drawText(font, head, -font.getWidth(head) / 2, 0, withAlpha(d == degree ? 0xFFFFD24A : 0xFFB89A6A, a), false);
            int line = 0;
            for (int r = 0; r < Ranks.count(); r++) {
                if (Ranks.degreeOf(r) != d) continue;
                line++;
                Text rn = Text.translatable(Ranks.nameKey(r));
                int color;
                if (r == rank) {
                    rn = Text.literal("✦ ").append(rn).append(" ✦").formatted(Formatting.BOLD);
                    color = 0xFFFFD24A;
                } else if (r < rank) {
                    color = 0xFFE8D9B0;
                } else {
                    color = 0xFF6E6252;
                }
                ctx.drawText(font, rn, -font.getWidth(rn) / 2, line * 11 + 2, withAlpha(color, a), false);
            }
            m.pop();
        }

        // сила «Благословения» по сану
        Text power = Text.translatable("hud.vestments.power", String.format("%.2f", Ranks.power(rank)).replace(".00", ""));
        m.push();
        m.translate(cx, y + h - 13, 0);
        m.scale(0.75f, 0.75f, 1f);
        ctx.drawText(font, power, -font.getWidth(power) / 2, 0, withAlpha(0xFFB89A6A, a), false);
        m.pop();

        m.pop();
    }

    private static void drawCentered(DrawContext ctx, TextRenderer font, Text t, int cx, int y, int color) {
        ctx.drawText(font, t, cx - font.getWidth(t) / 2, y, color, true);
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(n);
        };
    }
}
