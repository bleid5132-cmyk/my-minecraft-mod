package com.bleid.vestments.horse;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Повозка: два места на козлах, сзади — ещё два места или до 6 сундуков (у каждого свои 27 ячеек). Сундук ставится
 * правым кликом сундуком по кузову, открывается кликом по нему, снимается кликом с Shift. Всадник прицепляет
 * повозку кликом по ней прямо с коня (и так же отцепляет). Ломается тремя ударами — выпадает вместе с сундуками.
 */
public class CartEntity extends Entity {
    private static final TrackedData<Integer> CHESTS = DataTracker.registerData(CartEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> HORSE = DataTracker.registerData(CartEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> WOBBLE = DataTracker.registerData(CartEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** Расстояние от центра коня до оси повозки, блоки. */
    public static final double HITCH = 2.3;
    /** Гнёзда сундуков в кузове (x, z — блоки в осях повозки: x влево, z вперёд). */
    public static final double[][] SLOTS = { { 0.3125, -0.6875 }, { -0.3125, -0.6875 }, { 0.3125, -0.21875 }, { -0.3125, -0.21875 },
            { 0.3125, 0.25 }, { -0.3125, 0.25 } };

    private final SimpleInventory[] inv = new SimpleInventory[6];
    private UUID horseUuid;
    private int hits, lastHit;
    /** Клиент: угол колёс (градусы). */
    public float wheel, prevWheel;

    public CartEntity(EntityType<? extends CartEntity> type, World world) {
        super(type, world);
        for (int i = 0; i < 6; i++) {
            inv[i] = new SimpleInventory(27) {
                @Override
                public boolean canPlayerUse(PlayerEntity p) {
                    return CartEntity.this.isAlive() && p.squaredDistanceTo(CartEntity.this) < 64;
                }
            };
        }
        intersectionChecked = true;
        setStepHeight(1.0f);                 // колёса заезжают на ступеньку в блок
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(CHESTS, 0);
        dataTracker.startTracking(HORSE, -1);
        dataTracker.startTracking(WOBBLE, 0);
    }

    public int chests() {
        return dataTracker.get(CHESTS);
    }

    public boolean hasChest(int i) {
        return (chests() >> i & 1) != 0;
    }

    public int chestCount() {
        return Integer.bitCount(chests());
    }

    public int wobble() {
        return dataTracker.get(WOBBLE);
    }

    public AbstractHorseEntity horse() {
        int id = dataTracker.get(HORSE);
        return id >= 0 && getWorld().getEntityById(id) instanceof AbstractHorseEntity h ? h : null;
    }

    /** Насколько конь в упряжке может отвернуть от оглобель, градусы. */
    public static final float MAX_TURN = 35f;

    /** Не дать коню в упряжке развернуться поперёк повозки: курс коня — не дальше MAX_TURN от направления оглобель. */
    public static void clampHorse(AbstractHorseEntity h) {
        float axis = axis(h);
        if (Float.isNaN(axis)) return;
        for (Entity p : h.getPassengerList()) clampRider(h, p);
        float d = MathHelper.wrapDegrees(h.getYaw() - axis);
        if (Math.abs(d) <= MAX_TURN) return;
        float y = axis + Math.copySign(MAX_TURN, d);
        h.setYaw(y);
        h.prevYaw = y;
        h.bodyYaw = y;
        h.prevBodyYaw = y;
        h.headYaw = y;
    }

    /** Повозка, прицепленная к этому коню (на клиенте и сервере), или null. */
    public static CartEntity hitchedTo(Entity horse) {
        if (horse == null) return null;
        for (CartEntity c : horse.getWorld().getEntitiesByClass(CartEntity.class, horse.getBoundingBox().expand(HITCH + 2),
                c -> c.isAlive() && c.horse() == horse)) return c;
        return null;
    }

    /** Направление оглобель (от повозки к коню), градусы рысканья; NaN — если конь не в упряжке. */
    public static float axis(Entity horse) {
        CartEntity c = hitchedTo(horse);
        if (c == null) return Float.NaN;
        double dx = horse.getX() - c.getX(), dz = horse.getZ() - c.getZ();
        if (dx * dx + dz * dz < 1e-4) return c.getYaw();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Всадник в упряжке смотрит не дальше 90° от оглобель — как в лодке: обзор 180°. */
    public static void clampRider(Entity horse, Entity rider) {
        float a = axis(horse);
        if (Float.isNaN(a)) return;
        float d = MathHelper.wrapDegrees(rider.getYaw() - a);
        float c = MathHelper.clamp(d, -90f, 90f);
        if (c == d) return;
        float y = rider.getYaw() + (c - d);
        rider.prevYaw += c - d;
        rider.setYaw(y);
        rider.setHeadYaw(y);
    }

    private void hitch(AbstractHorseEntity h) {
        horseUuid = h == null ? null : h.getUuid();
        dataTracker.set(HORSE, h == null ? -1 : h.getId());
    }

    // ------------------------------------------------------------------ движение
    @Override
    public void tick() {
        super.tick();
        World w = getWorld();
        if (!w.isClient && horseUuid != null && horse() == null) {
            Entity e = ((ServerWorld) w).getEntity(horseUuid);
            if (e instanceof AbstractHorseEntity h && h.isAlive()) dataTracker.set(HORSE, h.getId());
        }
        if (!w.isClient && wobble() > 0) dataTracker.set(WOBBLE, wobble() - 1);
        AbstractHorseEntity h = horse();
        Vec3d v = getVelocity();
        double vx, vz;
        if (h != null) {
            if (!w.isClient && (!h.isAlive() || squaredDistanceTo(h) > 10 * 10)) {
                hitch(null);
                h = null;
            }
        }
        if (h != null) {
            // ось повозки тянется за конём: оглобли направлены на коня, повозка на расстоянии HITCH позади
            double dx = h.getX() - getX(), dz = h.getZ() - getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 1e-3) {
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                setYaw(yaw);
                double tx = h.getX() - dx / len * HITCH, tz = h.getZ() - dz / len * HITCH;
                vx = tx - getX();
                vz = tz - getZ();
            } else { vx = 0; vz = 0; }
        } else {
            vx = v.x * 0.6;
            vz = v.z * 0.6;
        }
        double vy = isOnGround() ? -0.04 : Math.max(-2.0, v.y - 0.08);
        setVelocity(vx, vy, vz);
        double ox = getX(), oz = getZ();
        move(MovementType.SELF, getVelocity());
        if (isOnGround() && getVelocity().y < 0) setVelocity(getVelocity().x, 0, getVelocity().z);
        // колёса: катятся на пройденный путь (радиус 9 пикс.)
        double mx = getX() - ox, mz = getZ() - oz;
        float yr = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double fwd = -MathHelper.sin(yr) * mx + MathHelper.cos(yr) * mz;
        prevWheel = wheel;
        wheel += (float) Math.toDegrees(fwd / (9.0 / 16.0));
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean interpolate) {
        // в упряжке клиент сам ведёт повозку за конём (плавно); иначе — как прислал сервер
        if (horse() != null && squaredDistanceTo(x, y, z) < 9) return;
        super.updateTrackedPositionAndAngles(x, y, z, yaw, pitch, steps, interpolate);
    }

    // ------------------------------------------------------------------ места
    private int seats() {
        return chestCount() == 0 ? 4 : 2;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengerList().size() < seats();
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
        int i = getPassengerList().indexOf(passenger);
        if (i < 0) return;
        double x = i % 2 == 0 ? 0.32 : -0.32, z = i < 2 ? 0.62 : -0.5, y = i < 2 ? 0.96 : 0.52;
        float yr = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double c = MathHelper.cos(yr), s = MathHelper.sin(yr);
        // оси повозки: вперёд (−sin, cos), влево (cos, sin)
        double wx = getX() + x * c - z * s, wz = getZ() + x * s + z * c;
        updater.accept(passenger, wx, getY() + y + passenger.getHeightOffset(), wz);
    }

    @Override
    public double getMountedHeightOffset() {
        return 0.96;
    }

    // ------------------------------------------------------------------ взаимодействие
    @Override
    public ActionResult interactAt(PlayerEntity player, Vec3d hit, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (getWorld().isClient) return ActionResult.SUCCESS;
        ItemStack stack = player.getStackInHand(hand);
        // с коня — прицепить / отцепить
        if (player.getVehicle() instanceof AbstractHorseEntity h) {
            if (horse() == h) {
                hitch(null);
                player.sendMessage(Text.translatable("message.vestments.cart_unhitched"), true);
            } else if (h.squaredDistanceTo(this) < 7 * 7 && hitchedTo(h) == null) {
                hitch(h);
                getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_LEASH_KNOT_PLACE, SoundCategory.NEUTRAL, 1f, 0.8f);
                player.sendMessage(Text.translatable("message.vestments.cart_hitched"), true);
            }
            return ActionResult.SUCCESS;
        }
        // точка клика в осях повозки
        float yr = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double c = MathHelper.cos(yr), s = MathHelper.sin(yr);
        double lx = hit.x * c + hit.z * s, lz = -hit.x * s + hit.z * c;
        int near = -1, free = -1;
        double best = 0.5 * 0.5, bestFree = 99;
        for (int i = 0; i < 6; i++) {
            double d = (lx - SLOTS[i][0]) * (lx - SLOTS[i][0]) + (lz - SLOTS[i][1]) * (lz - SLOTS[i][1]);
            if (hasChest(i) && d < best) { best = d; near = i; }
            if (!hasChest(i) && d < bestFree) { bestFree = d; free = i; }
        }
        if (stack.isOf(Items.CHEST)) {
            if (free < 0) {
                player.sendMessage(Text.translatable("message.vestments.cart_full").formatted(Formatting.GRAY), true);
                return ActionResult.SUCCESS;
            }
            if (getPassengerList().size() > 2) {
                player.sendMessage(Text.translatable("message.vestments.cart_seats_taken").formatted(Formatting.GRAY), true);
                return ActionResult.SUCCESS;
            }
            dataTracker.set(CHESTS, chests() | 1 << free);
            if (!player.getAbilities().creativeMode) stack.decrement(1);
            getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1f, 1f);
            return ActionResult.SUCCESS;
        }
        if (near >= 0) {
            if (player.isSneaking()) {
                removeChest(near);
                return ActionResult.SUCCESS;
            }
            final int slot = near;
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, pinv, pl) ->
                    GenericContainerScreenHandler.createGeneric9x3(id, pinv, inv[slot]),
                    Text.translatable("container.vestments.cart_chest", slot + 1)));
            getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.6f, 1f);
            return ActionResult.SUCCESS;
        }
        if (!player.isSneaking() && canAddPassenger(player) && !player.hasVehicle()) {
            player.startRiding(this);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    private void removeChest(int i) {
        if (!hasChest(i)) return;
        for (int k = 0; k < 27; k++) {
            ItemStack st = inv[i].removeStack(k);
            if (!st.isEmpty()) dropStack(st, 0.8f);
        }
        dropStack(new ItemStack(Items.CHEST), 0.8f);
        dataTracker.set(CHESTS, chests() & ~(1 << i));
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient || isRemoved() || isInvulnerableTo(source)) return false;
        boolean creative = source.getAttacker() instanceof PlayerEntity p && p.getAbilities().creativeMode;
        if (age - lastHit > 60) hits = 0;
        lastHit = age;
        hits++;
        dataTracker.set(WOBBLE, 10);
        getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_WOOD_HIT, SoundCategory.BLOCKS, 1f, 0.9f);
        if (hits >= 3 || creative) {
            for (int i = 0; i < 6; i++) removeChest(i);
            if (!creative) dropStack(new ItemStack(Horses.CART_ITEM), 0.5f);
            getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1f, 0.8f);
            discard();
        }
        return true;
    }

    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public ItemStack getPickBlockStack() {
        return new ItemStack(Horses.CART_ITEM);
    }

    // ------------------------------------------------------------------ сохранение
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        dataTracker.set(CHESTS, nbt.getInt("Chests"));
        for (int i = 0; i < 6; i++) {
            DefaultedList<ItemStack> list = DefaultedList.ofSize(27, ItemStack.EMPTY);
            Inventories.readNbt(nbt.getCompound("Chest" + i), list);
            for (int k = 0; k < 27; k++) inv[i].setStack(k, list.get(k));
        }
        horseUuid = nbt.containsUuid("Horse") ? nbt.getUuid("Horse") : null;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Chests", chests());
        for (int i = 0; i < 6; i++) {
            DefaultedList<ItemStack> list = DefaultedList.ofSize(27, ItemStack.EMPTY);
            for (int k = 0; k < 27; k++) list.set(k, inv[i].getStack(k));
            nbt.put("Chest" + i, Inventories.writeNbt(new NbtCompound(), list));
        }
        if (horseUuid != null) nbt.putUuid("Horse", horseUuid);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
