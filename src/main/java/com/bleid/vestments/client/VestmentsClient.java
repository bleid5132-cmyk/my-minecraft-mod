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
        // облачение остальных санов: своя модель и текстура у каждого
        for (var set : com.bleid.vestments.gear.RankGear.SETS) {
            if (set == com.bleid.vestments.gear.RankGear.PATRIARCH) continue;
            ArmorRenderers.register(GeoArmorRenderer.of(
                    new Identifier(Vestments.MOD_ID, "geo/" + set.id + ".geo.json"),
                    new Identifier(Vestments.MOD_ID, "textures/armor/" + set.id + ".png")),
                    set.helmet, set.chest, set.legs, set.boots);
        }
        // доспехи паладина
        for (var set : com.bleid.vestments.paladin.PaladinGear.SETS) {
            var r = GeoArmorRenderer.of(
                    new Identifier(Vestments.MOD_ID, "geo/" + set.id + ".geo.json"),
                    new Identifier(Vestments.MOD_ID, "textures/armor/" + set.id + ".png"));
            if (set.id.equals("general")) r.glow();
            ArmorRenderers.register(r, set.helmet, set.chest, set.legs, set.boots);
        }
        com.bleid.vestments.client.combat.CombatClient.register();
        com.bleid.vestments.client.combat.SwordTrails.register();
        WaterWalkClient.register();
        StaffBeamRenderer.register();
        AbilityHud.register();
        StaffModel.register();
        ScabbardFeature.register();
        WingsClient.register();
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.bleid.vestments.boss.Bosses.SERAEL,
                com.bleid.vestments.client.boss.SeraelRenderer::new);
        com.bleid.vestments.client.boss.SeraelClient.register();
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.bleid.vestments.boss.Bosses.BELL_RINGER,
                com.bleid.vestments.client.boss.BellRingerRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.bleid.vestments.boss.Bosses.SILENT_BELL,
                com.bleid.vestments.client.boss.SilentBellRenderer::new);
        com.bleid.vestments.client.boss.BellBossClient.register();
        // реалистичные лошади вместо ванильных (только вид и анимации — механика ванильная)
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(net.minecraft.entity.EntityType.HORSE,
                com.bleid.vestments.client.horse.RealHorseRenderer::new);
        PaladinKeys.register();
        PaladinHud.register();
        DodgeClient.register();
        HammerClient.register();
        SmallTooltipComponent.register();
        ClassSelectScreen.register();
        ServiceClient.register();
        PatriarchClient.register();
        SoulEffects.register();
        AltarScreen.register();
        SoulAllyClient.register();
        SoulSelectScreen.register();
        com.bleid.vestments.client.fx.FxSystem.register();
        com.bleid.vestments.client.fx.FxEffects.register();
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                Vestments.HOLY_ALTAR, net.minecraft.client.render.RenderLayer.getCutout());
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                Vestments.BELL_PORTAL, net.minecraft.client.render.RenderLayer.getTranslucent());
    }
}
