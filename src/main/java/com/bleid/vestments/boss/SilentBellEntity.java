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
 * Неуязвим, пока глаз закрыт: глаз открывается на 2–3 с после каждой атаки.
 * Атаки: конус звуковой волны, падение-ловушка (тень под ним; пойманный выбирается тремя ударами по глазу),
 * хор призрачных певчих (лечат колокол, пока живы). На трети здоровья трещина расширяется:
 * гул, ползущие по арене звуковые кольца, атаки чаще, окна глаза короче.
 */
public class SilentBellEntity extends BossBase {
    public static final int IDLE = 0, RING = 1, DROP = 2, CHOIR = 3;
    public static final int S_AWAKEN = 0, S_FIGHT = 1, S_DYING = 2;
    /** Пробуждение: тишина, удар колокола, открытие глаза, подъём. */
    public static final int A_TOLL = 50, A_EYE = 60, A_RISEN = 100;
    public static final int E_AWAKE = 1, E_CLANG = 2, E_CONE_CHARGE = 3, E_CONE = 4, E_SHADOW = 5, E_DROP_LAND = 6, E_TRAP = 7,
            E_TRAP_HIT = 8, E_RELEASE = 9, E_CHOIR = 10, E_HEAL = 11, E_GROUND_RING = 12, E_EYE_OPEN = 13, E_WIDEN = 14,
            E_SPLIT = 15, E_DARK = 16, E_RING_HIT = 17;
    public static final float CONE_DEG = 35f, CONE_LEN = 13f, GROUND_RING_SPEED = 0.25f, GROUND_RING_MAX = 16f;
    /** Высота глаза над ногами сущности (блоки). */
    public static final double EYE_Y = 3.9;

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
    private boolean coneFired2;

    private static final class Ring {
        final Vec3d c;
        float r;
        final Set<Integer> hit = new HashSet<>();

        Ring(Vec3d c) {
            this.c = c;
        }
    }

