package com.bleid.vestments;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.item.TooltipData;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
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
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Посох Света: бьёт враждебных мобов на 3 сердца. По игрокам, мирным и нейтральным мобам удар не проходит.
 * Зажатая ПКМ — «Благословение»: золотой луч как у маяка до 5 сек. — лечит игроков, мирных и нейтральных
 * мобов и жжёт нежить; перезарядка 15 сек. Сам луч рисует клиент (client/StaffBeamRenderer).
 */
public class StaffOfLightItem extends Item {
    private static final double ATTACK_DAMAGE = 6.0;     // 3 сердца (вместе с 1 базовым у игрока)
    private static final double ATTACK_SPEED = 1.3;      // ударов в секунду

    // «Благословение» (зажатая ПКМ): поддерживаемый луч
    public static final int BLESSING_MAX_TICKS = 5 * 20;      // луч держится до 5 секунд
    public static final int BLESSING_COOLDOWN_TICKS = 15 * 20;   // перезарядка 15 секунд
    private static final int BLESSING_COOLDOWN = BLESSING_COOLDOWN_TICKS;
    public static final double BEAM_RANGE = 20.0;
    private static final double BEAM_RADIUS = 0.6;             // насколько луч «толстый» для попадания
    private static final int PULSE_TICKS = 10;                 // действие луча раз в полсекунды
    private static final float PULSE_HEAL = 1.5f;              // 0.75 сердца за импульс
    private static final float PULSE_UNDEAD_DAMAGE = 3.0f;     // 1.5 сердца за импульс
    private static final int UNDEAD_FIRE_SECONDS = 2;

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
        player.setCurrentHand(hand);   // начинаем держать луч
        if (!world.isClient) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.9f, 1.5f);
        }
        return TypedActionResult.consume(stack);
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return BLESSING_MAX_TICKS;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world.isClient || !(world instanceof ServerWorld server) || !(user instanceof PlayerEntity player)) return;
        int used = BLESSING_MAX_TICKS - remainingUseTicks;
        if (used % PULSE_TICKS == 0) {
            pulseBlessing(server, player);
        }
        if (used % 20 == 0) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.8f, 1.6f);
            if (!player.getAbilities().creativeMode) {
                stack.damage(1, player, p -> p.sendToolBreakStatus(p.getActiveHand()));
            }
        }
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        endBlessing(world, user);
        return stack;
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        endBlessing(world, user);
    }

    private void endBlessing(World world, LivingEntity user) {
        if (user instanceof PlayerEntity player) {
            player.getItemCooldownManager().set(this, BLESSING_COOLDOWN);
        }
        if (!world.isClient) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.9f, 1.5f);
        }
    }

    /** Конец луча: туда, куда смотрит игрок, до 20 блоков или до стены. */
    public static Vec3d beamEnd(World world, PlayerEntity player, Vec3d eye, Vec3d dir) {
        Vec3d end = eye.add(dir.multiply(BEAM_RANGE));
        BlockHitResult wall = world.raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        return wall.getType() != HitResult.Type.MISS ? wall.getPos() : end;
    }

    /** Урон луча по враждебным мобам (не нежити) за импульс — 1 сердце каждые полсекунды. */
    private static final float PULSE_HOSTILE_DAMAGE = 2.0f;

    /** Один импульс луча: лечит игроков, мирных и нейтральных мобов, ранит враждебных, жжёт нежить. */
    private static void pulseBlessing(ServerWorld world, PlayerEntity player) {
        Vec3d start = player.getEyePos();
        Vec3d end = beamEnd(world, player, start, player.getRotationVec(1.0f));

        Box area = new Box(start, end).expand(BEAM_RADIUS + 1.0);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area,
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            Box hitbox = target.getBoundingBox().expand(BEAM_RADIUS);
            if (!hitbox.contains(start) && hitbox.raycast(start, end).isEmpty()) continue;

            if (target.getGroup() == EntityGroup.UNDEAD) {
                target.damage(world.getDamageSources().indirectMagic(player, player), PULSE_UNDEAD_DAMAGE);
                target.setOnFireFor(UNDEAD_FIRE_SECONDS);
                world.spawnParticles(ParticleTypes.FLAME, target.getX(), target.getBodyY(0.5), target.getZ(),
                        6, 0.3, 0.5, 0.3, 0.02);
            } else if (canHit(target)) {     // остальные враждебные мобы — урон светом
                target.damage(world.getDamageSources().indirectMagic(player, player), PULSE_HOSTILE_DAMAGE);
                world.spawnParticles(ParticleTypes.END_ROD, target.getX(), target.getBodyY(0.5), target.getZ(),
                        5, 0.3, 0.4, 0.3, 0.02);
            } else {                          // игроки, мирные и нейтральные — лечим   // игроки, мирные и нейтральные — лечим
                target.heal(PULSE_HEAL);
                world.spawnParticles(ParticleTypes.HEART, target.getX(), target.getBodyY(0.9), target.getZ(),
                        1, 0.3, 0.2, 0.3, 0.0);
            }
        }
        // искры в точке, куда упирается луч
        world.spawnParticles(ParticleTypes.WAX_OFF, end.x, end.y, end.z, 4, 0.15, 0.15, 0.15, 0.0);
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
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        return Optional.of(new SmallTooltipData(List.of(
                Text.translatable("tooltip.vestments.staff_of_light.peace").formatted(Formatting.YELLOW),
                Text.translatable("tooltip.vestments.staff_of_light.blessing").formatted(Formatting.GOLD),
                Text.translatable("tooltip.vestments.staff_of_light.blessing_desc").formatted(Formatting.GRAY))));
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
