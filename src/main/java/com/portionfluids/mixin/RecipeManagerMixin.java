package com.portionfluids.mixin;

import com.portionfluids.FluidPortions;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    // Production Forge keeps the SRG name. There is no refmap, so the official name never matches.
    @Inject(method = "m_44069_", at = @At("RETURN"), remap = false)
    private <C extends Container, T extends Recipe<C>> void portionFluids(RecipeType<T> type, C inventory, Level level,
                                                                           CallbackInfoReturnable<NonNullList<ItemStack>> cir) {
        NonNullList<ItemStack> remainders = cir.getReturnValue();
        if (remainders == null || inventory == null || level == null) {
            return;
        }
        Optional<T> found = ((RecipeManager) (Object) this).getRecipeFor(type, inventory, level);
        found.ifPresent(recipe -> FluidPortions.adjust(recipe, inventory, remainders));
    }
}
