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
import org.wsm.autolan.util.WorldFreezeController; // Новый импорт
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
    private static final int MAX_NGROK_KEY_FETCH_ATTEMPTS = 3; // Максимальное количество попыток получения ключа
    
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
            LOGGER.error("Failed to get client ID. Agent will not start basic background tasks.");
            // Тем не менее, мы можем попытаться инициализировать сервис ngrok,
            // так как resolveNgrokKey может работать и без clientId, если ключ есть в конфиге.
            // Но для запроса с сервера clientId нужен.
        }

        // Инициализация сервиса Ngrok (получение ключа и запуск)
        initializeNgrokService();

        if (clientId != null) {
            startBackgroundTasks(); // Запускаем фоновые задачи только если есть clientId
        } else {
            LOGGER.warn("Background tasks (heartbeat, commands) will not start as clientId is missing.");
        }
    }

    private void initializeNgrokService() {
        LOGGER.info("Initializing Ngrok service...");

        if (this.apiClient == null) {
            LOGGER.error("ApiClient is not initialized. Cannot proceed with Ngrok service initialization.");
            return;
        }

        // 1. Освобождаем предыдущий временный ключ, если он был и если пользователь добавил свой.
        // NgrokKeyManager.resolveNgrokKey сам проверит config.ngrokKey первым.
        // Если там будет ключ, то предыдущий временный ключ (isFromServer=true) не будет освобожден здесь,
        // а будет освобожден внутри releaseKeyIfNeeded, если это необходимо при следующем запуске resolveNgrokKey.
        // Однако, для явности и чтобы покрыть случай "пользователь добавил ключ в конфиг",
        // можно было бы сделать так:
        // if (config.ngrokKey != null && !config.ngrokKey.isEmpty()) {
        //    NgrokKeyManager.releaseKeyIfNeeded(this.clientId, this.apiClient); // Освобождаем, т.к. появился ручной ключ
        // }
        // Но текущая логика NgrokKeyManager.resolveNgrokKey и так справится:
        // если config.ngrokKey есть, он будет использован, isFromServer станет false.
        // Если его нет, то будет запрошен новый. releaseKeyIfNeeded должен быть вызван *перед* resolve,
        // чтобы освободить *старый* ключ *перед* получением *нового* или использованием ручного.

        // Перед тем как пытаться получить ключ (новый или из конфига),
        // освободим старый временный ключ, если он был.
        // Это важно, если, например, предыдущий сеанс использовал временный ключ,
        // а теперь пользователь добавил свой в конфиг.
        NgrokKeyManager.releaseKeyIfNeeded(this.clientId, this.apiClient);


        String ngrokKey = null;
        boolean ngrokStartedSuccessfully = false;

        for (int attempt = 1; attempt <= MAX_NGROK_KEY_FETCH_ATTEMPTS; attempt++) {
            LOGGER.info("Attempt {}/{} to obtain and start ngrok.", attempt, MAX_NGROK_KEY_FETCH_ATTEMPTS);

            // 2. Получаем ключ (из конфига или с сервера)
            // clientId может быть null на этом этапе, если первая регистрация не удалась.
            // NgrokKeyManager.resolveNgrokKey должен уметь это обрабатывать (не сможет запросить с сервера).
            if (this.clientId == null && (this.config.ngrokKey == null || this.config.ngrokKey.isEmpty())) {
                LOGGER.warn("Cannot request ngrok key from server because clientId is null and no custom key in config. Attempting registration again.");
                // Попытка повторной регистрации clientId перед запросом ключа
                performRegistration(); // Это может обновить this.clientId
                if (this.clientId == null) {
                     LOGGER.error("Still no clientId after re-attempting registration. Ngrok key acquisition from server will fail.");
                }
            }

            ngrokKey = NgrokKeyManager.resolveNgrokKey(
                this.clientId, // Может быть null, если регистрация не удалась
                this.config,
                this.apiClient,
                WorldFreezeController::freeze,
                WorldFreezeController::unfreeze
            );

            if (ngrokKey != null && !ngrokKey.isEmpty()) {
                // 3. Запускаем ngrok с полученным ключом
                // Этот метод должен быть реализован в AutoLan.java и возвращать boolean
                LOGGER.info("Attempting to start ngrok with key: {}", NgrokKeyManager.isKeyFromServer() ? "server-provided" : "user-provided");
                if (AutoLan.startNgrok(ngrokKey)) { // Предполагаем, что AutoLan.startNgrok существует
                    LOGGER.info("Ngrok started successfully with key.");
                    ngrokStartedSuccessfully = true;
                    break; // Успех, выходим из цикла попыток
                } else {
                    LOGGER.error("Failed to start ngrok with key: {}", ngrokKey);
                    if (NgrokKeyManager.isKeyFromServer()) {
                        LOGGER.info("Releasing failed server-provided ngrok key and attempting to get a new one.");
                        NgrokKeyManager.releaseKeyIfNeeded(this.clientId, this.apiClient); // Освобождаем нерабочий ключ
                        // Продолжаем цикл для следующей попытки
                    } else {
                        // Если ключ был пользовательский и не сработал, нет смысла пытаться снова с тем же ключом
                        LOGGER.error("User-provided ngrok key failed. Please check your ngrok key in the config.");
                        break;  // Выходим из цикла, т.к. пользовательский ключ не сработал
                    }
                }
            } else {
                LOGGER.error("Failed to obtain ngrok key (attempt {}/{}).", attempt, MAX_NGROK_KEY_FETCH_ATTEMPTS);
                // Если ключ не получен, и это была не последняя попытка, просто продолжаем цикл
            }

            if (attempt < MAX_NGROK_KEY_FETCH_ATTEMPTS) {
                try {
                    LOGGER.info("Waiting for 5 seconds before next attempt...");
                    Thread.sleep(5000); // Небольшая задержка перед следующей попыткой
                } catch (InterruptedException e) {
                    LOGGER.warn("Ngrok initialization retry delay interrupted.");
                    Thread.currentThread().interrupt();
                    break; // Прерываем попытки если поток прерван
                }
            }
        }

        if (!ngrokStartedSuccessfully) {
            LOGGER.error("Failed to start ngrok after {} attempts.", MAX_NGROK_KEY_FETCH_ATTEMPTS);
            // Можно добавить дополнительное уведомление пользователю здесь
        }
    }

    public void shutdown() {
        LOGGER.info("Auto-LAN Agent is shutting down...");
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        // Освобождаем ключ при выключении, если он был с сервера
        NgrokKeyManager.releaseKeyIfNeeded(this.clientId, this.apiClient);
    }

    private void loadOrRegisterClientId() {
        // 1. Попробовать из конфига (config.clientId)
        if (this.config.clientId != null && !this.config.clientId.trim().isEmpty()) {
            this.clientId = this.config.clientId.trim();
            LOGGER.info("Loaded client ID from config (client_config.json -> AutoLanConfig): {}", this.clientId);
            // Если загрузили из конфига, также запишем его в файл для консистентности,
            // если файла нет или содержимое отличается.
            try {
                String fileClientId = Files.exists(clientIdPath) ? Files.readString(clientIdPath).trim() : null;
                if (!this.clientId.equals(fileClientId)) {
                    Files.writeString(clientIdPath, this.clientId);
                    LOGGER.info("Updated client ID file ({}) with value from config.", CLIENT_ID_FILE);
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to write client ID (from config) to file {}: {}", CLIENT_ID_FILE, e.getMessage());
            }
            return;
        }

        // 2. Если в конфиге нет, попробовать из файла (autolan_client_id.txt)
        try {
            if (Files.exists(clientIdPath)) {
                this.clientId = Files.readString(clientIdPath).trim();
                if (!this.clientId.isEmpty()) {
                    LOGGER.info("Loaded client ID from file ({}): {}", CLIENT_ID_FILE, this.clientId);
                    // Попытаться обновить конфиг этим значением, если config.clientId пуст
                    if (this.config.clientId == null || this.config.clientId.trim().isEmpty()) {
                        this.config.clientId = this.clientId; // Обновляем поле в объекте конфига
                        // AutoConfig.getConfigHolder(AutoLanConfig.class).save(); // Это может не сработать или быть нежелательным без явного действия пользователя
                        LOGGER.info("Client ID from file was loaded. Consider updating client_config.json manually if needed, or if auto-save is implemented.");
                    }
                    return;
                } else {
                    LOGGER.info("Client ID file ({}) was empty. Will attempt to register a new ID.", CLIENT_ID_FILE);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error loading client ID from file ({}), will try to register a new one.", CLIENT_ID_FILE, e);
            // Продолжаем, чтобы попытаться зарегистрировать новый ID
        }

        // 3. Если нигде нет, регистрируем новый ID
        LOGGER.info("No client ID found in config or file. Performing new registration.");
        performRegistration();
    }

    private void performRegistration() {
        try {
            String computerName = getComputerName();
            RegistrationRequest request = new RegistrationRequest(computerName);
            
            try {
                RegistrationResponse response = apiClient.register(request);
                if (response != null && response.getClientId() != null && !response.getClientId().trim().isEmpty()) {
                    this.clientId = response.getClientId().trim();
                    LOGGER.info("Successfully registered new client ID: {}", clientId);

                    // Сохраняем в файл
                    try {
                        Files.writeString(clientIdPath, this.clientId);
                        LOGGER.info("Saved new client ID to file: {}", CLIENT_ID_FILE);
                    } catch (IOException ex) {
                        LOGGER.error("Failed to save new client ID to file ({}): {}", CLIENT_ID_FILE, ex.getMessage());
                    }

                    // Обновляем поле в текущем объекте конфига
                    this.config.clientId = this.clientId;
                    // Попытка сохранить конфиг AutoConfig. Это специфично для библиотеки AutoConfig.
                    // Обычно AutoConfig сохраняет конфиг автоматически при изменении через GUI (ModMenu).
                    // Принудительное сохранение из кода может потребовать дополнительных действий или быть не предусмотрено явно.
                    // Если AutoLan.CONFIG - это ConfigHolder, то можно попробовать:
                    if (AutoLan.CONFIG != null) {
                        boolean saved = AutoLan.CONFIG.save();
                        if (saved) {
                            LOGGER.info("Attempted to save updated clientId to AutoLanConfig. Please verify.");
                        } else {
                            LOGGER.warn("Failed to save updated clientId to AutoLanConfig via holder.save(). Manual update might be needed or check AutoConfig usage.");
                        }
                    } else {
                        LOGGER.warn("AutoLan.CONFIG holder is null, cannot attempt to save clientId to config file.");
                    }

                } else {
                    LOGGER.error("Registration response was null, or did not contain a client ID, or client ID was empty.");
                }
            } catch (IOException e) {
                LOGGER.error("Network error during registration: {}. Check server URL and API key.", e.getMessage());
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