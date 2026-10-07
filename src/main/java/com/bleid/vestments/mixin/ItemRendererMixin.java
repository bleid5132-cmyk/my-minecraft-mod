package com.bleid.vestments.mixin;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.client.StaffModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Посох Света: в инвентаре, на земле и в рамке — плоская иконка, в руке — объёмная 3D-модель
 * (как трезубец в ванильной игре).
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @ModifyVariable(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("HEAD"), argsOnly = true)
    private BakedModel vestments$staffInHand(BakedModel model, ItemStack stack, ModelTransformationMode mode) {
        if (!stack.isOf(Vestments.STAFF_OF_LIGHT)) return model;
        if (mode == ModelTransformationMode.GUI || mode == ModelTransformationMode.GROUND
                || mode == ModelTransformationMode.FIXED) {
            return model;
        }
        BakedModel staff = StaffModel.get(MinecraftClient.getInstance());
        return staff != null ? staff : model;
    }
}
