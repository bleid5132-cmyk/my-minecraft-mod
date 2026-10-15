package com.bleid.vestments.cathedral;

import com.bleid.vestments.Vestments;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

/**
 * Портал в Безмолвный Собор. Рама — резная медь любой степени патины (вощёная тоже), прямоугольник как у портала
 * в Незер (внутри от 2×3 до 21×21), зажигается осколком аметиста. Из обычного мира портал всегда ведёт к одному
 * месту — порталу на площади перед собором (там, где стоял обелиск); обратно — к тому порталу, через который вошли.
 */
public final class CathedralPortals {
    private CathedralPortals() { }

    /** Портал перед собором: x = 344, проём z −2..3, y −58..−54. Прибытие — на площадке перед ним, лицом к собору. */
    public static final Vec3d ARRIVE = new Vec3d(340.5, -59, 0.5);
    public static final float ARRIVE_YAW = 90f;
    private static final int DWELL = 30, COOLDOWN = 100;

    private static final Map<UUID, int[]> CONTACT = new HashMap<>();     // [последний такт касания, сколько тактов подряд]
    private static final Map<UUID, Long> COOL = new HashMap<>();

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(Items.AMETHYST_SHARD) || world.isClient) return ActionResult.PASS;
            BlockPos clicked = hit.getBlockPos();
            if (!isFrame(world.getBlockState(clicked))) return ActionResult.PASS;
            BlockPos start = clicked.offset(hit.getSide());
            if (light((ServerWorld) world, start) || lightAround((ServerWorld) world, clicked)) {
                if (!player.getAbilities().creativeMode) stack.decrement(1);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }

    public static boolean isFrame(BlockState s) {
        Block b = s.getBlock();
        return b == Blocks.CUT_COPPER || b == Blocks.EXPOSED_CUT_COPPER || b == Blocks.WEATHERED_CUT_COPPER || b == Blocks.OXIDIZED_CUT_COPPER
                || b == Blocks.WAXED_CUT_COPPER || b == Blocks.WAXED_EXPOSED_CUT_COPPER || b == Blocks.WAXED_WEATHERED_CUT_COPPER
                || b == Blocks.WAXED_OXIDIZED_CUT_COPPER;
    }

    private static boolean empty(World w, BlockPos p) {
        BlockState s = w.getBlockState(p);
        return s.isAir() || s.isReplaceable() && s.getFluidState().isEmpty();
    }

    /** Попробовать зажечь раму с любой из клеток вокруг блока, по которому ударили. */
    private static boolean lightAround(ServerWorld w, BlockPos frame) {
        for (Direction d : Direction.values()) if (light(w, frame.offset(d))) return true;
        return false;
    }

    /** Найти прямоугольный проём в медной раме, содержащий start, и заполнить его завесой. */
    private static boolean light(ServerWorld w, BlockPos start) {
        if (!empty(w, start)) return false;
        for (Direction.Axis axis : new Direction.Axis[] { Direction.Axis.X, Direction.Axis.Z }) {
            Direction dir = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
            BlockPos p = start;
            int n = 0;
            while (n++ < 21 && empty(w, p.down())) p = p.down();
            if (!isFrame(w.getBlockState(p.down()))) continue;
            BlockPos left = p;
            n = 0;
            while (n++ < 21 && empty(w, left.offset(dir.getOpposite()))) left = left.offset(dir.getOpposite());
            if (!isFrame(w.getBlockState(left.offset(dir.getOpposite())))) continue;
            int width = 0;
            while (width < 22 && empty(w, left.offset(dir, width))) width++;
            if (width < 2 || width > 21 || !isFrame(w.getBlockState(left.offset(dir, width)))) continue;
            int height = 0;
            boolean ok = true;
            for (; height < 22; height++) {
                BlockPos row = left.up(height);
                boolean allEmpty = true;
                for (int i = 0; i < width; i++) if (!empty(w, row.offset(dir, i))) { allEmpty = false; break; }
                if (!allEmpty) break;
                if (!isFrame(w.getBlockState(row.offset(dir.getOpposite()))) || !isFrame(w.getBlockState(row.offset(dir, width)))) { ok = false; break; }
            }
            if (!ok || height < 3 || height > 21) continue;
            for (int i = 0; i < width; i++) {
                if (!isFrame(w.getBlockState(left.up(height).offset(dir, i))) || !isFrame(w.getBlockState(left.down().offset(dir, i)))) { ok = false; break; }
            }
            if (!ok) continue;
            BlockState fill = Vestments.BELL_PORTAL.getDefaultState().with(BellPortalBlock.AXIS, axis);
            for (int h = 0; h < height; h++) for (int i = 0; i < width; i++) w.setBlockState(left.up(h).offset(dir, i), fill, Block.NOTIFY_LISTENERS);
            Vec3d c = Vec3d.ofCenter(left.up(height / 2).offset(dir, width / 2));
            w.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 3f, 0.5f);
            w.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 3f, 0.6f);
            w.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 2f, 0.6f);
            w.spawnParticles(ParticleTypes.GLOW, c.x, c.y, c.z, 80, width * 0.4, height * 0.4, 0.4, 0.05);
            w.spawnParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ переход

    static void touch(Entity e, BlockPos pos, Direction.Axis axis) {
        if (!(e instanceof ServerPlayerEntity p) || p.hasVehicle() || p.hasPassengers()) return;
        MinecraftServer server = p.getServer();
        long now = server.getTicks();
        Long cool = COOL.get(p.getUuid());
        if (cool != null && now < cool) return;
        int[] c = CONTACT.computeIfAbsent(p.getUuid(), k -> new int[] { -10, 0 });
        c[1] = c[0] >= now - 2 ? c[1] + (c[0] == now ? 0 : 1) : 1;
        c[0] = (int) now;
        int need = p.getAbilities().creativeMode ? 4 : DWELL;
        if (c[1] == 10 && need > 10) p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BELL_RESONATE,
                SoundCategory.PLAYERS, 1.5f, 0.6f);
        if (c[1] < need) return;
        CONTACT.remove(p.getUuid());
        COOL.put(p.getUuid(), now + COOLDOWN);
        if (p.getWorld().getRegistryKey() == Cathedral.WORLD) goBack(p);
        else goIn(p, pos, axis);
    }

    private static void goIn(ServerPlayerEntity p, BlockPos portal, Direction.Axis axis) {
        ServerWorld dest = p.getServer().getWorld(Cathedral.WORLD);
        if (dest == null) {
            p.sendMessage(Text.translatable("message.vestments.cathedral_missing").formatted(Formatting.RED), true);
            return;
        }
        // куда вернуть: на шаг из портала, с той стороны, откуда вошли
        double sx = axis == Direction.Axis.X ? p.getX() : portal.getX() + 0.5 + Math.signum(p.getX() - portal.getX() - 0.5 + 1e-3) * 1.6;
        double sz = axis == Direction.Axis.Z ? p.getZ() : portal.getZ() + 0.5 + Math.signum(p.getZ() - portal.getZ() - 0.5 + 1e-3) * 1.6;
        double sy = portal.getY();
        while (sy > p.getWorld().getBottomY() && p.getWorld().getBlockState(BlockPos.ofFloored(sx, sy - 1, sz)).isAir()) sy--;
        State st = state(p.getServer());
        NbtCompound r = new NbtCompound();
        r.putString("world", p.getWorld().getRegistryKey().getValue().toString());
        r.putDouble("x", sx); r.putDouble("y", sy); r.putDouble("z", sz); r.putFloat("yaw", p.getYaw() + 180f);
        st.back.put(p.getUuid(), r);
        st.markDirty();
        Cathedral.ensureLoaded(dest);
        fx(p);
        FabricDimensions.teleport(p, dest, new TeleportTarget(ARRIVE, Vec3d.ZERO, ARRIVE_YAW, 0f));
        p.sendMessage(Text.translatable("message.vestments.cathedral_enter").formatted(Formatting.DARK_GREEN), true);
    }

    private static void goBack(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        NbtCompound r = state(server).back.get(p.getUuid());
        ServerWorld dest = null;
        Vec3d pos = null;
        float yaw = p.getYaw();
        if (r != null) {
            dest = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(r.getString("world"))));
            pos = new Vec3d(r.getDouble("x"), r.getDouble("y"), r.getDouble("z"));
            yaw = r.getFloat("yaw");
        }
        if (dest == null) {
            dest = server.getOverworld();
            BlockPos sp = dest.getSpawnPos();
            pos = new Vec3d(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5);
        }
        fx(p);
        FabricDimensions.teleport(p, dest, new TeleportTarget(pos, Vec3d.ZERO, yaw, 0f));
    }

    private static void fx(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 2f, 0.4f);
        w.spawnParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 1, 0.6, 0.05);
    }

    // ------------------------------------------------------------------ откуда вернуть

    private static State state(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(State::fromNbt, State::new, "vestments_cathedral_portals");
    }

    public static final class State extends PersistentState {
        final Map<UUID, NbtCompound> back = new HashMap<>();

        static State fromNbt(NbtCompound nbt) {
            State s = new State();
            NbtCompound m = nbt.getCompound("back");
            for (String k : m.getKeys()) s.back.put(UUID.fromString(k), m.getCompound(k));
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            NbtCompound m = new NbtCompound();
            back.forEach((u, c) -> m.put(u.toString(), c));
            nbt.put("back", m);
            return nbt;
        }
    }
}
