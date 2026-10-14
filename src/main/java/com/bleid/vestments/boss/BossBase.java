package com.bleid.vestments.boss;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Общая основа боссов: текущая атака и её таймер, канал событий для клиентских эффектов,
 * полоса здоровья, агр на ближайшего игрока и вспомогательная геометрия.
 */
public abstract class BossBase extends HostileEntity {
    private static final TrackedData<Integer> ATTACK = DataTracker.registerData(BossBase.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> EVENT = DataTracker.registerData(BossBase.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Vector3f> EVENT_POS = DataTracker.registerData(BossBase.class, TrackedDataHandlerRegistry.VECTOR3F);
    private static final TrackedData<Float> EVENT_ARG = DataTracker.registerData(BossBase.class, TrackedDataHandlerRegistry.FLOAT);

    protected final ServerBossBar bossBar;
    protected int attackTick, cooldown = 40, queued = -1, lastAttack;
    protected final Map<Integer, Integer> cds = new HashMap<>();
    private int eventSeq;
    /** Клиент: тик начала текущей атаки. */
    public int clientAttackStart;

    protected BossBase(EntityType<? extends HostileEntity> type, World world, BossBar.Color color) {
        super(type, world);
        bossBar = new ServerBossBar(getDisplayName(), color, BossBar.Style.NOTCHED_10);
        setPersistent();
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(ATTACK, 0);
        dataTracker.startTracking(EVENT, 0);
        dataTracker.startTracking(EVENT_POS, new Vector3f());
        dataTracker.startTracking(EVENT_ARG, 0f);
    }

    public int attack() {
        return dataTracker.get(ATTACK);
    }

    protected void setAttack(int a) {
        dataTracker.set(ATTACK, a);
        attackTick = 0;
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (ATTACK.equals(data)) clientAttackStart = age;
    }

    public int eventValue() {
        return dataTracker.get(EVENT);
    }

    public Vec3d eventPos() {
        Vector3f v = dataTracker.get(EVENT_POS);
        return new Vec3d(v.x, v.y, v.z);
    }

    public float eventArg() {
        return dataTracker.get(EVENT_ARG);
    }

    private final java.util.ArrayDeque<Object[]> pending = new java.util.ArrayDeque<>();
    private int lastEventAge = -1;

    /** Событие для клиента; несколько событий в одном такте уходят по одному в следующие такты. */
    protected void event(int type, Vec3d p, float arg) {
        if (lastEventAge == age || !pending.isEmpty()) {
            pending.add(new Object[] { type, p, arg });
            return;
        }
        send(type, p, arg);
    }

    private void send(int type, Vec3d p, float arg) {
        lastEventAge = age;
        eventSeq++;
        dataTracker.set(EVENT_POS, new Vector3f((float) p.x, (float) p.y, (float) p.z));
        dataTracker.set(EVENT_ARG, arg);
        dataTracker.set(EVENT, (eventSeq << 5) | type);
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient && !pending.isEmpty() && lastEventAge != age) {
            Object[] e = pending.poll();
            send((Integer) e[0], (Vec3d) e[1], (Float) e[2]);
        }
    }

    /** Убрать с себя агрессию других мобов (для «мёртвых» фаз). */
    protected void forgetMe() {
        for (net.minecraft.entity.mob.MobEntity m : getWorld().getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class,
                getBoundingBox().expand(48), m -> m.getTarget() == this)) {
            m.setTarget(null);
            if (m instanceof net.minecraft.entity.mob.Angerable an) an.stopAnger();
        }
    }

