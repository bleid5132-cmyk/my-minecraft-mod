package com.bleid.vestments.patriarch;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.mixin.MobEntityAccessor;
import com.bleid.vestments.service.ServicePoints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Ульта «Сонм душ»: призывает союзные души одолённых священником врагов.
 * Сколько: мораль / 10 (до 10 одновременно). Живут 50 секунд, выглядят серо-голубой голограммой.
 * Души никого не трогают, пока священник сам не ударит кого-то или пока кто-то не ударит
 * священника — тогда они нападают именно на этого противника.
 * Здесь же: мораль (враждебные +, мирные и нейтральные −) и учёт побеждённых видов врагов.
 */
public final class SoulAllies {
    public static final Identifier OPEN_SELECT = new Identifier(Vestments.MOD_ID, "souls_open");
    public static final Identifier CHOOSE = new Identifier(Vestments.MOD_ID, "souls_choose");
    public static final Identifier SOUL_IDS = new Identifier(Vestments.MOD_ID, "souls_ids");
    public static final String TAG = "vestments_soul_ally";
    public static final int LIFE_TICKS = 50 * 20;
    public static final int MAX_SOULS = 10;

    private record Soul(UUID entity, UUID owner, RegistryKey<World> world, long expire) { }
    private record Enemy(UUID entity, long until) { }

    private static final List<Soul> SOULS = new ArrayList<>();
    private static final Map<UUID, Enemy> ENEMY = new HashMap<>();
    /** Обидчик конкретной души: она отвечает ему, даже если священник ни с кем не дерётся. */
    private static final Map<UUID, Enemy> SOUL_ENEMY = new HashMap<>();
    private static final double AGGRO_RANGE = 16;
    private static boolean dirty;

