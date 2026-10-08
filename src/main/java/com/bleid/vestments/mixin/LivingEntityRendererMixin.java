package com.bleid.vestments.mixin;

import com.bleid.vestments.client.SoulAllyClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Призванные души рисуются серо-голубой полупрозрачной голограммой. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void vestments$hologram(LivingEntity entity, boolean showBody, boolean translucent, boolean showOutline,
                                    CallbackInfoReturnable<RenderLayer> cir) {
        if (!SoulAllyClient.isSoul(entity)) return;
        Identifier src = ((LivingEntityRenderer) (Object) this).getTexture(entity);
        Identifier holo = SoulAllyClient.hologram(src);
        if (holo != null) cir.setReturnValue(RenderLayer.getEntityTranslucentEmissive(holo));
    }
}
