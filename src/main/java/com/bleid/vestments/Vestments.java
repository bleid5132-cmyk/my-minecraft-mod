package com.bleid.vestments;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Vestments implements ModInitializer {
    public static final String MOD_ID = "vestments";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Воротник (слот головы). */
    public static final Item COLLAR = register("collar", ArmorItem.Type.HELMET, "tooltip.vestments.collar");
    /** Фелонь с наперсным крестом и поручами (слот груди). */
    public static final Item PHELONION = register("phelonion", ArmorItem.Type.CHESTPLATE, "tooltip.vestments.phelonion");
    /** Подризник с епитрахилью, поясом и палицей (слот ног). */
    public static final Item PODRIZNIK = register("podriznik", ArmorItem.Type.LEGGINGS, "tooltip.vestments.podriznik");
    /** Ботинки с нижним ярусом подола (слот обуви). */
    public static final Item BOOTS = register("boots", ArmorItem.Type.BOOTS, "tooltip.vestments.boots");

    /** Посох Света — основное оружие. */
    public static final Item STAFF_OF_LIGHT = Registry.register(Registries.ITEM, new Identifier(MOD_ID, "staff_of_light"),
            new StaffOfLightItem(new FabricItemSettings().maxDamage(500).rarity(Rarity.EPIC)));

    /** Священный алтарь — подношения за очки служения. */
    public static final net.minecraft.block.Block HOLY_ALTAR = Registry.register(Registries.BLOCK,
            new Identifier(MOD_ID, "holy_altar"),
            new com.bleid.vestments.altar.HolyAltarBlock(net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings.create()
                    .mapColor(net.minecraft.block.MapColor.GOLD)
                    .strength(3.5f, 1200f)
                    .requiresTool()
                    .nonOpaque()
                    .luminance(state -> 10)
                    .sounds(net.minecraft.sound.BlockSoundGroup.STONE)));
    public static final Item HOLY_ALTAR_ITEM = Registry.register(Registries.ITEM, new Identifier(MOD_ID, "holy_altar"),
            new net.minecraft.item.BlockItem(HOLY_ALTAR, new FabricItemSettings().rarity(Rarity.UNCOMMON)));

    /** Книга Патриарха — три способности; продаёт мастер-священник (шанс 50%). */
    public static final Item PATRIARCH_BOOK = Registry.register(Registries.ITEM, new Identifier(MOD_ID, "patriarch_book"),
            new com.bleid.vestments.patriarch.PatriarchBookItem(new FabricItemSettings().maxCount(1).rarity(Rarity.EPIC)));

    /** Огонёк очков служения (частица). */
    public static final net.minecraft.particle.DefaultParticleType SERVICE_ORB = Registry.register(
            Registries.PARTICLE_TYPE, new Identifier(MOD_ID, "service_orb"),
            net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));

    private static Item register(String name, ArmorItem.Type type, String abilityKey) {
        return Registry.register(
                Registries.ITEM,
                new Identifier(MOD_ID, name),
                new VestmentArmorItem(VestmentMaterial.INSTANCE, type,
                        new FabricItemSettings().rarity(Rarity.EPIC)));
    }

    @Override
    public void onInitialize() {
        com.bleid.vestments.gear.RankGear.init();
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(COLLAR);
            entries.add(PHELONION);
            entries.add(PODRIZNIK);
            entries.add(BOOTS);
            entries.add(STAFF_OF_LIGHT);
            for (var set : com.bleid.vestments.gear.RankGear.SETS) {
                if (set == com.bleid.vestments.gear.RankGear.PATRIARCH) continue;
                set.items().forEach(entries::add);
            }
            com.bleid.vestments.gear.RankGear.WEAPONS.forEach(entries::add);
            entries.add(PATRIARCH_BOOK);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(HOLY_ALTAR_ITEM));
        SetBonus.register();
        StaffOfLightItem.registerEvents();
        com.bleid.vestments.classes.PlayerClasses.register();
        com.bleid.vestments.service.ServicePoints.register();
        com.bleid.vestments.church.ChurchSpawner.register();
        com.bleid.vestments.altar.HolyAltarBlock.registerNetworking();
        com.bleid.vestments.church.ChurchPriests.register();
        com.bleid.vestments.church.VanillaTempleRemover.register();
        com.bleid.vestments.weapon.IncenseClouds.register();
        com.bleid.vestments.patriarch.Mana.register();
        com.bleid.vestments.patriarch.PatriarchAbilities.register();
        // последняя сделка мастера-священника: с шансом 50% — Книга Патриарха
        net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper.registerVillagerOffers(
                net.minecraft.village.VillagerProfession.CLERIC, 5, factories -> factories.add((entity, random) ->
                        random.nextFloat() < 0.5f
                                ? new net.minecraft.village.TradeOffer(new net.minecraft.item.ItemStack(net.minecraft.item.Items.EMERALD, 48),
                                        new net.minecraft.item.ItemStack(net.minecraft.item.Items.BOOK),
                                        new net.minecraft.item.ItemStack(PATRIARCH_BOOK), 1, 30, 0.05f)
                                : null));
        LOGGER.info("Priest Vestments загружен");
    }
}
