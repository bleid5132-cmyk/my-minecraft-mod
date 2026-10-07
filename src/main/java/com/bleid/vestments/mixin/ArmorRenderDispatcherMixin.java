package com.bleid.vestments.mixin;

import com.bleid.vestments.client.CapeAnimator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.rpg_foundation.armor_api.client.ArmorRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Запоминает, чью броню сейчас рисует библиотека, — чтобы плащ знал скорость именно этой сущности. */
@Mixin(value = ArmorRenderDispatcher.class, remap = false)
public abstract class ArmorRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private static void vestments$captureEntity(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                ItemStack stack, LivingEntity entity, EquipmentSlot slot, int light,
                                                BipedEntityModel<LivingEntity> contextModel,
                                                CallbackInfoReturnable<Boolean> cir) {
        CapeAnimator.current = entity;
    }
}
