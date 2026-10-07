package com.bleid.vestments.church;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.village.VillagerType;

/**
 * Материалы церкви. Все церкви — как в снежной деревне: белые стены, каменный цоколь, еловые столбы,
 * тёмная дубовая кровля. По типу деревни меняется только цвет глав.
 */
public record ChurchStyle(Block foundation, Block base, Block wall, Block pillar, Block trim, Block floor,
                          Block roofStairs, Block roofSlab, Block roofBlock, Block door, Block fence,
                          Block dome, Block domeStairs, Block drum, Block mossyBase, Block crackedWall) {

    public static ChurchStyle of(VillagerType type) {
        Block dome = Blocks.WARPED_PLANKS, domeStairs = Blocks.WARPED_STAIRS;            // снега — сине-бирюзовые
        if (type == VillagerType.TAIGA) {
            dome = Blocks.WAXED_OXIDIZED_CUT_COPPER; domeStairs = Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS;   // зелёные
        } else if (type == VillagerType.PLAINS || type == VillagerType.DESERT || type == VillagerType.SAVANNA) {
            dome = Blocks.WAXED_CUT_COPPER; domeStairs = Blocks.WAXED_CUT_COPPER_STAIRS;   // золотисто-медные
        }
        return new ChurchStyle(Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.WHITE_TERRACOTTA, Blocks.STRIPPED_SPRUCE_LOG,
                Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB,
                Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_DOOR, Blocks.DARK_OAK_FENCE,
                dome, domeStairs, Blocks.WHITE_TERRACOTTA,
                Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS);
    }
}
