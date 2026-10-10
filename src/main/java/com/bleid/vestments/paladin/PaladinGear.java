package com.bleid.vestments.paladin;

import com.bleid.vestments.Vestments;
import java.util.ArrayList;
import java.util.HashMap;
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

/** Доспехи, мечи и щиты шести званий паладина. */
public final class PaladinGear {
    public static final List<PaladinSet> SETS = new ArrayList<>();
    public static final List<PaladinSwordItem> SWORDS = new ArrayList<>();
    public static final List<PaladinShieldItem> SHIELDS = new ArrayList<>();
    /** Молоты, копьё, кистень (без ножен). */
    public static final List<PaladinSwordItem> WEAPONS = new ArrayList<>();
    public static ReliquaryItem RELIQUARY;
    private static final Map<Item, PaladinSet> BY_ITEM = new HashMap<>();

    private PaladinGear() { }

    public static void init() {
        //        звание  id                защита          крепк. отбр.  проч. ремонт            звук
        armor(0, "junior_recruit", new int[] { 1, 3, 2, 1 }, 0f, 0f, 8, Items.LEATHER, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, Rarity.COMMON,
                0.04f, 0.05f, 0.05f, 0f, 0, 0, false, false);
        armor(1, "recruit", new int[] { 2, 4, 3, 1 }, 0.5f, 0f, 12, Items.IRON_INGOT, SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, Rarity.COMMON,
                0.07f, 0.08f, 0.1f, 1f, 3, 160, false, false);
        armor(2, "senior_recruit", new int[] { 2, 5, 4, 2 }, 1f, 0f, 16, Items.IRON_INGOT, SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, Rarity.UNCOMMON,
                0.1f, 0.12f, 0.15f, 1f, 4, 140, false, false);
        armor(3, "lord", new int[] { 3, 6, 5, 2 }, 2f, 0.025f, 25, Items.IRON_INGOT, SoundEvents.ITEM_ARMOR_EQUIP_IRON, Rarity.UNCOMMON,
                0.14f, 0.16f, 0.2f, 2f, 5, 120, false, false);
        armor(4, "high_lord", new int[] { 3, 7, 6, 3 }, 2.5f, 0.05f, 32, Items.GOLD_INGOT, SoundEvents.ITEM_ARMOR_EQUIP_IRON, Rarity.RARE,
                0.17f, 0.2f, 0.25f, 2f, 6, 100, true, false);
        armor(5, "general", new int[] { 4, 8, 7, 4 }, 3.5f, 0.075f, 40, Items.DIAMOND, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, Rarity.EPIC,
                0.22f, 0.25f, 0.3f, 3f, 7, 80, true, true);

        //     звание id                       урон  скор.  приём        перезар. прочн. ремонт
        sword(0, "junior_recruit_sword", 5.0, 1.6, "lunge", 8 * 20, 250, Items.IRON_INGOT, Rarity.COMMON);
        sword(1, "recruit_sword", 6.0, 1.6, "crush", 10 * 20, 400, Items.IRON_INGOT, Rarity.COMMON);
        sword(2, "senior_recruit_sword", 7.0, 1.5, "whirl", 12 * 20, 600, Items.IRON_INGOT, Rarity.UNCOMMON);
        sword(3, "lord_sword", 8.0, 1.5, "consecrate", 25 * 20, 900, Items.GOLD_INGOT, Rarity.UNCOMMON);
        sword(4, "high_lord_sword", 9.0, 1.5, "cleave", 15 * 20, 1300, Items.DIAMOND, Rarity.RARE);
        sword(5, "general_sword", 10.5, 1.4, "judgment", 30 * 20, 2000, Items.DIAMOND, Rarity.EPIC);

        //      звание id               урон  скор.  приём             перезар. прочн. ремонт
        weapon(1, "warhammer", 7.5, 1.05, "hammer_throw", 12 * 20, 700, Items.IRON_INGOT, Rarity.UNCOMMON, false);
        weapon(2, "holy_spear", 7.0, 1.3, "dawn_spear", 16 * 20, 800, Items.GOLD_INGOT, Rarity.RARE, false);
        weapon(3, "flail", 8.0, 1.15, "punishing_chain", 14 * 20, 900, Items.IRON_INGOT, Rarity.RARE, false);
        weapon(4, "greathammer", 12.5, 0.8, "heaven_crush", 18 * 20, 1500, Items.DIAMOND, Rarity.EPIC, true);
        RELIQUARY = Registry.register(Registries.ITEM, new Identifier(Vestments.MOD_ID, "reliquary"),
                new ReliquaryItem(new FabricItemSettings().maxCount(1).rarity(Rarity.EPIC)));

        shield(0, "junior_recruit_shield", 150, Items.OAK_PLANKS, Rarity.COMMON);
        shield(1, "recruit_shield", 260, Items.IRON_INGOT, Rarity.COMMON);
        shield(2, "senior_recruit_shield", 380, Items.IRON_INGOT, Rarity.UNCOMMON);
        shield(3, "lord_shield", 520, Items.IRON_INGOT, Rarity.UNCOMMON);
        shield(4, "high_lord_shield", 700, Items.GOLD_INGOT, Rarity.RARE);
        shield(5, "general_shield", 1000, Items.DIAMOND, Rarity.EPIC);
    }

