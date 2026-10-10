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
            Matrix4f mat = matrices.peek().getPositionMatrix();
            VertexConsumer vc = vcp.getBuffer(FxSystem.layer());
            float flap = MathHelper.sin(age * 0.12f) * 9f;
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 0; i < 8; i++) {
                    // перья веером: от почти горизонтальных до поднятых вверх; верхние длиннее
                    float a = (float) Math.toRadians(-18 + i * 13 + flap * (0.6f + i * 0.06f)) * o + (float) Math.toRadians(-60) * (1 - o);
                    float len = (11f + i * 1.3f) * (0.4f + 0.6f * o) / 16f;
                    float w = (1.6f + i * 0.12f) / 16f;
                    Vec3d root = new Vec3d(-side * 1.2 / 16, 3.0 / 16, 2.4 / 16);
                    Vec3d dir = new Vec3d(-side * Math.cos(a), -Math.sin(a), 0.45).normalize();
                    Vec3d wv = dir.crossProduct(new Vec3d(0, 0, 1)).normalize().multiply(w);
                    Vec3d tip = root.add(dir.multiply(len));
                    float br = o * (0.55f + 0.05f * i);
                    float[] rgb = { br, br * 0.92f, br * 0.7f, br, br * 0.92f, br * 0.7f,
                            br * 0.9f, br * 0.85f, br * 0.6f, br * 0.9f, br * 0.85f, br * 0.6f };
                    FxSystem.drawQuad(vc, mat, FxSystem.FEATHER, root.subtract(wv), root.add(wv), tip.add(wv.multiply(1.5)),
                            tip.subtract(wv.multiply(1.5)), new float[] { 0, 1, 1, 1, 1, 0, 0, 0 }, rgb);
                }
            }
            matrices.pop();
        }
    }
}
