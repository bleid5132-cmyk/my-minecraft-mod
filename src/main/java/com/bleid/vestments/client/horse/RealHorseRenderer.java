package com.bleid.vestments.client.horse;

import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.HorseColor;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.HorseMarking;
import net.minecraft.item.DyeableHorseArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

/** Рендер ванильной лошади нашей моделью: масть, отметины, броня, седло с оголовьем. */
@Environment(EnvType.CLIENT)
public class RealHorseRenderer extends MobEntityRenderer<HorseEntity, RealHorseModel> {
    private static final Map<HorseColor, Identifier[]> COAT = new EnumMap<>(HorseColor.class);
    private static final Map<HorseMarking, Identifier[]> MARK = new EnumMap<>(HorseMarking.class);
    static final Identifier TACK_TEX = tex("tack");
    static final Identifier IRON = tex("armor_iron"), GOLD = tex("armor_gold"), DIAMOND = tex("armor_diamond"), LEATHER = tex("armor_leather");

    static {
        for (HorseColor c : HorseColor.values()) {
            String n = switch (c) {
                case WHITE -> "white";
                case CREAMY -> "creamy";
                case CHESTNUT -> "chestnut";
                case BROWN -> "brown";
                case BLACK -> "black";
                case GRAY -> "gray";
                default -> "darkbrown";
            };
            COAT.put(c, new Identifier[] { tex("coat_" + n) });
        }
        for (HorseMarking m : HorseMarking.values()) {
            String n = switch (m) {
                case WHITE -> "white";
                case WHITE_FIELD -> "whitefield";
                case WHITE_DOTS -> "whitedots";
                case BLACK_DOTS -> "blackdots";
                default -> null;
            };
            if (n != null) MARK.put(m, new Identifier[] { tex("mark_" + n) });
        }
    }

    static Identifier tex(String n) {
        return new Identifier("vestments", "textures/entity/horse/" + n + ".png");
    }

