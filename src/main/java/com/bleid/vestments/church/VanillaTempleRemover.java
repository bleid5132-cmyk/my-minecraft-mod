package com.bleid.vestments.church;

import com.bleid.vestments.Vestments;
import com.bleid.vestments.mixin.StructurePoolAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.util.Identifier;

/**
 * Убирает стандартные храмы жителей (plains_temple, desert_temple, savanna_temple, taiga_temple,
 * snowy_temple и их зомби-версии) из пулов построек деревень — вместо них теперь наша церковь.
 */
public final class VanillaTempleRemover {
    private VanillaTempleRemover() { }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Registry<StructurePool> pools = server.getRegistryManager().get(RegistryKeys.TEMPLATE_POOL);
            int removed = 0;
            for (StructurePool pool : pools) {
                Identifier id = pools.getId(pool);
                if (id == null || !id.getPath().startsWith("village/")) continue;
                var elements = ((StructurePoolAccessor) pool).vestments$getElements();
                int before = elements.size();
                elements.removeIf(e -> e.toString().contains("_temple"));
                removed += before - elements.size();
            }
            Vestments.LOGGER.info("Стандартные храмы жителей убраны из деревень ({} записей)", removed);
        });
    }
}
