package com.bleid.vestments.client;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.service.ServicePoints;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

/**
 * Души врагов, убитых священником: полупрозрачная голубовато-серая копия моба, неосязаемая,
 * улетает в небо в случайную сторону и за 4 секунды растворяется по пикселям.
 * Копия рисуется только на клиенте и в мир не добавляется.
 */
@Environment(EnvType.CLIENT)
public final class SoulEffects {
    private static final float LIFE = 4.0f;            // секунд
    private static final int STEPS = 12;               // ступеней растворения
    private static final float ALPHA = 0.62f;
    private static final float FADE_LIFE = 3.0f;      // распад союзной души на месте

    private record Soul(Entity ghost, Vec3d start, Vec3d drift, float yaw, long startNanos, Random rnd, boolean fade) { }

    private static final List<Soul> SOULS = new ArrayList<>();
    /** Текстура моба → ступени растворения (голубовато-серые, с выпадающими пикселями). */
    private static final Map<Identifier, Identifier[]> DISSOLVE = new HashMap<>();
    private static final Map<Identifier, Identifier[]> DISSOLVE_HOLO = new HashMap<>();

    private SoulEffects() { }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ServicePoints.SOUL, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            long seed = buf.readLong();
            client.execute(() -> spawn(client, id, seed, false));
        });
        ClientPlayNetworking.registerGlobalReceiver(com.bleid.vestments.patriarch.SoulAllies.SOUL_FADE, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            long seed = buf.readLong();
            client.execute(() -> spawn(client, id, seed, true));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> SOULS.clear());
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> render(ctx.matrixStack(), ctx.consumers(), ctx.tickDelta()));
    }

    private static void spawn(MinecraftClient client, int id, long seed, boolean fade) {
        if (client.world == null) return;
        Entity dead = client.world.getEntityById(id);
        if (dead == null) return;
        Entity ghost = dead.getType().create(client.world);
        if (ghost == null) return;
        try {
            ghost.readNbt(dead.writeNbt(new NbtCompound()));     // размер слизня, экипировка и т.п.
        } catch (Exception ignored) { }
        ghost.copyPositionAndRotation(dead);
        ghost.setFireTicks(0);
        ghost.setInvisible(false);
        ghost.setCustomNameVisible(false);
        if (ghost instanceof LivingEntity le && dead instanceof LivingEntity ld) {
            le.deathTime = 0;
            le.hurtTime = 0;
            le.setHealth(le.getMaxHealth());
            le.bodyYaw = le.prevBodyYaw = ld.bodyYaw;
            le.headYaw = le.prevHeadYaw = ld.headYaw;
        }
        Random rnd = Random.create(seed);
        double a = rnd.nextDouble() * Math.PI * 2;
        double side = 0.35 + rnd.nextDouble() * 0.35;      // блоков в секунду вбок
        Vec3d drift = fade ? Vec3d.ZERO : new Vec3d(Math.cos(a) * side, 1.4 + rnd.nextDouble() * 0.5, Math.sin(a) * side);
        float yaw = dead instanceof LivingEntity ld ? ld.bodyYaw : dead.getYaw();
        SOULS.add(new Soul(ghost, dead.getPos(), drift, yaw, System.nanoTime(), rnd, fade));
        if (SOULS.size() > 40) SOULS.remove(0);
    }

    private static void render(MatrixStack matrices, VertexConsumerProvider consumers, float tickDelta) {
        if (SOULS.isEmpty() || consumers == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cam = client.gameRenderer.getCamera().getPos();
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        long now = System.nanoTime();
        for (Iterator<Soul> it = SOULS.iterator(); it.hasNext(); ) {
            Soul s = it.next();
            float t = (now - s.startNanos) / 1_000_000_000f;
            float life = s.fade ? FADE_LIFE : LIFE;
            if (t >= life) { it.remove(); continue; }
            // плавный разгон вверх, лёгкое покачивание; союзная душа распадается на месте, не двигаясь
            float ease = t * t * 0.25f + t * 0.6f;
            Vec3d pos = s.fade ? s.start : s.start.add(s.drift.multiply(ease)).add(0, Math.sin(t * 3.0) * 0.05, 0);
            s.ghost.setPosition(pos);
            s.ghost.prevX = pos.x; s.ghost.prevY = pos.y; s.ghost.prevZ = pos.z;

            float delay = s.fade ? 0.1f : 0.6f;
            int step = MathHelper.clamp((int) ((t - delay) / (life - delay) * STEPS), 0, STEPS - 1);
            Identifier tex = dissolveTexture(client, s.ghost, step, s.fade);
            if (tex == null) { it.remove(); continue; }
            float alpha = s.fade ? 1f : ALPHA * (t < 0.3f ? t / 0.3f : 1f);

            GhostProvider ghostConsumers = new GhostProvider(consumers, RenderLayer.getEntityTranslucent(tex), alpha);
            try {
                dispatcher.render(s.ghost, pos.x - cam.x, pos.y - cam.y, pos.z - cam.z, s.yaw, tickDelta,
                        matrices, ghostConsumers, 0xF000F0);
            } catch (Exception e) {
                it.remove();
                continue;
            }
            // осыпающиеся «пиксели»
            if (step > 0 && s.rnd.nextInt(3) == 0) {
                float w = s.ghost.getWidth(), h = s.ghost.getHeight();
                client.particleManager.addParticle(new DustParticleEffect(new Vector3f(0.72f, 0.82f, 0.95f), 0.7f),
                        pos.x + (s.rnd.nextDouble() - 0.5) * w, pos.y + s.rnd.nextDouble() * h,
                        pos.z + (s.rnd.nextDouble() - 0.5) * w, 0, 0.02, 0);
            }
        }
    }

    /** Ступень растворения текстуры моба: обесцвеченная в голубовато-серый, часть пикселей выпала. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Identifier dissolveTexture(MinecraftClient client, Entity ghost, int step, boolean holo) {
        EntityRenderer renderer = client.getEntityRenderDispatcher().getRenderer(ghost);
        Identifier src = renderer.getTexture(ghost);
        Map<Identifier, Identifier[]> cache = holo ? DISSOLVE_HOLO : DISSOLVE;
        Identifier[] steps = cache.get(src);
        if (steps == null) {
            steps = build(client, src, holo);
            cache.put(src, steps);
        }
        return steps.length == 0 ? null : steps[step];
    }

    private static Identifier[] build(MinecraftClient client, Identifier src, boolean holo) {
        Optional<Resource> res = client.getResourceManager().getResource(src);
        if (res.isEmpty()) return new Identifier[0];
        try (InputStream in = res.get().getInputStream(); NativeImage base = NativeImage.read(in)) {
            int w = base.getWidth(), h = base.getHeight();
            Identifier[] out = new Identifier[STEPS];
            for (int k = 0; k < STEPS; k++) {
                float threshold = k / (float) STEPS;
                NativeImage img = new NativeImage(w, h, true);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int abgr = base.getColor(x, y);
                        int a = (abgr >>> 24) & 0xFF;
                        int r = abgr & 0xFF, g = (abgr >>> 8) & 0xFF, b = (abgr >>> 16) & 0xFF;
                        // «шум» по пикселю: какие пиксели выпадают раньше
                        int hsh = (x * 73856093) ^ (y * 19349663);
                        hsh ^= hsh >>> 13;
                        float n = ((hsh * 0x5bd1e995) >>> 8 & 0xFFFF) / 65535f;
                        if (a == 0 || n < threshold) {
                            img.setColor(x, y, 0);
                            continue;
                        }
                        if (holo) { img.setColor(x, y, SoulAllyClient.holoPixel(abgr, y)); continue; }
                        float l = (0.3f * r + 0.59f * g + 0.11f * b) / 255f;
                        l = 0.45f + l * 0.55f;                         // светлее, как дымка
                        int nr = (int) (l * 175), ng = (int) (l * 200), nb = (int) (l * 235);
                        img.setColor(x, y, (a << 24) | (nb << 16) | (ng << 8) | nr);
                    }
                }
                Identifier id = new Identifier(Vestments.MOD_ID, (holo ? "soulholo/" : "soul/") + src.getNamespace() + "/" + src.getPath().replace('/', '_') + "_" + k);
                client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
                out[k] = id;
            }
            return out;
        } catch (Exception e) {
            return new Identifier[0];
        }
    }

    /** Подменяет слой модели на полупрозрачный слой души; прочие слои (тень, глаза, надписи) пропускает. */
    private static final class GhostProvider implements VertexConsumerProvider {
        private final VertexConsumerProvider base;
        private final RenderLayer ghostLayer;
        private final float alpha;
        private RenderLayer first;

        GhostProvider(VertexConsumerProvider base, RenderLayer ghostLayer, float alpha) {
            this.base = base;
            this.ghostLayer = ghostLayer;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            if (first == null) first = layer;
            if (layer != first) return NoopConsumer.INSTANCE;
            return new TintConsumer(base.getBuffer(ghostLayer), alpha);
        }
    }

    private static final class TintConsumer implements VertexConsumer {
        private final VertexConsumer d;
        private final int a;

        TintConsumer(VertexConsumer d, float alpha) {
            this.d = d;
            this.a = (int) (alpha * 255);
        }

        @Override public VertexConsumer vertex(double x, double y, double z) { d.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int alpha) { d.color(255, 255, 255, a); return this; }
        @Override public VertexConsumer texture(float u, float v) { d.texture(u, v); return this; }
        @Override public VertexConsumer overlay(int u, int v) { d.overlay(u, v); return this; }
        @Override public VertexConsumer light(int u, int v) { d.light(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { d.normal(x, y, z); return this; }
        @Override public void next() { d.next(); }
        @Override public void fixedColor(int r, int g, int b, int alpha) { d.fixedColor(255, 255, 255, a); }
        @Override public void unfixColor() { d.unfixColor(); }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v,
                           int overlay, int light, float nx, float ny, float nz) {
            d.vertex(x, y, z, 1f, 1f, 1f, a / 255f, u, v, overlay, 0xF000F0, nx, ny, nz);
        }
    }

    private static final class NoopConsumer implements VertexConsumer {
        static final NoopConsumer INSTANCE = new NoopConsumer();
        @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer texture(float u, float v) { return this; }
        @Override public VertexConsumer overlay(int u, int v) { return this; }
        @Override public VertexConsumer light(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void next() { }
        @Override public void fixedColor(int r, int g, int b, int a) { }
        @Override public void unfixColor() { }
        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v,
                           int overlay, int light, float nx, float ny, float nz) { }
    }
}
