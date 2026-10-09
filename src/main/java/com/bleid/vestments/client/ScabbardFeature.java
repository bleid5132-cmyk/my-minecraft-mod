package com.bleid.vestments.client;

import com.bleid.vestments.client.combat.CombatPose;
import com.bleid.vestments.client.combat.DrawPose;
import com.bleid.vestments.paladin.PaladinSwordItem;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Ножны меча паладина на левом бедре (у левши — на правом). Пока меч не в руке, он лежит в ножнах
 * (видна рукоять); во время выхватывания меч остаётся в ножнах до момента хвата рукояти.
 */
@Environment(EnvType.CLIENT)
public class ScabbardFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    /** Последний меч, который видели в руке у игрока (для чужих игроков — их хотбар нам не виден). */
    private static final Map<UUID, ItemStack> LAST = new java.util.HashMap<>();
    private static final ItemStack DUMMY = new ItemStack(Items.STICK);
    private final ItemRenderer items;

    public ScabbardFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx,
                           ItemRenderer items) {
        super(ctx);
        this.items = items;
    }

    public static void register() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerEntityRenderer pr) helper.register(new ScabbardFeature(pr, context.getItemRenderer()));
        });
    }

    private static ItemStack swordOf(AbstractClientPlayerEntity p) {
        ItemStack main = p.getMainHandStack();
        if (main.getItem() instanceof PaladinSwordItem) {
            LAST.put(p.getUuid(), main.copy());
            return main;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (p == mc.player) {
            for (int i = 0; i < 9; i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (s.getItem() instanceof PaladinSwordItem) return s;
            }
            if (p.getOffHandStack().getItem() instanceof PaladinSwordItem) return p.getOffHandStack();
            return ItemStack.EMPTY;
        }
        ItemStack last = LAST.get(p.getUuid());
        return last == null ? ItemStack.EMPTY : last;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity p,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (p.isInvisible() || p.isSpectator()) return;
        ItemStack sword = swordOf(p);
        if (sword.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        BakedModel sheath = StaffModel.getScabbard(mc, sword.getItem());
        if (sheath == null) return;
        boolean inHand = p.getMainHandStack().getItem() instanceof PaladinSwordItem;
        boolean sheathed = !inHand;
        if (inHand) {
            CombatPose.Pose pose = CombatPose.compute(p, tickDelta);
            if (pose != null && pose.draw != null && !pose.draw.inHand) sheathed = true;
        }
        boolean right = p.getMainArm() == Arm.RIGHT;
        Vector3f[] sh = DrawPose.sheath(right);
        Vector3f y = sh[1], z = sh[2], x = new Vector3f(y).cross(z).normalize();

        matrices.push();
        getContextModel().body.rotate(matrices);
        matrices.translate(sh[0].x / 16f, sh[0].y / 16f, sh[0].z / 16f);
        matrices.multiply(new Matrix3f().setColumn(0, x).setColumn(1, y).setColumn(2, z)
                .getNormalizedRotation(new org.joml.Quaternionf()));
        matrices.scale(0.85f, 0.85f, 0.85f);
        matrices.translate(0, 0.394f, 0);              // гарда — у устья ножен
        items.renderItem(DUMMY, ModelTransformationMode.NONE, false, matrices, vcp, light, OverlayTexture.DEFAULT_UV, sheath);
        if (sheathed) {
            items.renderItem(p, sword, ModelTransformationMode.NONE, false, matrices, vcp, p.getWorld(), light,
                    OverlayTexture.DEFAULT_UV, p.getId() + 7);
        }
        matrices.pop();
    }
}
