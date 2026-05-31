package dev.shadowsoffire.placebo.json;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;

public class JsonUtil {

    /**
     * Checks if an item is empty, and if it is, returns false and logs the key.
     */
    public static boolean checkAndLogEmpty(JsonElement e, Identifier id, Identifier regId, Logger logger) {
        String s = e.toString();
        if (s.isEmpty() || "{}".equals(s)) {
            logger.error("Ignoring {} item with id {} as it is empty.  Please switch to a condition-false json instead of an empty one.", regId, id);
            return false;
        }
        return true;
    }

    /**
     * Checks the conditions on a Json, and returns true if they are met.
     * <p>
     * Ported from NeoForge's {@code neoforge:conditions} to Fabric's {@code fabric:load_conditions} key (see
     * {@link ResourceConditions#CONDITIONS_KEY}); a Json with no conditions key always passes.
     *
     * @param e            The Json being checked.
     * @param id           The ID of that json.
     * @param regId        The id of the registry the json belongs to, for logging.
     * @param logger       The logger to log to.
     * @param registryInfo The registry lookup used for resolving conditions. May be {@code null}, per the Fabric
     *                     {@link ResourceCondition#test(RegistryOps.RegistryInfoLookup)} contract.
     * @return True if the item's conditions are met, false otherwise.
     */
    public static boolean checkConditions(JsonElement e, Identifier id, Identifier regId, Logger logger, RegistryOps.RegistryInfoLookup registryInfo) {
        if (!e.isJsonObject()) {
            return true;
        }

        JsonElement conditionsElement = e.getAsJsonObject().get(ResourceConditions.CONDITIONS_KEY);
        if (conditionsElement == null) {
            return true;
        }

        DataResult<ResourceCondition> parsed = ResourceCondition.CONDITION_CODEC.parse(JsonOps.INSTANCE, conditionsElement);
        if (parsed.isError()) {
            logger.error("Failed to parse resource conditions for {} item with id {}, skipping: {}", regId, id, parsed.error().get().message());
            return false;
        }

        if (parsed.getOrThrow().test(registryInfo)) {
            return true;
        }
        logger.trace("Skipping loading {} item with id {} as it's conditions were not met", regId, id);
        return false;
    }

}
