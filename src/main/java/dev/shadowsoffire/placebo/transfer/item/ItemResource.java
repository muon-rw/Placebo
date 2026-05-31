package dev.shadowsoffire.placebo.transfer.item;

import com.mojang.serialization.Codec;
import dev.shadowsoffire.placebo.transfer.resource.DataComponentHolderResource;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.item.ItemResource}.
 * <p>
 * An immutable, count-less item key (item + {@link DataComponentPatch}). Backed 1:1 by a Fabric
 * {@link ItemVariant}, which has identical semantics: immutable, count-less, value-based equality on
 * item + components. Every member Apotheosis uses ({@link #EMPTY}, {@link #of(ItemStack)}, {@link #toStack()},
 * {@link #toStack(int)}, {@link #isEmpty()}, {@link #is(ItemLike)}, {@link #matches(ItemStack)}) maps directly onto a
 * corresponding {@code ItemVariant} member.
 *
 * <h2>Fabric-forced deviations</h2>
 * <ul>
 * <li>{@link #toStack(int)} adds the NeoForge {@code count == 0 -> ItemStack.EMPTY} guard, because Fabric's
 * {@code ItemVariant.toStack(0)} returns a zero-count stack rather than {@code ItemStack.EMPTY}.</li>
 * <li>{@link #getMaxStackSize()} is computed via a one-count stack ({@code toStack(1).getMaxStackSize()}) so that
 * component-driven max-stack-size overrides are honoured, matching NeoForge's {@code innerStack.getMaxStackSize()}.</li>
 * <li>{@code CODEC}/{@code STREAM_CODEC} are mapped over {@code ItemVariant.CODEC}/{@code ItemVariant.PACKET_CODEC}.</li>
 * </ul>
 */
public final class ItemResource implements DataComponentHolderResource<Item> {

    /** The blank/empty resource (air). Mirrors NeoForge {@code ItemResource.EMPTY}; backed by {@link ItemVariant#blank()}. */
    public static final ItemResource EMPTY = new ItemResource(ItemVariant.blank());

    /** Codec over the backing {@link ItemVariant}. */
    public static final Codec<ItemResource> CODEC = ItemVariant.CODEC.xmap(ItemResource::new, ItemResource::variant);

    /** Stream codec over the backing {@link ItemVariant}. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemResource> STREAM_CODEC =
        ItemVariant.PACKET_CODEC.map(ItemResource::new, ItemResource::variant);

    private final ItemVariant variant;

    private ItemResource(ItemVariant variant) {
        this.variant = variant;
    }

    /**
     * {@return an {@code ItemResource} wrapping the given Fabric {@link ItemVariant} directly}
     * <p>
     * The lossless inverse of {@link #variant()}; used by the {@code Storage<ItemVariant>} view to convert an incoming
     * variant to a resource without round-tripping through an {@link ItemStack}.
     */
    public static ItemResource of(ItemVariant variant) {
        return variant.isBlank() ? EMPTY : new ItemResource(variant);
    }

    /**
     * {@return an {@code ItemResource} for the given stack, dropping the count} Mirrors NeoForge {@code of(ItemStack)};
     * backed by {@link ItemVariant#of(ItemStack)} which strips the count while preserving the component patch.
     */
    public static ItemResource of(ItemStack stack) {
        return new ItemResource(ItemVariant.of(stack));
    }

    /**
     * {@return an {@code ItemResource} for the given item with default components}
     */
    public static ItemResource of(ItemLike item) {
        return new ItemResource(ItemVariant.of(item));
    }

    /**
     * {@return an {@code ItemResource} for the given item with the supplied component patch}
     */
    public static ItemResource of(ItemLike item, DataComponentPatch patch) {
        return new ItemResource(ItemVariant.of(item, patch));
    }

    /**
     * {@return an {@code ItemResource} for the given item holder with default components}
     */
    public static ItemResource of(Holder<Item> holder) {
        return of(holder.value());
    }

    /**
     * {@return an {@code ItemResource} for the given item holder with the supplied component patch}
     */
    public static ItemResource of(Holder<Item> holder, DataComponentPatch patch) {
        return of(holder.value(), patch);
    }

    /**
     * {@return the backing Fabric {@link ItemVariant}} The seam between the {@code ResourceHandler} facade and the
     * Fabric {@code Storage<ItemVariant>} view.
     */
    public ItemVariant variant() {
        return this.variant;
    }

    /**
     * {@return the item held by this resource} Mirrors NeoForge {@code value()}.
     */
    public Item value() {
        return this.variant.getItem();
    }

    /**
     * {@return the item held by this resource} Mirrors NeoForge {@code getItem()}.
     */
    public Item getItem() {
        return this.variant.getItem();
    }

    /**
     * {@return the registry holder for the item} Mirrors NeoForge {@code typeHolder()}.
     */
    public Holder<Item> typeHolder() {
        return this.variant.typeHolder();
    }

    @Override
    public boolean isEmpty() {
        return this.variant.isBlank();
    }

    /**
     * {@return whether the given stack has the same item and components as this resource}
     */
    public boolean matches(ItemStack stack) {
        return this.variant.matches(stack);
    }

    /**
     * {@return whether this resource's item is the given item}
     */
    public boolean is(ItemLike item) {
        return this.variant.isOf(item.asItem());
    }

    @Override
    public boolean isComponentsPatchEmpty() {
        return !this.variant.hasComponents();
    }

    @Override
    public ItemResource withMergedPatch(DataComponentPatch patch) {
        if (this.isEmpty() || patch.isEmpty()) {
            return this;
        }
        // NeoForge semantics: merge the patch on top of existing components (not a replace), via a working stack.
        ItemStack stack = this.toStack(1);
        stack.applyComponents(patch);
        return of(stack);
    }

    @Override
    public <D> ItemResource with(DataComponentType<D> type, @Nullable D data) {
        if (this.isEmpty()) {
            return EMPTY;
        }
        ItemStack stack = this.toStack(1);
        stack.set(type, data);
        return of(stack);
    }

    @Override
    public ItemResource without(DataComponentType<?> type) {
        if (this.isEmpty()) {
            return EMPTY;
        }
        ItemStack stack = this.toStack(1);
        stack.remove(type);
        return of(stack);
    }

    @Override
    public DataComponentMap getComponents() {
        return this.variant.getComponents();
    }

    @Override
    public DataComponentPatch getComponentsPatch() {
        return this.variant.getComponentsPatch();
    }

    /**
     * {@return a stack of this resource with the given count} Mirrors NeoForge {@code toStack(int)}: a count of 0
     * yields {@link ItemStack#EMPTY} (the Fabric backing would otherwise return a zero-count stack).
     */
    public ItemStack toStack(int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        return this.variant.toStack(count);
    }

    /**
     * {@return a stack of this resource with count 1} Mirrors NeoForge {@code toStack()}.
     */
    public ItemStack toStack() {
        return this.toStack(1);
    }

    /**
     * {@return the maximum stack size for this resource} Computed from a one-count stack so component overrides apply.
     */
    public int getMaxStackSize() {
        if (this.isEmpty()) {
            return 0;
        }
        return this.variant.toStack(1).getMaxStackSize();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }
        return this.variant.equals(((ItemResource) obj).variant);
    }

    @Override
    public int hashCode() {
        return this.variant.hashCode();
    }

    @Override
    public String toString() {
        return Objects.toString(this.value()) + " [" + this.getComponentsPatch().size() + "]";
    }
}
