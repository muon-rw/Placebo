package dev.shadowsoffire.placebo.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The AnvilFallEvent is fired when a falling anvil lands on a block.
 * <p>
 * Ported from a NeoForge game-bus {@code Event} subclass; Fabric has no global event bus, so this is a plain data
 * carrier paired with a Fabric {@link Event} ({@link #EVENT}). The constructor and getters are unchanged from upstream.
 */
public class AnvilLandEvent {

    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(Callback.class, listeners -> event -> {
        for (Callback listener : listeners) {
            listener.onAnvilLand(event);
        }
    });

    protected final Level level;
    protected final BlockPos pos;
    protected final BlockState newState;
    protected final BlockState oldState;
    protected final FallingBlockEntity entity;

    public AnvilLandEvent(Level level, BlockPos pos, BlockState newState, BlockState oldState, FallingBlockEntity entity) {
        this.level = level;
        this.pos = pos;
        this.newState = newState;
        this.oldState = oldState;
        this.entity = entity;
    }

    public Level getLevel() {
        return this.level;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public BlockState getNewState() {
        return this.newState;
    }

    public BlockState getOldState() {
        return this.oldState;
    }

    public FallingBlockEntity getEntity() {
        return this.entity;
    }

    /**
     * Fabric replacement for {@code NeoForge.EVENT_BUS.post(new AnvilLandEvent(...))}; called from {@code AnvilBlockMixin}.
     */
    public static AnvilLandEvent post(Level level, BlockPos pos, BlockState newState, BlockState oldState, FallingBlockEntity entity) {
        AnvilLandEvent event = new AnvilLandEvent(level, pos, newState, oldState, entity);
        EVENT.invoker().onAnvilLand(event);
        return event;
    }

    @FunctionalInterface
    public interface Callback {
        void onAnvilLand(AnvilLandEvent event);
    }

}
