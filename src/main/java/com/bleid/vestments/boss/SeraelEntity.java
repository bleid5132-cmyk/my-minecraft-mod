package com.bleid.vestments.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
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
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Сераэль, Пепельный Нимб — босс в духе тёмного фэнтези: падший ангел с нимбом, демоническими крыльями
 * из раскалённого камня и треснувшим двуручным клинком.
 * <ul>
 * <li>«Проклятый взмах» — широкий удар, в воздухе остаётся след тёмной энергии; через 1,5 с по нему
 * проходит взрывная волна (урон + «Истощение»).</li>
 * <li>«Дождь из лепестков» — над игроком кружат алые лепестки, через 2 с они каскадом падают остриями.</li>
 * <li>«Тень нимба» — нимб пульсирует и расширяется кольцом света: внутри — урон и оглушение.</li>
 * <li>«Полёт и пикирование» — взлёт с пылающими крыльями, удар в точку игрока, зона горения на 5 с.</li>
 * <li>«Демонический барьер» — щит на 9 с; удары копят заряд, при полном заряде щит взрывается и босс
 * оглушён на 3 с; если не разбить — щит детонирует.</li>
 * </ul>
 */
public class SeraelEntity extends HostileEntity {
    public static final int IDLE = 0, SWING = 1, PETALS = 2, HALO = 3, DIVE = 4, BARRIER = 5, STUNNED = 6, SLASH = 7, ROAR = 8;
    public static final int[] DURATION = { 0, 56, 72, 50, 100, 180, 60, 24, 40 };
    private static final TrackedData<Integer> ATTACK = DataTracker.registerData(SeraelEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> ENRAGED = DataTracker.registerData(SeraelEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> CHARGE = DataTracker.registerData(SeraelEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public static final DustParticleEffect DARK = new DustParticleEffect(new Vector3f(0.32f, 0.05f, 0.45f), 1.8f);
    public static final DustParticleEffect VOIDP = new DustParticleEffect(new Vector3f(0.7f, 0.25f, 1f), 1.2f);
    public static final DustParticleEffect CRIMSON = new DustParticleEffect(new Vector3f(0.85f, 0.06f, 0.12f), 1.3f);
    public static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1f, 0.84f, 0.42f), 1.6f);
    private static final ParticleEffect PETAL = new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.RED_CONCRETE.getDefaultState());
    private static final float SHIELD_HP = 90f;

    private final ServerBossBar bossBar = new ServerBossBar(getDisplayName(), BossBar.Color.RED, BossBar.Style.NOTCHED_12);
    private int attackTick, cooldown = 50, lastAttack, impactTick = -1;
    private final Map<Integer, Integer> cds = new HashMap<>();
    /** След «Проклятого взмаха»: точки дуги и тик взрыва каждой. */
    private final List<Vec3d> arc = new ArrayList<>();
    private final List<Integer> arcBoom = new ArrayList<>();
    private final Set<Entity> waveHit = new HashSet<>();
    private Vec3d petalZone, diveMark;
    private final List<double[]> burns = new ArrayList<>();     // {x, y, z, тиков осталось}
    private boolean shield;
    private float charge;
    /** Клиент: тик начала текущей атаки (для анимации). */
    public int clientAttackStart;

