package com.bleid.vestments.client;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.patriarch.BibleAbilities;
import com.bleid.vestments.patriarch.Mana;
import com.bleid.vestments.service.Ranks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Клиент Библии: полоска маны над шкалой голода, счётчик морали справа сверху,
 * иконки трёх способностей текущей степени справа внизу (Z, V — обычные, B — ульта;
 * Shift + B — заново выбрать души).
 */
@Environment(EnvType.CLIENT)
public final class PatriarchClient {
    private static final Identifier MANA_TEX = new Identifier(Vestments.MOD_ID, "textures/gui/mana.png");
    private static final Identifier MORALE_TEX = new Identifier(Vestments.MOD_ID, "textures/gui/morale.png");
    // X и C в игре заняты (загрузить/сохранить панель), поэтому Z, V, B
    private static final int[] KEYS = { GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B };
    private static final String[] KEY_IDS = { "slot1", "slot2", "ult" };
    private static final KeyBinding[] BINDINGS = new KeyBinding[BibleAbilities.SLOTS];

    private static boolean synced, learned, soulsChosen;
    private static float mana, max = Mana.MAX;
    private static int degree, unlocked, morale;
    private static final int[] cooldown = new int[BibleAbilities.SLOTS];

    private PatriarchClient() { }

    /** Текущая мана (для иконок способностей). */
    public static float mana() {
        return mana;
    }

