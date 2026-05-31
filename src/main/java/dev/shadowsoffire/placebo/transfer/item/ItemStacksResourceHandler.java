package dev.shadowsoffire.placebo.transfer.item;

import dev.shadowsoffire.placebo.transfer.StacksResourceHandler;
import dev.shadowsoffire.placebo.transfer.transaction.PlaceboTransactionContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler}.
 * <p>
 * Extends {@link StacksResourceHandler StacksResourceHandler&lt;ItemStack, ItemResource&gt;} with the same item-specific
 * hooks as NeoForge, and ADDITIONALLY implements Fabric's {@link SlottedStorage SlottedStorage&lt;ItemVariant&gt;}
 * (hence {@link net.fabricmc.fabric.api.transfer.v1.storage.Storage Storage&lt;ItemVariant&gt;}). That dual identity is what
 * lets an instance be registered directly with {@code ItemStorage.SIDED} for hopper automation while still acting as the
 * {@code ResourceHandler} facade Apotheosis menus/tiles use.
 * <p>
 * Both access paths funnel into the same {@code StacksResourceHandler.insert/extract(index, ItemResource, amount, ctx)}
 * over the same Fabric transaction, so simulate/rollback is unified: the {@code Storage} path wraps the incoming Fabric
 * transaction in a {@link PlaceboTransactionContext} and converts the {@link ItemVariant} to an {@link ItemResource}.
 */
public class ItemStacksResourceHandler extends StacksResourceHandler<ItemStack, ItemResource> implements SlottedStorage<ItemVariant> {

    private ItemSlotView[] slotViews;

    public ItemStacksResourceHandler(int size) {
        super(size, ItemStack.EMPTY, ItemStack.OPTIONAL_CODEC);
    }

    public ItemStacksResourceHandler(NonNullList<ItemStack> stacks) {
        super(stacks, ItemStack.EMPTY, ItemStack.OPTIONAL_CODEC);
    }

    @Override
    public ItemResource getResourceFrom(ItemStack stack) {
        return ItemResource.of(stack);
    }

    @Override
    public int getAmountFrom(ItemStack stack) {
        return stack.getCount();
    }

    @Override
    protected ItemStack getStackFrom(ItemResource resource, int amount) {
        return resource.toStack(amount);
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
        return resource.isEmpty() ? 99 : Math.min(resource.getMaxStackSize(), 99);
    }

    @Override
    protected ItemStack copyOf(ItemStack stack) {
        return stack.copy();
    }

    @Override
    public boolean matches(ItemStack stack, ItemResource resource) {
        return resource.matches(stack);
    }

    // --- Fabric SlottedStorage<ItemVariant> view ------------------------------------------------------------------

    @Override
    public int getSlotCount() {
        return this.size();
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot(int slot) {
        return this.slotView(slot);
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        long inserted = 0;
        int size = this.size();
        for (int i = 0; i < size && inserted < maxAmount; i++) {
            inserted += this.slotView(i).insert(resource, maxAmount - inserted, transaction);
        }
        return inserted;
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        long extracted = 0;
        int size = this.size();
        for (int i = 0; i < size && extracted < maxAmount; i++) {
            extracted += this.slotView(i).extract(resource, maxAmount - extracted, transaction);
        }
        return extracted;
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        return new SlotViewIterator();
    }

    /**
     * Lazily-allocated cache of per-index views. Re-grown if the handler is resized via {@code setStacks}.
     */
    private ItemSlotView slotView(int index) {
        ItemSlotView[] views = this.slotViews;
        if (views == null || views.length != this.size()) {
            views = new ItemSlotView[this.size()];
            this.slotViews = views;
        }
        ItemSlotView view = views[index];
        if (view == null) {
            view = new ItemSlotView(index);
            views[index] = view;
        }
        return view;
    }

    /**
     * Per-index {@link SingleSlotStorage} bound to {@code (this, index)}. Converts between the {@link ItemVariant} the
     * Fabric storage API speaks and the {@link ItemResource} the {@link dev.shadowsoffire.placebo.transfer.ResourceHandler}
     * facade speaks, and threads the Fabric transaction through unchanged via {@link PlaceboTransactionContext}.
     */
    private class ItemSlotView implements SingleSlotStorage<ItemVariant> {

        private final int index;

        private ItemSlotView(int index) {
            this.index = index;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int amount = (int) Math.min(maxAmount, Integer.MAX_VALUE);
            return ItemStacksResourceHandler.this.insert(this.index, ItemResource.of(resource), amount, new PlaceboTransactionContext(transaction));
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int amount = (int) Math.min(maxAmount, Integer.MAX_VALUE);
            return ItemStacksResourceHandler.this.extract(this.index, ItemResource.of(resource), amount, new PlaceboTransactionContext(transaction));
        }

        @Override
        public boolean isResourceBlank() {
            return ItemStacksResourceHandler.this.getResource(this.index).isEmpty();
        }

        @Override
        public ItemVariant getResource() {
            return ItemStacksResourceHandler.this.getResource(this.index).variant();
        }

        @Override
        public long getAmount() {
            return ItemStacksResourceHandler.this.getAmountAsLong(this.index);
        }

        @Override
        public long getCapacity() {
            ItemResource res = ItemStacksResourceHandler.this.getResource(this.index);
            return ItemStacksResourceHandler.this.getCapacityAsLong(this.index, res);
        }
    }

    private class SlotViewIterator implements Iterator<StorageView<ItemVariant>> {

        private int next = 0;

        @Override
        public boolean hasNext() {
            return this.next < ItemStacksResourceHandler.this.size();
        }

        @Override
        public StorageView<ItemVariant> next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }
            return ItemStacksResourceHandler.this.slotView(this.next++);
        }
    }
}
