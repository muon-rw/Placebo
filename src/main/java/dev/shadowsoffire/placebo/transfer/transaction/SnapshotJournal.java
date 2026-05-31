package dev.shadowsoffire.placebo.transfer.transaction;

import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.transaction.SnapshotJournal}.
 * <p>
 * Backed by Fabric's transaction system: this class extends {@link SnapshotParticipant} so it is an
 * {@code is-a} Fabric transaction close-callback ({@link net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext.CloseCallback}
 * / {@link net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext.OuterCloseCallback}) and can therefore be
 * registered against the live Fabric transaction. The per-depth snapshot/revert/bubble-up algorithm is reproduced here
 * verbatim from NeoForge's {@code SnapshotJournal} (which is byte-for-byte identical to Fabric's {@code SnapshotParticipant}),
 * with one addition: the depth-0 original is retained so that {@link #onRootCommit(Object)} can receive it on the root
 * commit, exactly as NeoForge's {@code callOnRootCommit} does. (Fabric's {@code onFinalCommit()} takes no argument, so the
 * stash is the Fabric-forced bridge.)
 * <p>
 * The protected hook set ({@link #createSnapshot()}, {@link #revertToSnapshot(Object)}, {@link #releaseSnapshot(Object)},
 * {@link #onRootCommit(Object)}) and the public {@link #updateSnapshots(TransactionContext)} match the NeoForge names and
 * signatures exactly, so Apotheosis's {@code GemCaseItemHandler extends SnapshotJournal<Snapshot>} compiles unchanged under
 * the import swap.
 *
 * @param <T> the snapshot type captured before mutation.
 */
public abstract class SnapshotJournal<T> extends SnapshotParticipant<T> {

    private static final Object NO_SNAPSHOT = new Object();

    private final ArrayList<T> snapshots = new ArrayList<>();
    private @Nullable T originalState = null;

    /**
     * {@return a fresh snapshot capturing the current state} Called the first time this journal is touched within a
     * transaction depth.
     */
    @Override
    protected abstract T createSnapshot();

    /**
     * Reverts the participant's state to the given snapshot. Mirror of NeoForge {@code revertToSnapshot}.
     */
    protected abstract void revertToSnapshot(T snapshot);

    /**
     * Releases resources held by a snapshot that is no longer needed. No-op by default. Mirror of NeoForge
     * {@code releaseSnapshot} / Fabric {@code releaseSnapshot}.
     */
    @Override
    protected void releaseSnapshot(T snapshot) {}

    /**
     * Invoked once, after the outermost (root) transaction commits, with the state as it was BEFORE the transaction
     * began. Mirror of NeoForge {@code onRootCommit}. Use this for side effects such as {@code setChanged()}.
     */
    protected void onRootCommit(T originalState) {}

    /**
     * Bridges Fabric's {@link SnapshotParticipant#readSnapshot(Object)} to the NeoForge-named
     * {@link #revertToSnapshot(Object)}. Not part of the NeoForge surface; package consumers always call
     * {@code revertToSnapshot}.
     */
    @Override
    protected final void readSnapshot(T snapshot) {
        this.revertToSnapshot(snapshot);
    }

    /**
     * Bridges Fabric's {@link SnapshotParticipant#onFinalCommit()} (no-arg) to the NeoForge-named
     * {@link #onRootCommit(Object)} by replaying the stashed depth-0 original. Not part of the NeoForge surface.
     */
    @Override
    protected final void onFinalCommit() {
        this.callOnRootCommit();
    }

    /**
     * Registers a snapshot for the current transaction depth, if one has not already been taken at this depth, and
     * enrolls this journal as a close callback on the live Fabric transaction. Mirror of NeoForge
     * {@code updateSnapshots(TransactionContext)}.
     * <p>
     * Must be called immediately before mutating participant state inside an open transaction.
     *
     * @param transaction the Placebo transaction context (wrapping the live Fabric transaction).
     */
    public void updateSnapshots(TransactionContext transaction) {
        this.updateSnapshotsInternal(transaction.unwrap());
    }

    @SuppressWarnings("unchecked")
    private void updateSnapshotsInternal(net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
        int currentDepth = transaction.nestingDepth();
        this.snapshots.ensureCapacity(currentDepth);

        for (int i = this.snapshots.size(); i <= currentDepth; i++) {
            this.snapshots.add((T) NO_SNAPSHOT);
        }

        if (this.snapshots.get(currentDepth) == NO_SNAPSHOT) {
            this.snapshots.set(currentDepth, this.createSnapshot());
            transaction.addCloseCallback(this);
        }
    }

    /**
     * Fabric close callback. Reproduces NeoForge {@code SnapshotJournal.onClose}: on abort, revert; on a nested commit,
     * bubble the snapshot down one depth; on the root commit, stash the original and enroll the outer-close callback.
     */
    @Override
    public void onClose(net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction, net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext.Result result) {
        int currentDepth = transaction.nestingDepth();
        T snapshot = this.snapshots.remove(currentDepth);
        if (result.wasAborted()) {
            this.revertToSnapshot(snapshot);
            this.releaseSnapshot(snapshot);
        }
        else if (currentDepth <= 0) {
            if (this.originalState == null) {
                this.originalState = snapshot;
                transaction.addOuterCloseCallback(this);
            }
            else {
                this.releaseSnapshot(snapshot);
            }
        }
        else if (this.snapshots.get(currentDepth - 1) == NO_SNAPSHOT) {
            this.snapshots.set(currentDepth - 1, snapshot);
            transaction.getOpenTransaction(currentDepth - 1).addCloseCallback(this);
        }
        else {
            this.releaseSnapshot(snapshot);
        }
    }

    /**
     * Fabric outer-close callback. Delegates to {@link #callOnRootCommit()}.
     */
    @Override
    public void afterOuterClose(net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext.Result result) {
        this.callOnRootCommit();
    }

    private void callOnRootCommit() {
        T original = this.originalState;
        this.originalState = null;
        this.onRootCommit(original);
        this.releaseSnapshot(original);
    }
}
