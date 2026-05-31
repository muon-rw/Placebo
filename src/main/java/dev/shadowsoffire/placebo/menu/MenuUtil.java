package dev.shadowsoffire.placebo.menu;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MenuType.MenuSupplier;

/**
 * Utilities for building {@link MenuType}s and opening menus.
 *
 * <h2>Fabric port</h2>
 * NeoForge promoted {@code new MenuType<>(MenuSupplier, FeatureFlagSet)} and the data-carrying
 * {@code new MenuType<>(IContainerFactory, FeatureFlagSet)} constructors. Fabric splits these: a plain
 * {@link MenuType} has no extra-open-data path, and any menu that needs server&rarr;client open data must be a
 * {@link net.fabricmc.fabric.api.menu.v1.ExtendedMenuType ExtendedMenuType}. The factory surface
 * ({@link #type type} / {@link #posType posType} / {@link #bufType bufType}) and the {@link #openGui} helper keep their
 * HEAD names and signatures; only the implementations diverge per the table below.
 * <ul>
 * <li>{@link #type(MenuSupplier)} &mdash; unchanged; a plain {@code MenuType} with no open data.</li>
 * <li>{@link #posType(PosFactory)} &mdash; an {@code ExtendedMenuType} whose open data is a {@link BlockPos}, serialized
 * with {@link BlockPos#STREAM_CODEC}. Pairs with {@link #openGui}/{@link SimplerMenuProvider}, which supply the pos
 * server-side via {@link net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider#getScreenOpeningData}.</li>
 * <li>{@link #bufType(IContainerFactory)} &mdash; an {@code ExtendedMenuType} whose open data is the raw {@code byte[]}
 * the server wrote (mirroring NeoForge's {@code IContainerFactory} free-form {@link RegistryFriendlyByteBuf} contract).
 * The bytes are serialized with {@link ByteBufCodecs#BYTE_ARRAY}; on the client they are wrapped back into a
 * {@link RegistryFriendlyByteBuf} and handed to the {@link IContainerFactory}, so {@code (id, inv, buf)} consumers such
 * as {@code RavenEnchantmentMenu::fromBuf} are driven exactly as before. The server side must open the menu with an
 * {@code ExtendedMenuProvider<byte[]>} whose {@code getScreenOpeningData} returns those bytes.</li>
 * </ul>
 */
public class MenuUtil {

    /**
     * Creates a {@link MenuType} with the target menu supplier and the vanilla feature flags.
     */
    public static <T extends AbstractContainerMenu> MenuType<T> type(MenuSupplier<T> factory) {
        return new MenuType<>(factory, FeatureFlags.DEFAULT_FLAGS);
    }

    /**
     * Creates a {@link MenuType} for a menu that needs free-form server&rarr;client open data, supplied via a Placebo
     * {@link IContainerFactory}.
     * <p>
     * On Fabric this is an {@link ExtendedMenuType} whose data is the raw byte payload the server wrote. The bytes are
     * round-tripped with {@link ByteBufCodecs#BYTE_ARRAY}; the client-side factory wraps them in a fresh
     * {@link RegistryFriendlyByteBuf} (keyed to the opening player's {@link net.minecraft.core.RegistryAccess}) before
     * invoking {@code factory}, reproducing the NeoForge {@code IContainerFactory} contract. The opening side is
     * responsible for producing the byte payload via an {@code ExtendedMenuProvider<byte[]>}.
     */
    public static <T extends AbstractContainerMenu> MenuType<T> bufType(IContainerFactory<T> factory) {
        return new ExtendedMenuType<T, byte[]>(
            (id, inv, data) -> {
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), inv.player.registryAccess());
                return factory.create(id, inv, buf);
            },
            ByteBufCodecs.BYTE_ARRAY);
    }

    /**
     * Util method for the most common type of data-carrying menu - one supplying a {@link BlockPos}.
     * <p>
     * On Fabric this is an {@link ExtendedMenuType} whose open data is a {@link BlockPos}, serialized with
     * {@link BlockPos#STREAM_CODEC}. Use {@link #openGui} (or {@link SimplerMenuProvider}) to open it; that provider
     * delivers the pos to the client through {@link net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider}.
     */
    public static <T extends AbstractContainerMenu> MenuType<T> posType(PosFactory<T> factory) {
        return new ExtendedMenuType<T, BlockPos>(
            (id, inv, pos) -> factory.create(id, inv, pos),
            BlockPos.STREAM_CODEC);
    }

    /**
     * Opens the menu for the given {@link PosFactory} at the given position, returning an {@link InteractionResult}.
     * Designed for use with {@link BlockEntityMenu}.
     * <p>
     * <b>Fabric-forced deviation.</b> NeoForge's {@code Player.openMenu(MenuProvider, BlockPos)} buffers the pos into the
     * open packet itself. Fabric has no such overload; the pos is instead delivered as the
     * {@link net.fabricmc.fabric.api.menu.v1.ExtendedMenuType ExtendedMenuType} open data produced by
     * {@link SimplerMenuProvider} (an {@link net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider}). The behaviour - the
     * client menu being constructed with the table's pos - is identical.
     */
    public static <M extends AbstractContainerMenu> InteractionResult openGui(Player player, BlockPos pos, PosFactory<M> factory) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(new SimplerMenuProvider<>(player.level(), pos, factory));
        return InteractionResult.CONSUME;
    }

    @FunctionalInterface
    public static interface PosFactory<T extends AbstractContainerMenu> extends IContainerFactory<T> {
        T create(int id, Inventory pInv, BlockPos pos);

        @Override
        default T create(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
            return this.create(id, inv, buf.readBlockPos());
        }
    }

}