    public RealHorseRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new RealHorseModel(), 1.0f);
        addFeature(new Markings(this));
        addFeature(new Armor(this));
        addFeature(new Tack(this));
    }

    static final Identifier REIN = tex("rein");

    @Override
    public void render(HorseEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        super.render(e, yaw, td, ms, vcp, light);
        if (e.isSaddled() && !e.isInvisible() && !e.isBaby()) reins(e, td, ms, vcp, light);
    }

    /**
     * Повод: от колец трензеля к рукам всадника; без всадника — лежит на шее и уходит к луке седла.
     * Точки считаются в осях модели (пиксели) по текущей позе лошади, затем переводятся в мир.
     */
    private void reins(HorseEntity e, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        RealHorseModel m = getModel();
        if (m.mats == null || RealHorseModel.HEAD < 0) return;
        float by = net.minecraft.util.math.MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw);
        net.minecraft.entity.Entity rider = e.getFirstPassenger();
        RiderPose.Pose rp = rider instanceof net.minecraft.entity.LivingEntity le ? RiderPose.compute(le) : null;
        RealHorseModel.State st = RealHorseModel.state(e);
        net.minecraft.client.render.VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(REIN));
        for (int side = -1; side <= 1; side += 2) {
            org.joml.Vector3f bit = m.mats[RealHorseModel.HEAD].transformPosition(new org.joml.Vector3f(side * 3.2f, 35.85f, 17.1f));
            java.util.List<org.joml.Vector3f> pts = new java.util.ArrayList<>();
            pts.add(bit);
            if (rp != null && st != null) {
                pts.add(hand(st, rp, side));
                strap(ms, vc, light, by, pts, 0.06f);
            } else {
                pts.add(m.mats[RealHorseModel.NECK].transformPosition(new org.joml.Vector3f(side * 3.15f, 37f, 8.5f)));
                pts.add(m.mats[RealHorseModel.MIDDLE].transformPosition(new org.joml.Vector3f(side * 2.2f, 34f, 4f)));
                strap(ms, vc, light, by, pts, 0.12f);
            }
        }
    }

    /** Кисть всадника в осях модели лошади (пиксели): таз в седле, грудь наклонена, руки опущены к холке. */
    static org.joml.Vector3f hand(RealHorseModel.State st, RiderPose.Pose rp, int side) {
        float k = 0.9375f / RealHorseModel.SCALE;                 // пиксель игрока → пиксель модели лошади
        float c = rp.chest, a1 = -rp.armPitch + c, a2 = a1 + rp.elbow;
        float y = 6f + 4f * (float) Math.cos(c) - 4f * (float) Math.cos(a1) - 6f * (float) Math.cos(a2);
        float z = 4f * (float) Math.sin(c) + 4f * (float) Math.sin(a1) + 6f * (float) Math.sin(a2);
        org.joml.Vector3f off = new org.joml.Vector3f(side * 3.8f * k, y * k, z * k)
                .rotateX((float) Math.toRadians(-st.seatPitch));
        return off.add(st.seatX, st.seatY + rp.rise * 16f / RealHorseModel.SCALE, st.seatZ);
    }

    /** Ремень по ломаной с провисанием: точки в пикселях модели → в мир, лента крестом (видна с любой стороны). */
    private static void strap(MatrixStack ms, net.minecraft.client.render.VertexConsumer vc, int light, float bodyYaw,
                              java.util.List<org.joml.Vector3f> pts, float sag) {
        float k = RealHorseModel.SCALE / 16f;
        java.util.List<org.joml.Vector3f> w = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < pts.size(); i++) {
            org.joml.Vector3f a = new org.joml.Vector3f(pts.get(i)).mul(k), b = new org.joml.Vector3f(pts.get(i + 1)).mul(k);
            float len = a.distance(b);
            int n = 10;
            for (int j = (i == 0 ? 0 : 1); j <= n; j++) {
                float t = j / (float) n;
                org.joml.Vector3f p = new org.joml.Vector3f(a).lerp(b, t);
                p.y -= sag * len * 4f * t * (1 - t);
                w.add(p.rotateY((float) Math.toRadians(-bodyYaw)));
            }
        }
        MatrixStack.Entry en = ms.peek();
        org.joml.Matrix4f pm = en.getPositionMatrix();
        org.joml.Matrix3f nm = en.getNormalMatrix();
        float hw = 0.022f;
        for (int i = 0; i + 1 < w.size(); i++) {
            org.joml.Vector3f p0 = w.get(i), p1 = w.get(i + 1);
            org.joml.Vector3f d = new org.joml.Vector3f(p1).sub(p0);
            if (d.lengthSquared() < 1e-8f) continue;
            org.joml.Vector3f s1 = new org.joml.Vector3f(d).cross(0, 1, 0);
            if (s1.lengthSquared() < 1e-8f) s1.set(1, 0, 0);
            s1.normalize(hw);
            org.joml.Vector3f s2 = new org.joml.Vector3f(d).cross(s1).normalize(hw);
            for (org.joml.Vector3f s : new org.joml.Vector3f[] { s1, s2 }) {
                quad(vc, pm, nm, light, new org.joml.Vector3f(p0).sub(s), new org.joml.Vector3f(p0).add(s),
                        new org.joml.Vector3f(p1).add(s), new org.joml.Vector3f(p1).sub(s));
            }
        }
    }

    private static void quad(net.minecraft.client.render.VertexConsumer vc, org.joml.Matrix4f pm, org.joml.Matrix3f nm, int light,
                             org.joml.Vector3f a, org.joml.Vector3f b, org.joml.Vector3f c, org.joml.Vector3f d) {
        float[][] uv = { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } };
        org.joml.Vector3f[] v = { a, b, c, d };
        for (int i = 0; i < 4; i++) {
            vc.vertex(pm, v[i].x, v[i].y, v[i].z).color(1f, 1f, 1f, 1f).texture(uv[i][0], uv[i][1])
                    .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nm, 0f, 1f, 0f).next();
        }
    }

    @Override
    public Identifier getTexture(HorseEntity e) {
        return COAT.get(e.getVariant())[0];
    }

    static int overlay(HorseEntity e) {
        return LivingEntityRenderer.getOverlay(e, 0f);
    }

    static final class Markings extends FeatureRenderer<HorseEntity, RealHorseModel> {
        Markings(FeatureRendererContext<HorseEntity, RealHorseModel> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, HorseEntity e, float la, float ld, float td, float ap, float hy, float hp) {
            Identifier[] t = MARK.get(e.getMarking());
            if (t == null || e.isInvisible()) return;
            getContextModel().render(ms, vcp.getBuffer(RenderLayer.getEntityTranslucent(t[0])), light, overlay(e), 1f, 1f, 1f, 1f);
        }
    }

    static final class Armor extends FeatureRenderer<HorseEntity, RealHorseModel> {
        Armor(FeatureRendererContext<HorseEntity, RealHorseModel> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, HorseEntity e, float la, float ld, float td, float ap, float hy, float hp) {
            ItemStack st = e.getArmorType();
            if (st.isEmpty() || e.isInvisible()) return;
            Identifier t;
            float r = 1f, g = 1f, b = 1f;
            if (st.isOf(Items.IRON_HORSE_ARMOR)) t = IRON;
            else if (st.isOf(Items.GOLDEN_HORSE_ARMOR)) t = GOLD;
            else if (st.isOf(Items.DIAMOND_HORSE_ARMOR)) t = DIAMOND;
            else if (st.getItem() instanceof DyeableHorseArmorItem d) {
                t = LEATHER;
                int c = d.getColor(st);
                r = (c >> 16 & 255) / 255f; g = (c >> 8 & 255) / 255f; b = (c & 255) / 255f;
            } else t = IRON;
            getContextModel().renderArmor(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(t)), light, OverlayTexture.DEFAULT_UV, r, g, b);
        }
    }

    static final class Tack extends FeatureRenderer<HorseEntity, RealHorseModel> {
        Tack(FeatureRendererContext<HorseEntity, RealHorseModel> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, HorseEntity e, float la, float ld, float td, float ap, float hy, float hp) {
            if (!e.isSaddled() || e.isInvisible()) return;
            getContextModel().renderTack(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TACK_TEX)), light, OverlayTexture.DEFAULT_UV);
        }
    }
}
