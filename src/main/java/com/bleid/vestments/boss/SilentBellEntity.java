package com.bleid.vestments.boss;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Безмолвный Колокол — вторая стадия. Парящий бронзовый колокол с глазом в трещине.
 * Урон проходит только при попадании в разлом с глазом; после атаки глаз открыт — урон по нему ×1,5.
 * Атаки: конус звуковой волны, падение-ловушка (тень под ним; пойманный выбирается тремя ударами по глазу),
 * хор призрачных певчих (лечат колокол, пока живы). На трети здоровья трещина расширяется:
 * гул, ползущие по арене звуковые кольца, атаки чаще, окна глаза короче.
 * Новые атаки: набатный таран, вихрь цепей, тройной набат (звуковые сферы), погребальные скачки,
 * призрачный звон (колокола-призраки падают на отмеченные круги). Атаки идут сериями по 2–3 с рывками
 * между ними; глаз открывается только в конце серии.
 */
public class SilentBellEntity extends BossBase {
    public static final int IDLE = 0, RING = 1, DROP = 2, CHOIR = 3, RAM = 4, SPIN = 5, VOLLEY = 6, HOPS = 7, KNELL = 8;
    public static final int S_AWAKEN = 0, S_FIGHT = 1, S_DYING = 2, S_STONE = 3;
    /** Пробуждение: тишина, удар колокола, открытие глаза, подъём. */
    public static final int A_TOLL = 50, A_EYE = 60, A_RISEN = 100;
    public static final int E_AWAKE = 1, E_CLANG = 2, E_CONE_CHARGE = 3, E_CONE = 4, E_SHADOW = 5, E_DROP_LAND = 6, E_TRAP = 7,
            E_TRAP_HIT = 8, E_RELEASE = 9, E_CHOIR = 10, E_HEAL = 11, E_GROUND_RING = 12, E_EYE_OPEN = 13, E_WIDEN = 14,
            E_SPLIT = 15, E_DARK = 16, E_RING_HIT = 17, E_RAM_CHARGE = 18, E_RAM_WALL = 19, E_SPIN = 20, E_CHAIN_HIT = 21,
            E_ORB_FIRE = 22, E_ORB_BURST = 23, E_HOP_LAND = 24, E_PHANTOM_MARK = 25, E_PHANTOM_FALL = 26, E_DASH = 27, E_RAM_HIT = 28,
            E_STONE_HIT = 29;
    /** Смерть: колокол каменеет за STONE_T тактов; затем удар или стрела — взрыв силой STONE_BLAST. */
    public static final int STONE_T = 40;
    public static final float STONE_BLAST = 7f;
    /** Скорость звуковых сфер, их жизнь; радиус вихря цепей; круг призрачного колокола. */
    /** Колокол вдвое больше исходной модели. */
    public static final double S = 2.0;
    public static final float ORB_SPEED = 1.3f, SPIN_R = 11f, PHANTOM_R = 4.8f;
    public static final int ORB_LIFE = 50, SPIN_UP = 10, KNELL_MARK = 4, KNELL_FALL = 30;
    public static final float CONE_DEG = 35f, CONE_LEN = 26f, GROUND_RING_SPEED = 0.5f, GROUND_RING_MAX = 32f;
    /** Высота глаза над ногами сущности (блоки). */
    public static final double EYE_Y = 3.9 * S;

