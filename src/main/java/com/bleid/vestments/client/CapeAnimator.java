package com.bleid.vestments.client;

import java.util.ArrayList;
import java.util.List;
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
 * Живой плащ из 10 частей на шарнирах (кости vestments_cape, vestments_cape2 … vestments_cape10).
 *
 * <p>Верхняя часть «пружиной» с инерцией догоняет нужный наклон (скорость, падение, приседание,
 * ритм шагов). Каждая следующая часть — своя пружина, мягче предыдущей, которая догоняет часть над
 * собой с запаздыванием. Получается плавный разгон, лёгкий перелёт, волна по ткани и изгиб.
 * Состояние своё у каждой сущности; время реальное, поэтому плавно при любом FPS.
 */
@Environment(EnvType.CLIENT)
public final class CapeAnimator {
    public static final String CAPE_BONE = "vestments_cape";
    private static final int MAX_SEGMENTS = 10;

    private static final float BASE_DEG = 8f;        // наклон в покое (как в модели)
    private static final float K_TOP = 38f;          // жёсткость верхней части
    private static final float K_BOTTOM = 14f;       // жёсткость самой нижней части
    private static final float DAMPING_RATIO = 0.55f; // меньше — сильнее покачивание
    private static final float CURL_TOTAL = 0.30f;   // насколько низ подворачивается при отлёте
    private static final float MAX_BEND = 9f;        // предельный изгиб в одном шарнире, градусы
    // «дыхание» ткани в покое: бегущая вниз волна
    private static final float IDLE_WAVE_TOP = 0.3f;   // амплитуда у верха, градусы
    private static final float IDLE_WAVE_BOTTOM = 1.8f; // амплитуда у низа, градусы
    private static final float IDLE_WAVE_SPEED = 1.7f;  // радиан в секунду
    private static final float IDLE_WAVE_PHASE = 0.55f; // сдвиг фазы между соседними частями
    // прыжок
    private static final float JUMP_DIP = 4f;          // на взлёте плащ прижимается, градусы
    private static final float FALL_LIFT = 10f;        // в падении всплывает, градусы (максимум)
    private static final float LAND_KICK = 35f;        // толчок при приземлении, град/с (максимум)
    private static final float TAKEOFF_KICK = -15f;    // толчок в момент отрыва, град/с

    /** Сущность, броня которой рисуется сейчас (ставит ArmorRenderDispatcherMixin). */
    public static LivingEntity current;

    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();

    private static final class State {
        long lastNanos;
        final float[] angle = new float[MAX_SEGMENTS];  // мировые углы частей
        final float[] vel = new float[MAX_SEGMENTS];
        float roll, speed;
        float vy, minVy, time;   // сглаженная вертикальная скорость, самая быстрая в падении, время
        float phase = Float.NaN; // фаза волны ткани
        boolean wasOnGround = true;
        State() { java.util.Arrays.fill(angle, BASE_DEG); }
    }

    private CapeAnimator() { }

    /** Цепочка частей плаща сверху вниз. */
    private static List<ModelPart> chain(ModelPart body) {
        List<ModelPart> parts = new ArrayList<>(MAX_SEGMENTS);
        if (!body.hasChild(CAPE_BONE)) return parts;
        ModelPart part = body.getChild(CAPE_BONE);
        parts.add(part);
        for (int i = 2; i <= MAX_SEGMENTS; i++) {
            String name = CAPE_BONE + i;
            if (!part.hasChild(name)) break;
            part = part.getChild(name);
            parts.add(part);
        }
        return parts;
    }

