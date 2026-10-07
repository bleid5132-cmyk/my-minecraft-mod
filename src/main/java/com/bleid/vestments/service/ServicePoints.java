package com.bleid.vestments.service;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.classes.PlayerClasses;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * Очки служения священника. Хранятся в данных мира, не в инвентаре.
 * Источники: убийство любых враждебных мобов и подношения Священному алтарю.
 * Сервер сразу начисляет очки и шлёт клиентам пакет «огоньков» — клиент рисует,
 * как они летят к игроку, и прибавляет их к шкале в момент касания.
 */
public final class ServicePoints {
    /** Полная синхронизация: boolean священник ли, int очки. */
    public static final Identifier SYNC = new Identifier(Vestments.MOD_ID, "service_sync");
    /** Огоньки: int id цели, double x, y, z откуда летят, int сколько очков, int новый итог (-1 для чужих). */
    public static final Identifier ORBS = new Identifier(Vestments.MOD_ID, "service_orbs");
    /** Душа убитого священником моба: int id сущности, long зерно случайности. */
    public static final Identifier SOUL = new Identifier(Vestments.MOD_ID, "soul");

    private ServicePoints() { }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer()));

        // убийство любого враждебного моба
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof Monster)) return;
            if (!(source.getAttacker() instanceof ServerPlayerEntity player)) return;
            int pts = pointsForKill(entity);
            add(player, pts, entity.getPos().add(0, entity.getHeight() * 0.6, 0));
            if (canServe(player)) {
                // душа убитого моба: клиенты рядом рисуют её полёт в небо
                for (ServerPlayerEntity viewer : player.getServerWorld().getPlayers()) {
                    if (viewer.squaredDistanceTo(entity) > 96 * 96) continue;
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeVarInt(entity.getId());
                    buf.writeLong(entity.getUuid().getLeastSignificantBits());
                    ServerPlayNetworking.send(viewer, SOUL, buf);
                }
            }
        });

        // команды для проверки: /vestments points add|set <n>
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(
                CommandManager.literal("vestments").requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("points")
                                .then(CommandManager.literal("add")
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 100000))
                                                .executes(ctx -> {
                                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                                    Vec3d from = p.getEyePos().add(p.getRotationVec(1f).multiply(2.5));
                                                    add(p, IntegerArgumentType.getInteger(ctx, "amount"), from);
                                                    return 1;
                                                })))
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 1000000))
                                                .executes(ctx -> {
                                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                                    set(p.getServer(), p.getUuid(), IntegerArgumentType.getInteger(ctx, "amount"));
                                                    sync(p);
                                                    return 1;
                                                }))))));
    }

    /** Очки за моба: чем крепче моб, тем больше (зомби — 4, эндермен — 8, иссушитель — 60). */
    public static int pointsForKill(LivingEntity entity) {
        return Math.max(1, MathHelper.ceil(entity.getMaxHealth() / 5f));
    }

    public static boolean canServe(ServerPlayerEntity player) {
        return "priest".equals(PlayerClasses.get(player.getServer(), player.getUuid()));
    }

    /** Начислить очки и показать огоньки, летящие из точки from к игроку. */
    public static void add(ServerPlayerEntity player, int amount, Vec3d from) {
        if (amount <= 0 || !canServe(player)) return;
        MinecraftServer server = player.getServer();
        int old = get(server, player.getUuid());
        int now = old + amount;
        set(server, player.getUuid(), now);

        for (ServerPlayerEntity viewer : player.getServerWorld().getPlayers()) {
            if (viewer != player && viewer.squaredDistanceTo(from) > 64 * 64) continue;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeInt(player.getId());
            buf.writeDouble(from.x);
            buf.writeDouble(from.y);
            buf.writeDouble(from.z);
            buf.writeInt(amount);
            buf.writeInt(viewer == player ? now : -1);
            ServerPlayNetworking.send(viewer, ORBS, buf);
        }

        int oldRank = Ranks.rankFor(old), newRank = Ranks.rankFor(now);
        if (newRank > oldRank) {
            Text msg = Text.translatable("message.vestments.rank_up", player.getDisplayName(),
                    Text.translatable(Ranks.nameKey(newRank)).formatted(Formatting.GOLD));
            server.getPlayerManager().broadcast(msg, false);
        }
    }

    public static void sync(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(canServe(player));
        buf.writeInt(get(player.getServer(), player.getUuid()));
        ServerPlayNetworking.send(player, SYNC, buf);
    }

    public static int get(MinecraftServer server, UUID player) {
        return state(server).points.getOrDefault(player, 0);
    }

    public static int rank(ServerPlayerEntity player) {
        return Ranks.rankFor(get(player.getServer(), player.getUuid()));
    }

    /** Множитель силы «Благословения» по сану; не священникам — 1. */
    public static float power(ServerPlayerEntity player) {
        return canServe(player) ? Ranks.power(rank(player)) : 1.0f;
    }

    private static void set(MinecraftServer server, UUID player, int points) {
        State s = state(server);
        s.points.put(player, points);
        s.markDirty();
    }

    private static State state(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager()
                .getOrCreate(State::fromNbt, State::new, "vestments_service");
    }

    public static final class State extends PersistentState {
        final Map<UUID, Integer> points = new HashMap<>();

        static State fromNbt(NbtCompound nbt) {
            State s = new State();
            NbtCompound map = nbt.getCompound("points");
            for (String key : map.getKeys()) s.points.put(UUID.fromString(key), map.getInt(key));
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            NbtCompound map = new NbtCompound();
            points.forEach((uuid, p) -> map.putInt(uuid.toString(), p));
            nbt.put("points", map);
            return nbt;
        }
    }
}
