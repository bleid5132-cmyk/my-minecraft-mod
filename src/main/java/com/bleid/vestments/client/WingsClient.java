package com.bleid.vestments.client;

import com.bleid.vestments.client.fx.FxSystem;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * «Ангельские крылья» паладина: светящиеся крылья из перьев за спиной (медленно взмахивают),
 * свой игрок под крыльями плавно парит — падение замедлено, с Shift можно спуститься.
 */
@Environment(EnvType.CLIENT)
public final class WingsClient {
    private static final Map<Integer, long[]> ACTIVE = new HashMap<>();   // id → {начало, конец} (тики мира)

    private WingsClient() { }

    public static void start(Entity e, int dur) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        long now = mc.world.getTime();
        ACTIVE.put(e.getId(), new long[] { now, now + dur });
        if (e == mc.player && mc.player.isOnGround()) {
            mc.player.addVelocity(0, 0.55, 0);                  // взмах — и паладин поднимается над землёй
        }
    }

    /** 0..1 — насколько раскрыты крылья (плавно появляются и исчезают). */
    public static float open(Entity e, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long[] t = ACTIVE.get(e.getId());
        if (t == null || mc.world == null) return 0f;
        float now = mc.world.getTime() + td;
        if (now > t[1]) return 0f;
        float in = MathHelper.clamp((now - t[0]) / 8f, 0f, 1f), out = MathHelper.clamp((t[1] - now) / 12f, 0f, 1f);
        return Math.min(in, out);
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.world == null) { ACTIVE.clear(); return; }
            long now = mc.world.getTime();
            ACTIVE.values().removeIf(t -> now > t[1] + 2);
            ClientPlayerEntity me = mc.player;
            if (me != null && ACTIVE.containsKey(me.getId()) && !me.isOnGround() && !me.isSneaking() && !me.isTouchingWater()) {
                Vec3d v = me.getVelocity();
                if (v.y < -0.035) me.setVelocity(v.x, -0.035, v.z);          // парение
            }
        });
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerEntityRenderer pr) helper.register(new Feature(pr));
        });
    }

    // ---------------------------------------------------------------- орлиные крылья

    private static final net.minecraft.util.Identifier TEX = new net.minecraft.util.Identifier("vestments", "textures/entity/wing_feathers.png");
    private static final net.minecraft.util.Identifier GLOW = new net.minecraft.util.Identifier("vestments", "textures/entity/wing_feathers_glow.png");
    private static final float SCALE = 0.72f;
    /** Перья крыла в плоскости крыла: {корень u, корень v, угол°, длина, ширина, вид (0 маховое, 1 второстепенное, 2 кроющее), слой}. */
    private static final float[][] FEATHERS = build();

    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[] { a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t };
    }

    private static float[][] build() {
        java.util.List<float[]> f = new java.util.ArrayList<>();
        float[] r0 = { 1.5f, 0f }, el = { 9.5f, 8.5f }, wr = { 18f, 13f };
        for (int i = 0; i <= 10; i++) {                          // второстепенные маховые: свисают под «рукой» крыла
            float t = i / 10f;
            float[] r = t < 0.5f ? lerp(r0, el, t * 2) : lerp(el, wr, (t - 0.5f) * 2);
            f.add(new float[] { r[0], r[1], -96 + 44 * t, 17f + 4f * t, 4.0f, 1, 0 });
        }
        for (int i = 0; i <= 8; i++) {                           // первостепенные маховые: веер «пальцев» у запястья
            float k = i / 8f;
            double ra = Math.toRadians(40 - 80 * k);
            float len = 22f + 6f * (float) Math.sin(k * Math.PI * 0.8);
            f.add(new float[] { wr[0] + 1.5f * (float) Math.cos(ra), wr[1] + 1.5f * (float) Math.sin(ra), 38 - 88 * k, len, 3.4f, 0, 0 });
        }
        for (int row = 0; row < 2; row++) {                      // кроющие перья в два ряда поверх
            float len = row == 0 ? 9f : 5f;
            for (int i = 0; i <= 11; i++) {
                float t = i / 11f;
                float[] r = t < 0.5f ? lerp(r0, el, t * 2) : lerp(el, wr, (t - 0.5f) * 2);
                f.add(new float[] { r[0], r[1] + 0.3f, -95 + 55 * t + row * 4, len * (0.85f + 0.3f * t), 3.8f, 2, 1 + row });
            }
        }
        return f.toArray(new float[0][]);
    }

    static final class Feature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
        Feature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity p,
                           float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
            float o = open(p, tickDelta);
            if (o <= 0.001f || p.isInvisible()) return;
            matrices.push();
            getContextModel().body.rotate(matrices);
            MatrixStack.Entry en = matrices.peek();
            // медленный величественный взмах; в воздухе — чуть сильнее
            float flapDeg = MathHelper.sin(age * 0.09f) * (p.isOnGround() ? 5f : 11f);
            VertexConsumer solid = vcp.getBuffer(net.minecraft.client.render.RenderLayer.getEntityCutoutNoCull(TEX));
            draw(solid, en, o, flapDeg, light, false);
            VertexConsumer glow = vcp.getBuffer(net.minecraft.client.render.RenderLayer.getEyes(GLOW));
            draw(glow, en, o, flapDeg, light, true);
            matrices.pop();
        }

        private static void draw(VertexConsumer vc, MatrixStack.Entry en, float o, float flapDeg, int light, boolean glow) {
            Matrix4f mat = en.getPositionMatrix();
            org.joml.Matrix3f nm = en.getNormalMatrix();
            float raise = (float) Math.toRadians(flapDeg) - (1 - o) * 1.2f;     // складывание при появлении/исчезании
            float spread = 0.3f + 0.7f * o;
            float cr = MathHelper.cos(raise), sr = MathHelper.sin(raise);
            for (int side = -1; side <= 1; side += 2) {
                // плоскость крыла: «наружу» и «вверх», обе чуть назад от спины (модель: +X влево, −Y вверх, +Z назад)
                Vec3d u = new Vec3d(-side, 0, 0.35).normalize();
                Vec3d v = new Vec3d(0, -1, 0.3).normalize();
                Vec3d n = u.crossProduct(v).normalize();
                if (n.z < 0) n = n.multiply(-1);
                Vec3d root = new Vec3d(-side * 1.0, 2.5, 2.3);
                for (float[] f : FEATHERS) {
                    float ru = f[0] * spread, rv = f[1] * spread;
                    float qu = ru * cr - rv * sr, qv = ru * sr + rv * cr;
                    double a = Math.toRadians(f[2]) + raise - (1 - o) * Math.toRadians(70);
                    double du = Math.cos(a), dv = Math.sin(a);
                    double hu = -dv * f[4] / 2, hv = du * f[4] / 2;
                    double lay = f[6] * 0.06 + (glow ? 0.01 : 0);
                    double[][] q = {
                            { qu - hu, qv - hv }, { qu + hu, qv + hv },
                            { qu + du * f[3] + hu, qv + dv * f[3] + hv }, { qu + du * f[3] - hu, qv + dv * f[3] - hv } };
                    float u0 = f[5] * 0.25f, u1 = u0 + 0.25f;
                    float[][] uv = { { u0, 0 }, { u1, 0 }, { u1, 1 }, { u0, 1 } };
                    for (int k = 0; k < 4; k++) {
                        Vec3d pt = root.add(u.multiply(q[k][0])).add(v.multiply(q[k][1])).add(n.multiply(lay)).multiply(SCALE / 16.0);
                        vc.vertex(mat, (float) pt.x, (float) pt.y, (float) pt.z).color(255, 255, 255, 255)
                                .texture(uv[k][0], uv[k][1]).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
                                .light(glow ? 0xF000F0 : light).normal(nm, (float) n.x, (float) n.y, (float) n.z).next();
                    }
                }
            }
        }
    }
}
