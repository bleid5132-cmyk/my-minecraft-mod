package com.bleid.vestments.boss;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.EntityType;
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
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Звонарь Безмолвного Собора — первая стадия. Сгорбленный великан в пепельной рясе с бронзовой кувалдой
 * и треснувшим колоколом на цепях вместо головы.
 * Удар молотом с трещиной (через секунду по ней проходит волна), набат (рвёт колокол-голову рукой — звуковое кольцо; вплотную безопасно),
 * цепь-гарпун (подтягивает к удару; ломается, если ударить по ней в полёте), после половины здоровья —
 * погребальный размах колоколом по кругу и прыжки. Смерть — тишина и пробуждение Колокола.
 */
public class BellRingerEntity extends BossBase {
    public static final int IDLE = 0, SMASH = 1, TOLL = 2, CHAINS = 3, SWING = 4, LEAP = 5;
    public static final int[] DURATION = { 0, 50, 46, 46, 146, 60 };
    public static final int E_STEP = 1, E_SLAM = 2, E_CRACK = 3, E_WAVE = 4, E_TOLL = 5, E_CHAIN_SHOT = 6, E_CHAIN_BREAK = 7,
            E_CHAIN_HIT = 8, E_SWING_HIT = 9, E_LAND = 10, E_JUMP = 11, E_HAMMER_DROP = 12, E_SNAP = 13, E_BELL_FALL = 14,
            E_ASH = 15, E_RAGE = 16, E_RISE = 17, E_CATCH = 18, E_TOSS = 19;
    /** Погребальный размах: радиус орбиты колокола (блоки), угловая скорость (рад/такт), высота центра колокола. */
    public static final float SWING_R = 6f, SWING_R0 = 2.5f, SWING_W = 0.28f, SWING_Y = 2.1f;
    /** Размах: снимает колокол (14–28), раскручивает (36–110), сбавляет (110–118), подкидывает (118) и ловит головой (134). */
    public static final int SW_LIFT = 20, SW_SPIN = 36, SW_SLOW = 110, SW_TOSS = 118, SW_CATCH = 134;
    /** Тики смерти: колени, молот, цепи, колокол, пепел, пробуждение Колокола, остаток рясы. */
    public static final int D_HAMMER = 15, D_RISE = 48, D_COVER = 72, D_ASH = 74, D_END = 112;

