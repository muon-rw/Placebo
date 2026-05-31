package dev.shadowsoffire.placebo.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/**
 * Fabric replacement for NeoForge's {@code net.neoforged.neoforge.registries.DeferredBlock}.
 * <p>
 * A specialized {@link DeferredHolder} for {@link Block}s that also implements {@link ItemLike}, mirroring the NeoForge type
 * so that block handles can be used directly anywhere an {@link ItemLike} is expected (e.g. recipe and creative-tab APIs).
 *
 * @param <T> The specific type of the held block.
 */
public class DeferredBlock<T extends Block> extends DeferredHolder<Block, T> implements ItemLike {

    protected DeferredBlock(Identifier id) {
        super(Registries.BLOCK, id);
    }

    /**
     * Creates a new {@link DeferredBlock} targeting the block with the given {@code id}.
     *
     * @param id The name of the block.
     * @return A new lazily-resolved block handle.
     */
    public static <T extends Block> DeferredBlock<T> createBlock(Identifier id) {
        return new DeferredBlock<>(id);
    }

    /**
     * {@return the {@link Item} form of the held block, or {@code Items.AIR} if the block has no item}
     */
    @Override
    public Item asItem() {
        return this.value().asItem();
    }

}
