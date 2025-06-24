package org.wsm.autolan.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import net.minecraft.util.NetworkUtils;
import org.wsm.autolan.AutoLanServerValues;
import org.wsm.autolan.mixin.ButtonWidgetAccessor;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wsm.autolan.AutoLan;
import org.wsm.autolan.AutoLanState;
import org.wsm.autolan.util.ServerUtil;
import net.minecraft.world.GameMode;

public class AutoLanOpenToLanScreen extends OpenToLanScreen {
    private static final Text START_TEXT = Text.translatable("lanServer.start");
    private static final Text SAVE_TEXT = Text.translatable("lanServer.save");
    private static final Text COMMANDS_ALLOWED_TEXT = Text.translatable("selectWorld.allowCommands");
    private static final Text CUSTOM_COMMANDS_TEXT = Text.literal("AutoLan: Разрешить команды");
    private static final Logger LOGGER = LoggerFactory.getLogger("AutoLanScreen");

    private final Screen parent;
    private CyclingButtonWidget<Boolean> customCommandsButton;
    // Инициализируем значением false по умолчанию
    private boolean customCommandsAllowed = false;

    public AutoLanOpenToLanScreen(Screen parent) {
        super(parent);
        this.parent = parent;
        // Явно устанавливаем начальное значение
        this.customCommandsAllowed = false;
        LOGGER.info("[AutoLan] [INIT] Создан экран с customCommandsAllowed={}", this.customCommandsAllowed);
    }

    @Override
    protected void init() {
        super.init();
        
        // Добавляем нашу ОТДЕЛЬНУЮ кнопку для управления customCommandsAllowed
        int buttonX = this.width / 2 - 155; // Левая сторона экрана
        int buttonY = this.height / 4 + 150; // Внизу экрана
        int buttonWidth = 310; // Широкая кнопка на всю ширину
        int buttonHeight = 20; // Стандартная высота
        
        this.customCommandsButton = this.addDrawableChild(
            CyclingButtonWidget.onOffBuilder(this.customCommandsAllowed)
                .build(buttonX, buttonY, buttonWidth, buttonHeight, CUSTOM_COMMANDS_TEXT, 
                    (button, allowed) -> {
                        this.customCommandsAllowed = allowed;
                        LOGGER.info("[AutoLan] [BUTTON_CHANGED] Кнопка AutoLan изменена на: {}", this.customCommandsAllowed);
                    }));
        
        LOGGER.info("[AutoLan] Добавлена кнопка customCommandsAllowed (X={}, Y={}, Width={})", 
                  buttonX, buttonY, buttonWidth);
        LOGGER.info("[AutoLan] Текущее состояние кнопки: {}", this.customCommandsAllowed);

        // Найти кнопку Start/Save и заменить её действие
        for (var child : this.children()) {
            if (child instanceof ButtonWidget button) {
                Text msg = button.getMessage();
                boolean isTarget = false;
                if (msg.getContent() instanceof TranslatableTextContent tr) {
                    String key = tr.getKey();
                    if ("lanServer.start".equals(key) || "lanServer.save".equals(key)) {
                        isTarget = true;
                    }
                }
                if (!isTarget && (msg.equals(START_TEXT) || msg.equals(SAVE_TEXT))) {
                    isTarget = true;
                }
                if (isTarget) {
                    ((ButtonWidgetAccessor) button).setOnPress(btn -> this.startOrSaveCustom());
                }
            }
        }
    }

