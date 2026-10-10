package com.bleid.vestments.paladin;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.fx.Fx;
import com.bleid.vestments.service.RankView;
import com.bleid.vestments.service.ServicePoints;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Магия паладина (отдельные клавиши, открываются со званием):
 *  0 «Наложение рук» (рекрут) — лечит союзника, на которого смотришь, или себя;
 *  1 «Вызов» (старший рекрут) — враги вокруг переключаются на паладина;
 *  2 «Аура» (лорд) — переключает: защита → благословение → возмездие → выкл.;
 *  3 «Благословенный рывок» (верховный лорд) — рывок, сбивающий врагов, снимает замедление;
 *  4 «Ангельские крылья» (генерал) — 10 с парения, каждый удар зовёт столп света.
 * Пассивно: «Мученичество» (генерал) — забирает на себя смертельный удар союзника; реликварий
 * во второй руке усиливает лечение и ауры и понемногу лечит союзников рядом.
 */
public final class PaladinAbilities {
    public static final Identifier ABILITY = new Identifier(Vestments.MOD_ID, "pal_ability");
    public static final int HEAL = 0, TAUNT = 1, AURA = 2, DASH = 3, WINGS = 4;
    public static final String[] NAMES = { "lay_hands", "taunt", "aura", "dash", "wings" };
    private static final int[] RANK = { 1, 2, 3, 4, 5 };
    private static final int[] COOLDOWN = { 45 * 20, 20 * 20, 10, 10 * 20, 90 * 20 };
    public static final int AURA_NONE = 0, AURA_PROTECT = 1, AURA_BLESS = 2, AURA_RETRIB = 3;
    private static final int WINGS_TICKS = 200, MARTYR_CD = 180 * 20;

    private static final class St {
        final long[] ready = new long[5];
        int aura;
        long wingsUntil, martyrReady;
        int dashTicks;
        Vec3d dashDir = Vec3d.ZERO;
        final Set<Integer> dashHit = new HashSet<>();
    }

    private static final Map<UUID, St> ST = new HashMap<>();
    private static boolean reflecting;

    private PaladinAbilities() { }