    private static void armor(int rank, String id, int[] prot, float tough, float kb, int dur, Item repair, SoundEvent sound,
                              Rarity rarity, float reduce, float melee, float knock, float aura, double auraR, int auraEvery,
                              boolean slowImmune, boolean undying) {
        PaladinSet set = new PaladinSet(rank, id, reduce, melee, knock, aura, auraR, auraEvery, slowImmune, undying);
        PaladinMaterial mat = new PaladinMaterial(id, prot, tough, kb, dur, repair, sound);
        set.helmet = piece(id + "_helmet", mat, ArmorItem.Type.HELMET, rarity, set);
        set.chest = piece(id + "_chest", mat, ArmorItem.Type.CHESTPLATE, rarity, set);
        set.legs = piece(id + "_legs", mat, ArmorItem.Type.LEGGINGS, rarity, set);
        set.boots = piece(id + "_boots", mat, ArmorItem.Type.BOOTS, rarity, set);
        SETS.add(set);
    }

    private static Item piece(String id, PaladinMaterial mat, ArmorItem.Type type, Rarity rarity, PaladinSet set) {
        PaladinArmorItem item = new PaladinArmorItem(mat, type, new FabricItemSettings().rarity(rarity));
        item.set = set;
        Registry.register(Registries.ITEM, new Identifier(Vestments.MOD_ID, id), item);
        BY_ITEM.put(item, set);
        return item;
    }

    private static void sword(int rank, String id, double damage, double speed, String skill, int cd, int dur, Item repair,
                              Rarity rarity) {
        PaladinSwordItem item = new PaladinSwordItem(new FabricItemSettings().rarity(rarity), rank, damage, speed, skill, cd,
                dur, repair);
        SWORDS.add(Registry.register(Registries.ITEM, new Identifier(Vestments.MOD_ID, id), item));
    }

    private static void weapon(int rank, String id, double damage, double speed, String skill, int cd, int dur, Item repair,
                               Rarity rarity, boolean twoHanded) {
        PaladinSwordItem item = new PaladinSwordItem(new FabricItemSettings().rarity(rarity), rank, damage, speed, skill, cd,
                dur, repair);
        item.twoHanded = twoHanded;
        WEAPONS.add(Registry.register(Registries.ITEM, new Identifier(Vestments.MOD_ID, id), item));
    }

    private static void shield(int rank, String id, int dur, Item repair, Rarity rarity) {
        PaladinShieldItem item = new PaladinShieldItem(new FabricItemSettings().maxDamage(dur).rarity(rarity), rank, repair);
        SHIELDS.add(Registry.register(Registries.ITEM, new Identifier(Vestments.MOD_ID, id), item));
    }

    public static List<Item> allItems() {
        List<Item> l = new ArrayList<>();
        for (PaladinSet s : SETS) l.addAll(s.items());
        l.addAll(SWORDS);
        l.addAll(WEAPONS);
        l.addAll(SHIELDS);
        l.add(RELIQUARY);
        return l;
    }

    /** Полный комплект одного звания или null. */
    public static PaladinSet fullSet(LivingEntity e) {
        PaladinSet s = BY_ITEM.get(e.getEquippedStack(EquipmentSlot.HEAD).getItem());
        if (s == null) return null;
        if (e.getEquippedStack(EquipmentSlot.CHEST).getItem() != s.chest) return null;
        if (e.getEquippedStack(EquipmentSlot.LEGS).getItem() != s.legs) return null;
        if (e.getEquippedStack(EquipmentSlot.FEET).getItem() != s.boots) return null;
        return s;
    }
}