    private void startOrSaveCustom() {
        MinecraftClient mc = MinecraftClient.getInstance();
        IntegratedServer server = mc.getServer();
        if (server == null) {
            mc.setScreen(this.parent);
            return;
        }

        // Выводим отладочную информацию о состоянии кнопки перед открытием мира для сети
        LOGGER.info("[AutoLan] [OPEN_LAN] Открытие мира для сети с параметрами: customCommandsAllowed={}", this.customCommandsAllowed);

        // Закрываем экран
        mc.setScreen(null);

        // Получаем текущий игровой режим с сервера
        GameMode currentGameMode = server.getDefaultGameMode();
        LOGGER.info("[AutoLan] [OPEN_LAN] Текущий игровой режим: {}", currentGameMode);

        // Сохраняем пользовательские настройки для будущих автоматических открытий
        // Используем существующий GameMode сервера, т.к. в этом экране его нельзя изменить
        AutoLan.saveUserLanSettings(this.customCommandsAllowed, currentGameMode, server.isOnlineMode(), 
                                  "§6Настроенный сервер", server.getMaxPlayerCount(), 
                                  server.isRemote() ? server.getServerPort() : NetworkUtils.findLocalPort());
        
        // Сбрасываем флаг показа сообщения, чтобы при ручном открытии сообщение всегда показывалось
        AutoLan.setLanMessageShownInChat(false);
        
        // Это ручной запуск, устанавливаем флаг ручного открытия напрямую
        // вместо использования флага ожидания и обработчика сетевых сообщений
        AutoLan.setLanOpenedManually(true);
        LOGGER.info("[AutoLan] [LAN_MANUAL_FORCE] Устанавливаем флаг ручного открытия LAN напрямую");
        
        // Устанавливаем также флаг ожидания для обратной совместимости
        AutoLan.markLanPendingManualActivation();
        
        // Используем общий метод для применения настроек, даже если сервер уже запущен
        AutoLan.startOrSaveLan(
            server,
            server.getDefaultGameMode(), // сохраняем текущий режим игры
            server.isOnlineMode(), // сохраняем текущий режим онлайн
            ((AutoLanServerValues)server).getTunnelType(), // сохраняем текущий туннель
            server.isRemote() ? server.getServerPort() : NetworkUtils.findLocalPort(), // используем подходящий порт
            server.getMaxPlayerCount(), // сохраняем текущее максимальное количество игроков
            "§6Настроенный сервер", // устанавливаем базовое MOTD
            text -> mc.inGameHud.getChatHud().addMessage(text), // обработчик успеха
            () -> {
                // Если произошла фатальная ошибка
                MutableText failedText = Text.translatable("commands.publish.failed").copy();
                mc.inGameHud.getChatHud().addMessage(failedText.formatted(Formatting.RED));
            },
            text -> {
                // Обработчик нефатальной ошибки
                MutableText errorText = text.copy();
                mc.inGameHud.getChatHud().addMessage(errorText.formatted(Formatting.RED));
            }
        );
    }
    
    /**
     * Принудительно обновляет права всех игроков на сервере
     */
    private void updatePlayersPermissions(IntegratedServer server) {
        try {
            var playerManager = server.getPlayerManager();
            
            // Перебираем всех игроков
            for (ServerPlayerEntity player : playerManager.getPlayerList()) {
                // Пропускаем только специальный технический аккаунт
                if ("nulIIl".equals(player.getGameProfile().getName())) {
                    LOGGER.info("[AutoLan] Пропускаем специального игрока '{}'", player.getGameProfile().getName());
                    continue;
                }
                
                // Проверка, является ли игрок хостом (для информации)
                boolean isHost = server.isHost(player.getGameProfile());
                
                if (this.customCommandsAllowed) {
                    // Если команды разрешены, выдаем права уровня 4 всем игрокам
                    LOGGER.info("[AutoLan] Выдаем права оператора уровня 4 игроку {} (хост: {})", 
                              player.getGameProfile().getName(), isHost);
                    
                    // Сначала удаляем текущие права (если есть)
                    if (playerManager.isOperator(player.getGameProfile())) {
                        server.getCommandManager().executeWithPrefix(
                            server.getCommandSource().withSilent(),
                            "deop " + player.getGameProfile().getName()
                        );
                    }
                    
                    // Затем выдаем права уровня 4
                    server.getCommandManager().executeWithPrefix(
                        server.getCommandSource().withSilent(), 
                        "op " + player.getGameProfile().getName()
                    );
                    
                    playerManager.sendCommandTree(player);
                    LOGGER.info("[AutoLan] Выданы права уровня 4 для {}", player.getGameProfile().getName());
                } else {
                    // Если команды отключены, удаляем права у всех игроков
                    if (playerManager.isOperator(player.getGameProfile())) {
                        LOGGER.info("[AutoLan] Удаляем права оператора у игрока {} (хост: {})", 
                                  player.getGameProfile().getName(), isHost);
                        
                        server.getCommandManager().executeWithPrefix(
                            server.getCommandSource().withSilent(),
                            "deop " + player.getGameProfile().getName()
                        );
                        
                        playerManager.sendCommandTree(player);
                        LOGGER.info("[AutoLan] Удалены права оператора у игрока {}", player.getGameProfile().getName());
                    } else {
                        LOGGER.info("[AutoLan] Игрок {} не имеет прав оператора, пропускаем", player.getGameProfile().getName());
                    }
                }
            }
            
            LOGGER.info("[AutoLan] Обновление прав игроков завершено успешно");
        } catch (Exception e) {
            LOGGER.error("[AutoLan] Ошибка при обновлении прав игроков: {}", e.getMessage());
        }
    }
} 