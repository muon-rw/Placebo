package dev.shadowsoffire.placebo.transfer.transaction;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Internal adapter that lifts a raw Fabric {@link net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext}
 * into the Placebo {@link TransactionContext} facade.
 * <p>
 * This is the seam used by the {@code Storage<ItemVariant>} view: when a hopper (or any Fabric consumer) opens its own
 * Fabric transaction and calls {@code Storage.insert/extract}, the per-slot view wraps that Fabric context in one of
 * these and forwards to the {@link dev.shadowsoffire.placebo.transfer.ResourceHandler} facade. Because the same Fabric
 * transaction is threaded through, the {@code ResourceHandler} path and the {@code Storage} path share one transaction
 * and one snapshot/rollback stack.
 * <p>
 * Not part of the NeoForge surface (NeoForge has a single concrete {@code Transaction}); it is the Fabric-forced
 * bridge for contexts that are not themselves Placebo {@link Transaction}s.
 */
@ApiStatus.Internal
public final class PlaceboTransactionContext implements TransactionContext {

    private final net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext delegate;

    public PlaceboTransactionContext(net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext delegate) {
        this.delegate = Objects.requireNonNull(delegate, "Fabric transaction context may not be null");
    }

    @Override
    public int depth() {
        return this.delegate.nestingDepth();
    }

    @Override
    public net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext unwrap() {
        return this.delegate;
    }
}
