package com.bleid.vestments.horse;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Кони мода: повозка, зов рога, рывок на CTRL. */
public final class Horses {
    private Horses() { }

    public static final EntityType<CartEntity> CART = Registry.register(Registries.ENTITY_TYPE, new Identifier("vestments", "cart"),
            FabricEntityTypeBuilder.<CartEntity>create(SpawnGroup.MISC, CartEntity::new)
                    .dimensions(EntityDimensions.fixed(2.0f, 1.3f)).trackRangeBlocks(80).trackedUpdateRate(2).build());

    public static final Item CART_ITEM = Registry.register(Registries.ITEM, new Identifier("vestments", "cart"),
            new Item(new FabricItemSettings().maxCount(1)) {
                @Override
                public ActionResult useOnBlock(ItemUsageContext ctx) {
                    var w = ctx.getWorld();
                    if (w.isClient) return ActionResult.SUCCESS;
                    Vec3d p = ctx.getHitPos();
                    CartEntity c = CART.create(w);
                    if (c == null) return ActionResult.FAIL;
                    float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYaw() : 0f;
                    c.refreshPositionAndAngles(p.x, p.y, p.z, yaw, 0);
                    if (!w.isSpaceEmpty(c, c.getBoundingBox().contract(0.1))) return ActionResult.FAIL;
                    w.spawnEntity(c);
                    if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().creativeMode) ctx.getStack().decrement(1);
                    return ActionResult.CONSUME;
                }
            });

    public static void register() {
        HorseBond.register();
        HorseSprint.register();
    }
}
