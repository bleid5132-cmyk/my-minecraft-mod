package com.bleid.vestments.paladin;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;

/** Материал доспеха паладина: защита, прочность, звук надевания по званию. */
public class PaladinMaterial implements ArmorMaterial {
    private static final int[] BASE_DURABILITY = { 11, 16, 15, 13 };

    private final String name;
    private final int[] protection;
    private final float toughness, knockback;
    private final int durabilityMul;
    private final Item repair;
    private final SoundEvent sound;

    public PaladinMaterial(String name, int[] protection, float toughness, float knockback, int durabilityMul, Item repair,
                           SoundEvent sound) {
        this.name = name;
        this.protection = protection;
        this.toughness = toughness;
        this.knockback = knockback;
        this.durabilityMul = durabilityMul;
        this.repair = repair;
        this.sound = sound;
    }

    private static int index(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 0;
            case CHESTPLATE -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
        };
    }

    @Override public int getDurability(ArmorItem.Type type) { return BASE_DURABILITY[index(type)] * durabilityMul; }
    @Override public int getProtection(ArmorItem.Type type) { return protection[index(type)]; }
    @Override public int getEnchantability() { return 15; }
    @Override public SoundEvent getEquipSound() { return sound; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.ofItems(repair); }
    @Override public String getName() { return name; }
    @Override public float getToughness() { return toughness; }
    @Override public float getKnockbackResistance() { return knockback; }
}
