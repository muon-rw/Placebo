package dev.shadowsoffire.placebo.transfer;

import dev.shadowsoffire.placebo.transfer.resource.Resource;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.IndexModifier}.
 * <p>
 * Functional interface for unconditionally setting an index's resource and amount. The menu-tier
 * {@code ResourceHandlerSlot} takes one of these in its constructor; Apotheosis's {@code ReforgingMenu} passes
 * {@code itemHandler::set} (which matches the SAM, since {@code StacksResourceHandler.set(int, T, int)} has the same
 * shape). Created in this pass for completeness even though only menu-tier code consumes it.
 *
 * @param <T> the resource type.
 */
@FunctionalInterface
public interface IndexModifier<T extends Resource> {

    /**
     * Sets the given index to hold {@code amount} of {@code resource}.
     */
    void set(int index, T resource, int amount);
}
