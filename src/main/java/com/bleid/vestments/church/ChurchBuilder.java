package com.bleid.vestments.church;

import com.bleid.vestments.Vestments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import net.minecraft.world.Heightmap;

/**
 * Небольшая православная церковь: сруб/кладка в стиле деревни, двускатная крыша,
 * барабан с луковичным куполом и крестом. Внутри — Священный алтарь, зельеварка,
 * свечи, лампады и житель-священник. Разрушенная версия — без купола, с проломами,
 * паутиной и без священника.
 *
 * Локальные координаты: x 0..6 (ширина), z 0..10 (длина), y 0 — пол. Вход смотрит на север (-z).
 */
public final class ChurchBuilder {
    public static final String PRIEST_TAG = "vestments_church_priest";
    private static final int W = 7, D = 11;
    private static final String[] PRIEST_NAMES = { "nikolai", "sergii", "ioann", "aleksii", "mikhail", "pavel", "andrei", "serafim" };

    enum Role { FOUNDATION, FLOOR, BASE, WALL, PILLAR, GLASS, ROOF, GABLE, DOME, CROSS, DECOR, DOOR, CARPET }

    private record Placed(int x, int y, int z, BlockState state, Role role) { }

    private final List<Placed> plan = new ArrayList<>();
    private final ChurchStyle s;

    private ChurchBuilder(ChurchStyle style) {
        this.s = style;
    }

    private void put(int x, int y, int z, BlockState st, Role r) {
        plan.removeIf(p -> p.x == x && p.y == y && p.z == z);
        plan.add(new Placed(x, y, z, st, r));
    }

    private void put(int x, int y, int z, Block b, Role r) {
        put(x, y, z, b.getDefaultState(), r);
    }

    private BlockState stairs(Direction facing) {
        return s.roofStairs().getDefaultState().with(StairsBlock.FACING, facing);
    }