    protected static double hdist(Vec3d a, Vec3d b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    protected boolean ready(int a) {
        return cds.getOrDefault(a, 0) <= 0;
    }

    /** Цель — ближайшее живое существо (боссы враждебны ко всем); игроки чуть в приоритете. */
    protected LivingEntity pickTarget() {
        LivingEntity t = getTarget();
        if (t != null && (!prey(this, t) || squaredDistanceTo(t) > 64 * 64)) {
            setTarget(null);
            t = null;
        }
        if (age % 10 == 0) {
            LivingEntity near = nearestPrey(this, 40);
            if (near != null && (t == null || near != t && preyDist(this, near) + 2.5 < preyDist(this, t))) {
                setTarget(near);
                t = near;
            }
        }
        return t;
    }

    /** Можно ли боссу охотиться на это существо: живое, не он сам, не зритель/творческий, не стойка, не «мёртвый» босс. */
    public static boolean prey(LivingEntity self, LivingEntity e) {
        if (e == self || !e.isAlive() || e.isInvulnerable()) return false;
        if (e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())) return false;
        if (e instanceof net.minecraft.entity.decoration.ArmorStandEntity) return false;
        if (e instanceof SeraelEntity s && s.statue()) return false;
        if (e instanceof BellRingerEntity r && (r.dying() || r.asleep())) return false;
        if (e instanceof SilentBellEntity b && b.state() != SilentBellEntity.S_FIGHT) return false;
        if (self instanceof SilentBellEntity && e.getCommandTags().contains("vestments_choir")) return false;   // свой хор
        return true;
    }

    /** Расстояние с поправкой: игрок «ближе» на 4 блока — бой с игроком не сбивается на случайную курицу. */
    public static double preyDist(LivingEntity self, LivingEntity e) {
        return self.distanceTo(e) - (e instanceof PlayerEntity ? 4 : 0);
    }

    public static LivingEntity nearestPrey(LivingEntity self, double range) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : self.getWorld().getEntitiesByClass(LivingEntity.class, self.getBoundingBox().expand(range), e -> prey(self, e))) {
            if (self.squaredDistanceTo(e) > range * range) continue;
            if (!(e instanceof PlayerEntity) && !self.canSee(e)) continue;
            double d = preyDist(self, e);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    protected void faceTarget(LivingEntity t, float step) {
        float yaw = (float) (MathHelper.atan2(t.getZ() - getZ(), t.getX() - getX()) * 180 / Math.PI) - 90f;
        setYaw(MathHelper.stepUnwrappedAngleTowards(getYaw(), yaw, step));
        bodyYaw = getYaw();
        headYaw = MathHelper.stepUnwrappedAngleTowards(headYaw, yaw, 20f);
        getLookControl().lookAt(t, 30f, 30f);
    }

    public Vec3d local(double right, double up, double fwd) {
        float y = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        Vec3d f = new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y));
        Vec3d r = new Vec3d(-MathHelper.cos(y), 0, -MathHelper.sin(y));
        return getPos().add(f.multiply(fwd)).add(r.multiply(right)).add(0, up, 0);
    }

    protected Vec3d forward() {
        float y = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y));
    }

    protected double ground(Vec3d p) {
        World w = getWorld();
        int x = MathHelper.floor(p.x), z = MathHelper.floor(p.z), top = MathHelper.floor(p.y) + 3;
        for (int y = top; y > top - 40; y--) {
            BlockPos bp = new BlockPos(x, y, z);
            if (!w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) return y + 1;
        }
        return p.y;
    }

    protected List<LivingEntity> enemies(Box box) {
        return getWorld().getEntitiesByClass(LivingEntity.class, box, e -> prey(this, e));
    }

    protected void knock(LivingEntity e, double h, double up) {
        Vec3d k = new Vec3d(e.getX() - getX(), 0, e.getZ() - getZ());
        if (k.lengthSquared() < 1e-4) k = new Vec3d(1, 0, 0);
        k = k.normalize();
        e.addVelocity(k.x * h, up, k.z * h);
        e.velocityModified = true;
    }

    protected void sound(SoundEvent s, float vol, float pitch) {
        getWorld().playSound(null, getX(), getY() + 2, getZ(), s, SoundCategory.HOSTILE, vol, pitch);
    }

    protected void soundAt(Vec3d p, SoundEvent s, float vol, float pitch) {
        getWorld().playSound(null, p.x, p.y, p.z, s, SoundCategory.HOSTILE, vol, pitch);
    }

    @Override
    public boolean handleFallDamage(float dist, float mult, DamageSource src) {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public Box getVisibilityBoundingBox() {
        return getBoundingBox().expand(5, 2, 5);
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void setCustomName(Text name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }

    protected static boolean swinging(PlayerEntity p) {
        return p.handSwinging && p.handSwingTicks <= 2;
    }
}
