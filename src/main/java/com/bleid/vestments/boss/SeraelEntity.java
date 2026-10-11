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
    public static final int IDLE = 0, SWING = 1, PETALS = 2, HALO = 3, DIVE = 4, BARRIER = 5, STUNNED = 6, SLASH = 7, ROAR = 8, DASH = 9, BACKSTEP = 10, EVADE = 11;
    public static final int[] DURATION = { 0, 50, 72, 46, 100, 180, 60, 22, 40, 16, 16, 12 };
    private static final TrackedData<Integer> ATTACK = DataTracker.registerData(SeraelEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> ENRAGED = DataTracker.registerData(SeraelEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> CHARGE = DataTracker.registerData(SeraelEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> STATUE = DataTracker.registerData(SeraelEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private int statueTicks;
    /** Клиент: тик, когда началось окаменение. */
    public int clientStatueStart;
    private static final TrackedData<Integer> EVENT = DataTracker.registerData(SeraelEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Vector3f> EVENT_POS = DataTracker.registerData(SeraelEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
    private static final TrackedData<Float> EVENT_ARG = DataTracker.registerData(SeraelEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** События для клиентских эффектов. */
    public static final int E_ARC = 1, E_SLASH = 2, E_PETAL_ZONE = 3, E_PETAL_FALL = 4, E_HALO_RING = 5, E_DIVE_MARK = 6, E_IMPACT = 7,
            E_SHIELD_BREAK = 8, E_SHIELD_BOOM = 9, E_ROAR = 10, E_SHIELD_HIT = 11, E_DASH = 12, E_BACKSTEP = 13, E_SHATTER = 14, E_EVADE = 15, E_BLADE_HIT = 16;
    private final Set<Entity> swingHit = new HashSet<>(), arcHit = new HashSet<>();
    private Vec3d evadeDir = Vec3d.ZERO, wander;
    private int wanderWait;
    private int queued = -1, strafeDir = 1;
    private Vec3d dashDir = Vec3d.ZERO;
    private final Set<Entity> dashHit = new HashSet<>();
    private int eventSeq;
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
        experiencePoints = 0;
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
        dataTracker.startTracking(STATUE, false);
        dataTracker.startTracking(CRUMBLE, 0);
        dataTracker.startTracking(EVENT, 0);
        dataTracker.startTracking(EVENT_POS, new Vector3f());
        dataTracker.startTracking(EVENT_ARG, 0f);
    }

    @Override
    protected void initGoals() {
        targetSelector.add(1, new RevengeGoal(this));
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

    /** Побеждена: стоит на коленях каменной статуей, пока игрок её не разобьёт. */
    public boolean statue() {
        return dataTracker.get(STATUE);
    }

    /** Вместо смерти — окаменение. */
    public void petrify() {
        if (statue()) return;
        dataTracker.set(STATUE, true);
        statueTicks = 0;
        setHealth(1f);
        setAttack(IDLE);
        shield = false;
        dataTracker.set(CHARGE, -1f);
        arc.clear(); arcBoom.clear(); burns.clear(); queued = -1;
        setNoGravity(false);
        setTarget(null);
        bossBar.setPercent(0f);
        bossBar.setVisible(false);
        forgetMe();
    }

    private static final TrackedData<Integer> CRUMBLE = DataTracker.registerData(SeraelEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private int crumbleStart;
    /** Клиент: тик начала рассыпания. */
    public int clientCrumbleStart;

    /** 0 — статуя цела; иначе идёт рассыпание. */
    public int crumble() {
        return dataTracker.get(CRUMBLE);
    }

    private void beginCrumble() {
        crumbleStart = statueTicks;
        dataTracker.set(CRUMBLE, 1);
        sound(SoundEvents.BLOCK_STONE_BREAK, 3f, 0.5f);
        sound(SoundEvents.BLOCK_DEEPSLATE_BREAK, 2f, 0.6f);
        event(E_SHATTER, getPos(), 0f);
    }

    private void shatter() {
        ServerWorld w = (ServerWorld) getWorld();
        net.minecraft.particle.BlockStateParticleEffect stone = new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState());
        w.spawnParticles(stone, getX(), getY() + 2.5, getZ(), 500, 1.2, 2.4, 1.2, 0.3);
        w.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.getDefaultState()),
                getX(), getY() + 3, getZ(), 160, 1.8, 2.6, 1.8, 0.02);
        w.spawnParticles(ParticleTypes.ASH, getX(), getY() + 3, getZ(), 200, 2, 3, 2, 0.05);
        event(E_SHATTER, getPos(), 0f);
        sound(SoundEvents.BLOCK_STONE_BREAK, 4f, 0.5f);
        sound(SoundEvents.BLOCK_DEEPSLATE_BREAK, 4f, 0.7f);
        sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.5f, 1.5f);
        // из праха остаётся только упавший рядом нимб
        Vec3d hp = local(0.8, 0.3, -1.1);
        net.minecraft.entity.ItemEntity it = new net.minecraft.entity.ItemEntity(w, hp.x, hp.y, hp.z, new ItemStack(Bosses.SERAEL_HALO));
        it.setVelocity(0, 0.15, 0);
        it.setToDefaultPickupDelay();
        w.spawnEntity(it);
        bossBar.clearPlayers();
        discard();
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

    private void event(int type, Vec3d p, float arg) {
        eventSeq++;
        dataTracker.set(EVENT_POS, new Vector3f((float) p.x, (float) p.y, (float) p.z));
        dataTracker.set(EVENT_ARG, arg);
        dataTracker.set(EVENT, (eventSeq << 4) | type);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (ATTACK.equals(data)) clientAttackStart = age;
        if (STATUE.equals(data)) clientStatueStart = age;
        if (CRUMBLE.equals(data)) clientCrumbleStart = age;
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
        if (statue()) {
            statueTicks++;
            setTarget(null);
            if (statueTicks % 10 == 0) forgetMe();
            setVelocity(getVelocity().multiply(0, 1, 0));
            if (statueTicks == 20) {
                // клинок вонзается в землю перед ней
                sound(SoundEvents.BLOCK_ANVIL_LAND, 2f, 0.5f);
                Vec3d c = local(0.3, 0, 2.2);
                ((ServerWorld) getWorld()).spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK,
                        getWorld().getBlockState(net.minecraft.util.math.BlockPos.ofFloored(c.x, getY() - 0.5, c.z))), c.x, getY() + 0.1, c.z, 40, 0.4, 0.1, 0.4, 0.2);
            }
            if (statueTicks >= 34 && statueTicks <= 74 && statueTicks % 6 == 0) sound(SoundEvents.BLOCK_STONE_PLACE, 2f, 0.6f + statueTicks * 0.008f);
            if (statueTicks == 76) sound(SoundEvents.BLOCK_DEEPSLATE_PLACE, 3f, 0.5f);
            return;
        }
        bossBar.setPercent(getHealth() / getMaxHealth());
        LivingEntity t = getTarget();
        if (t != null && (!BossBase.prey(this, t) || squaredDistanceTo(t) > 64 * 64)) {
            setTarget(null);
            t = null;
        }
        // враждебна ко всем: агрится на ближайшее существо, игроки чуть в приоритете
        if (age % 10 == 0) {
            LivingEntity near = BossBase.nearestPrey(this, 40);
            if (near != null && (t == null || near != t && BossBase.preyDist(this, near) + 2.5 < BossBase.preyDist(this, t))) {
                setTarget(near);
                t = near;
            }
        }
        cds.replaceAll((k, v) -> Math.max(0, v - 1));
        if (attack() == IDLE && ready(EVADE)) dodgeProjectiles();
        int a = attack();
        if (!enraged() && getHealth() < getMaxHealth() * 0.5f && a != BARRIER && a != DIVE) {
            dataTracker.set(ENRAGED, true);
            setAttack(ROAR);
            a = ROAR;
            sound(SoundEvents.ENTITY_GHAST_SCREAM, 4f, 1.3f);
            sound(SoundEvents.ENTITY_VEX_CHARGE, 3f, 0.75f);
            sound(SoundEvents.ENTITY_ALLAY_HURT, 3f, 0.6f);
        }
        if (a != IDLE) {
            attackTick++;
            runAttack(a, t);
            int end = a == DIVE && impactTick >= 0 ? impactTick + 26 : DURATION[a];
            if (attack() == a && attackTick >= end) {
                if (a == BARRIER && shield) detonateShield();
                setNoGravity(false);
                setAttack(IDLE);
                cooldown = queued >= 0 ? 1 : enraged() ? 5 : 11;
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
        if (queued >= 0) {
            int q = queued;
            queued = -1;
            if (q != SWING && q != SLASH || d < 6.5) { begin(q); return; }
        }
        // рывок к далёкой цели с ударом в конце; отскок от слишком близкой с дальней атакой
        if (d > 8.5 && ready(DASH) && random.nextFloat() < 0.55f) {
            cds.put(DASH, enraged() ? 40 : 70);
            queued = random.nextBoolean() ? SLASH : SWING;
            begin(DASH);
            return;
        }
        if (d < 3.2 && ready(BACKSTEP) && random.nextFloat() < 0.4f) {
            cds.put(BACKSTEP, enraged() ? 60 : 100);
            queued = ready(PETALS) ? PETALS : ready(HALO) ? HALO : ready(DIVE) ? DIVE : -1;
            begin(BACKSTEP);
            return;
        }
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
        // связки: удар сверху → широкий взмах и наоборот, после пике — дождь лепестков
        float chain = enraged() ? 0.7f : 0.45f;
        if (pick == SLASH && random.nextFloat() < chain) queued = SWING;
        else if (pick == SWING && random.nextFloat() < chain * 0.6f) queued = SLASH;
        else if (pick == DIVE && ready(PETALS) && random.nextFloat() < chain) queued = PETALS;
        else if (pick == HALO && random.nextFloat() < chain) queued = SLASH;
        begin(pick);
    }

    private void begin(int pick) {
        lastAttack = pick;
        setAttack(pick);
        float m = enraged() ? 0.6f : 1f;
        switch (pick) {
            case SWING -> sound(SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, 2f, 0.6f);
            case SLASH -> sound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 2f, 0.5f);
            case PETALS -> { cds.put(PETALS, (int) (140 * m)); sound(SoundEvents.BLOCK_CHERRY_LEAVES_BREAK, 3f, 0.5f); }
            case HALO -> { cds.put(HALO, (int) (150 * m)); sound(SoundEvents.BLOCK_BEACON_ACTIVATE, 3f, 1.4f); }
            case DIVE -> { cds.put(DIVE, (int) (190 * m)); sound(SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 3f, 0.7f); }
            case BARRIER -> {
                cds.put(BARRIER, (int) (560 * m));
                shield = true;
                charge = 0;
                dataTracker.set(CHARGE, 0f);
                sound(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 3f, 0.5f);
            }
            case DASH -> sound(SoundEvents.ENTITY_PHANTOM_SWOOP, 2.5f, 0.7f);
            case BACKSTEP -> sound(SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 2.5f, 1.1f);
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
                if (k == 11) { lunge(0.45); swingHit.clear(); }
                if (k == 14) { event(E_ARC, getPos(), getYaw()); arcHit.clear(); }
                if (k >= 14 && k <= 19) bladeSweep(w, (k - 14) / 5f, rage);
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
                if (k == 16) sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 3f, 0.45f);
            }
            case SLASH -> {
                if (k == 8) lunge(0.55);
                if (k == 11) {
                    sound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 2.5f, 0.5f);
                    Vec3d c = local(0, 0.2, 3.2);
                    event(E_SLASH, new Vec3d(c.x, ground(c), c.z), getYaw());
                    for (LivingEntity e : enemies(new Box(c, c).expand(2.6, 3, 2.6))) {
                        e.damage(getDamageSources().mobAttack(this), rage ? 19 : 14);
                        knock(e, 0.8, 0.5);
                    }
                }
            }
            case PETALS -> {
                double R = rage ? 8.5 : 7;
                if (k == 8 && t != null) {
                    petalZone = new Vec3d(t.getX(), ground(t.getPos()), t.getZ());
                    event(E_PETAL_ZONE, petalZone, (float) R);
                }
                if (petalZone == null) break;
                if (k == 48) event(E_PETAL_FALL, petalZone, (float) R);
                if (k > 8 && k < 48) {
                    if (k % 2 == 0) {
                        double ang = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * R;
                        w.spawnParticles(PETAL, petalZone.x + Math.cos(ang) * r, petalZone.y + 6 + random.nextDouble() * 2.5,
                                petalZone.z + Math.sin(ang) * r, 1, 0, 0, 0, 0);
                    }
                }
                if (k == 46) sound(SoundEvents.ITEM_TRIDENT_RIPTIDE_1, 3f, 1.3f);
                if (k >= 48 && k < 70) {
                    int n = rage ? 11 : 7;
                    for (int i = 0; i < n; i++) {
                        double ang = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * R;
                        double x = petalZone.x + Math.cos(ang) * r, z = petalZone.z + Math.sin(ang) * r;
                        double gy = ground(new Vec3d(x, petalZone.y + 3, z));
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
                    sound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 2.5f, 0.6f + k * 0.03f);
                }
                if (k == 30) {
                    sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 2f, 1.8f);
                    sound(SoundEvents.BLOCK_BEACON_POWER_SELECT, 3f, 1.6f);
                    waveHit.clear();
                    event(E_HALO_RING, getPos(), rage ? 12f : 10.2f);
                }
                if (k >= 30 && k <= 42) {
                    double r = (k - 30) * (rage ? 1.0 : 0.85);
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
                    if (k == 34 && t != null) {
                        diveMark = new Vec3d(t.getX(), ground(t.getPos()), t.getZ());
                        event(E_DIVE_MARK, diveMark, 0f);
                    }
                    if (k == 39) sound(SoundEvents.ENTITY_BLAZE_SHOOT, 3f, 0.5f);
                } else if (impactTick < 0) {
                    if (diveMark == null) diveMark = t != null ? t.getPos() : getPos().add(0, -10, 0);
                    Vec3d dir = diveMark.subtract(getPos());
                    double len = dir.length();
                    setVelocity(dir.multiply(Math.min(2.0, len) / Math.max(1e-3, len)));
                    if (len < 1.6 || isOnGround() || k > 75) impact(w, rage);
                }
            }
            case BARRIER -> {
                setVelocity(getVelocity().multiply(0, 1, 0));
                if (k % 20 == 0) sound(SoundEvents.BLOCK_BEACON_AMBIENT, 2f, 0.5f);
                // под барьером медленно восстанавливает здоровье (~7% за всё время)
                if (k % 10 == 0 && shield) {
                    heal(getMaxHealth() * 0.004f);
                    if (k % 20 == 0) sound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 0.7f);
                }
            }
            case DASH -> {
                if (k == 3 && t != null) {
                    Vec3d d = new Vec3d(t.getX() - getX(), 0, t.getZ() - getZ());
                    dashDir = d.lengthSquared() > 1e-4 ? d.normalize() : forward3();
                    dashHit.clear();
                    event(E_DASH, getPos(), getYaw());
                }
                if (k >= 3 && k <= 11) {
                    boolean near = t != null && squaredDistanceTo(t) < 2.8 * 2.8;
                    double sp = near ? 0.2 : (rage ? 1.45 : 1.25);
                    // летит на крыльях низко над землёй
                    setNoGravity(true);
                    double gy = ground(getPos());
                    double vy = MathHelper.clamp((gy + 0.45 - getY()) * 0.35, -0.3, 0.3);
                    setVelocity(dashDir.x * sp, vy, dashDir.z * sp);
                    if (k == 4) sound(SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 2.5f, 1.2f);
                    for (LivingEntity e : enemies(getBoundingBox().expand(0.6))) {
                        if (dashHit.add(e)) {
                            e.damage(getDamageSources().mobAttack(this), rage ? 9 : 6);
                            knock(e, 0.9, 0.35);
                        }
                    }
                    if (near && k < 9) attackTick = 11;
                }
                if (k == 12) setNoGravity(false);
            }
            case BACKSTEP -> {
                if (k == 2) {
                    Vec3d b = t != null ? new Vec3d(getX() - t.getX(), 0, getZ() - t.getZ()) : forward3().negate();
                    b = b.lengthSquared() > 1e-4 ? b.normalize() : forward3().negate();
                    setVelocity(b.x * 1.05, 0.45, b.z * 1.05);
                    event(E_BACKSTEP, getPos(), getYaw());
                    for (LivingEntity e : enemies(getBoundingBox().expand(2.5))) knock(e, 0.7, 0.2);
                }
            }
            case EVADE -> {
                // уворот рывком на крыльях вбок: короткий полёт над землёй
                if (k <= 7) {
                    setNoGravity(true);
                    double sp = k <= 4 ? 1.15 : 0.6;
                    double vy = MathHelper.clamp((ground(getPos()) + 0.6 - getY()) * 0.3, -0.25, 0.25);
                    setVelocity(evadeDir.x * sp, vy, evadeDir.z * sp);
                }
                if (k == 8) setNoGravity(false);
            }
            case ROAR -> {
                if (k == 10) {
                    event(E_ROAR, getPos(), 0f);
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
        w.spawnParticles(ParticleTypes.LAVA, c.x, c.y + 0.5, c.z, 30, 2, 0.3, 2, 0);
        event(E_IMPACT, new Vec3d(c.x, ground(c), c.z), 0f);
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
        event(E_SHIELD_BOOM, getPos(), 0f);
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
        for (LivingEntity e : enemies(getBoundingBox().expand(5))) knock(e, 1.0, 0.3);
        setAttack(STUNNED);
        event(E_SHIELD_BREAK, getPos(), 0f);
    }

    private void tickArc() {
        if (arc.isEmpty()) return;
        ServerWorld w = (ServerWorld) getWorld();
        boolean any = false;
        for (int i = arc.size() - 1; i >= 0; i--) {
            Vec3d p = arc.get(i);
            int boom = arcBoom.get(i);
            if (age < boom) {
                if (random.nextInt(4) == 0) w.spawnParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.0);
            } else {
                any = true;
                w.spawnParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 3, 0.4, 0.4, 0.4, 0.02);
                for (LivingEntity e : enemies(new Box(p, p).expand(2.9, 3.0, 2.9))) {
                    if (!arcHit.add(e)) continue;
                    e.timeUntilRegen = 0;
                    e.damage(getDamageSources().indirectMagic(this, this), enraged() ? 16 : 12);
                    e.addStatusEffect(new StatusEffectInstance(BossEffects.EXHAUSTION, 240, 0));
                }
                arc.remove(i);
                arcBoom.remove(i);
            }
        }
        if (any && age % 2 == 0) sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.4f, 1.2f);
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
        if (a == DIVE || a == BARRIER || a == STUNNED || a == ROAR || a == DASH || a == BACKSTEP || a == EVADE) {
            if (t != null && (a == DASH || a == BACKSTEP || a == EVADE)) faceTarget(t, 14f);
            if (a == DASH || a == BACKSTEP || a == EVADE) return;
            if (t != null && a == DIVE) faceTarget(t, 8f);
            if (a != DIVE) setVelocity(getVelocity().multiply(0.5, 1, 0.5));
            return;
        }
        Vec3d v = getVelocity();
        double vx = 0, vz = 0;
        if (t == null && a == IDLE) {
            // без цели — неспешно прохаживается из стороны в сторону и осматривается
            if (wander == null || --wanderWait <= 0 && wander.squaredDistanceTo(getX(), wander.y, getZ()) < 1.0) {
                if (wanderWait <= 0) {
                    double ang = random.nextDouble() * Math.PI * 2, r = 2.5 + random.nextDouble() * 4.5;
                    wander = new Vec3d(getX() + Math.cos(ang) * r, getY(), getZ() + Math.sin(ang) * r);
                    wanderWait = 60 + random.nextInt(80);
                }
            }
            if (wander != null) {
                double dx = wander.x - getX(), dz = wander.z - getZ(), d = Math.sqrt(dx * dx + dz * dz);
                if (d > 0.8) {
                    vx = dx / d * 0.11;
                    vz = dz / d * 0.11;
                    float yaw = (float) (MathHelper.atan2(dz, dx) * 180 / Math.PI) - 90f;
                    setYaw(MathHelper.stepUnwrappedAngleTowards(getYaw(), yaw, 5f));
                    bodyYaw = getYaw();
                    headYaw = MathHelper.stepUnwrappedAngleTowards(headYaw, yaw, 6f);
                } else if (age % 40 == 0) {
                    headYaw = getYaw() + (random.nextFloat() - 0.5f) * 70f;
                }
            }
        }
        if (t != null) {
            faceTarget(t, a == SWING || a == SLASH ? 4f : 10f);
            double want = a == PETALS || a == HALO ? 5.5 : 3.2;
            double dx = t.getX() - getX(), dz = t.getZ() - getZ(), d = Math.sqrt(dx * dx + dz * dz);
            boolean busy = (a == SWING || a == SLASH) && attackTick > 6;
            double sp = enraged() ? 0.28 : 0.23;
            if (d > want && !busy) {
                vx = dx / d * sp;
                vz = dz / d * sp;
            }
            // в ожидании атаки и во время «Дождя лепестков» кружит вокруг цели
            if (!busy && d < 10 && (a == IDLE || a == PETALS)) {
                if (age % 70 == 0) strafeDir = random.nextBoolean() ? 1 : -1;
                double s2 = sp * 0.55 * strafeDir;
                vx += -dz / d * s2;
                vz += dx / d * s2;
            }
        }
        double nx = MathHelper.lerp(0.25, v.x, vx), nz = MathHelper.lerp(0.25, v.z, vz), ny = v.y;
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
        if (statue()) {
            boolean byPlayer = source.getAttacker() instanceof PlayerEntity;
            if (source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD) || source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL)) shatter();
            else if (byPlayer && statueTicks > 76) shatter();
            else if (byPlayer) sound(SoundEvents.BLOCK_STONE_HIT, 1.5f, 0.8f);
            return false;
        }
        if (shield && !source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)) {
            charge += amount;
            dataTracker.set(CHARGE, Math.min(1f, charge / SHIELD_HP));
            event(E_SHIELD_HIT, source.getPosition() != null ? source.getPosition() : getPos().add(0, 3, 0), charge / SHIELD_HP);
            sound(SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 2f, 0.6f + charge / SHIELD_HP);
            if (charge >= SHIELD_HP) breakShield();
            return false;
        }
        int at = attack();
        if (at == EVADE && attackTick <= 6) return false;            // увернулась
        Entity src = source.getAttacker();
        boolean canDodge = ready(EVADE) && (at == IDLE || at != STUNNED && at != BARRIER && at != DIVE && at != ROAR
                && attackTick > DURATION[at] * 0.7f) && src != null && !source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD);
        if (canDodge) {
            boolean ranged = source.getSource() != null && source.getSource() != src;
            if (random.nextFloat() < (ranged ? 0.4f : enraged() ? 0.38f : 0.28f)) {
                Vec3d from = (ranged ? source.getSource() : src).getPos();
                evade(new Vec3d(getX() - from.x, 0, getZ() - from.z), ranged ? source.getSource().getVelocity() : null);
                return false;
            }
        }
        if (at == STUNNED) amount *= 1.5f;
        return super.damage(source, amount);
    }

    // ================================================================== вспомогательное

    private Vec3d local(double right, double up, double fwd) {
        float y = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        Vec3d f = new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y));
        Vec3d r = new Vec3d(-MathHelper.cos(y), 0, -MathHelper.sin(y));
        return getPos().add(f.multiply(fwd)).add(r.multiply(right)).add(0, up, 0);
    }

    /** Широкий взмах: клинок как отрезок от плеча до острия, проходящий справа налево; задевает всех на пути. */
    private void bladeSweep(ServerWorld w, float f, boolean rage) {
        double ang = Math.toRadians(-80 + 160 * f);
        Vec3d sh = local(-0.7, 4.4, 0.2);
        Vec3d tip = local(Math.sin(ang) * -6.2, 1.6, Math.cos(ang) * 6.2);
        for (LivingEntity e : enemies(new Box(sh, tip).expand(1.4))) {
            if (swingHit.contains(e)) continue;
            Box b = e.getBoundingBox().expand(0.6);
            boolean hit = false;
            for (int i = 2; i <= 10 && !hit; i++) {
                Vec3d q = sh.lerp(tip, i / 10.0);
                hit = b.contains(q);
            }
            if (!hit) continue;
            swingHit.add(e);
            e.damage(getDamageSources().mobAttack(this), rage ? 24 : 18);
            knock(e, 0.55, 0.25);
            Vec3d c = e.getPos().add(0, e.getHeight() * 0.6, 0);
            w.spawnParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.4);
            event(E_BLADE_HIT, c, 0f);
            sound(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 2f, 0.6f);
        }
    }

    /** Уворот рывком на крыльях вбок от угрозы (from — направление от угрозы к боссу). */
    private void evade(Vec3d away, Vec3d threatVel) {
        Vec3d side;
        if (threatVel != null && threatVel.horizontalLengthSquared() > 1e-4) {
            Vec3d tv = new Vec3d(threatVel.x, 0, threatVel.z).normalize();
            side = new Vec3d(-tv.z, 0, tv.x);
        } else {
            Vec3d a = away.horizontalLengthSquared() > 1e-4 ? away.normalize() : forward3().negate();
            side = new Vec3d(-a.z, 0, a.x);
        }
        if (random.nextBoolean()) side = side.negate();
        Vec3d back = away.horizontalLengthSquared() > 1e-4 ? away.normalize() : Vec3d.ZERO;
        evadeDir = side.multiply(0.85).add(back.multiply(0.45)).normalize();
        cds.put(EVADE, enraged() ? 34 : 50);
        queued = -1;
        setAttack(EVADE);
        event(E_EVADE, getPos(), 0f);
        sound(SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 2.2f, 1.35f);
        sound(SoundEvents.ENTITY_PHANTOM_SWOOP, 1.6f, 1.3f);
    }

    /** Видит летящий в неё снаряд — уходит рывком в сторону. */
    private void dodgeProjectiles() {
        for (net.minecraft.entity.projectile.ProjectileEntity pr : getWorld().getEntitiesByClass(net.minecraft.entity.projectile.ProjectileEntity.class,
                getBoundingBox().expand(12), pr -> pr.getOwner() != this && !pr.isOnGround())) {
            Vec3d v = pr.getVelocity();
            if (v.lengthSquared() < 0.25) continue;
            Vec3d to = getPos().add(0, 3, 0).subtract(pr.getPos());
            double d = to.length();
            if (d > 10 || d < 1.5) continue;
            if (v.normalize().dotProduct(to.normalize()) < 0.93) continue;
            if (random.nextFloat() < (enraged() ? 0.75f : 0.55f)) evade(new Vec3d(-v.x, 0, -v.z), v);
            else cds.put(EVADE, 12);
            return;
        }
    }

    private void lunge(double s) {
        Vec3d f = forward3();
        setVelocity(getVelocity().add(f.x * s, 0, f.z * s));
    }

    private Vec3d forward3() {
        float y = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y));
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
        return getWorld().getEntitiesByClass(LivingEntity.class, box, e -> BossBase.prey(this, e));
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
        return statue() ? null : SoundEvents.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return statue() ? null : SoundEvents.ENTITY_VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return statue() ? null : SoundEvents.ENTITY_WITHER_DEATH;
    }

    /** Статуя — не цель: обычные мобы её не замечают и не атакуют. */
    @Override
    public boolean canTakeDamage() {
        return !statue() && super.canTakeDamage();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return !statue() && super.canBeHitByProjectile();
    }

    /** Мобы, уже нацеленные на неё, теряют цель. */
    private void forgetMe() {
        for (net.minecraft.entity.mob.MobEntity m : getWorld().getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class,
                getBoundingBox().expand(48), m -> m.getTarget() == this)) {
            m.setTarget(null);
            if (m instanceof net.minecraft.entity.mob.Angerable an) an.stopAnger();
        }
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
        if (nbt.getBoolean("Statue")) { dataTracker.set(STATUE, true); statueTicks = 100; bossBar.setVisible(false); }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Enraged", enraged());
        nbt.putBoolean("Statue", statue());
    }

    @Override
    protected void dropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops) {
        super.dropEquipment(source, lootingMultiplier, allowDrops);
        dropStack(new ItemStack(Bosses.SERAEL_HALO));
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
