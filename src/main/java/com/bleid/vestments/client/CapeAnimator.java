package com.bleid.vestments.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.rpg_foundation.armor_api.client.model.GeoArmorModel;

/**
 * Живой плащ из трёх частей на шарнирах.
 *
 * <p>Верхняя часть «пружиной» с инерцией догоняет нужный наклон (зависит от скорости, падения,
 * приседания, ритма шагов). Средняя и нижняя части — свои пружины помягче, которые догоняют часть
 * над собой с запаздыванием. Отсюда плавный разгон, лёгкий перелёт, мягкое оседание и изгиб ткани.
 * Состояние хранится отдельно для каждой сущности; время берётся реальное, поэтому движение
 * одинаково плавное при любом FPS.
 */
@Environment(EnvType.CLIENT)
public final class CapeAnimator {
    public static final String CAPE_BONE = "vestments_cape";
    public static final String CAPE_MID = "vestments_cape2";
    public static final String CAPE_LOW = "vestments_cape3";

    private static final float BASE_DEG = 8f;          // наклон в покое (как в модели)
    // пружины: жёсткость и затухание (меньше затухание — сильнее покачивание)
    private static final float K_TOP = 38f, C_TOP = 7.5f;
    private static final float K_MID = 26f, C_MID = 5.5f;
    private static final float K_LOW = 18f, C_LOW = 4.2f;

    /** Сущность, броня которой рисуется сейчас (ставит ArmorRenderDispatcherMixin). */
    public static LivingEntity current;

    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();

    private static final class State {
        long lastNanos;
        float top = BASE_DEG, topVel;      // мировой угол верхней части
        float mid = BASE_DEG, midVel;      // мировой угол средней части
        float low = BASE_DEG, lowVel;      // мировой угол нижней части
        float roll, speed;                 // сглаженные боковой наклон и скорость
    }

    private CapeAnimator() { }

    public static void apply(GeoArmorModel model) {
        ModelPart body = model.armorBone("armorBody");
        if (body == null || !body.hasChild(CAPE_BONE)) {
            return; // чужая броня — не трогаем
        }
        ModelPart top = body.getChild(CAPE_BONE);
        ModelPart mid = top.hasChild(CAPE_MID) ? top.getChild(CAPE_MID) : null;
        ModelPart low = mid != null && mid.hasChild(CAPE_LOW) ? mid.getChild(CAPE_LOW) : null;

        LivingEntity e = current;
        if (e == null) {
            setPose(top, mid, low, BASE_DEG, BASE_DEG, BASE_DEG, 0f);
            return;
        }
        State s = STATES.computeIfAbsent(e, k -> new State());
        long now = System.nanoTime();
        float dt = s.lastNanos == 0 ? 0f : (now - s.lastNanos) / 1_000_000_000f;
        s.lastNanos = now;
        dt = MathHelper.clamp(dt, 0f, 0.05f);   // защита от рывков после паузы/лагов

        if (dt > 0f) {
            float td = MinecraftClient.getInstance().getTickDelta();
            double dx = e.getX() - e.prevX;
            double dy = e.getY() - e.prevY;
            double dz = e.getZ() - e.prevZ;
            float yaw = MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
            float forward = (float) (dx * -MathHelper.sin(yaw) + dz * MathHelper.cos(yaw));
            float sideways = (float) (dx * MathHelper.cos(yaw) + dz * MathHelper.sin(yaw));

            // скорость сглаживаем отдельно, чтобы рывки позиции между тиками не передавались плащу
            float a = 1f - (float) Math.exp(-dt * 10f);
            s.speed += (Math.max(0f, forward) - s.speed) * a;
            s.roll += (MathHelper.clamp(sideways * 70f, -10f, 10f) - s.roll) * a;

            float back = MathHelper.clamp(s.speed * 160f, 0f, 48f);              // ходьба ~15°, бег ~40°
            float lift = (float) MathHelper.clamp(-dy * 60.0, 0.0, 30.0);         // при падении подлетает
            float limbPos = e.limbAnimator.getPos(td);
            float limbSpeed = e.limbAnimator.getSpeed(td);
            float sway = MathHelper.sin(limbPos * 0.6662f) * 3.5f * limbSpeed;    // мягкое покачивание шагов
            float sneak = e.isInSneakingPose() ? 14f : 0f;
            float target = BASE_DEG + back + lift + sway + sneak;

            // ткань изгибается: чем сильнее плащ отброшен, тем больше подворачивается низ
            float curl = (s.top - BASE_DEG) * 0.28f;

            // несколько мелких шагов на кадр — устойчиво и плавно при низком FPS
            int steps = Math.max(1, (int) Math.ceil(dt / 0.008f));
            float h = dt / steps;
            for (int i = 0; i < steps; i++) {
                s.topVel += (K_TOP * (target - s.top) - C_TOP * s.topVel) * h;
                s.top += s.topVel * h;
                s.midVel += (K_MID * (s.top + curl - s.mid) - C_MID * s.midVel) * h;
                s.mid += s.midVel * h;
                s.lowVel += (K_LOW * (s.mid + curl - s.low) - C_LOW * s.lowVel) * h;
                s.low += s.lowVel * h;
            }
            s.top = MathHelper.clamp(s.top, -10f, 80f);
            s.mid = MathHelper.clamp(s.mid, s.top - 25f, s.top + 35f);
            s.low = MathHelper.clamp(s.low, s.mid - 25f, s.mid + 35f);
        }
        setPose(top, mid, low, s.top, s.mid, s.low, s.roll);
    }

    /** Части вложены друг в друга, поэтому каждой задаём угол относительно предыдущей. */
    private static void setPose(ModelPart top, ModelPart mid, ModelPart low,
                                float topDeg, float midDeg, float lowDeg, float rollDeg) {
        float r = MathHelper.RADIANS_PER_DEGREE;
        top.pitch = topDeg * r;
        top.roll = rollDeg * r;
        if (mid != null) mid.pitch = (midDeg - topDeg) * r;
        if (low != null) low.pitch = (lowDeg - midDeg) * r;
    }
}
