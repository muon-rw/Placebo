package dev.shadowsoffire.placebo.transfer.energy;

import dev.shadowsoffire.placebo.transfer.transaction.SnapshotJournal;
import dev.shadowsoffire.placebo.transfer.transaction.TransactionContext;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Mirror of {@code net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler}.
 * <p>
 * An {@link EnergyHandler} backed by a single {@code int} store, with transactional state provided by a
 * {@link SnapshotJournal} (itself a Fabric {@code SnapshotParticipant}). The {@code insert} / {@code extract} bodies
 * are reproduced verbatim from NeoForge — capacity clamping, {@code maxInsert}/{@code maxExtract} throttling, and the
 * snapshot-before-mutate ordering all match — so a caller that opens a transaction and aborts it sees the energy
 * reverted, and the {@link #onEnergyChanged(int)} hook fires exactly once on the root commit with the pre-transaction
 * amount. This is kept self-contained on the Placebo transaction system (NOT Team Reborn), mirroring the item side.
 *
 * <h2>Fabric-forced deviations</h2>
 * <ul>
 * <li>NeoForge's {@code SimpleEnergyHandler implements EnergyHandler, ValueIOSerializable}. {@code ValueIOSerializable}
 * lives outside the transfer mirror and does not exist on Fabric, so it is NOT implemented here. {@link #serialize(ValueOutput)}
 * / {@link #deserialize(ValueInput)} are kept as plain public methods with identical names/signatures over vanilla
 * {@code ValueOutput}/{@code ValueInput} — exactly the deviation already taken by {@code StacksResourceHandler}.</li>
 * <li>NeoForge's {@code TransferPreconditions} is package-private to {@code net.neoforged.neoforge.transfer}; the
 * equivalent non-negative guard is inlined here as {@link #checkNonNegative(int)} (the Placebo {@code TransferPreconditions}
 * is package-private to {@code dev.shadowsoffire.placebo.transfer} and not visible from this sub-package). Behaviour is
 * identical.</li>
 * </ul>
 */
public class SimpleEnergyHandler implements EnergyHandler {

    protected int energy;
    protected int capacity;
    protected int maxInsert;
    protected int maxExtract;

    private final EnergyJournal energyJournal = new EnergyJournal();

    public SimpleEnergyHandler(int capacity) {
        this(capacity, capacity);
    }

    public SimpleEnergyHandler(int capacity, int maxTransfer) {
        this(capacity, maxTransfer, maxTransfer);
    }

    public SimpleEnergyHandler(int capacity, int maxInsert, int maxExtract) {
        this(capacity, maxInsert, maxExtract, 0);
    }

    public SimpleEnergyHandler(int capacity, int maxInsert, int maxExtract, int energy) {
        checkNonNegative(capacity);
        checkNonNegative(maxInsert);
        checkNonNegative(maxExtract);
        checkNonNegative(energy);
        this.capacity = capacity;
        this.maxInsert = maxInsert;
        this.maxExtract = maxExtract;
        this.energy = energy;
    }

    /**
     * Serializes the stored energy under the {@code "energy"} key.
     */
    public void serialize(ValueOutput output) {
        output.putInt("energy", this.energy);
    }

    /**
     * Deserializes the stored energy from the {@code "energy"} key, clamping to non-negative.
     */
    public void deserialize(ValueInput input) {
        this.energy = Math.max(0, input.getIntOr("energy", 0));
    }

    /**
     * Unconditionally sets the stored energy, firing {@link #onEnergyChanged(int)} if it changed. Mirror of NeoForge
     * {@code set(int)}; consumed by {@code SimpleDataSlots.EnergyDataSlot} (as {@code energy::set}).
     */
    public void set(int amount) {
        checkNonNegative(amount);
        if (this.energy != amount) {
            int previousAmount = this.energy;
            this.energy = amount;
            this.onEnergyChanged(previousAmount);
        }
    }

    /**
     * Invoked when the stored energy changes (after the root commit for transactional changes, immediately for
     * {@link #set(int)}), with the previous amount. No-op by default. Mirror of NeoForge {@code onEnergyChanged}.
     */
    protected void onEnergyChanged(int previousAmount) {}

    @Override
    public long getAmountAsLong() {
        return this.energy;
    }

    @Override
    public long getCapacityAsLong() {
        return this.capacity;
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        checkNonNegative(amount);
        int inserted = Math.min(this.capacity - this.energy, Math.min(amount, this.maxInsert));
        if (inserted > 0) {
            this.energyJournal.updateSnapshots(transaction);
            this.energy += inserted;
            return inserted;
        }
        return 0;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        checkNonNegative(amount);
        int extracted = Math.min(this.energy, Math.min(amount, this.maxExtract));
        if (extracted > 0) {
            this.energyJournal.updateSnapshots(transaction);
            this.energy -= extracted;
            return extracted;
        }
        return 0;
    }

    private static void checkNonNegative(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Expected value to be non-negative: " + value);
        }
    }

    /**
     * Snapshot journal over the single {@code energy} field. Mirrors NeoForge's inner {@code EnergyJournal}: snapshots
     * the amount before the first mutation at a transaction depth, reverts on abort, and fires
     * {@link #onEnergyChanged(int)} on root commit with the pre-transaction amount.
     */
    private class EnergyJournal extends SnapshotJournal<Integer> {

        @Override
        protected Integer createSnapshot() {
            return SimpleEnergyHandler.this.energy;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            SimpleEnergyHandler.this.energy = snapshot;
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            int previousAmount = originalState;
            if (SimpleEnergyHandler.this.energy != previousAmount) {
                SimpleEnergyHandler.this.onEnergyChanged(previousAmount);
            }
        }
    }
}
