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
        FabricDefaultAttributeRegistry.register(SERAEL, SeraelEntity.createAttributes());
    }
}
