package com.bleid.vestments.boss;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Багровый Серафим — летающий босс высотой 10 блоков. Парит над землёй, держит дистанцию и чередует атаки:
 * удар крыльями, залп перьев, Суд (столпы молний вокруг цели) и пике. На половине здоровья — вторая фаза:
 * атаки чаще и мощнее.
 */
public class CrimsonSeraphEntity extends HostileEntity {
    public static final int IDLE = 0, SWEEP = 1, VOLLEY = 2, JUDGEMENT = 3, DIVE = 4, ROAR = 5;
    /** Длительность атак в тиках (индекс — номер атаки). */
    public static final int[] DURATION = { 0, 30, 44, 54, 34, 40 };
    private static final TrackedData<Integer> ATTACK = DataTracker.registerData(CrimsonSeraphEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> ENRAGED = DataTracker.registerData(CrimsonSeraphEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(0.8f, 0.08f, 0.1f), 2.0f);
    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1f, 0.82f, 0.25f), 1.6f);

    private final ServerBossBar bossBar = new ServerBossBar(getDisplayName(), BossBar.Color.RED, BossBar.Style.NOTCHED_10);
    private int attackTick, cooldown = 60, lastAttack;
    private final List<Vec3d> marks = new ArrayList<>();
    private final List<Entity> diveHit = new ArrayList<>();
    private Vec3d diveDir = Vec3d.ZERO;
    /** Клиент: тик начала текущей атаки (для анимации). */
    public int clientAttackStart;

    public CrimsonSeraphEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        experiencePoints = 400;
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 600)
                .add(EntityAttributes.GENERIC_ARMOR, 12)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 6)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 16)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.6)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(ATTACK, IDLE);
        dataTracker.startTracking(ENRAGED, false);
    }

    @Override
    protected void initGoals() {
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
    }

    public int attack() {
        return dataTracker.get(ATTACK);
    }

    public boolean enraged() {
        return dataTracker.get(ENRAGED);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (ATTACK.equals(data)) clientAttackStart = age;
    }

    private void setAttack(int a) {
        dataTracker.set(ATTACK, a);
        attackTick = 0;
    }

    // ------------------------------------------------------------------ движение и атаки (сервер)

    @Override
    protected void mobTick() {
        super.mobTick();
        bossBar.setPercent(getHealth() / getMaxHealth());
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()))) {
            setTarget(null);
            t = null;
        }
        if (!enraged() && getHealth() < getMaxHealth() * 0.5f) {
            dataTracker.set(ENRAGED, true);
            setAttack(ROAR);
        }
        int a = attack();
        if (a != IDLE) {
            attackTick++;
            runAttack(a, t);
            if (attackTick >= DURATION[a]) {
                setAttack(IDLE);
                cooldown = enraged() ? 22 : 40;
            }
        } else if (t != null && --cooldown <= 0) {
            startAttack(t);
        }
        steer(t, a);
    }

    private void startAttack(LivingEntity t) {
        double d = Math.sqrt(squaredDistanceTo(t));
        List<Integer> opts = new ArrayList<>();
        if (d < 7.5) { opts.add(SWEEP); opts.add(SWEEP); }
        opts.add(VOLLEY);
        if (d > 4) opts.add(JUDGEMENT);
        if (d > 8) { opts.add(DIVE); opts.add(DIVE); }
        int pick;
        do {
            pick = opts.get(random.nextInt(opts.size()));
        } while (pick == lastAttack && opts.size() > 1 && random.nextFloat() < 0.7f);
        lastAttack = pick;
        setAttack(pick);
        switch (pick) {
            case SWEEP -> sound(SoundEvents.ENTITY_PHANTOM_FLAP, 2f, 0.5f);
            case VOLLEY -> sound(SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, 2f, 0.7f);
            case JUDGEMENT -> sound(SoundEvents.ENTITY_EVOKER_PREPARE_WOLOLO, 2.5f, 0.6f);
            case DIVE -> sound(SoundEvents.ENTITY_PHANTOM_SWOOP, 2.5f, 0.5f);
            default -> { }
        }
    }

    private void runAttack(int a, LivingEntity t) {
        ServerWorld w = (ServerWorld) getWorld();
        boolean rage = enraged();
        switch (a) {
            case SWEEP -> {
                if (attackTick == 13) {
                    sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 3f, 0.5f);
                    Vec3d f = forward();
                    Vec3d c = getPos().add(f.multiply(3)).add(0, 2.5, 0);
                    for (int i = 0; i < 40; i++) {
                        double ang = Math.toRadians(getYaw() + 90 + (i - 20) * 6);
                        w.spawnParticles(RED, c.x + Math.cos(ang) * 4.5, c.y + random.nextDouble() * 4, c.z + Math.sin(ang) * 4.5, 1, 0, 0, 0, 0);
                    }
                    w.spawnParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 6, 2.5, 1.5, 2.5, 0);
                    for (LivingEntity e : enemies(new Box(c, c).expand(5.5, 4, 5.5))) {
                        e.damage(getDamageSources().mobAttack(this), rage ? 22 : 16);
                        Vec3d k = e.getPos().subtract(getPos()).normalize();
                        e.addVelocity(k.x * 2.2, 0.7, k.z * 2.2);
                        e.velocityModified = true;
                    }
                }
            }
            case VOLLEY -> {
                if (t != null && attackTick >= 14 && attackTick <= 36 && attackTick % 3 == 0) {
                    int n = rage ? 5 : 3;
                    for (int i = 0; i < n; i++) {
                        double side = (i - (n - 1) / 2.0) * 1.6;
                        Vec3d r = right();
                        Vec3d from = getPos().add(0, 6.5 + random.nextDouble() * 1.5, 0).add(r.multiply(side * 1.6)).subtract(forward().multiply(0.8));
                        Vec3d to = t.getEyePos().add(random.nextGaussian() * 0.8, random.nextGaussian() * 0.5, random.nextGaussian() * 0.8);
                        Vec3d dir = to.subtract(from);
                        CrimsonFeatherEntity fe = new CrimsonFeatherEntity(w, this, rage ? 9 : 7);
                        fe.setPosition(from);
                        fe.setVelocity(dir.x, dir.y, dir.z, rage ? 1.6f : 1.3f, 2.5f);
                        w.spawnEntity(fe);
                    }
                    sound(SoundEvents.ENTITY_ARROW_SHOOT, 1.6f, 0.6f);
                }
            }
            case JUDGEMENT -> {
                if (attackTick == 10 && t != null) {
                    marks.clear();
                    int n = rage ? 8 : 5;
                    marks.add(t.getPos());
                    for (int i = 1; i < n; i++) {
                        double ang = random.nextDouble() * Math.PI * 2, r = 2 + random.nextDouble() * 4;
                        Vec3d p = t.getPos().add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
                        marks.add(new Vec3d(p.x, ground(p), p.z));
                    }
                }
                if (attackTick > 10 && attackTick < 36) {
                    for (Vec3d m : marks) {
                        for (int i = 0; i < 3; i++) {
                            double ang = random.nextDouble() * Math.PI * 2;
                            w.spawnParticles(GOLD, m.x + Math.cos(ang) * 1.5, m.y + 0.1, m.z + Math.sin(ang) * 1.5, 1, 0, 0, 0, 0);
                        }
                        w.spawnParticles(RED, m.x, m.y + 0.2, m.z, 1, 0.3, 0, 0.3, 0);
                    }
                }
                if (attackTick == 36) {
                    for (Vec3d m : marks) {
                        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
                        if (bolt != null) {
                            bolt.refreshPositionAfterTeleport(m);
                            bolt.setCosmetic(true);
                            w.spawnEntity(bolt);
                        }
                        w.spawnParticles(ParticleTypes.FLASH, m.x, m.y + 1, m.z, 1, 0, 0, 0, 0);
                        w.spawnParticles(GOLD, m.x, m.y + 2, m.z, 30, 0.4, 2, 0.4, 0);
                        for (LivingEntity e : enemies(new Box(m, m).expand(1.8, 3, 1.8).offset(0, 1.5, 0))) {
                            e.damage(getDamageSources().indirectMagic(this, this), rage ? 16 : 12);
                            e.setOnFireFor(4);
                        }
                    }
                    marks.clear();
                }
            }
            case DIVE -> {
                if (attackTick == 10 && t != null) {
                    diveDir = t.getPos().add(0, -1, 0).subtract(getPos()).normalize();
                    diveHit.clear();
                }
                if (attackTick >= 10 && attackTick < 24) {
                    setVelocity(diveDir.multiply(rage ? 1.7 : 1.4));
                    w.spawnParticles(RED, getX(), getY() + 4, getZ(), 6, 1.2, 3, 1.2, 0);
                    for (LivingEntity e : enemies(getBoundingBox().expand(1.2))) {
                        if (diveHit.contains(e)) continue;
                        diveHit.add(e);
                        e.damage(getDamageSources().mobAttack(this), rage ? 20 : 14);
                        e.addVelocity(diveDir.x * 1.8, 0.8, diveDir.z * 1.8);
                        e.velocityModified = true;
                    }
                }
            }
            case ROAR -> {
                if (attackTick == 12) {
                    sound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 4f, 0.8f);
                    w.spawnParticles(RED, getX(), getY() + 5, getZ(), 200, 4, 4, 4, 0.1);
                    for (LivingEntity e : enemies(getBoundingBox().expand(8))) {
                        Vec3d k = e.getPos().subtract(getPos()).normalize();
                        e.addVelocity(k.x * 2.5, 0.9, k.z * 2.5);
                        e.velocityModified = true;
                        e.damage(getDamageSources().mobAttack(this), 6);
                    }
                }
            }
            default -> { }
        }
    }

    /** Полёт: держит дистанцию до цели и парит в паре блоков над землёй. */
    private void steer(LivingEntity t, int a) {
        Vec3d v = getVelocity();
        if (a == DIVE && attackTick >= 10 && attackTick < 24) return;
        Vec3d goal;
        if (t != null) {
            lookAtEntity(t, 30f, 30f);
            double want = a == SWEEP ? 3.5 : a == VOLLEY || a == JUDGEMENT ? 10 : 7;
            Vec3d flat = new Vec3d(getX() - t.getX(), 0, getZ() - t.getZ());
            if (flat.lengthSquared() < 1e-4) flat = new Vec3d(1, 0, 0);
            flat = flat.normalize().multiply(want);
            // кружит вокруг цели
            double orbit = Math.sin(age * 0.02) * 0.35;
            flat = flat.rotateY((float) orbit);
            goal = t.getPos().add(flat);
            goal = new Vec3d(goal.x, Math.max(ground(goal) + 1.5, t.getY() + 0.5), goal.z);
            float yaw = (float) (MathHelper.atan2(t.getZ() - getZ(), t.getX() - getX()) * 180 / Math.PI) - 90f;
            setYaw(MathHelper.stepUnwrappedAngleTowards(getYaw(), yaw, 6f));
            bodyYaw = getYaw();
            headYaw = getYaw();
        } else {
            goal = new Vec3d(getX(), ground(getPos()) + 2, getZ());
        }
        Vec3d d = goal.subtract(getPos());
        double len = d.length();
        Vec3d acc = len > 0.3 ? d.multiply(Math.min(0.06, len * 0.02) / len) : Vec3d.ZERO;
        if (horizontalCollision) acc = acc.add(0, 0.08, 0);
        v = v.multiply(0.88).add(acc);
        double sp = v.length(), max = enraged() ? 0.55 : 0.42;
        if (sp > max) v = v.multiply(max / sp);
        setVelocity(v);
    }

    private double ground(Vec3d p) {
        World w = getWorld();
        int x = MathHelper.floor(p.x), z = MathHelper.floor(p.z);
        int top = MathHelper.floor(p.y) + 4;
        for (int y = top; y > top - 40; y--) {
            if (!w.getBlockState(new net.minecraft.util.math.BlockPos(x, y, z)).getCollisionShape(w, new net.minecraft.util.math.BlockPos(x, y, z)).isEmpty()) {
                return y + 1;
            }
        }
        return p.y - 2;
    }

    private List<LivingEntity> enemies(Box box) {
        return getWorld().getEntitiesByClass(LivingEntity.class, box, e -> e != this && e.isAlive()
                && !(e instanceof CrimsonSeraphEntity) && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())));
    }

    private Vec3d forward() {
        float y = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y));
    }

    private Vec3d right() {
        float y = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.cos(y), 0, -MathHelper.sin(y));
    }

    private void sound(SoundEvent s, float vol, float pitch) {
        getWorld().playSound(null, getX(), getY() + 5, getZ(), s, SoundCategory.HOSTILE, vol, pitch);
    }

    // ------------------------------------------------------------------ прочее поведение босса

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient && age % 2 == 0) {
            getWorld().addParticle(enraged() ? RED : GOLD, getX() + random.nextGaussian() * 1.5, getY() + 3 + random.nextDouble() * 5,
                    getZ() + random.nextGaussian() * 1.5, 0, -0.02, 0);
        }
        if (!getWorld().isClient && age % 28 == 0 && attack() == IDLE) sound(SoundEvents.ENTITY_PHANTOM_FLAP, 1.2f, 0.45f);
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
        return getBoundingBox().expand(5, 1.5, 5);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_WITHER_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return 0.55f;
    }

    @Override
    protected float getSoundVolume() {
        return 3f;
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
    public void setCustomName(net.minecraft.text.Text name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (hasCustomName()) bossBar.setName(getDisplayName());
        dataTracker.set(ENRAGED, nbt.getBoolean("Enraged"));
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Enraged", enraged());
    }

    @Override
    protected void dropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops) {
        super.dropEquipment(source, lootingMultiplier, allowDrops);
        dropStack(new ItemStack(Bosses.CRIMSON_FEATHER, 10 + random.nextInt(8) + lootingMultiplier * 3));
        dropStack(new ItemStack(Items.GOLD_BLOCK, 2 + random.nextInt(3)));
        dropStack(new ItemStack(Items.NETHER_STAR));
    }

    /** Долгая смерть: серафим замирает, осыпается красными и золотыми искрами. */
    @Override
    protected void updatePostDeath() {
        deathTime++;
        if (getWorld() instanceof ServerWorld w) {
            w.spawnParticles(deathTime % 2 == 0 ? RED : GOLD, getX(), getY() + 4.5, getZ(), 12, 2.5, 3.5, 2.5, 0.02);
            if (deathTime % 10 == 0) sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.5f, 1.4f);
            if (deathTime >= 70 && !isRemoved()) {
                w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 4, getZ(), 1, 0, 0, 0, 0);
                getWorld().sendEntityStatus(this, (byte) 60);
                remove(RemovalReason.KILLED);
            }
        }
    }
}
