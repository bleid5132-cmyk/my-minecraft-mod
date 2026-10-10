package com.bleid.vestments.client;

import com.bleid.vestments.paladin.PaladinAbilities;
import com.bleid.vestments.service.RankView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;

/** Клавиши магии паладина: G — Наложение рук, H — Вызов, J — Аура, K — Рывок, N — Ангельские крылья. */
@Environment(EnvType.CLIENT)
public final class PaladinKeys {
    private static final int[] KEYS = { GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_N };
    private static final KeyBinding[] BINDINGS = new KeyBinding[KEYS.length];

    private PaladinKeys() { }

    public static KeyBinding binding(int i) {
        return BINDINGS[i];
    }

    public static void register() {
        for (int i = 0; i < KEYS.length; i++) {
            BINDINGS[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.vestments.paladin_" + PaladinAbilities.NAMES[i],
                    InputUtil.Type.KEYSYM, KEYS[i], "key.categories.vestments"));
        }
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            for (int i = 0; i < BINDINGS.length; i++) {
                while (BINDINGS[i].wasPressed()) {
                    if (mc.player == null || mc.currentScreen != null || !RankView.clientPaladin()) continue;
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeVarInt(i);
                    ClientPlayNetworking.send(PaladinAbilities.ABILITY, buf);
                }
            }
        });
    }
}
