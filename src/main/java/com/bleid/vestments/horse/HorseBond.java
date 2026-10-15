package com.bleid.vestments.horse;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;

/**
 * Зов рога: у игрока есть «свой» конь — последний прирученный им (или первый свой, на которого он сел, если связи ещё нет).
 * Звук козьего рога собирает коня из белых частиц в стороне от игрока; конь подбегает и встаёт рядом. Если за минуту
 * на него не сели, он рассыпается частицами и ждёт следующего зова. Погибший конь больше не приходит — нужен новый.
 * Конь хранится снимком (NBT): при каждом зове старая копия, где бы она ни стояла, исчезает (поколение в теге).
 */
public final class HorseBond {
    private HorseBond() { }

    public static final String TAG = "vestments_bond";
    private static final String GEN = "vestments_bond_gen_";
    /** Сколько тактов конь «собирается» из частиц, прежде чем стать видимым и побежать. */
    private static final int FORM = 24;
    private static final int WAIT = 60 * 20;

    // ------------------------------------------------------------------ сохранение
    static final class Bond {
        UUID horse;
        int gen;
        boolean dead;
        NbtCompound snapshot;
        /** Копии коня, оставшиеся в незагруженных местах после зова: исчезнут, как только загрузятся. */
        final java.util.Set<UUID> stale = new java.util.HashSet<>();
    }

    public static final class State extends PersistentState {
        final Map<UUID, Bond> bonds = new HashMap<>();

        static State read(NbtCompound nbt) {
            State s = new State();
            NbtCompound all = nbt.getCompound("bonds");
            for (String k : all.getKeys()) {
                NbtCompound b = all.getCompound(k);
                Bond bond = new Bond();
                if (b.containsUuid("horse")) bond.horse = b.getUuid("horse");
                bond.gen = b.getInt("gen");
                bond.dead = b.getBoolean("dead");
                if (b.contains("snap")) bond.snapshot = b.getCompound("snap");
                for (var el : b.getList("stale", net.minecraft.nbt.NbtElement.INT_ARRAY_TYPE)) bond.stale.add(net.minecraft.nbt.NbtHelper.toUuid(el));
                s.bonds.put(UUID.fromString(k), bond);
            }
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            NbtCompound all = new NbtCompound();
            bonds.forEach((k, b) -> {
                NbtCompound c = new NbtCompound();
                if (b.horse != null) c.putUuid("horse", b.horse);
                c.putInt("gen", b.gen);
                c.putBoolean("dead", b.dead);
                if (b.snapshot != null) c.put("snap", b.snapshot);
                net.minecraft.nbt.NbtList l = new net.minecraft.nbt.NbtList();
                for (UUID u : b.stale) l.add(net.minecraft.nbt.NbtHelper.fromUuid(u));
                c.put("stale", l);
                all.put(k.toString(), c);
            });
            nbt.put("bonds", all);
            return nbt;
        }
    }

    static State state(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(State::read, State::new, "vestments_horse_bonds");
    }

    /** Призванные кони, ждущие всадника: uuid → такт, когда растворятся (и такт появления). */
    private static final Map<UUID, long[]> SUMMONED = new HashMap<>();

    static boolean bondable(Entity e) {
        return e instanceof AbstractHorseEntity h && !(e instanceof LlamaEntity) && h.isTame() && !h.isBaby();
    }

    private static int genOf(Entity e) {
        for (String t : e.getCommandTags()) if (t.startsWith(GEN)) try { return Integer.parseInt(t.substring(GEN.length())); } catch (Exception ignored) { }
        return -1;
    }

    private static void setGen(Entity e, int gen) {
        e.getCommandTags().removeIf(t -> t.startsWith(GEN));
        e.addCommandTag(TAG);
        e.addCommandTag(GEN + gen);
    }

    /** Сделать коня «своим» для игрока (новая связь заменяет старую). */
    public static void bond(ServerPlayerEntity p, AbstractHorseEntity h) {
        State st = state(p.server);
        Bond b = st.bonds.computeIfAbsent(p.getUuid(), k -> new Bond());
        if (h.getUuid().equals(b.horse) && !b.dead) { snapshot(st, b, h); return; }
        b.horse = h.getUuid();
        b.gen++;
        b.dead = false;
        setGen(h, b.gen);
        snapshot(st, b, h);
        p.sendMessage(Text.translatable("message.vestments.horse_bonded").formatted(Formatting.GOLD), true);
    }

    private static void snapshot(State st, Bond b, AbstractHorseEntity h) {
        NbtCompound n = new NbtCompound();
        if (h.saveSelfNbt(n)) {
            n.remove("Passengers");
            n.remove("Leash");
            b.snapshot = n;
            st.markDirty();
        }
    }

    private static Bond bondOf(MinecraftServer server, AbstractHorseEntity h) {
        UUID owner = h.getOwnerUuid();
        if (owner == null) return null;
        Bond b = state(server).bonds.get(owner);
        return b != null && h.getUuid().equals(b.horse) ? b : null;
    }

