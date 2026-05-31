package dev.shadowsoffire.placebo.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

/**
 * Fabric replacement for NeoForge's {@code net.neoforged.neoforge.registries.DeferredItem}.
 * <p>
 * A specialized {@link DeferredHolder} for {@link Item}s that also implements {@link ItemLike}, mirroring the NeoForge type so
 * that item handles can be used directly anywhere an {@link ItemLike} is expected.
 *
 * @param <T> The specific type of the held item.
 */
public class DeferredItem<T extends Item> extends DeferredHolder<Item, T> implements ItemLike {

    protected DeferredItem(Identifier id) {
        super(Registries.ITEM, id);
    }

    /**
     * Creates a new {@link DeferredItem} targeting the item with the given {@code id}.
     *
     * @param id The name of the item.
     * @return A new lazily-resolved item handle.
     */
    public static <T extends Item> DeferredItem<T> createItem(Identifier id) {
        return new DeferredItem<>(id);
    }

    /**
     * {@return the held item}
     */
    @Override
    public Item asItem() {
        return this.value();
    }

}
