package org.wsm.autolan;

import java.net.URI;

import org.jetbrains.annotations.Nullable;

import org.wsm.autolan.util.Utils;
import com.github.alexdlaird.ngrok.NgrokClient;
import com.github.alexdlaird.ngrok.conf.JavaNgrokConfig;
import com.github.alexdlaird.ngrok.protocol.CreateTunnel;
import com.github.alexdlaird.ngrok.protocol.Proto;
import com.github.alexdlaird.ngrok.protocol.Tunnel;

import net.minecraft.screen.ScreenTexts;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.StringIdentifiable;

public enum TunnelType implements StringIdentifiable {
    NONE("none"),
    NGROK("ngrok") {
        @Override
        public @Nullable String start(MinecraftServer server) throws TunnelException {
            // Теперь NgrokClient должен быть инициализирован заранее через AutoLan.startNgrok(key)
            if (AutoLan.NGROK_CLIENT == null) {
                AutoLan.LOGGER.error("[AutoLan] [NGROK_TUNNEL] NgrokClient is not initialized. Cannot start ngrok tunnel. Ensure AutoLan.startNgrok() was called successfully.");
                throw new TunnelException(ScreenTexts.composeGenericOptionText(NGROK_FAILED,
                        Text.translatable("autolan.ngrok.error.not_initialized"))); // Новое сообщение об ошибке
            }

            try {
                // NgrokClient уже должен быть настроен с authtoken
                // Просто создаем туннель
                Tunnel tunnel = AutoLan.NGROK_CLIENT.connect(new CreateTunnel.Builder()
                        .withProto(Proto.TCP)
                        .withAddr(server.getServerPort())
                        .build());

                AutoLan.LOGGER.info("[AutoLan] [NGROK_TUNNEL] Ngrok tunnel created successfully: {}", tunnel.getPublicUrl());
                return tunnel.getPublicUrl();
            } catch (Exception e) { // NgrokException или другие
                AutoLan.LOGGER.error("[AutoLan] [NGROK_TUNNEL] Failed to create ngrok tunnel", e);
                throw new TunnelException(
                        ScreenTexts.composeGenericOptionText(NGROK_FAILED, Text.of(e.getMessage())), e);
            }
        }

        @Override
        public void stop(MinecraftServer server) throws TunnelException {
            // Логика остановки остается прежней, так как NGROK_CLIENT.kill() корректно останавливает процесс.
            // Однако, NGROK_CLIENT может быть установлен в null в AutoLan.startNgrok, если ключ невалиден,
            // или в AutoLan.stopTunnels(). Поэтому, здесь дополнительная проверка не помешает.
            if (AutoLan.NGROK_CLIENT != null) {
                try {
                    AutoLan.LOGGER.info("[AutoLan] [NGROK_TUNNEL] Attempting to stop ngrok client and tunnels.");
                    // Закрываем все туннели перед остановкой клиента, если это возможно
                    // List<Tunnel> tunnels = AutoLan.NGROK_CLIENT.getTunnels();
                    // for (Tunnel t : tunnels) {
                    //    try {
                    //        AutoLan.NGROK_CLIENT.disconnect(t.getPublicUrl());
                    //    } catch (NgrokException ne) {
                    //        AutoLan.LOGGER.warn("[AutoLan] [NGROK_TUNNEL] Error disconnecting tunnel {}: {}", t.getPublicUrl(), ne.getMessage());
                    //    }
                    // }
                    AutoLan.NGROK_CLIENT.kill(); // Этот метод останавливает процесс ngrok
                    AutoLan.LOGGER.info("[AutoLan] [NGROK_TUNNEL] Ngrok client killed.");
                    // AutoLan.NGROK_CLIENT = null; // Не устанавливаем в null здесь, это может сделать AutoLan.stopTunnels
                } catch (Exception e) { // NgrokException или другие
                    AutoLan.LOGGER.error("[AutoLan] [NGROK_TUNNEL] Failed to stop ngrok client", e);
                    throw new TunnelException(
                            ScreenTexts.composeGenericOptionText(NGROK_STOP_FAILED, Text.of(e.getMessage())), e);
                }
            } else {
                 AutoLan.LOGGER.info("[AutoLan] [NGROK_TUNNEL] NgrokClient is already null, no stop action needed.");
            }
        }
    };

    public static class TunnelException extends Exception {
        private final Text messageText;

        public TunnelException(Text messageText) {
            super(messageText.getString());
            this.messageText = messageText;
        }

        public TunnelException(Text messageText, Throwable cause) {
            super(messageText.toString(), cause);
            this.messageText = messageText;
        }

        public Text getMessageText() {
            return this.messageText;
        }
    }

    public static final StringIdentifiable.EnumCodec<TunnelType> CODEC = StringIdentifiable
            .createCodec(TunnelType::values);

    private static final String NGROK_AUTHTOKEN_URL = "https://dashboard.ngrok.com/get-started/your-authtoken";

    private static final Text NGROK_FAILED = Text.translatable("commands.publish.failed.tunnel.ngrok");
    private static final Text NGROK_STOP_FAILED = Text.translatable("commands.publish.failed.tunnel.ngrok.stop");
    // NGROK_FAILED_NO_AUTHTOKEN больше не нужен здесь, так как проверка токена перенесена
    // private static final String NGROK_FAILED_NO_AUTHTOKEN = "commands.publish.failed.tunnel.ngrok.noAuthtoken";

    private final String name;

    public static TunnelType byName(String name) {
        return byName(name, NONE);
    }

    @Nullable
    public static TunnelType byName(String name, @Nullable TunnelType defaultTunnelType) {
        TunnelType tunnelType = CODEC.byId(name);
        return tunnelType != null ? tunnelType : defaultTunnelType;
    }

    private TunnelType(String name) {
        this.name = name;
    }

    @Nullable
    public String start(MinecraftServer server) throws TunnelException {
        return null;
    }

    public void stop(MinecraftServer server) throws TunnelException {
    }

    public String getName() {
        return this.name;
    }

    public Text getTranslatableName() {
        return Text.translatable("autolan.tunnel." + this.name);
    }

    @Override
    public String asString() {
        return this.name;
    }
}
