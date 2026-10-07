package com.bleid.vestments.weapon;

import com.bleid.vestments.gear.GearSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * «Каждение»: вокруг священника на несколько секунд встаёт облако ладана, которое идёт вместе с ним.
 * Каждую секунду лечит игроков, мирных и нейтральных мобов; враждебных замедляет (и ослабляет),
 * нежить в дыму может получать урон.
 */
public record IncenseAbility(double radius, int seconds, float healPerSec, int slowAmp, boolean weakness,
                             float undeadDps) implements WeaponAbility {
    @Override
    public String nameKey() {
        return "ability.vestments.incense";
    }

    @Override
    public List<Text> describe() {
        List<Text> l = new ArrayList<>();
        l.add(Text.translatable("tooltip.vestments.incense.heal", seconds, GearSet.num(radius), GearSet.num(healPerSec / 2.0))
                .formatted(Formatting.GRAY));
        String debuff = slowAmp >= 0
                ? (weakness ? "tooltip.vestments.incense.slow_weak" : "tooltip.vestments.incense.slow") : null;
        if (debuff != null) {
            l.add(Text.translatable(debuff, slowAmp + 1).formatted(Formatting.GRAY));
        }
        if (undeadDps > 0) {
            l.add(Text.translatable("tooltip.vestments.incense.undead", GearSet.num(undeadDps / 2.0)).formatted(Formatting.GRAY));
        }
        return l;
    }

    @Override
    public void activate(ServerWorld world, ServerPlayerEntity player, float power) {
        IncenseClouds.start(player, this, power);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_CHAIN_PLACE,
                SoundCategory.PLAYERS, 1.0f, 1.3f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.PLAYERS, 1.2f, 1.6f);
    }
}
