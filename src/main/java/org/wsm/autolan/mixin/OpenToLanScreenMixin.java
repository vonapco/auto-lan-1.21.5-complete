package org.wsm.autolan.mixin;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.wsm.autolan.AutoLan;
import org.wsm.autolan.AutoLanServerValues;
import org.wsm.autolan.AutoLanState;
import org.wsm.autolan.LanSettings;
import org.wsm.autolan.TunnelType;

import net.minecraft.client.font.MultilineText;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;
import net.minecraft.util.NetworkUtils;
import net.minecraft.world.GameMode;

@Mixin(OpenToLanScreen.class)
public abstract class OpenToLanScreenMixin extends Screen {
    private static final Text PUBLISH_FAILED_TEXT = Text.translatable("commands.publish.failed");
    private static final Text PUBLISH_STOP_FAILED_TEXT = Text.translatable("commands.publish.failed.stop");
    private static final Text PUBLISH_STOPPED_TEXT = Text.translatable("commands.publish.stopped");
    private static final Text PER_WORLD_TEXT = Text.translatable("lanServer.perWorld");
    private static final Text GLOBAL_TEXT = Text.translatable("lanServer.global");
    private static final Text SYSTEM_TEXT = Text.translatable("lanServer.system");
    private static final Text LOAD_TEXT = Text.translatable("lanServer.load");
    private static final Text LOAD_SYSTEM_TEXT = Text.translatable("lanServer.load.system");
    private static final Text CLEAR_TEXT = Text.translatable("lanServer.clear");
    private static final Text CLEAR_PERWORLD_QUESTION_TEXT = Text.translatable("lanServer.clear.perWorld.question");
    private static final Text CLEAR_GLOBAL_QUESTION_TEXT = Text.translatable("lanServer.clear.global.question");
    private static final Text START_TEXT = Text.translatable("lanServer.start");
    private static final Text SAVE_TEXT = Text.translatable("lanServer.save");
    private static final Text STOP_TEXT = Text.translatable("lanServer.stop");
    private static final Text ONLINE_MODE_TEXT = Text.translatable("lanServer.onlineMode");
    private static final Text TUNNEL_TEXT = Text.translatable("lanServer.tunnel");
    private static final Text MAX_PLAYERS_TEXT = Text.translatable("lanServer.maxPlayers");
    private static final Text INVALID_MAX_PLAYERS_TEXT = Text.translatable("lanServer.maxPlayers.invalid", 0);
    private static final Text MOTD_TEXT = Text.translatable("lanServer.motd");
    private static final Text MOTD_DESCRIPTION_TEXT = Text.translatable("lanServer.motd.description");

    @Shadow
    @Final
    private static Text PORT_TEXT;
    @Shadow
    @Final
    private static int ERROR_TEXT_COLOR;

    private boolean initialized = false;

    private CyclingButtonWidget<GameMode> gameModeButton;
    private MultilineText explanationText = MultilineText.EMPTY;
    private CyclingButtonWidget<Boolean> onlineModeButton;
    private CyclingButtonWidget<TunnelType> tunnelButton;
    private TextFieldWidget maxPlayersField;
    private TextFieldWidget motdField;
    private ButtonWidget startSaveButton;

    @Shadow
    @Nullable
    private TextFieldWidget portField;

    private boolean onlineMode;
    private TunnelType tunnel;
    private int rawPort;
    private boolean portValid = true;
    private int maxPlayers;
    private boolean maxPlayersValid = true;
    private String rawMotd;

    @Shadow
    private GameMode gameMode;
    @Shadow
    private int port;

    protected OpenToLanScreenMixin(Text title) {
        super(title);
    }

    private boolean canStartOrSave() {
        return this.portValid && this.maxPlayersValid;
    }

    private void loadLanSettingsValues(@Nullable LanSettings lanSettings) {
        if (lanSettings == null) {
            return;
        }
        this.gameMode = lanSettings.gameMode;
        this.onlineMode = lanSettings.onlineMode;
        this.tunnel = lanSettings.tunnel;
        this.rawPort = lanSettings.port;
        this.port = lanSettings.port != -1 ? lanSettings.port : NetworkUtils.findLocalPort();
        this.maxPlayers = lanSettings.maxPlayers;
        this.rawMotd = lanSettings.motd;
    }

