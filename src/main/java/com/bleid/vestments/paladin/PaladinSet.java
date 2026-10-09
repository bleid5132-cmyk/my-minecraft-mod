package com.bleid.vestments.paladin;

import com.bleid.vestments.gear.GearSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

/**
 * Доспех одного звания паладина и его способности (при полном комплекте и достигнутом звании).
 * Паладин — танк, слабый лекарь и боец ближнего боя:
 *   шлем   — «Свет надежды»: лечит союзников рядом;
 *   кираса — «Стойкость»: снижает весь входящий урон;
 *   поножи — «Натиск»: усиливает урон в ближнем бою;
 *   сапоги — «Непоколебимость»: сопротивление отбрасыванию (+ у верхних званий: не замедляют / не убивают).
 */
public final class PaladinSet {
    public final int rank;
    public final String id;
    public final float reduce;      // доля снижения урона
    public final float melee;       // доля прибавки к урону
    public final float knockback;   // сопротивление отбрасыванию
    public final float auraHeal;    // HP за раз
    public final double auraRadius;
    public final int auraInterval;  // тиков, 0 — нет
    public final boolean slowImmune;
    public final boolean undying;
    public Item helmet, chest, legs, boots;

    public PaladinSet(int rank, String id, float reduce, float melee, float knockback, float auraHeal, double auraRadius,
                      int auraInterval, boolean slowImmune, boolean undying) {
        this.rank = rank;
        this.id = id;
        this.reduce = reduce;
        this.melee = melee;
        this.knockback = knockback;
        this.auraHeal = auraHeal;
        this.auraRadius = auraRadius;
        this.auraInterval = auraInterval;
        this.slowImmune = slowImmune;
        this.undying = undying;
    }

    public List<Item> items() {
        List<Item> l = new ArrayList<>();
        l.add(helmet); l.add(chest); l.add(legs); l.add(boots);
        return l;
    }

    public List<Text> abilities(ArmorItem.Type type) {
        List<Text> l = new ArrayList<>();
        switch (type) {
            case HELMET -> {
                if (auraInterval > 0) l.add(Text.translatable("tooltip.vestments.paladin.aura",
                        GearSet.num(auraInterval / 20.0), GearSet.num(auraRadius), GearSet.num(auraHeal / 2.0)));
                else l.add(Text.translatable("tooltip.vestments.paladin.no_aura"));
            }
            case CHESTPLATE -> l.add(Text.translatable("tooltip.vestments.paladin.reduce", Math.round(reduce * 100)));
            case LEGGINGS -> l.add(Text.translatable("tooltip.vestments.paladin.melee", Math.round(melee * 100)));
            case BOOTS -> {
                l.add(Text.translatable("tooltip.vestments.paladin.knockback", Math.round(knockback * 100)));
                if (slowImmune) l.add(Text.translatable("tooltip.vestments.paladin.slow_immune"));
                if (undying) l.add(Text.translatable("tooltip.vestments.paladin.undying"));
            }
        }
        return l;
    }
}
