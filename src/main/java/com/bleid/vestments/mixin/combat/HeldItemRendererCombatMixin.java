package com.bleid.vestments.mixin.combat;

import com.bleid.vestments.client.combat.FirstPersonSword;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Вид от первого лица: во время приёма меч паладина рисуется по дуге удара. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererCombatMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void vestments$firstPerson(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                       float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                       VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        if (FirstPersonSword.render((HeldItemRenderer) (Object) this, player, tickDelta, hand, item, equipProgress,
                matrices, vcp, light)) {
            ci.cancel();
        }
    }
}
