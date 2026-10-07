package com.bleid.vestments.church;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerType;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;

/**
 * Украшает деревню один раз: фонари на столбах вдоль улиц, клумбы с цветами, кусты,
 * бочки и сено у дорог. Ставит только на свободную естественную землю рядом с дорогой,
 * ничего не ломает и не закрывает проходы.
 */
public final class VillageDecorator {
    static final int RADIUS = 80;
    private static final int LAMP_SPACING = 11;
    private static final int MAX_LAMPS = 40;

    private VillageDecorator() { }

    /** Готовы ли чанки вокруг колокола. */
    static boolean ready(ServerWorld world, BlockPos bell) {
        ChunkPos c = new ChunkPos(bell);
        int r = (RADIUS >> 4) + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!world.isChunkLoaded(c.x + dx, c.z + dz)) return false;
            }
        }
        return true;
    }

    public static void decorate(ServerWorld world, BlockPos bell, VillagerType type, boolean abandoned) {
        Random rnd = Random.create(world.getSeed() ^ bell.asLong() * 7919L);
        // дороги деревни
        Set<Long> path = new HashSet<>();
        List<BlockPos> pathList = new ArrayList<>();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                int wx = bell.getX() + x, wz = bell.getZ() + z;
                int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
                BlockPos p = new BlockPos(wx, top, wz);
                if (world.getBlockState(p).isOf(Blocks.DIRT_PATH)) {
                    path.add(p.asLong());
                    pathList.add(p);
                }
            }
        }
        if (pathList.isEmpty()) return;

        Block post = postBlock(type);
        List<BlockPos> lamps = new ArrayList<>();
        // перемешиваем, чтобы фонари шли по всей деревне
        for (int i = pathList.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            BlockPos t = pathList.get(i);
            pathList.set(i, pathList.get(j));
            pathList.set(j, t);
        }
        for (BlockPos p : pathList) {
            for (Direction d : Direction.Type.HORIZONTAL) {
                BlockPos g = p.offset(d);
                if (!isGroundNextToPath(world, g, p.getY())) continue;
                if (path.contains(g.asLong())) continue;

                if (!abandoned && lamps.size() < MAX_LAMPS && farFrom(lamps, g, LAMP_SPACING) && clearAround(world, g.up(), 3)) {
                    world.setBlockState(g.up(), post.getDefaultState(), Block.NOTIFY_ALL);
                    world.setBlockState(g.up(2), post.getDefaultState(), Block.NOTIFY_ALL);
                    world.setBlockState(g.up(3), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false), Block.NOTIFY_ALL);
                    lamps.add(g);
                    continue;
                }
                float f = rnd.nextFloat();
                BlockState above = world.getBlockState(g.up());
                if (!above.isAir() && !above.isReplaceable()) continue;
                if (!clearAround(world, g.up(), 1)) continue;
                BlockState ground = world.getBlockState(g);
                boolean soil = ground.isOf(Blocks.GRASS_BLOCK) || ground.isOf(Blocks.DIRT) || ground.isOf(Blocks.PODZOL);
                if (!soil && f < 0.13f) continue;
                if (f < 0.10f && flowersGrow(type)) {
                    world.setBlockState(g.up(), flower(rnd, type).getDefaultState(), Block.NOTIFY_ALL);
                } else if (f < 0.13f && !abandoned) {
                    world.setBlockState(g.up(), bush(type).getDefaultState().with(LeavesBlock.PERSISTENT, true), Block.NOTIFY_ALL);
                } else if (f < 0.14f && !abandoned) {
                    world.setBlockState(g.up(), (rnd.nextBoolean() ? Blocks.BARREL : Blocks.HAY_BLOCK).getDefaultState(), Block.NOTIFY_ALL);
                } else if (f < 0.16f && abandoned) {
                    world.setBlockState(g.up(), Blocks.COBWEB.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
    }

    private static boolean isGroundNextToPath(ServerWorld world, BlockPos g, int pathY) {
        if (g.getY() != pathY) return false;
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, g.getX(), g.getZ()) - 1;
        if (top != pathY) return false;
        BlockState st = world.getBlockState(g);
        return st.isOf(Blocks.GRASS_BLOCK) || st.isOf(Blocks.DIRT) || st.isOf(Blocks.SAND) || st.isOf(Blocks.SNOW_BLOCK)
                || st.isOf(Blocks.PODZOL) || st.isOf(Blocks.COARSE_DIRT) || st.isOf(Blocks.RED_SAND);
    }

    /** Вокруг (3x3) на высоту h — только воздух и трава: не ставим вплотную к домам и заборам. */
    private static boolean clearAround(ServerWorld world, BlockPos base, int h) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy < h; dy++) {
                    BlockState st = world.getBlockState(base.add(dx, dy, dz));
                    if (!st.isAir() && !st.isReplaceable() && !st.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS)) return false;
                }
            }
        }
        return true;
    }

    private static boolean farFrom(List<BlockPos> list, BlockPos p, int dist) {
        for (BlockPos q : list) if (q.getSquaredDistance(p) < dist * dist) return false;
        return true;
    }

    private static Block postBlock(VillagerType type) {
        if (type == VillagerType.DESERT) return Blocks.SANDSTONE_WALL;
        if (type == VillagerType.SAVANNA) return Blocks.ACACIA_FENCE;
        if (type == VillagerType.TAIGA || type == VillagerType.SNOW) return Blocks.SPRUCE_FENCE;
        return Blocks.OAK_FENCE;
    }

    private static boolean flowersGrow(VillagerType type) {
        return type != VillagerType.DESERT;
    }

    private static Block flower(Random r, VillagerType type) {
        Block[] cold = { Blocks.FERN, Blocks.FERN, Blocks.LILY_OF_THE_VALLEY, Blocks.BLUE_ORCHID };
        Block[] warm = { Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET,
                Blocks.ALLIUM, Blocks.LILY_OF_THE_VALLEY, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.PINK_TULIP };
        Block[] savanna = { Blocks.DANDELION, Blocks.POPPY, Blocks.ORANGE_TULIP, Blocks.OXEYE_DAISY };
        Block[] set = type == VillagerType.TAIGA || type == VillagerType.SNOW ? cold
                : type == VillagerType.SAVANNA ? savanna : warm;
        return set[r.nextInt(set.length)];
    }

    private static Block bush(VillagerType type) {
        if (type == VillagerType.TAIGA || type == VillagerType.SNOW) return Blocks.SPRUCE_LEAVES;
        if (type == VillagerType.SAVANNA) return Blocks.ACACIA_LEAVES;
        return Blocks.OAK_LEAVES;
    }

    /** Деревни, которые уже украшены. */
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

    static State state(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(State::fromNbt, State::new, "vestments_village_decor");
    }
}