    // ------------------------------------------------------------------ зов
    private static TypedActionResult<net.minecraft.item.ItemStack> horn(PlayerEntity player, net.minecraft.world.World world,
                                                                         net.minecraft.util.Hand hand) {
        var stack = player.getStackInHand(hand);
        if (world.isClient || !stack.isOf(Items.GOAT_HORN) || player.getItemCooldownManager().isCoolingDown(Items.GOAT_HORN)) {
            return TypedActionResult.pass(stack);
        }
        ServerPlayerEntity p = (ServerPlayerEntity) player;
        State st = state(p.server);
        Bond b = st.bonds.get(p.getUuid());
        if (b == null || b.horse == null || b.snapshot == null) {
            p.sendMessage(Text.translatable("message.vestments.horn_no_horse").formatted(Formatting.GRAY), true);
            return TypedActionResult.pass(stack);
        }
        if (b.dead) {
            p.sendMessage(Text.translatable("message.vestments.horn_dead").formatted(Formatting.RED), true);
            return TypedActionResult.pass(stack);
        }
        if (p.getVehicle() instanceof AbstractHorseEntity ridden && ridden.getUuid().equals(b.horse)) return TypedActionResult.pass(stack);
        // где бы ни стоял конь сейчас — он растворяется там и собирается здесь
        boolean found = false;
        for (ServerWorld w : p.server.getWorlds()) {
            Entity old = w.getEntity(b.horse);
            if (old instanceof AbstractHorseEntity oh) {
                if (oh.hasPassengers()) {
                    p.sendMessage(Text.translatable("message.vestments.horn_busy").formatted(Formatting.GRAY), true);
                    return TypedActionResult.pass(stack);
                }
                snapshot(st, b, oh);
                dissolveFx((ServerWorld) oh.getWorld(), oh);
                oh.discard();
                found = true;
            }
        }
        if (!found) b.stale.add(b.horse);
        ServerWorld w = p.getServerWorld();
        Vec3d at = spawnPoint(w, p);
        NbtCompound n = b.snapshot.copy();
        n.remove("UUID");
        Entity e = EntityType.loadEntityWithPassengers(n, w, x -> x);
        if (!(e instanceof AbstractHorseEntity h)) return TypedActionResult.pass(stack);
        b.gen++;
        setGen(h, b.gen);
        b.horse = h.getUuid();
        h.refreshPositionAndAngles(at.x, at.y, at.z, (float) Math.toDegrees(Math.atan2(-(p.getX() - at.x), p.getZ() - at.z)), 0);
        h.setHealth(Math.max(h.getHealth(), 1f));
        h.fallDistance = 0;
        h.setInvisible(true);
        h.setAiDisabled(true);
        w.spawnEntity(h);
        SUMMONED.put(h.getUuid(), new long[] { w.getTime() + FORM + WAIT, w.getTime() });
        snapshot(st, b, h);
        return TypedActionResult.pass(stack);                         // рог звучит как обычно
    }

