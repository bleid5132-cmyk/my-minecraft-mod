package com.bleid.vestments.client.combat;

import com.bleid.vestments.client.StaffModel;
import com.bleid.vestments.client.bend.Bends;
import com.bleid.vestments.samurai.KatanaItem;
import com.bleid.vestments.samurai.SamuraiMoves;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Катана от первого лица. Поза — та же, что от третьего лица (стойка, иай, удары, ното): кисти и катана
 * берутся из неё и переносятся в пространство камеры; руки — прямые, обычной длины, уходят за край экрана,
 * как ванильная рука. Камера рук чуть опущена и отодвинута, чтобы руки и катана были в кадре.
 */
@Environment(EnvType.CLIENT)
public final class SamuraiFirstPerson {
    private static final float TILT = 0.68f, EYE = 25.92f;
    private static final Vector3f OFF = new Vector3f(0, 1f, -2f);
    private static final float SH_X = 9f, SH_Y = -12f, SH_Z = 0f;
    private static final ItemStack DUMMY = new ItemStack(Items.STICK);

    private SamuraiFirstPerson() { }

    public static boolean render(HeldItemRenderer renderer, AbstractClientPlayerEntity pl, float td, ItemStack item,
                                 MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        if (!(item.getItem() instanceof KatanaItem)) return false;
        CombatPose.Pose p = CombatPose.compute(pl, td);
        if (p == null) return false;
        CombatClient.Playback pb = CombatClient.get(pl);
        boolean inHand = p.draw == null || p.draw.inHand;
        if (p.arms() <= 0.01f && pb == null) return true;           // катана в ножнах — руки опущены, не видны

        // модель → камера (в пикселях модели)
        float w = p.w;
        Matrix4f cam = new Matrix4f()
                .translate(OFF)
                .rotateX(TILT)
                .translate(0, -EYE, 0)
                .translate(0, p.lift * w * 16f + 14.4f, 0)
                .rotateY((float) Math.toRadians(-p.spin * w))
                .rotateX((float) Math.toRadians(-p.lean * w))
                .translate(0, -14.4f, 0)
                .translate(0, 24f, 0)
                .scale(-1f, -1f, 1f);
        float aw = p.arms();
        // плечи и руки по позе
        Vector3f rPiv, lPiv;
        if (p.draw != null) {
            rPiv = new Vector3f(p.shX, p.shY, p.shZ);
            lPiv = new Vector3f(p.offShX, p.offShY, p.offShZ);
        } else {
            float by = p.bodyYaw;
            rPiv = new Vector3f(-(float) Math.cos(by) * 5f, 2f, (float) Math.sin(by) * 5f);
            lPiv = new Vector3f((float) Math.cos(by) * 5f, 2f, -(float) Math.sin(by) * 5f);
        }
        Matrix4f rArm = part(rPiv, p.armPitch * aw, p.armYaw * aw, p.armRoll * aw).mul(forearm(p.mainElbow));
        Matrix4f lArm = part(lPiv, p.offPitch * aw, p.offYaw * aw, p.offRoll * aw).mul(forearm(p.offElbow));

        matrices.push();
        matrices.scale(1f / 16f, 1f / 16f, 1f / 16f);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getEntityRenderDispatcher().getRenderer(pl) instanceof PlayerEntityRenderer per) {
            PlayerEntityModel<AbstractClientPlayerEntity> model = per.getModel();
            var skin = pl.getSkinTexture();
            arm(matrices, vcp, light, skin, model.rightArm, model.rightSleeve, new Matrix4f(cam).mul(rArm), -1f, true);
            arm(matrices, vcp, light, skin, model.leftArm, model.leftSleeve, new Matrix4f(cam).mul(lArm), 1f, false);
        }
        // катана
        Matrix4f itemM = null;
        if (inHand) {
            Matrix4f keyed = p.draw != null ? keyed(p.draw) : (p.baseDraw != null ? keyed(p.baseDraw) : null);
            if (p.draw != null) itemM = keyed;
            else {
                Matrix4f strike = new Matrix4f(rArm).translate(-1f, 16.808f, -0.8f).rotateY(p.edge).scale(13.6f);
                itemM = keyed != null && p.baseW > 0 ? blend(strike, keyed, p.baseW) : strike;
            }
        }
        String clip = pb == null ? "" : SamuraiMoves.nameOf(pb.clip);
        boolean showSheath = clip.equals("draw") || clip.equals("iai") || clip.equals("sheathe") || clip.equals("ult_end");
        if (showSheath) {
            BakedModel sheath = StaffModel.getScabbard(mc, item.getItem());
            float[] m = SamuraiMoves.sheathMouth, d = SamuraiMoves.sheathDir, n = SamuraiMoves.sheathNormal;
            Vector3f y = new Vector3f(d[0], d[1], d[2]), z = new Vector3f(n[0], n[1], n[2]), x = new Vector3f(y).cross(z).normalize();
            Matrix4f sh = new Matrix4f(cam).rotateY(p.bodyYaw).translate(m[0], m[1], m[2])
                    .mul(new Matrix4f(new Matrix3f().setColumn(0, x).setColumn(1, y).setColumn(2, z))).scale(13.6f).translate(0, 0.375f, 0);
            matrices.push();
            matrices.multiplyPositionMatrix(sh);
            if (sheath != null) mc.getItemRenderer().renderItem(DUMMY, ModelTransformationMode.NONE, false, matrices, vcp, light,
                    OverlayTexture.DEFAULT_UV, sheath);
            if (!inHand) renderer.renderItem(pl, item, ModelTransformationMode.NONE, false, matrices, vcp, light);
            matrices.pop();
        }
        if (itemM != null) {
            matrices.push();
            matrices.multiplyPositionMatrix(new Matrix4f(cam).mul(itemM));
            renderer.renderItem(pl, item, ModelTransformationMode.NONE, false, matrices, vcp, light);
            matrices.pop();
        }
        matrices.pop();
        return true;
    }

    /** Прямая рука от плеча за краем экрана до кисти из позы (поворот кисти — тоже из позы). */
    private static void arm(MatrixStack matrices, VertexConsumerProvider vcp, int light, net.minecraft.util.Identifier skin,
                            ModelPart arm, ModelPart sleeve, Matrix4f handM, float cx, boolean right) {
        Vector3f hand = handM.transformPosition(new Vector3f(cx, 10f, 0f));
        Vector3f sh = new Vector3f(right ? SH_X : -SH_X, SH_Y, SH_Z);
        Vector3f Y = new Vector3f(hand).sub(sh).normalize();      // рука обычной длины (12 px), направлена к плечу за краем экрана
        Vector3f X = handM.transformDirection(new Vector3f(1, 0, 0)).normalize();
        X.sub(new Vector3f(Y).mul(X.dot(Y))).normalize();
        Vector3f Z = new Vector3f(X).cross(Y);
        Matrix4f m = new Matrix4f()
                .setColumn(0, new org.joml.Vector4f(X, 0))
                .setColumn(1, new org.joml.Vector4f(Y, 0))
                .setColumn(2, new org.joml.Vector4f(Z, 0))
                .setColumn(3, new org.joml.Vector4f(new Vector3f(hand).sub(new Vector3f(Y).mul(10f)).sub(new Vector3f(X).mul(cx)), 1));
        matrices.push();
        matrices.multiplyPositionMatrix(m);
        matrices.scale(16f, 16f, 16f);                     // ModelPart рисует в блоках (1/16 пикселя)
        float px = arm.pivotX, py = arm.pivotY, pz = arm.pivotZ, a = arm.pitch, b = arm.yaw, c = arm.roll;
        boolean vis = sleeve.visible;
        arm.setPivot(0, 0, 0); arm.setAngles(0, 0, 0);
        sleeve.copyTransform(arm);
        Bends.bend(arm, 0f, true);
        Bends.bend(sleeve, 0f, true);
        arm.render(matrices, vcp.getBuffer(RenderLayer.getEntitySolid(skin)), light, OverlayTexture.DEFAULT_UV);
        if (vis) sleeve.render(matrices, vcp.getBuffer(RenderLayer.getEntityTranslucent(skin)), light, OverlayTexture.DEFAULT_UV);
        arm.setPivot(px, py, pz); arm.setAngles(a, b, c);
        sleeve.copyTransform(arm);
        matrices.pop();
    }

    private static Matrix4f part(Vector3f piv, float pitch, float yaw, float roll) {
        return new Matrix4f().translate(piv).rotateZ(roll).rotateY(yaw).rotateX(pitch);
    }

    private static Matrix4f forearm(float elbow) {
        return new Matrix4f().translate(0, Bends.ELBOW_Y, 0).rotateX(-elbow).translate(0, -Bends.ELBOW_Y, 0);
    }

    /** Катана по позе: кулак (y=0 модели предмета) — в точке кисти. */
    private static Matrix4f keyed(DrawPose.Out d) {
        Vector3f y = new Vector3f(d.dir), z = new Vector3f(d.normal), x = new Vector3f(y).cross(z).normalize();
        return new Matrix4f().translate(d.hand).mul(new Matrix4f(new Matrix3f().setColumn(0, x).setColumn(1, y).setColumn(2, z)))
                .scale(13.6f).translate(0, 0.5f, 0);
    }

    private static Matrix4f blend(Matrix4f a, Matrix4f b, float w) {
        Vector3f ta = a.getTranslation(new Vector3f()), tb = b.getTranslation(new Vector3f());
        Quaternionf qa = a.getNormalizedRotation(new Quaternionf()), qb = b.getNormalizedRotation(new Quaternionf());
        return new Matrix4f().translate(ta.lerp(tb, w)).rotate(qa.slerp(qb, w)).scale(13.6f);
    }
}
