package com.bleid.vestments.church;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.village.VillagerType;

/** Материалы церкви в стиле деревни (по типу жителей биома). */
public record ChurchStyle(Block foundation, Block base, Block wall, Block pillar, Block floor, Block roofStairs,
                          Block roofSlab, Block roofBlock, Block door, Block fence, Block dome, Block drum,
                          Block mossyBase, Block crackedWall) {

    public static ChurchStyle of(VillagerType type) {
        if (type == VillagerType.DESERT) {
            return new ChurchStyle(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_SANDSTONE,
                    Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE,
                    Blocks.JUNGLE_DOOR, Blocks.JUNGLE_FENCE, Blocks.HONEYCOMB_BLOCK, Blocks.CUT_SANDSTONE,
                    Blocks.SANDSTONE, Blocks.SANDSTONE);
        }
        if (type == VillagerType.SAVANNA) {
            return new ChurchStyle(Blocks.COBBLESTONE, Blocks.ORANGE_TERRACOTTA, Blocks.ACACIA_PLANKS, Blocks.ACACIA_LOG,
                    Blocks.ACACIA_PLANKS, Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB, Blocks.ACACIA_PLANKS,
                    Blocks.ACACIA_DOOR, Blocks.ACACIA_FENCE, Blocks.HONEYCOMB_BLOCK, Blocks.WHITE_TERRACOTTA,
                    Blocks.MOSSY_COBBLESTONE, Blocks.STRIPPED_ACACIA_WOOD);
        }
        if (type == VillagerType.TAIGA) {
            return new ChurchStyle(Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG,
                    Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS,
                    Blocks.SPRUCE_DOOR, Blocks.SPRUCE_FENCE, Blocks.HONEYCOMB_BLOCK, Blocks.SPRUCE_PLANKS,
                    Blocks.MOSSY_COBBLESTONE, Blocks.STRIPPED_SPRUCE_WOOD);
        }
        if (type == VillagerType.SNOW) {
            return new ChurchStyle(Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.WHITE_TERRACOTTA, Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS,
                    Blocks.SPRUCE_DOOR, Blocks.SPRUCE_FENCE, Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.WHITE_TERRACOTTA,
                    Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS);
        }
        // равнины (и всё остальное)
        return new ChurchStyle(Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.OAK_LOG,
                Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.OAK_PLANKS,
                Blocks.OAK_DOOR, Blocks.OAK_FENCE, Blocks.HONEYCOMB_BLOCK, Blocks.WHITE_TERRACOTTA,
                Blocks.MOSSY_COBBLESTONE, Blocks.STRIPPED_OAK_WOOD);
    }
}
