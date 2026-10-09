package com.bleid.vestments.mixin;

import com.bleid.vestments.client.CapeAnimator;
import net.minecraft.entity.EquipmentSlot;
import net.rpg_foundation.armor_api.client.model.GeoArmorModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** После того как библиотека скопировала позу игрока на модель, наклоняем плащ. */
@Mixin(value = GeoArmorModel.class, remap = false)
public abstract class GeoArmorModelMixin {
    @Inject(method = "applySlotVisibility", at = @At("TAIL"))
    private void vestments$animateCape(EquipmentSlot slot, CallbackInfo ci) {
        CapeAnimator.apply((GeoArmorModel) (Object) this);
        // детали брони гнутся в локтях и коленях вместе с телом
        com.bleid.vestments.client.bend.Bends.applyArmor(
                (net.minecraft.client.render.entity.model.BipedEntityModel<?>) (Object) this, CapeAnimator.current);
    }
}
