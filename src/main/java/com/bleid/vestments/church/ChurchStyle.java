package com.bleid.vestments.church;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.village.VillagerType;

/** Материалы церкви в стиле деревни (по типу жителей биома). */
public record ChurchStyle(Block foundation, Block base, Block wall, Block pillar, Block trim, Block floor,
                          Block roofStairs, Block roofSlab, Block roofBlock, Block door, Block fence,
                          Block dome, Block domeStairs, Block drum, Block mossyBase, Block crackedWall,
                          Block trimStairs, Block trimSlab) {

    public static ChurchStyle of(VillagerType type) {
        if (type == VillagerType.DESERT) {
            return new ChurchStyle(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_SANDSTONE,
                    Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB,
                    Blocks.SMOOTH_SANDSTONE, Blocks.JUNGLE_DOOR, Blocks.JUNGLE_FENCE,
                    Blocks.WAXED_CUT_COPPER, Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.SMOOTH_SANDSTONE,
                    Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.CUT_SANDSTONE_SLAB);
        }
        if (type == VillagerType.SAVANNA) {
            return new ChurchStyle(Blocks.COBBLESTONE, Blocks.ORANGE_TERRACOTTA, Blocks.ACACIA_PLANKS, Blocks.ACACIA_LOG,
                    Blocks.STRIPPED_ACACIA_WOOD, Blocks.ACACIA_PLANKS, Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB,
                    Blocks.ACACIA_PLANKS, Blocks.ACACIA_DOOR, Blocks.ACACIA_FENCE,
                    Blocks.WAXED_CUT_COPPER, Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.WHITE_TERRACOTTA,
                    Blocks.MOSSY_COBBLESTONE, Blocks.STRIPPED_ACACIA_WOOD, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB);
        }
        if (type == VillagerType.TAIGA) {
            return new ChurchStyle(Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG,
                    Blocks.STRIPPED_SPRUCE_WOOD, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB,
                    Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_FENCE,
                    Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Blocks.SPRUCE_PLANKS,
                    Blocks.MOSSY_COBBLESTONE, Blocks.STRIPPED_SPRUCE_WOOD, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB);
        }
        if (type == VillagerType.SNOW) {
            return new ChurchStyle(Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.WHITE_TERRACOTTA, Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB,
                    Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_DOOR, Blocks.DARK_OAK_FENCE,
                    Blocks.WARPED_PLANKS, Blocks.WARPED_STAIRS, Blocks.WHITE_TERRACOTTA,
                    Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB);
        }
        // равнины (и всё остальное)
        return new ChurchStyle(Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.OAK_LOG,
                Blocks.STRIPPED_OAK_WOOD, Blocks.OAK_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB,
                Blocks.DARK_OAK_PLANKS, Blocks.OAK_DOOR, Blocks.DARK_OAK_FENCE,
                Blocks.WAXED_CUT_COPPER, Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.WHITE_TERRACOTTA,
                Blocks.MOSSY_COBBLESTONE, Blocks.STRIPPED_OAK_WOOD, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB);
    }
}
