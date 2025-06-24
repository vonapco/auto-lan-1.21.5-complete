package org.wsm.autolan.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wsm.autolan.AutoLanConfig;
import org.wsm.autolan.agent.model.RequestNgrokKeyResponse; // Будет создан позже

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NgrokKeyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NgrokKeyManager.class);
    private static final int REQUEST_TIMEOUT_SECONDS = 30; // Таймаут для запроса ключа

    private static String currentNgrokKey = null;
    private static boolean isFromServer = false;

    /**
     * Resolves the ngrok key. Checks custom config first, then requests from server if needed.
     *
     * @param clientId       The client ID for server requests.
     * @param config         The AutoLan configuration.
     * @param apiClient      The API client for server communication.
     * @param freezeCallback Callback to run before server request (e.g., freeze game).
     * @param unfreezeCallback Callback to run after server request (e.g., unfreeze game).
     * @return The ngrok key to use, or null if no key could be obtained.
     */
    public static String resolveNgrokKey(String clientId, AutoLanConfig config, ApiClient apiClient, Runnable freezeCallback, Runnable unfreezeCallback) {
        // 1. Проверка кастомного ключа из конфигурации
        if (config.ngrokKey != null && !config.ngrokKey.trim().isEmpty()) {
            LOGGER.info("Using custom ngrok key from config.");
            currentNgrokKey = config.ngrokKey.trim();
            isFromServer = false;
            return currentNgrokKey;
        }

        // 2. Если кастомного ключа нет, запрашиваем с сервера
        LOGGER.info("No custom ngrok key found in config. Requesting temporary key from server...");
        if (freezeCallback != null) {
            freezeCallback.run();
        }

        try {
            String serverKey = requestFromServer(clientId, apiClient);
            if (serverKey != null && !serverKey.trim().isEmpty()) {
                LOGGER.info("Successfully obtained temporary ngrok key from server.");
                currentNgrokKey = serverKey.trim();
                isFromServer = true;
                return currentNgrokKey;
            } else {
                LOGGER.error("Failed to obtain temporary ngrok key from server (server returned null or empty key).");
                currentNgrokKey = null;
                isFromServer = false;
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("Exception while requesting ngrok key from server.", e);
            currentNgrokKey = null;
            isFromServer = false;
            return null;
        } finally {
            if (unfreezeCallback != null) {
                unfreezeCallback.run();
            }
        }
    }

    /**
     * Requests an ngrok key from the server.
     *
     * @param clientId  The client ID.
     * @param apiClient The API client.
     * @return The ngrok key, or null if an error occurred.
     */
    private static String requestFromServer(String clientId, ApiClient apiClient) {
        if (apiClient == null) {
            LOGGER.error("ApiClient is null. Cannot request key from server.");
            return null;
        }
        if (clientId == null || clientId.trim().isEmpty()) {
            LOGGER.error("ClientId is null or empty. Cannot request key from server.");
            return null;
        }

        CompletableFuture<RequestNgrokKeyResponse> future = apiClient.requestNgrokKey(clientId);
        try {
            RequestNgrokKeyResponse response = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (response != null && response.getNgrokKey() != null && !response.getNgrokKey().trim().isEmpty()) {
                return response.getNgrokKey();
            } else {
                LOGGER.warn("Server response for ngrok key was null or key was empty.");
                return null;
            }
        } catch (InterruptedException e) {
            LOGGER.error("Thread interrupted while waiting for ngrok key response.", e);
            Thread.currentThread().interrupt(); // Restore interruption status
            return null;
        } catch (ExecutionException e) {
            LOGGER.error("Execution exception while requesting ngrok key.", e.getCause() != null ? e.getCause() : e);
            return null;
        } catch (TimeoutException e) {
            LOGGER.error("Timeout while waiting for ngrok key response from server.", e);
            return null;
        }
    }

    /**
     * Releases the current ngrok key to the server if it was obtained from the server.
     *
     * @param clientId  The client ID.
     * @param apiClient The API client.
     */
    public static void releaseKeyIfNeeded(String clientId, ApiClient apiClient) {
        if (isFromServer && currentNgrokKey != null && !currentNgrokKey.isEmpty()) {
            if (apiClient == null) {
                LOGGER.error("ApiClient is null. Cannot release key to server.");
                return;
            }
            if (clientId == null || clientId.trim().isEmpty()) {
                LOGGER.error("ClientId is null or empty. Cannot release key to server.");
                return;
            }
            LOGGER.info("Releasing temporary ngrok key '{}' to the server.", currentNgrokKey);
            try {
                // Мы не ждем завершения этого CompletableFuture, т.к. это "fire and forget"
                apiClient.releaseNgrokKey(clientId, currentNgrokKey)
                    .exceptionally(ex -> {
                        LOGGER.error("Failed to release ngrok key to server.", ex);
                        return null;
                    });
            } catch (Exception e) {
                LOGGER.error("Exception when initiating key release.", e);
            } finally {
                currentNgrokKey = null;
                isFromServer = false;
            }
        }
    }

    /**
     * Checks if the current ngrok key was obtained from the server.
     *
     * @return true if the key is from the server, false otherwise.
     */
    public static boolean isKeyFromServer() {
        return isFromServer;
    }

    /**
     * Gets the current ngrok key.
     * @return The current ngrok key, or null if not set.
     */
    public static String getCurrentNgrokKey() {
        return currentNgrokKey;
    }
}
