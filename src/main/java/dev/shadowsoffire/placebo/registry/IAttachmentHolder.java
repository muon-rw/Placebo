package dev.shadowsoffire.placebo.registry;

/**
 * Fabric replacement marker for NeoForge's {@code net.neoforged.neoforge.attachment.IAttachmentHolder}.
 * <p>
 * NeoForge exposes this interface on every object capable of holding attachment data (entities, block entities, levels,
 * chunks) and passes it to the holder-capturing default-value constructor of an attachment type. Fabric's analogous concept is
 * {@code net.fabricmc.fabric.api.attachment.v1.AttachmentTarget}, which is implemented directly on the vanilla targets.
 * <p>
 * Placebo keeps this type purely so the holder-capturing {@code DeferredHelper.attachment(String, Function, UnaryOperator)}
 * overload remains source-compatible. The default-value function is invoked with {@code null} for the holder, since the Fabric
 * {@link net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.Builder#initializer initializer} provides no holder and the
 * only known consumer ignores the argument. Consumers that need holder-aware initialization should migrate to the Fabric
 * {@code AttachmentTarget} API directly.
 */
public interface IAttachmentHolder {
}
