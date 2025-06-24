package org.wsm.autolan;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = AutoLan.MODID)
public class AutoLanConfig implements ConfigData {
    public String ngrokAuthtoken = "2yeF9ck5d8Xq6AreI7ay8NXOmIM_2REsUU9wVjVH1dVpS2ARe";
    public boolean agentEnabled = true;
    public String serverUrl = "http://192.168.1.8:5000";
    public String apiKey = "13722952";
}