    public SeraelEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        experiencePoints = 500;
        setStepHeight(1.2f);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 800)
                .add(EntityAttributes.GENERIC_ARMOR, 14)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 8)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 18)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(ATTACK, IDLE);
        dataTracker.startTracking(ENRAGED, false);
        dataTracker.startTracking(CHARGE, -1f);
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

    /** −1 — щита нет; иначе 0..1 — накопленный заряд щита. */
    public float shieldCharge() {
        return dataTracker.get(CHARGE);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (ATTACK.equals(data)) clientAttackStart = age;
    }

    private void setAttack(int a) {
        dataTracker.set(ATTACK, a);
        attackTick = 0;
        impactTick = -1;
    }

    // ================================================================== ИИ (сервер)

    @Override
    protected void mobTick() {
        super.mobTick();
        bossBar.setPercent(getHealth() / getMaxHealth());
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()))) {
            setTarget(null);
            t = null;
        }
        cds.replaceAll((k, v) -> Math.max(0, v - 1));
        int a = attack();
        if (!enraged() && getHealth() < getMaxHealth() * 0.5f && a != BARRIER && a != DIVE) {
            dataTracker.set(ENRAGED, true);
            setAttack(ROAR);
            a = ROAR;
            sound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 4f, 0.7f);
        }
        if (a != IDLE) {
            attackTick++;
            runAttack(a, t);
            int end = a == DIVE && impactTick >= 0 ? impactTick + 26 : DURATION[a];
            if (attack() == a && attackTick >= end) {
                if (a == BARRIER && shield) detonateShield();
                setNoGravity(false);
                setAttack(IDLE);
                cooldown = enraged() ? 16 : 28;
            }
        } else if (t != null && --cooldown <= 0) {
            startAttack(t);
        }
        tickArc();
        tickBurns();
        steer(t, attack());
    }

    private boolean ready(int a) {
        return cds.getOrDefault(a, 0) <= 0;
    }

    private void startAttack(LivingEntity t) {
        double d = Math.sqrt(squaredDistanceTo(t));
        List<Integer> opts = new ArrayList<>();
        if (d < 5.5) {
            opts.add(SWING); opts.add(SWING); opts.add(SLASH); opts.add(SLASH);
            if (ready(HALO)) { opts.add(HALO); opts.add(HALO); }
        }
        if (d >= 4 && ready(PETALS)) { opts.add(PETALS); opts.add(PETALS); }
        if (d > 7 && ready(DIVE)) { opts.add(DIVE); opts.add(DIVE); opts.add(DIVE); }
        if (getHealth() < getMaxHealth() * 0.8f && ready(BARRIER) && d < 14) opts.add(BARRIER);
        if (opts.isEmpty()) {
            cooldown = 6;          // подходим ближе
            return;
        }
        int pick;
        int guard = 0;
        do {
            pick = opts.get(random.nextInt(opts.size()));
        } while (pick == lastAttack && opts.size() > 1 && guard++ < 4);
        lastAttack = pick;
        setAttack(pick);
        float m = enraged() ? 0.6f : 1f;
        switch (pick) {
            case SWING -> sound(SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, 2f, 0.6f);
            case SLASH -> sound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 2f, 0.5f);
            case PETALS -> { cds.put(PETALS, (int) (200 * m)); sound(SoundEvents.BLOCK_CHERRY_LEAVES_BREAK, 3f, 0.5f); }
            case HALO -> { cds.put(HALO, (int) (220 * m)); sound(SoundEvents.BLOCK_BEACON_ACTIVATE, 3f, 1.4f); }
            case DIVE -> { cds.put(DIVE, (int) (260 * m)); sound(SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 3f, 0.7f); }
            case BARRIER -> {
                cds.put(BARRIER, (int) (700 * m));
                shield = true;
                charge = 0;
                dataTracker.set(CHARGE, 0f);
                sound(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 3f, 0.5f);
            }
            default -> { }
        }
    }

    private void runAttack(int a, LivingEntity t) {
        ServerWorld w = (ServerWorld) getWorld();
        boolean rage = enraged();
        int k = attackTick;
        switch (a) {
            case SWING -> {
                // дуга удара справа налево перед собой; каждая точка взрывается через 30 тиков
                if (k >= 14 && k <= 19) {
                    int per = 3;
                    for (int j = 0; j < per; j++) {
                        float f = ((k - 14) * per + j) / (6f * per - 1);
                        double ang = Math.toRadians(-75 + 150 * f);
                        Vec3d p = local(Math.sin(ang) * -4.6, 2.6 + 0.6 * Math.sin(f * Math.PI), Math.cos(ang) * 4.6);
                        arc.add(p);
                        arcBoom.add(age + 30 + (int) (f * 8));
                    }
                }
                if (k == 17) {
                    sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 3f, 0.45f);
                    for (LivingEntity e : enemies(getBoundingBox().expand(5.5, 1, 5.5))) {
                        if (!inFront(e, 80)) continue;
                        e.damage(getDamageSources().mobAttack(this), rage ? 24 : 18);
                        knock(e, 1.4, 0.4);
                    }
                }
            }
            case SLASH -> {
                if (k == 11) {
                    sound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 2.5f, 0.5f);
                    Vec3d c = local(0, 0.2, 3.2);
                    w.spawnParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y + 1, c.z, 3, 0.6, 0.4, 0.6, 0);
                    w.spawnParticles(DARK, c.x, c.y + 0.3, c.z, 30, 1, 0.2, 1, 0);
                    for (LivingEntity e : enemies(new Box(c, c).expand(2.6, 3, 2.6))) {
                        e.damage(getDamageSources().mobAttack(this), rage ? 19 : 14);
                        knock(e, 0.8, 0.5);
                    }
                }
            }
            case PETALS -> {
                if (k == 8 && t != null) petalZone = new Vec3d(t.getX(), ground(t.getPos()), t.getZ());
                if (petalZone == null) break;
                double R = rage ? 8.5 : 7;
                if (k > 8 && k < 48) {
                    for (int i = 0; i < 14; i++) {
                        double ang = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * R;
                        w.spawnParticles(i % 2 == 0 ? PETAL : CRIMSON, petalZone.x + Math.cos(ang) * r, petalZone.y + 6 + random.nextDouble() * 2.5,
                                petalZone.z + Math.sin(ang) * r, 1, 0, 0, 0, 0);
                    }
                    if (k % 4 == 0) ring(w, petalZone.add(0, 0.15, 0), R, CRIMSON, 48);
                }
                if (k == 46) sound(SoundEvents.ITEM_TRIDENT_RIPTIDE_1, 3f, 1.3f);
                if (k >= 48 && k < 70) {
                    int n = rage ? 11 : 7;
                    for (int i = 0; i < n; i++) {
                        double ang = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * R;
                        double x = petalZone.x + Math.cos(ang) * r, z = petalZone.z + Math.sin(ang) * r;
                        double gy = ground(new Vec3d(x, petalZone.y + 3, z));
                        for (int s = 0; s < 6; s++) w.spawnParticles(CRIMSON, x, gy + 7 - s * 1.2, z, 1, 0, 0, 0, 0);
                        w.spawnParticles(ParticleTypes.CRIT, x, gy + 0.2, z, 3, 0.2, 0.05, 0.2, 0.2);
                        for (LivingEntity e : enemies(new Box(x - 1.3, gy - 0.5, z - 1.3, x + 1.3, gy + 3, z + 1.3))) {
                            e.damage(getDamageSources().indirectMagic(this, this), rage ? 8 : 6);
                        }
                    }
                    if (k % 3 == 0) sound(SoundEvents.ENTITY_ARROW_HIT, 1.5f, 0.6f);
                }
                if (k >= 70) petalZone = null;
            }
            case HALO -> {
                Vec3d hp = getPos().add(0, 6.3, 0);
                if (k < 30 && k % 5 == 0) {
                    w.spawnParticles(GOLD, hp.x, hp.y, hp.z, 12 + k, 0.5, 0.2, 0.5, 0.02);
                    sound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 2.5f, 0.6f + k * 0.03f);
                }
                if (k == 30) {
                    sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 2f, 1.8f);
                    sound(SoundEvents.BLOCK_BEACON_POWER_SELECT, 3f, 1.6f);
                    waveHit.clear();
                }
                if (k >= 30 && k <= 42) {
                    double r = (k - 30) * (rage ? 1.0 : 0.85);
                    for (double hy : new double[] { 0.3, 1.4, 2.6, 4.2, 6.3 }) ring(w, getPos().add(0, hy, 0), r, GOLD, (int) (12 + r * 6));
                    for (LivingEntity e : enemies(getBoundingBox().expand(r + 1, 2, r + 1))) {
                        double dd = Math.sqrt(MathHelper.square(e.getX() - getX()) + MathHelper.square(e.getZ() - getZ()));
                        if (dd > r + 0.6 || waveHit.contains(e)) continue;
                        waveHit.add(e);
                        e.damage(getDamageSources().indirectMagic(this, this), rage ? 18 : 14);
                        e.addStatusEffect(new StatusEffectInstance(BossEffects.STUN, 40, 0));
                        knock(e, 0.6, 0.3);
                    }
                }
            }
            case DIVE -> {
                if (k < 24) {
                    setNoGravity(true);
                    setVelocity(getVelocity().multiply(0.6, 0, 0.6).add(0, 0.55, 0));
                    if (k % 6 == 0) sound(SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 2.5f, 0.8f);
                } else if (k < 40) {
                    setVelocity(getVelocity().multiply(0.7));
                    if (k == 34 && t != null) diveMark = new Vec3d(t.getX(), ground(t.getPos()), t.getZ());
                    if (diveMark != null) ring(w, diveMark.add(0, 0.2, 0), 2.5, CRIMSON, 30);
                    if (k == 39) sound(SoundEvents.ENTITY_BLAZE_SHOOT, 3f, 0.5f);
                } else if (impactTick < 0) {
                    if (diveMark == null) diveMark = t != null ? t.getPos() : getPos().add(0, -10, 0);
                    Vec3d dir = diveMark.subtract(getPos());
                    double len = dir.length();
                    setVelocity(dir.multiply(Math.min(2.0, len) / Math.max(1e-3, len)));
                    w.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 4, getZ(), 10, 1.6, 2, 1.6, 0.02);
                    if (len < 1.6 || isOnGround() || k > 75) impact(w, rage);
                }
            }
            case BARRIER -> {
                setVelocity(getVelocity().multiply(0, 1, 0));
                if (k % 20 == 0) sound(SoundEvents.BLOCK_BEACON_AMBIENT, 2f, 0.5f);
            }
            case ROAR -> {
                if (k == 10) {
                    w.spawnParticles(ParticleTypes.ASH, getX(), getY() + 3, getZ(), 300, 4, 3, 4, 0.05);
                    w.spawnParticles(PETAL, getX(), getY() + 4, getZ(), 120, 5, 3, 5, 0.05);
                    for (LivingEntity e : enemies(getBoundingBox().expand(8))) {
                        knock(e, 2.4, 0.8);
                        e.damage(getDamageSources().mobAttack(this), 6);
                    }
                }
            }
            default -> { }
        }
    }

    private void impact(ServerWorld w, boolean rage) {
        impactTick = attackTick;
        setNoGravity(false);
        setVelocity(Vec3d.ZERO);
        Vec3d c = getPos();
        sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 4f, 0.6f);
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        w.spawnParticles(ParticleTypes.LAVA, c.x, c.y + 0.5, c.z, 40, 2, 0.3, 2, 0);
        for (LivingEntity e : enemies(getBoundingBox().expand(4.5, 1.5, 4.5))) {
            e.damage(getDamageSources().mobAttack(this), rage ? 26 : 20);
            knock(e, 1.6, 0.7);
        }
        burns.add(new double[] { c.x, ground(c), c.z, 100 });
        diveMark = null;
    }

    private void detonateShield() {
        shield = false;
        dataTracker.set(CHARGE, -1f);
        ServerWorld w = (ServerWorld) getWorld();
        sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 3f, 0.8f);
        w.spawnParticles(DARK, getX(), getY() + 3, getZ(), 220, 3, 3, 3, 0.1);
        for (LivingEntity e : enemies(getBoundingBox().expand(7))) {
            e.damage(getDamageSources().indirectMagic(this, this), enraged() ? 16 : 12);
            knock(e, 1.8, 0.6);
        }
    }

    private void breakShield() {
        shield = false;
        dataTracker.set(CHARGE, -1f);
        ServerWorld w = (ServerWorld) getWorld();
        sound(SoundEvents.BLOCK_GLASS_BREAK, 4f, 0.5f);
        sound(SoundEvents.ENTITY_WITHER_BREAK_BLOCK, 2f, 0.8f);
        w.spawnParticles(VOIDP, getX(), getY() + 3, getZ(), 260, 2.6, 3, 2.6, 0.2);
        w.spawnParticles(ParticleTypes.FLASH, getX(), getY() + 3, getZ(), 1, 0, 0, 0, 0);
        for (LivingEntity e : enemies(getBoundingBox().expand(5))) knock(e, 1.0, 0.3);
        setAttack(STUNNED);
    }

    private void tickArc() {
        if (arc.isEmpty()) return;
        ServerWorld w = (ServerWorld) getWorld();
        boolean any = false;
        for (int i = arc.size() - 1; i >= 0; i--) {
            Vec3d p = arc.get(i);
            int boom = arcBoom.get(i);
            if (age < boom) {
                w.spawnParticles(DARK, p.x, p.y, p.z, 2, 0.12, 0.12, 0.12, 0);
                if (random.nextInt(3) == 0) w.spawnParticles(VOIDP, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.01);
            } else {
                any = true;
                w.spawnParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                w.spawnParticles(DARK, p.x, p.y, p.z, 18, 0.6, 0.6, 0.6, 0.05);
                for (LivingEntity e : enemies(new Box(p, p).expand(2.0, 2.6, 2.0))) {
                    if (waveHit.contains(e)) continue;
                    waveHit.add(e);
                    e.damage(getDamageSources().indirectMagic(this, this), enraged() ? 14 : 10);
                    e.addStatusEffect(new StatusEffectInstance(BossEffects.EXHAUSTION, 240, 0));
                }
                arc.remove(i);
                arcBoom.remove(i);
            }
        }
        if (any && age % 2 == 0) sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.4f, 1.2f);
        if (arc.isEmpty()) waveHit.clear();
    }

    private void tickBurns() {
        if (burns.isEmpty()) return;
        ServerWorld w = (ServerWorld) getWorld();
        for (int i = burns.size() - 1; i >= 0; i--) {
            double[] b = burns.get(i);
            for (int j = 0; j < 6; j++) {
                double ang = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * 4;
                w.spawnParticles(j % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, b[0] + Math.cos(ang) * r, b[1] + 0.1, b[2] + Math.sin(ang) * r, 1, 0, 0.05, 0, 0.01);
            }
            if ((int) b[3] % 10 == 0) {
                for (LivingEntity e : enemies(new Box(b[0] - 4, b[1] - 1, b[2] - 4, b[0] + 4, b[1] + 2.5, b[2] + 4))) {
                    if (MathHelper.square(e.getX() - b[0]) + MathHelper.square(e.getZ() - b[2]) > 16) continue;
                    e.setOnFireFor(3);
                    e.damage(getDamageSources().inFire(), 4);
                }
            }
            if (--b[3] <= 0) burns.remove(i);
        }
    }

    /** Ходьба: держит дистанцию под атаку, разворачивается к цели. */
    private void steer(LivingEntity t, int a) {
        if (a == DIVE || a == BARRIER || a == STUNNED || a == ROAR) {
            if (t != null && a == DIVE) faceTarget(t, 8f);
            if (a != DIVE) setVelocity(getVelocity().multiply(0.5, 1, 0.5));
            return;
        }
        Vec3d v = getVelocity();
        double vx = 0, vz = 0;
        if (t != null) {
            faceTarget(t, a == SWING || a == SLASH ? 4f : 10f);
            double want = a == PETALS || a == HALO ? 5.5 : 3.2;
            double dx = t.getX() - getX(), dz = t.getZ() - getZ(), d = Math.sqrt(dx * dx + dz * dz);
            boolean busy = a == SWING || a == SLASH;
            if (d > want && !busy) {
                double sp = enraged() ? 0.27 : 0.22;
                vx = dx / d * sp;
                vz = dz / d * sp;
            }
        }
        double nx = MathHelper.lerp(0.3, v.x, vx), nz = MathHelper.lerp(0.3, v.z, vz), ny = v.y;
        if (horizontalCollision && isOnGround() && (vx != 0 || vz != 0)) ny = 0.55;
        setVelocity(nx, ny, nz);
    }

    private void faceTarget(LivingEntity t, float step) {
        float yaw = (float) (MathHelper.atan2(t.getZ() - getZ(), t.getX() - getX()) * 180 / Math.PI) - 90f;
        setYaw(MathHelper.stepUnwrappedAngleTowards(getYaw(), yaw, step));
        bodyYaw = getYaw();
        headYaw = MathHelper.stepUnwrappedAngleTowards(headYaw, yaw, 20f);
        getLookControl().lookAt(t, 30f, 30f);
    }

    // ================================================================== урон и щит

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;
        if (source.getAttacker() == this) return false;
        if (shield && !source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)) {
            charge += amount;
            dataTracker.set(CHARGE, Math.min(1f, charge / SHIELD_HP));
            ((ServerWorld) getWorld()).spawnParticles(VOIDP, getX(), getY() + 3, getZ(), 20, 1.5, 2, 1.5, 0.1);
            sound(SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 2f, 0.6f + charge / SHIELD_HP);
            if (charge >= SHIELD_HP) breakShield();
            return false;
        }
        if (attack() == STUNNED) amount *= 1.5f;
        return super.damage(source, amount);
    }

    // ================================================================== вспомогательное

    private Vec3d local(double right, double up, double fwd) {
        float y = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        Vec3d f = new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y));
        Vec3d r = new Vec3d(-MathHelper.cos(y), 0, -MathHelper.sin(y));
        return getPos().add(f.multiply(fwd)).add(r.multiply(right)).add(0, up, 0);
    }

    private boolean inFront(Entity e, double halfAngle) {
        float y = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        Vec3d f = new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y));
        Vec3d d = new Vec3d(e.getX() - getX(), 0, e.getZ() - getZ());
        if (d.lengthSquared() < 1) return true;
        return f.dotProduct(d.normalize()) > Math.cos(Math.toRadians(halfAngle));
    }

    private void knock(LivingEntity e, double h, double up) {
        Vec3d k = new Vec3d(e.getX() - getX(), 0, e.getZ() - getZ());
        if (k.lengthSquared() < 1e-4) k = new Vec3d(1, 0, 0);
        k = k.normalize();
        e.addVelocity(k.x * h, up, k.z * h);
        e.velocityModified = true;
    }

    private void ring(ServerWorld w, Vec3d c, double r, ParticleEffect p, int n) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            w.spawnParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private double ground(Vec3d p) {
        World w = getWorld();
        int x = MathHelper.floor(p.x), z = MathHelper.floor(p.z), top = MathHelper.floor(p.y) + 3;
        for (int y = top; y > top - 30; y--) {
            BlockPos bp = new BlockPos(x, y, z);
            if (!w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) return y + 1;
        }
        return p.y;
    }

    private List<LivingEntity> enemies(Box box) {
        return getWorld().getEntitiesByClass(LivingEntity.class, box, e -> e != this && e.isAlive()
                && !(e instanceof SeraelEntity) && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())));
    }

    private void sound(SoundEvent s, float vol, float pitch) {
        getWorld().playSound(null, getX(), getY() + 3, getZ(), s, SoundCategory.HOSTILE, vol, pitch);
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
        return getBoundingBox().expand(4.5, 1.5, 4.5);
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
        return 0.6f;
    }

    @Override
    protected float getSoundVolume() {
        return 2.5f;
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
        dropStack(new ItemStack(Items.NETHER_STAR));
        dropStack(new ItemStack(Items.NETHERITE_SCRAP, 2 + random.nextInt(3) + lootingMultiplier));
        dropStack(new ItemStack(Items.GOLD_BLOCK, 3 + random.nextInt(3)));
    }

    /** Долгая смерть: замирает на коленях и рассыпается пеплом и лепестками. */
    @Override
    protected void updatePostDeath() {
        deathTime++;
        if (getWorld() instanceof ServerWorld w) {
            w.spawnParticles(ParticleTypes.ASH, getX(), getY() + 3, getZ(), 30, 1.5, 2.5, 1.5, 0.02);
            w.spawnParticles(deathTime % 2 == 0 ? CRIMSON : GOLD, getX(), getY() + 3, getZ(), 8, 1.5, 2.5, 1.5, 0.02);
            if (deathTime >= 80 && !isRemoved()) {
                w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
                w.spawnParticles(PETAL, getX(), getY() + 3, getZ(), 200, 3, 3, 3, 0.05);
                getWorld().sendEntityStatus(this, (byte) 60);
                remove(RemovalReason.KILLED);
            }
        }
    }
}
