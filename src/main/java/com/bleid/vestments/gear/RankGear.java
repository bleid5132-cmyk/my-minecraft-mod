package com.bleid.vestments.gear;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.VestmentArmorItem;
import com.bleid.vestments.weapon.IncenseAbility;
import com.bleid.vestments.weapon.LiturgicalWeaponItem;
import com.bleid.vestments.weapon.WaveAbility;
import com.bleid.vestments.weapon.WeaponAbility;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

/**
 * Облачение и оружие всех санов. Числа подобраны так, чтобы каждый сан был сильнее предыдущего,
 * а все — слабее Патриарха (Регенерация I постоянно, лечение 1 ♥ в радиусе 5 раз в 3 с,
 * +50% на песке душ, хождение по воде 15 с, защита 20 и прочность 3).
 */
public final class RankGear {
    public static final List<GearSet> SETS = new ArrayList<>();
    public static final List<LiturgicalWeaponItem> WEAPONS = new ArrayList<>();
    private static final Map<Item, GearSet> BY_ITEM = new HashMap<>();
    public static GearSet PATRIARCH;

    private RankGear() { }

    public static void init() {
        // ───────────── Облачение степеней (I — простое, II — богаче, III — самое богатое) ─────────────
        armor(0, "diaconate", new int[] { 1, 3, 2, 1 }, 0f, 8, Items.STRING, Rarity.COMMON,
                1.0f, 3.0, 100, 0, 0, 0.10, 0);
        armor(4, "presbyterate", new int[] { 2, 6, 4, 2 }, 1f, 15, Items.GOLD_INGOT, Rarity.UNCOMMON,
                1.0f, 4.0, 80, 240, 60, 0.25, 5);
        armor(10, "episcopate", new int[] { 3, 7, 6, 3 }, 2f, 25, Items.GOLD_INGOT, Rarity.RARE,
                2.0f, 4.5, 80, 160, 80, 0.35, 9);

        // ───────────── Посохи степеней (по одному на степень; у Патриарха — Посох Света) ─────────────
        weapon("diaconate_staff", 0, 4.0, 1.1, 220, Items.STICK, Rarity.COMMON,
                new IncenseAbility(3.0, 4, 1.0f, 0, false, 0f), 400, 25, "prayer");
        weapon("presbyterate_staff", 4, 5.0, 1.1, 420, Items.COPPER_INGOT, Rarity.UNCOMMON,
                new WaveAbility(6.0, 70.0, 4.0f, 1.4f, 4), 240, 30, "ripida");
        weapon("episcopate_staff", 10, 5.5, 1.1, 700, Items.IRON_INGOT, Rarity.RARE,
                new IncenseAbility(4.5, 6, 2.0f, 1, true, 1.5f), 360, 40, "prayer");

        // ───────────── Патриарх (облачение из первой версии мода) ─────────────
        PATRIARCH = new GearSet(14, "patriarch", 2.0f, 5.0, 60, -1, 0, 0.5, 15);
        PATRIARCH.helmet = Vestments.COLLAR;
        PATRIARCH.chest = Vestments.PHELONION;
        PATRIARCH.legs = Vestments.PODRIZNIK;
        PATRIARCH.boots = Vestments.BOOTS;
        add(PATRIARCH);
    }

    private static void add(GearSet set) {
        SETS.add(set);
        for (Item i : set.items()) {
            BY_ITEM.put(i, set);
            if (i instanceof VestmentArmorItem v) v.set = set;
        }
    }

    private static void armor(int rank, String id, int[] protection, float toughness, int durabilityMul, Item repair,
                              Rarity rarity, float auraHeal, double auraRadius, int auraInterval,
                              int regenEvery, int regenFor, double soulSand, int waterWalk) {
        GearSet set = new GearSet(rank, id, auraHeal, auraRadius, auraInterval, regenEvery, regenFor, soulSand, waterWalk);
        GearMaterial mat = new GearMaterial(id, protection, toughness, 0f, durabilityMul, repair);
        set.helmet = piece(id + "_helmet", mat, ArmorItem.Type.HELMET, rarity);
        set.chest = piece(id + "_chest", mat, ArmorItem.Type.CHESTPLATE, rarity);
        set.legs = piece(id + "_legs", mat, ArmorItem.Type.LEGGINGS, rarity);
        set.boots = piece(id + "_boots", mat, ArmorItem.Type.BOOTS, rarity);
        add(set);
    }

    private static Item piece(String id, GearMaterial mat, ArmorItem.Type type, Rarity rarity) {
        return Registry.register(Registries.ITEM, new Identifier(Vestments.MOD_ID, id),
                new VestmentArmorItem(mat, type, new FabricItemSettings().rarity(rarity)));
    }

    private static void weapon(String id, int rank, double damage, double speed, int durability, Item repair,
                               Rarity rarity, WeaponAbility ability, int cooldown, int mana, String icon) {
        LiturgicalWeaponItem item = new LiturgicalWeaponItem(new FabricItemSettings().maxDamage(durability).rarity(rarity),
                rank, damage, speed, ability, cooldown, mana, new Identifier(Vestments.MOD_ID, "textures/gui/" + icon + ".png")) {
            @Override
            public boolean canRepair(net.minecraft.item.ItemStack stack, net.minecraft.item.ItemStack ingredient) {
                return ingredient.isOf(repair) || super.canRepair(stack, ingredient);
            }
        };
        WEAPONS.add(Registry.register(Registries.ITEM, new Identifier(Vestments.MOD_ID, id), item));
    }

    public static GearSet setOf(Item item) {
        return BY_ITEM.get(item);
    }

    /** Комплект, если на сущности надеты все 4 части одного сана, иначе null. */
    public static GearSet fullSet(LivingEntity e) {
        GearSet s = setOf(e.getEquippedStack(EquipmentSlot.HEAD).getItem());
        if (s == null) return null;
        if (e.getEquippedStack(EquipmentSlot.CHEST).getItem() != s.chest) return null;
        if (e.getEquippedStack(EquipmentSlot.LEGS).getItem() != s.legs) return null;
        if (e.getEquippedStack(EquipmentSlot.FEET).getItem() != s.boots) return null;
        return s;
    }
}