    private void design() {
        // пол
        for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) put(x, 0, z, s.floor(), Role.FLOOR);
        // стены
        for (int y = 1; y <= 4; y++) {
            for (int x = 0; x < W; x++) {
                for (int z = 0; z < D; z++) {
                    boolean edge = x == 0 || x == W - 1 || z == 0 || z == D - 1;
                    if (!edge) continue;
                    boolean corner = (x == 0 || x == W - 1) && (z == 0 || z == D - 1 || z == 5);
                    if (corner) put(x, y, z, s.pillar().getDefaultState().withIfExists(PillarBlock.AXIS, Direction.Axis.Y), Role.PILLAR);
                    else put(x, y, z, y == 1 ? s.base() : s.wall(), y == 1 ? Role.BASE : Role.WALL);
                }
            }
        }
        // окна
        for (int z : new int[] { 2, 3, 7, 8 }) {
            for (int y = 2; y <= 3; y++) {
                put(0, y, z, Blocks.GLASS_PANE, Role.GLASS);
                put(W - 1, y, z, Blocks.GLASS_PANE, Role.GLASS);
            }
        }
        put(3, 2, D - 1, Blocks.GLASS_PANE, Role.GLASS);
        put(3, 3, D - 1, Blocks.GLASS_PANE, Role.GLASS);
        put(3, 4, 0, Blocks.GLASS_PANE, Role.GLASS);
        // дверь
        BlockState door = s.door().getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HINGE, DoorHinge.LEFT);
        put(3, 1, 0, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER), Role.DOOR);
        put(3, 2, 0, door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER), Role.DOOR);
        // фронтоны спереди и сзади
        for (int z : new int[] { 0, D - 1 }) {
            for (int x = 1; x <= 5; x++) put(x, 5, z, s.wall(), Role.GABLE);
            for (int x = 2; x <= 4; x++) put(x, 6, z, s.wall(), Role.GABLE);
            put(3, 7, z, s.wall(), Role.GABLE);
        }
        put(3, 6, 0, Blocks.GLASS_PANE, Role.GLASS);
        // крыша: скаты из ступеней с выносом, конёк из плиты
        for (int z = -1; z <= D; z++) {
            put(0, 5, z, s.roofBlock(), Role.ROOF);
            put(W - 1, 5, z, s.roofBlock(), Role.ROOF);
            for (int k = 0; k <= 3; k++) {
                put(-1 + k, 5 + k, z, stairs(Direction.EAST), Role.ROOF);
                put(7 - k, 5 + k, z, stairs(Direction.WEST), Role.ROOF);
            }
            put(3, 8, z, s.roofBlock(), Role.ROOF);
            put(3, 9, z, s.roofSlab().getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Role.ROOF);
        }
        // барабан и луковичный купол над входной частью
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                for (int y = 9; y <= 11; y++) put(x, y, z, s.drum(), Role.DOME);
                put(x, 12, z, s.dome(), Role.DOME);
                put(x, 13, z, s.dome(), Role.DOME);
            }
        }
        // «луковица» шире барабана
        put(1, 12, 3, s.dome(), Role.DOME);
        put(5, 12, 3, s.dome(), Role.DOME);
        put(3, 12, 1, s.dome(), Role.DOME);
        put(3, 12, 5, s.dome(), Role.DOME);
        put(3, 10, 2, Blocks.GLASS_PANE, Role.DOME);
        put(3, 10, 4, Blocks.GLASS_PANE, Role.DOME);
        put(2, 10, 3, Blocks.GLASS_PANE, Role.DOME);
        put(4, 10, 3, Blocks.GLASS_PANE, Role.DOME);
        put(3, 14, 3, s.dome(), Role.DOME);
        put(3, 15, 3, Blocks.GOLD_BLOCK.getDefaultState(), Role.DOME);
        // крест
        for (int y = 16; y <= 18; y++) put(3, y, 3, s.fence(), Role.CROSS);
        put(2, 17, 3, s.fence(), Role.CROSS);
        put(4, 17, 3, s.fence(), Role.CROSS);
        // внутри
        put(3, 1, D - 2, Vestments.HOLY_ALTAR.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH), Role.DECOR);
        put(1, 1, D - 2, Blocks.BREWING_STAND, Role.DECOR);
        put(5, 1, D - 2, Blocks.LECTERN.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH), Role.DECOR);
        BlockState candles = Blocks.CANDLE.getDefaultState().with(CandleBlock.CANDLES, 3).with(CandleBlock.LIT, true);
        put(2, 1, D - 2, candles, Role.DECOR);
        put(4, 1, D - 2, candles, Role.DECOR);
        put(1, 1, 1, Blocks.CANDLE.getDefaultState().with(CandleBlock.CANDLES, 2).with(CandleBlock.LIT, true), Role.DECOR);
        put(5, 1, 1, Blocks.CANDLE.getDefaultState().with(CandleBlock.CANDLES, 2).with(CandleBlock.LIT, true), Role.DECOR);
        put(3, 7, 3, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), Role.DECOR);
        put(3, 7, 7, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), Role.DECOR);
        for (int z = 1; z <= D - 3; z++) put(3, 1, z, Blocks.RED_CARPET, Role.CARPET);
    }

    /** Разрушенная и заброшенная: проломы, без купола и убранства, паутина, мох. */
    private void ruin(Random r) {
        List<Placed> out = new ArrayList<>();
        for (Placed p : plan) {
            BlockState st = p.state;
            switch (p.role) {
                case DOME, CROSS, DOOR, CARPET -> { continue; }
                case DECOR -> {
                    if (st.isOf(Vestments.HOLY_ALTAR) || st.isOf(Blocks.BREWING_STAND) || st.isOf(Blocks.LANTERN)) continue;
                    if (st.isOf(Blocks.CANDLE)) st = st.with(CandleBlock.LIT, false);
                    if (r.nextFloat() < 0.5f) continue;
                }
                case ROOF -> {
                    // над алтарной частью крыша обрушилась целиком, у входа — проломы
                    if (p.z >= 5 && (p.y > 5 || r.nextFloat() < 0.7f)) continue;
                    if (p.z < 5 && r.nextFloat() < 0.25f) continue;
                }
                case GABLE -> { if (p.z > 0 || r.nextFloat() < 0.3f) continue; }
                case WALL -> {
                    float f = r.nextFloat();
                    if (f < 0.12f + 0.06f * p.y) continue;
                    if (f < 0.35f) st = s.crackedWall().getDefaultState();
                }
                case BASE -> { if (r.nextFloat() < 0.4f) st = s.mossyBase().getDefaultState(); }
                case PILLAR -> { if (p.y >= 4 && r.nextFloat() < 0.4f) continue; }
                case GLASS -> { if (r.nextFloat() < 0.8f) continue; }
                case FLOOR -> {
                    float f = r.nextFloat();
                    if (f < 0.15f) st = Blocks.COARSE_DIRT.getDefaultState();
                    else if (f < 0.25f) st = Blocks.GRAVEL.getDefaultState();
                }
                default -> { }
            }
            out.add(new Placed(p.x, p.y, p.z, st, p.role));
        }
        // паутина, мох и обломки купола на полу
        for (int i = 0; i < 9; i++) {
            out.add(new Placed(1 + r.nextInt(5), 1 + r.nextInt(4), 1 + r.nextInt(D - 2), Blocks.COBWEB.getDefaultState(), Role.DECOR));
        }
        for (int i = 0; i < 6; i++) {
            out.add(new Placed(1 + r.nextInt(5), 1, 1 + r.nextInt(D - 2), Blocks.MOSS_CARPET.getDefaultState(), Role.DECOR));
        }
        for (int i = 0; i < 3; i++) {
            out.add(new Placed(1 + r.nextInt(5), 1, 2 + r.nextInt(4), s.dome().getDefaultState(), Role.DECOR));
        }
        plan.clear();
        plan.addAll(out);
    }

    // ------------------------------------------------------------------ размещение в мире

    private static BlockRotation rotationFor(Direction front) {
        return switch (front) {
            case EAST -> BlockRotation.CLOCKWISE_90;
            case SOUTH -> BlockRotation.CLOCKWISE_180;
            case WEST -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    /** Мировая позиция локальной точки: центр церкви (3, 5) → center, вход смотрит в сторону front. */
    static BlockPos toWorld(BlockPos center, Direction front, int lx, int ly, int lz) {
        BlockPos rel = new BlockPos(lx - 3, ly, lz - 5).rotate(rotationFor(front));
        return center.add(rel);
    }

    /** Подходит ли место: ровная естественная земля без построек. Возвращает высоту пола или null. */
    static Integer siteFloor(ServerWorld world, BlockPos center, Direction front) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        int[] counts = new int[512];
        int bottom = world.getBottomY();
        for (int lx = -1; lx <= W; lx++) {
            for (int lz = -2; lz <= D; lz++) {
                BlockPos p = toWorld(center, front, lx, 0, lz);
                int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) - 1;
                BlockPos g = new BlockPos(p.getX(), top, p.getZ());
                BlockState gs = world.getBlockState(g);
                if (!isNaturalGround(gs) || !gs.getFluidState().isEmpty()) return null;
                min = Math.min(min, top);
                max = Math.max(max, top);
                if (max - min > 3) return null;
                int idx = top - bottom;
                if (idx >= 0 && idx < counts.length) counts[idx]++;
            }
        }
        int best = min, bestN = -1;
        for (int y = min; y <= max; y++) {
            int n = counts[y - bottom];
            if (n > bestN) { bestN = n; best = y; }
        }
        // над полом — только воздух, трава, листва, снег и естественная земля
        for (int lx = -1; lx <= W; lx++) {
            for (int lz = -1; lz <= D; lz++) {
                for (int ly = 1; ly <= 19; ly++) {
                    BlockState st = world.getBlockState(toWorld(center, front, lx, 0, lz).withY(best + ly));
                    if (st.isAir()) continue;
                    if (!st.getFluidState().isEmpty()) return null;
                    if (st.isReplaceable() || st.isIn(BlockTags.LEAVES) || st.isIn(BlockTags.FLOWERS)
                            || st.isIn(BlockTags.SAPLINGS) || st.isOf(Blocks.SNOW) || isNaturalGround(st)) continue;
                    return null;
                }
            }
        }
        return best;
    }

    static boolean isNaturalGround(BlockState st) {
        return st.isIn(BlockTags.DIRT) || st.isIn(BlockTags.SAND) || st.isOf(Blocks.GRAVEL) || st.isOf(Blocks.STONE)
                || st.isOf(Blocks.SNOW_BLOCK) || st.isOf(Blocks.SNOW) || st.isOf(Blocks.SANDSTONE) || st.isOf(Blocks.CLAY)
                || st.isOf(Blocks.ANDESITE) || st.isOf(Blocks.DIORITE) || st.isOf(Blocks.GRANITE)
                || st.isOf(Blocks.TERRACOTTA) || st.isOf(Blocks.POWDER_SNOW) || st.isOf(Blocks.ICE);
    }

    /** Строит церковь. ruined — заброшенная версия. */
    public static void build(ServerWorld world, BlockPos center, int floorY, Direction front, VillagerType type,
                             boolean ruined, Random random) {
        ChurchStyle style = ChurchStyle.of(type);
        ChurchBuilder b = new ChurchBuilder(style);
        b.design();
        if (ruined) b.ruin(random);
        BlockRotation rot = rotationFor(front);
        BlockPos origin = center.withY(floorY);

        // расчистка и фундамент
        for (int lx = -1; lx <= W; lx++) {
            for (int lz = -1; lz <= D; lz++) {
                for (int ly = 1; ly <= 19; ly++) {
                    world.setBlockState(toWorld(origin, front, lx, ly, lz), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                if (lx < 0 || lx >= W || lz < 0 || lz >= D) {
                    // вокруг стен — земля вровень с полом (подсыпаем, если ниже)
                    boolean desert = type == VillagerType.DESERT;
                    for (int ly = 0; ly >= -5; ly--) {
                        BlockPos p = toWorld(origin, front, lx, ly, lz);
                        BlockState st = world.getBlockState(p);
                        if (st.isOpaqueFullCube(world, p) && st.getFluidState().isEmpty()) {
                            if (ly == 0) break;
                            continue;
                        }
                        Block fill = desert ? Blocks.SAND : (ly == 0 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
                        world.setBlockState(p, fill.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                    BlockPos top = toWorld(origin, front, lx, 0, lz);
                    if (world.getBlockState(top).isOf(Blocks.DIRT) && !desert) {
                        world.setBlockState(top, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                    continue;
                }
                for (int ly = -1; ly >= -6; ly--) {
                    BlockPos p = toWorld(origin, front, lx, ly, lz);
                    BlockState st = world.getBlockState(p);
                    if (st.isOpaqueFullCube(world, p) && st.getFluidState().isEmpty()) break;
                    world.setBlockState(p, style.foundation().getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }

        List<BlockPos> placed = new ArrayList<>();
        for (Placed p : b.plan) {
            BlockPos pos = toWorld(origin, front, p.x, p.y, p.z);
            BlockState st = p.state.rotate(rot);
            if (p.state.contains(Properties.HORIZONTAL_FACING)) {     // на случай блоков без своего rotate()
                st = st.with(Properties.HORIZONTAL_FACING, rot.rotate(p.state.get(Properties.HORIZONTAL_FACING)));
            }
            world.setBlockState(pos, st, Block.NOTIFY_ALL);
            placed.add(pos);
        }
        // соединения стёкол, заборов и ступеней
        for (BlockPos pos : placed) {
            BlockState st = world.getBlockState(pos);
            BlockState fixed = Block.postProcessState(st, world, pos);
            if (fixed != st) world.setBlockState(pos, fixed, Block.NOTIFY_LISTENERS);
        }

        // тропинка от входа в сторону деревни
        for (int i = 2; i <= 30; i++) {
            BlockPos col = toWorld(origin, front, 3, 0, -i);
            int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ()) - 1;
            BlockPos g = new BlockPos(col.getX(), top, col.getZ());
            BlockState gs = world.getBlockState(g);
            if (gs.isOf(Blocks.DIRT_PATH) || Math.abs(top - floorY) > 4) break;
            if (gs.isOf(Blocks.GRASS_BLOCK) || gs.isOf(Blocks.DIRT) || gs.isOf(Blocks.COARSE_DIRT) || gs.isOf(Blocks.PODZOL)) {
                if (world.getBlockState(g.up()).isReplaceable()) {
                    world.setBlockState(g.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    world.setBlockState(g, (ruined ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH).getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        BlockPos step = toWorld(origin, front, 3, 0, -1);
        world.setBlockState(step, (ruined ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH).getDefaultState(), Block.NOTIFY_LISTENERS);

        if (!ruined) spawnPriest(world, toWorld(origin, front, 3, 1, 6), type, random);
    }

    private static void spawnPriest(ServerWorld world, BlockPos pos, VillagerType type, Random random) {
        VillagerEntity v = EntityType.VILLAGER.create(world);
        if (v == null) return;
        v.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
        v.setVillagerData(v.getVillagerData().withType(type).withProfession(VillagerProfession.CLERIC).withLevel(2));
        v.setExperience(10);
        v.addCommandTag(PRIEST_TAG);
        v.setPersistent();
        String name = PRIEST_NAMES[random.nextInt(PRIEST_NAMES.length)];
        v.setCustomName(Text.translatable("entity.vestments.church_priest",
                Text.translatable("name.vestments.priest." + name)));
        world.spawnEntity(v);
    }
}
