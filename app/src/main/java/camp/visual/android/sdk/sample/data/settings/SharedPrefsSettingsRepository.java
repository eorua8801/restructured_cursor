package camp.visual.android.sdk.sample.data.settings;

import android.content.Context;
import android.content.SharedPreferences;

import camp.visual.android.sdk.sample.domain.model.UserSettings;

public class SharedPrefsSettingsRepository implements SettingsRepository {
    private static final String PREFS_NAME = "eye_tracking_settings";

    // 설정 키
    private static final String KEY_FIXATION_DURATION = "fixation_duration";
    private static final String KEY_AOI_RADIUS = "aoi_radius";
    private static final String KEY_SCROLL_ENABLED = "scroll_enabled";
    private static final String KEY_EDGE_MARGIN_RATIO = "edge_margin_ratio";
    private static final String KEY_EDGE_TRIGGER_MS = "edge_trigger_ms";
    private static final String KEY_CONTINUOUS_SCROLL_COUNT = "continuous_scroll_count";
    private static final String KEY_CLICK_ENABLED = "click_enabled";
    private static final String KEY_EDGE_SCROLL_ENABLED = "edge_scroll_enabled";
    private static final String KEY_BLINK_DETECTION_ENABLED = "blink_detection_enabled";

    private final SharedPreferences prefs;

    public SharedPrefsSettingsRepository(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public UserSettings getUserSettings() {
        return new UserSettings.Builder()
                .fixationDurationMs(prefs.getFloat(KEY_FIXATION_DURATION, 1000f))
                .aoiRadius(prefs.getFloat(KEY_AOI_RADIUS, 40f))
                .scrollEnabled(prefs.getBoolean(KEY_SCROLL_ENABLED, true))
                .edgeMarginRatio(prefs.getFloat(KEY_EDGE_MARGIN_RATIO, 0.01f))
                .edgeTriggerMs(prefs.getLong(KEY_EDGE_TRIGGER_MS, 3000))
                .continuousScrollCount(prefs.getInt(KEY_CONTINUOUS_SCROLL_COUNT, 2))
                .clickEnabled(prefs.getBoolean(KEY_CLICK_ENABLED, true))
                .edgeScrollEnabled(prefs.getBoolean(KEY_EDGE_SCROLL_ENABLED, true))
                .blinkDetectionEnabled(prefs.getBoolean(KEY_BLINK_DETECTION_ENABLED, false))
                .build();
    }

    @Override
    public void saveUserSettings(UserSettings settings) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat(KEY_FIXATION_DURATION, settings.getFixationDurationMs());
        editor.putFloat(KEY_AOI_RADIUS, settings.getAoiRadius());
        editor.putBoolean(KEY_SCROLL_ENABLED, settings.isScrollEnabled());
        editor.putFloat(KEY_EDGE_MARGIN_RATIO, settings.getEdgeMarginRatio());
        editor.putLong(KEY_EDGE_TRIGGER_MS, settings.getEdgeTriggerMs());
        editor.putInt(KEY_CONTINUOUS_SCROLL_COUNT, settings.getContinuousScrollCount());
        editor.putBoolean(KEY_CLICK_ENABLED, settings.isClickEnabled());
        editor.putBoolean(KEY_EDGE_SCROLL_ENABLED, settings.isEdgeScrollEnabled());
        editor.putBoolean(KEY_BLINK_DETECTION_ENABLED, settings.isBlinkDetectionEnabled());
        editor.apply();
    }

    @Override
    public void setDefaultSettings() {
        saveUserSettings(new UserSettings.Builder().build());
    }
}