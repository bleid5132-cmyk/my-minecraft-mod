package com.bleid.vestments.church;

import com.bleid.vestments.Vestments;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerType;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.minecraft.world.poi.PointOfInterest;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestTypes;

/**
 * Церкви в деревнях. Деревня находится по колоколу (место сбора жителей). Решение принимается
 * один раз на деревню: в обычной деревне церковь появляется с шансом CHANCE; в заброшенной
 * (одни зомби-жители) — с тем же шансом её разрушенные остатки. Стиль — по типу жителей биома.
 */
public final class ChurchSpawner {
    private static final float CHANCE = 0.65f;
    private static final int CHECK_INTERVAL = 100;
    private static final int SEARCH_RADIUS = 64;

    private ChurchSpawner() { }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CHECK_INTERVAL != 0) return;
            ServerWorld world = server.getWorld(World.OVERWORLD);
            if (world == null) return;
            for (ServerPlayerEntity player : world.getPlayers()) {
                List<BlockPos> bells = world.getPointOfInterestStorage()
                        .getInSquare(t -> t.matchesKey(PointOfInterestTypes.MEETING), player.getBlockPos(),
                                SEARCH_RADIUS, PointOfInterestStorage.OccupationStatus.ANY)
                        .map(PointOfInterest::getPos).toList();
                for (BlockPos bell : bells) tryVillage(world, bell);
            }
        });
    }

    private static void tryVillage(ServerWorld world, BlockPos bell) {
        State state = state(world);
        if (state.done.contains(bell.asLong())) return;
        ChunkPos c = new ChunkPos(bell);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (!world.isChunkLoaded(c.x + dx, c.z + dz)) return;      // ждём прогрузки деревни
            }
        }
        // одна деревня — одно решение: помечаем все колокола рядом
        world.getPointOfInterestStorage().getInSquare(t -> t.matchesKey(PointOfInterestTypes.MEETING), bell, 64,
                PointOfInterestStorage.OccupationStatus.ANY).forEach(p -> state.done.add(p.getPos().asLong()));
        state.done.add(bell.asLong());
        state.markDirty();

        Random random = Random.create(world.getSeed() ^ bell.asLong() * 31L);
        if (random.nextFloat() >= CHANCE) return;

        Box area = new Box(bell).expand(48, 24, 48);
        int villagers = world.getEntitiesByClass(VillagerEntity.class, area, e -> true).size();
        int zombies = world.getEntitiesByClass(ZombieVillagerEntity.class, area, e -> true).size();
        boolean abandoned = zombies > 0 && villagers == 0;
        VillagerType type = VillagerType.forBiome(world.getBiome(bell));

        for (int radius = 12; radius <= 40; radius += 2) {
            int start = random.nextInt(24);
            for (int k = 0; k < 24; k++) {
                double a = (start + k) * Math.PI * 2 / 24;
                BlockPos center = bell.add((int) Math.round(Math.cos(a) * radius), 0, (int) Math.round(Math.sin(a) * radius));
                Direction front = Direction.getFacing(bell.getX() - center.getX(), 0, bell.getZ() - center.getZ());
                if (front.getAxis().isVertical()) front = Direction.NORTH;
                Integer floor = ChurchBuilder.siteFloor(world, center, front);
                if (floor == null || Math.abs(floor - bell.getY()) > 8) continue;
                ChurchBuilder.build(world, center, floor, front, type, abandoned, random);
                Vestments.LOGGER.info("{} церковь поставлена у {}", abandoned ? "Разрушенная" : "Новая", center.withY(floor));
                return;
            }
        }
    }

    private static State state(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(State::fromNbt, State::new, "vestments_churches");
    }

    public static final class State extends PersistentState {
        final Set<Long> done = new HashSet<>();

        static State fromNbt(NbtCompound nbt) {
            State s = new State();
            for (long l : nbt.getLongArray("bells")) s.done.add(l);
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            nbt.putLongArray("bells", done.stream().mapToLong(Long::longValue).toArray());
            return nbt;
        }
    }
}
