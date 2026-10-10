package com.bleid.vestments.paladin.combat;

import com.bleid.vestments.fx.Fx;
import com.bleid.vestments.paladin.PaladinSwordItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Особые приёмы нового оружия паладина: брошенный молот (оглушает и возвращается), копьё рассвета
 * (пронзает всех на линии и оставляет лечащий след), «Сокрушение небес» и «Карающая цепь».
 * Плюс свойство кистеня: пробивает защиту щитом и часть брони.
 */
public final class WeaponSkills {
    private static final class Proj {
        ServerPlayerEntity owner;
        ServerWorld world;
        Vec3d pos, dir;
        double speed, max, traveled;
        boolean returning, pierce;
        float dmg;
        int tier, age;
        String kind;
        final Set<Integer> hit = new HashSet<>();
    }

    private record Zone(ServerPlayerEntity owner, ServerWorld world, List<Vec3d> pts, long until) { }

    private static final List<Proj> PROJ = new ArrayList<>();
    private static final List<Zone> ZONES = new ArrayList<>();

    private WeaponSkills() { }

    static void event(ServerPlayerEntity p, Strike k, Set<Integer> hits, float base, int tier) {
        ServerWorld w = p.getServerWorld();
        Vec3d[] fr = Blade.frame(p.getYaw(), p.getPitch());
        switch (k.event) {
            case "hammer_throw" -> {
                Proj pr = proj(p, "hammer", 1.4, 18, base * 1.6f, tier, false);
                PROJ.add(pr);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1f, 0.7f);
            }
            case "dawn_spear" -> {
                Proj pr = proj(p, "spear", 2.2, 22, base * 1.4f, tier, true);
                PROJ.add(pr);
                ZONES.add(new Zone(p, w, new ArrayList<>(), w.getTime() + 100));
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_2, SoundCategory.PLAYERS, 1f, 1.4f);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8f, 1.8f);
            }
            case "heaven_crush" -> {
                Vec3d at = p.getPos().add(fr[0].multiply(1.4));
                CombatServer.aoe(p, at, 4.5, base * 1.4f, 0.8f, 0.6f, hits, tier);
                Fx.play(w, "pal_heaven", at, null, 0, 4.5f, tier, 0);
                w.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 0.7f);
                w.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.9f, 1.2f);
            }
            case "chain_pull" -> {
                Vec3d c = p.getPos();
                Box box = new Box(c, c).expand(6.5, 2.5, 6.5);
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, x -> CombatServer.canHit(p, x))) {
                    if (e.squaredDistanceTo(c) > 6.5 * 6.5) continue;
                    Vec3d to = c.subtract(e.getPos());
                    double l = to.length();
                    if (l > 1.2) {
                        Vec3d v = to.multiply(Math.min(1.0, 0.25 + l * 0.12) / l);
                        e.setVelocity(v.x, 0.25, v.z);
                        e.velocityModified = true;
                    }
                    if (!hits.contains(e.getId())) CombatServer.damage(p, e, base * 0.6f, 0f, tier, 0, null);
                }
                Fx.play(w, "pal_chain", c, null, 0, 6.5f, tier, 0);
                w.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 1.2f, 0.7f);
            }
            default -> { }
        }
    }

    private static Proj proj(ServerPlayerEntity p, String kind, double speed, double max, float dmg, int tier, boolean pierce) {
        Proj pr = new Proj();
        pr.owner = p;
        pr.world = p.getServerWorld();
        pr.dir = p.getRotationVec(1f).normalize();
        pr.pos = p.getEyePos().add(pr.dir.multiply(0.8)).add(0, -0.2, 0);
        pr.speed = speed;
        pr.max = max;
        pr.dmg = dmg;
        pr.tier = tier;
        pr.kind = kind;
        pr.pierce = pierce;
        return pr;
    }

    static void tick(MinecraftServer server) {
        for (Iterator<Proj> it = PROJ.iterator(); it.hasNext(); ) {
            Proj pr = it.next();
            if (!pr.owner.isAlive() || pr.owner.getServerWorld() != pr.world || ++pr.age > 200) { it.remove(); continue; }
            Vec3d from = pr.pos;
            if (pr.returning) {
                Vec3d to = pr.owner.getEyePos().add(0, -0.3, 0).subtract(pr.pos);
                double l = to.length();
                if (l < 1.6) {
                    pr.world.playSound(null, pr.owner.getX(), pr.owner.getY(), pr.owner.getZ(), SoundEvents.ITEM_TRIDENT_RETURN,
                            SoundCategory.PLAYERS, 1f, 1.1f);
                    it.remove();
                    continue;
                }
                pr.dir = to.multiply(1 / l);
                pr.pos = pr.pos.add(pr.dir.multiply(Math.min(l, pr.speed * 1.1)));
            } else {
                Vec3d next = pr.pos.add(pr.dir.multiply(pr.speed));
                HitResult hr = pr.world.raycast(new RaycastContext(pr.pos, next, RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE, pr.owner));
                boolean wall = hr.getType() == HitResult.Type.BLOCK;
                pr.pos = wall ? hr.getPos() : next;
                pr.traveled += pr.speed;
                if (!pr.pierce || !wall) hitAlong(pr, from, pr.pos);
                if (wall || pr.traveled >= pr.max) {
                    if (pr.kind.equals("hammer")) {
                        pr.returning = true;
                        if (wall) pr.world.playSound(null, pr.pos.x, pr.pos.y, pr.pos.z, SoundEvents.BLOCK_ANVIL_LAND,
                                SoundCategory.PLAYERS, 0.5f, 1.4f);
                    } else {
                        Fx.play(pr.world, "pal_burst", pr.pos, null, 0, 1.6f, pr.tier, 0);
                        it.remove();
                        trail(pr, from);
                        continue;
                    }
                }
            }
            if (pr.kind.equals("hammer")) {
                Fx.play(pr.world, "pal_hammer", pr.pos, null, 0, pr.returning ? 1 : 0, pr.age, 0);
            } else {
                trail(pr, from);
            }
        }
        for (Iterator<Zone> it = ZONES.iterator(); it.hasNext(); ) {
            Zone z = it.next();
            long now = z.world.getTime();
            if (now > z.until || !z.owner.isAlive()) { it.remove(); continue; }
            if (now % 20 != 0 || z.pts.isEmpty()) continue;
            for (PlayerEntity o : z.world.getPlayers()) {
                if (!o.isAlive()) continue;
                for (Vec3d v : z.pts) {
                    if (o.getPos().add(0, 1, 0).squaredDistanceTo(v) < 1.9 * 1.9) {
                        o.heal(2f);
                        Fx.play(z.world, "heal_small", o, 0, 0);
                        break;
                    }
                }
            }
        }
    }

    /** Копьё оставляет за собой лечащий след света. */
    private static void trail(Proj pr, Vec3d from) {
        Vec3d d = pr.pos.subtract(from);
        Fx.play(pr.world, "pal_dawn", from, null, 100, (float) d.x, (float) d.y, (float) d.z);
        for (int i = ZONES.size() - 1; i >= 0; i--) {
            Zone z = ZONES.get(i);
            if (z.owner == pr.owner && z.world == pr.world) {
                for (double t = 0; t <= 1.0; t += 0.5) z.pts.add(from.add(d.multiply(t)));
                break;
            }
        }
    }

    private static void hitAlong(Proj pr, Vec3d a, Vec3d b) {
        Box box = new Box(a, b).expand(0.7);
        for (LivingEntity e : pr.world.getEntitiesByClass(LivingEntity.class, box,
                x -> CombatServer.canHit(pr.owner, x) && !pr.hit.contains(x.getId()))) {
            pr.hit.add(e.getId());
            if (!CombatServer.damage(pr.owner, e, pr.dmg, 0.6f, pr.tier, 0, null)) continue;
            if (pr.kind.equals("hammer")) {
                // оглушение: почти не может двигаться и бьёт слабее
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 6));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 2));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 2));
                Fx.play(pr.world, "pal_stun", e, 40, 0);
                pr.world.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 0.7f, 1.6f);
                pr.returning = true;
                return;
            }
        }
    }

    /** Свойство кистеня: выбивает щит из защиты и часть урона проходит сквозь броню. */
    static void onHit(ServerPlayerEntity p, LivingEntity e, float dmg) {
        if (!(p.getMainHandStack().getItem() instanceof PaladinSwordItem w) || !w.swordId().equals("flail")) return;
        if (e instanceof PlayerEntity tp && tp.isBlocking()) {
            tp.getItemCooldownManager().set(tp.getActiveItem().getItem(), 100);
            tp.clearActiveItem();
            p.getServerWorld().playSound(null, tp.getX(), tp.getY(), tp.getZ(), SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1f, 1f);
        }
        e.timeUntilRegen = 0;
        e.damage(p.getServerWorld().getDamageSources().magic(), dmg * 0.15f);
    }
}
