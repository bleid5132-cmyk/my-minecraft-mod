package com.bleid.vestments;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.List;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Посох Света: бьёт враждебных мобов на 3 сердца. По игрокам, мирным и нейтральным мобам удар не проходит.
 * ПКМ — «Благословение»: луч лечит игроков, мирных и нейтральных мобов и жжёт нежить; перезарядка 15 сек.
 */
public class StaffOfLightItem extends Item {
    private static final double ATTACK_DAMAGE = 6.0;     // 3 сердца (вместе с 1 базовым у игрока)
    private static final double ATTACK_SPEED = 1.3;      // ударов в секунду

    // «Благословение» (ПКМ)
    private static final int BLESSING_COOLDOWN = 15 * 20;   // 15 секунд
    private static final double BEAM_RANGE = 20.0;
    private static final double BEAM_RADIUS = 0.6;           // насколько луч «толстый» для попадания
    private static final float BLESSING_HEAL = 6.0f;         // 3 сердца
    private static final float BLESSING_UNDEAD_DAMAGE = 10.0f; // 5 сердец
    private static final int BLESSING_UNDEAD_FIRE = 3;       // секунд горения

    private final Multimap<EntityAttribute, EntityAttributeModifier> modifiers;

    public StaffOfLightItem(Settings settings) {
        super(settings);
        this.modifiers = ImmutableMultimap.<EntityAttribute, EntityAttributeModifier>builder()
                .put(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(ATTACK_DAMAGE_MODIFIER_ID,
                        "Staff of Light damage", ATTACK_DAMAGE - 1.0, EntityAttributeModifier.Operation.ADDITION))
                .put(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(ATTACK_SPEED_MODIFIER_ID,
                        "Staff of Light speed", ATTACK_SPEED - 4.0, EntityAttributeModifier.Operation.ADDITION))
                .build();
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getAttributeModifiers(slot);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        player.getItemCooldownManager().set(this, BLESSING_COOLDOWN);
        player.swingHand(hand);
        if (!world.isClient && world instanceof ServerWorld server) {
            castBlessing(server, player);
            if (!player.getAbilities().creativeMode) {
                stack.damage(2, player, p -> p.sendToolBreakStatus(hand));
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    /** Луч: лечит игроков, мирных и нейтральных мобов, жжёт нежить. Проходит сквозь существ, гаснет о стену. */
    private static void castBlessing(ServerWorld world, PlayerEntity player) {
        Vec3d start = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0f);
        Vec3d end = start.add(dir.multiply(BEAM_RANGE));
        BlockHitResult wall = world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) {
            end = wall.getPos();
        }

        Box area = new Box(start, end).expand(BEAM_RADIUS + 1.0);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area,
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            Box hitbox = target.getBoundingBox().expand(BEAM_RADIUS);
            if (!hitbox.contains(start) && hitbox.raycast(start, end).isEmpty()) continue;

            if (target.getGroup() == EntityGroup.UNDEAD) {
                target.damage(world.getDamageSources().indirectMagic(player, player), BLESSING_UNDEAD_DAMAGE);
                target.setOnFireFor(BLESSING_UNDEAD_FIRE);
                world.spawnParticles(ParticleTypes.FLAME, target.getX(), target.getBodyY(0.5), target.getZ(),
                        12, 0.3, 0.5, 0.3, 0.02);
            } else if (!canHit(target)) {   // игроки, мирные и нейтральные — лечим
                target.heal(BLESSING_HEAL);
                world.spawnParticles(ParticleTypes.HEART, target.getX(), target.getBodyY(0.9), target.getZ(),
                        3, 0.35, 0.25, 0.35, 0.0);
            }
        }

        // видимый луч из золотых искр
        double length = start.distanceTo(end);
        Vec3d from = start.add(dir.multiply(0.6)).add(0, -0.25, 0);
        for (double d = 0; d < length; d += 0.35) {
            Vec3d p = from.add(dir.multiply(d));
            world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            if (((int) (d / 0.35)) % 3 == 0) {
                world.spawnParticles(ParticleTypes.WAX_OFF, p.x, p.y, p.z, 1, 0.08, 0.08, 0.08, 0.0);
            }
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.6f);
        world.playSound(null, end.x, end.y, end.z,
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.2f);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.damage(1, attacker, e -> e.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public int getEnchantability() {
        return 15;
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return ingredient.isOf(net.minecraft.item.Items.GOLD_INGOT) || super.canRepair(stack, ingredient);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.vestments.staff_of_light.peace").formatted(Formatting.YELLOW));
        tooltip.add(Text.translatable("tooltip.vestments.staff_of_light.blessing").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.vestments.staff_of_light.blessing_desc").formatted(Formatting.GRAY));
    }

    /**
     * Кого посох может бить: только враждебных мобов. Игроки, мирные (животные, жители, рыбы,
     * летучие мыши), нейтральные (волки, эндермены, пчёлы, зомби-пиглины...) и големы — нельзя.
     */
    public static boolean canHit(Entity entity) {
        if (!(entity instanceof LivingEntity)) return true;          // кристаллы края и прочие не-существа
        if (entity instanceof PlayerEntity) return false;
        if (entity instanceof Angerable) return false;                // нейтральные
        return entity instanceof Monster;                             // враждебные
    }

    public static void registerEvents() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!player.getStackInHand(hand).isOf(Vestments.STAFF_OF_LIGHT) || player.isSpectator() || canHit(entity)) {
                return ActionResult.PASS;
            }
            // удар по игроку, мирному или нейтральному мобу не проходит
            if (world.isClient) {
                player.sendMessage(Text.translatable("message.vestments.staff_refuses").formatted(Formatting.YELLOW), true);
            }
            return ActionResult.FAIL;
        });
    }
}
