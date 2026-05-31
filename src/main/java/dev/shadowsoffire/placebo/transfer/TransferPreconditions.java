package dev.shadowsoffire.placebo.transfer;

import dev.shadowsoffire.placebo.transfer.resource.Resource;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.TransferPreconditions} (package-private helper).
 * <p>
 * Argument-validation helpers shared by the {@link ResourceHandler} default methods and
 * {@link StacksResourceHandler}. Package-private, matching its role in the NeoForge mirror (Apotheosis never calls it).
 */
final class TransferPreconditions {

    private TransferPreconditions() {}

    static void checkNonEmpty(Resource resource) {
        if (resource.isEmpty()) {
            throw new IllegalArgumentException("Expected resource to be non-empty: " + resource);
        }
    }

    static void checkNonNegative(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Expected value to be non-negative: " + value);
        }
    }

    static void checkNonEmptyNonNegative(Resource resource, int value) {
        checkNonEmpty(resource);
        checkNonNegative(value);
    }
}
