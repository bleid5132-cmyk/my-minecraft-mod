package com.bleid.vestments.patriarch;

import com.bleid.vestments.StaffOfLightItem;
import com.bleid.vestments.Vestments;
import com.bleid.vestments.service.Ranks;
import com.bleid.vestments.service.ServicePoints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Способности Библии. У каждой степени — две обычные способности и ульта «Сонм душ».
 * В степени они открываются по санам: 1-й сан — первая, 2-й — вторая, 3-й и выше — ульта.
 * При переходе в следующую степень набор сменяется на способности новой степени.
 *   I  Диаконат:      «Утешение» (лечение рядом), «Свеча веры» (вспышка света против врагов).
 *   II Пресвитерство: «Благословение стойкости» (щит), «Печать чистоты» (печать на земле).
 *   III Епископат:    «Ослепление» (заморозка мобов), «Небесный свет» (столп света в точке взгляда).
 * Клавиши Z, V, B — ячейки 1, 2 и ульта.
 */
public final class BibleAbilities {
    public static final Identifier CAST = new Identifier(Vestments.MOD_ID, "patriarch_cast");
    public static final int SLOTS = 3;
    public static final String ULT = "souls";
    /** Обычные способности по степеням. */
    public static final String[][] DEGREE = {
            { "consolation", "candle" },
            { "fortitude", "seal" },
            { "blinding", "heavenly_light" } };
    private static final Map<String, Integer> MANA = Map.of(
            "consolation", 20, "candle", 25, "fortitude", 20, "seal", 60,
            "blinding", 20, "heavenly_light", 50, ULT, 70);
    private static final Map<String, Integer> COOLDOWN = Map.of(
            "consolation", 15 * 20, "candle", 25 * 20, "fortitude", 20 * 20, "seal", 40 * 20,
            "blinding", 120 * 20, "heavenly_light", 45 * 20, ULT, 90 * 20);

    private static final int SEAL_TICKS = 10 * 20;
    private static final double SEAL_RADIUS = 5.0;
    private static final int SHIELD_TICKS = 5 * 20;
    private static final double BLIND_RADIUS = 15.0;
    private static final int FREEZE_TICKS = 5 * 20;
    private static final String FROZEN_TAG = "vestments_frozen";

    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> SHIELDS = new HashMap<>();
    private static final List<Seal> SEALS = new ArrayList<>();
    private static final Map<UUID, Frozen> FROZEN = new HashMap<>();

    private record Seal(RegistryKey<World> world, Vec3d pos, long end) { }
    private record Frozen(RegistryKey<World> world, long end, boolean hadNoAi) { }

    private BibleAbilities() { }

    private static long now(MinecraftServer server) {
        return server.getTicks();
    }

    public static String abilityAt(int degree, int slot) {
        return slot == 2 ? ULT : DEGREE[Math.max(0, Math.min(2, degree))][slot];
    }

    public static int manaCost(String id) { return MANA.getOrDefault(id, 0); }
    public static int cooldownTicks(String id) { return COOLDOWN.getOrDefault(id, 0); }

    /** Сколько способностей открыто в текущей степени: 1-й сан — 1, 2-й — 2, 3-й и выше — 3. */
    public static int unlocked(int rank) {
        int inDegree = rank - Ranks.degreeStart(Ranks.degreeOf(rank));
        return Math.min(3, inDegree + 1);
    }

    /** Сан, с которого открывается ячейка slot в степени degree. */
    public static int unlockRank(int degree, int slot) {
        return Ranks.degreeStart(degree) + slot;
    }

    public static int cooldownLeft(ServerPlayerEntity p, int slot) {
        String id = abilityAt(Ranks.degreeOf(ServicePoints.rank(p)), slot);
        Map<String, Long> cd = COOLDOWNS.get(p.getUuid());
        if (cd == null) return 0;
        return (int) Math.max(0, cd.getOrDefault(id, 0L) - now(p.getServer()));
    }

