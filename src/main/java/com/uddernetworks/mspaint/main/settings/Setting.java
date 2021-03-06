package com.uddernetworks.mspaint.main.settings;

import java.util.Arrays;

import static com.uddernetworks.mspaint.main.settings.SettingType.*;

public enum Setting {
    OPEN_PROJECT("openProject", STRING),
    DARK_THEME("darkTheme", BOOLEAN),
    DATABASE_URL("databaseURL", STRING),
    DATABASE_USER("databaseUser", STRING),
    DATABASE_PASS("databasePass", STRING),
    EDIT_FILE_SIZE("editFileFontSize", INT); // The font size that files are generated in

    private final String name;
    private final SettingType settingType;

    Setting(String name, SettingType settingType) {
        this.name = name;
        this.settingType = settingType;
    }

    public String getName() {
        return name;
    }

    public SettingType getSettingType() {
        return settingType;
    }

    public static Setting fromName(String name) {
        return Arrays.stream(values()).filter(setting -> setting.name.equals(name)).findFirst().orElse(null);
    }
}
