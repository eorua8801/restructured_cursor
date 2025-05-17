package camp.visual.android.sdk.sample.data.settings;

import camp.visual.android.sdk.sample.domain.model.UserSettings;

public interface SettingsRepository {
    UserSettings getUserSettings();
    void saveUserSettings(UserSettings settings);
    void setDefaultSettings();
}