package dev.shadowsoffire.placebo.cap;

import dev.shadowsoffire.placebo.transfer.item.ItemResource;
import dev.shadowsoffire.placebo.transfer.item.ItemStacksResourceHandler;
import dev.shadowsoffire.placebo.transfer.transaction.TransactionContext;

/**
 * Extension of {@link ItemStacksResourceHandler} which provides access to the unrestricted {@link #extractInternal} and {@link #insertInternal} methods.
 * <p>
 * Used by {@code dev.shadowsoffire.placebo.menu.FilteredSlot} so that menus may define their own logic that differs from the logic used by automation.
 */
public class InternalItemHandler extends ItemStacksResourceHandler {

    public InternalItemHandler(int size) {
        super(size);
    }

    public int extractInternal(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return super.extract(index, resource, amount, transaction);
    }

    public int insertInternal(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return super.insert(index, resource, amount, transaction);
    }

}
