package com.bleid.vestments.client;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.patriarch.SoulAllies;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.Entity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

/**
 * Клиентская часть союзных душ: какие сущности — души (сервер шлёт их id),
 * и голограммная текстура: обесцвеченная в серо-голубой, полупрозрачная, с полосами развёртки.
 */
@Environment(EnvType.CLIENT)
public final class SoulAllyClient {
    private static volatile Set<Integer> ids = Set.of();
    private static final Map<Identifier, Identifier> HOLO = new HashMap<>();
    private static final Identifier NONE = new Identifier(Vestments.MOD_ID, "none");

    private SoulAllyClient() { }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SoulAllies.SOUL_IDS, (client, handler, buf, sender) -> {
            int n = buf.readVarInt();
            Set<Integer> s = new HashSet<>();
            for (int i = 0; i < n; i++) s.add(buf.readVarInt());
            client.execute(() -> ids = s);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> ids = Set.of());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || ids.isEmpty() || client.isPaused()) return;
            for (int id : ids) {
                Entity e = client.world.getEntityById(id);
                if (e == null || client.world.random.nextInt(3) != 0) continue;
                client.world.addParticle(new DustParticleEffect(new Vector3f(0.62f, 0.78f, 0.95f), 0.6f),
                        e.getX() + (client.world.random.nextDouble() - 0.5) * e.getWidth(),
                        e.getY() + client.world.random.nextDouble() * e.getHeight(),
                        e.getZ() + (client.world.random.nextDouble() - 0.5) * e.getWidth(), 0, 0.03, 0);
            }
        });
    }

    public static boolean isSoul(Entity e) {
        return !ids.isEmpty() && ids.contains(e.getId());
    }

    /** Голограммная версия текстуры моба (кэшируется). */
    public static Identifier hologram(Identifier src) {
        Identifier h = HOLO.get(src);
        if (h == null) {
            h = build(src);
            HOLO.put(src, h == null ? NONE : h);
        }
        return h == NONE ? null : h;
    }

    /** Пиксель голограммы (ABGR): яркий серо-голубой, полупрозрачный, каждая 4-я строка — светлая полоса развёртки. */
    public static int holoPixel(int abgr, int y) {
        int a = (abgr >>> 24) & 0xFF;
        if (a == 0) return 0;
        int r = abgr & 0xFF, g = (abgr >>> 8) & 0xFF, b = (abgr >>> 16) & 0xFF;
        boolean scan = (y % 4) == 0;
        float l = (0.3f * r + 0.59f * g + 0.11f * b) / 255f;
        l = 0.58f + l * 0.42f;
        if (scan) l = Math.min(1f, l + 0.12f);
        int nr = (int) (l * 190), ng = (int) (l * 225), nb = (int) (l * 255);
        int na = Math.min(a, scan ? 145 : 105);
        return (na << 24) | (nb << 16) | (ng << 8) | nr;
    }

    private static Identifier build(Identifier src) {
        MinecraftClient client = MinecraftClient.getInstance();
        Optional<Resource> res = client.getResourceManager().getResource(src);
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().getInputStream(); NativeImage base = NativeImage.read(in)) {
            int w = base.getWidth(), hgt = base.getHeight();
            NativeImage img = new NativeImage(w, hgt, true);
            for (int y = 0; y < hgt; y++)
                for (int x = 0; x < w; x++) img.setColor(x, y, holoPixel(base.getColor(x, y), y));
            Identifier id = new Identifier(Vestments.MOD_ID, "holo/" + src.getNamespace() + "/" + src.getPath().replace('/', '_'));
            client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
            return id;
        } catch (Exception e) {
            return null;
        }
    }
}
