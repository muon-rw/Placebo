package dev.shadowsoffire.placebo.mixin;

import java.nio.file.Path;
import java.util.Comparator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.sugar.Local;

import dev.shadowsoffire.placebo.datagen.DataGenBuilder;
import dev.shadowsoffire.placebo.datagen.FieldOrderingFactory;
import net.minecraft.data.DataProvider;

/**
 * Supports {@link FieldOrderingFactory} (per-object JSON field ordering) and a configurable indent width.
 * <p>
 * Ported from the NeoForge build of this mixin, which {@code @Overwrite}-replaced the {@code DataProvider.saveStable}
 * overload that NeoForge had widened with an {@code INDENT_WIDTH} field. Vanilla 26.1 has neither that field nor a
 * NeoForge-style save path, so this retargets the vanilla three-arg
 * {@link DataProvider#saveStable(net.minecraft.data.CachedOutput, JsonElement, Path)} and uses conflict-friendly
 * {@link ModifyArg} injectors against the save lambda instead of an overwrite:
 * <ul>
 * <li>the {@code jsonWriter.setIndent("  ")} argument is replaced with an indent string sized by
 * {@link DataGenBuilder#INDENT_WIDTH} (defaulting to 2, matching vanilla);</li>
 * <li>the {@code KEY_COMPARATOR} passed to {@code GsonHelper.writeValue} is replaced with the per-object comparator
 * from {@link FieldOrderingFactory.Impl#getComparatorFor}.</li>
 * </ul>
 */
@Mixin(value = DataProvider.class, remap = false)
public interface DataProviderMixin {

    /**
     * Replaces the hardcoded {@code "  "} (2-space) vanilla indent with one sized by {@link DataGenBuilder#INDENT_WIDTH}.
     */
    @ModifyArg(method = "lambda$saveStable$0", at = @At(value = "INVOKE", target = "Lcom/google/gson/stream/JsonWriter;setIndent(Ljava/lang/String;)V"))
    private static String placebo$indentWidth(String indent) {
        return " ".repeat(Math.max(0, DataGenBuilder.INDENT_WIDTH.get()));
    }

    /**
     * Replaces the vanilla {@code KEY_COMPARATOR} with the {@link FieldOrderingFactory}-resolved comparator for this
     * object and output path. {@code root} (the JSON being written) and {@code path} are the save lambda's parameters.
     */
    @ModifyArg(method = "lambda$saveStable$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/GsonHelper;writeValue(Lcom/google/gson/stream/JsonWriter;Lcom/google/gson/JsonElement;Ljava/util/Comparator;)V"), index = 2)
    private static Comparator<String> placebo$orderFields(Comparator<String> keyComparator, @Local(argsOnly = true, ordinal = 0) JsonElement root, @Local(argsOnly = true) Path path) {
        return FieldOrderingFactory.Impl.getComparatorFor(root, path);
    }

}
