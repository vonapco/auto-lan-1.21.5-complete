package org.wsm.autolan.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldFreezeController {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldFreezeController.class);

    private static boolean isFrozen = false; // Простое состояние для отслеживания

    /**
     * "Freezes" the game world or displays a waiting UI.
     * Currently, this is a placeholder and only logs the action.
     * TODO: Implement actual game freeze logic if possible, or a UI overlay.
     */
    public static void freeze() {
        if (isFrozen) {
            LOGGER.warn("WorldFreezeController.freeze() called while already frozen.");
            return;
        }
        LOGGER.info("WorldFreezeController.freeze() called - Simulating world freeze/UI overlay.");
        // Сюда можно добавить код для отображения GUI ожидания или блокировки ввода.
        // Например, можно попробовать отправить игрока на специальный экран загрузки
        // или показать полноэкранное сообщение.
        // MinecraftClient.getInstance().setScreen(new SomeCustomLoadingScreen());
        isFrozen = true;
    }

    /**
     * "Unfreezes" the game world or hides the waiting UI.
     * Currently, this is a placeholder and only logs the action.
     * TODO: Implement actual game unfreeze logic or hide UI overlay.
     */
    public static void unfreeze() {
        if (!isFrozen) {
            LOGGER.warn("WorldFreezeController.unfreeze() called while not frozen.");
            // В некоторых случаях это может быть нормально, если unfreeze вызывается превентивно
        }
        LOGGER.info("WorldFreezeController.unfreeze() called - Simulating world unfreeze/UI hide.");
        // Если был установлен экран загрузки, здесь его нужно закрыть:
        // if (MinecraftClient.getInstance().currentScreen instanceof SomeCustomLoadingScreen) {
        // MinecraftClient.getInstance().setScreen(null);
        // }
        isFrozen = false;
    }

    /**
     * Checks if the world is currently considered "frozen" by this controller.
     * @return true if freeze() was called more recently than unfreeze(), false otherwise.
     */
    public static boolean isFrozen() {
        return isFrozen;
    }
}
