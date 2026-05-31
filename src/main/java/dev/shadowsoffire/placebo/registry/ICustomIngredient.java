package dev.shadowsoffire.placebo.registry;

import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;

/**
 * Fabric replacement for NeoForge's {@code net.neoforged.neoforge.common.crafting.ICustomIngredient}.
 * <p>
 * Placebo's interface extends Fabric's {@link CustomIngredient} and re-exposes NeoForge's method names so that existing
 * implementations (which declare {@code isSimple()} and {@code getType()}) compile and behave unchanged. The two NeoForge-named
 * abstract methods are bridged onto Fabric's {@link CustomIngredient#requiresTesting()} and
 * {@link CustomIngredient#getSerializer()} by the defaults below.
 * <p>
 * Implementors must still provide {@link CustomIngredient#test(net.minecraft.world.item.ItemStack)} and
 * {@link CustomIngredient#items()}; those method names are identical between NeoForge and Fabric.
 */
public interface ICustomIngredient extends CustomIngredient {

    /**
     * {@return whether this ingredient ignores data components (NBT) when matching}, equivalent to the inverse of Fabric's
     * {@link CustomIngredient#requiresTesting()}. A simple ingredient matches purely on item identity.
     */
    boolean isSimple();

    /**
     * {@return the {@link IngredientType} describing this ingredient's serialization}.
     */
    IngredientType<?> getType();

    /**
     * Bridges NeoForge's {@link #isSimple()} onto Fabric's {@link CustomIngredient#requiresTesting()}: a simple ingredient does
     * not require per-stack testing.
     */
    @Override
    default boolean requiresTesting() {
        return !this.isSimple();
    }

    /**
     * Bridges NeoForge's {@link #getType()} onto Fabric's {@link CustomIngredient#getSerializer()} by returning the
     * {@link CustomIngredientSerializer} created and registered for this ingredient's {@link IngredientType}.
     */
    @Override
    default CustomIngredientSerializer<?> getSerializer() {
        return this.getType().serializer();
    }

}
