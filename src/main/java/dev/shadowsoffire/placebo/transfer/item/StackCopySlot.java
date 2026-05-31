package dev.shadowsoffire.placebo.transfer.item;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Mirror of {@code net.neoforged.neoforge.world.inventory.StackCopySlot}.
 * <p>
 * A vanilla {@link Slot} subclass whose contents are computed on demand rather than stored in a backing
 * {@link Container}. Subclasses supply the stack via {@link #getStackCopy()} and accept writes via
 * {@link #setStackCopy(ItemStack)}; the no-op {@code emptyInventory} is only present to satisfy the vanilla
 * {@code Slot} constructor (it is never read or written). The {@link #getItem()} / {@link #set(ItemStack)} /
 * {@link #setChanged()} / {@link #remove(int)} bodies are reproduced verbatim from NeoForge so the
 * cached-returned-stack semantics (the menu syncs against the last value returned by {@link #getItem()}) match.
 *
 * <h2>Fabric-forced deviation</h2>
 * None. This is a faithful port; the class compiles unchanged because every member it touches
 * ({@link Slot} ctor, {@link ItemStack#matches(ItemStack, ItemStack)}, {@code split}/{@code copy}) is vanilla.
 */
public abstract class StackCopySlot extends Slot {

    private static final Container emptyInventory = new SimpleContainer(0);

    private @Nullable ItemStack cachedReturnedStack = null;

    public StackCopySlot(int slot, int x, int y) {
        super(emptyInventory, slot, x, y);
    }

    /**
     * {@return the current contents of this slot} Recomputed each call; subclasses derive it from their backing store.
     */
    protected abstract ItemStack getStackCopy();

    /**
     * Writes {@code stack} back into the subclass's backing store. Mirror of NeoForge {@code setStackCopy}.
     */
    protected abstract void setStackCopy(ItemStack stack);

    @Override
    public final ItemStack getItem() {
        return this.cachedReturnedStack = this.getStackCopy();
    }

    @Override
    public final void set(ItemStack stack) {
        this.setStackCopy(stack);
        this.cachedReturnedStack = stack;
    }

    @Override
    public final void setChanged() {
        if (this.cachedReturnedStack != null && !ItemStack.matches(this.cachedReturnedStack, this.getStackCopy())) {
            this.set(this.cachedReturnedStack);
        }
    }

    @Override
    public ItemStack remove(int amount) {
        ItemStack stack = this.getStackCopy().copy();
        ItemStack ret = stack.split(amount);
        this.set(stack);
        this.cachedReturnedStack = null;
        return ret;
    }
}
