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
import net.minecraft.util.math.MathHelper;

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
        if (main.getItem() instanceof PaladinSwordItem
                && StaffModel.getScabbard(MinecraftClient.getInstance(), main.getItem()) != null) {
            LAST.put(p.getUuid(), main.copy());
            return main;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (p == mc.player) {
            for (int i = 0; i < 9; i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (s.getItem() instanceof PaladinSwordItem
                        && StaffModel.getScabbard(mc, s.getItem()) != null) return s;     // только мечи с ножнами
            }
            if (p.getOffHandStack().getItem() instanceof PaladinSwordItem) return p.getOffHandStack();
            return ItemStack.EMPTY;
        }
        ItemStack last = LAST.get(p.getUuid());
        return last == null ? ItemStack.EMPTY : last;
    }

    /** Ножны катаны: за поясом слева спереди, клинок назад-вниз, лезвием вверх. */
    private static Vector3f[] katanaSheath(boolean right, CombatPose.Pose pose) {
        float[] m = com.bleid.vestments.samurai.SamuraiMoves.sheathMouth, d = com.bleid.vestments.samurai.SamuraiMoves.sheathDir,
                n = com.bleid.vestments.samurai.SamuraiMoves.sheathNormal;
        if (pose != null && pose.scM != null) {     // ножны, заданные в ключах анимации — с весом позы
            float w = MathHelper.clamp(pose.w, 0f, 1f);
            m = lerp(m, pose.scM, w); d = lerp(d, pose.scD, w); n = lerp(n, pose.scN, w);
        }
        float s = right ? 1f : -1f;
        return new Vector3f[] { new Vector3f(m[0] * s, m[1], m[2]), new Vector3f(d[0] * s, d[1], d[2]), new Vector3f(n[0] * s, n[1], n[2]) };
    }

    private static float[] lerp(float[] a, float[] b, float w) {
        return new float[] { a[0] + (b[0] - a[0]) * w, a[1] + (b[1] - a[1]) * w, a[2] + (b[2] - a[2]) * w };
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity p,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (p.isInvisible() || p.isSpectator()) return;
        CombatPose.Pose pose = CombatPose.compute(p, tickDelta);
        com.bleid.vestments.client.combat.CombatClient.Playback pb = com.bleid.vestments.client.combat.CombatClient.get(p);
        boolean putting = pose != null && pose.clip.reverse && pb != null && !pb.stack.isEmpty();
        ItemStack sword = putting ? pb.stack : swordOf(p);
        if (sword.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        BakedModel sheath = StaffModel.getScabbard(mc, sword.getItem());
        if (sheath == null) return;
        boolean inHand = p.getMainHandStack().getItem() == sword.getItem();
        boolean sheathed = !inHand;
        if (inHand && pose != null && pose.draw != null && !pose.draw.inHand) sheathed = true;
        if (putting) {
            sheathed = !pose.draw.inHand;
            if (pose.draw.inHand) {
                // убираемый меч ещё в руке: ведём его по позе (в руке у игрока уже другой предмет)
                Arm arm = p.getMainArm();
                com.bleid.vestments.client.combat.SwordGrip.render(mc.getEntityRenderDispatcher().getHeldItemRenderer(),
                        p, sword, arm, getContextModel(), pose.draw, pose.w, matrices, vcp, light);
            }
        }
        boolean right = p.getMainArm() == Arm.RIGHT;
        boolean katana = sword.getItem() instanceof com.bleid.vestments.samurai.KatanaItem;
        Vector3f[] sh = katana ? katanaSheath(right, pose) : DrawPose.sheath(right);
        Vector3f y = new Vector3f(sh[1]).normalize(), z = new Vector3f(sh[2]).sub(new Vector3f(y).mul(sh[2].dot(y))).normalize(), x = new Vector3f(y).cross(z).normalize();

        matrices.push();
        getContextModel().body.rotate(matrices);
        matrices.translate(sh[0].x / 16f, sh[0].y / 16f, sh[0].z / 16f);
        matrices.multiply(new Matrix3f().setColumn(0, x).setColumn(1, y).setColumn(2, z)
                .getNormalizedRotation(new org.joml.Quaternionf()));
        matrices.scale(0.85f, 0.85f, 0.85f);
        matrices.translate(0, katana ? 0.375f : 0.394f, 0);   // гарда — у устья ножен
        items.renderItem(DUMMY, ModelTransformationMode.NONE, false, matrices, vcp, light, OverlayTexture.DEFAULT_UV, sheath);
        if (sheathed) {
            items.renderItem(p, sword, ModelTransformationMode.NONE, false, matrices, vcp, p.getWorld(), light,
                    OverlayTexture.DEFAULT_UV, p.getId() + 7);
        }
        matrices.pop();
    }
}
