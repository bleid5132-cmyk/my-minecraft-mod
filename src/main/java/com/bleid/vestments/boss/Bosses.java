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

/** Боссы мода: Багровый Серафим, его перья-снаряды, яйцо призыва. */
public final class Bosses {
    private Bosses() { }

    public static final EntityType<CrimsonSeraphEntity> CRIMSON_SERAPH = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(Vestments.MOD_ID, "crimson_seraph"),
            FabricEntityTypeBuilder.<CrimsonSeraphEntity>create(SpawnGroup.MONSTER, CrimsonSeraphEntity::new)
                    .dimensions(EntityDimensions.fixed(2.6f, 9.0f)).fireImmune().trackRangeBlocks(128).trackedUpdateRate(2).build());

    public static final EntityType<CrimsonFeatherEntity> CRIMSON_FEATHER_PROJECTILE = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(Vestments.MOD_ID, "crimson_feather_projectile"),
            FabricEntityTypeBuilder.<CrimsonFeatherEntity>create(SpawnGroup.MISC, CrimsonFeatherEntity::new)
                    .dimensions(EntityDimensions.fixed(0.4f, 0.4f)).trackRangeBlocks(64).trackedUpdateRate(10).build());

    /** Багровое перо — снаряд серафима и трофей с него. */
    public static final Item CRIMSON_FEATHER = Registry.register(Registries.ITEM, new Identifier(Vestments.MOD_ID, "crimson_feather"),
            new Item(new FabricItemSettings().rarity(Rarity.RARE)));

    public static final Item CRIMSON_SERAPH_SPAWN_EGG = Registry.register(Registries.ITEM,
            new Identifier(Vestments.MOD_ID, "crimson_seraph_spawn_egg"),
            new SpawnEggItem(CRIMSON_SERAPH, 0x8E2121, 0xD2B2A1, new FabricItemSettings().rarity(Rarity.EPIC)));

    public static void init() {
        FabricDefaultAttributeRegistry.register(CRIMSON_SERAPH, CrimsonSeraphEntity.createAttributes());
    }
}