    private void loadLanSettings(@Nullable LanSettings lanSettings) {
        this.loadLanSettingsValues(lanSettings);
        this.gameModeButton.setValue(this.gameMode);
        this.onlineModeButton.setValue(this.onlineMode);
        this.tunnelButton.setValue(this.tunnel);
        this.portField.setText(this.rawPort != -1 ? Integer.toString(this.port) : "");
        this.maxPlayersField.setText(Integer.toString(this.maxPlayers));
        this.motdField.setText(this.rawMotd);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void preInit(CallbackInfo ci) {
        IntegratedServer server = this.client.getServer();

        // Initialization cannot be done in the constructor because
        // this.client wouldn't have been initialized yet.
        if (!this.initialized) {
            if (server.isRemote()) {
                AutoLanServerValues serverValues = (AutoLanServerValues) server;

                this.gameMode = server.getDefaultGameMode();
                this.onlineMode = server.isOnlineMode();
                this.tunnel = serverValues.getTunnelType();
                this.port = server.getServerPort();
                this.rawPort = this.port;
                this.maxPlayers = server.getMaxPlayerCount();
                this.rawMotd = serverValues.getRawMotd();
            } else {
                this.loadLanSettingsValues(LanSettings.systemDefaults(server));
            }

            this.initialized = true;
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "init", at = @At("TAIL"))
    private void postInit(CallbackInfo ci) {
        IntegratedServer server = this.client.getServer();
        boolean alreadyOpenedToLan = server.isRemote();

        // Replace the Allow Cheats button
        // with explanation text below it (added in renderText)
        // and have the Game Mode button fill its place.
        this.remove(this.children().get(8));
        this.gameModeButton = (CyclingButtonWidget<GameMode>) this.children().get(7);
        this.gameModeButton.setWidth(310);
        // Online Mode button
        this.onlineModeButton = this.addDrawableChild(
                CyclingButtonWidget.onOffBuilder(this.onlineMode).build(this.width / 2 - 155, 124, 150,
                        20, ONLINE_MODE_TEXT, (button, onlineMode) -> {
                            this.onlineMode = onlineMode;
                        }));
        // PvP Enabled button
        this.tunnelButton = this.addDrawableChild(
                CyclingButtonWidget.builder(TunnelType::getTranslatableName).values(TunnelType.values())
                        .initially(tunnel).build(this.width / 2 + 5, 124, 150, 20, TUNNEL_TEXT, (button, tunnel) -> {
                            this.tunnel = tunnel;
                        }));

        // Update the Port field and re-add it for consistent Tab order.
        this.portField.setPosition(this.width / 2 - 154, this.height - 92);
        this.portField.setWidth(148);
        this.portField.setText(this.rawPort != -1 ? Integer.toString(this.port) : "");
        this.remove(this.portField);
        this.addDrawableChild(this.portField);

        // Max Players field
        this.maxPlayersField = new TextFieldWidget(this.textRenderer, this.width / 2 + 6, this.height - 92,
                148, 20, MAX_PLAYERS_TEXT);
        maxPlayersField.setText(Integer.toString(maxPlayers));
        maxPlayersField.setChangedListener(maxPlayers -> {
            Integer newMaxPlayers = null;
            try {
                newMaxPlayers = Integer.parseInt(maxPlayers);
            } catch (NumberFormatException e) {
            }
            if (newMaxPlayers != null && newMaxPlayers > 0) {
                this.maxPlayers = newMaxPlayers;
                this.maxPlayersValid = true;
                maxPlayersField.setEditableColor(TextFieldWidget.DEFAULT_EDITABLE_COLOR);
                maxPlayersField.setTooltip(null);
            } else {
                this.maxPlayersValid = false;
                maxPlayersField.setEditableColor(ERROR_TEXT_COLOR);
                maxPlayersField.setTooltip(Tooltip.of(INVALID_MAX_PLAYERS_TEXT));
            }
            this.startSaveButton.active = this.canStartOrSave();
        });
        this.addDrawableChild(maxPlayersField);

        // MOTD field
        this.motdField = new TextFieldWidget(this.textRenderer, this.width / 2 - 154, this.height - 54, 308, 20,
                MOTD_TEXT);
        motdField.setMaxLength(59); // https://minecraft.wiki/w/Server.properties#motd
        motdField.setText(this.rawMotd);
        motdField.setTooltip(
                Tooltip.of(Text.literal(AutoLan.processMotd(server, MOTD_DESCRIPTION_TEXT.getString()))));
        motdField.setChangedListener(rawMotd -> this.rawMotd = rawMotd);
        this.addDrawableChild(motdField);

        // Replace the Start LAN World button with a Start/Save button.
        this.remove(this.children().get(8));
        this.startSaveButton = this.addDrawableChild(
                ButtonWidget.builder(alreadyOpenedToLan ? SAVE_TEXT : START_TEXT, button -> this.startOrSave())
                        .dimensions(this.width / 2 - 155, this.height - 28, alreadyOpenedToLan ? 73 : 150, 20).build());
        this.startSaveButton.active = this.canStartOrSave();

        if (alreadyOpenedToLan) {
            this.addDrawableChild(ButtonWidget.builder(STOP_TEXT, button -> this.stop())
                    .dimensions(this.width / 2 - 78, this.height - 28, 73, 20).build());
        }

        // Re-add the Cancel button for consistent Tab order.
        ButtonWidget cancelButton = (ButtonWidget) this.children().get(8);
        this.remove(cancelButton);
        this.addDrawableChild(cancelButton);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Per-world settings text
        context.drawTextWithShadow(this.textRenderer, PER_WORLD_TEXT, this.width / 2 - 155, 14, 0xFFFFFF);
        // Global settings text
        context.drawTextWithShadow(this.textRenderer, GLOBAL_TEXT, this.width / 2 - 155, 38, 0xFFFFFF);
        // System settings text
        context.drawTextWithShadow(this.textRenderer, SYSTEM_TEXT, this.width / 2 - 155, 62, 0xFFFFFF);

        // Explanation text
        this.explanationText.drawWithShadow(context, this.width / 2 - 154, 172, 9, 0xA0A0A0);

        // Port field text
        context.drawTextWithShadow(this.textRenderer, PORT_TEXT, this.width / 2 - 154, this.height - 104, 0xA0A0A0);
        // Max Players field text
        context.drawTextWithShadow(this.textRenderer, MAX_PLAYERS_TEXT, this.width / 2 + 6, this.height - 104,
                0xA0A0A0);
        // MOTD field text
        context.drawTextWithShadow(this.textRenderer, MOTD_TEXT, this.width / 2 - 154, this.height - 66, 0xA0A0A0);
    }

    @Redirect(method = "updatePort", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/NetworkUtils;isPortAvailable(I)Z"))
    private boolean allowKeepingPort(int port) {
        return NetworkUtils.isPortAvailable(port) || port == this.client.getServer().getServerPort();
    }

    @Inject(method = "method_47416", at = @At("TAIL"), remap = false)
    private void updatePortValid(ButtonWidget button, String portText, CallbackInfo ci) {
        this.portValid = button.active;
        if (this.startSaveButton != null) {
            this.startSaveButton.active = this.canStartOrSave();
        }
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 50))
    private int changeTitleY(int y) {
        return 88;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"), slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V", ordinal = 1)))
    private void removeOtherPlayersAndPortText(DrawContext context, TextRenderer textRenderer, Text text,
            int centerX, int y, int color) {
    }

