package org.wsm.autolan.agent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wsm.autolan.agent.model.*;

import java.io.IOException;
import java.util.Objects;

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
                throw new IOException("Unexpected code " + response);
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
                throw new IOException("Unexpected code " + response);
            }
            return gson.fromJson(Objects.requireNonNull(response.body()).charStream(), HeartbeatResponse.class);
        }
    }

    @Nullable
    public CommandsResponse getCommands(@NotNull String clientId) throws IOException {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(baseUrl + "/commands"))
                .newBuilder()
                .addQueryParameter("client_id", clientId)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("X-API-Key", apiKey)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            return gson.fromJson(Objects.requireNonNull(response.body()).charStream(), CommandsResponse.class);
        }
    }
} 