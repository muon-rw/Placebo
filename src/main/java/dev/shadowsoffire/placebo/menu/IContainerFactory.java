package dev.shadowsoffire.placebo.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Placebo-owned mirror of {@code net.neoforged.neoforge.network.IContainerFactory}.
 * <p>
 * The NeoForge-shaped client-side menu factory SAM: given the container id, the opening player's {@link Inventory}, and
 * the free-form {@link RegistryFriendlyByteBuf} the server wrote, produce the menu. Downstream consumers reference it by
 * its method shape (e.g. {@code RavenEnchantmentMenu::fromBuf}), so the {@link #create(int, Inventory, RegistryFriendlyByteBuf)}
 * signature is preserved exactly. {@link MenuUtil.PosFactory} extends this (overriding {@code create} to read a
 * {@link net.minecraft.core.BlockPos} off the buffer), matching the HEAD relationship.
 *
 * <h2>Fabric-forced deviation</h2>
 * NeoForge's {@code IContainerFactory} lives in {@code net.neoforged.neoforge.network} and is consumed directly by
 * NeoForge's promoted {@code new MenuType<>(IContainerFactory, FeatureFlagSet)} constructor. Fabric has no such
 * constructor; instead {@link MenuUtil#bufType(IContainerFactory)} adapts this SAM into a Fabric
 * {@link net.fabricmc.fabric.api.menu.v1.ExtendedMenuType} whose extra-open-data is the raw byte payload (see
 * {@link MenuUtil} for the wire mechanics). The SAM surface itself is unchanged so downstream method references compile.
 *
 * @param <T> the menu type produced.
 */
@FunctionalInterface
public interface IContainerFactory<T extends AbstractContainerMenu> {

    /**
     * Creates the menu on the client from the container id, the player inventory, and the server-written data buffer.
     */
    T create(int id, Inventory inv, RegistryFriendlyByteBuf buf);
}
