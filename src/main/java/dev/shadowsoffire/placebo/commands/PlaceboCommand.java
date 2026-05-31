package dev.shadowsoffire.placebo.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class PlaceboCommand {

    /**
     * Fabric replacement for NeoForge's {@code RegisterCommandsEvent}: registers {@link #register} with
     * {@link CommandRegistrationCallback}. Call once from {@code ModInitializer#onInitialize}.
     */
    public static void bootstrap() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> register(dispatcher, buildContext));
    }

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher, CommandBuildContext ctx) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("placebo");
        SerializeLootTableCommand.register(builder, ctx);
        HandToJsonCommand.register(builder);
        GetDimensionTypeCommand.register(builder);
        pDispatcher.register(builder);
    }

}
