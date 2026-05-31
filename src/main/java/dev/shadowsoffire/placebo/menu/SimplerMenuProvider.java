package dev.shadowsoffire.placebo.menu;

import dev.shadowsoffire.placebo.menu.MenuUtil.PosFactory;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.level.Level;

/**
 * Boilerplate for creating {@link MenuProvider}s when using {@link BlockEntityMenu}.
 *
 * <h2>Fabric port</h2>
 * Implemented as an {@link ExtendedMenuProvider} of {@link BlockPos} so the target position is shipped as the
 * extra-open-data of the {@link net.fabricmc.fabric.api.menu.v1.ExtendedMenuType ExtendedMenuType} produced by
 * {@link MenuUtil#posType(PosFactory)}. On NeoForge the pos was buffered by {@code Player.openMenu(MenuProvider, BlockPos)};
 * here {@link #getScreenOpeningData(ServerPlayer)} returns it instead, and the client {@code PosFactory} reads it back to
 * build the menu. The {@link #createMenu} path is unchanged: it builds the server-side menu directly from the stored pos.
 */
public class SimplerMenuProvider<M extends AbstractContainerMenu> implements ExtendedMenuProvider<BlockPos> {
    private final BlockPos pos;
    private final Component title;
    private final MenuConstructor menuConstructor;

    public SimplerMenuProvider(Level level, BlockPos pos, PosFactory<M> factory) {
        this.pos = pos;
        this.menuConstructor = (id, inv, player) -> factory.create(id, inv, pos);
        this.title = Component.translatable(level.getBlockState(pos).getBlock().getDescriptionId());
    }

    @Override
    public Component getDisplayName() {
        return this.title;
    }

    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pInventory, Player pPlayer) {
        return this.menuConstructor.createMenu(pContainerId, pInventory, pPlayer);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.pos;
    }

}
