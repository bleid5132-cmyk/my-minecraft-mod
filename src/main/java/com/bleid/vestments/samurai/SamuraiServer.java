package com.bleid.vestments.samurai;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.classes.PlayerClasses;
import com.bleid.vestments.fx.Fx;
import com.bleid.vestments.paladin.combat.Blade;
import com.bleid.vestments.paladin.combat.Clip;
import com.bleid.vestments.paladin.combat.CombatServer;
import com.bleid.vestments.paladin.combat.Strike;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Самурай на сервере: ки, пассив «Путь меча», пять способностей, особые приёмы катан и бонусы комплектов.
 *
 *  Ки (0..100): +6 за удар катаной (боккэн ×1,5, бамбуковый доспех ×1,15), +20 за парирование,
 *  −10 при полученном уроне. «Путь меча»: каждый 5-й удар подряд без полученного урона — крит ×1,5.
 *  Способности: 0 Иай, 1 Парирование, 2 Шаг ветра (2 заряда), 3 Медитация, 4 Буря лепестков (полная ки).
 */
public final class SamuraiServer {
    public static final Identifier ABILITY = new Identifier(Vestments.MOD_ID, "sam_ability");
    public static final Identifier SYNC = new Identifier(Vestments.MOD_ID, "sam_sync");
    public static final int IAI = 0, PARRY = 1, WIND = 2, MEDITATE = 3, ULT = 4;
    public static final String[] NAMES = { "iai", "parry", "wind", "meditate", "ult" };
    private static final int[] COOLDOWN = { 12 * 20, 4 * 20, 6 * 20, 45 * 20, 90 * 20 };
    public static final float MAX_KI = 100f;
    private static final UUID STRAW_SPEED = UUID.fromString("5a3b9d1e-7c11-4e0a-9f2b-1d0c6a5e9b31");

    private static final class St {
        float ki;
        int streak;
        boolean critNext;
        final long[] ready = new long[5];
        final int[] total = { 1, 1, 1, 1, 1 };
        final long[] windReady = new long[2];
        long parryUntil, iframesUntil, meditating = -1;
        int sneak, hitCount;
        Vec3d clipStart = Vec3d.ZERO;
        float clipYaw;
        LivingEntity target;
        final List<LivingEntity> ultTargets = new ArrayList<>();
        final Map<Integer, Integer> slashes = new HashMap<>();
        boolean detonated;
        boolean dirty = true;
    }

    private static final Map<UUID, St> ST = new HashMap<>();
    private static boolean reflecting;

    private SamuraiServer() { }

    private static St st(PlayerEntity p) {
        return ST.computeIfAbsent(p.getUuid(), k -> new St());
    }

    public static boolean isSamurai(ServerPlayerEntity p) {
        return "samurai".equals(PlayerClasses.of(p));
    }

