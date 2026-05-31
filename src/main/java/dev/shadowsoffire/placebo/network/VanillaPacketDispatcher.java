package dev.shadowsoffire.placebo.network;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class VanillaPacketDispatcher {

    /**
     * Sends the block entity's vanilla update packet to all players watching it.
     */
    public static void dispatchTEToNearbyPlayers(BlockEntity tile) {
        // PlayerLookup.tracking is the Fabric equivalent of NeoForge's chunkMap.getPlayers(ChunkPos, false).
        PlayerLookup.tracking(tile).forEach(player -> {
            player.connection.send(tile.getUpdatePacket());
        });
    }

    /**
     * Sends the block entity's vanilla update packet to all players watching it.
     */
    public static void dispatchTEToNearbyPlayers(Level world, BlockPos pos) {
        BlockEntity tile = world.getBlockEntity(pos);
        if (tile != null) {
            dispatchTEToNearbyPlayers(tile);
        }
    }
}
