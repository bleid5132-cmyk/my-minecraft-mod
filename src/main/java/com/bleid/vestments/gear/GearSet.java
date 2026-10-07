package com.bleid.vestments.gear;

import com.bleid.vestments.service.Ranks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

/**
 * Комплект облачения одного сана и его способности (только при полном комплекте и достигнутом сане).
 * Способности те же, что у Патриарха, но слабее: лечение игроков рядом, регенерация,
 * скорость на песке душ, хождение по воде.
 */
public final class GearSet {
    public final int rank;
    public final String id;
    public final float auraHeal;       // HP за раз (2 = 1 сердце)
    public final double auraRadius;
    public final int auraInterval;     // тиков; 0 — нет
    public final int regenEvery;       // -1 — постоянная Регенерация I, 0 — нет, иначе раз в N тиков
    public final int regenFor;         // на сколько тиков
    public final double soulSand;      // 0.2 = +20%
    public final int waterWalkSec;     // 0 — нет

    public Item helmet, chest, legs, boots;

    public GearSet(int rank, String id, float auraHeal, double auraRadius, int auraInterval,
                   int regenEvery, int regenFor, double soulSand, int waterWalkSec) {
        this.rank = rank;
        this.id = id;
        this.auraHeal = auraHeal;
        this.auraRadius = auraRadius;
        this.auraInterval = auraInterval;
        this.regenEvery = regenEvery;
        this.regenFor = regenFor;
        this.soulSand = soulSand;
        this.waterWalkSec = waterWalkSec;
    }

    public boolean contains(Item item) {
        return item == helmet || item == chest || item == legs || item == boots;
    }

    public static String num(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-4) return String.valueOf(Math.round(v));
        return String.format(java.util.Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    /** Способность, которую даёт эта часть облачения (null — нет). */
    public Text ability(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> regenEvery < 0 ? Text.translatable("tooltip.vestments.ability.regen_always")
                    : regenEvery > 0 ? Text.translatable("tooltip.vestments.ability.regen", num(regenFor / 20.0), num(regenEvery / 20.0))
                    : null;
            case CHESTPLATE -> auraInterval > 0 ? Text.translatable("tooltip.vestments.ability.aura",
                    num(auraInterval / 20.0), num(auraRadius), num(auraHeal / 2.0)) : null;
            case LEGGINGS -> soulSand > 0 ? Text.translatable("tooltip.vestments.ability.soul_sand", num(soulSand * 100)) : null;
            case BOOTS -> waterWalkSec > 0 ? Text.translatable("tooltip.vestments.ability.water_walk", waterWalkSec) : null;
        };
    }

    public List<Item> items() {
        List<Item> l = new ArrayList<>();
        l.add(helmet); l.add(chest); l.add(legs); l.add(boots);
        return l;
    }

    public String rankKey() {
        return Ranks.nameKey(rank);
    }
}
