package com.bleid.vestments.client;

import com.bleid.vestments.StaffOfLightItem;
import com.bleid.vestments.Vestments;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import com.bleid.vestments.service.RankView;
import com.bleid.vestments.weapon.LiturgicalWeaponItem;

/**
 * Иконка способности «Благословение» в правом нижнем углу экрана, пока Посох Света в руке:
 * готово — яркая иконка с подписью «ПКМ»; луч активен — золотая рамка и секунды луча;
 * перезарядка — затемнение сверху вниз и оставшиеся секунды.
 */
@Environment(EnvType.CLIENT)
public final class AbilityHud {
    private static final Identifier ICON = new Identifier(Vestments.MOD_ID, "textures/gui/blessing.png");
    private static final int SIZE = 24;
    private static final int MARGIN = 6;

    private AbilityHud() { }

    public static void register() {
        HudRenderCallback.EVENT.register(AbilityHud::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden || player.isSpectator()) return;
        boolean holding = player.getMainHandStack().isOf(Vestments.STAFF_OF_LIGHT)
                || player.getOffHandStack().isOf(Vestments.STAFF_OF_LIGHT);
        if (!holding) {
            renderWeapon(ctx, client, player, tickDelta);
            return;
        }

        TextRenderer font = client.textRenderer;
        int x = ctx.getScaledWindowWidth() - SIZE - MARGIN;
        int y = ctx.getScaledWindowHeight() - SIZE - MARGIN;

        boolean channeling = player.isUsingItem() && player.getActiveItem().isOf(Vestments.STAFF_OF_LIGHT);
        float cooldown = player.getItemCooldownManager().getCooldownProgress(Vestments.STAFF_OF_LIGHT, tickDelta);

        // подсветка при активном луче
        if (channeling) {
            ctx.fill(x - 2, y - 2, x + SIZE + 2, y + SIZE + 2, 0xCCFFD24A);
        }
        ctx.drawTexture(ICON, x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);

        if (channeling) {
            int left = MathHelper.ceil(player.getItemUseTimeLeft() / 20.0f);
            drawCentered(ctx, font, String.valueOf(left), x + SIZE / 2, y + SIZE / 2 - 4, 0xFFFFF4C0);
        } else if (cooldown > 0f) {
            int dark = MathHelper.ceil(SIZE * cooldown);
            ctx.fill(x, y, x + SIZE, y + dark, 0xB0000000);                    // затемнение сверху вниз
            int seconds = MathHelper.ceil(cooldown * StaffOfLightItem.BLESSING_COOLDOWN_TICKS / 20.0f);
            drawCentered(ctx, font, String.valueOf(seconds), x + SIZE / 2, y + SIZE / 2 - 4, 0xFFFFFFFF);
        }

        // подпись клавиши над иконкой
        Text key = Text.translatable("hud.vestments.blessing_key");
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + SIZE / 2.0f, y - 7.0f, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1.0f);
        int w = font.getWidth(key);
        ctx.drawText(font, key, -w / 2, 0, cooldown > 0f && !channeling ? 0xFFAAAAAA : 0xFFFFD24A, true);
        ctx.getMatrices().pop();
    }

    /** Иконка способности богослужебного оружия сана (кадило, рипида…). */
    private static void renderWeapon(DrawContext ctx, MinecraftClient client, ClientPlayerEntity player, float tickDelta) {
        LiturgicalWeaponItem weapon = null;
        if (player.getMainHandStack().getItem() instanceof LiturgicalWeaponItem w) weapon = w;
        else if (player.getOffHandStack().getItem() instanceof LiturgicalWeaponItem w) weapon = w;
        if (weapon == null) return;
        TextRenderer font = client.textRenderer;
        int x = ctx.getScaledWindowWidth() - SIZE - MARGIN;
        int y = ctx.getScaledWindowHeight() - SIZE - MARGIN;
        boolean allowed = RankView.clientRank() >= weapon.rank;
        float cooldown = player.getItemCooldownManager().getCooldownProgress(weapon, tickDelta);
        ctx.drawTexture(weapon.hudIcon, x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
        if (!allowed) {
            ctx.fill(x, y, x + SIZE, y + SIZE, 0xB0300000);
            drawCentered(ctx, font, "✖", x + SIZE / 2, y + SIZE / 2 - 4, 0xFFFF6060);
        } else if (cooldown <= 0f && PatriarchClient.mana() < weapon.manaCost) {
            ctx.fill(x, y, x + SIZE, y + SIZE, 0x803060C0);                  // не хватает маны
        } else if (cooldown > 0f) {
            int dark = MathHelper.ceil(SIZE * cooldown);
            ctx.fill(x, y, x + SIZE, y + dark, 0xB0000000);
            int seconds = MathHelper.ceil(cooldown * weapon.cooldownTicks / 20.0f);
            drawCentered(ctx, font, String.valueOf(seconds), x + SIZE / 2, y + SIZE / 2 - 4, 0xFFFFFFFF);
        }
        Text key = Text.translatable("hud.vestments.blessing_key");
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + SIZE / 2.0f, y - 7.0f, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1.0f);
        int w = font.getWidth(key);
        ctx.drawText(font, key, -w / 2, 0, cooldown > 0f || !allowed ? 0xFFAAAAAA : 0xFFFFD24A, true);
        ctx.getMatrices().pop();
    }

    private static void drawCentered(DrawContext ctx, TextRenderer font, String text, int cx, int y, int color) {
        ctx.drawText(font, text, cx - font.getWidth(text) / 2, y, color, true);
    }
}