    private void startOrSave() {
        this.client.setScreen(null);

        IntegratedServer server = this.client.getServer();
        if (server.isRemote()) {
            AutoLanServerValues serverValues = (AutoLanServerValues) server;
            Text tunnelText = serverValues.getTunnelText();
            Text portText = Texts.bracketedCopyable(String.valueOf(server.getServerPort()));
            String motdString = server.getServerMotd();

            if (tunnelText != null) {
                this.client.inGameHud.getChatHud().addMessage(Text.translatable("commands.publish.saved.tunnel", portText, tunnelText, motdString));
            } else {
                this.client.inGameHud.getChatHud().addMessage(Text.translatable("commands.publish.saved", portText, motdString));
            }
            return;
        }

        AutoLan.startOrSaveLan(this.client.getServer(), this.gameMode, this.onlineMode, this.tunnel,
                this.port, this.maxPlayers, this.rawMotd,
                text -> this.client.inGameHud.getChatHud().addMessage(text),
                () -> this.client.inGameHud.getChatHud()
                        .addMessage(PUBLISH_FAILED_TEXT.copy().formatted(Formatting.RED)),
                text -> this.client.inGameHud.getChatHud().addMessage(text.copy().formatted(Formatting.RED)));
    }

    private void stop() {
        this.client.setScreen(null);

        AutoLan.stopLan(this.client.getServer(),
                () -> this.client.inGameHud.getChatHud().addMessage(PUBLISH_STOPPED_TEXT),
                () -> this.client.inGameHud.getChatHud()
                        .addMessage(PUBLISH_STOP_FAILED_TEXT.copy().formatted(Formatting.RED)),
                text -> this.client.inGameHud.getChatHud().addMessage(text.copy().formatted(Formatting.RED)));
    }

    /**
     * Allow starting/saving with Enter as well as the Start/Save button.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.canStartOrSave()) {
                this.startOrSave();
            }
            return true;
        }
        return false;
    }
}
