package com.bleid.vestments.mixin;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.client.StaffModel;
import com.bleid.vestments.client.StaffRibbons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Посох Света: в инвентаре, на земле и в рамке — плоская иконка, в руке — объёмная 3D-модель.
 * При зажатой ПКМ от первого лица посох наводится навершием вперёд.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V",
            at = @At("HEAD"))
    private void vestments$captureEntity(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
                                         MatrixStack matrices, VertexConsumerProvider consumers, World world,
                                         int light, int overlay, int seed, CallbackInfo ci) {
        StaffModel.currentEntity = entity;
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;III)V",
            at = @At("RETURN"))
    private void vestments$releaseEntity(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
                                         MatrixStack matrices, VertexConsumerProvider consumers, World world,
                                         int light, int overlay, int seed, CallbackInfo ci) {
        StaffModel.currentEntity = null;
    }

    /** Перед тем как модель посоха «закроется», дорисовываем ленты с физикой ткани. */
    @Inject(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"))
    private void vestments$ribbons(ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices,
                                   VertexConsumerProvider consumers, int light, int overlay, BakedModel model,
                                   CallbackInfo ci) {
        if (!stack.isOf(Vestments.STAFF_OF_LIGHT) || mode == ModelTransformationMode.GUI
                || mode == ModelTransformationMode.GROUND || mode == ModelTransformationMode.FIXED) {
            return;
        }
        boolean firstPerson = mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND
                || mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
        StaffRibbons.render(matrices, consumers, light, overlay, StaffModel.currentEntity, firstPerson);
    }

    @ModifyVariable(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("HEAD"), argsOnly = true)
    private BakedModel vestments$staffInHand(BakedModel model, ItemStack stack, ModelTransformationMode mode) {
        if (!stack.isOf(Vestments.STAFF_OF_LIGHT)) return model;
        if (mode == ModelTransformationMode.GUI || mode == ModelTransformationMode.GROUND
                || mode == ModelTransformationMode.FIXED) {
            return model;
        }
        boolean firstPerson = mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND
                || mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
        boolean aiming = firstPerson && StaffModel.isAiming(StaffModel.currentEntity);
        BakedModel staff = StaffModel.get(MinecraftClient.getInstance(), aiming);
        return staff != null ? staff : model;
    }
}
