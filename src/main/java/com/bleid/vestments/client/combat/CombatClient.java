package com.bleid.vestments.client.combat;

import com.bleid.vestments.paladin.PaladinSwordItem;
import com.bleid.vestments.paladin.combat.Clip;
import com.bleid.vestments.paladin.combat.CombatServer;
import com.bleid.vestments.paladin.combat.Moveset;
import com.bleid.vestments.paladin.combat.Movesets;
import com.bleid.vestments.service.RankView;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Клиент боёвки паладина: ЛКМ с мечом паладина запускает приём (сразу, без ожидания сервера),
 * нажатия во время удара запоминаются на 0.4 с (буфер ввода), серия сбрасывается после паузы.
 * Здесь же — шаги вперёд, прыжок и падение приёма, «заморозка кадра» при попадании.
 */
@Environment(EnvType.CLIENT)
public final class CombatClient {
    public static final class Playback {
        public final Clip clip;
        public final Moveset ms;
        public float t, prevT;
        public int hitstop;
        /** Меч, который убирается в ножны (в руке его уже нет). */
        public ItemStack stack = ItemStack.EMPTY;
        Playback(Clip c, Moveset m) { clip = c; ms = m; }
    }

    private static final Map<Integer, Playback> PLAY = new HashMap<>();
    /** Что было в руках у игроков на прошлом такте — чтобы заметить, что меч/щит только что взяли. */
    private static final Map<Integer, net.minecraft.item.Item[]> HELD = new HashMap<>();
    private static KeyBinding skillKey;

    public static KeyBinding skillKey() {
        return skillKey;
    }
    private static int combo, idle, sprintTicks, buffer = -1;
    private static int chainKind;

    private static Clip[] chain(Moveset ms, int k) {
        return k == 2 ? ms.comboC : k == 1 ? ms.comboB : ms.combo;
    }

    private CombatClient() { }

