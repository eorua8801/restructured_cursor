package camp.visual.android.sdk.sample.ui.settings;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import camp.visual.android.sdk.sample.R;
import camp.visual.android.sdk.sample.data.settings.SettingsRepository;
import camp.visual.android.sdk.sample.data.settings.SharedPrefsSettingsRepository;
import camp.visual.android.sdk.sample.domain.model.OneEuroFilterPreset;
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

    // 커서 오프셋 UI 요소
    private SeekBar cursorOffsetXBar;
    private TextView cursorOffsetXText;
    private SeekBar cursorOffsetYBar;
    private TextView cursorOffsetYText;

    // OneEuroFilter 프리셋 UI 요소
    private RadioGroup filterPresetRadioGroup;
    private RadioButton radioStability;
    private RadioButton radioBalancedStability;
    private RadioButton radioBalanced;
    private RadioButton radioBalancedResponsive;
    private RadioButton radioResponsive;
    private RadioButton radioCustom;
    private LinearLayout customFilterLayout;

    // OneEuroFilter 커스텀 UI 요소
    private SeekBar oneEuroFreqBar;
    private TextView oneEuroFreqText;
    private SeekBar oneEuroMinCutoffBar;
    private TextView oneEuroMinCutoffText;
    private SeekBar oneEuroBetaBar;
    private TextView oneEuroBetaText;
    private SeekBar oneEuroDCutoffBar;
    private TextView oneEuroDCutoffText;

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
        // 기존 SeekBar와 TextView 초기화
        fixationDurationBar = findViewById(R.id.seekbar_fixation_duration);
        fixationDurationText = findViewById(R.id.text_fixation_duration);
        aoiRadiusBar = findViewById(R.id.seekbar_aoi_radius);
        aoiRadiusText = findViewById(R.id.text_aoi_radius);
        edgeTriggerTimeBar = findViewById(R.id.seekbar_edge_trigger_time);
        edgeTriggerTimeText = findViewById(R.id.text_edge_trigger_time);
        scrollCountBar = findViewById(R.id.seekbar_scroll_count);
        scrollCountText = findViewById(R.id.text_scroll_count);

        // 커서 오프셋 UI 초기화
        cursorOffsetXBar = findViewById(R.id.seekbar_cursor_offset_x);
        cursorOffsetXText = findViewById(R.id.text_cursor_offset_x);
        cursorOffsetYBar = findViewById(R.id.seekbar_cursor_offset_y);
        cursorOffsetYText = findViewById(R.id.text_cursor_offset_y);

        // OneEuroFilter 프리셋 UI 초기화
        filterPresetRadioGroup = findViewById(R.id.radio_group_filter_preset);
        radioStability = findViewById(R.id.radio_stability);
        radioBalancedStability = findViewById(R.id.radio_balanced_stability);
        radioBalanced = findViewById(R.id.radio_balanced);
        radioBalancedResponsive = findViewById(R.id.radio_balanced_responsive);
        radioResponsive = findViewById(R.id.radio_responsive);
        radioCustom = findViewById(R.id.radio_custom);
        customFilterLayout = findViewById(R.id.layout_custom_filter);

        // OneEuroFilter 커스텀 UI 초기화
        oneEuroFreqBar = findViewById(R.id.seekbar_one_euro_freq);
        oneEuroFreqText = findViewById(R.id.text_one_euro_freq);
        oneEuroMinCutoffBar = findViewById(R.id.seekbar_one_euro_min_cutoff);
        oneEuroMinCutoffText = findViewById(R.id.text_one_euro_min_cutoff);
        oneEuroBetaBar = findViewById(R.id.seekbar_one_euro_beta);
        oneEuroBetaText = findViewById(R.id.text_one_euro_beta);
        oneEuroDCutoffBar = findViewById(R.id.seekbar_one_euro_d_cutoff);
        oneEuroDCutoffText = findViewById(R.id.text_one_euro_d_cutoff);

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

        // 커서 오프셋 범위 설정: -50px ~ +50px (0~100으로 매핑)
        cursorOffsetXBar.setMax(100);
        cursorOffsetYBar.setMax(100);

        // OneEuroFilter 범위 설정
        oneEuroFreqBar.setMax(90); // 10 ~ 100 Hz
        oneEuroMinCutoffBar.setMax(50); // 0.0 ~ 5.0
        oneEuroBetaBar.setMax(20); // 0.0 ~ 2.0
        oneEuroDCutoffBar.setMax(50); // 0.0 ~ 5.0
    }

    private void loadSettings() {
        // 기존 SeekBar 설정
        fixationDurationBar.setProgress((int)((currentSettings.getFixationDurationMs() - 300) / 100));
        updateFixationDurationText();

        aoiRadiusBar.setProgress((int)(currentSettings.getAoiRadius() - 10));
        updateAoiRadiusText();

        edgeTriggerTimeBar.setProgress((int)((currentSettings.getEdgeTriggerMs() - 1000) / 100));
        updateEdgeTriggerTimeText();

        scrollCountBar.setProgress(currentSettings.getContinuousScrollCount() - 1);
        updateScrollCountText();

        // 커서 오프셋 설정 (-50~+50을 0~100으로 변환)
        cursorOffsetXBar.setProgress((int)(currentSettings.getCursorOffsetX() + 50));
        cursorOffsetYBar.setProgress((int)(currentSettings.getCursorOffsetY() + 50));
        updateCursorOffsetTexts();

        // OneEuroFilter 프리셋 설정
        OneEuroFilterPreset preset = currentSettings.getOneEuroFilterPreset();
        switch (preset) {
            case STABILITY:
                radioStability.setChecked(true);
                break;
            case BALANCED_STABILITY:
                radioBalancedStability.setChecked(true);
                break;
            case BALANCED:
                radioBalanced.setChecked(true);
                break;
            case BALANCED_RESPONSIVE:
                radioBalancedResponsive.setChecked(true);
                break;
            case RESPONSIVE:
                radioResponsive.setChecked(true);
                break;
            case CUSTOM:
                radioCustom.setChecked(true);
                break;
        }

        // OneEuroFilter 커스텀 설정 (항상 로드하되, 커스텀 모드일 때만 표시)
        oneEuroFreqBar.setProgress((int)(currentSettings.getOneEuroFreq() - 10));
        oneEuroMinCutoffBar.setProgress((int)(currentSettings.getOneEuroMinCutoff() * 10));
        oneEuroBetaBar.setProgress((int)(currentSettings.getOneEuroBeta() * 1000)); // 0.007 같은 작은 값 처리
        oneEuroDCutoffBar.setProgress((int)(currentSettings.getOneEuroDCutoff() * 10));
        updateOneEuroTexts();

        // 커스텀 레이아웃 표시/숨김
        updateCustomFilterVisibility();

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
        // 프리셋 라디오 그룹 리스너
        filterPresetRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            updateCustomFilterVisibility();
            saveSettings();
        });

        // 기존 SeekBar 리스너들...
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

        // 커서 오프셋 SeekBar 리스너
        cursorOffsetXBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateCursorOffsetTexts();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();
            }
        });

        cursorOffsetYBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateCursorOffsetTexts();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();
            }
        });

        // OneEuroFilter 커스텀 SeekBar 리스너
        oneEuroFreqBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateOneEuroTexts();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();
            }
        });

        oneEuroMinCutoffBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateOneEuroTexts();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();
            }
        });

        oneEuroBetaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateOneEuroTexts();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();
            }
        });

        oneEuroDCutoffBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateOneEuroTexts();
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

        autoOnePointCalibrationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSettings();

            // 서비스에 설정 변경 알림
            if (GazeTrackingService.getInstance() != null) {
                GazeTrackingService.getInstance().refreshSettings();
            }
        });
    }

    private void updateCustomFilterVisibility() {
        boolean isCustom = radioCustom.isChecked();
        customFilterLayout.setVisibility(isCustom ? View.VISIBLE : View.GONE);
    }

    private OneEuroFilterPreset getSelectedPreset() {
        int checkedId = filterPresetRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.radio_stability) return OneEuroFilterPreset.STABILITY;
        if (checkedId == R.id.radio_balanced_stability) return OneEuroFilterPreset.BALANCED_STABILITY;
        if (checkedId == R.id.radio_balanced) return OneEuroFilterPreset.BALANCED;
        if (checkedId == R.id.radio_balanced_responsive) return OneEuroFilterPreset.BALANCED_RESPONSIVE;
        if (checkedId == R.id.radio_responsive) return OneEuroFilterPreset.RESPONSIVE;
        if (checkedId == R.id.radio_custom) return OneEuroFilterPreset.CUSTOM;
        return OneEuroFilterPreset.BALANCED; // 기본값
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

    private void updateCursorOffsetTexts() {
        // 0~100을 -50~+50으로 변환
        float offsetX = cursorOffsetXBar.getProgress() - 50;
        float offsetY = cursorOffsetYBar.getProgress() - 50;

        cursorOffsetXText.setText(String.format("%.0f px", offsetX));
        cursorOffsetYText.setText(String.format("%.0f px", offsetY));
    }

    private void updateOneEuroTexts() {
        double freq = 10 + oneEuroFreqBar.getProgress();
        double minCutoff = oneEuroMinCutoffBar.getProgress() / 10.0;
        double beta = oneEuroBetaBar.getProgress() / 1000.0; // 0.001 단위로 조정
        double dCutoff = oneEuroDCutoffBar.getProgress() / 10.0;

        oneEuroFreqText.setText(String.format("%.0f Hz", freq));
        oneEuroMinCutoffText.setText(String.format("%.1f", minCutoff));
        oneEuroBetaText.setText(String.format("%.3f", beta)); // 소수점 3자리까지 표시
        oneEuroDCutoffText.setText(String.format("%.1f", dCutoff));
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
                .autoOnePointCalibrationEnabled(autoOnePointCalibrationSwitch.isChecked())
                .cursorOffsetX(cursorOffsetXBar.getProgress() - 50) // 0~100을 -50~+50으로 변환
                .cursorOffsetY(cursorOffsetYBar.getProgress() - 50) // 0~100을 -50~+50으로 변환
                .oneEuroFilterPreset(getSelectedPreset())
                .oneEuroFreq(10 + oneEuroFreqBar.getProgress())
                .oneEuroMinCutoff(oneEuroMinCutoffBar.getProgress() / 10.0)
                .oneEuroBeta(oneEuroBetaBar.getProgress() / 1000.0) // 0.001 단위
                .oneEuroDCutoff(oneEuroDCutoffBar.getProgress() / 10.0);

        UserSettings newSettings = builder.build();
        settingsRepository.saveUserSettings(newSettings);
        currentSettings = newSettings;

        // 서비스에 설정 변경 알림 (OneEuroFilter 설정도 실시간 반영)
        if (GazeTrackingService.getInstance() != null) {
            GazeTrackingService.getInstance().refreshSettings();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 설정 화면이 다시 보일 때마다 최신 설정 로드
        currentSettings = settingsRepository.getUserSettings();
        loadSettings();

        Log.d("SettingsActivity", "설정 새로고침 - 현재 커서 오프셋: X=" +
                currentSettings.getCursorOffsetX() + ", Y=" + currentSettings.getCursorOffsetY());
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