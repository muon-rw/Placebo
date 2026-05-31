package dev.shadowsoffire.placebo.transfer.energy;

import com.google.common.primitives.Ints;
import dev.shadowsoffire.placebo.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.ApiStatus;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.energy.EnergyHandler}.
 * <p>
 * The transactional energy-store facade. Mirrors the NeoForge interface member-for-member so that the menu-tier
 * {@code SimpleDataSlots.EnergyDataSlot} (which reads {@link #getAmountAsInt()}) and any consumer that drives energy
 * through {@link #insert(int, TransactionContext)} / {@link #extract(int, TransactionContext)} compile unchanged under
 * the import swap.
 */
public interface EnergyHandler {

    /**
     * {@return the amount of energy stored, as a {@code long}}
     */
    long getAmountAsLong();

    /**
     * {@return the amount of energy stored, saturated to {@code int} range}
     */
    @ApiStatus.NonExtendable
    default int getAmountAsInt() {
        return Ints.saturatedCast(this.getAmountAsLong());
    }

    /**
     * {@return the maximum amount of energy that can be stored, as a {@code long}}
     */
    long getCapacityAsLong();

    /**
     * {@return the maximum amount of energy that can be stored, saturated to {@code int} range}
     */
    @ApiStatus.NonExtendable
    default int getCapacityAsInt() {
        return Ints.saturatedCast(this.getCapacityAsLong());
    }

    /**
     * Inserts up to {@code amount} energy within the given transaction.
     *
     * @return the amount actually inserted.
     */
    int insert(int amount, TransactionContext transaction);

    /**
     * Extracts up to {@code amount} energy within the given transaction.
     *
     * @return the amount actually extracted.
     */
    int extract(int amount, TransactionContext transaction);
}
