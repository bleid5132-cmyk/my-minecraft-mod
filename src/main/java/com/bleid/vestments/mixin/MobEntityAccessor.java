package com.bleid.vestments.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Доступ к целям моба: у союзных душ убираем собственные цели нападения. */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {
    @Accessor("targetSelector")
    GoalSelector vestments$targetSelector();
}
