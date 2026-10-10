package com.bleid.vestments.client.combat;

import com.bleid.vestments.paladin.PaladinSwordItem;
import com.bleid.vestments.paladin.combat.Blade;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Меч от первого лица во время приёма: клинок проходит по той же дуге, что хитбокс и след,
 * но в пространстве камеры — рука «из-за плеча» проводит меч через весь экран.
 */
@Environment(EnvType.CLIENT)
public final class FirstPersonSword {
    private FirstPersonSword() { }

    public static boolean render(HeldItemRenderer renderer, AbstractClientPlayerEntity player, float tickDelta, Hand hand,
                                 ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vcp,
                                 int light) {
        if (hand != Hand.MAIN_HAND || !(item.getItem() instanceof PaladinSwordItem)) return false;
        CombatPose.Pose p = CombatPose.compute(player, tickDelta);
        if (p == null || p.clip.offOnly || p.clip.dodge) return false;
        boolean right = player.getMainArm() == Arm.RIGHT;
        float s = right ? 1f : -1f;

        float yaw = player.getYaw(tickDelta), pitch = player.getPitch(tickDelta);
        Vec3d[] fr = Blade.frame(yaw, pitch);
        Vec3d d = Blade.toWorld(p.blade.dir(), fr), n = Blade.toWorld(p.blade.normal(), fr);
        Vec3d look = Vec3d.fromPolar(pitch, yaw);
        Vec3d rc = new Vec3d(-Math.cos(Math.toRadians(yaw)), 0, -Math.sin(Math.toRadians(yaw)));
        Vec3d uc = rc.crossProduct(look);
        // в пространство камеры: x вправо, y вверх, z назад
        Vector3f dc = new Vector3f((float) d.dotProduct(rc) * s, (float) d.dotProduct(uc), (float) -d.dotProduct(look));
        Vector3f nc = new Vector3f((float) n.dotProduct(rc) * s, (float) n.dotProduct(uc), (float) -n.dotProduct(look));
        dc.normalize();
        nc.sub(new Vector3f(dc).mul(nc.dot(dc))).normalize();
        Vector3f xc = new Vector3f(dc).cross(nc).normalize();

        // рука: от плеча (справа-снизу) вдоль клинка, не дальше чем в полуметре перед глазами
        Vector3f hand0 = new Vector3f(0.36f * s, -0.42f, -0.32f).add(new Vector3f(dc).mul(0.32f));
        hand0.z = Math.min(hand0.z, -0.28f);
        Matrix4f fight = new Matrix4f().translate(hand0)
                .mul(new Matrix4f().set(new Matrix3f().setColumn(0, xc).setColumn(1, dc).setColumn(2, nc)))
                .scale(0.62f).translate(0, 0.53f, 0);
        // обычное положение меча от первого лица (как у ванильного меча)
        Matrix4f van = new Matrix4f().translate(0.56f * s, -0.52f + equipProgress * -0.6f, -0.72f)
                .translate(1.13f / 16f * s, 3.2f / 16f, 1.13f / 16f)
                .rotateXYZ(0, (float) Math.toRadians(-90 * s), (float) Math.toRadians(-20 * s))
                .scale(0.68f);
        float w = p.w;
        Vector3f t = van.getTranslation(new Vector3f()).lerp(fight.getTranslation(new Vector3f()), w);
        Quaternionf q = van.getNormalizedRotation(new Quaternionf()).slerp(fight.getNormalizedRotation(new Quaternionf()), w);
        float sc = MathHelper.lerp(w, 0.68f, 0.62f);
        matrices.push();
        matrices.translate(t.x, t.y, t.z);
        matrices.multiply(q);
        matrices.scale(sc, sc, sc);
        renderer.renderItem(player, item, ModelTransformationMode.NONE, !right, matrices, vcp, light);
        matrices.pop();
        return true;
    }
}
