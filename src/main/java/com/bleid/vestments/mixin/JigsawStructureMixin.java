package com.bleid.vestments.mixin;

import java.util.Optional;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolBasedGenerator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.structure.JigsawStructure;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Деревни жителей случайного размера: глубина застройки (сколько «звеньев» улиц и домов
 * нарастает от центра) у каждой деревни своя — от чуть меньше обычной до заметно больше.
 */
@Mixin(JigsawStructure.class)
public abstract class JigsawStructureMixin {
    private static final String GENERATE = "Lnet/minecraft/structure/pool/StructurePoolBasedGenerator;generate(Lnet/minecraft/world/gen/structure/Structure$Context;Lnet/minecraft/registry/entry/RegistryEntry;Ljava/util/Optional;ILnet/minecraft/util/math/BlockPos;ZLjava/util/Optional;I)Ljava/util/Optional;";

    private static boolean isVillage(RegistryEntry<StructurePool> pool) {
        return pool.getKey().map(k -> k.getValue().getPath().startsWith("village/")).orElse(false);
    }

    private static int villageSize(Structure.Context ctx) {
        java.util.Random r = new java.util.Random(ctx.seed() ^ (ctx.chunkPos().toLong() * 0x9E3779B97F4A7C15L));
        int roll = r.nextInt(100);
        if (roll < 20) return 5;          // чуть меньше обычной
        if (roll < 40) return 6;          // обычная
        if (roll < 65) return 7;
        if (roll < 85) return 8;
        return 9;                          // большая
    }

    @ModifyArg(method = "getStructurePosition", at = @At(value = "INVOKE", target = GENERATE), index = 3)
    private int vestments$villageSize(Structure.Context ctx, RegistryEntry<StructurePool> pool, Optional<Identifier> name,
                                      int size, BlockPos pos, boolean expansionHack, Optional<Heightmap.Type> heightmap,
                                      int maxDistance) {
        return isVillage(pool) ? villageSize(ctx) : size;
    }

    @ModifyArg(method = "getStructurePosition", at = @At(value = "INVOKE", target = GENERATE), index = 7)
    private int vestments$villageReach(Structure.Context ctx, RegistryEntry<StructurePool> pool, Optional<Identifier> name,
                                       int size, BlockPos pos, boolean expansionHack, Optional<Heightmap.Type> heightmap,
                                       int maxDistance) {
        return isVillage(pool) && villageSize(ctx) > 6 ? Math.max(maxDistance, 116) : maxDistance;
    }
}