    public static void register() {
        skillKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.vestments.paladin_skill",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.vestments"));
        ClientPlayNetworking.registerGlobalReceiver(CombatServer.PLAY, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            String clipId = buf.readString();
            client.execute(() -> {
                Clip c = Movesets.CLIPS.get(clipId);
                if (c == null || client.world == null) return;
                Moveset m = Movesets.of(clipId.substring(0, clipId.indexOf('/')));
                boolean local = client.player != null && client.player.getId() == id;
                if (local && !c.skill) return;            // свои удары уже играем сами
                PLAY.put(id, new Playback(c, m));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(CombatServer.HIT, (client, handler, buf, sender) -> {
            int stop = buf.readVarInt();
            float shake = buf.readFloat();
            client.execute(() -> {
                if (client.player == null) return;
                Playback p = PLAY.get(client.player.getId());
                if (p != null) {
                    p.hitstop = Math.max(p.hitstop, stop);
                    kickFor(p);
                }
                CameraShake.add(shake);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> { PLAY.clear(); HELD.clear(); });
        ClientTickEvents.END_CLIENT_TICK.register(CombatClient::tick);
    }

    /** Толчок камеры по направлению текущего взмаха — удар «ощущается» рукой. */
    private static void kickFor(Playback p) {
        com.bleid.vestments.paladin.combat.Strike cur = null;
        for (com.bleid.vestments.paladin.combat.Strike k : p.clip.strikes) {
            cur = k;
            if (p.t < k.contact + 0.05f) break;
        }
        if (cur == null) return;
        float power = cur.heavy ? 1.6f : 1f;
        if (cur.thrust()) {
            CameraShake.kick(0f, 0.6f * power, 0f);
            return;
        }
        // направление движения клинка в момент контакта: (вправо, вверх)
        double a = Math.toRadians(cur.a1 - cur.a0 > 0 ? 1 : -1), r = Math.toRadians(cur.roll);
        double mid = Math.toRadians((cur.a0 + cur.a1) * 0.5);
        double right = Math.cos(mid) * Math.cos(r) * Math.signum(a), up = Math.cos(mid) * Math.sin(r) * Math.signum(a);
        CameraShake.kick((float) (right * 1.3 * power), (float) (-up * 1.1 * power), (float) (right * 1.6 * power));
    }

    /** Проиграть приём у своего игрока (уворот и т.п.); сбрасывает серию ударов. */
    public static void playLocal(net.minecraft.entity.player.PlayerEntity p, Clip c) {
        Moveset ms = Movesets.of("junior_recruit_sword");
        PLAY.put(p.getId(), new Playback(c, ms));
        combo = 0;
        buffer = -1;
    }

    public static Map<Integer, Playback> all() {
        return PLAY;
    }

    public static Playback get(Entity e) {
        return e == null ? null : PLAY.get(e.getId());
    }

    /** Время приёма с учётом доли тика (во время заморозки кадра — стоит). */
    public static float time(Playback p, float tickDelta) {
        if (p.hitstop > 0) return p.t;
        return Math.min(p.t + tickDelta * 0.05f, p.clip.length);
    }

    /** Множитель движения своего игрока: во время удара ноги «заняты». */
    public static float moveFactor() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 1f;
        Playback p = PLAY.get(mc.player.getId());
        if (p == null || p.t >= p.clip.moveLockUntil()) return 1f;
        return 0.2f;
    }

    private static boolean holdsSword(ClientPlayerEntity p) {
        return p != null && RankView.clientPaladin() && p.getMainHandStack().getItem() instanceof PaladinSwordItem;
    }

    /** Вызывается вместо обычного удара (миксин MinecraftClient.doAttack). true — удар наш. */
    public static boolean onAttack(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (!holdsSword(p) || p.isSpectator()) return false;
        if (p.isUsingItem()) return true;
        tryAttack(p);
        return true;
    }

    private static void tryAttack(ClientPlayerEntity p) {
        ItemStack st = p.getMainHandStack();
        if (!(st.getItem() instanceof PaladinSwordItem sword)) return;
        Moveset ms = sword.moveset();
        if (ms == null) return;
        Playback cur = PLAY.get(p.getId());
        if (cur != null && cur.t < cur.clip.cancelTime() - 0.02f) {
            buffer = 8;
            return;
        }
        buffer = -1;
        int kind;
        if (!p.isOnGround() && !p.isTouchingWater() && !p.isClimbing() && !p.getAbilities().flying) {
            kind = 101;
        } else if (p.isSprinting() && sprintTicks >= 8) {
            kind = 100;
            p.setSprinting(false);
        } else {
            // серия выбирается в начале: стоя — основная, вперёд — вторая, вбок/назад — третья
            Clip[] chain = chain(ms, chainKind);
            if (combo >= chain.length) combo = 0;
            if (combo == 0) {
                float fw = p.input.movementForward, sw = p.input.movementSideways;
                chainKind = 0;
                if ((Math.abs(sw) > 0.1f || fw < -0.1f) && ms.comboC.length > 0) chainKind = 2;
                else if (fw > 0.1f && ms.comboB.length > 0) chainKind = 1;
                chain = chain(ms, chainKind);
            }
            kind = (chainKind == 2 ? 70 : chainKind == 1 ? 50 : 0) + combo++;
        }
        Clip c = ms.byKind(kind);
        if (c == null) return;
        PLAY.put(p.getId(), new Playback(c, ms));
        idle = 0;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(kind);
        ClientPlayNetworking.send(CombatServer.ATTACK, buf);
    }

    private static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            PLAY.clear();
            return;
        }
        if (mc.isPaused()) return;
        ClientPlayerEntity me = mc.player;
        sprintTicks = me.isSprinting() ? sprintTicks + 1 : 0;

        while (skillKey.wasPressed()) {
            if (holdsSword(me) && mc.currentScreen == null && !me.isUsingItem()
                    && !me.getItemCooldownManager().isCoolingDown(me.getMainHandStack().getItem())) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(200);
                ClientPlayNetworking.send(CombatServer.ATTACK, buf);
            }
        }

        watchEquip(mc);

        Playback mine = PLAY.get(me.getId());
        if (mine == null) {
            if (++idle > 16) combo = 0;
        }
        if (buffer >= 0) {
            buffer--;
            Playback cur = PLAY.get(me.getId());
            if (holdsSword(me) && (cur == null || cur.t >= cur.clip.cancelTime() - 0.02f)) tryAttack(me);
        }

        for (Iterator<Map.Entry<Integer, Playback>> it = PLAY.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Playback> e = it.next();
            Playback p = e.getValue();
            Entity ent = mc.world.getEntityById(e.getKey());
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) { it.remove(); continue; }
            p.prevT = p.t;
            if (p.hitstop > 0) { p.hitstop--; }
            else p.t += 0.05f;
            // во время приёма тело смотрит туда же, куда голова
            le.setBodyYaw(le.getHeadYaw());
            le.prevBodyYaw = le.prevHeadYaw;
            if (le == me) motion(me, p);
            if (p.clip.sheath) drawCues(mc, le, p);
            if (p.t >= p.clip.length) it.remove();
        }
        SwordTrails.tick(mc);
    }

    /** Взяли меч паладина — он красиво выхватывается; надели щит — щит выносится в стойку. */
    private static void watchEquip(MinecraftClient mc) {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (net.minecraft.entity.player.PlayerEntity pl : mc.world.getPlayers()) {
            seen.add(pl.getId());
            net.minecraft.item.Item main = pl.getMainHandStack().getItem(), off = pl.getOffHandStack().getItem();
            net.minecraft.item.Item[] prev = HELD.put(pl.getId(), new net.minecraft.item.Item[] { main, off });
            if (prev == null || pl.isSpectator()) continue;
            Playback cur = PLAY.get(pl.getId());
            boolean free = cur == null || cur.clip.visualOnly;
            if (!free) continue;
            if (main != prev[0] && main instanceof PaladinSwordItem sword && sword.moveset() != null
                    && sword.moveset().draw != null) {
                Moveset ms = sword.moveset();
                PLAY.put(pl.getId(), new Playback(ms.draw, ms));
                sound(mc, pl, net.minecraft.sound.SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0.5f, 1.2f);
            } else if (main != prev[0] && prev[0] instanceof PaladinSwordItem old && old.moveset() != null
                    && old.moveset().sheathe != null && !(main instanceof PaladinSwordItem)) {
                // меч убрали из руки — он плавно возвращается в ножны
                Moveset ms = old.moveset();
                Playback pb = new Playback(ms.sheathe, ms);
                pb.stack = new ItemStack(old);
                PLAY.put(pl.getId(), pb);
                sound(mc, pl, net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, 0.35f, 1.4f);
            } else if (off != prev[1] && off instanceof com.bleid.vestments.paladin.PaladinShieldItem) {
                Moveset ms = main instanceof PaladinSwordItem sw && sw.moveset() != null ? sw.moveset()
                        : Movesets.of("junior_recruit_sword");
                PLAY.put(pl.getId(), new Playback(Movesets.SHIELD_DRAW, ms));
                sound(mc, pl, net.minecraft.sound.SoundEvents.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 0.9f);
                sound(mc, pl, net.minecraft.sound.SoundEvents.ITEM_SHIELD_BLOCK, 0.3f, 1.5f);
            }
        }
        HELD.keySet().retainAll(seen);
    }

    /** Звуки и вспышка выхватывания: хват рукояти, звон выходящего клинка, свист при постановке в стойку. */
    private static void drawCues(MinecraftClient mc, LivingEntity le, Playback p) {
        float L = p.clip.length;
        if (p.clip.reverse) {
            // клинок входит в ножны — шорох; гарда упирается в устье — щелчок
            if (crossed(p, (1f - DrawPose.CLEAR) * L)) {
                sound(mc, le, net.minecraft.sound.SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, 0.5f, 1.3f);
            }
            if (crossed(p, (1f - DrawPose.GRAB) * L - 0.03f)) {
                sound(mc, le, net.minecraft.sound.SoundEvents.ITEM_ARMOR_EQUIP_IRON, 0.65f, 1.25f);
                sound(mc, le, net.minecraft.sound.SoundEvents.BLOCK_CHAIN_PLACE, 0.5f, 1.6f);
            }
            return;
        }
        if (crossed(p, DrawPose.GRAB * L)) {
            sound(mc, le, net.minecraft.sound.SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, 0.5f, 1.6f);
        }
        if (crossed(p, DrawPose.CLEAR * L)) {
            sound(mc, le, net.minecraft.sound.SoundEvents.ITEM_ARMOR_EQUIP_IRON, 0.7f, 1.75f);
            sound(mc, le, net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.9f);
            sound(mc, le, net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f + 0.1f * p.ms.tier, 1.7f);
            // блик на клинке у груди
            float yaw = (float) Math.toRadians(le.getBodyYaw());
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);
            double x = le.getX() + fx * 0.45 - fz * 0.05, y = le.getY() + le.getHeight() * 0.62, z = le.getZ() + fz * 0.45 + fx * 0.05;
            float[] c = p.ms.trailCore;
            com.bleid.vestments.client.fx.FxSystem.spawn(com.bleid.vestments.client.fx.FxSystem.FLARE, x, y, z)
                    .size(0.25f + 0.06f * p.ms.tier, 0f).life(7).color(c[0], c[1], c[2]).rot((float) Math.random() * 6f, 0.2f);
            for (int i = 0; i < 3 + p.ms.tier; i++) {
                com.bleid.vestments.client.fx.FxSystem.spawn(com.bleid.vestments.client.fx.FxSystem.GLINT,
                                x + (Math.random() - 0.5) * 0.4, y + Math.random() * 0.5, z + (Math.random() - 0.5) * 0.4)
                        .vel((Math.random() - 0.5) * 0.03, 0.01 + Math.random() * 0.02, (Math.random() - 0.5) * 0.03)
                        .size(0.07f + (float) Math.random() * 0.05f, 0f).life(10 + (int) (Math.random() * 8)).color(c[0], c[1], c[2]);
            }
        }
        if (crossed(p, 0.74f * L)) {
            sound(mc, le, net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, 0.5f, 1.25f);
        }
    }

    private static boolean crossed(Playback p, float at) {
        return p.prevT < at && p.t >= at;
    }

    private static void sound(MinecraftClient mc, Entity e, net.minecraft.sound.SoundEvent s, float vol, float pitch) {
        mc.world.playSound(e.getX(), e.getY() + 1, e.getZ(), s, net.minecraft.sound.SoundCategory.PLAYERS, vol, pitch, false);
    }

    /** Шаги вперёд (не проскакивая цель), прыжок и падение — только для своего игрока. */
    private static void motion(ClientPlayerEntity me, Playback p) {
        if (p.hitstop > 0) return;
        Vec3d fwd = Vec3d.fromPolar(0, me.getYaw());
        for (float[] s : p.clip.steps) {
            float a = Math.max(p.prevT, s[0]), b = Math.min(p.t, s[1]);
            if (b <= a) continue;
            double speed = s[2] * (b - a) / Math.max(0.05, s[1] - s[0]);
            // не налетать на цель
            Box ahead = me.getBoundingBox().stretch(fwd.multiply(1.2));
            boolean blocked = !me.getWorld().getOtherEntities(me, ahead, x -> x instanceof LivingEntity && x.isAlive()).isEmpty();
            if (blocked) speed *= 0.15;
            Vec3d v = me.getVelocity();
            me.setVelocity(fwd.x * speed, v.y, fwd.z * speed);
        }
        Clip c = p.clip;
        if (c.leapAt >= 0 && p.prevT < c.leapAt && p.t >= c.leapAt) {
            Vec3d v = me.getVelocity();
            me.setVelocity(v.x + fwd.x * 0.3, c.leapVy, v.z + fwd.z * 0.3);
        }
        if (c.slamAt >= 0 && p.prevT < c.slamAt && p.t >= c.slamAt) {
            Vec3d v = me.getVelocity();
            me.setVelocity(v.x, c.slamVy, v.z);
        }
    }
}
