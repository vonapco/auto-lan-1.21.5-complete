package org.wsm.autolan.agent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wsm.autolan.agent.model.*; // Этот импорт уже включает новые модели

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.Objects; // Явный импорт для java.util.Objects

public class ApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String baseUrl;
    private final String apiKey;
    private final OkHttpClient client;
    private final Gson gson;

    public ApiClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.client = new OkHttpClient();
        this.gson = new GsonBuilder().create();
    }

    @Nullable
    public RegistrationResponse register(RegistrationRequest registrationRequest) throws IOException {
        String json = gson.toJson(registrationRequest);
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/register")
                .header("X-API-Key", apiKey)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                // Подробное логирование ошибки
                String responseBody = response.body() != null ? response.body().string() : "null";
                throw new IOException("Unexpected code " + response.code() + " for /register. Response: " + responseBody);
            }
            return gson.fromJson(Objects.requireNonNull(response.body()).charStream(), RegistrationResponse.class);
        }
    }

    @Nullable
    public HeartbeatResponse sendHeartbeat(HeartbeatPayload payload) throws IOException {
        String json = gson.toJson(payload);
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/heartbeat")
                .header("X-API-Key", apiKey)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "null";
                throw new IOException("Unexpected code " + response.code() + " for /heartbeat. Response: " + responseBody);
            }
            return gson.fromJson(Objects.requireNonNull(response.body()).charStream(), HeartbeatResponse.class);
        }
    }

    @Nullable
    public CommandsResponse getCommands(@NotNull String clientId) throws IOException {
        HttpUrl httpUrl = HttpUrl.parse(baseUrl + "/commands");
        if (httpUrl == null) {
            throw new IOException("Invalid base URL for /commands endpoint.");
        }
        HttpUrl url = httpUrl.newBuilder()
                .addQueryParameter("client_id", clientId)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("X-API-Key", apiKey)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "null";
                throw new IOException("Unexpected code " + response.code() + " for /commands. Response: " + responseBody);
            }
            return gson.fromJson(Objects.requireNonNull(response.body()).charStream(), CommandsResponse.class);
        }
    }

    // Новый метод для запроса ngrok ключа
    public CompletableFuture<RequestNgrokKeyResponse> requestNgrokKey(String clientId) {
        CompletableFuture<RequestNgrokKeyResponse> future = new CompletableFuture<>();
        RequestNgrokKeyRequest payload = new RequestNgrokKeyRequest(clientId, false); // hasCustomKey = false, т.к. мы запрашиваем ключ
        String json = gson.toJson(payload);
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
                .url(baseUrl + "/request_ngrok_key")
                .header("X-API-Key", apiKey)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "null";
                    future.completeExceptionally(new IOException("Unexpected code " + response.code() + " for /request_ngrok_key. Response: " + responseBody));
                    return;
                }
                try (ResponseBody responseBody = response.body()) {
                    if (responseBody == null) {
                        future.completeExceptionally(new IOException("Response body was null for /request_ngrok_key"));
                        return;
                    }
                    RequestNgrokKeyResponse ngrokKeyResponse = gson.fromJson(responseBody.charStream(), RequestNgrokKeyResponse.class);
                    future.complete(ngrokKeyResponse);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    // Новый метод для освобождения ngrok ключа
    public CompletableFuture<Void> releaseNgrokKey(String clientId, String key) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ReleaseNgrokKeyRequest payload = new ReleaseNgrokKeyRequest(clientId, key);
        String json = gson.toJson(payload);
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
                .url(baseUrl + "/release_ngrok_key")
                .header("X-API-Key", apiKey) // Предполагается, что этот эндпоинт также требует X-API-Key
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                     String responseBody = response.body() != null ? response.body().string() : "null";
                    future.completeExceptionally(new IOException("Unexpected code " + response.code() + " for /release_ngrok_key. Response: " + responseBody));
                    return;
                }
                // Если сервер возвращает пустое тело при успехе (например, 200 OK или 204 No Content)
                future.complete(null);
                // Не забываем закрыть тело ответа
                if (response.body() != null) {
                    response.body().close();
                }
            }
        });
        return future;
    }
} 