    private static St st(PlayerEntity p) {
        return ST.computeIfAbsent(p.getUuid(), k -> new St());
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ABILITY, (server, player, handler, buf, sender) -> {
            int id = buf.readVarInt();
            server.execute(() -> use(player, id));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> ST.remove(h.getPlayer().getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(PaladinAbilities::tick);
        // «Мученичество»: паладин-генерал рядом забирает смертельный удар союзника
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity ally)) return true;
            ServerWorld w = ally.getServerWorld();
            for (ServerPlayerEntity pal : w.getPlayers()) {
                if (pal == ally || !pal.isAlive() || pal.squaredDistanceTo(ally) > 100) continue;
                if (!ServicePoints.isPaladin(pal) || !RankView.hasPaladin(pal, 5)) continue;
                St s = st(pal);
                if (w.getTime() < s.martyrReady || pal.getHealth() <= 2f) continue;
                s.martyrReady = w.getTime() + MARTYR_CD;
                ally.setHealth(Math.min(ally.getMaxHealth(), 6f));
                ally.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 2));
                Fx.play(w, "pal_martyr", ally.getPos().add(0, 1, 0), pal, 20, 0, 0, 0);
                w.playSound(null, ally.getX(), ally.getY(), ally.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.7f, 1.3f);
                ally.sendMessage(Text.translatable("message.vestments.martyr_saved", pal.getDisplayName()).formatted(Formatting.GOLD), true);
                pal.sendMessage(Text.translatable("message.vestments.martyr_took", ally.getDisplayName()).formatted(Formatting.GOLD), true);
                pal.timeUntilRegen = 0;
                pal.damage(w.getDamageSources().magic(), Math.min(amount, 40f));
                return false;
            }
            return true;
        });
        // «Аура возмездия»: часть урона, полученного союзником, возвращается нападавшему
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> {
            if (reflecting || !(target instanceof PlayerEntity tp) || !(source.getAttacker() instanceof LivingEntity att)
                    || att == target || !(tp.getWorld() instanceof ServerWorld w)) return true;
            for (ServerPlayerEntity pal : w.getPlayers()) {
                St s = ST.get(pal.getUuid());
                if (s == null || s.aura != AURA_RETRIB || pal.squaredDistanceTo(tp) > sq(auraRadius(pal))) continue;
                reflecting = true;
                try {
                    att.damage(w.getDamageSources().thorns(tp), amount * 0.3f);
                } finally {
                    reflecting = false;
                }
                Fx.play(w, "pal_hit", att.getPos().add(0, att.getHeight() * 0.5, 0), null, 0, 4, 0, 0.1f);
                break;
            }
            return true;
        });
    }

    private static double sq(double r) {
        return r * r;
    }

    private static double auraRadius(PlayerEntity p) {
        return ReliquaryItem.held(p) ? 10 : 8;
    }

    // ---------------------------------------------------------------- использование

    private static void use(ServerPlayerEntity p, int id) {
        if (id < 0 || id >= NAMES.length || !p.isAlive() || p.isSpectator()) return;
        if (!ServicePoints.isPaladin(p)) return;
        if (!RankView.hasPaladin(p, RANK[id])) {
            p.sendMessage(Text.translatable("message.vestments.rank_required",
                    Text.translatable(PaladinRanks.nameKey(RANK[id]))).formatted(Formatting.RED), true);
            return;
        }
        St s = st(p);
        long now = p.getServerWorld().getTime();
        if (now < s.ready[id]) {
            p.sendMessage(Text.translatable("message.vestments.ability_cooldown",
                    Text.translatable("ability.vestments." + NAMES[id]), (s.ready[id] - now + 19) / 20).formatted(Formatting.GRAY), true);
            return;
        }
        boolean ok = switch (id) {
            case HEAL -> layHands(p);
            case TAUNT -> taunt(p);
            case AURA -> aura(p, s);
            case DASH -> dash(p, s);
            case WINGS -> wings(p, s);
            default -> false;
        };
        if (ok) {
            int cd = COOLDOWN[id];
            if (id == HEAL && ReliquaryItem.held(p)) cd = 30 * 20;
            s.ready[id] = now + cd;
        }
    }

    private static boolean layHands(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        PlayerEntity target = p;
        double best = 0.965;
        for (PlayerEntity o : w.getPlayers()) {
            if (o == p || !o.isAlive() || o.squaredDistanceTo(p) > 36) continue;
            Vec3d to = o.getPos().add(0, o.getHeight() * 0.5, 0).subtract(eye).normalize();
            double dot = to.dotProduct(look);
            if (dot > best && p.canSee(o)) { best = dot; target = o; }
        }
        boolean relic = ReliquaryItem.held(p);
        target.heal(relic ? 12f : 8f);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, relic ? 100 : 60, 0));
        target.removeStatusEffect(StatusEffects.POISON);
        target.removeStatusEffect(StatusEffects.WITHER);
        Fx.play(w, "heal", target, 0, 0);
        if (target != p) Fx.play(w, "pal_martyr", target.getPos().add(0, 1, 0), p, 12, 1, 0, 0);
        w.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8f, 1.7f);
        w.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1f, 1.2f);
        return true;
    }

    private static boolean taunt(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Box box = p.getBoundingBox().expand(10, 4, 10);
        int n = 0;
        for (MobEntity m : w.getEntitiesByClass(MobEntity.class, box, x -> x.isAlive() && (x instanceof HostileEntity
                || x instanceof Angerable || x.getTarget() instanceof PlayerEntity))) {
            if (m.squaredDistanceTo(p) > 100) continue;
            m.setTarget(p);
            if (m instanceof Angerable a) {
                a.setAngryAt(p.getUuid());
                a.setAngerTime(400);
            }
            Fx.play(w, "pal_stun", m, 10, 1);
            n++;
        }
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0));
        Fx.play(w, "pal_taunt", p.getPos(), p, 0, 10f, 0, 0);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 0.6f, 1.3f);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1f, 0.8f);
        p.sendMessage(Text.translatable("message.vestments.taunt", n).formatted(Formatting.GOLD), true);
        return true;
    }

    private static boolean aura(ServerPlayerEntity p, St s) {
        s.aura = (s.aura + 1) % 4;
        p.sendMessage(Text.translatable("message.vestments.aura",
                Text.translatable("aura.vestments." + s.aura)).formatted(Formatting.GOLD), true);
        ServerWorld w = p.getServerWorld();
        if (s.aura != AURA_NONE) Fx.play(w, "pal_aura2", p.getPos(), p, 0, (float) auraRadius(p), s.aura, 0);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f,
                s.aura == AURA_NONE ? 0.8f : 1.2f + 0.15f * s.aura);
        return true;
    }

    private static boolean dash(ServerPlayerEntity p, St s) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d d = new Vec3d(look.x, 0, look.z);
        if (d.lengthSquared() < 1e-4) return false;
        s.dashDir = d.normalize();
        s.dashTicks = 6;
        s.dashHit.clear();
        p.removeStatusEffect(StatusEffects.SLOWNESS);
        p.removeStatusEffect(StatusEffects.MINING_FATIGUE);
        ServerWorld w = p.getServerWorld();
        Fx.play(w, "pal_dash", p.getPos(), p, 8, 0, 0, 0);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.PLAYERS, 1f, 1.3f);
        return true;
    }

    private static boolean wings(ServerPlayerEntity p, St s) {
        ServerWorld w = p.getServerWorld();
        s.wingsUntil = w.getTime() + WINGS_TICKS;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, WINGS_TICKS, 0, false, false, true));
        Fx.play(w, "pal_wings", p.getPos(), p, WINGS_TICKS, 0, 0, 0);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 0.5f, 1.6f);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1f, 1.5f);
        return true;
    }

    public static boolean wingsActive(PlayerEntity p) {
        St s = ST.get(p.getUuid());
        return s != null && p.getWorld().getTime() < s.wingsUntil;
    }

    /** Попадание паладина: под крыльями каждый удар зовёт столп света на цель. */
    public static void onHit(ServerPlayerEntity p, LivingEntity e, float dmg) {
        if (!wingsActive(p)) return;
        ServerWorld w = p.getServerWorld();
        e.timeUntilRegen = 0;
        e.damage(w.getDamageSources().magic(), 3f + dmg * 0.35f);
        if (e.getGroup() == net.minecraft.entity.EntityGroup.UNDEAD) e.setOnFireFor(3);
        Fx.play(w, "pal_pillar", e.getPos(), null, 0, 0, 0, 0);
        w.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.5f, 1.8f);
    }

    // ---------------------------------------------------------------- такт

    private static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            St s = ST.get(p.getUuid());
            ServerWorld w = p.getServerWorld();
            long now = w.getTime();
            boolean relic = ReliquaryItem.held(p);
            if (s == null && !relic) continue;
            final St fs = s == null ? st(p) : s;
            s = fs;
            if (!p.isAlive()) { s.dashTicks = 0; continue; }
            // рывок: несколько тактов разгона, враги на пути сбиты
            if (s.dashTicks > 0) {
                s.dashTicks--;
                Vec3d v = s.dashDir.multiply(1.25);
                p.setVelocity(v.x, Math.max(p.getVelocity().y, 0.05), v.z);
                p.velocityModified = true;
                p.fallDistance = 0;
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(1.0),
                        x -> com.bleid.vestments.paladin.combat.CombatServer.canHit(p, x) && !fs.dashHit.contains(x.getId()))) {
                    s.dashHit.add(e.getId());
                    e.timeUntilRegen = 0;
                    e.damage(w.getDamageSources().playerAttack(p), 4f);
                    e.takeKnockback(1.4f, -s.dashDir.x, -s.dashDir.z);
                    e.addVelocity(0, 0.3, 0);
                    e.velocityModified = true;
                    Fx.play(w, "pal_hit", e.getPos().add(0, e.getHeight() * 0.5, 0), null, 0, 4, 0, 0.3f);
                }
                Fx.play(w, "pal_dash_trail", p.getPos(), null, 0, 0, 0, 0);
            }
            // крылья: без урона от падения
            if (now < s.wingsUntil) p.fallDistance = 0;
            // ауры
            if (s.aura != AURA_NONE && now % 20 == 0) {
                double r = auraRadius(p);
                for (PlayerEntity o : w.getPlayers()) {
                    if (!o.isAlive() || o.squaredDistanceTo(p) > r * r) continue;
                    switch (s.aura) {
                        case AURA_PROTECT -> o.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 0, true, false, true));
                        case AURA_BLESS -> {
                            if (now % (relic ? 40 : 60) == 0) {
                                o.heal(relic ? 1.5f : 1f);
                                if (o != p) Fx.play(w, "heal_small", o, 0, 0);
                            }
                        }
                        default -> { }
                    }
                }
                if (now % 40 == 0) Fx.play(w, "pal_aura2", p.getPos(), p, 0, (float) r, s.aura, 0);
            }
            // реликварий: понемногу лечит союзников рядом
            if (relic && now % 80 == 0) {
                for (PlayerEntity o : w.getPlayers()) {
                    if (o.isAlive() && o.squaredDistanceTo(p) < 36) o.heal(0.5f);
                }
                Fx.play(w, "pal_relic", p.getPos(), p, 0, 0, 0, 0);
            }
        }
    }
}
