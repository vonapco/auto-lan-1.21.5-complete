package org.wsm.autolan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

public class AutoLanState extends PersistentState {
    private static final String WHITELIST_ENABLED_KEY = "whitelistEnabled";
    private static final String CUSTOM_COMMANDS_ALLOWED_KEY = "customCommandsAllowed";

    public static final String CUSTOM_LAN_KEY = AutoLan.MODID;
    public static final Codec<AutoLanState> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(Codec.BOOL.optionalFieldOf(WHITELIST_ENABLED_KEY, false)
                            .forGetter(state -> state.whitelistEnabled),
                   Codec.BOOL.optionalFieldOf(CUSTOM_COMMANDS_ALLOWED_KEY, true)
                            .forGetter(state -> state.customCommandsAllowed))
            .apply(instance, (whitelistEnabled, customCommandsAllowed) -> {
                AutoLanState state = new AutoLanState();
                state.whitelistEnabled = whitelistEnabled;
                state.customCommandsAllowed = customCommandsAllowed;
                return state;
            }));
    public static final PersistentStateType<AutoLanState> STATE_TYPE = new PersistentStateType<>(CUSTOM_LAN_KEY,
            AutoLanState::new, CODEC, null);

    private boolean whitelistEnabled;
    private boolean customCommandsAllowed;

    public AutoLanState() {
        this.whitelistEnabled = false;
        this.customCommandsAllowed = true; // По умолчанию разрешаем команды
    }

    public boolean getWhitelistEnabled() {
        return this.whitelistEnabled;
    }

    public void setWhitelistEnabled(boolean whitelistEnabled) {
        this.whitelistEnabled = whitelistEnabled;
        this.markDirty();
    }
    
    /**
     * Получает значение собственного флага разрешения команд
     * @return true, если команды разрешены
     */
    public boolean getCustomCommandsAllowed() {
        return this.customCommandsAllowed;
    }
    
    /**
     * Устанавливает значение собственного флага разрешения команд
     * @param allowed true, если команды разрешены
     */
    public void setCustomCommandsAllowed(boolean allowed) {
        this.customCommandsAllowed = allowed;
        this.markDirty();
    }
}
