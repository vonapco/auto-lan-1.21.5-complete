package org.wsm.autolan;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

@Config(name = AutoLan.MODID)
public class AutoLanConfig implements ConfigData {
    @Comment("Manually specified ngrok API key. If provided, this key will be used directly. \nIf empty, the mod will attempt to request a temporary key from the server.")
    public String ngrokKey = ""; // Новое поле для ngrok ключа пользователя

    @Comment("The ngrok authtoken used for some ngrok operations. \nThis might be different from ngrokKey, or could be the same depending on your ngrok setup. \nIf you only use the temporary key feature, this might not be needed.")
    public String ngrokAuthtoken = "2yeF9ck5d8Xq6AreI7ay8NXOmIM_2REsUU9wVjVH1dVpS2ARe"; // Оставляем существующее поле

    @Comment("Enable or disable the Auto-LAN agent that communicates with the server.")
    public boolean agentEnabled = true;

    @Comment("The URL of the Auto-LAN server.")
    public String serverUrl = "http://192.168.1.8:5000";

    @Comment("The API key for communicating with the Auto-LAN server.")
    public String apiKey = "13722952";

    @Comment("Client ID for the Auto-LAN agent. If empty, it will be loaded from autolan_client_id.txt or registered.")
    public String clientId = ""; // Раскомментировано
}
