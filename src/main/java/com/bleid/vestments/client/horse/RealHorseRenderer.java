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
            COAT.put(c, new Identifier[] { tex("coat_" + n + "_hi"), tex("coat_" + n + "_lo") });
        }
        for (HorseMarking m : HorseMarking.values()) {
            String n = switch (m) {
                case WHITE -> "white";
                case WHITE_FIELD -> "whitefield";
                case WHITE_DOTS -> "whitedots";
                case BLACK_DOTS -> "blackdots";
                default -> null;
            };
            if (n != null) MARK.put(m, new Identifier[] { tex("mark_" + n + "_hi"), tex("mark_" + n + "_lo") });
        }
    }

    static Identifier tex(String n) {
        return new Identifier("vestments", "textures/entity/horse/" + n + ".png");
    }

    public RealHorseRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new RealHorseModel(), 0.8f);
        addFeature(new Markings(this));
        addFeature(new Armor(this));
        addFeature(new Tack(this));
    }

    @Override
    public Identifier getTexture(HorseEntity e) {
        return COAT.get(e.getVariant())[RealHorseModel.far(e) ? 1 : 0];
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
            RealHorseModel m = getContextModel();
            m.render(ms, vcp.getBuffer(RenderLayer.getEntityTranslucent(t[m.far ? 1 : 0])), light, overlay(e), 1f, 1f, 1f, 1f);
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
            RealHorseModel.ensure();
            getContextModel().renderWith(RealHorseModel.armor, ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(t)), light, OverlayTexture.DEFAULT_UV, r, g, b);
        }
    }

    static final class Tack extends FeatureRenderer<HorseEntity, RealHorseModel> {
        Tack(FeatureRendererContext<HorseEntity, RealHorseModel> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, HorseEntity e, float la, float ld, float td, float ap, float hy, float hp) {
            if (!e.isSaddled() || e.isInvisible()) return;
            RealHorseModel.ensure();
            getContextModel().renderWith(RealHorseModel.tack, ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TACK_TEX)), light, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f);
        }
    }
}
