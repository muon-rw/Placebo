package dev.shadowsoffire.placebo.transfer;

import com.mojang.serialization.Codec;
import dev.shadowsoffire.placebo.transfer.resource.Resource;
import dev.shadowsoffire.placebo.transfer.transaction.SnapshotJournal;
import dev.shadowsoffire.placebo.transfer.transaction.TransactionContext;
import net.minecraft.core.NonNullList;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.StacksResourceHandler}.
 * <p>
 * Abstract {@link ResourceHandler} backed by a {@link NonNullList} of stacks, with per-index transactional state
 * provided by a {@link SnapshotJournal} (which is itself a Fabric {@code SnapshotParticipant}). The {@code insert} /
 * {@code extract} bodies are reproduced verbatim from NeoForge so that simulate-then-rollback, capacity clamping, and
 * the match-or-empty rule behave identically; mutations call {@code updateSnapshots(transaction)} before writing, so a
 * caller that opens a transaction and aborts it (closes without committing) sees the index reverted.
 *
 * <h2>Fabric-forced deviation</h2>
 * NeoForge's {@code StacksResourceHandler implements ResourceHandler<T>, ValueIOSerializable}. {@code ValueIOSerializable}
 * lives in {@code net.neoforged.neoforge.common.util} (outside the transfer mirror) and does not exist on Fabric, so it
 * is NOT implemented here. The {@link #serialize(ValueOutput)} / {@link #deserialize(ValueInput)} methods are kept as
 * plain public methods (same names/signatures over vanilla {@code ValueOutput}/{@code ValueInput}), preserving behaviour.
 * Apotheosis serializes these handlers through NeoForge's {@code ValueOutput.putChild}/{@code ValueInput.readChild}
 * extensions, which are a separate serialization-plumbing concern outside this transfer mirror.
 *
 * @param <S> the concrete stack type (e.g. {@link net.minecraft.world.item.ItemStack}).
 * @param <T> the resource type (e.g. {@link dev.shadowsoffire.placebo.transfer.item.ItemResource}).
 */
public abstract class StacksResourceHandler<S, T extends Resource> implements ResourceHandler<T> {

    public static final String VALUE_IO_KEY = "stacks";

    protected final S emptyStack;
    protected NonNullList<S> stacks;
    protected final Codec<NonNullList<S>> codec;
    private final ArrayList<StackJournal> snapshotJournals;

    protected StacksResourceHandler(int size, S emptyStack, Codec<S> stackCodec) {
        this(NonNullList.withSize(size, emptyStack), emptyStack, stackCodec);
    }

    protected StacksResourceHandler(NonNullList<S> stacks, S emptyStack, Codec<S> stackCodec) {
        this.emptyStack = emptyStack;
        this.stacks = this.mutableCopyOf(stacks);
        this.codec = stackCodec.listOf().xmap(this::mutableCopyOf, Function.identity());
        this.snapshotJournals = new ArrayList<>(this.stacks.size());
        this.updateStacksSize();
    }

    @SuppressWarnings("unchecked")
    private NonNullList<S> mutableCopyOf(Collection<S> list) {
        return NonNullList.of(this.emptyStack, (S[]) list.toArray());
    }

    protected void setStacks(NonNullList<S> stacks) {
        this.stacks = this.mutableCopyOf(stacks);
        this.updateStacksSize();
    }

    private void updateStacksSize() {
        this.snapshotJournals.ensureCapacity(this.stacks.size());
        while (this.snapshotJournals.size() < this.stacks.size()) {
            this.snapshotJournals.add(new StackJournal(this.snapshotJournals.size()));
        }
        if (this.snapshotJournals.size() > this.stacks.size()) {
            this.snapshotJournals.subList(this.stacks.size(), this.snapshotJournals.size()).clear();
        }
    }

    /**
     * Serializes the backing stack list under the {@value #VALUE_IO_KEY} key.
     */
    public void serialize(ValueOutput output) {
        output.store(VALUE_IO_KEY, this.codec, this.stacks);
    }

    /**
     * Deserializes the backing stack list from the {@value #VALUE_IO_KEY} key.
     */
    public void deserialize(ValueInput input) {
        input.read(VALUE_IO_KEY, this.codec).ifPresent(l -> {
            this.stacks = l;
            this.updateStacksSize();
        });
    }

    /**
     * Unconditionally sets the given index to hold {@code amount} of {@code resource}, firing
     * {@link #onContentsChanged(int, Object)}. Mirror of NeoForge {@code set}; matches the {@link IndexModifier} SAM.
     */
    public void set(int index, T resource, int amount) {
        TransferPreconditions.checkNonNegative(amount);
        if (resource.isEmpty() && amount > 0) {
            throw new IllegalArgumentException("Resource is empty but the amount is positive: " + amount);
        }
        S oldContents = this.stacks.set(index, this.getStackFrom(resource, amount));
        this.onContentsChanged(index, oldContents);
    }

    protected abstract T getResourceFrom(S stack);

    protected abstract int getAmountFrom(S stack);

    protected abstract S getStackFrom(T resource, int amount);

    protected abstract S copyOf(S stack);

    protected boolean matches(S stack, T resource) {
        return this.getResourceFrom(stack).equals(resource);
    }

    @Override
    public boolean isValid(int index, T resource) {
        return true;
    }

    protected abstract int getCapacity(int index, T resource);

    protected void onContentsChanged(int index, S previousContents) {}

    public NonNullList<S> copyToList() {
        return this.mutableCopyOf(this.stacks);
    }

    @Override
    public int size() {
        return this.stacks.size();
    }

    @Override
    public T getResource(int index) {
        Objects.checkIndex(index, this.size());
        return this.getResourceFrom(this.stacks.get(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        Objects.checkIndex(index, this.size());
        return this.getAmountFrom(this.stacks.get(index));
    }

    @Override
    public long getCapacityAsLong(int index, T resource) {
        Objects.checkIndex(index, this.size());
        return !resource.isEmpty() && !this.isValid(index, resource) ? 0L : (long) this.getCapacity(index, resource);
    }

    @Override
    public int insert(int index, T resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, this.size());
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        S currentStack = this.stacks.get(index);
        int currentAmount = this.getAmountFrom(currentStack);
        if ((currentAmount == 0 || this.matches(currentStack, resource)) && this.isValid(index, resource)) {
            int inserted = Math.min(amount, this.getCapacity(index, resource) - currentAmount);
            if (inserted > 0) {
                this.snapshotJournals.get(index).updateSnapshots(transaction);
                this.stacks.set(index, this.getStackFrom(resource, currentAmount + inserted));
                return inserted;
            }
        }
        return 0;
    }

    @Override
    public int extract(int index, T resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, this.size());
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        S currentStack = this.stacks.get(index);
        if (this.matches(currentStack, resource)) {
            int currentAmount = this.getAmountFrom(currentStack);
            int extracted = Math.min(amount, currentAmount);
            if (extracted > 0) {
                this.snapshotJournals.get(index).updateSnapshots(transaction);
                this.stacks.set(index, this.getStackFrom(resource, currentAmount - extracted));
                return extracted;
            }
        }
        return 0;
    }

    /**
     * Per-index snapshot journal. Mirrors NeoForge's inner {@code StackJournal}: snapshots the index's stack before the
     * first mutation at a transaction depth, reverts on abort, and fires {@link #onContentsChanged(int, Object)} on root
     * commit with the pre-transaction stack.
     */
    private class StackJournal extends SnapshotJournal<S> {

        private final int index;

        private StackJournal(int index) {
            this.index = index;
        }

        @Override
        protected S createSnapshot() {
            return StacksResourceHandler.this.copyOf(StacksResourceHandler.this.stacks.get(this.index));
        }

        @Override
        protected void revertToSnapshot(S snapshot) {
            StacksResourceHandler.this.stacks.set(this.index, snapshot);
        }

        @Override
        protected void onRootCommit(S originalState) {
            StacksResourceHandler.this.onContentsChanged(this.index, originalState);
        }
    }
}
