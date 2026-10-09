package com.bleid.vestments.client.combat;

import com.bleid.vestments.client.bend.Bends;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.ModelWithArms;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Меч в руке при выхватывании/убирании: положение задаётся позой DrawPose в пространстве модели и
 * плавно смешивается с обычным хватом (кисть · ванильная display-трансформация).
 */
@Environment(EnvType.CLIENT)
public final class SwordGrip {
    private SwordGrip() { }

    public static float elbow(LivingEntity e, Arm arm) {
        float[] v = Bends.of(e);
        return v == null ? 0f : (arm == Arm.RIGHT ? v[0] : v[1]);
    }

    /** Обычный хват (ванильная display-трансформация меча) в системе координат кисти. */
    public static Matrix4f vanillaGrip(float s) {
        return new Matrix4f()
                .rotateX((float) Math.toRadians(-90)).rotateY((float) Math.PI)
                .translate(s / 16f, 0.125f, -0.625f)
                .translate(0, 4f / 16f, 0.5f / 16f)
                .rotateXYZ(0, (float) Math.toRadians(-90 * s), (float) Math.toRadians(10 * s))
                .scale(0.85f);
    }

    /** Меч по позе выхватывания (матрицы — в пространстве модели), w — вес позы. */
    public static void render(HeldItemRenderer renderer, LivingEntity e, ItemStack stack, Arm arm, ModelWithArms model,
                              DrawPose.Out d, float w, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        boolean left = arm == Arm.LEFT;
        float s = left ? -1f : 1f;
        Matrix4f base = new Matrix4f(matrices.peek().getPositionMatrix());
        matrices.push();
        model.setArmAngle(arm, matrices);
        Bends.forearm(matrices, arm, elbow(e, arm));
        Matrix4f hand = new Matrix4f(matrices.peek().getPositionMatrix());
        matrices.pop();
        Matrix4f van = base.invert().mul(hand).mul(vanillaGrip(s));
        Vector3f y = new Vector3f(d.dir), z = new Vector3f(d.normal), x = new Vector3f(y).cross(z).normalize();
        Matrix4f target = new Matrix4f()
                .translate(d.hand.x / 16f, d.hand.y / 16f, d.hand.z / 16f)
                .mul(new Matrix4f(new Matrix3f().setColumn(0, x).setColumn(1, y).setColumn(2, z)))
                .scale(0.85f)
                .translate(0, 0.5375f, 0);               // середина рукояти — в кулаке
        Vector3f tv = van.getTranslation(new Vector3f()), tf = target.getTranslation(new Vector3f());
        Quaternionf qv = van.getNormalizedRotation(new Quaternionf()), qf = target.getNormalizedRotation(new Quaternionf());
        Vector3f t = tv.lerp(tf, w);
        Quaternionf q = qv.slerp(qf, w);
        matrices.push();
        matrices.translate(t.x, t.y, t.z);
        matrices.multiply(q);
        matrices.scale(0.85f, 0.85f, 0.85f);
        renderer.renderItem(e, stack, ModelTransformationMode.NONE, left, matrices, vcp, light);
        matrices.pop();
    }
}
