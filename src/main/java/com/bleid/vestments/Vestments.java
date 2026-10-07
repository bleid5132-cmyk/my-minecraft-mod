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
    public static final Item COLLAR = register("collar", ArmorItem.Type.HELMET);
    /** Фелонь с наперсным крестом и поручами (слот груди). */
    public static final Item PHELONION = register("phelonion", ArmorItem.Type.CHESTPLATE);
    /** Подризник с епитрахилью, поясом и палицей (слот ног). */
    public static final Item PODRIZNIK = register("podriznik", ArmorItem.Type.LEGGINGS);
    /** Ботинки с нижним ярусом подола (слот обуви). */
    public static final Item BOOTS = register("boots", ArmorItem.Type.BOOTS);

    private static Item register(String name, ArmorItem.Type type) {
        return Registry.register(
                Registries.ITEM,
                new Identifier(MOD_ID, name),
                new ArmorItem(VestmentMaterial.INSTANCE, type, new FabricItemSettings().rarity(Rarity.UNCOMMON)));
    }

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(COLLAR);
            entries.add(PHELONION);
            entries.add(PODRIZNIK);
            entries.add(BOOTS);
        });
        LOGGER.info("Priest Vestments загружен");
    }
}