    public static void register() {
        for (int i = 0; i < BibleAbilities.SLOTS; i++) {
            BINDINGS[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.vestments.bible_" + KEY_IDS[i], InputUtil.Type.KEYSYM, KEYS[i], "key.categories.vestments"));
        }
        ClientPlayNetworking.registerGlobalReceiver(Mana.SYNC, (client, handler, buf, sender) -> {
            float m = buf.readFloat(), mx = buf.readFloat();
            boolean l = buf.readBoolean();
            int deg = buf.readVarInt(), un = buf.readVarInt(), mor = buf.readVarInt();
            boolean ch = buf.readBoolean();
            int[] cd = new int[BibleAbilities.SLOTS];
            for (int i = 0; i < cd.length; i++) cd[i] = buf.readInt();
            client.execute(() -> {
                mana = m; max = mx; learned = l; synced = true;
                degree = deg; unlocked = un; morale = mor; soulsChosen = ch;
                System.arraycopy(cd, 0, cooldown, 0, cd.length);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> { synced = false; learned = false; });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (int i = 0; i < cooldown.length; i++) if (cooldown[i] > 0) cooldown[i]--;
            if (client.player == null || client.currentScreen != null) return;
            for (int i = 0; i < BINDINGS.length; i++) {
                while (BINDINGS[i].wasPressed()) {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeVarInt(i);
                    buf.writeBoolean(i == 2 && Screen.hasShiftDown());
                    ClientPlayNetworking.send(BibleAbilities.CAST, buf);
                }
            }
        });
        HudRenderCallback.EVENT.register(PatriarchClient::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (!synced || player == null || client.options.hudHidden || client.interactionManager == null
                || player.isSpectator()) return;
        boolean bars = client.interactionManager.hasStatusBars();     // в творческом шкал голода нет
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        TextRenderer font = client.textRenderer;

        // мана над голодом (как шкала голода — справа налево); если видны пузырьки воздуха — ещё выше
        int right = w / 2 + 91;
        int y = bars ? h - 39 - 10 : h - 39;
        if (player.isSubmergedInWater() || player.getAir() < player.getMaxAir()) y -= 10;
        if (player.getVehicle() != null) y -= 10;
        float perIcon = max / 10f;
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            float v = mana - i * perIcon;
            int u = v >= perIcon ? 18 : v >= perIcon / 2 ? 9 : 0;     // полный, половина, пустой
            ctx.drawTexture(MANA_TEX, x, y, u, 0, 9, 9, 27, 9);
        }

        renderMorale(ctx, font, w);
        if (!learned) return;

        // иконки способностей текущей степени — в ряд слева от иконки посоха
        int size = 24, margin = 6, gap = 6;
        for (int i = 0; i < BibleAbilities.SLOTS; i++) {
            String id = BibleAbilities.abilityAt(degree, i);
            int slot = BibleAbilities.SLOTS - i;
            int x = w - margin - size - slot * (size + gap);
            int iy = h - margin - size;
            if (i == 2) ctx.fill(x - 2, iy - 2, x + size + 2, iy + size + 2, 0xA08FC8FF);   // рамка ульты
            ctx.drawTexture(new Identifier(Vestments.MOD_ID, "textures/gui/" + id + ".png"), x, iy, 0, 0, size, size, size, size);
            boolean locked = i >= unlocked;
            boolean noMana = mana < BibleAbilities.manaCost(id);
            if (locked) {
                ctx.fill(x, iy, x + size, iy + size, 0xD0000000);
                ctx.drawText(font, "✖", x + size / 2 - font.getWidth("✖") / 2, iy + size / 2 - 4, 0xFF8C7A5A, true);
            } else if (cooldown[i] > 0) {
                float f = cooldown[i] / (float) BibleAbilities.cooldownTicks(id);
                ctx.fill(x, iy, x + size, iy + MathHelper.ceil(size * f), 0xB0000000);
                String sec = String.valueOf(MathHelper.ceil(cooldown[i] / 20f));
                ctx.drawText(font, sec, x + size / 2 - font.getWidth(sec) / 2, iy + size / 2 - 4, 0xFFFFFFFF, true);
            } else if (noMana || (i == 2 && morale < 10)) {
                ctx.fill(x, iy, x + size, iy + size, 0x803060C0);
            }
            Text key = locked ? Text.translatable(Ranks.nameKey(BibleAbilities.unlockRank(degree, i)))
                    : BINDINGS[i].getBoundKeyLocalizedText();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + size / 2f, iy - 7f, 0);
            float sc = locked ? 0.5f : 0.75f;
            ctx.getMatrices().scale(sc, sc, 1f);
            int col = locked ? 0xFF8C7A5A : cooldown[i] > 0 || noMana ? 0xFFAAAAAA : (i == 2 ? 0xFF8FC8FF : 0xFFFFD24A);
            ctx.drawText(font, key, -font.getWidth(key) / 2, 0, col, true);
            ctx.getMatrices().pop();
        }
    }

    /** Счётчик морали справа сверху: значок, число и полоса от красного к золотому. */
    private static void renderMorale(DrawContext ctx, TextRenderer font, int w) {
        int x = w - 96, y = 6;
        ctx.fill(x - 3, y - 3, x + 92, y + 21, 0x90180E06);
        ctx.fill(x - 3, y - 3, x + 92, y - 2, 0xFFC9A13B);
        ctx.fill(x - 3, y + 20, x + 92, y + 21, 0xFF8A6A2A);
        ctx.drawTexture(MORALE_TEX, x, y, 0, 0, 16, 16, 16, 16);
        Text t = Text.translatable("hud.vestments.morale", morale);
        ctx.drawText(font, t, x + 20, y, 0xFFF2E6C9, true);
        int bx = x + 20, bw = 66, by = y + 11;
        ctx.fill(bx, by, bx + bw, by + 4, 0xFF20140A);
        int fw = bw * morale / Mana.MORALE_MAX;
        float f = morale / (float) Mana.MORALE_MAX;
        int r = (int) MathHelper.lerp(f, 200, 255), g = (int) MathHelper.lerp(f, 60, 210), b = (int) MathHelper.lerp(f, 50, 74);
        if (fw > 0) ctx.fill(bx, by, bx + fw, by + 4, 0xFF000000 | (r << 16) | (g << 8) | b);
        for (int k = 1; k < 10; k++) ctx.fill(bx + bw * k / 10, by, bx + bw * k / 10 + 1, by + 4, 0x60000000);
    }
}
