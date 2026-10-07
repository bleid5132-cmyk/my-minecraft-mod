package com.bleid.vestments.weapon;

import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/** Способность оружия на ПКМ. */
public interface WeaponAbility {
    /** Ключ названия способности (например «Каждение»). */
    String nameKey();

    /** Строки описания для подсказки. */
    List<Text> describe();

    /** Сработать (сервер). power — множитель силы по сану. */
    void activate(ServerWorld world, ServerPlayerEntity player, float power);
}
