package com.bleid.vestments.paladin.combat;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.fx.Fx;
import com.bleid.vestments.paladin.PaladinRanks;
import com.bleid.vestments.paladin.PaladinSwordItem;
import com.bleid.vestments.service.RankView;
import com.bleid.vestments.service.ServicePoints;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Сервер боёвки паладина: принимает запросы ударов, ведёт время приёма, в окне удара «заметает»
 * дугу клинка коробками (несколько промежуточных положений за тик), наносит урон, отбрасывает,
 * рассылает эффекты и «заморозку кадра» атакующему.
 */
public final class CombatServer {
    /** C2S: varint вид приёма (0.. серия, 100 разбег, 101 прыжок, 200 особый). */
    public static final Identifier ATTACK = new Identifier(Vestments.MOD_ID, "pal_attack");
    /** S2C: varint id сущности, string id приёма. */
    public static final Identifier PLAY = new Identifier(Vestments.MOD_ID, "pal_play");
    /** S2C атакующему: varint тиков заморозки, float тряска. */
    public static final Identifier HIT = new Identifier(Vestments.MOD_ID, "pal_hit");

    private static final class State {
        Clip clip;
        Moveset ms;
        float t, tPrev;
        List<Set<Integer>> hits = new ArrayList<>();
        boolean[] preFired = new boolean[0], contactFired = new boolean[0];
        long buffUntil;
    }

    private record Wave(ServerPlayerEntity owner, ServerWorld world, Vec3d dir, Set<Integer> hit, float dmg, int tier,
                        Vec3d[] pos, int[] age) { }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final List<Wave> WAVES = new ArrayList<>();

    private CombatServer() { }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ATTACK, (server, player, handler, buf, sender) -> {
            int kind = buf.readVarInt();
            server.execute(() -> request(player, kind));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> STATES.remove(h.getPlayer().getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(CombatServer::tick);
        ServerTickEvents.END_SERVER_TICK.register(WeaponSkills::tick);
    }

    public static boolean consecrated(PlayerEntity p) {
        State s = STATES.get(p.getUuid());
        return s != null && p.getServer() != null && s.buffUntil > p.getServer().getTicks();
    }

    // ---------------------------------------------------------------- запрос удара

    private static void request(ServerPlayerEntity p, int kind) {
        if (!ServicePoints.isPaladin(p) || !p.isAlive() || p.isSpectator() || p.isUsingItem()) return;
        ItemStack st = p.getMainHandStack();
        if (!(st.getItem() instanceof PaladinSwordItem sword)) return;
        Moveset ms = sword.moveset();
        if (ms == null) return;
        Clip c = ms.byKind(kind);
        if (c == null) return;
        State s = STATES.computeIfAbsent(p.getUuid(), k -> new State());
        if (s.clip != null && s.t < s.clip.cancelTime() - 0.12f) return;
        if (kind == 200) {
            if (!RankView.hasPaladin(p, sword.rank)) {
                p.sendMessage(Text.translatable("message.vestments.rank_required",
                        Text.translatable(PaladinRanks.nameKey(sword.rank))).formatted(Formatting.RED), true);
                return;
            }
            if (p.getItemCooldownManager().isCoolingDown(sword)) return;
            p.getItemCooldownManager().set(sword, sword.skillCooldown);
        }
        s.clip = c;
        s.ms = ms;
        s.t = 0;
        s.tPrev = 0;
        int n = c.strikes.size();
        s.hits = new ArrayList<>();
        for (int i = 0; i < n; i++) s.hits.add(new HashSet<>());
        s.preFired = new boolean[n];
        s.contactFired = new boolean[n];
        for (ServerPlayerEntity viewer : PlayerLookup.tracking(p)) sendPlay(viewer, p, c);
        if (kind == 200) sendPlay(p, p, c);
    }

    /** Показать приём игрока всем, кто его видит (кроме него самого). */
    public static void broadcast(ServerPlayerEntity who, Clip c) {
        for (ServerPlayerEntity viewer : PlayerLookup.tracking(who)) sendPlay(viewer, who, c);
    }

    /** Прервать текущий удар (уворот отменяет приём). */
    public static void cancel(ServerPlayerEntity p) {
        State s = STATES.get(p.getUuid());
        if (s != null) s.clip = null;
    }

    private static void sendPlay(ServerPlayerEntity to, ServerPlayerEntity who, Clip c) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(who.getId());
        buf.writeString(c.id);
        ServerPlayNetworking.send(to, PLAY, buf);
    }

