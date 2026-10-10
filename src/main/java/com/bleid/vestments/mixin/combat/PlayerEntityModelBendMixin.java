package com.bleid.vestments.mixin.combat;

import com.bleid.vestments.client.bend.Bends;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Настоящий изгиб локтей и коленей у модели игрока (bendy-lib): рука, рукав, нога, штанина. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelBendMixin {
    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;
    @Shadow @Final public ModelPart leftPants;
    @Shadow @Final public ModelPart rightPants;
    @Shadow @Final public ModelPart jacket;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void vestments$initBend(ModelPart root, boolean thinArms, CallbackInfo ci) {
        BipedEntityModel<?> m = (BipedEntityModel<?>) (Object) this;
        for (ModelPart part : new ModelPart[] { m.rightArm, m.leftArm, rightSleeve, leftSleeve,
                m.rightLeg, m.leftLeg, rightPants, leftPants }) {
            Bends.init(part);
        }
        Bends.initBody(m.body);
        Bends.initBody(jacket);
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void vestments$applyBend(LivingEntity e, float limbAngle, float limbDistance, float progress, float headYaw,
                                     float headPitch, CallbackInfo ci) {
        float[] v = Bends.current;
        Bends.current = null;
        float eR = v == null ? 0 : v[0], eL = v == null ? 0 : v[1], kR = v == null ? 0 : v[2], kL = v == null ? 0 : v[3];
        BipedEntityModel<?> m = (BipedEntityModel<?>) (Object) this;
        Bends.bend(m.rightArm, eR, true);
        Bends.bend(rightSleeve, eR, true);
        Bends.bend(m.leftArm, eL, true);
        Bends.bend(leftSleeve, eL, true);
        Bends.bend(m.rightLeg, kR, false);
        Bends.bend(rightPants, kR, false);
        Bends.bend(m.leftLeg, kL, false);
        Bends.bend(leftPants, kL, false);
        float cf = v == null || v.length < 6 ? 0 : v[4], cs = v == null || v.length < 6 ? 0 : v[5];
        Bends.bendBody(m.body, cf, cs);
        Bends.bendBody(jacket, cf, cs);
    }
}
