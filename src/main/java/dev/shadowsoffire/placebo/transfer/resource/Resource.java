package dev.shadowsoffire.placebo.transfer.resource;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.resource.Resource}.
 * <p>
 * Base marker interface for immutable, count-less resource keys. Bounds the {@code T extends Resource} type
 * variable on {@link dev.shadowsoffire.placebo.transfer.ResourceHandler} and
 * {@link dev.shadowsoffire.placebo.transfer.IndexModifier}.
 */
public interface Resource {

    /**
     * {@return whether this resource represents the empty/blank state (e.g. air for items)}
     */
    boolean isEmpty();
}
