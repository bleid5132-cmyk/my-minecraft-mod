package com.bleid.vestments.client;

import com.bleid.vestments.classes.Dodge;
import com.bleid.vestments.client.combat.CombatClient;
import com.bleid.vestments.paladin.combat.Movesets;
import com.bleid.vestments.service.RankView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Уворот (Left Alt): кувырок в сторону, куда жмёшь W/A/S/D (без направления — отскок назад).
 * Движение применяется сразу на клиенте (отзывчиво), сервер даёт короткую неуязвимость.
 */
@Environment(EnvType.CLIENT)
public final class DodgeClient {
    private static KeyBinding key;
    private static int cooldown;

    private DodgeClient() { }

    public static void register() {
        key = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.vestments.dodge", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT, "key.categories.vestments"));
        ClientTickEvents.END_CLIENT_TICK.register(DodgeClient::tick);
    }

    private static void tick(MinecraftClient mc) {
        if (cooldown > 0) cooldown--;
        ClientPlayerEntity p = mc.player;
        while (key.wasPressed()) {
            if (p == null || mc.currentScreen != null || cooldown > 0 || !Dodge.allowed(RankView.classOf(p))) continue;
            if (!p.isOnGround() || p.isUsingItem() || p.hasVehicle() || p.isSwimming() || p.getAbilities().flying) continue;
            float fw = p.input.movementForward, sw = p.input.movementSideways;
            int dir;
            if (Math.abs(sw) > Math.abs(fw) && Math.abs(sw) > 0.1f) dir = sw > 0 ? 2 : 3;   // A — влево, D — вправо
            else if (fw > 0.1f) dir = 0;
            else dir = 1;
            double yaw = Math.toRadians(p.getYaw());
            Vec3d f = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw)), r = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
            Vec3d d = switch (dir) { case 0 -> f; case 1 -> f.multiply(-1); case 2 -> r.multiply(-1); default -> r; };
            double speed = dir == 1 ? 0.85 : 1.0;
            p.setVelocity(d.x * speed, dir == 1 ? 0.28 : 0.12, d.z * speed);
            CombatClient.playLocal(p, Movesets.DODGE[dir]);
            p.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.8f);
            p.playSound(SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0.6f, 1.4f);
            cooldown = Dodge.COOLDOWN;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(dir);
            ClientPlayNetworking.send(Dodge.PACKET, buf);
        }
    }
}
