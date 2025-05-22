package camp.visual.android.sdk.sample.ui.settings;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import camp.visual.android.sdk.sample.R;
import camp.visual.android.sdk.sample.data.settings.SettingsRepository;
import camp.visual.android.sdk.sample.data.settings.SharedPrefsSettingsRepository;
import camp.visual.android.sdk.sample.domain.model.UserSettings;
import camp.visual.android.sdk.sample.service.tracking.GazeTrackingService;

public class SettingsActivity extends AppCompatActivity {

    private SettingsRepository settingsRepository;
    private UserSettings currentSettings;

    // UI 요소
    private SeekBar fixationDurationBar;
    private TextView fixationDurationText;
    private SeekBar aoiRadiusBar;
    private TextView aoiRadiusText;
    private SeekBar edgeTriggerTimeBar;
    private TextView edgeTriggerTimeText;
    private SeekBar scrollCountBar;
    private TextView scrollCountText;

    private Switch clickEnabledSwitch;
    private Switch scrollEnabledSwitch;
    private Switch edgeScrollEnabledSwitch;
    private Switch blinkDetectionSwitch;
    private Switch autoOnePointCalibrationSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 액션바 설정
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("시선 추적 설정");
        }

        // 설정 저장소 초기화
        settingsRepository = new SharedPrefsSettingsRepository(this);
        currentSettings = settingsRepository.getUserSettings();

        // UI 초기화
        initViews();
        loadSettings();
        setupListeners();
    }

    private void initViews() {
        // SeekBar와 TextView 초기화
        fixationDurationBar = findViewById(R.id.seekbar_fixation_duration);
        fixationDurationText = findViewById(R.id.text_fixation_duration);
        aoiRadiusBar = findViewById(R.id.seekbar_aoi_radius);
        aoiRadiusText = findViewById(R.id.text_aoi_radius);
        edgeTriggerTimeBar = findViewById(R.id.seekbar_edge_trigger_time);
        edgeTriggerTimeText = findViewById(R.id.text_edge_trigger_time);
        scrollCountBar = findViewById(R.id.seekbar_scroll_count);
        scrollCountText = findViewById(R.id.text_scroll_count);

        // Switch 초기화
        clickEnabledSwitch = findViewById(R.id.switch_click_enabled);
        scrollEnabledSwitch = findViewById(R.id.switch_scroll_enabled);
        edgeScrollEnabledSwitch = findViewById(R.id.switch_edge_scroll_enabled);
        blinkDetectionSwitch = findViewById(R.id.switch_blink_detection);
        autoOnePointCalibrationSwitch = findViewById(R.id.switch_auto_one_point_calibration);

        // SeekBar 범위 설정
        fixationDurationBar.setMax(30); // 300ms ~ 3000ms
        aoiRadiusBar.setMax(60); // 10 ~ 70
        edgeTriggerTimeBar.setMax(40); // 1000ms ~ 5000ms
        scrollCountBar.setMax(4); // 1 ~ 5
    }

    private void loadSettings() {
        // SeekBar 설정
        fixationDurationBar.setProgress((int)((currentSettings.getFixationDurationMs() - 300) / 100));
        updateFixationDurationText();

        aoiRadiusBar.setProgress((int)(currentSettings.getAoiRadius() - 10));
        updateAoiRadiusText();

        edgeTriggerTimeBar.setProgress((int)((currentSettings.getEdgeTriggerMs() - 1000) / 100));
        updateEdgeTriggerTimeText();

        scrollCountBar.setProgress(currentSettings.getContinuousScrollCount() - 1);
        updateScrollCountText();

        // Switch 설정
        clickEnabledSwitch.setChecked(currentSettings.isClickEnabled());
        scrollEnabledSwitch.setChecked(currentSettings.isScrollEnabled());
        edgeScrollEnabledSwitch.setChecked(currentSettings.isEdgeScrollEnabled());
        blinkDetectionSwitch.setChecked(currentSettings.isBlinkDetectionEnabled());
        autoOnePointCalibrationSwitch.setChecked(currentSettings.isAutoOnePointCalibrationEnabled());

        // 스크롤 관련 설정의 활성화 상태 업데이트
        updateScrollSettingsState();
    }

    private void setupListeners() {
        // SeekBar 리스너
        fixationDurationBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateFixationDurationText();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();
            }
        });

        aoiRadiusBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateAoiRadiusText();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();
            }
        });

        edgeTriggerTimeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateEdgeTriggerTimeText();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();
            }
        });

        scrollCountBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateScrollCountText();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();
            }
        });

        // Switch 리스너
        clickEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());

        scrollEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateScrollSettingsState();
            saveSettings();
        });

        edgeScrollEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());

        blinkDetectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());

        // 자동 1포인트 캘리브레이션 스위치 리스너 추가
        autoOnePointCalibrationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSettings();

            // 서비스에 설정 변경 알림
            if (GazeTrackingService.getInstance() != null) {
                GazeTrackingService.getInstance().refreshSettings();
            }
        });
    }

    private void updateFixationDurationText() {
        float value = 300 + (fixationDurationBar.getProgress() * 100);
        fixationDurationText.setText(String.format("%.1f초", value / 1000));
    }

    private void updateAoiRadiusText() {
        float value = 10 + aoiRadiusBar.getProgress();
        aoiRadiusText.setText(String.format("%.0f 픽셀", value));
    }

    private void updateEdgeTriggerTimeText() {
        float value = 1000 + (edgeTriggerTimeBar.getProgress() * 100);
        edgeTriggerTimeText.setText(String.format("%.1f초", value / 1000));
    }

    private void updateScrollCountText() {
        int value = scrollCountBar.getProgress() + 1;
        scrollCountText.setText(String.format("%d회", value));
    }

    private void updateScrollSettingsState() {
        boolean scrollEnabled = scrollEnabledSwitch.isChecked();
        edgeScrollEnabledSwitch.setEnabled(scrollEnabled);
        edgeTriggerTimeBar.setEnabled(scrollEnabled);
        scrollCountBar.setEnabled(scrollEnabled);
    }

    private void saveSettings() {
        UserSettings.Builder builder = new UserSettings.Builder()
                .fixationDurationMs(300 + (fixationDurationBar.getProgress() * 100))
                .aoiRadius(10 + aoiRadiusBar.getProgress())
                .scrollEnabled(scrollEnabledSwitch.isChecked())
                .edgeMarginRatio(0.01f) // 고정 값 사용
                .edgeTriggerMs(1000 + (edgeTriggerTimeBar.getProgress() * 100))
                .continuousScrollCount(scrollCountBar.getProgress() + 1)
                .clickEnabled(clickEnabledSwitch.isChecked())
                .edgeScrollEnabled(edgeScrollEnabledSwitch.isChecked())
                .blinkDetectionEnabled(blinkDetectionSwitch.isChecked())
                .autoOnePointCalibrationEnabled(autoOnePointCalibrationSwitch.isChecked());

        UserSettings newSettings = builder.build();
        settingsRepository.saveUserSettings(newSettings);
        currentSettings = newSettings;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}