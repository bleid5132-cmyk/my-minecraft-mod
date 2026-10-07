package com.bleid.vestments.church;

import com.bleid.vestments.Vestments;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import net.minecraft.world.Heightmap;

/**
 * Православный храм по чертежу data/vestments/church/church.json (его рисует church_design.py):
 * колокольня с шатром, главкой и колоколом над входом, трапезная, четверик с барабаном и
 * луковичной главой, полукруглая алтарная апсида. Материалы — по стилю деревни (ChurchStyle).
 * Внутри: Священный алтарь, зельеварка, кафедра, свечи, паникадила и житель-священник.
 * Разрушенная версия — без глав, колокола и убранства, с обрушенной кровлей, проломами и паутиной.
 *
 * Локальные координаты чертежа: вход на z=0 смотрит на север (-z), центр поворота — CENTER.
 */
public final class ChurchBuilder {
    public static final String PRIEST_TAG = "vestments_church_priest";
    private static final String[] PRIEST_NAMES = { "nikolai", "sergii", "ioann", "aleksii", "mikhail", "pavel", "andrei", "serafim" };

    private record Entry(int x, int y, int z, String sym, JsonObject props, String role) { }

    private static List<Entry> plan;
    private static int cx, cz, minX, maxX, minZ, maxZ, maxY;

    private ChurchBuilder() { }

