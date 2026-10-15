package com.bleid.vestments.horse;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

/** Рывок коня на CTRL: клиент сообщает, зажата ли клавиша бега; скорость считается на обеих сторонах одинаково. */
public final class HorseSprint {
    private HorseSprint() { }

    public static final Identifier PACKET = new Identifier("vestments", "horse_sprint");
    /** Во сколько раз быстрее с CTRL (без повозки) и медленнее с повозкой. */
    public static final float BOOST = 1.45f, CART = 0.82f;
    private static final Set<UUID> SERVER = new HashSet<>();
    /** На клиенте: зажата ли клавиша бега у своего игрока. */
    public static volatile boolean client;

    public static boolean sprinting(net.minecraft.entity.player.PlayerEntity p) {
        return p.getWorld().isClient ? client : SERVER.contains(p.getUuid());
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(PACKET, (server, player, handler, buf, sender) -> {
            boolean on = buf.readBoolean();
            server.execute(() -> {
                if (on) SERVER.add(player.getUuid());
                else SERVER.remove(player.getUuid());
            });
        });
    }
}
