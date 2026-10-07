package com.bleid.vestments;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Материал облачения Патриарха — самого сильного сана: защита 20, прочность 3. */
public class VestmentMaterial implements ArmorMaterial {
    public static final VestmentMaterial INSTANCE = new VestmentMaterial();

    @Override
    public int getDurability(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 11 * 33;
            case CHESTPLATE -> 16 * 33;
            case LEGGINGS -> 15 * 33;
            case BOOTS -> 13 * 33;
        };
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 3;
            case CHESTPLATE -> 8;
            case LEGGINGS -> 6;
            case BOOTS -> 3;
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
        return 3;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.1f;
    }
}