    public static void apply(GeoArmorModel model) {
        ModelPart body = model.armorBone("armorBody");
        if (body == null) return;
        List<ModelPart> parts = chain(body);
        int n = parts.size();
        if (n == 0) return; // чужая броня — не трогаем

        LivingEntity e = current;
        if (e == null) {
            parts.get(0).pitch = BASE_DEG * MathHelper.RADIANS_PER_DEGREE;
            parts.get(0).roll = 0f;
            for (int i = 1; i < n; i++) parts.get(i).pitch = 0f;
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

            float a = 1f - (float) Math.exp(-dt * 10f);  // сглаживание скорости
            s.speed += (Math.max(0f, forward) - s.speed) * a;
            s.roll += (MathHelper.clamp(sideways * 70f, -10f, 10f) - s.roll) * a;
            s.vy += ((float) dy - s.vy) * (1f - (float) Math.exp(-dt * 14f));
            s.time += dt;
            if (Float.isNaN(s.phase)) s.phase = e.getId() * 1.7f;
            // темп волны слегка «гуляет», как от сквозняка
            float breeze = 0.75f + 0.25f * MathHelper.sin(s.time * 0.37f + e.getId());
            s.phase += dt * IDLE_WAVE_SPEED * breeze;

            // прыжок: отрыв от земли — плащ по инерции отстаёт вниз; приземление — мягкий подскок
            boolean onGround = e.isOnGround();
            float kick = 0f;
            if (s.wasOnGround && !onGround && dy > 0.05) {
                kick = TAKEOFF_KICK;                           // толчок вниз в момент отрыва
                s.minVy = 0f;
            }
            if (!onGround) s.minVy = Math.min(s.minVy, s.vy);
            if (!s.wasOnGround && onGround) {
                kick = MathHelper.clamp(-s.minVy * 200f, 0f, LAND_KICK);
                s.minVy = 0f;
            }
            s.wasOnGround = onGround;

            float back = MathHelper.clamp(s.speed * 160f, 0f, 48f);
            float lift = MathHelper.clamp(-s.vy * 22f, 0f, FALL_LIFT);           // в падении всплывает
            float dip = MathHelper.clamp(s.vy * 22f, 0f, JUMP_DIP);              // на взлёте прижимается
            float limbPos = e.limbAnimator.getPos(td);
            float limbSpeed = e.limbAnimator.getSpeed(td);
            float sway = MathHelper.sin(limbPos * 0.6662f) * 3.5f * limbSpeed;
            float sneak = e.isInSneakingPose() ? 14f : 0f;
            float target = BASE_DEG + back + lift - dip + sway + sneak;


            // подворот низа распределяем по всем шарнирам поровну
            float curlPerJoint = n > 1 ? (s.angle[0] - BASE_DEG) * CURL_TOTAL / (n - 1) : 0f;

            if (kick != 0f) {
                for (int i = 0; i < n; i++) {
                    float t = n > 1 ? (float) i / (n - 1) : 0f;
                    s.vel[i] += kick * (0.35f + 0.65f * t);    // низ подпрыгивает сильнее верха
                }
            }

            int steps = Math.max(1, (int) Math.ceil(dt / 0.006f));
            float h = dt / steps;
            for (int step = 0; step < steps; step++) {
                for (int i = 0; i < n; i++) {
                    float t = n > 1 ? (float) i / (n - 1) : 0f;
                    float k = MathHelper.lerp(t, K_TOP, K_BOTTOM);
                    float c = 2f * DAMPING_RATIO * MathHelper.sqrt(k);
                    float goal = i == 0 ? target : s.angle[i - 1] + curlPerJoint;
                    s.vel[i] += (k * (goal - s.angle[i]) - c * s.vel[i]) * h;
                    s.angle[i] += s.vel[i] * h;
                }
            }
            s.angle[0] = MathHelper.clamp(s.angle[0], -10f, 80f);
            for (int i = 1; i < n; i++) {
                s.angle[i] = MathHelper.clamp(s.angle[i], s.angle[i - 1] - MAX_BEND, s.angle[i - 1] + MAX_BEND);
            }
        }

        // еле заметная бегущая волна ткани поверх физики: амплитуда ровно заданная, без раскачки.
        // На ходу затихает (движения хватает от шагов); у каждой сущности своя фаза.
        float calm = 1f - MathHelper.clamp(s.speed * 6f, 0f, 0.7f);
        float phase0 = Float.isNaN(s.phase) ? 0f : s.phase;
        float[] shown = new float[n];
        for (int i = 0; i < n; i++) {
            float t = n > 1 ? (float) i / (n - 1) : 0f;
            float wave = MathHelper.lerp(t, IDLE_WAVE_TOP, IDLE_WAVE_BOTTOM) * calm
                    * MathHelper.sin(phase0 - i * IDLE_WAVE_PHASE);
            shown[i] = s.angle[i] + wave;
        }

        // части вложены друг в друга: каждой задаём угол относительно предыдущей
        float r = MathHelper.RADIANS_PER_DEGREE;
        parts.get(0).pitch = shown[0] * r;
        parts.get(0).roll = s.roll * r;
        for (int i = 1; i < n; i++) {
            parts.get(i).pitch = (shown[i] - shown[i - 1]) * r;
        }
    }
}
