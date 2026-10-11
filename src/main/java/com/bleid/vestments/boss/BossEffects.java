package com.bleid.vestments.boss;

import com.bleid.vestments.Vestments;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Эффекты босса: «Истощение» (лечение слабеет в 4 раза) и «Оглушение» (нельзя двигаться и прыгать). */
public final class BossEffects {
    private BossEffects() { }

    static final class Effect extends StatusEffect {
        Effect(StatusEffectCategory c, int color) {
            super(c, color);
        }
    }

    public static final StatusEffect EXHAUSTION = Registry.register(Registries.STATUS_EFFECT,
            new Identifier(Vestments.MOD_ID, "exhaustion"), new Effect(StatusEffectCategory.HARMFUL, 0x3A1030));
    public static final StatusEffect STUN = Registry.register(Registries.STATUS_EFFECT,
            new Identifier(Vestments.MOD_ID, "stun"), new Effect(StatusEffectCategory.HARMFUL, 0xF2D27A)
                    .addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, "6f2a1d0e-5b7c-4c11-9a3e-2c4d5e6f7a81", -1.0,
                            EntityAttributeModifier.Operation.MULTIPLY_TOTAL));

    public static void init() { }
}
