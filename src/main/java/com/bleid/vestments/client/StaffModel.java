package com.bleid.vestments.client;

import com.bleid.vestments.Vestments;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

/**
 * Объёмные модели Посоха Света для руки: обычная и «наведённая» (при зажатой ПКМ посох
 * опущен навершием вперёд, в сторону удара).
 */
@Environment(EnvType.CLIENT)
public final class StaffModel {
    public static final Identifier ID = new Identifier(Vestments.MOD_ID, "item/staff_of_light_3d");
    public static final Identifier AIM_ID = new Identifier(Vestments.MOD_ID, "item/staff_of_light_3d_aim");

    /** Сущность, чей предмет сейчас рисуется (ставит ItemRendererMixin). */
    public static LivingEntity currentEntity;

    private StaffModel() { }

    public static void register() {
        ModelLoadingPlugin.register(context -> {
            context.addModels(ID, AIM_ID);
            for (var w : com.bleid.vestments.gear.RankGear.WEAPONS) context.addModels(weaponModel(w));
        });
    }

    /** 3D-модель оружия сана для руки: item/<id>_3d. */
    public static Identifier weaponModel(net.minecraft.item.Item item) {
        Identifier id = net.minecraft.registry.Registries.ITEM.getId(item);
        return new Identifier(id.getNamespace(), "item/" + id.getPath() + "_3d");
    }

    public static BakedModel getWeapon(MinecraftClient client, net.minecraft.item.Item item) {
        BakedModel model = ((FabricBakedModelManager) client.getBakedModelManager()).getModel(weaponModel(item));
        return model == null || model == client.getBakedModelManager().getMissingModel() ? null : model;
    }

    public static BakedModel get(MinecraftClient client, boolean aiming) {
        BakedModel model = ((FabricBakedModelManager) client.getBakedModelManager()).getModel(aiming ? AIM_ID : ID);
        return model == null || model == client.getBakedModelManager().getMissingModel() ? null : model;
    }

    public static boolean isAiming(LivingEntity entity) {
        return entity != null && entity.isUsingItem() && entity.getActiveItem().isOf(Vestments.STAFF_OF_LIGHT);
    }
}
