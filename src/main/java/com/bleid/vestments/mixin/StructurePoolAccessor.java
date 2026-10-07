package com.bleid.vestments.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Доступ к списку построек пула (чтобы убрать из деревень стандартные храмы). */
@Mixin(StructurePool.class)
public interface StructurePoolAccessor {
    @Accessor("elements")
    ObjectArrayList<StructurePoolElement> vestments$getElements();
}