    private static final TrackedData<Boolean> DYING = DataTracker.registerData(BellRingerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> CHAIN_STATE = DataTracker.registerData(BellRingerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Vector3f> CHAIN = DataTracker.registerData(BellRingerEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
    private static final TrackedData<Boolean> RAGE = DataTracker.registerData(BellRingerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public int clientDyingStart;

    private Vec3d crackO, crackDir;
    private int crackTick = -1;
    private final Set<Integer> hit = new HashSet<>(), crackHit = new HashSet<>();
    private final Map<Integer, Integer> swingHit = new HashMap<>();
    private Vec3d chainPos = Vec3d.ZERO, chainVel = Vec3d.ZERO;
    private double chainDist;
    private LivingEntity chainVictim;
    private int chainTicks;
    private Vec3d leapVel = Vec3d.ZERO;
    private boolean leaping;
    private int landTick = -1, stepTimer, dyingTicks = -1, lastTurn;
    private float swingYaw;

    public BellRingerEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.WHITE);
        setStepHeight(1.1f);
        experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 500)
                .add(EntityAttributes.GENERIC_ARMOR, 10)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 6)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 18)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(DYING, false);
        dataTracker.startTracking(CHAIN_STATE, 0);
        dataTracker.startTracking(CHAIN, new Vector3f());
        dataTracker.startTracking(RAGE, false);
    }

    @Override
    protected void initGoals() {
        targetSelector.add(1, new RevengeGoal(this));
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (DYING.equals(data) && dying()) clientDyingStart = age;
    }

    public boolean dying() {
        return dataTracker.get(DYING);
    }

    public boolean rage() {
        return dataTracker.get(RAGE);
    }

    /** 0 — цепи нет, 1 — летит, 2 — зацепила цель. */
    public int chainState() {
        return dataTracker.get(CHAIN_STATE);
    }

    public Vec3d chainPos() {
        Vector3f v = dataTracker.get(CHAIN);
        return new Vec3d(v.x, v.y, v.z);
    }

    private void setChain(int state, Vec3d p) {
        dataTracker.set(CHAIN_STATE, state);
        if (p != null) dataTracker.set(CHAIN, new Vector3f((float) p.x, (float) p.y, (float) p.z));
    }

    private boolean phase2() {
        return getHealth() < getMaxHealth() * 0.5f;
    }

    /** Положение колокола при погребальном размахе на такте k атаки (локально: x — лево, z — вперёд, блоки). */
    public static double[] swingOffset(float k) {
        float r = swingRadius(k), phi = swingAngle(k);
        return new double[] { r * MathHelper.sin(phi), SWING_Y, -r * MathHelper.cos(phi) };
    }

    public static float swingRadius(float k) {
        if (k < SW_SLOW) return SWING_R0 + (SWING_R - SWING_R0) * smooth((k - SW_SPIN) / 10f);
        return SWING_R0 + (SWING_R - SWING_R0) * (1 - smooth((k - SW_SLOW) / 8f));
    }

    /** Начинает спереди (π) и крутится всё время, пока колокол на цепи. */
    public static float swingAngle(float k) {
        return MathHelper.PI + Math.max(0, k - SW_SPIN) * SWING_W;
    }

    private static float smooth(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    // ================================================================== ИИ

    @Override
    protected void mobTick() {
        super.mobTick();
        if (dyingTicks >= 0) {
            tickDying();
            return;
        }
        bossBar.setPercent(getHealth() / getMaxHealth());
        LivingEntity t = pickTarget();
        cds.replaceAll((k, v) -> Math.max(0, v - 1));
        if (!rage() && phase2()) {
            dataTracker.set(RAGE, true);
            sound(SoundEvents.BLOCK_BELL_USE, 4f, 0.45f);
            sound(SoundEvents.ENTITY_RAVAGER_ROAR, 3f, 0.5f);
            event(E_RAGE, getPos(), 0);
            cds.put(SWING, 30);
        }
        int a = attack();
        if (a != IDLE) {
            attackTick++;
            runAttack(a, t);
            if (attack() == a && attackTick >= duration(a)) endAttack();
        } else if (t != null && --cooldown <= 0) {
            startAttack(t);
        }
        tickCrack();
        steer(t, attack());
    }

    private int duration(int a) {
        if (a == CHAINS && chainState() != 0) return Integer.MAX_VALUE;
        if (a == LEAP) return leaping ? Integer.MAX_VALUE : landTick >= 0 ? landTick + 14 : DURATION[LEAP];
        return DURATION[a];
    }

    private void endAttack() {
        if (attack() == CHAINS) setChain(0, null);
        chainVictim = null;
        leaping = false;
        setAttack(IDLE);
        cooldown = queued >= 0 ? 2 : phase2() ? 12 : 22;
    }

    private void startAttack(LivingEntity t) {
        double d = distanceTo(t);
        if (queued >= 0) {
            int q = queued;
            queued = -1;
            begin(q);
            return;
        }
        boolean p2 = phase2();
        if (p2 && ready(SWING) && d < 11 && random.nextFloat() < 0.45f) {
            cds.put(SWING, 280);
            begin(SWING);
            return;
        }
        if (d > 6.5 && d < 16 && ready(CHAINS) && random.nextFloat() < 0.6f) {
            cds.put(CHAINS, p2 ? 100 : 140);
            begin(CHAINS);
            return;
        }
        if (p2 && d > 5.5 && d < 18 && ready(LEAP) && random.nextFloat() < 0.6f) {
            cds.put(LEAP, 90);
            begin(LEAP);
            return;
        }
        if (d < 9 && ready(TOLL) && random.nextFloat() < 0.35f) {
            cds.put(TOLL, p2 ? 120 : 170);
            begin(TOLL);
            return;
        }
        if (d < 6.0) {
            begin(SMASH);
            return;
        }
        cooldown = 4;
    }

    private void begin(int a) {
        hit.clear();
        swingHit.clear();
        landTick = -1;
        leaping = false;
        chainTicks = 0;
        if (a == SWING) swingYaw = getYaw();
        setAttack(a);
        switch (a) {
            case SMASH -> sound(SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, 2f, 0.5f);
            case TOLL -> sound(SoundEvents.BLOCK_CHAIN_STEP, 2f, 0.5f);
            case SWING -> {
                sound(SoundEvents.BLOCK_CHAIN_BREAK, 2f, 0.6f);
                sound(SoundEvents.BLOCK_BELL_RESONATE, 2f, 0.5f);
            }
            default -> { }
        }
    }

    private void runAttack(int a, LivingEntity t) {
        int k = attackTick;
        switch (a) {
            case SMASH -> smash(k, t);
            case TOLL -> toll(k, t);
            case CHAINS -> chains(k, t);
            case SWING -> swing(k, t);
            case LEAP -> leap(k, t);
            default -> { }
        }
    }

    private void smash(int k, LivingEntity t) {
        if (k <= 13 && t != null) faceTarget(t, 10f);
        if (k == 10) sound(SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, 1.5f, 0.5f);
        if (k == 18) {
            Vec3d p = local(0.35, 0, 4.65);
            p = new Vec3d(p.x, ground(p), p.z);
            for (LivingEntity e : enemies(new Box(p, p).expand(2.4, 2.6, 2.4))) {
                if (hdist(e.getPos(), p) > 2.4) continue;
                e.damage(getDamageSources().mobAttack(this), 20f);
                knock(e, 0.9, 0.45);
            }
            sound(SoundEvents.BLOCK_ANVIL_LAND, 2.5f, 0.45f);
            sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.6f, 0.55f);
            event(E_SLAM, p, 0);
            crackO = p;
            crackDir = forward();
            crackTick = 0;
            crackHit.clear();
            event(E_CRACK, p, getYaw());
        }
    }

    /** Через секунду после удара по трещине проходит волна. */
    private void tickCrack() {
        if (crackTick < 0) return;
        crackTick++;
        if (crackTick == 20) {
            event(E_WAVE, crackO, (float) (MathHelper.atan2(crackDir.z, crackDir.x) * 180 / Math.PI) - 90f);
            soundAt(crackO, SoundEvents.BLOCK_BASALT_BREAK, 3f, 0.5f);
            soundAt(crackO, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, 2f, 0.6f);
        }
        if (crackTick >= 20 && crackTick < 28) {
            int i = crackTick - 20;
            for (double s = i * 1.15; s < (i + 1) * 1.15; s += 0.5) {
                Vec3d p = crackO.add(crackDir.multiply(s + 0.6));
                p = new Vec3d(p.x, ground(p), p.z);
                for (LivingEntity e : enemies(new Box(p, p).expand(1.5, 2.5, 1.5))) {
                    if (hdist(e.getPos(), p) > 1.5 || !crackHit.add(e.getId())) continue;
                    e.damage(getDamageSources().mobAttack(this), 12f);
                    e.addVelocity(0, 0.75, 0);
                    e.velocityModified = true;
                }
            }
        }
        if (crackTick >= 28) crackTick = -1;
    }

    private void toll(int k, LivingEntity t) {
        if (k <= 10 && t != null) faceTarget(t, 8f);
        if (k == 14) {
            sound(SoundEvents.BLOCK_BELL_USE, 5f, 0.5f);
            sound(SoundEvents.BLOCK_BELL_RESONATE, 4f, 0.6f);
            event(E_TOLL, local(0, 6.1, 0.94), 0);
        }
        if (k >= 14 && k <= 29) {
            double r = (k - 14) * 0.75 + 0.6;
            for (LivingEntity e : enemies(getBoundingBox().expand(r + 1, 3, r + 1))) {
                double hd = hdist(e.getPos(), getPos());
                if (hd < r - 0.9 || hd > r + 0.3 || hit.contains(e.getId())) continue;
                if (hd < 2.6) continue;                                // прямо под колоколом-головой звук не задевает
                hit.add(e.getId());
                e.damage(getDamageSources().mobAttack(this), 8f);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0), this);
                e.addStatusEffect(new StatusEffectInstance(BossEffects.STUN, 20, 0), this);
                knock(e, 0.6, 0.2);
            }
        }
    }

    private void chains(int k, LivingEntity t) {
        int st = chainState();
        if (k <= 10 && t != null) faceTarget(t, 12f);
        if (k == 10 && t != null) {
            chainPos = local(0, 6.6, 2.9);                      // с кончика колокола-головы
            Vec3d aim = t.getPos().add(0, t.getHeight() * 0.55, 0);
            chainVel = aim.subtract(chainPos).normalize().multiply(1.3);
            chainDist = 0;
            setChain(1, chainPos);
            sound(SoundEvents.BLOCK_CHAIN_PLACE, 2.5f, 0.5f);
            sound(SoundEvents.ENTITY_FISHING_BOBBER_THROW, 2f, 0.4f);
            event(E_CHAIN_SHOT, chainPos, 0);
            return;
        }
        if (st == 1) {
            for (int sub = 0; sub < 2 && chainState() == 1; sub++) {
                chainPos = chainPos.add(chainVel.multiply(0.5));
                chainDist += 0.65;
                for (LivingEntity e : enemies(new Box(chainPos, chainPos).expand(0.7))) {
                    chainVictim = e;
                    chainTicks = 0;
                    e.damage(getDamageSources().mobAttack(this), 6f);
                    setChain(2, chainPos);
                    sound(SoundEvents.BLOCK_CHAIN_HIT, 3f, 0.6f);
                    event(E_CHAIN_HIT, chainPos, 0);
                    return;
                }
                BlockPos bp = BlockPos.ofFloored(chainPos);
                if (!getWorld().getBlockState(bp).getCollisionShape(getWorld(), bp).isEmpty() || chainDist > 16) {
                    soundAt(chainPos, SoundEvents.BLOCK_CHAIN_HIT, 2f, 0.8f);
                    setChain(0, chainPos);
                    return;
                }
            }
            // цепь в полёте ломается от удара игрока, смотрящего на неё
            for (PlayerEntity p : getWorld().getPlayers()) {
                if (!p.isAlive() || p.isSpectator() || p.squaredDistanceTo(chainPos) > 4.2 * 4.2 || !swinging(p)) continue;
                Vec3d to = chainPos.subtract(p.getEyePos()).normalize();
                if (to.dotProduct(p.getRotationVec(1f)) < 0.75) continue;
                soundAt(chainPos, SoundEvents.BLOCK_CHAIN_BREAK, 3f, 0.7f);
                soundAt(chainPos, SoundEvents.BLOCK_ANVIL_LAND, 1f, 1.6f);
                event(E_CHAIN_BREAK, chainPos, 0);
                setChain(0, chainPos);
                endAttack();
                cooldown = 45;                      // звонарь пошатнулся
                return;
            }
            setChain(1, chainPos);
        } else if (st == 2) {
            chainTicks++;
            LivingEntity v = chainVictim;
            if (v == null || !v.isAlive()) {
                setChain(0, null);
                return;
            }
            Vec3d dst = getPos().add(forward().multiply(4.4));
            Vec3d to = dst.subtract(v.getPos());
            double d = Math.sqrt(to.x * to.x + to.z * to.z);
            if (d < 1.3 || chainTicks > 26) {
                setChain(0, null);
                v.setVelocity(v.getVelocity().multiply(0.2, 1, 0.2));
                v.velocityModified = true;
                queued = SMASH;
                endAttack();
                cooldown = 1;
                return;
            }
            Vec3d pull = new Vec3d(to.x, 0, to.z).normalize().multiply(Math.min(1.15, 0.35 + d * 0.12));
            v.setVelocity(pull.x, Math.max(v.getVelocity().y, 0.08), pull.z);
            v.velocityModified = true;
            v.fallDistance = 0;
            if (chainTicks % 4 == 0) soundAt(v.getPos(), SoundEvents.BLOCK_CHAIN_STEP, 2f, 0.6f);
            setChain(2, v.getPos().add(0, v.getHeight() * 0.55, 0));
        }
    }

    private void swing(int k, LivingEntity t) {
        if (k <= 6 && t != null) {
            faceTarget(t, 20f);
            swingYaw = getYaw();
        }
        setYaw(swingYaw);
        bodyYaw = swingYaw;
        headYaw = swingYaw;
        if (k == 6) sound(SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, 2f, 0.6f);            // молот за спину
        if (k == SW_LIFT) sound(SoundEvents.BLOCK_CHAIN_BREAK, 2f, 0.7f);           // снимает колокол-голову
        if (k == SW_TOSS) {
            sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 3f, 0.6f);
            event(E_TOSS, local(0, 5, 2.5), 0);
        }
        if (k == SW_CATCH) {
            sound(SoundEvents.BLOCK_BELL_USE, 4f, 0.55f);
            sound(SoundEvents.BLOCK_ANVIL_LAND, 1.5f, 0.6f);
            event(E_CATCH, local(0, 6.1, 0.94), 0);
        }
        if (k < SW_SPIN + 2 || k > SW_TOSS || swingRadius(k) < 3.5f) return;
        double[] o = swingOffset(k);
        Vec3d bell = local(-o[0], o[1], o[2]);
        int turn = (int) (swingAngle(k) / (Math.PI * 2));
        if (turn != lastTurn || k == SW_SPIN + 2) {
            lastTurn = turn;
            soundAt(bell, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 3f, 0.4f);
            soundAt(bell, SoundEvents.BLOCK_BELL_RESONATE, 1.5f, 0.6f);
        }
        if (k % 6 == 0) sound(SoundEvents.BLOCK_CHAIN_STEP, 1.5f, 0.5f);
        for (LivingEntity e : enemies(new Box(bell, bell).expand(2.1, 2.6, 2.1))) {
            if (hdist(e.getPos(), bell) > 2.1) continue;
            Integer last = swingHit.get(e.getId());
            if (last != null && age - last < 12) continue;
            swingHit.put(e.getId(), age);
            e.damage(getDamageSources().mobAttack(this), 14f);
            knock(e, 1.4, 0.45);
            soundAt(bell, SoundEvents.BLOCK_BELL_USE, 3f, 0.7f);
            event(E_SWING_HIT, e.getPos().add(0, 1, 0), 0);
        }
    }

    private void leap(int k, LivingEntity t) {
        if (k <= 10 && t != null) faceTarget(t, 14f);
        if (k == 10) {
            Vec3d dst = t != null ? t.getPos() : getPos().add(forward().multiply(6));
            Vec3d to = dst.subtract(getPos());
            double d = Math.sqrt(to.x * to.x + to.z * to.z);
            Vec3d land = d > 1.6 ? dst.subtract(to.x / d * 1.4, 0, to.z / d * 1.4) : getPos();
            leapVel = new Vec3d((land.x - getX()) / 22.0, 0, (land.z - getZ()) / 22.0);
            setVelocity(leapVel.x, 0.95, leapVel.z);
            velocityDirty = true;
            leaping = true;
            sound(SoundEvents.ENTITY_RAVAGER_ROAR, 2f, 0.6f);
            sound(SoundEvents.BLOCK_CHAIN_BREAK, 1.5f, 0.5f);
            event(E_JUMP, getPos(), 0);
            return;
        }
        if (leaping) {
            setVelocity(leapVel.x, getVelocity().y, leapVel.z);
            if (k > 16 && isOnGround() || k > 44) {
                leaping = false;
                landTick = k;
                setVelocity(0, getVelocity().y, 0);
                Vec3d p = getPos();
                for (LivingEntity e : enemies(getBoundingBox().expand(5, 2, 5))) {
                    if (hdist(e.getPos(), p) > 5) continue;
                    e.damage(getDamageSources().mobAttack(this), 14f);
                    knock(e, 1.2, 0.6);
                }
                sound(SoundEvents.BLOCK_ANVIL_LAND, 3f, 0.4f);
                sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
                event(E_LAND, p, 0);
            }
        }
    }

    private void steer(LivingEntity t, int a) {
        Vec3d v = getVelocity();
        if (a == LEAP && leaping) return;
        if (a != IDLE) {
            setVelocity(v.x * 0.4, v.y, v.z * 0.4);
            return;
        }
        double vx = 0, vz = 0;
        if (t != null) {
            faceTarget(t, 6f);
            double d = distanceTo(t);
            if (d > 3.3) {
                Vec3d f = forward();
                double sp = phase2() ? 0.14 : 0.12;
                vx = f.x * sp;
                vz = f.z * sp;
            }
        }
        setVelocity(vx, v.y, vz);
        if ((vx != 0 || vz != 0) && isOnGround() && ++stepTimer >= 16) {
            stepTimer = 0;
            sound(SoundEvents.ENTITY_WARDEN_STEP, 2.5f, 0.5f);
            sound(SoundEvents.BLOCK_CHAIN_STEP, 1f, 0.5f);
            event(E_STEP, getPos(), 0);
        }
    }

    // ================================================================== смерть и переход

    public void startDying() {
        if (dyingTicks >= 0) return;
        dyingTicks = 0;
        setHealth(1f);
        setAttack(IDLE);
        setChain(0, null);
        crackTick = -1;
        dataTracker.set(DYING, true);
        bossBar.setVisible(false);
        setTarget(null);
        forgetMe();
    }

    private void tickDying() {
        dyingTicks++;
        int k = dyingTicks;
        setTarget(null);
        setVelocity(0, getVelocity().y, 0);
        if (k % 10 == 0) forgetMe();
        if (k == D_HAMMER) {
            Vec3d p = local(1.4, 0, 2.75);
            soundAt(p, SoundEvents.BLOCK_ANVIL_LAND, 1.4f, 0.35f);
            event(E_HAMMER_DROP, p, 0);
        }
        if (k >= 20 && k <= 44 && (k - 20) % 8 == 0) {
            int i = (k - 20) / 8;
            Vec3d p = local(i % 2 == 0 ? -0.9 : 0.9, 4.2 - i * 0.15, 0.6);
            soundAt(p, SoundEvents.BLOCK_CHAIN_BREAK, 2.5f, 0.45f + i * 0.08f);
            event(E_SNAP, p, i);
        }
        // колокол отделяется, растёт над телом и накрывает его
        if (k == D_RISE) {
            sound(SoundEvents.BLOCK_BELL_RESONATE, 3f, 0.4f);
            event(E_RISE, getPos(), 0);
        }
        if (k == D_COVER) {
            sound(SoundEvents.BLOCK_ANVIL_LAND, 3f, 0.3f);
            sound(SoundEvents.BLOCK_BELL_USE, 2f, 0.35f);
            event(E_BELL_FALL, getPos(), 0);
            if (getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                SilentBellEntity bell = Bosses.SILENT_BELL.create(sw);
                if (bell != null) {
                    bell.refreshPositionAndAngles(getX(), ground(getPos()), getZ(), getYaw(), 0);
                    bell.setYaw(getYaw());
                    bell.bodyYaw = getYaw();
                    bell.headYaw = getYaw();
                    sw.spawnEntity(bell);
                }
            }
        }
        // тело рассыпается пеплом уже под колоколом
        if (k == D_ASH) event(E_ASH, getPos(), 0);
        if (k >= D_ASH && k < D_ASH + 30 && k % 6 == 0) sound(SoundEvents.BLOCK_SAND_BREAK, 0.7f, 0.5f);
        if (k >= D_END) discard();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) {
            if (dyingTicks >= 0) {
                discard();
                return true;
            }
            return super.damage(source, amount);
        }
        if (dyingTicks >= 0 || source.getAttacker() == this) return false;
        return super.damage(source, amount);
    }

    @Override
    public boolean isAttackable() {
        return !dying();
    }

    @Override
    public boolean canHit() {
        return !dying() && super.canHit();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return !dying() && super.canBeHitByProjectile();
    }

    @Override
    public boolean canTarget(LivingEntity target) {
        return !dying() && super.canTarget(target);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return dying() ? null : SoundEvents.ENTITY_WARDEN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return dying() ? null : SoundEvents.ENTITY_IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public float getSoundPitch() {
        return 0.5f;
    }

    @Override
    protected float getSoundVolume() {
        return 2f;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 160;
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("RingerDying") && nbt.getInt("RingerDying") >= 0) {
            dyingTicks = nbt.getInt("RingerDying");
            dataTracker.set(DYING, true);
            bossBar.setVisible(false);
        }
        bossBar.setName(getDisplayName());
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("RingerDying", dyingTicks);
    }
}
