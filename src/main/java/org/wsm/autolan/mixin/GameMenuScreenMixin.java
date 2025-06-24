package org.wsm.autolan.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.client.gui.screen.GameMenuScreen;
import org.wsm.autolan.gui.CustomOpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;

@Mixin(GameMenuScreen.class)
public class GameMenuScreenMixin extends Screen {
    private static final Text PLAYER_REPORTING_TEXT = Text.translatable("menu.playerReporting");

    private static final Text OPEN_LAN_TEXT = Text.translatable("menu.shareToLan");

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "initWidgets", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    private void updateOpenToLanButton(CallbackInfo ci, GridWidget gridWidget) {
        IntegratedServer server = this.client.getServer();

        boolean isHost = this.client.isIntegratedServerRunning();
        if (isHost) {
            org.wsm.autolan.AutoLan.LOGGER.info("[GameMenuScreen] Инициализация кнопок меню паузы (isHost: {})", isHost);
            
            for (Widget widget : ((GridWidgetAccessor) gridWidget).getChildren()) {
                if (!(widget instanceof ButtonWidget)) {
                    continue;
                }
                ButtonWidget button = (ButtonWidget) widget;

                // Проверяем, был ли сервер открыт вручную
                boolean isLanOpenedManually = org.wsm.autolan.AutoLan.isLanOpenedManually();
                boolean isServerRemote = server != null && server.isRemote();
                
                org.wsm.autolan.AutoLan.LOGGER.info("[GameMenuScreen] Состояние сервера: isRemote={}, isLanOpenedManually={}", 
                                                   isServerRemote, isLanOpenedManually);
                
                if (PLAYER_REPORTING_TEXT.equals(button.getMessage())) {
                    // Если LAN не запущен или запущен автоматически (но не вручную),
                    // показываем кнопку "Открыть для сети"
                    if (!isServerRemote || (isServerRemote && !isLanOpenedManually)) {
                    button.setMessage(OPEN_LAN_TEXT);
                    ((ButtonWidgetAccessor) button)
                            .setOnPress(btn -> this.client.setScreen(new CustomOpenToLanScreen(this)));
                        org.wsm.autolan.AutoLan.LOGGER.info("[GameMenuScreen] Заменили кнопку 'Player Reporting' на 'Открыть для сети', LAN открыт вручную: {}", isLanOpenedManually);
                    } else {
                        // Иначе оставляем кнопку "Жалобы" как в ванильном Minecraft
                        org.wsm.autolan.AutoLan.LOGGER.info("[GameMenuScreen] Оставили кнопку 'Player Reporting', LAN открыт вручную: {}", isLanOpenedManually);
                    }
                }

                // Случай, когда кнопка "Открыть для сети" уже на месте: просто меняем действие
                if (OPEN_LAN_TEXT.equals(button.getMessage())) {
                    ((ButtonWidgetAccessor) button)
                            .setOnPress(btn -> this.client.setScreen(new CustomOpenToLanScreen(this)));
                    org.wsm.autolan.AutoLan.LOGGER.info("[GameMenuScreen] Настроили обработчик для кнопки 'Открыть для сети'");
                }
            }
        }
    }
}