    private static synchronized List<Entry> plan() {
        if (plan != null) return plan;
        List<Entry> list = new ArrayList<>();
        try (InputStream in = ChurchBuilder.class.getResourceAsStream("/data/vestments/church/church.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray c = root.getAsJsonArray("center");
            cx = c.get(0).getAsInt();
            cz = c.get(1).getAsInt();
            minX = minZ = Integer.MAX_VALUE;
            maxX = maxZ = maxY = Integer.MIN_VALUE;
            for (JsonElement el : root.getAsJsonArray("entries")) {
                JsonArray a = el.getAsJsonArray();
                Entry e = new Entry(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(),
                        a.get(3).getAsString(), a.get(4).getAsJsonObject(), a.get(5).getAsString());
                list.add(e);
                minX = Math.min(minX, e.x); maxX = Math.max(maxX, e.x);
                minZ = Math.min(minZ, e.z); maxZ = Math.max(maxZ, e.z);
                maxY = Math.max(maxY, e.y);
            }
        } catch (Exception ex) {
            Vestments.LOGGER.error("Не удалось прочитать чертёж церкви", ex);
        }
        plan = list;
        return plan;
    }

    // ------------------------------------------------------------------ блоки по символам

    private static Block block(String sym, ChurchStyle s) {
        return switch (sym) {
            case "floor" -> s.floor();
            case "base" -> s.base();
            case "wall" -> s.wall();
            case "pillar" -> s.pillar();
            case "trim" -> s.trim();
            case "glass" -> Blocks.GLASS_PANE;
            case "roof_stairs" -> s.roofStairs();
            case "roof_slab" -> s.roofSlab();
            case "roof_block" -> s.roofBlock();
            case "dome" -> s.dome();
            case "dome_stairs" -> s.domeStairs();
            case "drum" -> s.drum();
            case "fence" -> s.fence();
            case "door" -> s.door();
            case "gold" -> Blocks.GOLD_BLOCK;
            case "bell" -> Blocks.BELL;
            case "altar" -> Vestments.HOLY_ALTAR;
            case "brewing" -> Blocks.BREWING_STAND;
            case "lectern" -> Blocks.LECTERN;
            case "candle" -> Blocks.CANDLE;
            case "lantern" -> Blocks.LANTERN;
            case "carpet" -> Blocks.RED_CARPET;
            default -> Blocks.AIR;
        };
    }

    private static BlockState withProps(BlockState st, JsonObject props) {
        for (Map.Entry<String, JsonElement> e : props.entrySet()) {
            Property<?> p = st.getBlock().getStateManager().getProperty(e.getKey());
            if (p != null) st = with(st, p, e.getValue().getAsString());
        }
        return st;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState st, Property<T> p, String value) {
        return p.parse(value).map(v -> st.with(p, v)).orElse(st);
    }

    // ------------------------------------------------------------------ размещение

    private static BlockRotation rotationFor(Direction front) {
        return switch (front) {
            case EAST -> BlockRotation.CLOCKWISE_90;
            case SOUTH -> BlockRotation.CLOCKWISE_180;
            case WEST -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    static BlockPos toWorld(BlockPos center, Direction front, int lx, int ly, int lz) {
        return center.add(new BlockPos(lx - cx, ly, lz - cz).rotate(rotationFor(front)));
    }

    /** Подходит ли место: ровная естественная земля без построек. Возвращает высоту пола или null. */
    static Integer siteFloor(ServerWorld world, BlockPos center, Direction front) {
        plan();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        int bottom = world.getBottomY();
        int[] counts = new int[world.getHeight() + 1];
        for (int lx = minX - 1; lx <= maxX + 1; lx++) {
            for (int lz = minZ - 1; lz <= maxZ + 1; lz++) {
                BlockPos p = toWorld(center, front, lx, 0, lz);
                int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) - 1;
                BlockState gs = world.getBlockState(new BlockPos(p.getX(), top, p.getZ()));
                if (!isNaturalGround(gs) || !gs.getFluidState().isEmpty()) return null;
                min = Math.min(min, top);
                max = Math.max(max, top);
                if (max - min > 3) return null;
                counts[Math.max(0, Math.min(counts.length - 1, top - bottom))]++;
            }
        }
        int best = min, bestN = -1;
        for (int y = min; y <= max; y++) {
            int n = counts[Math.max(0, Math.min(counts.length - 1, y - bottom))];
            if (n > bestN) { bestN = n; best = y; }
        }
        if (best + maxY + 2 >= world.getTopY()) return null;
        for (int lx = minX - 1; lx <= maxX + 1; lx++) {
            for (int lz = minZ; lz <= maxZ + 1; lz++) {
                BlockPos col = toWorld(center, front, lx, 0, lz);
                for (int ly = 1; ly <= maxY + 1; ly++) {
                    BlockState st = world.getBlockState(col.withY(best + ly));
                    if (st.isAir()) continue;
                    if (!st.getFluidState().isEmpty()) return null;
                    if (st.isReplaceable() || st.isIn(BlockTags.LEAVES) || st.isIn(BlockTags.FLOWERS)
                            || st.isIn(BlockTags.SAPLINGS) || isNaturalGround(st)) continue;
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

    /** Выкинуть ли элемент в разрушенной версии; может вернуть изменённое состояние. */
    private static BlockState ruin(Entry e, BlockState st, ChurchStyle s, Random r) {
        switch (e.role) {
            case "dome", "cross", "bell", "door", "carpet" -> { return null; }
            case "decor" -> {
                if (st.isOf(Vestments.HOLY_ALTAR) || st.isOf(Blocks.BREWING_STAND) || st.isOf(Blocks.LANTERN)) return null;
                if (st.isOf(Blocks.CANDLE)) return r.nextBoolean() ? null : st.with(CandleBlock.LIT, false);
                return r.nextFloat() < 0.5f ? null : st;
            }
            case "roof" -> {
                boolean tower = e.z <= 4;
                if (tower && e.y >= 13 && r.nextFloat() < 0.6f) return null;
                if (!tower && e.z >= 9 && e.y >= 9) return null;          // кровля четверика обрушилась
                return r.nextFloat() < 0.3f ? null : st;
            }
            case "wall" -> {
                float f = r.nextFloat();
                if (f < 0.12f + 0.025f * e.y) return null;
                if (f < 0.35f && !st.isOf(s.fence())) return s.crackedWall().getDefaultState();
                return st;
            }
            case "base" -> { return r.nextFloat() < 0.4f ? s.mossyBase().getDefaultState() : st; }
            case "pillar" -> { return e.y >= 8 && r.nextFloat() < 0.4f ? null : st; }
            case "glass" -> { return r.nextFloat() < 0.8f ? null : st; }
            case "floor" -> {
                float f = r.nextFloat();
                if (f < 0.15f) return Blocks.COARSE_DIRT.getDefaultState();
                if (f < 0.25f) return Blocks.GRAVEL.getDefaultState();
                return st;
            }
            default -> { return st; }
        }
    }

    /** Строит церковь. Возвращает позицию колокола (или null), чтобы не принять его за новую деревню. */
    public static BlockPos build(ServerWorld world, BlockPos center, int floorY, Direction front, VillagerType type,
                                 boolean ruined, Random random) {
        List<Entry> entries = plan();
        ChurchStyle style = ChurchStyle.of(type);
        BlockRotation rot = rotationFor(front);
        BlockPos origin = center.withY(floorY);
        boolean desert = type == VillagerType.DESERT;

        // пол-площадка: какие клетки заняты зданием
        java.util.Set<Long> footprint = new java.util.HashSet<>();
        for (Entry e : entries) if (e.y == 0) footprint.add(((long) e.x << 32) ^ (e.z & 0xffffffffL));

        // расчистка, фундамент и подсыпка вокруг
        for (int lx = minX - 1; lx <= maxX + 1; lx++) {
            for (int lz = minZ - 1; lz <= maxZ + 1; lz++) {
                for (int ly = 1; ly <= maxY + 1; ly++) {
                    world.setBlockState(toWorld(origin, front, lx, ly, lz), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                boolean inside = footprint.contains(((long) lx << 32) ^ (lz & 0xffffffffL));
                for (int ly = 0; ly >= -6; ly--) {
                    BlockPos p = toWorld(origin, front, lx, ly, lz);
                    BlockState st = world.getBlockState(p);
                    if (st.isOpaqueFullCube(world, p) && st.getFluidState().isEmpty()) {
                        if (ly == 0 && !inside && st.isOf(Blocks.DIRT) && !desert) {
                            world.setBlockState(p, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
                        }
                        if (ly < 0 || !inside) break;
                        continue;
                    }
                    Block fill = inside ? style.foundation() : desert ? Blocks.SAND : ly == 0 ? Blocks.GRASS_BLOCK : Blocks.DIRT;
                    world.setBlockState(p, fill.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }

        List<BlockPos> placed = new ArrayList<>();
        BlockPos bell = null;
        for (Entry e : entries) {
            BlockState st = withProps(block(e.sym, style).getDefaultState(), e.props);
            if (ruined) {
                st = ruin(e, st, style, random);
                if (st == null) continue;
            }
            BlockState rotated = st.rotate(rot);
            if (st.contains(Properties.HORIZONTAL_FACING)) {      // на случай блоков без своего rotate()
                rotated = rotated.with(Properties.HORIZONTAL_FACING, rot.rotate(st.get(Properties.HORIZONTAL_FACING)));
            }
            BlockPos pos = toWorld(origin, front, e.x, e.y, e.z);
            world.setBlockState(pos, rotated, Block.NOTIFY_ALL);
            placed.add(pos);
            if (rotated.isOf(Blocks.BELL)) bell = pos;
        }
        if (ruined) {
            // паутина, мох и обломки главы внутри
            for (int i = 0; i < 14; i++) {
                int x = 1 + random.nextInt(7), z = 1 + random.nextInt(15), y = 1 + random.nextInt(5);
                BlockPos p = toWorld(origin, front, x, y, z);
                if (world.getBlockState(p).isAir()) world.setBlockState(p, Blocks.COBWEB.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
            for (int i = 0; i < 10; i++) {
                int x = 1 + random.nextInt(7), z = 1 + random.nextInt(15);
                BlockPos p = toWorld(origin, front, x, 1, z);
                if (world.getBlockState(p).isAir()) {
                    Block b = i < 4 ? style.dome() : Blocks.MOSS_CARPET;
                    world.setBlockState(p, b.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        // соединения стёкол, заборов, ступеней
        for (BlockPos pos : placed) {
            BlockState st = world.getBlockState(pos);
            if (st.isAir()) continue;
            BlockState fixed = Block.postProcessState(st, world, pos);
            if (fixed != st) world.setBlockState(pos, fixed, Block.NOTIFY_LISTENERS);
        }

        // тропинка от входа к деревне
        for (int i = 1; i <= 30; i++) {
            BlockPos col = toWorld(origin, front, cx, 0, minZ - i);
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

        if (!ruined) spawnPriest(world, toWorld(origin, front, cx, 1, 11), type, random);
        return bell;
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
