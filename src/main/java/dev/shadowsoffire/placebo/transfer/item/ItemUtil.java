package dev.shadowsoffire.placebo.transfer.item;

import dev.shadowsoffire.placebo.transfer.ResourceHandler;
import dev.shadowsoffire.placebo.transfer.transaction.Transaction;
import dev.shadowsoffire.placebo.transfer.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.item.ItemUtil}.
 * <p>
 * Static helpers over an item {@link ResourceHandler}. Bodies are reproduced verbatim from NeoForge, using the Placebo
 * {@link Transaction} (which wraps the Fabric transaction). Not referenced by Apotheosis in the current grep, but part
 * of the mandated surface and trivially mirrorable with no UI dependencies.
 */
public final class ItemUtil {

    private ItemUtil() {}

    /**
     * {@return the contents of the given index as an {@link ItemStack}} Empty resources yield {@link ItemStack#EMPTY}.
     */
    public static ItemStack getStack(ResourceHandler<ItemResource> handler, int index) {
        ItemResource resource = handler.getResource(index);
        return resource.isEmpty() ? ItemStack.EMPTY : resource.toStack(handler.getAmountAsInt(index));
    }

    /**
     * Attempts to insert {@code stack} into {@code handler}, optionally only simulating.
     *
     * @return the remaining (un-inserted) portion of the stack.
     */
    public static ItemStack insertItemReturnRemaining(ResourceHandler<ItemResource> handler, ItemStack stack, boolean simulate, @Nullable TransactionContext transaction) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try (Transaction tx = Transaction.open(transaction)) {
            int inserted = handler.insert(ItemResource.of(stack), stack.getCount(), tx);
            if (!simulate) {
                tx.commit();
            }
            int leftover = stack.getCount() - inserted;
            return leftover == 0 ? ItemStack.EMPTY : stack.copyWithCount(leftover);
        }
    }

    /**
     * Attempts to insert {@code stack} into a specific index of {@code handler}, optionally only simulating.
     *
     * @return the remaining (un-inserted) portion of the stack.
     */
    public static ItemStack insertItemReturnRemaining(ResourceHandler<ItemResource> handler, int index, ItemStack stack, boolean simulate, @Nullable TransactionContext transaction) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try (Transaction tx = Transaction.open(transaction)) {
            int inserted = handler.insert(index, ItemResource.of(stack), stack.getCount(), tx);
            if (!simulate) {
                tx.commit();
            }
            int leftover = stack.getCount() - inserted;
            return leftover == 0 ? ItemStack.EMPTY : stack.copyWithCount(leftover);
        }
    }
}
