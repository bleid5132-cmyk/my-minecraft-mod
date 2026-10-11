package com.bleid.vestments.client.boss;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

/** Пружинное сглаживание поз: каждый сустав догоняет целевой угол (полунеявный Эйлер, шаг 5 мс). */
@Environment(EnvType.CLIENT)
final class Springs {
    static final class State {
        float[] x, v;
        long nanos;
        /** Свободные поля для рендеров (например, такт приземления). */
        float mark = -1f;
        int markAttack = -1;
    }

    private final Map<Integer, State> states = new HashMap<>();

    State get(int id) {
        return states.computeIfAbsent(id, k -> new State());
    }

    void forget(int id) {
        states.remove(id);
    }

    void retain(java.util.function.IntPredicate alive) {
        states.keySet().removeIf(id -> !alive.test(id));
    }

    /** Продвинуть пружины к target и записать результат обратно в target. */
    static void step(State s, float[] target, float[] omega, float zeta) {
        if (s.x == null || s.x.length != target.length) {
            s.x = target.clone();
            s.v = new float[target.length];
        }
        long now = System.nanoTime();
        float dts = s.nanos == 0 ? 0f : Math.min(0.1f, (now - s.nanos) / 1e9f);
        s.nanos = now;
        if (MinecraftClient.getInstance().isPaused()) dts = 0f;
        int steps = Math.max(1, (int) Math.ceil(dts / 0.005f));
        float h = dts / steps;
        for (int si = 0; si < steps && dts > 0; si++) {
            for (int i = 0; i < target.length; i++) {
                float w = omega[Math.min(i, omega.length - 1)];
                float acc = w * w * (target[i] - s.x[i]) - 2f * zeta * w * s.v[i];
                s.v[i] += acc * h;
                s.x[i] += s.v[i] * h;
            }
        }
        System.arraycopy(s.x, 0, target, 0, target.length);
    }
}