    private SoulAllies() { }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CHOOSE, (server, player, handler, buf, sender) -> {
            int n = Math.min(buf.readVarInt(), 32);
            List<String> list = new ArrayList<>();
            for (int i = 0; i < n; i++) list.add(buf.readString(128));
            server.execute(() -> {
                var killed = Mana.killed(server, player.getUuid());
                List<String> ok = new ArrayList<>();
                for (String t : list) if (killed.contains(t) && !ok.contains(t) && ok.size() < Mana.MAX_CHOSEN) ok.add(t);
                if (ok.isEmpty()) return;
                Mana.choose(server, player.getUuid(), ok);
                player.sendMessage(Text.translatable("message.vestments.souls_chosen").formatted(Formatting.AQUA), true);
                Mana.sync(player);
            });
        });

        // мораль и побеждённые враги
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getAttacker() instanceof ServerPlayerEntity p) || !ServicePoints.canServe(p)) return;
            MinecraftServer server = p.getServer();
            if (entity instanceof Monster) {
                Mana.addMorale(server, p.getUuid(), entity.getMaxHealth() >= 100 ? 10 : 1);
                Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
                Mana.recordKill(server, p.getUuid(), id.toString());
            } else if (entity instanceof VillagerEntity) {
                Mana.addMorale(server, p.getUuid(), -10);
            } else if (entity instanceof MobEntity) {          // мирные и нейтральные
                Mana.addMorale(server, p.getUuid(), entity instanceof Angerable ? -3 : -3);
            }
        });

        // противник священника: тот, кого он ударил, или тот, кто ударил его
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient) return ActionResult.PASS;
            if (isSoulOf(entity, player.getUuid())) return ActionResult.FAIL;      // своих душ не бьём
            if (entity instanceof LivingEntity) ENEMY.put(player.getUuid(), new Enemy(entity.getUuid(), now(world) + 30 * 20));
            return ActionResult.PASS;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> {
            Entity attacker = source.getAttacker();
            // души не ранят своего священника и друг друга
            if (attacker != null && attacker.getCommandTags().contains(TAG)) {
                UUID owner = ownerOf(attacker.getUuid());
                if (owner != null && (target.getUuid().equals(owner) || isSoulOf(target, owner))) return false;
            }
            // душу ударили — она отвечает обидчику
            if (target.getCommandTags().contains(TAG) && attacker instanceof LivingEntity
                    && !attacker.getCommandTags().contains(TAG)) {
                UUID owner = ownerOf(target.getUuid());
                if (owner != null && !attacker.getUuid().equals(owner)) {
                    SOUL_ENEMY.put(target.getUuid(), new Enemy(attacker.getUuid(), now(target.getWorld()) + 30 * 20));
                }
            }
            if (target instanceof ServerPlayerEntity p && attacker instanceof LivingEntity && attacker != p
                    && !isSoulOf(attacker, p.getUuid())) {
                ENEMY.put(p.getUuid(), new Enemy(attacker.getUuid(), now(p.getWorld()) + 30 * 20));
            }
            return true;
        });
        // души не умирают по-настоящему: растворяются без добычи и опыта
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!entity.getCommandTags().contains(TAG)) return true;
            poof((ServerWorld) entity.getWorld(), entity);
            entity.discard();
            return false;
        });
        // после перезапуска не оставляем «потерянных» душ
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getCommandTags().contains(TAG) && ownerOf(entity.getUuid()) == null) entity.discard();
        });
        ServerTickEvents.END_SERVER_TICK.register(SoulAllies::tick);
    }

    private static long now(World w) {
        return w.getServer() != null ? w.getServer().getTicks() : 0;
    }

    private static UUID ownerOf(UUID soul) {
        for (Soul s : SOULS) if (s.entity.equals(soul)) return s.owner;
        return null;
    }

    private static boolean isSoulOf(Entity e, UUID owner) {
        if (e == null || !e.getCommandTags().contains(TAG)) return false;
        return owner.equals(ownerOf(e.getUuid()));
    }

    // ---------------------------------------------------------- выбор душ

    public static void openSelection(ServerPlayerEntity p) {
        var killed = Mana.killed(p.getServer(), p.getUuid());
        if (killed.isEmpty()) {
            p.sendMessage(Text.translatable("message.vestments.souls_none_killed").formatted(Formatting.GRAY), true);
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(killed.size());
        for (String t : killed) buf.writeString(t);
        List<String> chosen = Mana.chosen(p.getServer(), p.getUuid());
        buf.writeVarInt(chosen.size());
        for (String t : chosen) buf.writeString(t);
        ServerPlayNetworking.send(p, OPEN_SELECT, buf);
    }

    static boolean canSummon(ServerPlayerEntity p) {
        if (Mana.chosen(p.getServer(), p.getUuid()).isEmpty()) {
            openSelection(p);
            return false;
        }
        if (Mana.morale(p.getServer(), p.getUuid()) < 10) {
            p.sendMessage(Text.translatable("message.vestments.souls_low_morale").formatted(Formatting.RED), true);
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------- призыв

    static void summon(ServerWorld w, ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        int count = Math.min(MAX_SOULS, Mana.morale(server, p.getUuid()) / 10);
        List<String> types = Mana.chosen(server, p.getUuid());
        var rnd = w.getRandom();
        for (int i = 0; i < count; i++) {
            Identifier id = Identifier.tryParse(types.get(rnd.nextInt(types.size())));
            if (id == null) continue;
            EntityType<?> type = Registries.ENTITY_TYPE.get(id);
            Entity e = type.create(w);
            if (!(e instanceof MobEntity mob)) { if (e != null) e.discard(); continue; }
            double a = i * Math.PI * 2 / Math.max(1, count) + rnd.nextDouble() * 0.4;
            double r = 2.0 + rnd.nextDouble();
            Vec3d pos = p.getPos().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            BlockPos bp = BlockPos.ofFloored(pos);
            if (!w.isSpaceEmpty(mob.getType().createSimpleBoundingBox(pos.x, pos.y, pos.z))) pos = p.getPos();
            mob.refreshPositionAndAngles(pos.x, pos.y, pos.z, rnd.nextFloat() * 360f, 0);
            mob.initialize(w, w.getLocalDifficulty(bp), SpawnReason.MOB_SUMMONED, null, null);
            ((MobEntityAccessor) mob).vestments$targetSelector().clear(g -> true);   // сами ни на кого не нападают
            mob.setCanPickUpLoot(false);
            mob.addCommandTag(TAG);
            mob.setPersistent();
            // сначала регистрируем душу, иначе ENTITY_LOAD при спавне сочтёт её «потерянной» и уберёт
            Soul soul = new Soul(mob.getUuid(), p.getUuid(), w.getRegistryKey(), now(w) + LIFE_TICKS);
            SOULS.add(soul);
            if (!w.spawnEntity(mob)) { SOULS.remove(soul); continue; }
            w.spawnParticles(ParticleTypes.SOUL, mob.getX(), mob.getBodyY(0.5), mob.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
        }
        // не больше 10 душ у одного священника — лишние (старые) растворяются
        List<Soul> mine = new ArrayList<>();
        for (Soul s : SOULS) if (s.owner.equals(p.getUuid())) mine.add(s);
        for (int i = 0; i < mine.size() - MAX_SOULS; i++) {
            Soul s = mine.get(i);
            Entity e = w.getServer().getWorld(s.world) != null ? w.getServer().getWorld(s.world).getEntity(s.entity) : null;
            if (e != null) { poof((ServerWorld) e.getWorld(), e); e.discard(); }
            SOULS.remove(s);
        }
        dirty = true;
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 2.0f, 0.7f);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, SoundCategory.PLAYERS, 1.0f, 0.6f);
        p.sendMessage(Text.translatable("message.vestments.souls_summoned", count).formatted(Formatting.AQUA), true);
    }

    private static void poof(ServerWorld w, Entity e) {
        w.spawnParticles(ParticleTypes.SOUL, e.getX(), e.getBodyY(0.5), e.getZ(), 10, 0.3, 0.5, 0.3, 0.03);
        w.spawnParticles(ParticleTypes.CLOUD, e.getX(), e.getBodyY(0.5), e.getZ(), 6, 0.3, 0.4, 0.3, 0.01);
    }

    // ---------------------------------------------------------- поведение душ

    private static void tick(MinecraftServer server) {
        long t = server.getTicks();
        for (Iterator<Soul> it = SOULS.iterator(); it.hasNext(); ) {
            Soul s = it.next();
            ServerWorld w = server.getWorld(s.world);
            Entity e = w != null ? w.getEntity(s.entity) : null;
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(s.owner);
            if (e == null || !e.isAlive()) { it.remove(); SOUL_ENEMY.remove(s.entity); dirty = true; continue; }
            if (t >= s.expire || owner == null) {
                poof(w, e); e.discard(); it.remove(); SOUL_ENEMY.remove(s.entity); dirty = true; continue;
            }
            MobEntity mob = (MobEntity) e;
            mob.extinguish();                                   // души не горят на солнце
            if (t % 10 == 0) provokeHostiles(w, mob);
            if (t % 5 != 0) continue;
            LivingEntity target = enemyOf(server, owner);
            if (target == null) target = soulEnemy(w, mob, t);
            if (target != null && target.getWorld() == mob.getWorld() && target.squaredDistanceTo(mob) < 40 * 40) {
                if (mob.getTarget() != target) mob.setTarget(target);
            } else {
                if (mob.getTarget() != null) mob.setTarget(null);
                double d = mob.squaredDistanceTo(owner);
                if (owner.getWorld() != mob.getWorld() || d > 28 * 28) {
                    mob.refreshPositionAndAngles(owner.getX(), owner.getY(), owner.getZ(), mob.getYaw(), 0);
                } else if (d > 5 * 5 && t % 20 == 0) {
                    mob.getNavigation().startMovingTo(owner, 1.15);
                }
            }
            if (t % 20 == 0) {
                w.spawnParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + mob.getHeight() * 0.8, mob.getZ(), 1, 0.2, 0.2, 0.2, 0.01);
            }
        }
        if (dirty || t % 40 == 0) {
            dirty = false;
            syncIds(server);
        }
    }

    /** Враждебные мобы рядом, у которых нет цели, нападают на душу — как на игрока. */
    private static void provokeHostiles(ServerWorld w, MobEntity soul) {
        for (MobEntity m : w.getEntitiesByClass(MobEntity.class, soul.getBoundingBox().expand(AGGRO_RANGE),
                m -> m instanceof Monster && !(m instanceof Angerable) && m.isAlive() && !m.getCommandTags().contains(TAG) && !m.isAiDisabled())) {
            LivingEntity cur = m.getTarget();
            if (cur != null && cur.isAlive()) continue;
            if (!m.canSee(soul)) continue;
            m.setTarget(soul);
        }
    }

    private static LivingEntity soulEnemy(ServerWorld w, MobEntity soul, long t) {
        Enemy en = SOUL_ENEMY.get(soul.getUuid());
        if (en == null) return null;
        Entity e = w.getEntity(en.entity);
        if (t > en.until || !(e instanceof LivingEntity le) || !le.isAlive() || le.squaredDistanceTo(soul) > 40 * 40) {
            SOUL_ENEMY.remove(soul.getUuid());
            return null;
        }
        return le;
    }

    private static LivingEntity enemyOf(MinecraftServer server, ServerPlayerEntity owner) {
        Enemy en = ENEMY.get(owner.getUuid());
        if (en == null || server.getTicks() > en.until) return null;
        Entity e = owner.getServerWorld().getEntity(en.entity);
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le == owner || isSoulOf(le, owner.getUuid())) return null;
        if (le instanceof PlayerEntity pl && (pl.isCreative() || pl.isSpectator())) return null;
        return le;
    }

    /** Список id сущностей-душ для клиентов: они рисуют их серо-голубой голограммой. */
    private static void syncIds(MinecraftServer server) {
        for (ServerWorld w : server.getWorlds()) {
            List<Integer> ids = new ArrayList<>();
            for (Soul s : SOULS) {
                if (!s.world.equals(w.getRegistryKey())) continue;
                Entity e = w.getEntity(s.entity);
                if (e != null) ids.add(e.getId());
            }
            for (ServerPlayerEntity p : w.getPlayers()) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(ids.size());
                for (int id : ids) buf.writeVarInt(id);
                ServerPlayNetworking.send(p, SOUL_IDS, buf);
            }
        }
    }
}