    public SilentBellEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.GREEN);
        setNoGravity(true);
        experiencePoints = 0;
        bossBar.setVisible(false);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 700)
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
        return getPos().add(forward().multiply(0.5)).add(0, EYE_Y, 0);
    }

    // ================================================================== ИИ

    @Override
    protected void mobTick() {
        super.mobTick();
        stateTicks++;
        if (age % 10 == 0 && state() != S_FIGHT) forgetMe();
        switch (state()) {
            case S_AWAKEN -> awaken();
            case S_DYING -> tickDying();
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
            setPosition(getX(), groundY + 0.6 * s * s * (3 - 2 * s), getZ());
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
        int a = attack();
        if (eyeTicks > 0) {
            if (--eyeTicks == 0) {
                setEye(false);
                cooldown = wide ? 6 : 14;
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
            case RING -> widened() ? 34 : 42;
            case DROP -> dropPhase >= 4 ? 0 : Integer.MAX_VALUE;
            case CHOIR -> 36;
            default -> 0;
        };
    }

    /** После каждой атаки глаз открывается — окно для ударов. */
    private void endAttack(int a) {
        setAttack(IDLE);
        int open = widened() ? 30 : 50;
        if (a == DROP && trapHits >= 3) open = 80;           // выбрались — колокол оглушён
        eyeTicks = open;
        setEye(true);
        trapHits = 0;
    }

    private void startAttack(LivingEntity t) {
        double d = distanceTo(t);
        if (choir.isEmpty() && ready(CHOIR) && random.nextFloat() < 0.35f) {
            cds.put(CHOIR, widened() ? 420 : 560);
            begin(CHOIR);
            return;
        }
        if (ready(DROP) && (d < 8 || random.nextFloat() < 0.4f)) {
            cds.put(DROP, widened() ? 120 : 180);
            begin(DROP);
            return;
        }
        begin(RING);
    }

    private void begin(int a) {
        hit.clear();
        dropPhase = 0;
        trapTicks = 0;
        trapHits = 0;
        riseTicks = 0;
        coneFired2 = false;
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
            default -> { }
        }
    }

    private void cone(int k, LivingEntity t) {
        int fire = widened() ? 14 : 20;
        if (k < fire - 2 && t != null) faceTarget(t, 8f);
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
            double r = (k - fire) * 1.15 + 1.5;
            Vec3d o = getPos().add(0, EYE_Y, 0), f = forward();
            double cos = Math.cos(Math.toRadians(CONE_DEG));
            for (LivingEntity e : enemies(getBoundingBox().expand(CONE_LEN + 1, 8, CONE_LEN + 1))) {
                if (hit.contains(e.getId())) continue;
                Vec3d to = e.getPos().add(0, e.getHeight() * 0.5, 0).subtract(o);
                double hd = Math.sqrt(to.x * to.x + to.z * to.z);
                if (hd > r || hd > CONE_LEN || Math.abs(to.y) > 6) continue;
                if (hd > 1.2 && (to.x * f.x + to.z * f.z) / hd < cos) continue;
                hit.add(e.getId());
                e.damage(getDamageSources().mobAttack(this), 12f);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0), this);
                knock(e, 1.3, 0.35);
            }
        }
    }

    private void drop(int k, LivingEntity t) {
        switch (dropPhase) {
            case 0 -> {                                   // подъём над целью; тень следует за ним
                if (t != null) {
                    Vec3d dst = new Vec3d(t.getX(), t.getY() + 11, t.getZ());
                    Vec3d v = dst.subtract(getPos()).multiply(0.14);
                    if (v.length() > 0.9) v = v.normalize().multiply(0.9);
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
                setVelocity(0, -1.7, 0);
                if (getY() + getVelocity().y <= gy - 1.2 || verticalCollision || k > 80) {
                    setVelocity(Vec3d.ZERO);
                    setPosition(getX(), gy - 1.2, getZ());
                    land(gy);
                }
            }
            case 2 -> tickTrap();
            case 3 -> {                                   // подъём обратно
                riseTicks++;
                double gy = ground(getPos().add(0, 1.3, 0));
                double ty = gy + 0.6;
                setVelocity(0, MathHelper.clamp((ty - getY()) * 0.2, -0.3, 0.3), 0);
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
        LivingEntity caught = null;
        double best = 2.8;
        for (LivingEntity e : enemies(new Box(c, c).expand(6, 3, 6))) {
            double hd = hdist(e.getPos(), c);
            if (hd < best && Math.abs(e.getY() - gy) < 2.5) {
                best = hd;
                caught = e;
            }
        }
        for (LivingEntity e : enemies(new Box(c, c).expand(6, 3, 6))) {
            if (e == caught || hdist(e.getPos(), c) > 6) continue;
            e.damage(getDamageSources().mobAttack(this), 14f);
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
        Vec3d c = new Vec3d(getX(), getY() + 1.2, getZ());
        if (hdist(v.getPos(), c) > 0.45 || Math.abs(v.getY() - c.y) > 0.6) {
            if (v instanceof ServerPlayerEntity p) p.networkHandler.requestTeleport(c.x, c.y, c.z, p.getYaw(), p.getPitch());
            else v.refreshPositionAndAngles(c.x, c.y, c.z, v.getYaw(), v.getPitch());
        }
        v.setVelocity(Vec3d.ZERO);
        v.velocityModified = true;
        if (trapTicks % 20 == 0) {
            v.damage(getDamageSources().mobAttack(this), 2f);
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
                Vec3d p = getPos().add(Math.cos(ang) * 4.5, 2.5, Math.sin(ang) * 4.5);
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
            g.r += GROUND_RING_SPEED;
            if (g.r > GROUND_RING_MAX) {
                it.remove();
                continue;
            }
            for (LivingEntity e : enemies(new Box(g.c, g.c).expand(g.r + 1, 2, g.r + 1))) {
                if (!e.isOnGround() || Math.abs(e.getY() - g.c.y) > 1.5 || g.hit.contains(e.getId())) continue;
                if (Math.abs(hdist(e.getPos(), g.c) - g.r) > 0.45) continue;
                g.hit.add(e.getId());
                e.damage(getDamageSources().mobAttack(this), 6f);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1), this);
                event(E_RING_HIT, e.getPos(), 0);
            }
        }
    }

    private void steer(LivingEntity t, int a) {
        if (a == DROP) return;
        Vec3d want;
        double bob = Math.sin(age * 0.05) * 0.25;
        if (t != null) {
            if (a == IDLE) faceTarget(t, 4f);
            Vec3d away = new Vec3d(getX() - t.getX(), 0, getZ() - t.getZ());
            if (away.lengthSquared() < 1e-3) away = forward().multiply(-1);
            away = away.normalize();
            // медленно облетает цель на расстоянии ~6 блоков
            Vec3d side = new Vec3d(-away.z, 0, away.x).multiply(0.35);
            Vec3d p = t.getPos().add(away.add(side).normalize().multiply(6.0));
            want = new Vec3d(p.x, ground(new Vec3d(p.x, Math.max(getY(), t.getY()) + 1, p.z)) + 0.6 + bob, p.z);
        } else {
            want = new Vec3d(getX(), ground(getPos().add(0, 1, 0)) + 0.6 + bob, getZ());
        }
        Vec3d v = want.subtract(getPos()).multiply(0.06);
        double max = a == IDLE ? 0.16 : 0.05;
        if (v.length() > max) v = v.normalize().multiply(max);
        setVelocity(v);
    }

    // ================================================================== урон и смерть

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) {
            if (state() == S_DYING) {
                discard();
                return true;
            }
            return super.damage(source, amount);
        }
        if (source.getAttacker() == this || state() != S_FIGHT) return false;
        Entity src = source.getAttacker();
        if (trapped != null && src == trapped) {
            trapHits++;
            event(E_TRAP_HIT, eyePos(), trapHits);
            sound(SoundEvents.BLOCK_BELL_USE, 3f, 0.9f + trapHits * 0.2f);
            return super.damage(source, amount);
        }
        if (!eyeOpen()) {
            Vec3d p = source.getPosition() != null ? source.getPosition() : eyePos();
            if (src != null) p = src.getEyePos().add(eyePos().subtract(src.getEyePos()).multiply(0.75));
            event(E_CLANG, p, 0);
            sound(SoundEvents.BLOCK_ANVIL_PLACE, 1f, 1.7f);
            sound(SoundEvents.BLOCK_BELL_USE, 1f, 1.9f);
            return false;
        }
        return super.damage(source, amount);
    }

    public void startDying() {
        if (state() == S_DYING) return;
        setHealth(1f);
        if (trapped != null) release();
        setAttack(IDLE);
        setEye(false);
        setState(S_DYING);
        rings.clear();
        bossBar.setPercent(0f);
        for (UUID id : choir) {
            if (getWorld() instanceof ServerWorld sw && sw.getEntity(id) instanceof LivingEntity le) le.discard();
        }
        choir.clear();
        event(E_DARK, eyePos(), 0);
        sound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 3f, 0.4f);
    }

    private void tickDying() {
        int k = stateTicks;
        setTarget(null);
        double gy = ground(getPos().add(0, 1, 0));
        setVelocity(0, MathHelper.clamp((gy - getY()) * 0.12, -0.25, 0.1), 0);
        if (k == 30) bossBar.setVisible(false);
        if (k == 40) {
            sound(SoundEvents.BLOCK_BELL_USE, 6f, 0.5f);
            sound(SoundEvents.BLOCK_DEEPSLATE_BREAK, 4f, 0.5f);
            sound(SoundEvents.BLOCK_ANVIL_BREAK, 3f, 0.4f);
            event(E_SPLIT, getPos(), 0);
        }
        // долгое эхо раскола
        if (k == 46 || k == 58 || k == 74 || k == 94 || k == 118) {
            float fade = 1f - (k - 46) / 90f;
            sound(SoundEvents.BLOCK_BELL_RESONATE, 4f * fade + 0.4f, 0.45f);
        }
        if (k == 60 && getWorld() instanceof ServerWorld sw) {
            ItemEntity it = new ItemEntity(sw, getX(), gy + 1.0, getZ(), new ItemStack(Bosses.BELL_TONGUE));
            it.setVelocity(0, 0.25, 0);
            it.setToDefaultPickupDelay();
            it.setGlowing(true);
            sw.spawnEntity(it);
        }
        if (k >= 160) discard();
    }

    @Override
    public boolean isAttackable() {
        return state() == S_FIGHT;
    }

    @Override
    public boolean canHit() {
        return state() != S_DYING && super.canHit();
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
