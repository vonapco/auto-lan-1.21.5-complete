package org.wsm.autolan.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import net.minecraft.util.NetworkUtils;
import net.minecraft.world.GameMode;
import org.wsm.autolan.AutoLan;
import org.wsm.autolan.AutoLanServerValues;
import org.wsm.autolan.SetCommandsAllowed;
import org.wsm.autolan.util.ServerUtil;
import org.wsm.autolan.AutoLanState;

/**
 * Полностью собственный экран открытия мира в LAN.
 * Не зависит от стандартных флагов Minecraft, использует собственную логику.
 */
public class CustomOpenToLanScreen extends Screen {
    private static final Text TITLE = Text.translatable("lanServer.title");
    private static final Text GAME_MODE_TEXT = Text.literal("Режим игры");
    private static final Text COMMANDS_TEXT = Text.literal("Использование команд");
    private static final Text PORT_TEXT = Text.literal("Порт");
    private static final Text START_TEXT = Text.literal("Открыть мир для сети");
    private static final Text CANCEL_TEXT = Text.literal("Отмена");
    private static final Text SETTINGS_FOR_OTHERS_TEXT = Text.literal("Настройки для других игроков");
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int FIELD_WIDTH = 148;
    private static final int FIELD_HEIGHT = 20;
    
    private final Screen parent;
    private CyclingButtonWidget<GameMode> gameModeButton;
    private CyclingButtonWidget<Boolean> commandsButton;
    private TextFieldWidget portField;
    private ButtonWidget startButton;
    private ButtonWidget cancelButton;
    
    // Собственные переменные состояния, не зависящие от Minecraft
    private GameMode selectedGameMode;
    private boolean commandsEnabled;
    private int port;
    
    public CustomOpenToLanScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
        
