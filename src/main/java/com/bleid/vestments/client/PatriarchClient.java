package com.bleid.vestments.client;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.patriarch.Mana;
import com.bleid.vestments.patriarch.PatriarchAbilities;
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
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Клиент маны и способностей Патриарха: полоска маны (10 кристаллов) над шкалой голода,
 * иконки трёх способностей с перезарядкой справа внизу, клавиши Z / X / C.
 */
@Environment(EnvType.CLIENT)
public final class PatriarchClient {
    private static final Identifier MANA_TEX = new Identifier(Vestments.MOD_ID, "textures/gui/mana.png");
    // X и C в игре заняты (загрузить/сохранить панель), поэтому Z, V, B
    private static final int[] KEYS = { GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B };
    private static final KeyBinding[] BINDINGS = new KeyBinding[PatriarchAbilities.COUNT];

    private static boolean synced, learned;
    private static float mana, max = Mana.MAX;
    private static final int[] cooldown = new int[PatriarchAbilities.COUNT];

    private PatriarchClient() { }

    public static void register() {
        for (int i = 0; i < PatriarchAbilities.COUNT; i++) {
            BINDINGS[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.vestments.patriarch_" + PatriarchAbilities.IDS[i], InputUtil.Type.KEYSYM, KEYS[i], "key.categories.vestments"));
        }
        ClientPlayNetworking.registerGlobalReceiver(Mana.SYNC, (client, handler, buf, sender) -> {
            float m = buf.readFloat(), mx = buf.readFloat();
            boolean l = buf.readBoolean();
            int[] cd = new int[PatriarchAbilities.COUNT];
            for (int i = 0; i < cd.length; i++) cd[i] = buf.readInt();
            client.execute(() -> {
                mana = m; max = mx; learned = l; synced = true;
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
                    ClientPlayNetworking.send(PatriarchAbilities.CAST, buf);
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

        boolean patriarch = com.bleid.vestments.service.RankView.clientRank() >= PatriarchAbilities.REQUIRED_RANK;
        if (!learned && !patriarch) return;
        // иконки способностей Патриарха — в ряд слева от иконки способности посоха, как у посоха
        TextRenderer font = client.textRenderer;
        int size = 24, margin = 6, gap = 6;
        for (int i = 0; i < PatriarchAbilities.COUNT; i++) {
            int slot = PatriarchAbilities.COUNT - i;            // справа — место иконки посоха
            int x = w - margin - size - slot * (size + gap);
            int iy = h - margin - size;
            Identifier icon = new Identifier(Vestments.MOD_ID, "textures/gui/" + PatriarchAbilities.IDS[i] + ".png");
            ctx.drawTexture(icon, x, iy, 0, 0, size, size, size, size);
            boolean noMana = mana < PatriarchAbilities.MANA[i];
            if (!learned) {                                      // Патриарх, но книга ещё не прочитана
                ctx.fill(x, iy, x + size, iy + size, 0xC0000000);
                ctx.drawText(font, "?", x + size / 2 - font.getWidth("?") / 2, iy + size / 2 - 4, 0xFFFFD24A, true);
            } else if (cooldown[i] > 0) {
                float f = cooldown[i] / (float) PatriarchAbilities.COOLDOWN[i];
                ctx.fill(x, iy, x + size, iy + MathHelper.ceil(size * f), 0xB0000000);
                String sec = String.valueOf(MathHelper.ceil(cooldown[i] / 20f));
                ctx.drawText(font, sec, x + size / 2 - font.getWidth(sec) / 2, iy + size / 2 - 4, 0xFFFFFFFF, true);
            } else if (noMana) {
                ctx.fill(x, iy, x + size, iy + size, 0x803060C0);
            }
            Text key = BINDINGS[i].getBoundKeyLocalizedText();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + size / 2f, iy - 7f, 0);
            ctx.getMatrices().scale(0.75f, 0.75f, 1f);
            int col = cooldown[i] > 0 || noMana ? 0xFFAAAAAA : 0xFFFFD24A;
            ctx.drawText(font, key, -font.getWidth(key) / 2, 0, col, true);
            ctx.getMatrices().pop();
        }
    }
}
