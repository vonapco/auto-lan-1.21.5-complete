package org.wsm.autolan.util;

import net.minecraft.server.MinecraftServer;
import org.wsm.autolan.AutoLan;

/**
 * Утилитарный класс для работы с сервером
 */
public class ServerUtil {
    
    /**
     * Проверяет, является ли сервер интегрированным (SinglePlayer/LAN)
     * 
     * @param server Сервер для проверки
     * @return true, если это интегрированный сервер
     */
    public static boolean isIntegratedServer(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        
        try {
            // Проверяем, является ли сервер IntegratedServer по типу класса
            boolean isIntegrated = server.getClass().getName().contains("IntegratedServer");
            AutoLan.LOGGER.debug("[AutoLan] Тип сервера: {}, isIntegrated={}", 
                server.getClass().getName(), isIntegrated);
            return isIntegrated;
        } catch (Exception e) {
            AutoLan.LOGGER.error("[AutoLan] Ошибка при проверке типа сервера", e);
            return false;
        }
    }
    
    /**
     * Проверяет, является ли сервер открытым в LAN через isRemote()
     * 
     * @param server Сервер для проверки
     * @return true, если сервер открыт в LAN
     */
    public static boolean isRemoteLAN(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        
        return server.isRemote();
    }
    
    /**
     * Проверяет, включены ли читы (команды) на сервере
     * 
     * @param server Сервер для проверки
     * @return true, если читы разрешены
     */
    public static boolean areCommandsAllowed(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        
        return server.getSaveProperties().areCommandsAllowed();
    }
    
    /**
     * Проверяет, следует ли выдавать права оператора игроку на основе текущих настроек сервера.
     * 
     * @param server Сервер для проверки
     * @return true, если игроку следует выдать права оператора
     */
    public static boolean shouldGrantPermissions(MinecraftServer server) {
        boolean isRemote = isRemoteLAN(server);
        boolean customCommandsAllowed = false;
        
        try {
            if (server.getOverworld() != null && server.getOverworld().getPersistentStateManager() != null) {
                org.wsm.autolan.AutoLanState state = server.getOverworld().getPersistentStateManager().getOrCreate(org.wsm.autolan.AutoLanState.STATE_TYPE);
                customCommandsAllowed = state.getCustomCommandsAllowed();
                AutoLan.LOGGER.debug("[AutoLan] [CONFIG] Собственный флаг CustomCommandsAllowed = {}", customCommandsAllowed);
            } else {
                AutoLan.LOGGER.warn("[AutoLan] [CONFIG_MISSING] Невозможно получить состояние мира для проверки customCommandsAllowed");
            }
        } catch (Exception e) {
            AutoLan.LOGGER.error("[AutoLan] [ERROR] Ошибка при получении состояния AutoLanState", e);
        }
        
        // Проверяем только флаг customCommandsAllowed и то, что сервер в режиме LAN
        boolean result = isRemote && customCommandsAllowed;
        AutoLan.LOGGER.info("[AutoLan] [PERMISSION_CHECK] Проверка прав: isRemote={}, customCommandsAllowed={}, RESULT={}",
            isRemote, customCommandsAllowed, result);
            
        if (!result) {
            // Логируем причину отказа в правах
            if (!isRemote) {
                AutoLan.LOGGER.info("[AutoLan] [PERMISSION_DENIED_REASON] Сервер не является LAN");
            } else if (!customCommandsAllowed) {
                AutoLan.LOGGER.info("[AutoLan] [PERMISSION_DENIED_REASON] Команды не разрешены (собственный флаг)");
            }
        }
        
        return result;
    }
    
    /**
     * Проверяет, является ли сервер открытым в LAN интегрированным сервером
     * 
     * @param server Сервер для проверки
     * @return true, если это интегрированный сервер, открытый в LAN
     */
    public static boolean isIntegratedLanServer(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        
        return isIntegratedServer(server) && isRemoteLAN(server);
    }
    
    /**
     * Устанавливает флаг разрешения команд на сервере
     * 
     * @param server Сервер для изменения
     * @param allowed Разрешены ли команды
     */
    public static void setCommandsAllowed(MinecraftServer server, boolean allowed) {
        if (server == null) {
            return;
        }
        
        try {
            ((org.wsm.autolan.SetCommandsAllowed)server.getSaveProperties()).setCommandsAllowed(allowed);
            AutoLan.LOGGER.info("[AutoLan] [CONFIG] Установлен стандартный флаг areCommandsAllowed = {}", allowed);
        } catch (Exception e) {
            AutoLan.LOGGER.error("[AutoLan] [ERROR] Не удалось установить флаг areCommandsAllowed: {}", e.getMessage());
        }
    }
} 