    public static float ki(PlayerEntity p) {
        St s = ST.get(p.getUuid());
        return s == null ? 0 : s.ki;
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ABILITY, (server, player, handler, buf, sender) -> {
            int id = buf.readVarInt();
            int target = buf.isReadable() ? buf.readVarInt() : -1;
            server.execute(() -> use(player, id, target));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, srv) -> ST.remove(h.getPlayer().getUuid()));
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> st(h.getPlayer()).dirty = true);
        ServerTickEvents.END_SERVER_TICK.register(SamuraiServer::tick);
        // самурай не берёт щит
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!world.isClient && stack.getItem() instanceof ShieldItem && player instanceof ServerPlayerEntity sp && isSamurai(sp)) {
                sp.sendMessage(Text.translatable("message.vestments.samurai_no_shield").formatted(Formatting.RED), true);
                return TypedActionResult.fail(stack);
            }
            return TypedActionResult.pass(stack);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(SamuraiServer::allowDamage);
    }

    // ---------------------------------------------------------------- урон по самураю: парирование, рывок, падение

    private static boolean allowDamage(LivingEntity target, net.minecraft.entity.damage.DamageSource source, float amount) {
        if (reflecting || !(target instanceof ServerPlayerEntity p) || !isSamurai(p)) return true;
        St s = st(p);
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        if (source.isOf(DamageTypes.FALL) && SamuraiGear.wears(p, "domaru") && amount <= 3.01f) return false;
        if (now < s.iframesUntil && source.getAttacker() != null) return false;
        if (now < s.parryUntil && (source.getAttacker() != null || source.getSource() instanceof ProjectileEntity)) {
            s.parryUntil = 0;
            parried(p, s, source, amount);
            return false;
        }
        if (amount > 0 && !p.isInvulnerableTo(source)) {
            s.streak = 0;
            s.ki = Math.max(0, s.ki - 10);
            s.dirty = true;
            if (s.meditating >= 0) interruptMeditation(p, s);
        }
        return true;
    }

    private static void parried(ServerPlayerEntity p, St s, net.minecraft.entity.damage.DamageSource source, float amount) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = p.getPos().add(0, 1.3, 0).add(p.getRotationVector().multiply(0.7));
        w.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1f, 1.5f);
        w.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.35f, 1.9f);
        Fx.play(w, "pal_parry", at, null, 0, 3, 0, 0);
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 16, 0.2, 0.2, 0.2, 0.25);
        // стрелы и снаряды отлетают обратно
        if (source.getSource() instanceof ProjectileEntity proj) {
            proj.setVelocity(proj.getVelocity().multiply(-0.8));
            proj.setOwner(p);
            if (proj instanceof PersistentProjectileEntity arrow) arrow.setDamage(arrow.getDamage() * 1.5);
        }
        if (source.getAttacker() instanceof LivingEntity att) {
            att.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, 4));
            att.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 25, 1));
            Vec3d d = att.getPos().subtract(p.getPos());
            double l = Math.max(1e-3, Math.sqrt(d.x * d.x + d.z * d.z));
            att.takeKnockback(0.6, -d.x / l, -d.z / l);
            Fx.play(w, "pal_stun", att.getPos(), att, 25, 0, 0, 0);
            if (SamuraiGear.wears(p, "akadou")) {
                reflecting = true;
                att.damage(w.getDamageSources().thorns(p), amount * 0.3f);
                reflecting = false;
            }
        }
        s.critNext = true;
        gainKi(p, s, 20);
        Clip rip = SamuraiMoves.clip(katanaId(p), "riposte");
        if (rip != null && SamuraiGear.held(p) != null) CombatServer.forcePlay(p, rip);
    }

    // ---------------------------------------------------------------- удары катаной

    /** Перед уроном катаной: крит «Пути меча», гарантированный крит после парирования, удар в спину синоби. */
    public static float beforeHit(ServerPlayerEntity p, LivingEntity e, float dmg) {
        if (!isSamurai(p)) return dmg;
        St s = st(p);
        s.streak++;
        boolean crit = s.critNext || s.streak % 5 == 0;
        s.critNext = false;
        if (crit) {
            dmg *= 1.5f;
            ServerWorld w = p.getServerWorld();
            w.spawnParticles(ParticleTypes.CRIT, e.getX(), e.getBodyY(0.6), e.getZ(), 18, 0.3, 0.3, 0.3, 0.4);
            w.spawnParticles(ParticleTypes.CHERRY_LEAVES, e.getX(), e.getBodyY(0.7), e.getZ(), 6, 0.4, 0.4, 0.4, 0.05);
            w.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1f, 1.2f);
            critFlag = true;
        } else {
            critFlag = false;
        }
        if (SamuraiGear.wears(p, "shinobi")) {
            Vec3d look = Vec3d.fromPolar(0, e.getBodyYaw());
            Vec3d toP = p.getPos().subtract(e.getPos()).normalize();
            if (look.dotProduct(toP) < -0.3) dmg *= 1.2f;
        }
        return dmg;
    }

    private static boolean critFlag;

    public static void afterHit(ServerPlayerEntity p, LivingEntity e, float dmg) {
        if (!isSamurai(p)) return;
        St s = st(p);
        KatanaItem k = SamuraiGear.held(p);
        float gain = 6f;
        if (k != null && k.spec.id.equals("bokken")) gain *= 1.5f;
        gainKi(p, s, gain);
        ServerWorld w = p.getServerWorld();
        if (!e.isAlive()) {
            if (SamuraiGear.wears(p, "kinkou")) p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 1));
            if (critFlag && k != null && k.spec.id.equals("kinryu")) {
                net.minecraft.entity.ExperienceOrbEntity.spawn(w, e.getPos(), 6 + w.random.nextInt(6));
                w.spawnParticles(ParticleTypes.WAX_ON, e.getX(), e.getBodyY(0.5), e.getZ(), 14, 0.4, 0.4, 0.4, 0.1);
            }
        }
        // Амэ-но-Мурагумо: каждый 5-й удар — летящий клинок ветра
        if (k != null && k.spec.id.equals("murakumo")) {
            if (++s.hitCount % 5 == 0) {
                CombatServer.spawnWave(p, CombatServer.baseDamage(p) * 0.8f, 5, "pal_wave");
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_2, SoundCategory.PLAYERS, 0.8f, 1.4f);
            }
        }
        // Энма: удары поджигают
        if (k != null && k.spec.id.equals("enma")) e.setOnFireFor(3);
    }

    private static void gainKi(ServerPlayerEntity p, St s, float v) {
        if (SamuraiGear.wears(p, "bamboo")) v *= 1.15f;
        s.ki = Math.min(MAX_KI, s.ki + v);
        s.dirty = true;
    }

    // ---------------------------------------------------------------- способности

    private static void use(ServerPlayerEntity p, int id, int targetId) {
        if (id < 0 || id > 4 || !p.isAlive() || p.isSpectator() || !isSamurai(p)) return;
        KatanaItem k = SamuraiGear.held(p);
        St s = st(p);
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        if (k == null) {
            p.sendMessage(Text.translatable("message.vestments.samurai_need_katana").formatted(Formatting.RED), true);
            return;
        }
        if (id == WIND) {
            int slot = now >= s.windReady[0] ? 0 : now >= s.windReady[1] ? 1 : -1;
            if (slot < 0) return;
            if (p.isTouchingWater() && !SamuraiGear.wears(p, "ryujin")) return;
            int cd = COOLDOWN[WIND];
            s.windReady[slot] = Math.max(now, Math.max(s.windReady[0], s.windReady[1]) - cd) + cd;
            s.iframesUntil = now + 6;
            play(p, k, "wind");
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1f, 1.6f);
            w.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 10, 0.3, 0.1, 0.3, 0.05);
            s.dirty = true;
            return;
        }
        if (now < s.ready[id]) return;
        int cd = COOLDOWN[id];
        switch (id) {
            case IAI -> {
                if (SamuraiGear.wears(p, "ooyoroi")) cd = (int) (cd * 0.7f);
                play(p, k, "iai");
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.PLAYERS, 0.8f, 1.4f);
            }
            case PARRY -> {
                int win = 8;                                     // 0,4 с
                if (SamuraiGear.wears(p, "kozane")) win += 2;
                if (k.spec.id.equals("seisui")) win += 3;
                s.parryUntil = now + win;
                play(p, k, "parry");
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 0.6f, 1.6f);
            }
            case MEDITATE -> {
                s.meditating = now;
                play(p, k, "meditate");
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 0.7f);
            }
            case ULT -> {
                float need = SamuraiGear.wears(p, "shogun") ? MAX_KI * 0.5f : MAX_KI;
                if (s.ki < need - 0.01f) {
                    p.sendMessage(Text.translatable("message.vestments.samurai_need_ki").formatted(Formatting.RED), true);
                    return;
                }
                List<LivingEntity> near = w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(12),
                        e -> CombatServer.canHit(p, e) && e.squaredDistanceTo(p) <= 144 && !(e instanceof PlayerEntity));
                if (near.isEmpty()) {
                    p.sendMessage(Text.translatable("message.vestments.samurai_no_target").formatted(Formatting.RED), true);
                    return;
                }
                s.ki -= need;
                s.ultTargets.clear();
                s.ultTargets.addAll(near);
                s.slashes.clear();
                s.detonated = false;
                Entity picked = targetId >= 0 ? w.getEntityById(targetId) : null;
                s.target = picked instanceof LivingEntity le && near.contains(le) ? le
                        : near.stream().min((a, b) -> Double.compare(a.squaredDistanceTo(p), b.squaredDistanceTo(p))).get();
                for (LivingEntity e : near) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 5));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0));
                }
                play(p, k, "ult");
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 1f, 1.3f);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 0.8f, 1.6f);
                w.spawnParticles(ParticleTypes.CHERRY_LEAVES, p.getX(), p.getY() + 2, p.getZ(), 120, 6, 2, 6, 0.02);
            }
            default -> { }
        }
        s.ready[id] = now + cd;
        s.total[id] = cd;
        s.dirty = true;
    }

    private static void play(ServerPlayerEntity p, KatanaItem k, String name) {
        Clip c = SamuraiMoves.clip(k.spec.id, name);
        if (c != null) CombatServer.forcePlay(p, c);
    }

    private static String katanaId(ServerPlayerEntity p) {
        KatanaItem k = SamuraiGear.held(p);
        return k == null ? "bokken" : k.spec.id;
    }

    private static void interruptMeditation(ServerPlayerEntity p, St s) {
        s.meditating = -1;
        Clip draw = SamuraiMoves.clip(katanaId(p), "draw");
        if (draw != null && SamuraiGear.held(p) != null) CombatServer.forcePlay(p, draw);
        else CombatServer.stop(p);
        p.sendMessage(Text.translatable("message.vestments.samurai_meditation_broken").formatted(Formatting.RED), true);
    }

    // ---------------------------------------------------------------- ход приёма (вызывается из CombatServer)

    public static void clipStarted(ServerPlayerEntity p, Clip c) {
        St s = st(p);
        s.clipStart = p.getPos();
        s.clipYaw = p.getYaw();
        String n = SamuraiMoves.nameOf(c);
        if (n.equals("k_kage")) {
            s.target = lookTarget(p, 12);
        } else if (!n.equals("ult") && !n.equals("ult_end")) {
            if (n.startsWith("k_")) s.target = null;
        }
        if (n.equals("ult_end")) s.detonated = false;
    }

    public static void tickClip(ServerPlayerEntity p, Clip c, float t0, float t1) {
        St s = st(p);
        ServerWorld w = p.getServerWorld();
        String n = SamuraiMoves.nameOf(c);
        // рывки-телепорты вокруг цели
        for (float[] wp : c.warps) {
            if (!(t0 < wp[0] && t1 >= wp[0])) continue;
            if (s.target == null || !s.target.isAlive()) continue;
            double yaw = Math.toRadians(s.clipYaw);
            Vec3d fwd = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw)), left = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
            Vec3d to = s.target.getPos().add(left.multiply(wp[1])).add(fwd.multiply(wp[2] - 3));
            to = safe(w, p, to);
            w.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 8, 0.3, 0.6, 0.3, 0.02);
            float face = s.clipYaw + wp[3];
            p.networkHandler.requestTeleport(to.x, to.y, to.z, face, p.getPitch());
            p.setYaw(face);
            p.setHeadYaw(face);
            w.playSound(null, to.x, to.y, to.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.5f, 1.8f);
            if (n.equals("ult")) w.spawnParticles(ParticleTypes.CHERRY_LEAVES, to.x, to.y + 1, to.z, 14, 0.4, 0.6, 0.4, 0.02);
        }
        // иай: рез на всём пути рывка
        if (n.equals("iai") && t0 < 0.5f && t1 >= 0.5f) iaiCut(p, s);
        if (n.equals("iai") && t1 > 0.26f && t1 < 0.55f) {
            w.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
        }
        // медитация: вылечить по завершении
        if (n.equals("meditate") && t0 < 2.1f && t1 >= 2.1f && s.meditating >= 0) {
            p.heal(12f);
            gainKi(p, s, MAX_KI / 3f);
            s.meditating = -1;
            w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 20, 0.5, 0.6, 0.5, 0.05);
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.6f);
        }
        if (n.equals("meditate") && s.meditating >= 0 && w.getTime() % 4 == 0) {
            w.spawnParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 0.3, p.getZ(), 6, 0.6, 0.1, 0.6, 0.2);
        }
        // ульта: щелчок ножен — все разрезы взрываются
        if (n.equals("ult_end") && t0 < 1.34f && t1 >= 1.34f && !s.detonated) detonate(p, s);
        // эффекты приёма (частицы и звуки, общие для всех игроков)
        for (Object[] f : c.fx) {
            float ft = (Float) f[0];
            if (t0 < ft && t1 >= ft) fx(p, s, (String) f[1]);
        }
    }

    public static void clipEnded(ServerPlayerEntity p, Clip c) {
        String n = SamuraiMoves.nameOf(c);
        St s = st(p);
        if (n.equals("ult")) {
            Clip end = SamuraiMoves.clip(katanaId(p), "ult_end");
            if (end != null && SamuraiGear.held(p) != null) CombatServer.forcePlay(p, end);
        }
        if (n.equals("meditate")) s.meditating = -1;
    }

    private static Vec3d safe(ServerWorld w, ServerPlayerEntity p, Vec3d to) {
        // стоим на земле: ищем свободное место по высоте рядом
        for (int dy = 0; dy <= 3; dy++) {
            for (int sgn : new int[] { 1, -1 }) {
                Vec3d c = to.add(0, dy * sgn, 0);
                if (w.isSpaceEmpty(p, p.getDimensions(p.getPose()).getBoxAt(c))) return c;
            }
        }
        return p.getPos();
    }

    private static LivingEntity lookTarget(ServerPlayerEntity p, double range) {
        Vec3d eye = p.getEyePos(), look = p.getRotationVector();
        LivingEntity best = null;
        double bestScore = 0;
        for (LivingEntity e : p.getServerWorld().getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(range),
                e -> CombatServer.canHit(p, e))) {
            Vec3d d = e.getPos().add(0, e.getHeight() * 0.5, 0).subtract(eye);
            double dist = d.length();
            if (dist > range) continue;
            double dot = d.normalize().dotProduct(look);
            if (dot < 0.8) continue;
            double score = dot - dist * 0.01;
            if (score > bestScore && p.canSee(e)) { bestScore = score; best = e; }
        }
        return best;
    }

    private static void iaiCut(ServerPlayerEntity p, St s) {
        ServerWorld w = p.getServerWorld();
        Vec3d a = s.clipStart.add(0, 1, 0), b = p.getPos().add(0, 1, 0).add(p.getRotationVector().multiply(1.6));
        Box box = new Box(a, b).expand(1.6);
        float dmg = CombatServer.baseDamage(p) * 2f;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, x -> CombatServer.canHit(p, x))) {
            if (distToSegment(e.getPos().add(0, e.getHeight() * 0.5, 0), a, b) > 1.4 + e.getWidth() * 0.5) continue;
            CombatServer.damage(p, e, dmg, 0.5f, 3, 0, null);
            w.spawnParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getBodyY(0.6), e.getZ(), 2, 0.2, 0.2, 0.2, 0);
        }
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2f, 0.7f);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.PLAYERS, 0.8f, 1.6f);
        // светлая полоса вдоль пути
        Vec3d d = b.subtract(a);
        for (int i = 0; i <= 24; i++) {
            Vec3d q = a.add(d.multiply(i / 24.0));
            w.spawnParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private static double distToSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double l2 = ab.lengthSquared();
        double t = l2 < 1e-6 ? 0 : MathHelper.clamp(p.subtract(a).dotProduct(ab) / l2, 0, 1);
        return p.distanceTo(a.add(ab.multiply(t)));
    }

    private static void detonate(ServerPlayerEntity p, St s) {
        s.detonated = true;
        ServerWorld w = p.getServerWorld();
        float base = CombatServer.baseDamage(p);
        for (LivingEntity e : s.ultTargets) {
            if (!e.isAlive()) continue;
            int n = s.slashes.getOrDefault(e.getId(), 0);
            if (n == 0 && e.squaredDistanceTo(p) > 64) continue;
            CombatServer.damage(p, e, base * (0.8f + 0.5f * n), 0.8f, 5, 0, null);
            w.spawnParticles(ParticleTypes.CHERRY_LEAVES, e.getX(), e.getBodyY(0.6), e.getZ(), 40, 0.6, 0.6, 0.6, 0.15);
            w.spawnParticles(ParticleTypes.EXPLOSION, e.getX(), e.getBodyY(0.6), e.getZ(), 2, 0.3, 0.3, 0.3, 0);
            w.spawnParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getBodyY(0.6), e.getZ(), 5, 0.5, 0.5, 0.5, 0);
        }
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.9f, 1.4f);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1f, 1.8f);
        s.ultTargets.clear();
        s.slashes.clear();
    }

    /** Особые события ударов катан (при контакте удара). true — обработано. */
    public static boolean event(ServerPlayerEntity p, Clip c, Strike k, Set<Integer> hits, float base) {
        ServerWorld w = p.getServerWorld();
        St s = st(p);
        Vec3d[] fr = Blade.frame(p.getYaw(), p.getPitch());
        Vec3d fwd = new Vec3d(fr[0].x, 0, fr[0].z).normalize();
        switch (k.event) {
            case "stun" -> {
                for (int id : hits) if (w.getEntityById(id) instanceof LivingEntity e) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 9));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 10, 4));
                    Fx.play(w, "pal_stun", e.getPos(), e, 10, 0, 0, 0);
                }
                Vec3d at = p.getPos().add(fwd.multiply(1.5));
                w.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK,
                        net.minecraft.block.Blocks.COBBLESTONE.getDefaultState()), at.x, at.y + 0.2, at.z, 30, 0.6, 0.1, 0.6, 0.2);
                w.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1f, 0.8f);
            }
            case "weakness" -> {
                for (int id : hits) if (w.getEntityById(id) instanceof LivingEntity e)
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0));
                Vec3d at = p.getPos().add(fwd.multiply(1.6)).add(0, 1, 0);
                w.spawnParticles(ParticleTypes.WAX_OFF, at.x, at.y, at.z, 18, 0.5, 0.4, 0.5, 0.1);
            }
            case "spin3" -> {
                CombatServer.aoe(p, p.getPos(), 3.0, base, 0.6f, 0.15f, hits, 2);
                w.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1, p.getZ(), 8, 1.5, 0.2, 1.5, 0);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2f, 0.8f);
            }
            case "gold" -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 0));
                w.spawnParticles(ParticleTypes.WAX_ON, p.getX(), p.getY() + 1, p.getZ(), 20, 0.5, 0.6, 0.5, 0.1);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_ARMOR_EQUIP_GOLD, SoundCategory.PLAYERS, 1f, 1.3f);
            }
            case "wave" -> {
                CombatServer.spawnWave(p, base * 1.6f, 3, "pal_wave");
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2f, 0.8f);
                Vec3d at = p.getPos().add(fwd.multiply(2)).add(0, 1, 0);
                w.spawnParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 30, 0.8, 0.5, 0.8, 0.05);
            }
            case "backstab" -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 60, 0));
                w.spawnParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1, p.getZ(), 16, 0.4, 0.6, 0.4, 0.03);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.PLAYERS, 0.4f, 2f);
            }
            case "fire" -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 0));
                Vec3d eye = p.getEyePos();
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(8), x -> CombatServer.canHit(p, x))) {
                    Vec3d d = e.getPos().add(0, e.getHeight() * 0.5, 0).subtract(eye);
                    if (d.length() > 8.5) continue;
                    Vec3d dh = new Vec3d(d.x, 0, d.z).normalize();
                    if (dh.dotProduct(fwd) < Math.cos(Math.toRadians(28))) continue;
                    if (!hits.contains(e.getId())) CombatServer.damage(p, e, base * 1.2f, 0.4f, 4, 0, null);
                    e.setOnFireFor(5);
                }
                for (int i = 0; i < 60; i++) {
                    double ang = Math.toRadians((w.random.nextDouble() - 0.5) * 56), sp = 0.4 + w.random.nextDouble() * 0.5;
                    Vec3d dir = fwd.rotateY((float) ang);
                    w.spawnParticles(i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, eye.x + dir.x, eye.y - 0.3, eye.z + dir.z, 0,
                            dir.x, (w.random.nextDouble() - 0.4) * 0.2, dir.z, sp);
                }
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.2f, 0.8f);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1f, 0.7f);
            }
            case "heaven" -> {
                Vec3d start = p.getPos().add(0, 1, 0);
                Vec3d end = start.add(fwd.multiply(20));
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(start, end).expand(2.5), x -> CombatServer.canHit(p, x))) {
                    if (distToSegment(e.getPos().add(0, e.getHeight() * 0.5, 0), start, end) > 2.2 + e.getWidth() * 0.5) continue;
                    CombatServer.damage(p, e, 20f, 1.2f, 5, 90, null);
                }
                for (int i = 1; i <= 5; i++) {
                    Vec3d at = p.getPos().add(fwd.multiply(i * 3.5));
                    LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
                    if (bolt != null) {
                        bolt.refreshPositionAfterTeleport(at.x, at.y, at.z);
                        bolt.setCosmetic(true);
                        w.spawnEntity(bolt);
                    }
                }
                for (int i = 0; i <= 40; i++) {
                    Vec3d q = start.add(fwd.multiply(i * 0.5));
                    w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y + 0.5, q.z, 4, 0.3, 1.0, 0.3, 0.1);
                    w.spawnParticles(ParticleTypes.SWEEP_ATTACK, q.x, q.y + 0.6, q.z, 1, 0.1, 0.4, 0.1, 0);
                }
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1f, 1.2f);
            }
            default -> { return false; }
        }
        return true;
    }

    /** Отметить разрез ульты на цели (каждое попадание ударом ульты). */
    private static void ultSlash(ServerPlayerEntity p, St s) {
        for (LivingEntity e : s.ultTargets) {
            if (!e.isAlive() || e.squaredDistanceTo(p) > 16) continue;
            s.slashes.merge(e.getId(), 1, Integer::sum);
        }
    }

    private static void fx(ServerPlayerEntity p, St s, String type) {
        ServerWorld w = p.getServerWorld();
        Vec3d c = p.getPos().add(0, 1.2, 0);
        switch (type) {
            case "click" -> {
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 0.7f, 1.5f);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 0.5f, 1.8f);
            }
            case "blood" -> w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.PLAYERS, 0.6f, 1.4f);
            case "slash" -> {
                ultSlash(p, s);
                if (s.target != null) {
                    Vec3d t = s.target.getPos().add(0, s.target.getHeight() * 0.6, 0);
                    w.spawnParticles(ParticleTypes.SWEEP_ATTACK, t.x, t.y, t.z, 2, 0.3, 0.3, 0.3, 0);
                    w.spawnParticles(ParticleTypes.CHERRY_LEAVES, t.x, t.y, t.z, 10, 0.5, 0.5, 0.5, 0.05);
                }
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1f, 1.5f);
            }
            case "aura" -> w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, c.x, p.getY() + 0.2, c.z, 12, 0.6, 0.1, 0.6, 0.02);
            case "spark" -> w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.1);
            case "smoke" -> w.spawnParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 30, 0.4, 0.7, 0.4, 0.02);
            case "embers" -> w.spawnParticles(ParticleTypes.SMALL_FLAME, c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0.02);
            case "charge" -> {
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1, c.z, 40, 0.8, 0.8, 0.8, 0.1);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.9f, 1.6f);
            }
            case "goldburst" -> w.spawnParticles(ParticleTypes.WAX_ON, c.x, c.y, c.z, 30, 0.6, 0.5, 0.6, 0.15);
            case "splash" -> w.spawnParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 50, 0.8, 0.4, 0.8, 0.2);
            case "ring" -> w.spawnParticles(ParticleTypes.SWEEP_ATTACK, c.x, p.getY() + 0.8, c.z, 6, 1.5, 0.1, 1.5, 0);
            case "rocks", "sparks", "thrust", "gather", "wind", "afterimage", "swish", "flash", "petals", "slowmo",
                 "detonate", "iai_cut", "vanish", "wave", "big_wave", "fire_cone", "lightning" -> { }
            default -> { }
        }
        if (type.equals("afterimage")) {
            w.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 6, 0.25, 0.5, 0.25, 0.01);
        }
        if (type.equals("petals")) {
            w.spawnParticles(ParticleTypes.CHERRY_LEAVES, p.getX(), p.getY() + 3, p.getZ(), 160, 7, 2, 7, 0.02);
        }
    }

    // ---------------------------------------------------------------- такт: бонусы комплектов и синхронизация

    private static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!isSamurai(p)) { straw(p, false); continue; }
            St s = st(p);
            String set = SamuraiGear.fullSet(p);
            straw(p, "straw".equals(set));
            if (p.isSneaking()) s.sneak++; else s.sneak = 0;
            if ("shinobi".equals(set) && s.sneak >= 40 && s.sneak % 10 == 0) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 25, 0, false, false, true));
            }
            if (server.getTicks() % 10 == 0) {
                if ("ryujin".equals(set)) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 220, 0, false, false, true));
                    if (p.isTouchingWater()) p.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 40, 0, false, false, true));
                }
                if ("shogun".equals(set)) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 220, 0, false, false, true));
                    for (PlayerEntity o : p.getServerWorld().getPlayers()) {
                        if (o != p && o.isAlive() && o.squaredDistanceTo(p) < 64 && (o.isTeammate(p) || p.getScoreboardTeam() == null))
                            o.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 0, false, false, true));
                    }
                }
            }
            if (s.dirty || server.getTicks() % 40 == 0) sync(p, s);
        }
    }

    private static void straw(ServerPlayerEntity p, boolean on) {
        EntityAttributeInstance a = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (a == null) return;
        boolean has = a.getModifier(STRAW_SPEED) != null;
        if (on && !has) a.addTemporaryModifier(new EntityAttributeModifier(STRAW_SPEED, "samurai straw", 0.1,
                EntityAttributeModifier.Operation.MULTIPLY_BASE));
        else if (!on && has) a.removeModifier(STRAW_SPEED);
    }

    private static void sync(ServerPlayerEntity p, St s) {
        s.dirty = false;
        long now = p.getServerWorld().getTime();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(s.ki);
        buf.writeFloat(SamuraiGear.wears(p, "shogun") ? MAX_KI * 0.5f : MAX_KI);
        buf.writeVarInt(s.streak % 5);
        for (int i = 0; i < 5; i++) {
            long left = Math.max(0, s.ready[i] - now);
            if (i == WIND) {
                int charges = (now >= s.windReady[0] ? 1 : 0) + (now >= s.windReady[1] ? 1 : 0);
                left = charges > 0 ? 0 : Math.min(s.windReady[0], s.windReady[1]) - now;
                buf.writeVarInt((int) Math.max(0, left));
                buf.writeVarInt(COOLDOWN[WIND]);
                buf.writeVarInt(charges);
                continue;
            }
            buf.writeVarInt((int) left);
            buf.writeVarInt(Math.max(1, s.total[i]));
        }
        ServerPlayNetworking.send(p, SYNC, buf);
    }

    @SuppressWarnings("unused")
    private static void sound(ServerWorld w, Vec3d at, SoundEvent s, float v, float pitch) {
        w.playSound(null, at.x, at.y, at.z, s, SoundCategory.PLAYERS, v, pitch);
    }

    @SuppressWarnings("unused")
    private static ParticleEffect petal() {
        return ParticleTypes.CHERRY_LEAVES;
    }

    /** Чужие игроки без класса самурая — для ограничения монстров (не используется). */
    @SuppressWarnings("unused")
    private static boolean hostile(Entity e) {
        return e instanceof Monster;
    }
}
