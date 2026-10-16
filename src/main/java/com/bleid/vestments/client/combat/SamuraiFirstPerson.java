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
import net.minecraft.util.math.MathHelper;
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
    private static final Vector3f OFF = new Vector3f(0, -4.5f, -6f);
    /** Ближе этой глубины (пиксели перед камерой) остриё не подходит — клинок не лезет в камеру. */
    private static final float TIP_MIN_Z = -7f;
    private static final float SH_X = 9f, SH_Y = -12f, SH_Z = 0f;
    private static final ItemStack DUMMY = new ItemStack(Items.STICK);

    private SamuraiFirstPerson() { }

    /** Что рисуем от первого лица (всё — в пикселях модели в пространстве камеры). */
    private static final class Fp {
        Vector3f rh, lh, rx, lx;
        Matrix4f item, sheath;
        boolean inSheath;
    }

    public static boolean render(HeldItemRenderer renderer, AbstractClientPlayerEntity pl, float td, ItemStack item,
                                 MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        if (!(item.getItem() instanceof KatanaItem k)) return false;
        CombatPose.Pose p = CombatPose.compute(pl, td);
        if (p == null) return false;
        CombatClient.Playback pb = CombatClient.get(pl);
        if (p.arms() <= 0.01f && pb == null) return true;           // катана в ножнах — руки опущены, не видны
        MinecraftClient mc = MinecraftClient.getInstance();
        String clip = pb == null ? "" : SamuraiMoves.nameOf(pb.clip);
        Fp f;
        if (pb == null) {
            f = authored("stance", ((pl.age + td) / 20f / 3.2f) % 1f, mc);
        } else if (SamuraiMoves.FP.containsKey(clip)) {
            // приёмы, снятые с видео (выхватывание, одиночный удар): своя поза от первого лица
            float u = MathHelper.clamp(CombatClient.time(pb, td) / pb.clip.length, 0f, 1f);
            f = authored(clip, u, mc);
        } else {
            f = derived(p, pb, k);
            // переход из стойки (вид от первого лица по видео) в приём и обратно
            if (!CombatClient.isSheathed(pl) && p.w < 0.999f && !pb.clip.endsSheathed) {
                Fp st = authored("stance", ((pl.age + td) / 20f / 3.2f) % 1f, mc);
                f = mix(st, f, p.w);
            }
        }

        matrices.push();
        matrices.scale(1f / 16f, 1f / 16f, 1f / 16f);
        if (mc.getEntityRenderDispatcher().getRenderer(pl) instanceof PlayerEntityRenderer per) {
            PlayerEntityModel<AbstractClientPlayerEntity> model = per.getModel();
            var skin = pl.getSkinTexture();
            arm(matrices, vcp, light, skin, model.rightArm, model.rightSleeve, f.rh, f.rx, -1f, true);
            arm(matrices, vcp, light, skin, model.leftArm, model.leftSleeve, f.lh, f.lx, 1f, false);
        }
        if (f.sheath != null) {
            BakedModel sheath = StaffModel.getScabbard(mc, item.getItem());
            matrices.push();
            matrices.multiplyPositionMatrix(f.sheath);
            if (sheath != null) mc.getItemRenderer().renderItem(DUMMY, ModelTransformationMode.NONE, false, matrices, vcp, light,
                    OverlayTexture.DEFAULT_UV, sheath);
            if (f.inSheath) renderer.renderItem(pl, item, ModelTransformationMode.NONE, false, matrices, vcp, light);
            matrices.pop();
        }
        if (f.item != null && !f.inSheath) {
            matrices.push();
            matrices.multiplyPositionMatrix(keepOutOfCamera(f.item, k));
            renderer.renderItem(pl, item, ModelTransformationMode.NONE, false, matrices, vcp, light);
            matrices.pop();
        }
        matrices.pop();
        return true;
    }

    // ---------------------------------------------------------------- поза по ключам вида от первого лица (по видео)

    private static Vector3f toCam(float[] s, MinecraftClient mc) {
        float ty = (float) Math.tan(Math.toRadians(mc.options.getFov().getValue() * 0.5));
        float aspect = (float) mc.getWindow().getFramebufferWidth() / Math.max(1, mc.getWindow().getFramebufferHeight());
        return new Vector3f(s[0] * s[2] * ty * aspect, s[1] * s[2] * ty, -s[2]);
    }

    private static Fp authored(String set, float u, MinecraftClient mc) {
        java.util.List<SamuraiMoves.FpKey> ks = SamuraiMoves.FP.get(set);
        int i = 0;
        while (i < ks.size() - 2 && u > ks.get(i + 1).u) i++;
        SamuraiMoves.FpKey a = ks.get(i), b = ks.get(i + 1);
        float x = MathHelper.clamp((u - a.u) / Math.max(1e-4f, b.u - a.u), 0f, 1f);
        float e = a.hold || b.hold ? x * x * (3 - 2 * x) : smoothCatmull(ks, i, x);
        float s = x * x * (3 - 2 * x);
        Vector3f hand = lerpS(toCam(a.hand, mc), toCam(b.hand, mc), e);
        Vector3f tip = lerpS(toCam(a.tip, mc), toCam(b.tip, mc), e);
        Vector3f dir = new Vector3f(tip).sub(hand).normalize();
        Vector3f edge = new Vector3f(a.edge[0], a.edge[1], a.edge[2]).lerp(new Vector3f(b.edge[0], b.edge[1], b.edge[2]), s);
        Vector3f grip = new Vector3f(hand).sub(new Vector3f(dir).mul(SamuraiMoves.gripGap));
        Vector3f la = a.off == null ? null : toCam(a.off, mc), lb = b.off == null ? null : toCam(b.off, mc);
        Vector3f lh = lerpS(la == null ? grip : la, lb == null ? grip : lb, s);
        Fp f = new Fp();
        f.rh = hand;
        f.lh = lh;
        f.rx = side(new Vector3f(hand).sub(SH_X, SH_Y, SH_Z));
        f.lx = side(new Vector3f(lh).sub(-SH_X, SH_Y, SH_Z));
        f.item = frame(hand, dir, edge, 0.5f);
        if (set.equals("draw") && u < 0.62f) {
            Vector3f m = toCam(SamuraiMoves.fpSheathMouth, mc), t = toCam(SamuraiMoves.fpSheathTip, mc);
            Vector3f d = new Vector3f(t).sub(m).normalize();
            f.sheath = frame(m, d, new Vector3f(0, 1, 0), 0.375f);
            f.inSheath = u < 0.22f;
        }
        return f;
    }

    /** Плавный ход между ключами с учётом соседних (без остановок в промежуточных ключах). */
    private static float smoothCatmull(java.util.List<SamuraiMoves.FpKey> ks, int i, float x) {
        return x * x * (3 - 2 * x) * 0.5f + x * 0.5f;
    }

    private static Vector3f lerpS(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a).lerp(b, t);
    }

    /** Ось «вбок» для руки: перпендикулярно руке, почти горизонтально. */
    private static Vector3f side(Vector3f armDir) {
        Vector3f y = new Vector3f(armDir).normalize();
        Vector3f x = new Vector3f(y).cross(new Vector3f(0, 0, 1));
        if (x.lengthSquared() < 1e-4f) x.set(1, 0, 0);
        return x.normalize();
    }

    /** Предмет: кулак (или устье ножен) в точке at, клинок по dir, лезвие к edge. */
    private static Matrix4f frame(Vector3f at, Vector3f dir, Vector3f edge, float off) {
        Vector3f y = new Vector3f(dir).normalize();
        Vector3f x = new Vector3f(edge).sub(new Vector3f(y).mul(edge.dot(y)));
        if (x.lengthSquared() < 1e-5f) x.set(1, 0, 0).sub(new Vector3f(y).mul(y.x));
        x.normalize();
        Vector3f z = new Vector3f(x).cross(y).normalize();
        return new Matrix4f().translate(at).mul(new Matrix4f(new Matrix3f().setColumn(0, x).setColumn(1, y).setColumn(2, z)))
                .scale(13.6f).translate(0, off, 0);
    }

    private static Fp mix(Fp a, Fp b, float w) {
        Fp f = new Fp();
        f.rh = lerpS(a.rh, b.rh, w);
        f.lh = lerpS(a.lh, b.lh, w);
        f.rx = lerpS(a.rx, b.rx, w).normalize();
        f.lx = lerpS(a.lx, b.lx, w).normalize();
        f.item = a.item != null && b.item != null ? blend(a.item, b.item, w) : (w > 0.5f ? b.item : a.item);
        f.sheath = w > 0.5f ? b.sheath : a.sheath;
        f.inSheath = w > 0.5f ? b.inSheath : a.inSheath;
        return f;
    }

    // ---------------------------------------------------------------- поза из позы от третьего лица (удары, ното, иай…)

    private static Fp derived(CombatPose.Pose p, CombatClient.Playback pb, KatanaItem k) {
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
        Matrix4f rc = new Matrix4f(cam).mul(rArm), lc = new Matrix4f(cam).mul(lArm);
        Fp f = new Fp();
        f.rh = rc.transformPosition(new Vector3f(-1f, 10f, 0f));
        f.lh = lc.transformPosition(new Vector3f(1f, 10f, 0f));
        f.rx = rc.transformDirection(new Vector3f(1, 0, 0)).normalize();
        f.lx = lc.transformDirection(new Vector3f(1, 0, 0)).normalize();
        boolean inHand = p.draw == null || p.draw.inHand;
        if (inHand) {
            Matrix4f keyed = p.draw != null ? keyed(p.draw) : (p.baseDraw != null ? keyed(p.baseDraw) : null);
            Matrix4f m;
            if (p.draw != null) m = keyed;
            else {
                Matrix4f strike = new Matrix4f(rArm).translate(-1f, 16.808f, -0.8f).rotateY(p.edge).scale(13.6f);
                m = keyed != null && p.baseW > 0 ? blend(strike, keyed, p.baseW) : strike;
            }
            f.item = new Matrix4f(cam).mul(m);
        }
        String clip = SamuraiMoves.nameOf(pb.clip);
        if (clip.equals("iai") || clip.equals("sheathe") || clip.equals("ult_end")) {
            float[] m = SamuraiMoves.sheathMouth, d = SamuraiMoves.sheathDir, n = SamuraiMoves.sheathNormal;
            Vector3f y = new Vector3f(d[0], d[1], d[2]), z = new Vector3f(n[0], n[1], n[2]), x = new Vector3f(y).cross(z).normalize();
            f.sheath = new Matrix4f(cam).rotateY(p.bodyYaw).translate(m[0], m[1], m[2])
                    .mul(new Matrix4f(new Matrix3f().setColumn(0, x).setColumn(1, y).setColumn(2, z))).scale(13.6f).translate(0, 0.375f, 0);
            f.inSheath = !inHand;
        }
        return f;
    }

    /** Прямая рука обычной длины: кисть в точке hand, рука уходит к плечу за краем экрана. */
    private static void arm(MatrixStack matrices, VertexConsumerProvider vcp, int light, net.minecraft.util.Identifier skin,
                            ModelPart arm, ModelPart sleeve, Vector3f hand, Vector3f xAxis, float cx, boolean right) {
        Vector3f sh = new Vector3f(right ? SH_X : -SH_X, SH_Y, SH_Z);
        Vector3f Y = new Vector3f(hand).sub(sh).normalize();
        Vector3f X = new Vector3f(xAxis);
        X.sub(new Vector3f(Y).mul(X.dot(Y)));
        if (X.lengthSquared() < 1e-5f) X = side(Y);
        X.normalize();
        Vector3f Z = new Vector3f(X).cross(Y);
        Matrix4f m = new Matrix4f()
                .setColumn(0, new org.joml.Vector4f(X, 0))
                .setColumn(1, new org.joml.Vector4f(Y, 0))
                .setColumn(2, new org.joml.Vector4f(Z, 0))
                .setColumn(3, new org.joml.Vector4f(new Vector3f(hand).sub(new Vector3f(Y).mul(10f)).sub(new Vector3f(X).mul(cx)), 1));
        matrices.push();
        matrices.multiplyPositionMatrix(m);
        matrices.scale(16f, 16f, 16f);
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

    /**
     * Если остриё подходит к камере ближе TIP_MIN_Z (замах за голову, удар через лицо), клинок
     * поворачивается вокруг кулака ровно настолько, чтобы остриё осталось перед камерой.
     */
    private static Matrix4f keepOutOfCamera(Matrix4f m, KatanaItem k) {
        float len = k.spec.id.equals("murakumo") ? 26.2f : 2.6f + 20f;
        Vector3f fist = m.transformPosition(new Vector3f(0, -0.5f, 0));
        Vector3f tip = m.transformPosition(new Vector3f(0, len / 16f - 0.5f, 0));
        Vector3f d = new Vector3f(tip).sub(fist);
        float L = d.length();
        if (L < 1e-3f || tip.z <= TIP_MIN_Z) return m;
        d.div(L);
        float dz = Math.max(-1f, Math.min(1f, (TIP_MIN_Z - fist.z) / L));
        Vector3f xy = new Vector3f(d.x, d.y, 0);
        if (xy.lengthSquared() < 1e-6f) xy.set(0, 1, 0);
        xy.normalize().mul((float) Math.sqrt(Math.max(0f, 1f - dz * dz)));
        Vector3f nd = new Vector3f(xy.x, xy.y, dz);
        Quaternionf q = new Quaternionf().rotationTo(d, nd);
        return new Matrix4f().translate(fist).rotate(q).translate(new Vector3f(fist).negate()).mul(m);
    }

    private static Matrix4f blend(Matrix4f a, Matrix4f b, float w) {
        Vector3f ta = a.getTranslation(new Vector3f()), tb = b.getTranslation(new Vector3f());
        Quaternionf qa = a.getNormalizedRotation(new Quaternionf()), qb = b.getNormalizedRotation(new Quaternionf());
        return new Matrix4f().translate(ta.lerp(tb, w)).rotate(qa.slerp(qb, w)).scale(13.6f);
    }
}
