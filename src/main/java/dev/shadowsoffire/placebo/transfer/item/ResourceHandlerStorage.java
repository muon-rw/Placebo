package dev.shadowsoffire.placebo.transfer.item;

import dev.shadowsoffire.placebo.transfer.ResourceHandler;
import dev.shadowsoffire.placebo.transfer.transaction.PlaceboTransactionContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Placebo adapter (no direct NeoForge equivalent): exposes any {@link ResourceHandler ResourceHandler&lt;ItemResource&gt;}
 * as a Fabric {@link SlottedStorage SlottedStorage&lt;ItemVariant&gt;} so it can be registered with
 * {@code ItemStorage.SIDED} for hopper automation.
 * <p>
 * Needed because some Apotheosis handlers ({@code GemCaseTile.GemCaseItemHandler}) implement
 * {@code ResourceHandler<ItemResource>} only and are not themselves a Fabric {@code Storage}. Wrapping such a handler in
 * the {@code ItemStorage.SIDED} registration lambda yields a valid {@code Storage<ItemVariant>}. The handler's
 * transactional state (its own {@link dev.shadowsoffire.placebo.transfer.transaction.SnapshotJournal}) still works
 * because every call threads through the same Fabric transaction via {@link PlaceboTransactionContext}.
 * <p>
 * The per-slot view logic is identical to the one inside {@link ItemStacksResourceHandler}, but delegates to the wrapped
 * handler rather than {@code this}.
 */
public class ResourceHandlerStorage implements SlottedStorage<ItemVariant> {

    private final ResourceHandler<ItemResource> handler;
    private HandlerSlotView[] slotViews;

    public ResourceHandlerStorage(ResourceHandler<ItemResource> handler) {
        this.handler = Objects.requireNonNull(handler, "Wrapped ResourceHandler may not be null");
    }

    @Override
    public int getSlotCount() {
        return this.handler.size();
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot(int slot) {
        return this.slotView(slot);
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        long inserted = 0;
        int size = this.handler.size();
        for (int i = 0; i < size && inserted < maxAmount; i++) {
            inserted += this.slotView(i).insert(resource, maxAmount - inserted, transaction);
        }
        return inserted;
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        long extracted = 0;
        int size = this.handler.size();
        for (int i = 0; i < size && extracted < maxAmount; i++) {
            extracted += this.slotView(i).extract(resource, maxAmount - extracted, transaction);
        }
        return extracted;
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        return new SlotViewIterator();
    }

    private HandlerSlotView slotView(int index) {
        HandlerSlotView[] views = this.slotViews;
        if (views == null || views.length != this.handler.size()) {
            views = new HandlerSlotView[this.handler.size()];
            this.slotViews = views;
        }
        HandlerSlotView view = views[index];
        if (view == null) {
            view = new HandlerSlotView(index);
            views[index] = view;
        }
        return view;
    }

    private class HandlerSlotView implements SingleSlotStorage<ItemVariant> {

        private final int index;

        private HandlerSlotView(int index) {
            this.index = index;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int amount = (int) Math.min(maxAmount, Integer.MAX_VALUE);
            return ResourceHandlerStorage.this.handler.insert(this.index, ItemResource.of(resource), amount, new PlaceboTransactionContext(transaction));
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int amount = (int) Math.min(maxAmount, Integer.MAX_VALUE);
            return ResourceHandlerStorage.this.handler.extract(this.index, ItemResource.of(resource), amount, new PlaceboTransactionContext(transaction));
        }

        @Override
        public boolean isResourceBlank() {
            return ResourceHandlerStorage.this.handler.getResource(this.index).isEmpty();
        }

        @Override
        public ItemVariant getResource() {
            return ResourceHandlerStorage.this.handler.getResource(this.index).variant();
        }

        @Override
        public long getAmount() {
            return ResourceHandlerStorage.this.handler.getAmountAsLong(this.index);
        }

        @Override
        public long getCapacity() {
            ItemResource res = ResourceHandlerStorage.this.handler.getResource(this.index);
            return ResourceHandlerStorage.this.handler.getCapacityAsLong(this.index, res);
        }
    }

    private class SlotViewIterator implements Iterator<StorageView<ItemVariant>> {

        private int next = 0;

        @Override
        public boolean hasNext() {
            return this.next < ResourceHandlerStorage.this.handler.size();
        }

        @Override
        public StorageView<ItemVariant> next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }
            return ResourceHandlerStorage.this.slotView(this.next++);
        }
    }
}
