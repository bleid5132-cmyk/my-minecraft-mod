package com.bleid.vestments.client;

import com.bleid.vestments.Vestments;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.util.Identifier;
import net.rpg_foundation.armor_api.client.ArmorRenderers;
import net.rpg_foundation.armor_api.client.GeoArmorRenderer;

public class VestmentsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Одна 3D-модель на весь комплект: каждый предмет показывает свои кости
        // (воротник — голова, фелонь — тело и руки, подризник — ноги и пояс, ботинки — низ подола и обувь).
        var renderer = GeoArmorRenderer.of(
                new Identifier(Vestments.MOD_ID, "geo/priest_vestments.geo.json"),
                new Identifier(Vestments.MOD_ID, "textures/armor/priest_vestments.png"));
        ArmorRenderers.register(renderer, Vestments.COLLAR, Vestments.PHELONION, Vestments.PODRIZNIK, Vestments.BOOTS);
        SlowFallClient.register();
    }
}
