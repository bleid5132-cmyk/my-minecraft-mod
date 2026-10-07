package com.bleid.vestments.patriarch;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.service.ServicePoints;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * Мана священника: до MAX, сама восполняется со временем. Хранится на сервере, клиенту шлётся
 * вместе с изученностью книги и перезарядками способностей Патриарха (пакет SYNC).
 */
public final class Mana {
    public static final Identifier SYNC = new Identifier(Vestments.MOD_ID, "mana_sync");
    public static final float MAX = 100f;
    /** Восполнение в секунду: полная шкала за 50 секунд. */
    public static final float REGEN_PER_SEC = 2f;

    private static final Map<UUID, Float> MANA = new HashMap<>();

    private Mana() { }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((h, s, server) -> MANA.put(h.getPlayer().getUuid(), MAX));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (!ServicePoints.canServe(p)) continue;
                float m = get(p);
                if (m < MAX) MANA.put(p.getUuid(), Math.min(MAX, m + REGEN_PER_SEC / 20f));
                if (server.getTicks() % 5 == 0) sync(p);
            }
        });
    }

    public static float get(ServerPlayerEntity p) {
        return MANA.getOrDefault(p.getUuid(), MAX);
    }

    public static boolean spend(ServerPlayerEntity p, float amount) {
        float m = get(p);
        if (m < amount) return false;
        MANA.put(p.getUuid(), m - amount);
        return true;
    }

    public static void sync(ServerPlayerEntity p) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(get(p));
        buf.writeFloat(MAX);
        buf.writeBoolean(learned(p.getServer(), p.getUuid()));
        for (int i = 0; i < PatriarchAbilities.COUNT; i++) buf.writeInt(PatriarchAbilities.cooldownLeft(p, i));
        ServerPlayNetworking.send(p, SYNC, buf);
    }

    // ---------------------------------------------------------- изучена ли Книга Патриарха

    public static boolean learned(MinecraftServer server, UUID id) {
        return state(server).learned.contains(id);
    }

    public static void learn(MinecraftServer server, UUID id) {
        State s = state(server);
        s.learned.add(id);
        s.markDirty();
    }

    private static State state(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager()
                .getOrCreate(State::fromNbt, State::new, "vestments_patriarch");
    }

    public static final class State extends PersistentState {
        final Set<UUID> learned = new HashSet<>();

        static State fromNbt(NbtCompound nbt) {
            State s = new State();
            NbtList l = nbt.getList("learned", 8);
            for (int i = 0; i < l.size(); i++) s.learned.add(UUID.fromString(l.getString(i)));
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList l = new NbtList();
            learned.forEach(u -> l.add(NbtString.of(u.toString())));
            nbt.put("learned", l);
            return nbt;
        }
    }
}
