package dev.shadowsoffire.placebo.transfer.transaction;

import org.jetbrains.annotations.ApiStatus;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.transaction.TransactionContext}.
 * <p>
 * Backed by the Fabric transaction system: a Placebo {@code TransactionContext} is a thin facade over a
 * {@link net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext}. This unifies the whole transfer
 * stack on the single per-thread Fabric transaction so that the {@link dev.shadowsoffire.placebo.transfer.ResourceHandler}
 * facade and the underlying Fabric {@link net.fabricmc.fabric.api.transfer.v1.storage.Storage} view share one
 * transaction (simulate-then-rollback works across both).
 *
 * <h2>Fabric-forced deviation</h2>
 * NeoForge declares this {@code sealed interface ... permits Transaction}. The Placebo mirror is NOT sealed because
 * the wrapping {@link Transaction} lives in the same package and must implement it, and because the bridge needs an
 * (package-private) {@link #unwrap()} accessor to reach the real Fabric context. Behaviour is identical.
 */
public interface TransactionContext {

    /**
     * {@return the nesting depth of this transaction} The outermost (root) transaction has depth {@code 0}.
     * Delegates to {@link net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext#nestingDepth()}.
     */
    int depth();

    /**
     * {@return the underlying Fabric transaction context this facade wraps}
     * <p>
     * Used by the bridge to feed the real Fabric transaction to {@code Transaction.openNested},
     * {@code Storage.insert/extract}, and {@code SnapshotParticipant.updateSnapshots}.
     */
    @ApiStatus.Internal
    net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext unwrap();
}
