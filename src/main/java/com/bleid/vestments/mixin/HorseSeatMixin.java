package com.bleid.vestments.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Лошадь в настоящую величину (холка ~1,75 блока при росте игрока 1,8): всадник садится выше, ровно в седло.
 * Остальная механика лошади не меняется.
 */
@Mixin(HorseEntity.class)
public abstract class HorseSeatMixin extends AbstractHorseEntity {
    protected HorseSeatMixin(EntityType<? extends AbstractHorseEntity> type, World world) {
        super(type, world);
    }

    @Override
    public double getMountedHeightOffset() {
        return getHeight() * 0.75 + 0.17;
    }
}
