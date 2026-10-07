package com.bleid.vestments.classes;

import com.bleid.vestments.Vestments;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * Классы игроков. При первом входе в мир сервер открывает игроку экран выбора класса,
 * выбор сохраняется в данных мира. Пока доступен один класс — «Священник».
 */
public final class PlayerClasses {
    public static final Identifier OPEN_SELECT = new Identifier(Vestments.MOD_ID, "open_class_select");
    public static final Identifier CHOOSE = new Identifier(Vestments.MOD_ID, "choose_class");

    /** Доступные классы (id). Порядок = порядок на экране выбора. */
    public static final List<String> CLASSES = List.of("priest");

    private PlayerClasses() { }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (get(server, player.getUuid()) == null) {
                ServerPlayNetworking.send(player, OPEN_SELECT, PacketByteBufs.empty());
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(CHOOSE, (server, player, handler, buf, responseSender) -> {
            String chosen = buf.readString(32);
            server.execute(() -> {
                if (!CLASSES.contains(chosen) || get(server, player.getUuid()) != null) return;
                set(server, player.getUuid(), chosen);
                // Стартовый набор пока отключён: giveKit(player, chosen);
                player.sendMessage(Text.translatable("message.vestments.class_chosen",
                        Text.translatable("class.vestments." + chosen).formatted(Formatting.GOLD)), false);
                com.bleid.vestments.service.ServicePoints.sync(player);
            });
        });
    }

    private static void giveKit(ServerPlayerEntity player, String classId) {
        if (!"priest".equals(classId)) return;
        equipOrGive(player, EquipmentSlot.HEAD, Vestments.COLLAR);
        equipOrGive(player, EquipmentSlot.CHEST, Vestments.PHELONION);
        equipOrGive(player, EquipmentSlot.LEGS, Vestments.PODRIZNIK);
        equipOrGive(player, EquipmentSlot.FEET, Vestments.BOOTS);
        player.giveItemStack(new ItemStack(Vestments.STAFF_OF_LIGHT));
    }

    private static void equipOrGive(ServerPlayerEntity player, EquipmentSlot slot, Item item) {
        if (player.getEquippedStack(slot).isEmpty()) {
            player.equipStack(slot, new ItemStack(item));
        } else {
            player.giveItemStack(new ItemStack(item));
        }
    }

    public static String get(MinecraftServer server, UUID player) {
        return state(server).classes.get(player);
    }

    private static void set(MinecraftServer server, UUID player, String classId) {
        State s = state(server);
        s.classes.put(player, classId);
        s.markDirty();
    }

    private static State state(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager()
                .getOrCreate(State::fromNbt, State::new, "vestments_classes");
    }

    /** Классы игроков этого мира, сохраняются вместе с миром. */
    public static final class State extends PersistentState {
        final Map<UUID, String> classes = new HashMap<>();

        static State fromNbt(NbtCompound nbt) {
            State s = new State();
            NbtCompound map = nbt.getCompound("classes");
            for (String key : map.getKeys()) {
                s.classes.put(UUID.fromString(key), map.getString(key));
            }
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            NbtCompound map = new NbtCompound();
            classes.forEach((uuid, id) -> map.putString(uuid.toString(), id));
            nbt.put("classes", map);
            return nbt;
        }
    }
}
