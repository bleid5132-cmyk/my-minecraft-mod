package com.bleid.vestments.cathedral;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.boss.BellRingerEntity;
import com.bleid.vestments.boss.BossBase;
import com.bleid.vestments.boss.Bosses;
import com.bleid.vestments.boss.SilentBellEntity;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
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
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Измерение Безмолвного Собора: пустота и остров с разрушенным собором Святого Петра. Мир острова поставляется
 * готовыми файлами регионов и при первом запуске сервера копируется в папку измерения.
 * Правила битвы со Звонарём: игроки не ставят и не ломают блоки; выходы из собора закрыты звуковыми барьерами;
 * Звонарь ломает внутренние стены ударами, наружные стены и опоры купола — нет.
 */
public final class Cathedral {
    private Cathedral() { }

    public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, new Identifier(Vestments.MOD_ID, "silent_cathedral"));
    private static final String[] REGIONS = { "r.-1.-1.mca", "r.-1.0.mca", "r.0.-1.mca", "r.0.0.mca" };

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(Cathedral::install);
        CathedralPortals.register();
        // во время битвы — ни ломать, ни ставить
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (player.isCreative() || !(world instanceof ServerWorld sw)) return true;
            if (!fightNear(sw, player.getPos())) return true;
            player.sendMessage(Text.translatable("message.vestments.fight_no_build").formatted(Formatting.DARK_AQUA), true);
            return false;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player.isCreative() || !(world instanceof ServerWorld sw)) return ActionResult.PASS;
            ItemStack st = player.getStackInHand(hand);
            if (!(st.getItem() instanceof BlockItem) && !(st.getItem() instanceof BucketItem)) return ActionResult.PASS;
            if (!fightNear(sw, player.getPos())) return ActionResult.PASS;
            player.sendMessage(Text.translatable("message.vestments.fight_no_build").formatted(Formatting.DARK_AQUA), true);
            return ActionResult.FAIL;
        });
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getRegistryKey() == WORLD) tickBarriers(world);
        });
    }

    // ------------------------------------------------------------------ установка мира острова

    private static void install(MinecraftServer server) {
        try {
            Path dim = server.getSavePath(WorldSavePath.ROOT).resolve("dimensions").resolve(Vestments.MOD_ID).resolve("silent_cathedral");
            Path region = dim.resolve("region");
            if (Files.isDirectory(region) && Files.list(region).findAny().isPresent()) return;
            Files.createDirectories(region);
            Files.createDirectories(dim.resolve("entities"));
            for (String r : REGIONS) copy("/vestments_cathedral/region/" + r, region.resolve(r));
            copy("/vestments_cathedral/entities/r.0.0.mca", dim.resolve("entities").resolve("r.0.0.mca"));
            Vestments.LOGGER.info("Silent Cathedral island installed into {}", dim);
        } catch (Exception e) {
            Vestments.LOGGER.error("Failed to install the Silent Cathedral island", e);
        }
    }

    private static void copy(String res, Path to) throws Exception {
        try (InputStream in = Cathedral.class.getResourceAsStream(res)) {
            if (in == null) throw new IllegalStateException("missing resource " + res);
            Files.copy(in, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Прогрузить чанки у портала прибытия до телепорта. */
    static void ensureLoaded(ServerWorld w) {
        ChunkPos c = new ChunkPos(BlockPos.ofFloored(CathedralPortals.ARRIVE));
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.getChunk(c.x + dx, c.z + dz);
    }

    // ------------------------------------------------------------------ маски собора

    private static int fx0, fz0, fnx, fnz;
    private static byte[] foot;
    private static int bx0, by0, bz0, bnx, bny, bnz;
    private static byte[] brk;
    private static short[] exits;
    private static boolean loaded;

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Cathedral.class.getResourceAsStream("/vestments_cathedral/footprint.bin.gz")))) {
            fx0 = in.readInt(); fz0 = in.readInt(); fnx = in.readInt(); fnz = in.readInt();
            foot = in.readAllBytes();
        } catch (Exception e) {
            Vestments.LOGGER.error("cathedral footprint", e);
        }
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Cathedral.class.getResourceAsStream("/vestments_cathedral/breakable.bin.gz")))) {
            bx0 = in.readInt(); by0 = in.readInt(); bz0 = in.readInt(); bnx = in.readInt(); bny = in.readInt(); bnz = in.readInt();
            brk = in.readAllBytes();
        } catch (Exception e) {
            Vestments.LOGGER.error("cathedral breakable", e);
        }
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Cathedral.class.getResourceAsStream("/vestments_cathedral/exits.bin.gz")))) {
            int n = in.readInt();
            exits = new short[n * 3];
            for (int i = 0; i < n * 3; i++) exits[i] = in.readShort();
        } catch (Exception e) {
            Vestments.LOGGER.error("cathedral exits", e);
            exits = new short[0];
        }
    }

    private static boolean bit(byte[] a, long i) {
        return a != null && i >= 0 && (i >> 3) < a.length && (a[(int) (i >> 3)] & (0x80 >> (i & 7))) != 0;
    }

    /** Внутри ли собора (по плану). */
    public static boolean inside(double x, double z) {
        load();
        int i = (int) Math.floor(x) - fx0, k = (int) Math.floor(z) - fz0;
        if (i < 0 || k < 0 || i >= fnx || k >= fnz) return false;
        return bit(foot, (long) i * fnz + k);
    }

    /** Можно ли Звонарю разбить этот блок (внутренняя стена, не наружная и не опора купола). */
    public static boolean breakable(World w, BlockPos p) {
        if (w.getRegistryKey() != WORLD) return false;
        load();
        int i = p.getX() - bx0, j = p.getY() - by0, k = p.getZ() - bz0;
        if (i < 0 || j < 0 || k < 0 || i >= bnx || j >= bny || k >= bnz) return false;
        return bit(brk, ((long) i * bny + j) * bnz + k);
    }

    /** Удар босса разрушает внутренние стены вокруг точки. */
    public static void smashWalls(BossBase boss, Vec3d c, double r) {
        World w = boss.getWorld();
        if (w.isClient || w.getRegistryKey() != WORLD) return;
        int R = (int) Math.ceil(r);
        BlockPos o = BlockPos.ofFloored(c);
        int broken = 0;
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r) continue;
                    BlockPos p = o.add(dx, dy, dz);
                    if (!breakable(w, p) || w.getBlockState(p).isAir()) continue;
                    if (w.getBlockState(p).getHardness(w, p) < 0) continue;
                    if (broken < 24 && boss.getRandom().nextInt(3) == 0) w.breakBlock(p, false, boss);
                    else w.removeBlock(p, false);
                    broken++;
                }
            }
        }
        if (broken > 0) {
            w.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2f, 0.6f);
            ((ServerWorld) w).spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, r * 0.4, r * 0.4, r * 0.4, 0);
        }
    }

    // ------------------------------------------------------------------ битва

    /** Идёт ли битва со Звонарём/Колоколом рядом с точкой. */
    public static boolean fightNear(ServerWorld w, Vec3d p) {
        Box b = new Box(p, p).expand(96);
        for (BellRingerEntity r : w.getEntitiesByType(Bosses.BELL_RINGER, b, e -> e.isAlive() && !e.asleep() && !e.dying())) return true;
        for (SilentBellEntity s : w.getEntitiesByType(Bosses.SILENT_BELL, b, e -> e.isAlive() && e.state() == SilentBellEntity.S_FIGHT)) return true;
        return false;
    }

    private static boolean fightInWorld(ServerWorld w) {
        if (!w.getEntitiesByType(Bosses.BELL_RINGER, e -> e.isAlive() && !e.asleep() && !e.dying()).isEmpty()) return true;
        return !w.getEntitiesByType(Bosses.SILENT_BELL, e -> e.isAlive() && e.state() == SilentBellEntity.S_FIGHT).isEmpty();
    }

    private static final Map<UUID, Vec3d> LAST_INSIDE = new HashMap<>();

    /** Звуковые барьеры на выходах из собора: пока идёт битва, изнутри наружу не выйти. */
    private static void tickBarriers(ServerWorld w) {
        boolean fight = w.getTime() % 2 == 0 ? fightInWorld(w) : lastFight;
        lastFight = fight;
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.isSpectator() || p.isCreative()) continue;
            boolean in = inside(p.getX(), p.getZ());
            if (in) {
                LAST_INSIDE.put(p.getUuid(), p.getPos());
                continue;
            }
            Vec3d last = LAST_INSIDE.remove(p.getUuid());
            if (!fight || last == null || p.getPos().squaredDistanceTo(last) > 64) continue;
            // отбросить обратно в собор
            Vec3d back = last.subtract(p.getPos()).multiply(1, 0, 1);
            p.networkHandler.requestTeleport(last.x, last.y, last.z, p.getYaw(), p.getPitch());
            Vec3d push = back.lengthSquared() > 1e-4 ? back.normalize().multiply(0.6) : Vec3d.ZERO;
            p.setVelocity(push.x, 0.25, push.z);
            p.velocityModified = true;
            LAST_INSIDE.put(p.getUuid(), last);
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 1.5f, 1.4f);
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.HOSTILE, 2f, 0.5f);
            w.spawnParticles(ParticleTypes.SONIC_BOOM, p.getX() - push.x, p.getY() + 1, p.getZ() - push.z, 1, 0, 0, 0, 0);
            p.sendMessage(Text.translatable("message.vestments.fight_barrier").formatted(Formatting.DARK_AQUA), true);
        }
        // видимые барьеры в проёмах рядом с игроками
        if (fight && w.getTime() % 6 == 0) {
            load();
            for (ServerPlayerEntity p : w.getPlayers()) {
                for (int i = 0; i + 2 < exits.length; i += 3) {
                    double x = exits[i] + 1, y = exits[i + 1] + 1, z = exits[i + 2] + 1;
                    double dx = x - p.getX(), dz = z - p.getZ();
                    if (dx * dx + dz * dz > 40 * 40 || w.random.nextInt(3) != 0) continue;
                    w.spawnParticles(p, ParticleTypes.GLOW, false, x + w.random.nextDouble() * 2 - 1, y + w.random.nextDouble() * 2 - 1,
                            z + w.random.nextDouble() * 2 - 1, 1, 0, 0.1, 0, 0.01);
                    if (w.random.nextInt(12) == 0) w.spawnParticles(p, ParticleTypes.NOTE, false, x, y + 0.5, z, 1, 0.3, 0.3, 0.3, 1);
                }
            }
        }
    }

    private static boolean lastFight;
}
