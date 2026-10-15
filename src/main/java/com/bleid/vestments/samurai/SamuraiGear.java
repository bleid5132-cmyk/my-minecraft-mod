package com.bleid.vestments.samurai;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.paladin.PaladinMaterial;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

/** Катаны и доспехи самурая. */
public final class SamuraiGear {
    public static final List<KatanaItem> KATANAS = new ArrayList<>();
    /** Комплект → 4 части (шлем, нагрудник, поножи, обувь). */
    public static final Map<String, Item[]> SETS = new LinkedHashMap<>();
    public static final String[] SET_IDS = { "straw", "domaru", "bamboo", "kozane", "akadou", "kinkou", "shinobi", "ooyoroi", "ryujin", "shogun" };

    private SamuraiGear() { }

    public static void init() {
        SamuraiMoves.buildMovesets();
        Item[] repair = { Items.BAMBOO, Items.FLINT, Items.COPPER_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT, Items.AMETHYST_SHARD,
                Items.DIAMOND, Items.ECHO_SHARD, Items.NETHERITE_INGOT, Items.NETHER_STAR };
        Rarity[] rar = { Rarity.COMMON, Rarity.COMMON, Rarity.COMMON, Rarity.COMMON, Rarity.UNCOMMON, Rarity.UNCOMMON, Rarity.RARE,
                Rarity.RARE, Rarity.EPIC, Rarity.EPIC };
        int i = 0;
        for (SamuraiMoves.KatanaSpec s : SamuraiMoves.KATANAS.values()) {
            FabricItemSettings st = new FabricItemSettings().rarity(rar[i]);
            if (s.rank >= 9) st.fireproof();
            KATANAS.add(Registry.register(Registries.ITEM, new Identifier(Vestments.MOD_ID, s.id), new KatanaItem(st, s, repair[i])));
            i++;
        }
        //          id         защита (шлем, грудь, ноги, обувь)  крепк. отбр.  проч. ремонт                звук                                      редкость
        armor("straw", new int[] { 1, 2, 1, 1 }, 0f, 0f, 5, Items.WHEAT, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, Rarity.COMMON, false);
        armor("domaru", new int[] { 1, 3, 2, 1 }, 0f, 0f, 7, Items.LEATHER, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, Rarity.COMMON, false);
        armor("bamboo", new int[] { 2, 3, 2, 1 }, 0f, 0f, 9, Items.BAMBOO, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, Rarity.COMMON, false);
        armor("kozane", new int[] { 2, 6, 5, 2 }, 0f, 0f, 15, Items.IRON_INGOT, SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, Rarity.COMMON, false);
        armor("akadou", new int[] { 2, 5, 4, 2 }, 0.5f, 0f, 13, Items.COPPER_INGOT, SoundEvents.ITEM_ARMOR_EQUIP_IRON, Rarity.UNCOMMON, false);
        armor("kinkou", new int[] { 2, 5, 3, 1 }, 0f, 0f, 9, Items.GOLD_INGOT, SoundEvents.ITEM_ARMOR_EQUIP_GOLD, Rarity.UNCOMMON, false);
        armor("shinobi", new int[] { 2, 5, 4, 2 }, 1f, 0f, 20, Items.BLACK_WOOL, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, Rarity.RARE, false);
        armor("ooyoroi", new int[] { 3, 8, 6, 3 }, 2f, 0.125f, 33, Items.DIAMOND, SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, Rarity.RARE, false);
        armor("ryujin", new int[] { 3, 7, 6, 3 }, 2f, 0.05f, 30, Items.PRISMARINE_CRYSTALS, SoundEvents.ITEM_ARMOR_EQUIP_TURTLE, Rarity.EPIC, false);
        armor("shogun", new int[] { 3, 8, 6, 3 }, 3f, 0.1f, 37, Items.NETHERITE_INGOT, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, Rarity.EPIC, true);
    }

    private static void armor(String id, int[] prot, float tough, float kb, int dur, Item repair, SoundEvent sound, Rarity rarity,
                              boolean fireproof) {
        PaladinMaterial mat = new PaladinMaterial("samurai_" + id, prot, tough, kb, dur, repair, sound);
        Item[] parts = new Item[4];
        ArmorItem.Type[] types = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
        String[] names = { "helmet", "chest", "legs", "boots" };
        for (int i = 0; i < 4; i++) {
            FabricItemSettings st = new FabricItemSettings().rarity(rarity);
            if (fireproof) st.fireproof();
            parts[i] = Registry.register(Registries.ITEM, new Identifier(Vestments.MOD_ID, "samurai_" + id + "_" + names[i]),
                    new SamuraiArmorItem(mat, types[i], st, id));
        }
        SETS.put(id, parts);
    }

    public static List<Item> allItems() {
        List<Item> l = new ArrayList<>(KATANAS);
        for (Item[] s : SETS.values()) for (Item it : s) l.add(it);
        return l;
    }

    /** Полный комплект самурая на сущности (id) или null. */
    public static String fullSet(LivingEntity e) {
        if (!(e.getEquippedStack(EquipmentSlot.HEAD).getItem() instanceof SamuraiArmorItem h)) return null;
        String s = h.set;
        for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            if (!(e.getEquippedStack(slot).getItem() instanceof SamuraiArmorItem a) || !a.set.equals(s)) return null;
        }
        return s;
    }

    public static boolean wears(LivingEntity e, String set) {
        return set.equals(fullSet(e));
    }

    /** Хоть одна часть комплекта (для пиглинов). */
    public static boolean wearsAny(LivingEntity e, String set) {
        for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            if (e.getEquippedStack(slot).getItem() instanceof SamuraiArmorItem a && a.set.equals(set)) return true;
        }
        return false;
    }

    public static KatanaItem held(LivingEntity e) {
        return e.getMainHandStack().getItem() instanceof KatanaItem k ? k : null;
    }
}
