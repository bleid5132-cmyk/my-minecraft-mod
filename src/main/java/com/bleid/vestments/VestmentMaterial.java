package com.bleid.vestments;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Материал облачения: прочность и защита примерно как у золотой брони. */
public class VestmentMaterial implements ArmorMaterial {
    public static final VestmentMaterial INSTANCE = new VestmentMaterial();

    @Override
    public int getDurability(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 120;
            case CHESTPLATE -> 180;
            case LEGGINGS -> 165;
            case BOOTS -> 140;
        };
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 1;
            case CHESTPLATE -> 5;
            case LEGGINGS -> 3;
            case BOOTS -> 1;
        };
    }

    @Override
    public int getEnchantability() {
        return 25;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(Items.GOLD_INGOT);
    }

    @Override
    public String getName() {
        return "vestments";
    }

    @Override
    public float getToughness() {
        return 0;
    }

    @Override
    public float getKnockbackResistance() {
        return 0;
    }
}