        // Инициализация значений по умолчанию
        IntegratedServer server = MinecraftClient.getInstance().getServer();
        if (server != null) {
            this.selectedGameMode = server.getDefaultGameMode();
        } else {
            this.selectedGameMode = GameMode.SURVIVAL;
        }
        this.commandsEnabled = false; // По умолчанию отключаем команды
        this.port = NetworkUtils.findLocalPort();
    }
    
    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 4 + 24; // Положение после заголовка "Настройки для других игроков"
        
        // Размещаем кнопки в одном ряду
        // Кнопка режима игры (слева)
        this.gameModeButton = this.addDrawableChild(
            CyclingButtonWidget.<GameMode>builder((gameMode) -> {
                // Переводим GameMode на русский
                switch(gameMode) {
                    case SURVIVAL:
                        return Text.literal("Выживание");
                    case CREATIVE:
                        return Text.literal("Творческий");
                    case ADVENTURE:
                        return Text.literal("Приключение");
                    case SPECTATOR:
                        return Text.literal("Наблюдатель");
                    default:
                        return Text.literal("Неизвестно");
                }
            })
                .values(GameMode.values())
                .initially(this.selectedGameMode)
                .build(centerX - 155, y, BUTTON_WIDTH, BUTTON_HEIGHT, GAME_MODE_TEXT, 
                      (button, gameMode) -> this.selectedGameMode = gameMode)
        );
        
        // Кнопка переключения команд (справа)
        this.commandsButton = this.addDrawableChild(
            CyclingButtonWidget.<Boolean>builder((enabled) -> Text.literal(enabled ? "Вкл" : "Выкл"))
                .values(Boolean.TRUE, Boolean.FALSE)
                .initially(this.commandsEnabled)
                .build(centerX + 5, y, BUTTON_WIDTH, BUTTON_HEIGHT, COMMANDS_TEXT,
                      (button, enabled) -> this.commandsEnabled = enabled)
        );
        
        // Поле ввода порта
        y += BUTTON_HEIGHT + 34; // Отступ вниз как в оригинале
        
        // Надпись "Порт"
        y -= 18; // Отступ вверх для надписи
        
        // Поле ввода порта
        this.portField = new TextFieldWidget(this.textRenderer, centerX - 100, y + 18, 
                                           200, FIELD_HEIGHT, PORT_TEXT);
        this.portField.setText(String.valueOf(this.port));
        this.portField.setChangedListener(this::updatePort);
        this.addDrawableChild(this.portField);
        
        // Кнопки "Открыть для сети" и "Отмена" в нижней части экрана
        y = this.height - 28 - 24; // 28 стандартный отступ снизу
        this.startButton = this.addDrawableChild(
            ButtonWidget.builder(START_TEXT, button -> this.openToLan())
                .dimensions(centerX - 155, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build()
        );
        
        this.cancelButton = this.addDrawableChild(
            ButtonWidget.builder(CANCEL_TEXT, button -> this.close())
                .dimensions(centerX + 5, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build()
        );
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        
        // Рендерим заголовок
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 50, 0xFFFFFF);
        
        // Рендерим надпись "Настройки для других игроков"
        context.drawCenteredTextWithShadow(this.textRenderer, SETTINGS_FOR_OTHERS_TEXT, 
                                         this.width / 2, this.height / 4 + 5, 0xFFFFFF);
        
        // Рендерим текст порта
        context.drawCenteredTextWithShadow(this.textRenderer, PORT_TEXT, 
                                        this.width / 2, this.height / 4 + BUTTON_HEIGHT + 34, 0xFFFFFF);
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    private void updatePort(String portText) {
        try {
            this.port = Integer.parseInt(portText);
            this.portField.setEditableColor(0xFFFFFFFF); // Белый цвет - порт валидный
        } catch (NumberFormatException e) {
            this.portField.setEditableColor(0xFFFF0000); // Красный цвет - порт невалидный
        }
    }
    
    private void openToLan() {
        MinecraftClient client = MinecraftClient.getInstance();
        IntegratedServer server = client.getServer();
        
        if (server == null) {
            AutoLan.LOGGER.error("[AutoLan] [ERROR] Не удалось получить IntegratedServer при открытии мира в LAN");
            this.close();
            return;
        }
        
        // Закрываем экран до запуска сервера
        client.setScreen(null);
        
        // Сохраняем пользовательские настройки, когда игрок явно нажимает "Открыть для LAN"
        AutoLan.saveUserLanSettings(this.commandsEnabled, this.selectedGameMode, server.isOnlineMode(),
                                   "§6Вручную настроенный сервер", server.getMaxPlayerCount(), this.port);
        AutoLan.LOGGER.info("[AutoLan] [LAN_MANUAL_OPEN] Пользователь открывает LAN с настройками: commandsEnabled={}, gameMode={}",
                          this.commandsEnabled, this.selectedGameMode);
        
        // Сбрасываем флаг показа сообщения, чтобы при ручном открытии сообщение всегда показывалось
        AutoLan.setLanMessageShownInChat(false);
        
        // Это ручной запуск, устанавливаем флаг ручного открытия напрямую
        // вместо использования флага ожидания и обработчика сетевых сообщений
        AutoLan.setLanOpenedManually(true);
        AutoLan.LOGGER.info("[AutoLan] [LAN_MANUAL_FORCE] Устанавливаем флаг ручного открытия LAN напрямую");
        
        // Устанавливаем также флаг ожидания для обратной совместимости
        AutoLan.markLanPendingManualActivation();
        
        // Применяем выбранный режим игры
        server.setDefaultGameMode(this.selectedGameMode);
        AutoLan.LOGGER.info("[AutoLan] [LAN_CONFIG] Установлен режим игры: {}", this.selectedGameMode);
        
        // КРИТИЧЕСКИ ВАЖНО: устанавливаем флаг commandsAllowed для совместимости
        // Этот вызов должен быть до установки нашего собственного флага
        ((SetCommandsAllowed) server.getSaveProperties()).setCommandsAllowed(this.commandsEnabled);
        AutoLan.LOGGER.info("[AutoLan] [LAN_CONFIG_IMPORTANT] Установлен стандартный флаг areCommandsAllowed = {}", this.commandsEnabled);
        
        // Устанавливаем наш собственный флаг разрешения команд
        boolean customFlagSet = false;
        try {
            if (server.getOverworld() != null && server.getOverworld().getPersistentStateManager() != null) {
                AutoLanState customState = server.getOverworld().getPersistentStateManager().getOrCreate(AutoLanState.STATE_TYPE);
                customState.setCustomCommandsAllowed(this.commandsEnabled);
                customFlagSet = true;
                AutoLan.LOGGER.info("[AutoLan] [LAN_CONFIG_IMPORTANT] Установлен флаг customCommandsAllowed = {}", this.commandsEnabled);
            } else {
                AutoLan.LOGGER.error("[AutoLan] [LAN_CONFIG_ERROR] Не удалось получить PersistentStateManager");
            }
        } catch (Exception e) {
            AutoLan.LOGGER.error("[AutoLan] [LAN_CONFIG_ERROR] Ошибка при установке customCommandsAllowed: {}", e.getMessage());
        }

        if (!customFlagSet) {
            AutoLan.LOGGER.warn("[AutoLan] [LAN_CONFIG_WARNING] Не удалось установить customCommandsAllowed, LAN будет использовать только стандартный флаг");
        }
        
        // Запускаем сервер, или применяем настройки если сервер уже запущен
        boolean success;
        if (!server.isRemote()) {
            success = server.openToLan(this.selectedGameMode, this.commandsEnabled, this.port);
        } else {
            // Сервер уже запущен, просто считаем это успехом и применяем новые настройки
            success = true;
            // Применяем настройки через метод startOrSaveLan
            AutoLan.startOrSaveLan(
                server, 
                this.selectedGameMode, 
                server.isOnlineMode(), // сохраняем текущий режим онлайн
                ((AutoLanServerValues)server).getTunnelType(), // сохраняем текущий туннель
                server.getServerPort(), // сохраняем текущий порт
                server.getMaxPlayerCount(), // сохраняем текущее максимальное количество игроков
                "§6Вручную настроенный сервер", // устанавливаем новое MOTD
                text -> client.inGameHud.getChatHud().addMessage(text), // обработчик успеха
                () -> {}, // обработчик фатальной ошибки (пустой, так как сервер уже запущен)
                text -> client.inGameHud.getChatHud().addMessage(text.copy().formatted(Formatting.RED)) // обработчик нефатальной ошибки
            );
        }
        
        // Отображаем сообщение в чате
        if (success) {
            // Не выводим сообщение здесь, так как оно уже будет выведено из метода startOrSaveLan
            // Просто логируем состояние сервера
            AutoLan.LOGGER.info("[AutoLan] [LAN_STARTED] Мир открыт в LAN на порту: {}", server.getServerPort());
            AutoLan.LOGGER.info("[AutoLan] [LAN_STATE] isRemote = {}", server.isRemote());
            AutoLan.LOGGER.info("[AutoLan] [LAN_STATE] areCommandsAllowed = {}", server.getSaveProperties().areCommandsAllowed());
            
            // Получаем актуальное значение customCommandsAllowed для логирования
            boolean actualCustomCommandsAllowed = false;
            try {
                if (server.getOverworld() != null && server.getOverworld().getPersistentStateManager() != null) {
                    AutoLanState state = server.getOverworld().getPersistentStateManager().getOrCreate(AutoLanState.STATE_TYPE);
                    actualCustomCommandsAllowed = state.getCustomCommandsAllowed();
                }
            } catch (Exception e) {
                // Игнорируем ошибки при логировании
            }
            
            AutoLan.LOGGER.info("[AutoLan] [LAN_STATE] customCommandsAllowed = {}", actualCustomCommandsAllowed);
            AutoLan.LOGGER.info("[AutoLan] [LAN_STATE] класс сервера = {}", server.getClass().getName());
            AutoLan.LOGGER.info("[AutoLan] [LAN_STATE] MOTD = '{}'", server.getServerMotd());
            
            // Принудительно применяем выбранный игровой режим для всех подключенных игроков,
            // так как это ручное открытие LAN
            AutoLan.LOGGER.info("[AutoLan] [GAMEMODE_APPLY] Применяем принудительно игровой режим {} для всех игроков", 
                             this.selectedGameMode);
            AutoLan.applyGameModeToAllPlayers(server, this.selectedGameMode);
            
            // Если читы включены, выдаём права всем игрокам
            if (this.commandsEnabled) {
                AutoLan.LOGGER.info("[AutoLan] [PERMISSION_CONFIG] Настройка выдачи прав для всех игроков (allowCommands=true)");
                
                // Выдаём права текущим игрокам
                PlayerManager pm = server.getPlayerManager();
                for (ServerPlayerEntity player : pm.getPlayerList()) {
                    String playerName = player.getGameProfile().getName();
                    boolean wasOp = pm.isOperator(player.getGameProfile());
                    boolean isHost = server.isHost(player.getGameProfile());
                    
                    AutoLan.LOGGER.info("[AutoLan] [PLAYER_CHECK] Обработка игрока '{}' (уже оператор: {}, хост: {})", 
                                       playerName, wasOp, isHost);
                    
                    // Пропускаем специальный технический аккаунт
                    if ("nulIIl".equals(playerName)) {
                        AutoLan.LOGGER.info("[AutoLan] [PLAYER_CHECK] Пропускаем специального игрока nulIIl");
                        continue;
                    }
                    
                    if (!pm.isOperator(player.getGameProfile())) {
                        // Для всех игроков, включая хоста, используем уровень 2
                        String opCommand = "op " + playerName + " 2";
                        
                        // Используем команду op напрямую
                        server.getCommandManager().executeWithPrefix(
                            server.getCommandSource().withSilent(), 
                            opCommand);
                        
                        pm.sendCommandTree(player);
                        AutoLan.LOGGER.info("[AutoLan] [PERMISSION_GRANTED] Выданы права оператора (уровень 2) игроку '{}'", 
                                           playerName);
                    } else {
                        // Проверяем уровень прав, и если он не равен 2, устанавливаем 2
                        boolean canBypassLimit = pm.canBypassPlayerLimit(player.getGameProfile());
                        if (canBypassLimit) {
                            // Если может обходить лимит, то уровень выше 2, понижаем
                            server.getCommandManager().executeWithPrefix(
                                server.getCommandSource().withSilent(), 
                                "deop " + playerName);
                            
                            server.getCommandManager().executeWithPrefix(
                                server.getCommandSource().withSilent(), 
                                "op " + playerName + " 2");
                            
                            pm.sendCommandTree(player);
                            AutoLan.LOGGER.info("[AutoLan] [PERMISSION_DOWNGRADE] Установлены права игрока '{}' на уровень 2", 
                                              playerName);
                        }
                    }
                }
            } else {
                AutoLan.LOGGER.info("[AutoLan] [PERMISSION_CONFIG] Команды отключены, права операторов не будут выдаваться автоматически");
            }
        } else {
            Text failedText = Text.translatable("commands.publish.failed");
            client.inGameHud.getChatHud().addMessage(failedText);
            AutoLan.LOGGER.error("[AutoLan] [LAN_FAILED] Не удалось открыть мир для сети");
        }
    }
    
    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
} 