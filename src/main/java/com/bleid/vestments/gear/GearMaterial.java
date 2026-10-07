package com.bleid.vestments.gear;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Материал облачения сана: защита и прочность растут с саном, у Патриарха — максимум. */
public class GearMaterial implements ArmorMaterial {
    private static final int[] BASE_DURABILITY = { 11, 16, 15, 13 };   // шлем, грудь, ноги, ботинки

    private final String name;
    private final int[] protection;      // шлем, грудь, ноги, ботинки
    private final float toughness, knockback;
    private final int durabilityMul;
    private final Item repair;

    public GearMaterial(String name, int[] protection, float toughness, float knockback, int durabilityMul, Item repair) {
        this.name = name;
        this.protection = protection;
        this.toughness = toughness;
        this.knockback = knockback;
        this.durabilityMul = durabilityMul;
        this.repair = repair;
    }

    private static int index(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 0;
            case CHESTPLATE -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
        };
    }

    public int protectionTotal() {
        int s = 0;
        for (int p : protection) s += p;
        return s;
    }

    @Override public int getDurability(ArmorItem.Type type) { return BASE_DURABILITY[index(type)] * durabilityMul; }
    @Override public int getProtection(ArmorItem.Type type) { return protection[index(type)]; }
    @Override public int getEnchantability() { return 22; }
    @Override public SoundEvent getEquipSound() { return SoundEvents.ITEM_ARMOR_EQUIP_LEATHER; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.ofItems(repair); }
    @Override public String getName() { return name; }
    @Override public float getToughness() { return toughness; }
    @Override public float getKnockbackResistance() { return knockback; }
}
