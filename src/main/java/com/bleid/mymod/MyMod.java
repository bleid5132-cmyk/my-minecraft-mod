package com.bleid.mymod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyMod implements ModInitializer {
    public static final String MOD_ID = "mymod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Предмет "Рубин"
    public static final Item RUBY = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "ruby"),
            new Item(new FabricItemSettings())
    );

    @Override
    public void onInitialize() {
        // Показываем рубин во вкладке "Ингредиенты" в креативе
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(RUBY));
        LOGGER.info("My Mod загружен!");
    }
}