    /** Место появления: в 10–14 блоках от игрока на земле, лучше за спиной; иначе — рядом. */
    private static Vec3d spawnPoint(ServerWorld w, PlayerEntity p) {
        float yaw = p.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        for (int i = 0; i < 12; i++) {
            double a = yaw + Math.PI + (i % 2 == 0 ? 1 : -1) * (i / 2) * 0.5;
            double d = 12 - (i / 4) * 2;
            int x = MathHelper.floor(p.getX() - Math.sin(a) * d), z = MathHelper.floor(p.getZ() + Math.cos(a) * d);
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - p.getY()) > 6) continue;
            BlockPos bp = new BlockPos(x, y, z);
            if (!w.getBlockState(bp.down()).isSolidBlock(w, bp.down())) continue;
            if (!w.isSpaceEmpty(EntityType.HORSE.createSimpleBoundingBox(x + 0.5, y, z + 0.5))) continue;
            return new Vec3d(x + 0.5, y, z + 0.5);
        }
        return p.getPos().add(p.getRotationVector().multiply(-2.5, 0, -2.5));
    }

    private static void formFx(ServerWorld w, AbstractHorseEntity h, float k) {
        var box = h.getBoundingBox();
        for (int i = 0; i < 14; i++) {
            double x = MathHelper.lerp(w.random.nextDouble(), box.minX, box.maxX), y = MathHelper.lerp(w.random.nextDouble(), box.minY, box.maxY),
                    z = MathHelper.lerp(w.random.nextDouble(), box.minZ, box.maxZ);
            double cx = box.getCenter().x, cy = box.getCenter().y, cz = box.getCenter().z;
            // частицы слетаются к силуэту коня
            w.spawnParticles(ParticleTypes.END_ROD, x + (x - cx) * (1 - k) * 2, y + (y - cy) * (1 - k), z + (z - cz) * (1 - k) * 2, 0,
                    (cx - x) * 0.08, (cy - y) * 0.05, (cz - z) * 0.08, 1.0);
        }
        w.spawnParticles(ParticleTypes.CLOUD, box.getCenter().x, box.minY + 0.8, box.getCenter().z, 3, 0.5, 0.5, 0.5, 0.01);
    }

    private static void dissolveFx(ServerWorld w, Entity h) {
        var box = h.getBoundingBox();
        w.spawnParticles(ParticleTypes.CLOUD, box.getCenter().x, box.getCenter().y, box.getCenter().z, 40, 0.6, 0.7, 0.9, 0.04);
        w.spawnParticles(ParticleTypes.END_ROD, box.getCenter().x, box.getCenter().y, box.getCenter().z, 50, 0.6, 0.7, 0.9, 0.08);
        w.playSound(null, h.getX(), h.getY(), h.getZ(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.NEUTRAL, 0.8f, 1.3f);
    }

    // ------------------------------------------------------------------ события
    public static void register() {
        UseItemCallback.EVENT.register(HorseBond::horn);

        // приручил — конь откликается на рог; сел на своего коня, а связи нет (или старый погиб) — он становится «своим»
        ServerTickEvents.END_WORLD_TICK.register(w -> {
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (p.getVehicle() instanceof AbstractHorseEntity h && bondable(h) && p.getUuid().equals(h.getOwnerUuid())) {
                    State st = state(w.getServer());
                    Bond b = st.bonds.get(p.getUuid());
                    if (b == null || b.dead || b.horse == null) bond(p, h);
                    else if (h.getUuid().equals(b.horse) && w.getTime() % 100 == 0) snapshot(st, b, h);
                }
            }
            // призванные: собраться из частиц, подбежать, встать рядом; через минуту без всадника — раствориться
            Iterator<Map.Entry<UUID, long[]>> it = SUMMONED.entrySet().iterator();
            while (it.hasNext()) {
                var en = it.next();
                Entity e = w.getEntity(en.getKey());
                if (e == null) continue;
                if (!(e instanceof AbstractHorseEntity h) || !h.isAlive()) { it.remove(); continue; }
                long age = w.getTime() - en.getValue()[1];
                if (age < FORM) {
                    formFx(w, h, age / (float) FORM);
                    if (age == 1) w.playSound(null, h.getX(), h.getY(), h.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.NEUTRAL, 1f, 0.8f);
                    continue;
                }
                if (h.isInvisible() && age >= FORM) {
                    h.setInvisible(false);
                    h.setAiDisabled(false);
                    w.playSound(null, h.getX(), h.getY(), h.getZ(), SoundEvents.ENTITY_HORSE_AMBIENT, SoundCategory.NEUTRAL, 1f, 1f);
                }
                if (h.hasPassengers()) { it.remove(); continue; }
                UUID owner = h.getOwnerUuid();
                PlayerEntity p = owner == null ? null : w.getPlayerByUuid(owner);
                if (p != null && h.squaredDistanceTo(p) > 3.2 * 3.2) {
                    h.getNavigation().startMovingTo(p.getX(), p.getY(), p.getZ(), h.squaredDistanceTo(p) > 64 ? 2.2 : 1.4);
                } else if (p != null) {
                    h.getNavigation().stop();
                    h.getLookControl().lookAt(p, 30, 30);
                }
                if (w.getTime() >= en.getValue()[0]) {
                    State st = state(w.getServer());
                    Bond b = bondOf(w.getServer(), h);
                    if (b != null) snapshot(st, b, h);
                    dissolveFx(w, h);
                    h.discard();
                    it.remove();
                }
            }
        });

        // слезли — запомнить коня таким, какой он сейчас (сумки, седло, броня, здоровье)
        ServerEntityEvents.ENTITY_LOAD.register((e, w) -> {
            if (!(e instanceof AbstractHorseEntity h) || !e.getCommandTags().contains(TAG)) return;
            UUID owner = h.getOwnerUuid();
            if (owner == null) return;
            Bond b = state(w.getServer()).bonds.get(owner);
            if (b != null && b.stale.remove(h.getUuid())) {
                // устаревшая копия: коня уже призвали в другом месте — она исчезает
                state(w.getServer()).markDirty();
                h.discard();
            } else if (b == null || !h.getUuid().equals(b.horse)) {
                // бывший «свой» конь (связь перешла к другому) — просто обычная лошадь
                h.getCommandTags().removeIf(t -> t.equals(TAG) || t.startsWith(GEN));
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> {
            if (!(e instanceof AbstractHorseEntity h) || e.getWorld().isClient) return;
            Bond b = bondOf(e.getServer(), h);
            if (b == null || genOf(h) != b.gen) return;
            b.dead = true;
            b.snapshot = null;
            state(e.getServer()).markDirty();
            SUMMONED.remove(h.getUuid());
            PlayerEntity p = ((ServerWorld) e.getWorld()).getPlayerByUuid(h.getOwnerUuid());
            if (p != null) p.sendMessage(Text.translatable("message.vestments.horse_died").formatted(Formatting.RED), false);
        });
    }

    /** Вызывается миксином, когда игрок приручил коня. */
    public static void onTamed(AbstractHorseEntity h, PlayerEntity p) {
        if (p instanceof ServerPlayerEntity sp && bondable(h)) bond(sp, h);
    }
}