    public static boolean shielded(Entity e) {
        Long end = SHIELDS.get(e.getUuid());
        return end != null && e.getServer() != null && end > now(e.getServer());
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CAST, (server, player, handler, buf, sender) -> {
            int i = buf.readVarInt();
            boolean reselect = buf.readBoolean();
            server.execute(() -> {
                if (i == 2 && reselect) SoulAllies.openSelection(player);
                else cast(player, i);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(BibleAbilities::tick);
        // щит стойкости: полная неуязвимость (кроме пустоты и /kill)
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amount) ->
                !shielded(e) || src.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_INVULNERABILITY));
        // моб с меткой заморозки загрузился, а заморозки уже нет (перезапуск, выгрузка чанка) — размораживаем
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof MobEntity mob && mob.getCommandTags().contains(FROZEN_TAG)
                    && !FROZEN.containsKey(mob.getUuid())) {
                mob.setAiDisabled(false);
                mob.removeScoreboardTag(FROZEN_TAG);
            }
        });
    }

    static void cast(ServerPlayerEntity p, int slot) {
        if (slot < 0 || slot >= SLOTS || !p.isAlive() || !ServicePoints.canServe(p)) return;
        if (!Mana.learned(p.getServer(), p.getUuid())) {
            p.sendMessage(Text.translatable("message.vestments.book_not_learned").formatted(Formatting.RED), true);
            return;
        }
        int rank = ServicePoints.rank(p);
        int degree = Ranks.degreeOf(rank);
        if (slot >= unlocked(rank)) {
            p.sendMessage(Text.translatable("message.vestments.ability_locked",
                    Text.translatable(Ranks.nameKey(unlockRank(degree, slot)))).formatted(Formatting.RED), true);
            return;
        }
        String id = abilityAt(degree, slot);
        if (cooldownLeft(p, slot) > 0) return;
        if (id.equals(ULT) && !SoulAllies.canSummon(p)) return;
        if (!Mana.spend(p, manaCost(id))) {
            p.sendMessage(Text.translatable("message.vestments.no_mana").formatted(Formatting.AQUA), true);
            return;
        }
        ServerWorld w = p.getServerWorld();
        switch (id) {
            case "consolation" -> consolation(w, p);
            case "candle" -> candle(w, p);
            case "fortitude" -> fortitude(w, p);
            case "seal" -> seal(w, p);
            case "blinding" -> blinding(w, p);
            case "heavenly_light" -> heavenlyLight(w, p);
            default -> SoulAllies.summon(w, p);
        }
        COOLDOWNS.computeIfAbsent(p.getUuid(), k -> new HashMap<>()).put(id, now(p.getServer()) + cooldownTicks(id));
        Mana.sync(p);
    }

    // ---------------------------------------------------------- I. Утешение

    private static void consolation(ServerWorld w, ServerPlayerEntity p) {
        double r = 4.0;
        for (PlayerEntity o : w.getPlayers()) {
            if (!o.isAlive() || o.isSpectator() || o.squaredDistanceTo(p) > r * r) continue;
            o.heal(6f);
            w.spawnParticles(ParticleTypes.HEART, o.getX(), o.getBodyY(1.0) + 0.3, o.getZ(), 5, 0.4, 0.3, 0.4, 0);
        }
        for (int k = 0; k < 24; k++) {
            double a = k * Math.PI / 12;
            w.spawnParticles(Vestments.SERVICE_ORB, p.getX() + Math.cos(a) * r, p.getY() + 0.2, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1f, 1.4f);
    }

    // ---------------------------------------------------------- I. Свеча веры

    private static void candle(ServerWorld w, ServerPlayerEntity p) {
        double r = 6.0;
        Box box = p.getBoundingBox().expand(r);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != p)) {
            if (!StaffOfLightItem.canHit(e) || e.squaredDistanceTo(p) > r * r) continue;
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, true, true));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0, true, false));
            if (e.getGroup() == EntityGroup.UNDEAD) {
                e.damage(w.getDamageSources().indirectMagic(p, p), 4f);
                e.setOnFireFor(3);
            }
        }
        for (int k = 0; k < 40; k++) {
            double a = k * Math.PI / 20;
            w.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 0, Math.cos(a), 0.02, Math.sin(a), 0.35);
        }
        w.spawnParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1.2, p.getZ(), 1, 0, 0, 0, 0);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.8f, 1.5f);
    }

    // ---------------------------------------------------------- III. Небесный свет

    private static void heavenlyLight(ServerWorld w, ServerPlayerEntity p) {
        Vec3d eye = p.getEyePos();
        Vec3d end = eye.add(p.getRotationVec(1f).multiply(25));
        BlockHitResult hit = w.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, p));
        Vec3d at = hit.getType() != HitResult.Type.MISS ? hit.getPos() : end;
        double r = 4.0;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(at, at).expand(r, 4, r), LivingEntity::isAlive)) {
            if (e.squaredDistanceTo(at) > r * r * 2) continue;
            if (StaffOfLightItem.canHit(e)) {
                e.damage(w.getDamageSources().indirectMagic(p, p), 8f);
                if (e.getGroup() == EntityGroup.UNDEAD) e.setOnFireFor(5);
            } else {
                e.heal(8f);
                w.spawnParticles(ParticleTypes.HEART, e.getX(), e.getBodyY(1.0), e.getZ(), 3, 0.3, 0.3, 0.3, 0);
            }
        }
        for (int y = 0; y < 28; y++) {         // столп света с неба
            w.spawnParticles(ParticleTypes.END_ROD, at.x, at.y + y * 0.6, at.z, 3, 0.25, 0.1, 0.25, 0.0);
        }
        for (int k = 0; k < 32; k++) {
            double a = k * Math.PI / 16;
            w.spawnParticles(Vestments.SERVICE_ORB, at.x + Math.cos(a) * r, at.y + 0.1, at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        w.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.4f, 0.8f);
        w.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.6f, 1.6f);
    }

    // ---------------------------------------------------------- 1. Печать чистоты

    private static void seal(ServerWorld w, ServerPlayerEntity p) {
        Vec3d pos = p.getPos();
        SEALS.add(new Seal(w.getRegistryKey(), pos, now(p.getServer()) + SEAL_TICKS));
        w.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.3f);
        w.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0f, 0.8f);
    }

    private static void tickSeal(ServerWorld w, Seal s, long t) {
        double r = SEAL_RADIUS;
        // круг печати и восьмиконечная звезда, медленно вращаются
        if (t % 2 == 0) {
            double spin = t * 0.03;
            for (int k = 0; k < 28; k++) {
                double a = spin + k * Math.PI * 2 / 28;
                w.spawnParticles(Vestments.SERVICE_ORB, s.pos.x + Math.cos(a) * r, s.pos.y + 0.1, s.pos.z + Math.sin(a) * r,
                        1, 0, 0, 0, 0);
            }
            for (int k = 0; k < 8; k++) {
                double a = -spin * 1.5 + k * Math.PI / 4;
                for (double d = 0.6; d < r; d += 0.9) {
                    w.spawnParticles(ParticleTypes.ENCHANT, s.pos.x + Math.cos(a) * d, s.pos.y + 0.15, s.pos.z + Math.sin(a) * d,
                            1, 0, 0, 0, 0);
                }
            }
        }
        if (t % 10 != 0) return;
        Box box = new Box(s.pos, s.pos).expand(r, 3, r);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
            if (e.squaredDistanceTo(s.pos) > r * r) continue;
            if (StaffOfLightItem.canHit(e)) {
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 2, true, true));
            } else {
                List<StatusEffectInstance> bad = new ArrayList<>();
                for (StatusEffectInstance in : e.getStatusEffects()) {
                    if (in.getEffectType().getCategory() == StatusEffectCategory.HARMFUL) bad.add(in);
                }
                for (StatusEffectInstance in : bad) e.removeStatusEffect(in.getEffectType());
                if (!bad.isEmpty()) {
                    w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getBodyY(0.6), e.getZ(), 6, 0.3, 0.4, 0.3, 0);
                }
            }
        }
    }

    // ---------------------------------------------------------- 2. Благословение стойкости

    private static void fortitude(ServerWorld w, ServerPlayerEntity p) {
        Vec3d eye = p.getEyePos();
        Vec3d end = eye.add(p.getRotationVec(1f).multiply(30));
        EntityHitResult hit = ProjectileUtil.raycast(p, eye, end, p.getBoundingBox().stretch(end.subtract(eye)).expand(1),
                e -> e instanceof PlayerEntity && e != p && e.isAlive() && !e.isSpectator(), 30 * 30);
        LivingEntity target = hit != null && hit.getEntity() instanceof PlayerEntity tp ? tp : p;
        SHIELDS.put(target.getUuid(), now(p.getServer()) + SHIELD_TICKS);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, SHIELD_TICKS, 4, true, true, true));
        w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, target.getX(), target.getBodyY(0.6), target.getZ(), 30, 0.4, 0.6, 0.4, 0.25);
        w.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT,
                SoundCategory.PLAYERS, 1.0f, 1.4f);
        if (target != p) {
            target.sendMessage(Text.translatable("message.vestments.fortitude_given", p.getDisplayName()).formatted(Formatting.GOLD));
        }
    }

    // ---------------------------------------------------------- 3. Ослепление

    private static void blinding(ServerWorld w, ServerPlayerEntity p) {
        long end = now(p.getServer()) + FREEZE_TICKS;
        Box box = p.getBoundingBox().expand(BLIND_RADIUS);
        for (MobEntity mob : w.getEntitiesByClass(MobEntity.class, box, m -> m.isAlive() && !(m instanceof EnderDragonEntity))) {
            if (mob.squaredDistanceTo(p) > BLIND_RADIUS * BLIND_RADIUS) continue;
            Frozen old = FROZEN.get(mob.getUuid());
            boolean hadNoAi = old != null ? old.hadNoAi : mob.isAiDisabled();
            FROZEN.put(mob.getUuid(), new Frozen(w.getRegistryKey(), end, hadNoAi));
            mob.setAiDisabled(true);
            mob.addCommandTag(FROZEN_TAG);
            mob.setVelocity(0, Math.min(0, mob.getVelocity().y), 0);
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, FREEZE_TICKS, 0, true, false));
            w.spawnParticles(ParticleTypes.SNOWFLAKE, mob.getX(), mob.getBodyY(0.6), mob.getZ(), 12, 0.4, 0.5, 0.4, 0.02);
        }
        Vec3d c = p.getEyePos();
        w.spawnParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        for (int k = 0; k < 60; k++) {
            double a = k * Math.PI * 2 / 60;
            w.spawnParticles(ParticleTypes.END_ROD, c.x, c.y - 0.5, c.z, 0, Math.cos(a), 0.05, Math.sin(a), 0.6);
        }
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 1.2f, 1.2f);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.6f, 1.6f);
    }

    // ---------------------------------------------------------- такт

    private static void tick(MinecraftServer server) {
        long t = now(server);
        for (Iterator<Seal> it = SEALS.iterator(); it.hasNext(); ) {
            Seal s = it.next();
            ServerWorld w = server.getWorld(s.world);
            if (w == null || t >= s.end) { it.remove(); continue; }
            tickSeal(w, s, t);
        }
        for (Iterator<Map.Entry<UUID, Long>> it = SHIELDS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            if (t >= e.getValue()) { it.remove(); continue; }
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p == null) { it.remove(); continue; }
            if (t % 3 == 0) {       // золотое кольцо-щит вокруг игрока
                double a = t * 0.35;
                for (int k = 0; k < 3; k++) {
                    double b = a + k * Math.PI * 2 / 3;
                    p.getServerWorld().spawnParticles(Vestments.SERVICE_ORB, p.getX() + Math.cos(b) * 0.9,
                            p.getY() + 0.3 + (k * 0.6), p.getZ() + Math.sin(b) * 0.9, 1, 0, 0, 0, 0);
                }
            }
        }
        for (Iterator<Map.Entry<UUID, Frozen>> it = FROZEN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Frozen> e = it.next();
            ServerWorld w = server.getWorld(e.getValue().world);
            Entity ent = w != null ? w.getEntity(e.getKey()) : null;
            if (t >= e.getValue().end || ent == null) {
                if (ent instanceof MobEntity mob) {
                    mob.setAiDisabled(e.getValue().hadNoAi);
                    mob.removeScoreboardTag(FROZEN_TAG);
                }
                it.remove();
                continue;
            }
            if (t % 10 == 0 && ent instanceof MobEntity mob) {
                mob.setVelocity(0, Math.min(0, mob.getVelocity().y), 0);
                w.spawnParticles(ParticleTypes.SNOWFLAKE, mob.getX(), mob.getY() + mob.getHeight() + 0.2, mob.getZ(),
                        2, 0.3, 0.1, 0.3, 0.01);
            }
        }
    }
}
