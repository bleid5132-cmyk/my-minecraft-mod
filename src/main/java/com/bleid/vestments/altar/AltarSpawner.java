package com.bleid.vestments.altar;

import com.bleid.vestments.Vestments;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestTypes;

/**
 * Ставит Священный алтарь в каждой деревне жителей: рядом с колоколом (место сбора жителей),
 * один раз на колокол. Деревни проверяются вокруг игроков раз в 5 секунд.
 */
public final class AltarSpawner {
    private static final int CHECK_INTERVAL = 100;
    private static final int SEARCH_RADIUS = 64;

    private AltarSpawner() { }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CHECK_INTERVAL != 0) return;
            ServerWorld world = server.getWorld(World.OVERWORLD);
            if (world == null) return;
            for (ServerPlayerEntity player : world.getPlayers()) {
                world.getPointOfInterestStorage()
                        .getInSquare(type -> type.matchesKey(PointOfInterestTypes.MEETING), player.getBlockPos(),
                                SEARCH_RADIUS, PointOfInterestStorage.OccupationStatus.ANY)
                        .map(poi -> poi.getPos())
                        .toList()
                        .forEach(bell -> tryPlace(world, bell));
            }
        });
    }

    private static void tryPlace(ServerWorld world, BlockPos bell) {
        State state = state(world);
        long key = bell.asLong();
        if (state.done.contains(key)) return;
        // ждём, пока вокруг колокола прогрузятся чанки
        ChunkPos c = new ChunkPos(bell);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!world.isChunkLoaded(c.x + dx, c.z + dz)) return;
            }
        }
        state.done.add(key);
        state.markDirty();

        Random random = Random.create(key);
        for (int radius = 3; radius <= 12; radius++) {
            int start = random.nextInt(8);
            for (int k = 0; k < 8; k++) {
                double a = (start + k) * Math.PI / 4.0;
                int x = bell.getX() + (int) Math.round(Math.cos(a) * radius);
                int z = bell.getZ() + (int) Math.round(Math.sin(a) * radius);
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                if (Math.abs(y - bell.getY()) > 3) continue;
                if (!canPlace(world, pos)) continue;
                Direction facing = Direction.getFacing(bell.getX() - x, 0, bell.getZ() - z);
                if (facing.getAxis().isVertical()) facing = Direction.NORTH;
                BlockState altar = Vestments.HOLY_ALTAR.getDefaultState().with(HorizontalFacingBlock.FACING, facing);
                world.setBlockState(pos, altar);
                Vestments.LOGGER.info("Священный алтарь поставлен в деревне у {}", pos);
                return;
            }
        }
    }

    private static boolean canPlace(ServerWorld world, BlockPos pos) {
        BlockPos ground = pos.down();
        BlockState g = world.getBlockState(ground);
        if (!g.isSideSolidFullSquare(world, ground, Direction.UP)) return false;
        if (!g.getFluidState().isEmpty()) return false;
        for (int i = 0; i <= 2; i++) {
            BlockState s = world.getBlockState(pos.up(i));
            if (!(s.isAir() || (s.isReplaceable() && s.getFluidState().isEmpty()))) return false;
        }
        // не ставим вплотную к стенам и в проходах между домами
        for (Direction d : Direction.Type.HORIZONTAL) {
            if (!world.getBlockState(pos.offset(d)).getCollisionShape(world, pos.offset(d)).isEmpty()) return false;
            if (world.getFluidState(pos.offset(d).down()).isIn(FluidTags.WATER)) return false;
        }
        return true;
    }

    private static State state(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(State::fromNbt, State::new, "vestments_altars");
    }

    /** Колокола, у которых уже стоит (или пытались поставить) алтарь. */
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
