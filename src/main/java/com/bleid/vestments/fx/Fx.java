package com.bleid.vestments.fx;

import com.bleid.vestments.Vestments;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Эффекты способностей: сервер шлёт одну команду «сыграй эффект X здесь», а вся анимация
 * (кольца, столпы света, искры, перья, руны) рисуется на клиенте — плавно и детально.
 */
public final class Fx {
    public static final Identifier PACKET = new Identifier(Vestments.MOD_ID, "fx");
    private static final double RANGE = 96;

    private Fx() { }

    /**
     * @param follow сущность, за которой эффект следует (или null)
     * @param dur    длительность в тиках (для длительных эффектов)
     * @param a,b,c  параметры эффекта (радиус, направление и т.п.)
     */
    public static void play(ServerWorld w, String type, Vec3d pos, Entity follow, int dur, float a, float b, float c) {
        for (ServerPlayerEntity p : PlayerLookup.around(w, pos, RANGE)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(type);
            buf.writeDouble(pos.x);
            buf.writeDouble(pos.y);
            buf.writeDouble(pos.z);
            buf.writeVarInt(follow != null ? follow.getId() : -1);
            buf.writeVarInt(dur);
            buf.writeFloat(a);
            buf.writeFloat(b);
            buf.writeFloat(c);
            ServerPlayNetworking.send(p, PACKET, buf);
        }
    }

    public static void play(ServerWorld w, String type, Entity e, int dur, float a) {
        play(w, type, e.getPos(), e, dur, a, 0, 0);
    }
}
