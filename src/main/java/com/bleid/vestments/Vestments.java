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
            new StaffOfLightItem(new FabricItemSettings().maxDamage(500).rarity(Rarity.RARE)));

    private static Item register(String name, ArmorItem.Type type, String abilityKey) {
        return Registry.register(
                Registries.ITEM,
                new Identifier(MOD_ID, name),
                new VestmentArmorItem(VestmentMaterial.INSTANCE, type,
                        new FabricItemSettings().rarity(Rarity.UNCOMMON), abilityKey));
    }

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(COLLAR);
            entries.add(PHELONION);
            entries.add(PODRIZNIK);
            entries.add(BOOTS);
            entries.add(STAFF_OF_LIGHT);
        });
        SetBonus.register();
        StaffOfLightItem.registerEvents();
        com.bleid.vestments.classes.PlayerClasses.register();
        LOGGER.info("Priest Vestments загружен");
    }
}
