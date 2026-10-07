package com.bleid.vestments.church;

import com.bleid.vestments.service.ServicePoints;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Поведение жителя-священника из церкви:
 * — изредка бросает взрывное зелье исцеления в раненого игрока-священника (любого сана);
 * — очень редко бросает любому игроку немного еды (1–5 шт., без золотых яблок).
 */
public final class ChurchPriests {
    private static final int CHECK_TICKS = 40;                     // проверка раз в 2 с
    private static final double POTION_RANGE = 8.0;
    private static final float POTION_CHANCE = 0.3f;               // шанс за проверку, если игрок ранен
    private static final int POTION_COOLDOWN_MIN = 60 * 20;        // не чаще раза в 1–2 минуты
    private static final int POTION_COOLDOWN_RAND = 60 * 20;
    private static final double FOOD_RANGE = 6.0;
    private static final float FOOD_CHANCE = 1f / 200f;            // в среднем раз в ~7 минут рядом
    private static final int FOOD_COOLDOWN = 10 * 60 * 20;         // и не чаще раза в 10 минут

    private static final Item[] FOOD = {
            Items.BREAD, Items.BAKED_POTATO, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN,
            Items.COOKED_MUTTON, Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON, Items.APPLE,
            Items.CARROT, Items.COOKIE, Items.PUMPKIN_PIE, Items.MELON_SLICE, Items.SWEET_BERRIES,
            Items.HONEY_BOTTLE, Items.BEETROOT, Items.DRIED_KELP, Items.MUSHROOM_STEW, Items.BEETROOT_SOUP
    };

    private static final Map<UUID, Long> NEXT_POTION = new HashMap<>();
    private static final Map<UUID, Long> NEXT_FOOD = new HashMap<>();

    private ChurchPriests() { }

    public static boolean isChurchPriest(VillagerEntity v) {
        return v.getCommandTags().contains(ChurchBuilder.PRIEST_TAG);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CHECK_TICKS != 0) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!player.isAlive() || player.isSpectator()) continue;
                tickPlayer(server, player);
            }
        });
    }

    private static void tickPlayer(MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        long now = world.getTime();
        Random rnd = world.getRandom();
        List<VillagerEntity> priests = world.getEntitiesByClass(VillagerEntity.class,
                new Box(player.getBlockPos()).expand(POTION_RANGE),
                v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && isChurchPriest(v) && v.canSee(player));
        for (VillagerEntity priest : priests) {
            double dist = priest.distanceTo(player);
            UUID id = priest.getUuid();

            // взрывное зелье исцеления — только священникам, когда здоровье не полное
            if (player.getHealth() < player.getMaxHealth() && ServicePoints.canServe(player)
                    && now >= NEXT_POTION.getOrDefault(id, 0L) && rnd.nextFloat() < POTION_CHANCE) {
                throwHealing(world, priest, player);
                NEXT_POTION.put(id, now + POTION_COOLDOWN_MIN + rnd.nextInt(POTION_COOLDOWN_RAND));
                continue;
            }

            // еда — любому игроку, очень редко
            if (dist <= FOOD_RANGE && now >= NEXT_FOOD.getOrDefault(id, 0L) && rnd.nextFloat() < FOOD_CHANCE) {
                throwFood(world, priest, player, rnd);
                NEXT_FOOD.put(id, now + FOOD_COOLDOWN);
            }
        }
        if (NEXT_POTION.size() > 512) NEXT_POTION.values().removeIf(t -> t < now);
        if (NEXT_FOOD.size() > 512) NEXT_FOOD.values().removeIf(t -> t < now);
    }

    private static void throwHealing(ServerWorld world, VillagerEntity priest, ServerPlayerEntity player) {
        priest.getLookControl().lookAt(player, 30f, 30f);
        priest.swingHand(Hand.MAIN_HAND);
        ItemStack stack = PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.HEALING);
        PotionEntity potion = new PotionEntity(world, priest);
        potion.setItem(stack);
        Vec3d v = player.getVelocity();
        double dx = player.getX() + v.x - priest.getX();
        double dy = player.getEyeY() - 1.1 - priest.getY();
        double dz = player.getZ() + v.z - priest.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        potion.setPitch(potion.getPitch() - 20f);
        potion.setVelocity(dx, dy + flat * 0.2, dz, 0.75f, 4.0f);
        world.spawnEntity(potion);
        world.playSound(null, priest.getX(), priest.getY(), priest.getZ(), SoundEvents.ENTITY_WITCH_THROW,
                SoundCategory.NEUTRAL, 1.0f, 0.8f + world.getRandom().nextFloat() * 0.4f);
    }

    private static void throwFood(ServerWorld world, VillagerEntity priest, ServerPlayerEntity player, Random rnd) {
        Item item = FOOD[rnd.nextInt(FOOD.length)];
        int count = Math.min(1 + rnd.nextInt(5), item.getMaxCount());
        ItemStack stack = new ItemStack(item, count);
        priest.getLookControl().lookAt(player, 30f, 30f);
        priest.swingHand(Hand.MAIN_HAND);
        ItemEntity drop = new ItemEntity(world, priest.getX(), priest.getEyeY() - 0.3, priest.getZ(), stack);
        Vec3d d = player.getPos().subtract(priest.getPos());
        Vec3d flat = new Vec3d(d.x, 0, d.z);
        if (flat.lengthSquared() > 1e-4) flat = flat.normalize();
        double speed = Math.min(0.12 + priest.distanceTo(player) * 0.05, 0.4);
        drop.setVelocity(flat.x * speed, 0.25, flat.z * speed);
        drop.setToDefaultPickupDelay();
        world.spawnEntity(drop);
        world.playSound(null, priest.getX(), priest.getY(), priest.getZ(), SoundEvents.ENTITY_VILLAGER_YES,
                SoundCategory.NEUTRAL, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, priest.getX(), priest.getEyeY() + 0.3, priest.getZ(),
                5, 0.3, 0.2, 0.3, 0.0);
    }
}
