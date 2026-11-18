package com.portionfluids;

import net.dries007.tfc.common.capabilities.Capabilities;
import net.dries007.tfc.common.recipes.DelegateRecipe;
import net.dries007.tfc.common.recipes.NoRemainderCraftingRecipe;
import net.dries007.tfc.common.recipes.ingredients.DelegateIngredient;
import net.dries007.tfc.common.recipes.ingredients.FluidItemIngredient;
import net.dries007.tfc.common.recipes.ingredients.FluidStackIngredient;
import net.dries007.tfc.config.TFCConfig;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Crafting recipes that take a fluid container drain one fixed portion and put the
 * container back. A recipe that accepts the ceramic jug drains {@link #JUG_MB}.
 * A recipe that needs a wooden bucket or an iron bucket drains {@link #BUCKET_MB}.
 */
public final class FluidPortions {
    public static final int JUG_MB = 100;
    public static final int BUCKET_MB = 1000;
    private static final ResourceLocation JUG_ID = new ResourceLocation("tfc", "ceramic/jug");

    private static final Field DELEGATE = field(DelegateIngredient.class, "delegate");
    private static final Field FLUID = field(FluidItemIngredient.class, "fluid");

    private FluidPortions() {
    }

    public static void adjust(Recipe<?> recipe, Container inventory, NonNullList<ItemStack> remainders) {
        if (recipe == null || inventory == null || remainders == null || suppressesRemainder(recipe)) {
            return;
        }
        List<FluidItemIngredient> fluids = fluidIngredients(recipe);
        if (fluids.isEmpty()) {
            return;
        }
        boolean[] used = new boolean[fluids.size()];
        int slots = Math.min(inventory.getContainerSize(), remainders.size());
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            for (int i = 0; i < fluids.size(); i++) {
                if (used[i] || !fluids.get(i).test(stack)) {
                    continue;
                }
                ItemStack kept = drain(stack, portion(fluids.get(i)));
                if (kept != null) {
                    remainders.set(slot, kept);
                    used[i] = true;
                }
                break;
            }
        }
    }

    /** Jug-sized recipes stay at 100. Anything a jug cannot satisfy is a bucket portion. */
    static int portion(FluidItemIngredient ingredient) {
        int asked = fluidAmount(ingredient);
        if (acceptsJug(ingredient) && asked <= jugCapacity()) {
            return JUG_MB;
        }
        return BUCKET_MB;
    }

    private static int jugCapacity() {
        try {
            Integer value = TFCConfig.SERVER.jugCapacity.get();
            if (value != null && value > 0) {
                return value;
            }
        } catch (RuntimeException ignored) {
            // Config is not ready yet. The shipped default is one jug.
        }
        return JUG_MB;
    }

    private static boolean acceptsJug(FluidItemIngredient ingredient) {
        if (ingredient.lacksDelegate()) {
            return true;
        }
        Ingredient containers = delegate(ingredient);
        if (containers == null) {
            return true;
        }
        Item jug = ForgeRegistries.ITEMS.getValue(JUG_ID);
        if (jug == null || jug == Items.AIR) {
            return true;
        }
        return containers.test(new ItemStack(jug));
    }

    private static ItemStack drain(ItemStack stack, int want) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        IFluidHandlerItem handler = fluidHandler(copy);
        if (handler == null) {
            return null;
        }
        FluidStack stored = handler.getFluidInTank(0);
        if (stored.isEmpty()) {
            return null;
        }
        int amount = Math.min(want, stored.getAmount());
        if (amount <= 0) {
            return null;
        }
        FluidStack drained = handler.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty() || drained.getAmount() < amount) {
            return null;
        }
        ItemStack container = handler.getContainer();
        return container == null ? ItemStack.EMPTY : container;
    }

    private static IFluidHandlerItem fluidHandler(ItemStack stack) {
        IFluidHandlerItem tfc = stack.getCapability(Capabilities.FLUID_ITEM).resolve().orElse(null);
        if (tfc != null && !tfc.getFluidInTank(0).isEmpty()) {
            return tfc;
        }
        IFluidHandlerItem forge = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElse(null);
        if (forge != null && !forge.getFluidInTank(0).isEmpty()) {
            return forge;
        }
        return tfc != null ? tfc : forge;
    }

    private static boolean suppressesRemainder(Recipe<?> recipe) {
        Recipe<?> current = recipe;
        for (int depth = 0; current != null && depth < 6; depth++) {
            if (current instanceof NoRemainderCraftingRecipe<?>) {
                return true;
            }
            if (current instanceof DelegateRecipe<?, ?> delegate) {
                current = delegate.getDelegate();
            } else {
                break;
            }
        }
        return false;
    }

    private static List<FluidItemIngredient> fluidIngredients(Recipe<?> recipe) {
        List<FluidItemIngredient> found = new ArrayList<>();
        Recipe<?> current = recipe;
        for (int depth = 0; current != null && depth < 6; depth++) {
            for (Ingredient ingredient : current.getIngredients()) {
                if (ingredient instanceof FluidItemIngredient fluid) {
                    found.add(fluid);
                }
            }
            if (!found.isEmpty()) {
                return found;
            }
            if (current instanceof DelegateRecipe<?, ?> delegate) {
                current = delegate.getDelegate();
            } else {
                break;
            }
        }
        return found;
    }

    private static int fluidAmount(FluidItemIngredient ingredient) {
        Object value = read(FLUID, ingredient);
        if (value instanceof FluidStackIngredient fluid) {
            return fluid.amount();
        }
        return JUG_MB;
    }

    private static Ingredient delegate(FluidItemIngredient ingredient) {
        Object value = read(DELEGATE, ingredient);
        return value instanceof Ingredient containers ? containers : null;
    }

    private static Object read(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static Field field(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }
}
