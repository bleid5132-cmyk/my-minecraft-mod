package com.bleid.vestments.patriarch;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.service.Ranks;
import com.bleid.vestments.service.ServicePoints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * Мана священника (до MAX, сама восполняется) и всё, что хранит Библия: прочитана ли она,
 * мораль (0–100), каких врагов священник одолел и чьи души выбрал для ульты.
 * Клиенту всё это шлётся одним пакетом SYNC.
 */
public final class Mana {
    public static final Identifier SYNC = new Identifier(Vestments.MOD_ID, "mana_sync");
    public static final float MAX = 100f;
    public static final float REGEN_PER_SEC = 2f;
    public static final int MORALE_MAX = 100;
    public static final int MAX_CHOSEN = 3;

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

    /** Пакет: мана, максимум, прочитана ли Библия, степень, сколько способностей открыто,
     *  мораль, выбраны ли души, затем перезарядки трёх ячеек (тики). */
    public static void sync(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        int rank = ServicePoints.rank(p);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(get(p));
        buf.writeFloat(MAX);
        buf.writeBoolean(learned(server, p.getUuid()));
        buf.writeVarInt(Ranks.degreeOf(rank));
        buf.writeVarInt(BibleAbilities.unlocked(rank));
        buf.writeVarInt(morale(server, p.getUuid()));
        buf.writeBoolean(!chosen(server, p.getUuid()).isEmpty());
        for (int i = 0; i < BibleAbilities.SLOTS; i++) buf.writeInt(BibleAbilities.cooldownLeft(p, i));
        ServerPlayNetworking.send(p, SYNC, buf);
    }

    // ---------------------------------------------------------- Библия

    public static boolean learned(MinecraftServer server, UUID id) {
        return state(server).learned.contains(id);
    }

    public static void learn(MinecraftServer server, UUID id) {
        State s = state(server);
        s.learned.add(id);
        s.markDirty();
    }

    // ---------------------------------------------------------- мораль

    public static int morale(MinecraftServer server, UUID id) {
        return state(server).morale.getOrDefault(id, 0);
    }

    public static void addMorale(MinecraftServer server, UUID id, int delta) {
        State s = state(server);
        s.morale.put(id, MathHelper.clamp(morale(server, id) + delta, 0, MORALE_MAX));
        s.markDirty();
    }

    // ---------------------------------------------------------- побеждённые враги и выбранные души

    public static Set<String> killed(MinecraftServer server, UUID id) {
        return state(server).killed.getOrDefault(id, Set.of());
    }

    public static void recordKill(MinecraftServer server, UUID id, String type) {
        State s = state(server);
        if (s.killed.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(type)) s.markDirty();
    }

    public static List<String> chosen(MinecraftServer server, UUID id) {
        return state(server).chosen.getOrDefault(id, List.of());
    }

    public static void choose(MinecraftServer server, UUID id, List<String> types) {
        State s = state(server);
        s.chosen.put(id, new ArrayList<>(types));
        s.markDirty();
    }

    private static State state(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager()
                .getOrCreate(State::fromNbt, State::new, "vestments_patriarch");
    }

    public static final class State extends PersistentState {
        final Set<UUID> learned = new HashSet<>();
        final Map<UUID, Integer> morale = new HashMap<>();
        final Map<UUID, Set<String>> killed = new HashMap<>();
        final Map<UUID, List<String>> chosen = new HashMap<>();

        static State fromNbt(NbtCompound nbt) {
            State s = new State();
            NbtList l = nbt.getList("learned", 8);
            for (int i = 0; i < l.size(); i++) s.learned.add(UUID.fromString(l.getString(i)));
            NbtCompound mo = nbt.getCompound("morale");
            for (String k : mo.getKeys()) s.morale.put(UUID.fromString(k), mo.getInt(k));
            NbtCompound ki = nbt.getCompound("killed");
            for (String k : ki.getKeys()) {
                Set<String> set = new LinkedHashSet<>();
                NbtList li = ki.getList(k, 8);
                for (int i = 0; i < li.size(); i++) set.add(li.getString(i));
                s.killed.put(UUID.fromString(k), set);
            }
            NbtCompound ch = nbt.getCompound("chosen");
            for (String k : ch.getKeys()) {
                List<String> list = new ArrayList<>();
                NbtList li = ch.getList(k, 8);
                for (int i = 0; i < li.size(); i++) list.add(li.getString(i));
                s.chosen.put(UUID.fromString(k), list);
            }
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList l = new NbtList();
            learned.forEach(u -> l.add(NbtString.of(u.toString())));
            nbt.put("learned", l);
            NbtCompound mo = new NbtCompound();
            morale.forEach((u, v) -> mo.putInt(u.toString(), v));
            nbt.put("morale", mo);
            NbtCompound ki = new NbtCompound();
            killed.forEach((u, set) -> {
                NbtList li = new NbtList();
                set.forEach(t -> li.add(NbtString.of(t)));
                ki.put(u.toString(), li);
            });
            nbt.put("killed", ki);
            NbtCompound ch = new NbtCompound();
            chosen.forEach((u, list) -> {
                NbtList li = new NbtList();
                list.forEach(t -> li.add(NbtString.of(t)));
                ch.put(u.toString(), li);
            });
            nbt.put("chosen", ch);
            return nbt;
        }
    }
}
