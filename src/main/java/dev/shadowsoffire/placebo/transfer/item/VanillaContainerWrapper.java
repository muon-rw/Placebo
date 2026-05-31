package dev.shadowsoffire.placebo.transfer.item;

import com.google.common.collect.MapMaker;
import dev.shadowsoffire.placebo.transfer.ResourceHandler;
import dev.shadowsoffire.placebo.transfer.transaction.SnapshotJournal;
import dev.shadowsoffire.placebo.transfer.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.item.VanillaContainerWrapper}.
 * <p>
 * Exposes a vanilla {@link Container} as a {@link ResourceHandler ResourceHandler&lt;ItemResource&gt;}, one index per
 * container slot. Apothic-Enchanting registers it against the item capability for its filtering shelf via
 * {@code VanillaContainerWrapper.of(tile)}, so the {@link #of(Container)} signature, the {@code ResourceHandler} surface,
 * and the transactional {@code setChanged()}-on-commit behaviour are reproduced exactly from NeoForge.
 * <p>
 * Each index is backed by an inner {@link SlotWrapper} that reproduces NeoForge's {@code ItemStackResourceHandler}
 * single-slot semantics (match-or-empty insert, capacity clamping, simulate-then-rollback) over a Placebo
 * {@link SnapshotJournal SnapshotJournal&lt;ItemStack&gt;} (itself a Fabric {@code SnapshotParticipant}), so the live Fabric
 * transaction drives the slot. A separate {@link #setChangedJournal} reproduces NeoForge's {@code RootCommitJournal}: it
 * enrolls in the same transaction whenever a slot mutates and fires {@link Container#setChanged()} exactly once, on the
 * root commit.
 *
 * <h2>Fabric-forced deviations</h2>
 * <ul>
 * <li><b>No {@code PlayerInventoryWrapper}.</b> NeoForge's {@code internalOf} branches to a {@code PlayerInventoryWrapper}
 * for {@code Inventory}; that player-specific subclass (armor slots, drops, menu broadcast) lives with the excluded menu
 * tier and is not part of this mirror, so an {@code Inventory} is wrapped as a generic container here. The filtering-shelf
 * registration (a {@code ChiseledBookShelfBlockEntity}) never hits that branch.</li>
 * <li><b>{@code container.setItem(index, stack)} (2-arg).</b> Vanilla {@code Container} has no NeoForge-added
 * {@code setItem(int, ItemStack, boolean)} "silent" overload, so the standard two-argument setter is used.</li>
 * <li><b>No {@code container.onTransfer(...)}.</b> NeoForge's {@code SlotWrapper.insert/extract} notify the container via
 * the NeoForge-added {@code Container.onTransfer(int, int, TransactionContext)} hook, which vanilla does not have, so that
 * notification is dropped. {@code setChanged()} on root commit (the behaviour Apothic relies on) is preserved.</li>
 * </ul>
 */
public class VanillaContainerWrapper implements ResourceHandler<ItemResource> {

    private static final Map<Container, VanillaContainerWrapper> wrappers = new MapMaker().weakKeys().weakValues().makeMap();

    private final Container container;
    int size;
    final List<SlotWrapper> slotWrappers = new ArrayList<>();
    private final RootCommitJournal setChangedJournal;

    /**
     * {@return a (cached) {@link ResourceHandler} view over the given vanilla {@link Container}} Mirror of NeoForge
     * {@code VanillaContainerWrapper.of(Container)}; the returned handler is shared per container and re-sized to the
     * current container size on each call.
     */
    public static ResourceHandler<ItemResource> of(Container container) {
        return internalOf(container);
    }

    static VanillaContainerWrapper internalOf(Container container) {
        VanillaContainerWrapper wrapper = wrappers.computeIfAbsent(container, VanillaContainerWrapper::new);
        wrapper.resize();
        return wrapper;
    }

    VanillaContainerWrapper(Container container) {
        this.container = container;
        this.setChangedJournal = new RootCommitJournal(this::onRootCommit);
    }

    void resize() {
        this.size = this.container.getContainerSize();
        while (this.slotWrappers.size() < this.size) {
            this.slotWrappers.add(new SlotWrapper(this.slotWrappers.size()));
        }
    }

    SlotWrapper getSlotWrapper(int index) {
        Objects.checkIndex(index, this.size());
        return this.slotWrappers.get(index);
    }

    void onRootCommit() {
        this.container.setChanged();
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return this.getSlotWrapper(index).insert(0, resource, amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return this.getSlotWrapper(index).extract(0, resource, amount, transaction);
    }

    @Override
    public ItemResource getResource(int index) {
        return this.getSlotWrapper(index).getResource(0);
    }

    @Override
    public long getAmountAsLong(int index) {
        return this.getSlotWrapper(index).getAmountAsLong(0);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return this.getSlotWrapper(index).getCapacityAsLong(0, resource);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return this.getSlotWrapper(index).isValid(0, resource);
    }

    @Override
    public String toString() {
        return "VanillaContainerWrapper{container=%s}".formatted(this.container);
    }

    /**
     * Single-slot {@link ResourceHandler} bound to one container index. Reproduces NeoForge's
     * {@code SlotWrapper extends ItemStackResourceHandler}: the {@code ItemStackResourceHandler} insert/extract bodies are
     * inlined here (the Placebo mirror has no standalone {@code ItemStackResourceHandler}) over a
     * {@link SnapshotJournal SnapshotJournal&lt;ItemStack&gt;}, with the same furnace/brewing capacity rules,
     * {@code canPlaceItem} validity, double-chest {@code setChanged} fan-out, and component-restoring root commit.
     */
    class SlotWrapper extends SnapshotJournal<ItemStack> implements ResourceHandler<ItemResource> {

        private final int index;

        SlotWrapper(int index) {
            this.index = index;
        }

        protected ItemStack getStack() {
            return VanillaContainerWrapper.this.container.getItem(this.index);
        }

        protected void setStack(ItemStack item) {
            // Vanilla Container has no NeoForge-added setItem(int, ItemStack, boolean) silent overload.
            VanillaContainerWrapper.this.container.setItem(this.index, item);
        }

        protected boolean isValid(ItemResource resource) {
            return VanillaContainerWrapper.this.container.canPlaceItem(this.index, resource.toStack());
        }

        protected int getCapacity(ItemResource resource) {
            if (this.index == 1 && resource.is(Items.BUCKET) && VanillaContainerWrapper.this.container instanceof AbstractFurnaceBlockEntity) {
                return 1;
            }
            else if (this.index < 3 && VanillaContainerWrapper.this.container instanceof BrewingStandBlockEntity) {
                return 1;
            }
            else {
                return resource.isEmpty()
                    ? VanillaContainerWrapper.this.container.getMaxStackSize()
                    : VanillaContainerWrapper.this.container.getMaxStackSize(resource.toStack());
            }
        }

        @Override
        public final int size() {
            return 1;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, this.size());
            checkNonEmptyNonNegative(resource, amount);
            ItemStack currentStack = this.getStack();
            if ((currentStack.isEmpty() || resource.matches(currentStack)) && this.isValid(resource)) {
                int insertedAmount = Math.min(amount, this.getCapacity(resource) - currentStack.getCount());
                if (insertedAmount > 0) {
                    this.updateSnapshots(transaction);
                    currentStack = this.getStack();
                    if (currentStack.isEmpty()) {
                        currentStack = resource.toStack(insertedAmount);
                    }
                    else {
                        currentStack.grow(insertedAmount);
                    }
                    this.setStack(currentStack);
                    return insertedAmount;
                }
            }
            return 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, this.size());
            checkNonEmptyNonNegative(resource, amount);
            ItemStack currentStack = this.getStack();
            if (resource.matches(currentStack)) {
                int extracted = Math.min(currentStack.getCount(), amount);
                if (extracted > 0) {
                    this.updateSnapshots(transaction);
                    currentStack = this.getStack();
                    currentStack.shrink(extracted);
                    this.setStack(currentStack);
                    return extracted;
                }
            }
            return 0;
        }

        @Override
        public ItemResource getResource(int index) {
            Objects.checkIndex(index, this.size());
            return ItemResource.of(this.getStack());
        }

        @Override
        public long getAmountAsLong(int index) {
            Objects.checkIndex(index, this.size());
            return this.getStack().getCount();
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            Objects.checkIndex(index, this.size());
            return !resource.isEmpty() && !this.isValid(resource) ? 0L : this.getCapacity(resource);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            Objects.checkIndex(index, this.size());
            checkNonEmpty(resource);
            return this.isValid(resource);
        }

        @Override
        protected ItemStack createSnapshot() {
            ItemStack original = this.getStack();
            this.setStack(original.copy());
            return original;
        }

        @Override
        protected void revertToSnapshot(ItemStack snapshot) {
            this.setStack(snapshot);
        }

        @Override
        public void updateSnapshots(TransactionContext transaction) {
            super.updateSnapshots(transaction);
            VanillaContainerWrapper.this.setChangedJournal.updateSnapshots(transaction);
            if (VanillaContainerWrapper.this.container instanceof ChestBlockEntity chest) {
                if (chest.getBlockState().getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                    BlockPos otherChestPos = chest.getBlockPos().relative(ChestBlock.getConnectedDirection(chest.getBlockState()));
                    BlockEntity other = chest.getLevel().getBlockEntity(otherChestPos);
                    if (other instanceof ChestBlockEntity) {
                        VanillaContainerWrapper.internalOf((ChestBlockEntity) other).setChangedJournal.updateSnapshots(transaction);
                    }
                }
            }
        }

        @Override
        protected void onRootCommit(ItemStack original) {
            ItemStack currentStack = this.getStack();
            if (!original.isEmpty() && original.getItem() == currentStack.getItem()) {
                ((PatchedDataComponentMap) original.getComponents()).restorePatch(currentStack.getComponentsPatch());
                original.setCount(currentStack.getCount());
                this.setStack(original);
            }
            else {
                original.setCount(0);
            }
        }

        @Override
        public String toString() {
            return "vanilla container wrapper[container=" + VanillaContainerWrapper.this.container + ",slot=" + this.index + "]";
        }
    }

    /**
     * Mirror of NeoForge's {@code net.neoforged.neoforge.transfer.transaction.RootCommitJournal}: a snapshot-less journal
     * (its snapshot is {@code null}) that enrolls in the live transaction and runs a callback exactly once, on the root
     * commit. Used to fire {@link Container#setChanged()} a single time after a (possibly nested) transaction commits.
     */
    private static final class RootCommitJournal extends SnapshotJournal<Void> {

        private final Runnable rootCommitCallback;

        private RootCommitJournal(Runnable rootCommitCallback) {
            this.rootCommitCallback = rootCommitCallback;
        }

        @Override
        protected Void createSnapshot() {
            return null;
        }

        @Override
        protected void revertToSnapshot(Void snapshot) {}

        @Override
        protected void onRootCommit(Void originalState) {
            this.rootCommitCallback.run();
        }
    }

    // --- argument validation (TransferPreconditions is package-private to dev.shadowsoffire.placebo.transfer) ----------

    private static void checkNonEmpty(ItemResource resource) {
        if (resource.isEmpty()) {
            throw new IllegalArgumentException("Expected resource to be non-empty: " + resource);
        }
    }

    private static void checkNonEmptyNonNegative(ItemResource resource, int amount) {
        checkNonEmpty(resource);
        if (amount < 0) {
            throw new IllegalArgumentException("Expected value to be non-negative: " + amount);
        }
    }
}
