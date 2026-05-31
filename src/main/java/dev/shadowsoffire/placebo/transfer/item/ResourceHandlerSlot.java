package dev.shadowsoffire.placebo.transfer.item;

import dev.shadowsoffire.placebo.transfer.IndexModifier;
import dev.shadowsoffire.placebo.transfer.ResourceHandler;
import dev.shadowsoffire.placebo.transfer.transaction.Transaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.item.ResourceHandlerSlot}.
 * <p>
 * A menu {@link Slot} backed by a {@link ResourceHandler}{@code <}{@link ItemResource}{@code >} index rather than a
 * vanilla {@code Container} slot. Reads go through {@link ResourceHandler#getResource(int)} /
 * {@link ResourceHandler#getAmountAsInt(int)}; writes go through the {@link IndexModifier} SAM (e.g.
 * {@code itemHandler::set}). Placebo's {@code FilteredSlot}, Apotheosis's {@code ReforgingResultSlot}, and
 * Apothic-Enchanting's {@code ApothEnchantmentMenu} all extend or construct this class, so the constructor signature
 * {@code (ResourceHandler<ItemResource>, IndexModifier<ItemResource>, int index, int x, int y)} and the public
 * {@link #getResourceHandler()} accessor are preserved exactly.
 *
 * <h2>Fabric-forced deviation</h2>
 * NeoForge patches vanilla {@code Slot} to add {@code getSlotIndex()}; Fabric does not. So that subclasses (Apotheosis's
 * {@code ReforgingResultSlot} calls {@code this.getSlotIndex()}) keep the NeoForge surface, {@link #getSlotIndex()} is
 * provided here as a thin shim over the vanilla {@link Slot#getContainerSlot()} accessor - which returns the same
 * {@code slot} field NeoForge's patch returned - and the bodies below mirror NeoForge verbatim. Behaviour is identical.
 */
public class ResourceHandlerSlot extends StackCopySlot {

    private final ResourceHandler<ItemResource> handler;
    private final IndexModifier<ItemResource> slotModifier;

    public ResourceHandlerSlot(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> slotModifier, int handlerSlot, int xPosition, int yPosition) {
        super(handlerSlot, xPosition, yPosition);
        this.handler = handler;
        this.slotModifier = slotModifier;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.isEmpty() ? false : this.handler.isValid(this.getSlotIndex(), ItemResource.of(stack));
    }

    @Override
    protected ItemStack getStackCopy() {
        return this.handler.getResource(this.getSlotIndex()).toStack(this.handler.getAmountAsInt(this.getSlotIndex()));
    }

    @Override
    protected void setStackCopy(ItemStack stack) {
        this.slotModifier.set(this.getSlotIndex(), ItemResource.of(stack), stack.getCount());
    }

    @Override
    public void onQuickCraft(ItemStack oldStackIn, ItemStack newStackIn) {}

    @Override
    public int getMaxStackSize() {
        return this.handler.getCapacityAsInt(this.getSlotIndex(), ItemResource.EMPTY);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return this.handler.getCapacityAsInt(this.getSlotIndex(), ItemResource.of(stack));
    }

    @Override
    public boolean mayPickup(Player player) {
        ItemResource resource = this.handler.getResource(this.getSlotIndex());
        if (resource.isEmpty()) {
            return false;
        }
        try (Transaction tx = Transaction.openRoot()) {
            return this.handler.extract(this.getSlotIndex(), resource, 1, tx) == 1;
        }
    }

    public ResourceHandler<ItemResource> getResourceHandler() {
        return this.handler;
    }

    /**
     * Parity shim for NeoForge's vanilla-patched {@code Slot.getSlotIndex()}, which Fabric's {@link Slot} lacks. Returns
     * the {@code slot} value passed to the constructor via the vanilla {@link Slot#getContainerSlot()} accessor (the same
     * field NeoForge's patch returns), so subclasses such as {@code ReforgingResultSlot} that call {@code getSlotIndex()}
     * compile unchanged.
     */
    public int getSlotIndex() {
        return this.getContainerSlot();
    }

    /**
     * Mirror of NeoForge {@code ResourceHandlerSlot.isSameInventory(Slot)} (a NeoForge patch-added override of vanilla
     * {@code Slot}). Vanilla Fabric {@code Slot} has no such method to override, so this is a plain public method with
     * the same name/signature, kept for parity with the NeoForge surface. Note that the vanilla menu merge machinery
     * does NOT consult it on Fabric; Placebo menus route quick-move through index-range rules in {@code QuickMoveHandler}
     * rather than relying on slot/container identity, so behaviour is preserved.
     */
    public boolean isSameInventory(Slot other) {
        return other instanceof ResourceHandlerSlot rhs && rhs.handler == this.handler;
    }
}
