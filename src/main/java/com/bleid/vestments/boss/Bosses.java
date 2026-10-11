package com.bleid.vestments.boss;

import com.bleid.vestments.Vestments;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

/** Боссы мода. */
public final class Bosses {
    private Bosses() { }

    public static final EntityType<SeraelEntity> SERAEL = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(Vestments.MOD_ID, "serael"),
            FabricEntityTypeBuilder.<SeraelEntity>create(SpawnGroup.MONSTER, SeraelEntity::new)
                    .dimensions(EntityDimensions.fixed(1.8f, 5.8f)).fireImmune().trackRangeBlocks(96).trackedUpdateRate(2).build());

    public static final Item SERAEL_SPAWN_EGG = Registry.register(Registries.ITEM, new Identifier(Vestments.MOD_ID, "serael_spawn_egg"),
            new SpawnEggItem(SERAEL, 0x1A0E12, 0xB0142A, new FabricItemSettings().rarity(Rarity.EPIC)));

    /** Нимб Сераэль — единственный трофей: падает рядом со статуей, когда она рассыпается в прах. */
    public static final Item SERAEL_HALO = Registry.register(Registries.ITEM, new Identifier(Vestments.MOD_ID, "serael_halo"),
            new Item(new FabricItemSettings().maxCount(1).rarity(Rarity.EPIC).fireproof()) {
                @Override
                public boolean hasGlint(net.minecraft.item.ItemStack stack) {
                    return true;
                }
            });

    public static final EntityType<BellRingerEntity> BELL_RINGER = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(Vestments.MOD_ID, "bell_ringer"),
            FabricEntityTypeBuilder.<BellRingerEntity>create(SpawnGroup.MONSTER, BellRingerEntity::new)
                    .dimensions(EntityDimensions.fixed(3.0f, 7.4f)).fireImmune().trackRangeBlocks(96).trackedUpdateRate(2).build());

    public static final EntityType<SilentBellEntity> SILENT_BELL = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(Vestments.MOD_ID, "silent_bell"),
            FabricEntityTypeBuilder.<SilentBellEntity>create(SpawnGroup.MONSTER, SilentBellEntity::new)
                    .dimensions(EntityDimensions.fixed(3.6f, 7.4f)).fireImmune().trackRangeBlocks(96).trackedUpdateRate(2).build());

    public static final Item BELL_RINGER_SPAWN_EGG = Registry.register(Registries.ITEM, new Identifier(Vestments.MOD_ID, "bell_ringer_spawn_egg"),
            new SpawnEggItem(BELL_RINGER, 0x4A4540, 0xB07A35, new FabricItemSettings().rarity(Rarity.EPIC)));

    /** Язык Колокола — бронзовый молот паладина из сердца Безмолвного Колокола: удар оглушает звоном. */
    public static final com.bleid.vestments.paladin.PaladinSwordItem BELL_TONGUE = Registry.register(Registries.ITEM,
            new Identifier(Vestments.MOD_ID, "bell_tongue"), bellTongue());

    private static com.bleid.vestments.paladin.PaladinSwordItem bellTongue() {
        var item = new com.bleid.vestments.paladin.PaladinSwordItem(new FabricItemSettings().rarity(Rarity.EPIC).fireproof(), 2, 13.0, 0.85,
                "heaven_crush", 18 * 20, 2000, net.minecraft.item.Items.COPPER_INGOT) {
            @Override
            public void appendTooltip(net.minecraft.item.ItemStack stack, net.minecraft.world.World world,
                                      java.util.List<net.minecraft.text.Text> tooltip, net.minecraft.client.item.TooltipContext ctx) {
                super.appendTooltip(stack, world, tooltip, ctx);
                tooltip.add(net.minecraft.text.Text.translatable("tooltip.vestments.bell_tongue").formatted(net.minecraft.util.Formatting.DARK_GREEN));
            }
        };
        item.twoHanded = true;
        return item;
    }

    public static void init() {
        BossEffects.init();
        // вместо смерти Сераэль каменеет: статуя рассыпается только от удара игрока
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof SeraelEntity s && !s.statue()) {
                s.petrify();
                return false;
            }
            return true;
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof BellRingerEntity r) {
                r.startDying();
                return false;
            }
            if (entity instanceof SilentBellEntity b) {
                b.startDying();
                return false;
            }
            return true;
        });
        // Язык Колокола: каждый удар — звон, оглушение и замедление
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (source.getAttacker() instanceof net.minecraft.entity.LivingEntity a && source.getSource() == a && entity != a
                    && a.getMainHandStack().isOf(BELL_TONGUE)) {
                entity.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(BossEffects.STUN, 24, 0), a);
                entity.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.SLOWNESS, 50, 1), a);
                entity.getWorld().playSound(null, entity.getX(), entity.getY() + 1, entity.getZ(), net.minecraft.sound.SoundEvents.BLOCK_BELL_USE,
                        net.minecraft.sound.SoundCategory.PLAYERS, 1.3f, 1.0f + entity.getRandom().nextFloat() * 0.3f);
                if (entity.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                    sw.spawnParticles(net.minecraft.particle.ParticleTypes.NOTE, entity.getX(), entity.getY() + entity.getHeight() + 0.3,
                            entity.getZ(), 3, 0.4, 0.2, 0.4, 1.0);
                }
            }
            return true;
        });
        com.bleid.vestments.paladin.combat.Movesets.BY_SWORD.putIfAbsent("bell_tongue",
                com.bleid.vestments.paladin.combat.Movesets.of("greathammer"));
        FabricDefaultAttributeRegistry.register(SERAEL, SeraelEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BELL_RINGER, BellRingerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SILENT_BELL, SilentBellEntity.createAttributes());
    }
}
