package com.bleid.vestments.client.horse;

import com.bleid.vestments.horse.CartEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/** Повозка: кузов, колёса крутятся по пройденному пути, оглобли поднимаются к коню, сундуки — по числу установленных. */
@Environment(EnvType.CLIENT)
public class CartRenderer extends EntityRenderer<CartEntity> {
    static final Identifier GEO = new Identifier("vestments", "geo/cart.geo.json");
    static final Identifier TEX = new Identifier("vestments", "textures/entity/cart.png");
    static GeoModel model;
    static int WL, WR, SHAFTS;
    static final int[] CHEST = new int[6];

    public CartRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        shadowRadius = 1.0f;
    }

    static void ensure() {
        if (model != null) return;
        model = GeoModel.load(GEO);
        WL = model.index("wheel_l"); WR = model.index("wheel_r"); SHAFTS = model.index("shafts");
        for (int i = 0; i < 6; i++) CHEST[i] = model.index("chest" + (i + 1));
    }

    @Override
    public Identifier getTexture(CartEntity e) {
        return TEX;
    }

    @Override
    public void render(CartEntity e, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        ensure();
        int n = model.bones.size();
        float[] rot = new float[n * 3], pos = new float[n * 3], scl = new float[n * 3];
        java.util.Arrays.fill(scl, 1f);
        float wheel = MathHelper.lerp(td, e.prevWheel, e.wheel);
        if (WL >= 0) rot[WL * 3] = wheel;
        if (WR >= 0) rot[WR * 3] = wheel;
        // оглобли: в упряжке — к боку коня, без коня — концами на землю
        float shaft = 18f;
        AbstractHorseEntity h = e.horse();
        if (h != null) {
            double dy = MathHelper.lerp(td, h.lastRenderY, h.getY()) + 1.25 - (MathHelper.lerp(td, e.lastRenderY, e.getY()) + 15 / 16.0);
            shaft = (float) -Math.toDegrees(Math.atan2(dy, 1.9));
        }
        if (SHAFTS >= 0) rot[SHAFTS * 3] = shaft;
        Matrix4f[] mats = model.pose(rot, pos, scl);
        int[] map = new int[n];
        for (int i = 0; i < n; i++) map[i] = i;
        for (int i = 0; i < 6; i++) if (CHEST[i] >= 0 && !e.hasChest(i)) map[CHEST[i]] = -1;
        ms.push();
        float y = MathHelper.lerpAngleDegrees(td, e.prevYaw, e.getYaw());
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-y));
        float wob = e.wobble() - td;
        if (wob > 0) ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(wob) * wob * 0.8f));
        ms.scale(1 / 16f, 1 / 16f, 1 / 16f);
        model.render(ms, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX)), light, OverlayTexture.DEFAULT_UV, mats, map, 1f, 1f, 1f, 1f);
        ms.pop();
        super.render(e, yaw, td, ms, vcp, light);
    }
}