    private static final TrackedData<Integer> STATE = DataTracker.registerData(SilentBellEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> EYE = DataTracker.registerData(SilentBellEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> WIDE = DataTracker.registerData(SilentBellEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> TRAPPED = DataTracker.registerData(SilentBellEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public int clientStateStart, clientEyeChange;

    private int stateTicks, eyeTicks, dropPhase, trapTicks, trapHits, riseTicks;
    private double groundY;
    private LivingEntity trapped;
    private final List<UUID> choir = new ArrayList<>();
    private final Set<Integer> hit = new HashSet<>();
    private final List<Ring> rings = new ArrayList<>();
    private boolean coneFired2, done;
    private int combo, phase, phaseTick, dashes, dashTicks, orbitDir = 1;
    private Vec3d dashDir = Vec3d.ZERO, hopFrom = Vec3d.ZERO, hopTo = Vec3d.ZERO;
    private final List<Orb> orbs = new ArrayList<>();
    private final List<Vec3d> spots = new ArrayList<>();
    private final java.util.Map<Integer, Integer> lastHit = new java.util.HashMap<>();

    private static final class Orb {
        Vec3d p;
        final Vec3d v;
        int life;

        Orb(Vec3d p, Vec3d v) {
            this.p = p;
            this.v = v;
        }
    }

    private static final class Ring {
        final Vec3d c;
        float r;
        final float speed, max, dmg;
        final Set<Integer> hit = new HashSet<>();

        Ring(Vec3d c) {
            this(c, GROUND_RING_SPEED, GROUND_RING_MAX, 6f, 0f);
        }

        Ring(Vec3d c, float speed, float max, float dmg, float r0) {
            this.c = c;
            this.speed = speed;
            this.max = max;
            this.dmg = dmg;
            this.r = r0;
        }
    }

    @Override
    public double scale() {
        return S;
    }

    public SilentBellEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.GREEN);
        setNoGravity(true);
        experiencePoints = 0;
        bossBar.setVisible(false);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 2100)
                .add(EntityAttributes.GENERIC_ARMOR, 6)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(STATE, S_AWAKEN);
        dataTracker.startTracking(EYE, false);
        dataTracker.startTracking(WIDE, false);
        dataTracker.startTracking(TRAPPED, -1);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (STATE.equals(data)) clientStateStart = age;
        if (EYE.equals(data)) clientEyeChange = age;
    }

    public int state() {
        return dataTracker.get(STATE);
    }

    public boolean eyeOpen() {
        return dataTracker.get(EYE);
    }

    public boolean widened() {
        return dataTracker.get(WIDE);
    }

    public int trappedId() {
        return dataTracker.get(TRAPPED);
    }

    private void setState(int s) {
        dataTracker.set(STATE, s);
        stateTicks = 0;
    }

    private void setEye(boolean open) {
        if (eyeOpen() == open) return;
        dataTracker.set(EYE, open);
        if (open) {
            event(E_EYE_OPEN, eyePos(), 0);
            sound(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.5f, 0.5f);
        } else {
            sound(SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, 1.5f, 0.4f);
        }
    }

    public Vec3d eyePos() {
        return getPos().add(forward().multiply(0.5 * S)).add(0, EYE_Y, 0);
    }

    // ================================================================== ИИ

    @Override
    protected void mobTick() {
        super.mobTick();
        stateTicks++;
        if (age % 10 == 0 && state() != S_FIGHT) forgetMe();
        switch (state()) {
            case S_AWAKEN -> awaken();
            case S_DYING, S_STONE -> tickDying();
            default -> fight();
        }
    }

    private void awaken() {
        int k = stateTicks;
        if (k == 1) groundY = getY();
        setVelocity(Vec3d.ZERO);
        if (k == A_TOLL) {
            sound(SoundEvents.BLOCK_BELL_USE, 6f, 0.35f);
            sound(SoundEvents.BLOCK_BELL_RESONATE, 5f, 0.4f);
            sound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 4f, 0.5f);
            event(E_AWAKE, getPos(), 0);
        }
        if (k == A_EYE) {
            setEye(true);
            bossBar.setPercent(1f);
            bossBar.setVisible(true);
            sound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 2f, 0.4f);
        }
        if (k > A_EYE && k <= A_RISEN) {
            float s = (k - A_EYE) / (float) (A_RISEN - A_EYE);
            setPosition(getX(), groundY + 0.6 * S * s * s * (3 - 2 * s), getZ());
        }
        if (k >= A_RISEN) {
            setState(S_FIGHT);
            setEye(false);
            cooldown = 20;
        }
    }

    private void fight() {
        bossBar.setPercent(getHealth() / getMaxHealth());
        LivingEntity t = pickTarget();
        cds.replaceAll((k, v) -> Math.max(0, v - 1));
        boolean wide = widened();
        if (!wide && getHealth() < getMaxHealth() / 3f) {
            dataTracker.set(WIDE, true);
            wide = true;
            sound(SoundEvents.BLOCK_BELL_USE, 6f, 0.3f);
            sound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 3f, 0.5f);
            sound(SoundEvents.BLOCK_DEEPSLATE_BREAK, 3f, 0.4f);
            event(E_WIDEN, eyePos(), 0);
        }
        if (wide) {
            if (age % 40 == 0) sound(SoundEvents.BLOCK_BEACON_AMBIENT, 3f, 0.5f);
            if (age % 60 == 0) {
                Vec3d c = new Vec3d(getX(), ground(getPos()), getZ());
                rings.add(new Ring(c));
                event(E_GROUND_RING, c, 0);
                soundAt(c, SoundEvents.BLOCK_BELL_RESONATE, 1.5f, 0.5f);
            }
        }
        tickRings();
        tickChoir();
        tickOrbs();
        int a = attack();
        if (eyeTicks > 0) {
            if (--eyeTicks == 0) {
                setEye(false);
                cooldown = wide ? 3 : 8;
                if (t != null) sidestep(t);                     // сразу рывок в сторону — без передышки
            }
        } else if (a != IDLE) {
            attackTick++;
            runAttack(a, t);
            if (attack() == a && attackTick >= duration(a)) endAttack(a);
        } else if (t != null && --cooldown <= 0) {
            startAttack(t);
        }
        steer(t, attack());
    }

    private int duration(int a) {
        return switch (a) {
            case RING -> coneFire(widened()) + 16;
            case DROP -> dropPhase >= 4 ? 0 : Integer.MAX_VALUE;
            case CHOIR -> 30;
            case RAM, SPIN, VOLLEY, HOPS, KNELL -> done ? 0 : Integer.MAX_VALUE;
            default -> 0;
        };
    }

    /**
     * Атаки идут сериями: 2 подряд (3 после расширения трещины) с короткими рывками между ними.
     * Глаз открывается только в конце серии — это окно для ударов.
     */
    private void endAttack(int a) {
        setAttack(IDLE);
        lastAttack = a;
        boolean escaped = a == DROP && trapHits >= 3;
        int max = widened() ? 3 : 2;
        if (!escaped && ++combo < max) {
            cooldown = widened() ? 3 : 5;
            LivingEntity t = getTarget();
            if (t != null && a != SPIN && a != HOPS) sidestep(t);
            trapHits = 0;
            return;
        }
        combo = 0;
        int open = widened() ? 28 : 40;
        if (escaped) open = 80;                              // выбрались — колокол оглушён
        eyeTicks = open;
        setEye(true);
        trapHits = 0;
    }

    private void startAttack(LivingEntity t) {
        double d = distanceTo(t);
        boolean wide = widened();
        if (choir.isEmpty() && ready(CHOIR) && random.nextFloat() < 0.25f) {
            cds.put(CHOIR, wide ? 420 : 560);
            begin(CHOIR);
            return;
        }
        // взвешенный выбор: вдали — таран, сферы, призраки, скачки; вблизи — вихрь, скачки, конус, падение
        int[] ids = { RING, DROP, RAM, SPIN, VOLLEY, HOPS, KNELL };
        int[] far = { 1, 1, 4, 0, 3, 3, 3 };
        int[] near = { 3, 3, 1, 4, 1, 2, 1 };
        int[] cd = { 0, 160, 50, 130, 60, 110, 150 };
        int sum = 0;
        int[] w = new int[ids.length];
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == lastAttack || !ready(ids[i])) continue;
            w[i] = d > 9 ? far[i] : near[i];
            sum += w[i];
        }
        int pick = RING;
        if (sum > 0) {
            int r = random.nextInt(sum);
            for (int i = 0; i < ids.length; i++) {
                r -= w[i];
                if (r < 0) {
                    pick = ids[i];
                    if (cd[i] > 0) cds.put(ids[i], wide ? cd[i] * 7 / 10 : cd[i]);
                    break;
                }
            }
        }
        begin(pick);
    }

    /** Короткий рывок вбок (меняет сторону облёта). */
    private void sidestep(LivingEntity t) {
        Vec3d to = new Vec3d(t.getX() - getX(), 0, t.getZ() - getZ());
        if (to.lengthSquared() < 1e-3) to = forward();
        to = to.normalize();
        if (random.nextFloat() < 0.5f) orbitDir = -orbitDir;
        Vec3d side = new Vec3d(-to.z, 0, to.x).multiply(orbitDir);
        double d = distanceTo(t);
        double toward = d > 9 * S ? 0.6 : d < 4 * S ? -0.5 : 0.0;
        dashDir = side.add(to.multiply(toward)).normalize();
        dashTicks = 6;
        event(E_DASH, getPos().add(0, 2.5 * S, 0), (float) (Math.atan2(dashDir.z, dashDir.x) * 180 / Math.PI));
        sound(SoundEvents.ENTITY_PHANTOM_FLAP, 2f, 0.5f);
        sound(SoundEvents.BLOCK_CHAIN_HIT, 1.5f, 0.6f);
    }

    private void begin(int a) {
        hit.clear();
        dropPhase = 0;
        trapTicks = 0;
        trapHits = 0;
        riseTicks = 0;
        coneFired2 = false;
        done = false;
        phase = 0;
        phaseTick = 0;
        dashes = 0;
        spots.clear();
        lastHit.clear();
        setAttack(a);
        if (a == DROP) {
            event(E_SHADOW, getPos(), 0);
            sound(SoundEvents.BLOCK_BELL_RESONATE, 2f, 0.8f);
        }
    }

    private void runAttack(int a, LivingEntity t) {
        switch (a) {
            case RING -> cone(attackTick, t);
            case DROP -> drop(attackTick, t);
            case CHOIR -> choirAttack(attackTick, t);
            case RAM -> ram(t);
            case SPIN -> spin(attackTick, t);
            case VOLLEY -> volley(attackTick, t);
            case HOPS -> hops(t);
            case KNELL -> knell(attackTick, t);
            default -> { }
        }
    }

    public static int coneFire(boolean wide) {
        return wide ? 12 : 16;
    }

    private void cone(int k, LivingEntity t) {
        int fire = coneFire(widened());
        if (k < fire - 2 && t != null) faceTarget(t, 10f);
        if (k == 2) {
            event(E_CONE_CHARGE, eyePos(), fire - 2);
            sound(SoundEvents.BLOCK_BEACON_POWER_SELECT, 2f, 0.5f);
        }
        if (k == fire) {
            event(E_CONE, eyePos(), getYaw());
            sound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 4f, 0.6f);
            sound(SoundEvents.BLOCK_BELL_USE, 4f, 0.5f);
        }
        if (k >= fire && k <= fire + 12) {
            double r = ((k - fire) * 1.15 + 1.5) * S;
            Vec3d o = getPos().add(0, EYE_Y, 0), f = forward();
            double cos = Math.cos(Math.toRadians(CONE_DEG));
            for (LivingEntity e : enemies(getBoundingBox().expand(CONE_LEN + 1, 8 * S, CONE_LEN + 1))) {
                if (hit.contains(e.getId())) continue;
                Vec3d to = e.getPos().add(0, e.getHeight() * 0.5, 0).subtract(o);
                double hd = Math.sqrt(to.x * to.x + to.z * to.z);
                if (hd > r || hd > CONE_LEN || Math.abs(to.y) > 6 * S) continue;
                if (hd > 1.2 * S && (to.x * f.x + to.z * f.z) / hd < cos) continue;
                hit.add(e.getId());
                e.damage(getDamageSources().mobAttack(this), 24f);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0), this);
                knock(e, 1.3, 0.35);
            }
        }
    }

    private void drop(int k, LivingEntity t) {
        switch (dropPhase) {
            case 0 -> {                                   // подъём над целью; тень следует за ним
                if (t != null) {
                    Vec3d dst = new Vec3d(t.getX(), t.getY() + 11 * S, t.getZ());
                    Vec3d v = dst.subtract(getPos()).multiply(0.14);
                    if (v.length() > 0.9 * S) v = v.normalize().multiply(0.9 * S);
                    setVelocity(v);
                    faceTarget(t, 6f);
                }
                if (k == 20) sound(SoundEvents.BLOCK_BELL_RESONATE, 2f, 0.6f);
                if (k >= 40) {
                    dropPhase = 1;
                    sound(SoundEvents.ITEM_ELYTRA_FLYING, 1.5f, 1.6f);
                }
            }
            case 1 -> {                                   // падение
                double gy = ground(getPos());
                setVelocity(0, -2.4, 0);
                if (getY() + getVelocity().y <= gy - 1.2 * S || verticalCollision || k > 80) {
                    setVelocity(Vec3d.ZERO);
                    setPosition(getX(), gy - 1.2 * S, getZ());
                    land(gy);
                }
            }
            case 2 -> tickTrap();
            case 3 -> {                                   // подъём обратно
                riseTicks++;
                double gy = ground(getPos().add(0, 1.3 * S, 0));
                double ty = gy + 0.6 * S;
                setVelocity(0, MathHelper.clamp((ty - getY()) * 0.2, -0.5, 0.5), 0);
                if (riseTicks > 18) dropPhase = 4;
            }
            default -> { }
        }
    }

    private void land(double gy) {
        Vec3d c = new Vec3d(getX(), gy, getZ());
        sound(SoundEvents.BLOCK_ANVIL_LAND, 4f, 0.3f);
        sound(SoundEvents.BLOCK_BELL_USE, 5f, 0.4f);
        event(E_DROP_LAND, c, 0);
        com.bleid.vestments.cathedral.Cathedral.smashWalls(this, c.add(0, 2 * S, 0), 3.2 * S);
        LivingEntity caught = null;
        double best = 2.8 * S;
        for (LivingEntity e : enemies(new Box(c, c).expand(6 * S, 3 * S, 6 * S))) {
            double hd = hdist(e.getPos(), c);
            if (hd < best && Math.abs(e.getY() - gy) < 2.5 * S) {
                best = hd;
                caught = e;
            }
        }
        for (LivingEntity e : enemies(new Box(c, c).expand(6 * S, 3 * S, 6 * S))) {
            if (e == caught || hdist(e.getPos(), c) > 6 * S) continue;
            e.damage(getDamageSources().mobAttack(this), 28f);
            knock(e, 1.1, 0.5);
        }
        if (caught != null) {
            trapped = caught;
            trapTicks = 0;
            trapHits = 0;
            dataTracker.set(TRAPPED, caught.getId());
            event(E_TRAP, c, caught.getId());
            soundAt(c, SoundEvents.BLOCK_IRON_DOOR_CLOSE, 3f, 0.4f);
            dropPhase = 2;
        } else {
            dropPhase = 3;
        }
    }

    /** Пойманный под колоколом: темнота, урон каждую секунду; выбраться — трижды ударить по глазу. */
    private void tickTrap() {
        trapTicks++;
        LivingEntity v = trapped;
        setVelocity(Vec3d.ZERO);
        if (v == null || !v.isAlive() || trapTicks > 160 || trapHits >= 3) {
            release();
            return;
        }
        Vec3d c = new Vec3d(getX(), getY() + 1.2 * S, getZ());
        if (hdist(v.getPos(), c) > 0.45 || Math.abs(v.getY() - c.y) > 0.6) {
            if (v instanceof ServerPlayerEntity p) p.networkHandler.requestTeleport(c.x, c.y, c.z, p.getYaw(), p.getPitch());
            else v.refreshPositionAndAngles(c.x, c.y, c.z, v.getYaw(), v.getPitch());
        }
        v.setVelocity(Vec3d.ZERO);
        v.velocityModified = true;
        if (trapTicks % 20 == 0) {
            v.damage(getDamageSources().mobAttack(this), 4f);
            soundAt(c, SoundEvents.BLOCK_BELL_RESONATE, 1.2f, 0.5f + trapTicks * 0.003f);
        }
        if (trapTicks % 10 == 1) v.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 50, 0), this);
    }

    private void release() {
        boolean escaped = trapHits >= 3;
        dataTracker.set(TRAPPED, -1);
        if (trapped != null && trapped.isAlive()) {
            trapped.removeStatusEffect(StatusEffects.DARKNESS);
            if (escaped) knock(trapped, 0.4, 0.3);
        }
        trapped = null;
        event(E_RELEASE, getPos(), escaped ? 1 : 0);
        sound(SoundEvents.BLOCK_BELL_USE, 4f, escaped ? 1.2f : 0.5f);
        if (escaped) sound(SoundEvents.BLOCK_ANVIL_LAND, 2f, 1.4f);
        dropPhase = 3;
        riseTicks = 0;
    }

    private void choirAttack(int k, LivingEntity t) {
        if (t != null) faceTarget(t, 4f);
        if (k == 4) {
            sound(SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 3f, 0.4f);
            sound(SoundEvents.BLOCK_BELL_RESONATE, 3f, 0.7f);
        }
        if (k == 12 && getWorld() instanceof ServerWorld sw) {
            int n = 3 + random.nextInt(2);
            for (int i = 0; i < n; i++) {
                double ang = getYaw() * MathHelper.RADIANS_PER_DEGREE + i * Math.PI * 2 / n + 0.4;
                Vec3d p = getPos().add(Math.cos(ang) * 4.5 * S, 2.5 * S, Math.sin(ang) * 4.5 * S);
                VexEntity v = EntityType.VEX.create(sw);
                if (v == null) continue;
                v.refreshPositionAndAngles(p.x, p.y, p.z, random.nextFloat() * 360, 0);
                v.setCustomName(Text.translatable("entity.vestments.choir_singer"));
                v.addCommandTag("vestments_choir");
                v.setLifeTicks(20 * 45);
                v.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(18);
                v.setHealth(18);
                v.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 45, 0, false, false));
                if (t != null) v.setTarget(t);
                sw.spawnEntity(v);
                choir.add(v.getUuid());
                event(E_CHOIR, p, i);
            }
        }
    }

    /** Певчие лечат колокол, пока живы. */
    private void tickChoir() {
        if (choir.isEmpty() || !(getWorld() instanceof ServerWorld sw)) return;
        boolean pulse = age % 20 == 0;
        for (Iterator<UUID> it = choir.iterator(); it.hasNext(); ) {
            Entity e = sw.getEntity(it.next());
            if (!(e instanceof LivingEntity le) || !le.isAlive()) {
                it.remove();
                continue;
            }
            if (pulse) {
                heal(2f);
                event(E_HEAL, le.getPos().add(0, 0.5, 0), 0);
            }
        }
        if (pulse && !choir.isEmpty() && age % 60 == 0) sound(SoundEvents.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 2f, 0.45f);
    }

    /** Медленные зелёные звуковые кольца, ползущие по арене (после расширения трещины). */
    private void tickRings() {
        for (Iterator<Ring> it = rings.iterator(); it.hasNext(); ) {
            Ring g = it.next();
            g.r += g.speed;
            if (g.r > g.max) {
                it.remove();
                continue;
            }
            for (LivingEntity e : enemies(new Box(g.c, g.c).expand(g.r + 1, 2, g.r + 1))) {
                if (!e.isOnGround() || Math.abs(e.getY() - g.c.y) > 1.5 || g.hit.contains(e.getId())) continue;
                if (Math.abs(hdist(e.getPos(), g.c) - g.r) > 0.55) continue;
                g.hit.add(e.getId());
                e.damage(getDamageSources().mobAttack(this), g.dmg);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1), this);
                event(E_RING_HIT, e.getPos(), 0);
            }
        }
    }

    private void steer(LivingEntity t, int a) {
        if (a == DROP || a == RAM || a == SPIN || a == HOPS) return;   // эти атаки сами ведут колокол
        if (dashTicks > 0) {                                           // рывок между атаками
            dashTicks--;
            double gy = ground(getPos().add(0, 1, 0)) + 0.6 * S;
            double sp = (0.35 + 0.17 * dashTicks) * 1.6;
            setVelocity(dashDir.x * sp, MathHelper.clamp((gy - getY()) * 0.2, -0.5, 0.5), dashDir.z * sp);
            if (t != null) faceTarget(t, 12f);
            return;
        }
        Vec3d want;
        double bob = Math.sin(age * 0.05) * 0.25 * S;
        if (t != null) {
            if (a == IDLE || a == VOLLEY || a == KNELL) faceTarget(t, 8f);
            Vec3d away = new Vec3d(getX() - t.getX(), 0, getZ() - t.getZ());
            if (away.lengthSquared() < 1e-3) away = forward().multiply(-1);
            away = away.normalize();
            // быстро кружит вокруг цели на расстоянии ~5,5 блоков, иногда меняя направление
            if (a == IDLE && random.nextInt(70) == 0) orbitDir = -orbitDir;
            Vec3d side = new Vec3d(-away.z, 0, away.x).multiply(0.9 * orbitDir);
            Vec3d p = t.getPos().add(away.add(side).normalize().multiply(5.5 * S));
            want = new Vec3d(p.x, ground(new Vec3d(p.x, Math.max(getY(), t.getY()) + 1, p.z)) + 0.6 * S + bob, p.z);
        } else {
            want = new Vec3d(getX(), ground(getPos().add(0, 1, 0)) + 0.6 * S + bob, getZ());
        }
        Vec3d v = want.subtract(getPos()).multiply(0.12);
        double max = a == IDLE ? 0.55 : a == VOLLEY || a == KNELL || a == CHOIR ? 0.22 : 0.08;
        if (v.length() > max) v = v.normalize().multiply(max);
        setVelocity(v);
    }

    // ================================================================== новые атаки

    /** Держать колокол на высоте парения над землёй под ним. */
    private double hoverVy() {
        double gy = ground(getPos().add(0, 1, 0)) + 0.6 * S;
        return MathHelper.clamp((gy - getY()) * 0.25, -0.6, 0.6);
    }

    private boolean tooSoon(LivingEntity e, int gap) {
        Integer last = lastHit.get(e.getId());
        if (last != null && age - last < gap) return true;
        lastHit.put(e.getId(), age);
        return false;
    }

    /**
     * «Набатный таран»: колокол отступает, наклоняется и с гулом проносится сквозь арену по прямой,
     * сбивая всё на пути. Удар о стену — звон и отскок. После расширения трещины — два тарана подряд.
     */
    private void ram(LivingEntity t) {
        phaseTick++;
        int windup = dashes == 0 ? (widened() ? 10 : 14) : 7;
        switch (phase) {
            case 0 -> {                                       // замах: отплывает назад, поворачивается к цели
                if (t != null) faceTarget(t, 18f);
                Vec3d f = forward();
                setVelocity(f.x * -0.2, hoverVy(), f.z * -0.2);
                if (phaseTick == 1) {
                    event(E_RAM_CHARGE, getPos().add(0, 2.5 * S, 0), windup);
                    sound(SoundEvents.BLOCK_BELL_RESONATE, 3f, 0.6f);
                    sound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.2f, 0.5f);
                }
                if (phaseTick >= windup) {
                    Vec3d dir = forward();
                    if (t != null) {
                        Vec3d aim = t.getPos().add(t.getVelocity().multiply(6));
                        Vec3d d = new Vec3d(aim.x - getX(), 0, aim.z - getZ());
                        if (d.lengthSquared() > 1e-3) dir = d.normalize();
                    }
                    dashDir = dir;
                    setYaw((float) (MathHelper.atan2(dir.z, dir.x) * 180 / Math.PI) - 90f);
                    bodyYaw = getYaw();
                    headYaw = getYaw();
                    phase = 1;
                    phaseTick = 0;
                    hit.clear();
                    sound(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 2f, 1.2f);
                    sound(SoundEvents.BLOCK_BELL_USE, 4f, 0.7f);
                }
            }
            case 1 -> {                                       // рывок
                double sp = 1.35 * S;
                setVelocity(dashDir.x * sp, hoverVy(), dashDir.z * sp);
                for (LivingEntity e : enemies(getBoundingBox().expand(1.0 * S, 0.5 * S, 1.0 * S))) {
                    if (hit.contains(e.getId())) continue;
                    hit.add(e.getId());
                    e.damage(getDamageSources().mobAttack(this), 26f);
                    Vec3d side = new Vec3d(e.getX() - getX(), 0, e.getZ() - getZ());
                    Vec3d push = dashDir.multiply(1.3).add(side.lengthSquared() > 1e-3 ? side.normalize().multiply(0.7) : Vec3d.ZERO);
                    e.addVelocity(push.x, 0.55, push.z);
                    e.velocityModified = true;
                    event(E_RAM_HIT, e.getPos().add(0, 1, 0), 0);
                    soundAt(e.getPos(), SoundEvents.BLOCK_ANVIL_LAND, 1.5f, 0.7f);
                }
                com.bleid.vestments.cathedral.Cathedral.smashWalls(this, getPos().add(dashDir.multiply(2.2 * S)).add(0, 2.5 * S, 0), 2.4 * S);
                boolean wall = horizontalCollision && phaseTick > 1;
                if (wall || phaseTick >= 17) {
                    if (wall) {
                        event(E_RAM_WALL, getPos().add(dashDir.multiply(1.6 * S)).add(0, 2.5 * S, 0), 0);
                        sound(SoundEvents.BLOCK_BELL_USE, 5f, 0.45f);
                        sound(SoundEvents.BLOCK_ANVIL_LAND, 3f, 0.5f);
                        for (LivingEntity e : enemies(getBoundingBox().expand(4 * S, 2 * S, 4 * S))) {
                            if (hdist(e.getPos(), getPos()) > 4.5 * S) continue;
                            e.damage(getDamageSources().mobAttack(this), 12f);
                            knock(e, 0.8, 0.4);
                        }
                    }
                    phase = 2;
                    phaseTick = 0;
                }
            }
            default -> {                                      // торможение / отскок
                double back = horizontalCollision ? -0.25 : 0.0;
                double sp = (Math.max(0, 0.6 - phaseTick * 0.1) + back) * S;
                setVelocity(dashDir.x * sp, hoverVy(), dashDir.z * sp);
                if (phaseTick >= 7) {
                    dashes++;
                    if (widened() && dashes < 2) {
                        phase = 0;
                        phaseTick = 0;
                    } else done = true;
                }
            }
        }
    }

    /**
     * «Вихрь цепей»: колокол раскручивается, цепи и лохмотья хлещут по кругу; он дрейфует к цели,
     * затягивая к себе. В конце — резкий отброс всех вокруг.
     */
    private void spin(int k, LivingEntity t) {
        int len = widened() ? 64 : 52;
        float r = widened() ? SPIN_R + 1 : SPIN_R;
        Vec3d v = Vec3d.ZERO;
        if (t != null && k > SPIN_UP) {
            Vec3d d = new Vec3d(t.getX() - getX(), 0, t.getZ() - getZ());
            if (d.lengthSquared() > 1) v = d.normalize().multiply((widened() ? 0.27 : 0.21) * 1.6);
        }
        setVelocity(v.x, hoverVy(), v.z);
        if (k == 1) {
            event(E_SPIN, getPos(), 0);
            sound(SoundEvents.BLOCK_BELL_RESONATE, 3f, 0.9f);
            sound(SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, 3f, 0.5f);
        }
        if (k > SPIN_UP && k < SPIN_UP + len) {
            if (k % 4 == 0) sound(SoundEvents.BLOCK_CHAIN_PLACE, 2f, 0.6f + random.nextFloat() * 0.3f);
            if (k % 10 == 0) sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 2.5f, 0.5f);
            for (LivingEntity e : enemies(getBoundingBox().expand(r, 3, r))) {
                double hd = hdist(e.getPos(), getPos());
                if (hd > r || Math.abs(e.getY() - (getY() + 1.5 * S)) > 4 * S) continue;
                // затягивание по спирали
                Vec3d in = new Vec3d(getX() - e.getX(), 0, getZ() - e.getZ());
                if (in.lengthSquared() > 1e-3) {
                    Vec3d n = in.normalize(), tan = new Vec3d(-n.z, 0, n.x);
                    Vec3d pull = n.multiply(hd > 2 * S ? 0.08 : 0).add(tan.multiply(0.07));
                    e.addVelocity(pull.x, 0, pull.z);
                    e.velocityModified = true;
                }
                if (tooSoon(e, 10)) continue;
                e.damage(getDamageSources().mobAttack(this), 10f);
                event(E_CHAIN_HIT, e.getPos().add(0, 1, 0), 0);
            }
        }
        if (k == SPIN_UP + len) {
            event(E_SPIN, getPos(), 1);
            sound(SoundEvents.BLOCK_BELL_USE, 5f, 0.6f);
            sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.5f, 1.4f);
            for (LivingEntity e : enemies(getBoundingBox().expand(r + 1.5 * S, 3 * S, r + 1.5 * S))) {
                if (hdist(e.getPos(), getPos()) > r + 1.5 * S) continue;
                e.damage(getDamageSources().mobAttack(this), 14f);
                knock(e, 1.7, 0.55);
            }
        }
        if (k >= SPIN_UP + len + 6) done = true;
    }

    public static int volleyCount(boolean wide) {
        return wide ? 5 : 3;
    }

    public static int volleyGap(boolean wide) {
        return wide ? 5 : 7;
    }

    public static final int VOLLEY_FIRST = 8;

    /** «Тройной набат»: из трещины одна за другой вылетают звуковые сферы с упреждением; взрываются при попадании. */
    private void volley(int k, LivingEntity t) {
        boolean wide = widened();
        int n = volleyCount(wide), gap = volleyGap(wide);
        if (k == 2) {
            event(E_CONE_CHARGE, eyePos(), VOLLEY_FIRST - 2);
            sound(SoundEvents.BLOCK_BEACON_POWER_SELECT, 2f, 0.8f);
        }
        int i = k - VOLLEY_FIRST;
        if (i >= 0 && i % gap == 0 && i / gap < n && t != null) {
            int idx = i / gap;
            Vec3d o = eyePos().add(forward().multiply(0.8 * S));
            Vec3d aim = t.getPos().add(0, t.getHeight() * 0.5, 0);
            double tt = o.distanceTo(aim) / ORB_SPEED;
            aim = aim.add(t.getVelocity().multiply(tt * 0.8));
            Vec3d dir = aim.subtract(o).normalize();
            if (idx > 0) {                                    // веер: каждая следующая чуть в сторону
                double ang = Math.toRadians((idx % 2 == 1 ? 1 : -1) * 7 * ((idx + 1) / 2));
                double c = Math.cos(ang), s = Math.sin(ang);
                dir = new Vec3d(dir.x * c - dir.z * s, dir.y, dir.z * c + dir.x * s).normalize();
            }
            orbs.add(new Orb(o, dir.multiply(ORB_SPEED)));
            float yaw = (float) (MathHelper.atan2(dir.z, dir.x) * 180 / Math.PI);
            float pitch = (float) (Math.asin(MathHelper.clamp(dir.y, -1, 1)) * 180 / Math.PI);
            event(E_ORB_FIRE, o, packDir(yaw, pitch));
            sound(SoundEvents.BLOCK_BELL_USE, 3f, 0.9f + idx * 0.12f);
            sound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.6f);
        }
        if (k >= VOLLEY_FIRST + (n - 1) * gap + 8) done = true;
    }

    /** Направление сферы в одно число (точно передаётся во float). */
    public static float packDir(float yawDeg, float pitchDeg) {
        int y = Math.round((MathHelper.wrapDegrees(yawDeg) + 180f) * 10f), p = Math.round((pitchDeg + 90f) * 10f);
        return y * 2000 + p;
    }

    public static Vec3d unpackDir(float v) {
        int i = Math.round(v);
        double yaw = Math.toRadians(i / 2000 / 10.0 - 180), pitch = Math.toRadians(i % 2000 / 10.0 - 90);
        double c = Math.cos(pitch);
        return new Vec3d(Math.cos(yaw) * c, Math.sin(pitch), Math.sin(yaw) * c);
    }

    private void tickOrbs() {
        for (Iterator<Orb> it = orbs.iterator(); it.hasNext(); ) {
            Orb o = it.next();
            o.life++;
            Vec3d np = o.p.add(o.v);
            boolean burst = o.life > ORB_LIFE;
            net.minecraft.util.math.BlockPos bp = net.minecraft.util.math.BlockPos.ofFloored(np);
            if (!getWorld().getBlockState(bp).getCollisionShape(getWorld(), bp).isEmpty()) burst = true;
            if (!burst && !enemies(new Box(o.p, np).expand(0.7)).isEmpty()) burst = true;
            o.p = np;
            if (!burst) continue;
            it.remove();
            event(E_ORB_BURST, o.p, 0);
            soundAt(o.p, SoundEvents.BLOCK_BELL_USE, 2.5f, 1.3f);
            soundAt(o.p, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8f, 1.7f);
            for (LivingEntity e : enemies(new Box(o.p, o.p).expand(2.6 * S))) {
                if (e.getPos().add(0, e.getHeight() * 0.5, 0).distanceTo(o.p) > 2.8 * S) continue;
                e.damage(getDamageSources().mobAttack(this), 16f);
                Vec3d k = e.getPos().subtract(o.p);
                k = new Vec3d(k.x, 0, k.z);
                if (k.lengthSquared() > 1e-3) k = k.normalize().multiply(0.6);
                e.addVelocity(k.x, 0.3, k.z);
                e.velocityModified = true;
            }
        }
    }

    /**
     * «Погребальные скачки»: колокол серией прыгает на цель; каждое приземление — удар по площади
     * и быстрая звуковая волна по земле (перепрыгнуть).
     */
    private void hops(LivingEntity t) {
        boolean wide = widened();
        int n = wide ? 4 : 3, crouch = wide ? 3 : 5, air = wide ? 12 : 14;
        phaseTick++;
        if (phase == 0) {                                     // присел
            setVelocity(0, -0.08, 0);
            if (t != null) faceTarget(t, 20f);
            if (phaseTick >= crouch) {
                hopFrom = getPos();
                Vec3d to = t != null ? t.getPos().add(t.getVelocity().multiply(air * 0.6)) : getPos().add(forward().multiply(4 * S));
                Vec3d d = new Vec3d(to.x - getX(), 0, to.z - getZ());
                if (d.length() > 10 * S) d = d.normalize().multiply(10 * S);
                Vec3d tgt = getPos().add(d);
                hopTo = new Vec3d(tgt.x, ground(new Vec3d(tgt.x, Math.max(getY(), tgt.y) + 2, tgt.z)) + 0.6 * S, tgt.z);
                phase = 1;
                phaseTick = 0;
                sound(SoundEvents.ENTITY_RAVAGER_STEP, 2f, 0.5f);
                sound(SoundEvents.BLOCK_BELL_RESONATE, 1.5f, 1.1f);
            }
        } else if (phase == 1) {                              // полёт по дуге
            double s = Math.min(1.0, phaseTick / (double) air);
            Vec3d p = hopFrom.lerp(hopTo, s).add(0, 5.5 * S * 4 * s * (1 - s), 0);
            setVelocity(p.subtract(getPos()));
            if (phaseTick >= air) {
                setVelocity(Vec3d.ZERO);
                setPosition(hopTo.x, hopTo.y, hopTo.z);
                Vec3d c = new Vec3d(getX(), hopTo.y - 0.6 * S, getZ());
                event(E_HOP_LAND, c, 0);
                com.bleid.vestments.cathedral.Cathedral.smashWalls(this, c.add(0, 2 * S, 0), 3 * S);
                sound(SoundEvents.BLOCK_ANVIL_LAND, 3f, 0.4f);
                sound(SoundEvents.BLOCK_BELL_USE, 4f, 0.55f);
                for (LivingEntity e : enemies(new Box(c, c).expand(4.5 * S, 3 * S, 4.5 * S))) {
                    if (hdist(e.getPos(), c) > 4.2 * S || Math.abs(e.getY() - c.y) > 2.5 * S) continue;
                    e.damage(getDamageSources().mobAttack(this), 20f);
                    knock(e, 0.9, 0.6);
                    hit.add(e.getId());
                }
                Ring g = new Ring(c, 1.0f, 20f, 10f, 8.4f);
                g.hit.addAll(hit);
                hit.clear();
                rings.add(g);
                event(E_GROUND_RING, c, 1.0f);
                dashes++;
                phase = 2;
                phaseTick = 0;
            }
        } else {
            setVelocity(0, hoverVy() * 0.3, 0);
            if (phaseTick >= (dashes >= n ? 8 : 2)) {
                if (dashes >= n) done = true;
                else {
                    phase = 0;
                    phaseTick = 0;
                }
            }
        }
    }

    public static int knellCount(boolean wide) {
        return wide ? 7 : 5;
    }

    /**
     * «Призрачный звон»: колокол раскачивается и звонит; вокруг цели вспыхивают круги-метки,
     * на которые один за другим обрушиваются призрачные колокола.
     */
    private void knell(int k, LivingEntity t) {
        int n = knellCount(widened());
        if (k == 1) {
            sound(SoundEvents.BLOCK_BELL_RESONATE, 4f, 0.45f);
            sound(SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, 2f, 0.5f);
        }
        int mi = k - KNELL_MARK;
        if (mi >= 0 && mi % 2 == 0 && mi / 2 < n) {
            int i = mi / 2;
            Vec3d base = t != null ? t.getPos() : getPos();
            Vec3d spot;
            if (i == 0 && t != null) spot = base.add(t.getVelocity().multiply(10));
            else {
                double ang = random.nextDouble() * Math.PI * 2, r = (2.5 + random.nextDouble() * 3.5) * S;
                spot = base.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            }
            spot = new Vec3d(spot.x, ground(new Vec3d(spot.x, base.y + 2, spot.z)), spot.z);
            spots.add(spot);
            event(E_PHANTOM_MARK, spot, KNELL_FALL + i * 3 - k);
            soundAt(spot, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 2f, 0.5f);
        }
        int fi = k - KNELL_FALL;
        if (fi >= 0 && fi % 3 == 0 && fi / 3 < spots.size()) {
            Vec3d c = spots.get(fi / 3);
            event(E_PHANTOM_FALL, c, 0);
            soundAt(c, SoundEvents.BLOCK_BELL_USE, 3f, 0.4f + random.nextFloat() * 0.2f);
            soundAt(c, SoundEvents.BLOCK_ANVIL_LAND, 1.5f, 0.6f);
            for (LivingEntity e : enemies(new Box(c, c).expand(PHANTOM_R + 0.5, 3 * S, PHANTOM_R + 0.5))) {
                if (hdist(e.getPos(), c) > PHANTOM_R || Math.abs(e.getY() - c.y) > 2.5 * S) continue;
                e.damage(getDamageSources().mobAttack(this), 22f);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 2), this);
                knock(e, 0.3, 0.4);
            }
        }
        if (k >= KNELL_FALL + n * 3 + 8) done = true;
    }

    // ================================================================== урон и смерть

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) {
            if (state() == S_DYING || state() == S_STONE) {
                discard();
                return true;
            }
            return super.damage(source, amount);
        }
        if (state() == S_STONE) return hitStone(source);
        if (state() == S_DYING) return false;
        if (source.getAttacker() == this || state() != S_FIGHT) return false;
        Entity src = source.getAttacker();
        if (trapped != null && src == trapped) {
            trapHits++;
            event(E_TRAP_HIT, eyePos(), trapHits);
            sound(SoundEvents.BLOCK_BELL_USE, 3f, 0.9f + trapHits * 0.2f);
            return super.damage(source, amount);
        }
        // урон проходит только при попадании в разлом с глазом — в любой фазе, при любой ширине трещины
        if (!hitsEye(source)) {
            Vec3d p = source.getPosition() != null ? source.getPosition() : eyePos();
            if (src != null) p = src.getEyePos().add(eyePos().subtract(src.getEyePos()).multiply(0.75));
            event(E_CLANG, p, 0);
            sound(SoundEvents.BLOCK_ANVIL_PLACE, 1f, 1.7f);
            sound(SoundEvents.BLOCK_BELL_USE, 1f, 1.9f);
            return false;
        }
        event(E_TRAP_HIT, eyePos(), 1);
        sound(SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 2f, 0.6f);
        // открытый глаз (после атаки) — уязвимее
        return super.damage(source, eyeOpen() ? amount * 1.5f : amount);
    }

    /** Зона разлома с глазом: эллипсоид спереди на высоте глаза (в системе колокола). */
    private static final double EYE_FWD = 1.0 * S, EYE_H = 4.1 * S, RX = 0.95 * S, RY = 1.15 * S, RZ = 1.0 * S;

    private boolean hitsEye(DamageSource source) {
        Entity atk = source.getAttacker(), direct = source.getSource();
        Vec3d f = forward(), r = new Vec3d(-f.z, 0, f.x);
        Vec3d c = getPos().add(f.multiply(EYE_FWD)).add(0, EYE_H, 0);
        if (direct != null && direct != atk) {                          // снаряд
            Vec3d pos = direct.getPos(), v = direct.getVelocity();
            if (v.lengthSquared() > 1e-6 && v.dotProduct(f) > 0.05) return false;   // прилетел не спереди
            if (ellip(pos.subtract(c), r, f) < 1.35) return true;
            return v.lengthSquared() > 1e-6 && rayHits(pos.subtract(v.multiply(2)), v.normalize(), v.length() * 3 + 2, c, r, f);
        }
        if (atk instanceof net.minecraft.entity.LivingEntity le) {
            Vec3d o = le.getEyePos();
            if (o.subtract(c).dotProduct(f) < -0.2 * S) return false;          // бьёт сбоку или со спины
            return rayHits(o, le.getRotationVec(1f), 7 * S, c, r, f);
        }
        return false;
    }

    private static double ellip(Vec3d d, Vec3d r, Vec3d f) {
        double x = d.dotProduct(r) / RX, y = d.y / RY, z = d.dotProduct(f) / RZ;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static boolean rayHits(Vec3d o, Vec3d dir, double max, Vec3d c, Vec3d r, Vec3d f) {
        Vec3d d = o.subtract(c);
        double ox = d.dotProduct(r) / RX, oy = d.y / RY, oz = d.dotProduct(f) / RZ;
        double dx = dir.dotProduct(r) / RX, dy = dir.y / RY, dz = dir.dotProduct(f) / RZ;
        double A = dx * dx + dy * dy + dz * dz, B = 2 * (ox * dx + oy * dy + oz * dz), C = ox * ox + oy * oy + oz * oz - 1;
        if (C <= 0) return true;
        double disc = B * B - 4 * A * C;
        if (disc < 0) return false;
        double t = (-B - Math.sqrt(disc)) / (2 * A);
        return t >= 0 && t <= max;
    }

    public void startDying() {
        if (state() == S_DYING || state() == S_STONE) return;
        setHealth(1f);
        if (trapped != null) release();
        setAttack(IDLE);
        setEye(false);
        setState(S_DYING);
        rings.clear();
        orbs.clear();
        bossBar.setPercent(0f);
        for (UUID id : choir) {
            if (getWorld() instanceof ServerWorld sw && sw.getEntity(id) instanceof LivingEntity le) le.discard();
        }
        choir.clear();
        event(E_DARK, eyePos(), 0);
        sound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 3f, 0.4f);
        // победа: всем, кто бился рядом, открывается сан Патриарха
        if (getWorld() instanceof ServerWorld sw) {
            for (ServerPlayerEntity p : sw.getPlayers()) {
                if (p.squaredDistanceTo(this) < 128 * 128 && !p.isSpectator()) com.bleid.vestments.service.ServicePoints.markBellSlain(p);
            }
        }
    }

    /** Смерть: глаз гаснет, колокол опускается на землю и каменеет снизу вверх. Каменный колокол ждёт удара. */
    private void tickDying() {
        int k = stateTicks;
        setTarget(null);
        double gy = ground(getPos().add(0, 1, 0));
        setVelocity(0, MathHelper.clamp((gy - getY()) * 0.12, -0.25, 0.1), 0);
        if (k == 30) bossBar.setVisible(false);
        if (k == 1) sound(SoundEvents.BLOCK_DEEPSLATE_PLACE, 3f, 0.5f);
        if (k > 1 && k < STONE_T && k % 6 == 0) sound(SoundEvents.BLOCK_STONE_PLACE, 2.5f, 0.4f + k * 0.008f);
        if (k == STONE_T) {
            sound(SoundEvents.BLOCK_DEEPSLATE_BREAK, 3f, 0.4f);
            sound(SoundEvents.BLOCK_BELL_RESONATE, 2f, 0.3f);
            event(E_SPLIT, getPos(), 0);
            dataTracker.set(STATE, S_STONE);
        }
        if (k > STONE_T && age % 80 == 0) sound(SoundEvents.BLOCK_BELL_RESONATE, 0.8f, 0.3f);   // глухое эхо внутри камня
    }

    /** Окаменевший колокол: удар игрока или снаряд — мощный взрыв (3–4 крипера), выпадает Язык Колокола. */
    private boolean hitStone(DamageSource source) {
        if (state() != S_STONE || isRemoved()) return false;
        Entity atk = source.getAttacker();
        if (atk == this || source.isOf(DamageTypes.EXPLOSION) || source.isOf(DamageTypes.PLAYER_EXPLOSION)) return false;
        if (atk == null && source.getSource() == null) return false;
        if (!(getWorld() instanceof ServerWorld sw)) return false;
        Vec3d c = getPos().add(0, 2.2 * S, 0);
        event(E_STONE_HIT, c, 0);
        sound(SoundEvents.BLOCK_BELL_USE, 6f, 0.4f);
        sound(SoundEvents.BLOCK_DEEPSLATE_BREAK, 4f, 0.5f);
        discard();
        sw.createExplosion(this, c.x, c.y, c.z, STONE_BLAST, World.ExplosionSourceType.MOB);
        ItemEntity it = new ItemEntity(sw, getX(), getY() + 1.0, getZ(), new ItemStack(Bosses.BELL_TONGUE));
        it.setVelocity(0, 0.35, 0);
        it.setToDefaultPickupDelay();
        it.setGlowing(true);
        it.setInvulnerable(true);
        sw.spawnEntity(it);
        return true;
    }

    public boolean stone() {
        return state() == S_STONE;
    }

    @Override
    public boolean isAttackable() {
        return state() == S_FIGHT || state() == S_STONE;
    }

    @Override
    public boolean canHit() {
        return state() != S_AWAKEN && !isRemoved();
    }

    @Override
    public boolean canTarget(LivingEntity target) {
        return state() == S_FIGHT && super.canTarget(target);
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BLOCK_BELL_USE;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public float getSoundPitch() {
        return 0.6f + random.nextFloat() * 0.2f;
    }

    @Override
    protected float getSoundVolume() {
        return 2.5f;
    }

    @Override
    public boolean hasNoGravity() {
        return true;
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("BellState")) {
            int s = nbt.getInt("BellState");
            if (s == S_FIGHT) {
                dataTracker.set(STATE, S_FIGHT);
                bossBar.setVisible(true);
            } else if (s == S_STONE) {                       // каменный колокол так и стоит, ждёт удара
                dataTracker.set(STATE, S_STONE);
                stateTicks = STONE_T + 1;
                bossBar.setVisible(false);
            }
        }
        bossBar.setName(getDisplayName());
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("BellState", state() == S_DYING ? S_FIGHT : state());
    }
}
