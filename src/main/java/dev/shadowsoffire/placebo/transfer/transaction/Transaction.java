package dev.shadowsoffire.placebo.transfer.transaction;

import org.jetbrains.annotations.Nullable;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.transaction.Transaction}.
 * <p>
 * Wraps a {@link net.fabricmc.fabric.api.transfer.v1.transaction.Transaction} (Fabric's {@code Transaction} is
 * {@code @NonExtendable}, so this is composition, not inheritance). Fabric owns the single per-thread transaction
 * stack; this class is the open/commit/close handle the rest of the mirror and Apotheosis use.
 * <p>
 * Semantic parity with NeoForge:
 * <ul>
 * <li>{@link #openRoot()} -> {@code Transaction.openOuter()}.</li>
 * <li>{@link #open(TransactionContext)} with {@code null} parent -> {@code openOuter()}; with a parent ->
 * {@code Transaction.openNested(parent.unwrap())}.</li>
 * <li>{@link #commit()} delegates to {@code commit()} (closes without aborting).</li>
 * <li>{@link #close()} delegates to {@code close()} which aborts only if still open, matching NeoForge's close
 * guard ({@code if (open) close(true)}).</li>
 * <li>{@link #depth()} -> {@code nestingDepth()}.</li>
 * </ul>
 * Implements {@link AutoCloseable} so {@code try (Transaction probe = Transaction.open(transaction)) { ...; probe.commit(); }}
 * works exactly as in the NeoForge API.
 */
public final class Transaction implements AutoCloseable, TransactionContext {

    private final net.fabricmc.fabric.api.transfer.v1.transaction.Transaction delegate;

    private Transaction(net.fabricmc.fabric.api.transfer.v1.transaction.Transaction delegate) {
        this.delegate = delegate;
    }

    /**
     * Opens a new root (outermost) transaction.
     *
     * @return the opened transaction; must be {@link #close() closed} (or {@link #commit() committed}).
     */
    public static Transaction openRoot() {
        return new Transaction(net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter());
    }

    /**
     * Opens a transaction nested inside {@code parent}, or a new root transaction if {@code parent} is {@code null}.
     * <p>
     * Mirrors {@code Transaction.open(@Nullable TransactionContext)}. A nested transaction may be committed or
     * aborted independently; aborting reverts only the mutations performed since this nested transaction was opened.
     *
     * @param parent the parent context to nest under, or {@code null} for a root transaction.
     * @return the opened transaction.
     */
    public static Transaction open(@Nullable TransactionContext parent) {
        if (parent == null) {
            return openRoot();
        }
        return new Transaction(net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openNested(parent.unwrap()));
    }

    /**
     * Commits this transaction, making its (and any committed nested) mutations permanent. Closes the transaction.
     */
    public void commit() {
        this.delegate.commit();
    }

    /**
     * Closes this transaction. If it has not been {@link #commit() committed}, all of its mutations are reverted.
     * Safe to call after {@link #commit()} (the underlying Fabric transaction is already closed and aborting is a no-op).
     */
    @Override
    public void close() {
        this.delegate.close();
    }

    @Override
    public int depth() {
        return this.delegate.nestingDepth();
    }

    @Override
    public net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext unwrap() {
        return this.delegate;
    }

    @Override
    public String toString() {
        return "Transaction[depth=" + this.depth() + "]";
    }
}
