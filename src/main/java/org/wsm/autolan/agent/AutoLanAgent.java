package org.wsm.autolan.agent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wsm.autolan.AutoLan;
import org.wsm.autolan.AutoLanConfig;
import org.wsm.autolan.agent.model.*;
import com.github.alexdlaird.ngrok.protocol.Tunnel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class AutoLanAgent {
    public static final Logger LOGGER = LoggerFactory.getLogger("AutoLanAgent");
    private static final String CLIENT_ID_FILE = "autolan_client_id.txt";
    
    private final ApiClient apiClient;
    private final Path clientIdPath;
    private ScheduledExecutorService scheduler;
    private String clientId;
    private final AutoLanConfig config;
    private volatile boolean worldActive = false;

    public AutoLanAgent() {
        this.config = AutoLan.CONFIG.getConfig();
        this.apiClient = new ApiClient(config.serverUrl, config.apiKey);
        this.clientIdPath = FabricLoader.getInstance().getGameDir().resolve(CLIENT_ID_FILE);
        
        // Регистрируем обработчики событий подключения/отключения
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            worldActive = true;
            LOGGER.info("World joined, setting status to active");
        });
        
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            worldActive = false;
            LOGGER.info("World disconnected, setting status to inactive");
        });
    }

    public void init() {
        if (!config.agentEnabled) {
            LOGGER.info("Auto-LAN Agent is disabled in the config.");
            return;
        }
        LOGGER.info("Auto-LAN Agent is initializing...");

        loadOrRegisterClientId();

        if (clientId == null) {
            LOGGER.error("Failed to get client ID. Agent will not start.");
            return;
        }

        startBackgroundTasks();
    }

    public void shutdown() {
        LOGGER.info("Auto-LAN Agent is shutting down...");
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    private void loadOrRegisterClientId() {
        try {
            if (Files.exists(clientIdPath)) {
                this.clientId = Files.readString(clientIdPath).trim();
                LOGGER.info("Loaded client ID from file: {}", this.clientId);
            } else {
                performRegistration();
            }
        } catch (IOException e) {
            LOGGER.error("Error loading client ID, will try to register a new one.", e);
            performRegistration();
        }
    }

    private void performRegistration() {
        try {
            String computerName = getComputerName();
            RegistrationRequest request = new RegistrationRequest(computerName);
            
            try {
                RegistrationResponse response = apiClient.register(request);
                if (response != null && response.getClientId() != null) {
                    this.clientId = response.getClientId();
                    Files.writeString(clientIdPath, this.clientId);
                    LOGGER.info("Successfully registered and saved new client ID: {}", clientId);
                } else {
                    LOGGER.error("Registration response was null or did not contain a client ID.");
                }
            } catch (IOException e) {
                LOGGER.error("Network error during registration: {}", e.getMessage());
                // Планируем повторную попытку через некоторое время
                scheduleRetryRegistration();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to register client", e);
        }
    }

    private void scheduleRetryRegistration() {
        if (scheduler != null && !scheduler.isShutdown()) {
            LOGGER.info("Scheduling registration retry in 60 seconds");
            scheduler.schedule(this::performRegistration, 60, TimeUnit.SECONDS);
        }
    }

    private void startBackgroundTasks() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        
        scheduler = Executors.newScheduledThreadPool(2); // One for heartbeat, one for commands
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::fetchAndExecuteCommands, 0, 5, TimeUnit.SECONDS);
        
        LOGGER.info("Auto-LAN agent started with client ID: {}", clientId);
    }

    private void sendHeartbeat() {
        try {
            HeartbeatPayload payload = new HeartbeatPayload();
            payload.setClientId(this.clientId);
            payload.setStatus(gatherStatus());
            payload.setNgrokUrls(getNgrokUrls());

            try {
                apiClient.sendHeartbeat(payload);
                LOGGER.debug("Heartbeat sent successfully");
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("404")) {
                    LOGGER.warn("Client ID not recognized by server. Re-registering...");
                    performRegistration();
                } else {
                    // Для других ошибок просто логируем, но не прерываем работу агента
                    LOGGER.error("Failed to send heartbeat: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            // Перехватываем все исключения, чтобы не нарушить работу планировщика
            LOGGER.error("Error preparing heartbeat data", e);
        }
    }

    private Map<String, String> getNgrokUrls() {
        Map<String, String> urls = new HashMap<>();
        
        // Только если мир активен, получаем туннели из Auto-LAN
        if (worldActive) {
            // Получаем URLs из активных туннелей
            urls.putAll(AutoLan.activeTunnels);
            
            // Также проверяем Ngrok клиент, как дополнительный источник
            if (AutoLan.NGROK_CLIENT != null) {
                try {
                    for (Tunnel tunnel : AutoLan.NGROK_CLIENT.getTunnels()) {
                        urls.put("minecraft", tunnel.getPublicUrl());
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to get ngrok tunnels", e);
                }
            }
        }
        
        return urls;
    }

    private void fetchAndExecuteCommands() {
        if (clientId == null || clientId.isEmpty()) {
            // Если ID клиента отсутствует, пытаемся его получить
            loadOrRegisterClientId();
            return;
        }
        
        try {
            CommandsResponse response = apiClient.getCommands(this.clientId);
            if (response != null && response.getCommands() != null) {
                for (Command command : response.getCommands()) {
                    executeCommand(command);
                }
            }
        } catch (IOException e) {
            // При ошибке запроса просто логируем, но не останавливаем агента
            LOGGER.error("Failed to fetch commands: {}", e.getMessage());
        } catch (Exception e) {
            // Перехватываем все исключения, чтобы не нарушить работу планировщика
            LOGGER.error("Unexpected error when processing commands", e);
        }
    }

    private Status gatherStatus() {
        // Получение информации о состоянии системы и сервера
        Status status = new Status();
        
        boolean isServerRunning = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        
        // Проверяем, работает ли интегрированный сервер
        if (mc != null && mc.isIntegratedServerRunning() && mc.getServer() != null) {
            isServerRunning = true;
        }
        
        status.setServerRunning(isServerRunning);
        status.setMinecraftClientActive(worldActive);
        
        // Заглушка для статистики процессов
        status.setSystemStats(new ProcessStats(0.0, 0.0));
        status.setServerProcessStats(new ProcessStats(0.0, 0.0));
        
        return status;
    }

    private void executeCommand(Command command) {
        LOGGER.info("Received command: {}", command.getCommand());
        // Реализация выполнения команд
        switch (command.getCommand()) {
            case "start_server":
                // Логика запуска сервера
                break;
            case "stop_server":
                // Логика остановки сервера
                break;
            default:
                LOGGER.warn("Unknown command received: {}", command.getCommand());
        }
    }

    private String getComputerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            LOGGER.warn("Could not determine hostname, using 'unknown-pc'.", e);
            return "unknown-pc";
        }
    }

    /**
     * Обновляет URL-адреса туннелей без ожидания следующего heartbeat
     * @param tunnelName Имя туннеля
     * @param tunnelUrl URL туннеля
     */
    public void updateTunnelUrl(String tunnelName, String tunnelUrl) {
        if (tunnelName == null || tunnelUrl == null) {
            return;
        }
        
        // Отправляем внеочередной heartbeat с обновленным URL
        try {
            HeartbeatPayload payload = new HeartbeatPayload();
            payload.setClientId(this.clientId);
            payload.setStatus(gatherStatus());
            
            Map<String, String> urls = getNgrokUrls();
            urls.put(tunnelName, tunnelUrl);
            payload.setNgrokUrls(urls);
            
            try {
                apiClient.sendHeartbeat(payload);
                LOGGER.info("Tunnel URL updated and sent to server: {} -> {}", tunnelName, tunnelUrl);
            } catch (IOException e) {
                LOGGER.error("Failed to send tunnel update: {}", e.getMessage());
            }
        } catch (Exception e) {
            LOGGER.error("Error preparing tunnel update", e);
        }
    }
} 