    // ---------------------------------------------------------------- такт

    private static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, State> e = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            State s = e.getValue();
            if (p == null) { it.remove(); continue; }
            if (s.clip == null) continue;
            if (!p.isAlive() || !(p.getMainHandStack().getItem() instanceof PaladinSwordItem)) { s.clip = null; continue; }
            s.tPrev = s.t;
            s.t += 0.05f;
            List<Strike> strikes = s.clip.strikes;
            for (int i = 0; i < strikes.size(); i++) {
                Strike k = strikes.get(i);
                if (!s.preFired[i] && s.t >= k.pre) {
                    s.preFired[i] = true;
                    swingSound(p, s.ms, k);
                }
                if (s.tPrev < k.contact && s.t > k.pre && k.maxTargets > 0) {
                    sweep(p, s, i, Math.max(s.tPrev, k.pre), Math.min(s.t, k.contact));
                }
                if (!s.contactFired[i] && s.t >= k.contact) {
                    s.contactFired[i] = true;
                    if (k.event != null) event(p, s, i, k);
                }
            }
            if (s.t >= s.clip.length) s.clip = null;
        }
        for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            if (w.age[0]++ > 13 || !w.owner.isAlive()) { it.remove(); continue; }
            w.pos[0] = w.pos[0].add(w.dir.multiply(1.1));
            Box box = new Box(w.pos[0], w.pos[0]).expand(1.5, 1.2, 1.5);
            for (LivingEntity t : w.world.getEntitiesByClass(LivingEntity.class, box, x -> canHit(w.owner, x) && !w.hit.contains(x.getId()))) {
                w.hit.add(t.getId());
                damage(w.owner, t, w.dmg, 0.6f, w.tier, 0, null);
                if (t.getGroup() == EntityGroup.UNDEAD) t.setOnFireFor(4);
            }
        }
    }

    // ---------------------------------------------------------------- хитбокс

    private static void sweep(ServerPlayerEntity p, State s, int idx, float from, float to) {
        Strike k = s.clip.strikes.get(idx);
        Set<Integer> done = s.hits.get(idx);
        if (done.size() >= k.maxTargets) return;
        ServerWorld world = p.getServerWorld();
        Vec3d[] fr = Blade.frame(p.getYaw(), p.getPitch());
        Vec3d sh = Blade.shoulder(p.getPos(), fr, p.getHeight());
        double len = s.ms.length;
        Box search = p.getBoundingBox().expand(len + 3.0);
        List<LivingEntity> cands = world.getEntitiesByClass(LivingEntity.class, search,
                e -> e != p && canHit(p, e) && !done.contains(e.getId()));
        if (cands.isEmpty()) return;
        List<LivingEntity> found = new ArrayList<>();
        int steps = Math.max(3, MathHelper.ceil((to - from) / 0.012f));
        for (int j = 0; j <= steps && found.size() < cands.size(); j++) {
            float tt = from + (to - from) * j / steps;
            Blade.State b = Blade.at(s.clip, tt, len);
            Vec3d d = Blade.toWorld(b.dir(), fr);
            for (double r = Blade.INNER; r <= b.reach() + 0.01; r += 0.3) {
                Vec3d pt = sh.add(d.multiply(Math.min(r, b.reach())));
                Box pb = new Box(pt, pt).expand(k.width);
                for (LivingEntity e : cands) {
                    if (!found.contains(e) && e.getBoundingBox().intersects(pb)) found.add(e);
                }
            }
        }
        found.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(p)));
        int maxShake = 0;
        float shake = 0;
        boolean any = false;
        boolean airCrit = p.fallDistance > 0 && !p.isOnGround();
        float base = baseDamage(p, s) * k.dmg * (airCrit ? 1.2f : 1f);
        for (LivingEntity e : found) {
            if (done.size() >= k.maxTargets) break;
            if (!p.canSee(e)) continue;
            done.add(e.getId());
            float ench = EnchantmentHelper.getAttackDamage(p.getMainHandStack(), e.getGroup());
            if (damage(p, e, base + ench * k.dmg, k.kb, s.ms.tier, k.roll, k)) {
                any = true;
                maxShake = Math.max(maxShake, k.hitstop);
                shake = Math.max(shake, k.shake);
                if (consecrated(p)) p.heal(1f);
            }
        }
        if (any) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(maxShake);
            buf.writeFloat(shake);
            ServerPlayNetworking.send(p, HIT, buf);
            p.getMainHandStack().damage(1, p, x -> x.sendEquipmentBreakStatus(net.minecraft.entity.EquipmentSlot.MAINHAND));
        }
    }

    private static float baseDamage(ServerPlayerEntity p, State s) {
        float b = (float) p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        // двуручное оружие со щитом или предметом во второй руке бьёт слабее
        if (p.getMainHandStack().getItem() instanceof PaladinSwordItem w && w.twoHanded && !p.getOffHandStack().isEmpty()) b *= 0.6f;
        return s != null && consecrated(p) ? b * 1.3f : b;
    }

    /** Нанести удар: урон, отбрасывание, огонь от зачарования, эффект попадания. */
    static boolean damage(ServerPlayerEntity p, LivingEntity e, float dmg, float kb, int tier, float roll, Strike k) {
        ServerWorld world = p.getServerWorld();
        e.timeUntilRegen = 0;
        boolean ok = e.damage(world.getDamageSources().playerAttack(p), dmg);
        if (!ok) return false;
        WeaponSkills.onHit(p, e, dmg);
        com.bleid.vestments.paladin.PaladinAbilities.onHit(p, e, dmg);
        Vec3d d = e.getPos().subtract(p.getPos());
        double l = Math.sqrt(d.x * d.x + d.z * d.z);
        float knock = kb + EnchantmentHelper.getKnockback(p) * 0.5f;
        if (l > 1e-4 && knock > 0) e.takeKnockback(knock, -d.x / l, -d.z / l);
        int fire = EnchantmentHelper.getFireAspect(p);
        if (fire > 0) e.setOnFireFor(fire * 4);
        p.onAttacking(e);
        p.addExhaustion(0.1f);
        Vec3d at = e.getPos().add(0, e.getHeight() * 0.55, 0);
        Fx.play(world, "pal_hit", at, null, 0, tier, roll, k != null ? k.shake : 0.3f);
        SoundEvent snd = tier >= 3 || (k != null && k.heavy) ? SoundEvents.ENTITY_PLAYER_ATTACK_CRIT : SoundEvents.ENTITY_PLAYER_ATTACK_STRONG;
        world.playSound(null, at.x, at.y, at.z, snd, SoundCategory.PLAYERS, 1.0f, 0.9f + world.random.nextFloat() * 0.2f);
        if (k != null && k.hitstop >= 2) {
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 0.7f, 0.7f);
        }
        if (tier >= 4) world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 0.8f, 1.4f);
        return true;
    }

    public static boolean canHit(PlayerEntity p, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isSpectator() || e == p) return false;
        if (e instanceof MerchantEntity) return false;           // жителей и торговцев широкие удары не задевают
        if (e instanceof TameableEntity t && t.isTamed() && p.getUuid().equals(t.getOwnerUuid())) return false;
        if (e instanceof PlayerEntity other && (other.isCreative() || p.isTeammate(other))) return false;
        return !e.isInvulnerable();
    }

    private static void swingSound(ServerPlayerEntity p, Moveset ms, Strike k) {
        ServerWorld w = p.getServerWorld();
        float pitch = 0.9f + w.random.nextFloat() * 0.25f + (k.heavy ? -0.25f : 0f);
        SoundEvent s = ms.tier >= 2 ? SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP : SoundEvents.ENTITY_PLAYER_ATTACK_WEAK;
        w.playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundCategory.PLAYERS, 0.9f, pitch);
        if (ms.tier >= 4) w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 0.5f, 1.3f);
    }

    // ---------------------------------------------------------------- особые действия

    private static void event(ServerPlayerEntity p, State s, int idx, Strike k) {
        ServerWorld w = p.getServerWorld();
        Vec3d[] fr = Blade.frame(p.getYaw(), p.getPitch());
        Set<Integer> hits = s.hits.get(idx);
        float base = baseDamage(p, s);
        int tier = s.ms.tier;
        switch (k.event) {
            case "crush" -> {
                for (int id : hits) if (w.getEntityById(id) instanceof LivingEntity e)
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
                Vec3d at = p.getPos().add(fr[0].multiply(1.6));
                Fx.play(w, "pal_shock", at, null, 0, 2.5f, tier, 0);
                w.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.4f, 1.5f);
            }
            case "launch" -> {
                for (int id : hits) if (w.getEntityById(id) instanceof LivingEntity e) {
                    e.addVelocity(0, 0.65, 0);
                    e.velocityModified = true;
                }
                Fx.play(w, "pal_launch", p.getPos().add(fr[0].multiply(1.5)), null, 0, tier, 0, 0);
            }
            case "shock" -> {
                Vec3d at = p.getPos().add(fr[0].multiply(1.6));
                aoe(p, at, 3.2, base * 0.6f, 0.5f, 0.35f, hits, tier);
                Fx.play(w, "pal_shock", at, null, 0, 3.2f, tier, 0);
                w.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5f, 1.3f);
            }
            case "burst" -> {
                Blade.State b = Blade.at(s.clip, k.contact, s.ms.length);
                Vec3d tip = Blade.shoulder(p.getPos(), fr, p.getHeight()).add(Blade.toWorld(b.dir(), fr).multiply(b.reach()));
                aoe(p, tip, 3.0, base * 0.8f, 0.9f, 0.2f, hits, tier);
                Fx.play(w, "pal_burst", tip, null, 0, 3.0f, tier, 0);
                w.playSound(null, tip.x, tip.y, tip.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 1.6f);
            }
            case "buff" -> {
                s.buffUntil = p.getServer().getTicks() + 160;
                Fx.play(w, "pal_consecrate", p, 160, 0);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.4f);
                p.sendMessage(Text.translatable("message.vestments.consecrated").formatted(Formatting.GOLD), true);
            }
            case "wave" -> {
                Vec3d dir = new Vec3d(fr[0].x, 0, fr[0].z).normalize();
                Vec3d start = p.getPos().add(0, 1.0, 0).add(dir.multiply(1.0));
                WAVES.add(new Wave(p, w, dir, new HashSet<>(hits), base * 2f, tier, new Vec3d[] { start }, new int[] { 0 }));
                Fx.play(w, "pal_wave", start, null, 14, p.getYaw(), tier, 1.1f);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.PLAYERS, 1.0f, 1.2f);
            }
            case "judgment" -> {
                Vec3d at = p.getPos();
                aoe(p, at, 6.0, base * 1.5f, 0.6f, 0.8f, hits, tier);
                for (PlayerEntity o : w.getPlayers()) {
                    if (o.squaredDistanceTo(at) > 36 || !o.isAlive()) continue;
                    o.heal(4f);
                    o.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0));
                }
                Fx.play(w, "pal_judgment", at, null, 0, 6f, tier, 0);
                w.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.2f, 0.9f);
                w.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8f, 0.8f);
            }
            default -> WeaponSkills.event(p, k, hits, base, tier);
        }
    }

    /** Удар по площади: всем, кого ещё не задело этим ударом (кроме игроков). */
    static void aoe(ServerPlayerEntity p, Vec3d at, double r, float dmg, float kb, float up, Set<Integer> skip, int tier) {
        ServerWorld w = p.getServerWorld();
        Box box = new Box(at, at).expand(r, 2.5, r);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, x -> canHit(p, x) && !(x instanceof PlayerEntity))) {
            if (skip.contains(e.getId()) || e.squaredDistanceTo(at) > r * r) continue;
            if (damage(p, e, dmg, kb, tier, 0, null)) {
                e.addVelocity(0, up, 0);
                e.velocityModified = true;
            }
        }
    }
}
