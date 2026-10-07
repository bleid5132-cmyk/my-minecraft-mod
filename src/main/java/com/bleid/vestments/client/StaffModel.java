package com.bleid.vestments.client;

import com.bleid.vestments.Vestments;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.Identifier;

/** Объёмная модель Посоха Света для руки: загружается как дополнительная модель. */
@Environment(EnvType.CLIENT)
public final class StaffModel {
    public static final Identifier ID = new Identifier(Vestments.MOD_ID, "item/staff_of_light_3d");

    private StaffModel() { }

    public static void register() {
        ModelLoadingPlugin.register(context -> context.addModels(ID));
    }

    public static BakedModel get(MinecraftClient client) {
        BakedModel model = ((FabricBakedModelManager) client.getBakedModelManager()).getModel(ID);
        return model == null || model == client.getBakedModelManager().getMissingModel() ? null : model;
    }
}
