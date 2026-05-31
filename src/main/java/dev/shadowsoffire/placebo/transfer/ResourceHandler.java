package dev.shadowsoffire.placebo.transfer;

import com.google.common.primitives.Ints;
import dev.shadowsoffire.placebo.transfer.resource.Resource;
import dev.shadowsoffire.placebo.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.ApiStatus;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.ResourceHandler}.
 * <p>
 * The slot-indexed transfer facade. Apotheosis implements this interface directly (e.g.
 * {@code SalvagingTableTile.SalvagingItemHandler}, {@code GemCaseTile.GemCaseItemHandler}) and also consumes it as a
 * field / return type. Every abstract member and the {@link #getAmountAsInt(int)} / {@link #getCapacityAsInt(int, Resource)}
 * defaults Apotheosis relies on are reproduced with identical names and signatures.
 *
 * @param <T> the resource type handled (e.g. {@link dev.shadowsoffire.placebo.transfer.item.ItemResource}).
 */
public interface ResourceHandler<T extends Resource> {

    /**
     * {@return the number of indices (slots) in this handler}
     */
    int size();

    /**
     * {@return the resource stored at the given index, or the empty resource if the index is empty}
     */
    T getResource(int index);

    /**
     * {@return the amount stored at the given index, as a {@code long}}
     */
    long getAmountAsLong(int index);

    /**
     * {@return the amount stored at the given index, saturated to {@code int} range}
     */
    @ApiStatus.NonExtendable
    default int getAmountAsInt(int index) {
        return Ints.saturatedCast(this.getAmountAsLong(index));
    }

    /**
     * {@return the capacity of the given index for the given resource, as a {@code long}}
     */
    long getCapacityAsLong(int index, T resource);

    /**
     * {@return the capacity of the given index for the given resource, saturated to {@code int} range}
     */
    @ApiStatus.NonExtendable
    default int getCapacityAsInt(int index, T resource) {
        return Ints.saturatedCast(this.getCapacityAsLong(index, resource));
    }

    /**
     * {@return whether the given resource is valid for the given index}
     */
    boolean isValid(int index, T resource);

    /**
     * Inserts up to {@code amount} of {@code resource} into the given index within the given transaction.
     *
     * @return the amount actually inserted.
     */
    int insert(int index, T resource, int amount, TransactionContext transaction);

    /**
     * Inserts up to {@code amount} of {@code resource}, distributing across indices.
     *
     * @return the total amount actually inserted.
     */
    default int insert(T resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int inserted = 0;
        int size = this.size();
        for (int index = 0; index < size; index++) {
            inserted += this.insert(index, resource, amount - inserted, transaction);
            if (inserted == amount) {
                break;
            }
        }
        return inserted;
    }

    /**
     * Extracts up to {@code amount} of {@code resource} from the given index within the given transaction.
     *
     * @return the amount actually extracted.
     */
    int extract(int index, T resource, int amount, TransactionContext transaction);

    /**
     * Extracts up to {@code amount} of {@code resource}, drawing across indices.
     *
     * @return the total amount actually extracted.
     */
    default int extract(T resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int extracted = 0;
        int size = this.size();
        for (int index = 0; index < size; index++) {
            extracted += this.extract(index, resource, amount - extracted, transaction);
            if (extracted == amount) {
                break;
            }
        }
        return extracted;
    }

    /**
     * {@return the {@link Class} object for {@code ResourceHandler<T>}} Mirrors NeoForge's {@code asClass()}.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static <T extends Resource> Class<ResourceHandler<T>> asClass() {
        return (Class) ResourceHandler.class;
    }
}
