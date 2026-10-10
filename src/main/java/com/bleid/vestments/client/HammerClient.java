package com.bleid.vestments.client;

import com.bleid.vestments.paladin.PaladinSwordItem;
import com.bleid.vestments.paladin.combat.CombatServer;
import com.bleid.vestments.paladin.combat.WeaponSkills;
import com.bleid.vestments.service.RankView;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * «Молот правосудия» на клиенте: брошенный молот летит настоящей моделью (вращаясь) и пропадает
 * из руки бросившего, пока не вернётся; цель броска под прицелом подсвечивается золотым контуром.
 */
@Environment(EnvType.CLIENT)
public final class HammerClient {
    private static final class Flight {
        Vec3d pos, prev;
        long last;
        boolean back;
        int age;
    }

    private static final Map<Integer, Flight> FLYING = new HashMap<>();
    private static int target = -1;
    private static Item hammer;

    private HammerClient() { }

    private static Item hammer() {
        if (hammer == null) hammer = Registries.ITEM.get(new Identifier("vestments", "warhammer"));
        return hammer;
    }

    /** Позиция летящего молота с сервера (каждый такт). */
    public static void update(Entity owner, Vec3d pos, boolean back, int age) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (owner == null || mc.world == null) return;
        Flight f = FLYING.computeIfAbsent(owner.getId(), k -> new Flight());
        f.prev = f.pos == null ? pos : f.pos;
        f.pos = pos;
        f.back = back;
        f.age = age;
        f.last = mc.world.getTime();
    }

    public static void end(Entity owner) {
        if (owner != null) FLYING.remove(owner.getId());
    }

    /** Молот этого игрока сейчас в полёте (в руке его не рисуем). */
    public static boolean flying(Entity owner) {
        return owner != null && FLYING.containsKey(owner.getId());
    }

    public static boolean isHammer(ItemStack s) {
        return !s.isEmpty() && s.getItem() == hammer();
    }

    /** Подсвеченная цель броска (id) или -1. */
    public static int target() {
        return target;
    }

    public static boolean highlighted(Entity e) {
        return target >= 0 && e.getId() == target;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.world == null) { FLYING.clear(); target = -1; return; }
            long now = mc.world.getTime();
            FLYING.values().removeIf(f -> now - f.last > 12);
            target = -1;
            ClientPlayerEntity me = mc.player;
            if (me == null || !RankView.clientPaladin() || flying(me)) return;
            ItemStack st = me.getMainHandStack();
            if (!isHammer(st) || !(st.getItem() instanceof PaladinSwordItem w)) return;
            if (RankView.clientPaladinRank() < w.rank || me.getItemCooldownManager().isCoolingDown(w)) return;
            LivingEntity t = WeaponSkills.aimed(me, mc.world.getEntitiesByClass(LivingEntity.class, me.getBoundingBox().expand(22),
                    x -> CombatServer.canHit(me, x)));
            if (t != null) target = t.getId();
        });
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (FLYING.isEmpty() || mc.world == null || ctx.consumers() == null) return;
            float td = ctx.tickDelta();
            Vec3d cam = ctx.camera().getPos();
            MatrixStack m = ctx.matrixStack();
            ItemStack stack = new ItemStack(hammer());
            for (Flight f : FLYING.values()) {
                Vec3d p = f.prev.lerp(f.pos, td);
                Vec3d d = f.pos.subtract(f.prev);
                float yaw = d.lengthSquared() > 1e-6 ? (float) Math.toDegrees(Math.atan2(d.x, d.z)) : 0f;
                int light = WorldRenderer.getLightmapCoordinates(mc.world, BlockPos.ofFloored(p));
                m.push();
                m.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
                // молот кувыркается в полёте вокруг поперечной оси
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees((f.age + td) * (f.back ? -38f : 38f)));
                m.scale(1.1f, 1.1f, 1.1f);
                m.translate(0, -0.25, 0);
                mc.getItemRenderer().renderItem(stack, ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV, m,
                        ctx.consumers(), mc.world, 0);
                m.pop();
            }
        });
    }
}
