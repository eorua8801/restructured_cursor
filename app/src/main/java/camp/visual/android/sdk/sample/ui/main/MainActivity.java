package camp.visual.android.sdk.sample.ui.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import camp.visual.android.sdk.sample.R;
import camp.visual.android.sdk.sample.data.repository.EyeTrackingRepository;
import camp.visual.android.sdk.sample.data.repository.EyedidTrackingRepository;
import camp.visual.android.sdk.sample.service.accessibility.MyAccessibilityService;
import camp.visual.android.sdk.sample.service.tracking.GazeTrackingService;
import camp.visual.android.sdk.sample.ui.settings.SettingsActivity;
import camp.visual.android.sdk.sample.ui.views.CalibrationViewer;
import camp.visual.android.sdk.sample.ui.views.PointView;
import camp.visual.eyedid.gazetracker.GazeTracker;
import camp.visual.eyedid.gazetracker.callback.CalibrationCallback;
import camp.visual.eyedid.gazetracker.callback.InitializationCallback;
import camp.visual.eyedid.gazetracker.callback.StatusCallback;
import camp.visual.eyedid.gazetracker.callback.TrackingCallback;
import camp.visual.eyedid.gazetracker.constant.CalibrationModeType;
import camp.visual.eyedid.gazetracker.constant.GazeTrackerOptions;
import camp.visual.eyedid.gazetracker.constant.StatusErrorType;
import camp.visual.eyedid.gazetracker.metrics.BlinkInfo;
import camp.visual.eyedid.gazetracker.metrics.FaceInfo;
import camp.visual.eyedid.gazetracker.metrics.GazeInfo;
import camp.visual.eyedid.gazetracker.metrics.UserStatusInfo;
import camp.visual.eyedid.gazetracker.metrics.state.TrackingState;
import camp.visual.eyedid.gazetracker.util.ViewLayoutChecker;

public class MainActivity extends AppCompatActivity {
    private GazeTracker gazeTracker;
    private final String EYEDID_SDK_LICENSE = "dev_plnp4o1ya7d0tif2rmgko169l1z4jnali2q4f63f";
    private final CalibrationModeType calibrationType = CalibrationModeType.DEFAULT;
    private final String[] PERMISSIONS = new String[]{
            Manifest.permission.CAMERA
    };
    private final int REQ_PERMISSION = 1000;
    private final int REQ_OVERLAY_PERMISSION = 1001;

    private View layoutProgress;
    private PointView viewPoint;
    private boolean skipProgress = false;
    private Button btnStartTracking, btnStopTracking, btnStartCalibration, btnSettings;
    private CalibrationViewer viewCalibration;
    private final ViewLayoutChecker viewLayoutChecker = new ViewLayoutChecker();
    private Handler backgroundHandler;
    private final HandlerThread backgroundThread = new HandlerThread("background");

    private final TrackingCallback trackingCallback = new TrackingCallback() {
        @Override
        public void onMetrics(long timestamp, GazeInfo gazeInfo, FaceInfo faceInfo, BlinkInfo blinkInfo,
                              UserStatusInfo userStatusInfo) {
            if (gazeInfo.trackingState == TrackingState.SUCCESS) {
                viewPoint.setPosition(gazeInfo.x, gazeInfo.y);
            }
        }

        @Override
        public void onDrop(long timestamp) {
            Log.d("MainActivity", "drop frame " + timestamp);
        }
    };

    private boolean isFirstPoint = false;

    private final CalibrationCallback calibrationCallback = new CalibrationCallback() {

        @Override
        public void onCalibrationProgress(float progress) {
            if (!skipProgress)  {
                runOnUiThread(() -> viewCalibration.setPointAnimationPower(progress));
            }
        }

        @Override
        public void onCalibrationNextPoint(final float x, final float y) {
            runOnUiThread(() -> {
                viewCalibration.setVisibility(View.VISIBLE);
                if (isFirstPoint) {
                    backgroundHandler.postDelayed(() -> showCalibrationPointView(x, y), 2500);
                } else {
                    showCalibrationPointView(x, y);
                }
            });
        }

        @Override
        public void onCalibrationFinished(double[] calibrationData) {
            hideCalibrationView();
            showToast("calibrationFinished", true);
        }

        @Override
        public void onCalibrationCanceled(double[] doubles) {
            showToast("calibrationCanceled", true);
        }
    };

    private final StatusCallback statusCallback = new StatusCallback() {
        @Override
        public void onStarted() {
            runOnUiThread(() -> {
                btnStartTracking.setEnabled(false);
                btnStopTracking.setEnabled(true);
                btnStartCalibration.setEnabled(true);
            });
        }

        @Override
        public void onStopped(StatusErrorType error) {
            runOnUiThread(() -> {
                btnStartTracking.setEnabled(true);
                btnStopTracking.setEnabled(false);
                btnStartCalibration.setEnabled(false);
            });
            if (error != StatusErrorType.ERROR_NONE) {
                if (error == StatusErrorType.ERROR_CAMERA_START) {
                    showToast("ERROR_CAMERA_START ", false);
                } else if (error == StatusErrorType.ERROR_CAMERA_INTERRUPT) {
                    showToast("ERROR_CAMERA_INTERRUPT ", false);
                }
            }
        }
    };

    private final View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if(gazeTracker != null) {
                if (v == btnStartTracking) {
                    gazeTracker.startTracking();
                } else if (v == btnStopTracking) {
                    gazeTracker.stopTracking();
                } else if (v == btnStartCalibration) {
                    startCalibration();
                }
            }
        }
    };

    private final InitializationCallback initializationCallback = (gazeTracker, error) -> {
        if (gazeTracker == null) {
            showToast("error : " + error.name(), true);
        } else {
            this.gazeTracker = gazeTracker;
            this.gazeTracker.setTrackingCallback(trackingCallback);
            this.gazeTracker.setCalibrationCallback(calibrationCallback);
            this.gazeTracker.setStatusCallback(statusCallback);
            this.btnStartTracking.setEnabled(true);
        }
        hideProgress();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        initViews();
        checkPermission();
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        // 서비스 시작 및 권한 확인 로직 변경 부분
        startServicesAndCheckPermissions();
    }

    // 추가된 메소드: 서비스 시작 및 권한 확인
    private void startServicesAndCheckPermissions() {
        // 오버레이 권한 확인
        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog();
        } else {
            // 접근성 서비스 활성화 확인
            if (!isAccessibilityServiceEnabled()) {
                showAccessibilityPermissionDialog();
            }

            // 오버레이 권한이 있으면 서비스 시작
            Intent serviceIntent = new Intent(this, GazeTrackingService.class);
            startForegroundService(serviceIntent);
        }
    }

    // 추가된 메소드: 오버레이 권한 요청 다이얼로그
    private void showOverlayPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("화면 오버레이 권한 필요")
                .setMessage("시선 추적 기능을 사용하려면 다른 앱 위에 표시 권한이 필요합니다. 설정 화면으로 이동하시겠습니까?")
                .setPositiveButton("이동", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQ_OVERLAY_PERMISSION);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // 추가된 메소드: 접근성 서비스 확인
    private boolean isAccessibilityServiceEnabled() {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + MyAccessibilityService.class.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            // 설정을 찾을 수 없음
        }

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

            if (settingValue != null) {
                return settingValue.contains(service);
            }
        }
        return false;
    }

    // 추가된 메소드: 접근성 권한 요청 다이얼로그
    private void showAccessibilityPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("접근성 권한 설정 필요")
                .setMessage("시선 클릭 기능을 사용하려면 접근성 설정에서 다음 단계를 따라야 합니다:\n\n" +
                        "1. '설치된 앱' 항목을 누르세요\n" +
                        "2. 목록에서 'EyedidSampleApp'을 선택하세요\n" +
                        "3. '사용 안 함'을 '사용 중'으로 바꾸고 확인을 누르세요\n\n" +
                        "지금 설정 화면으로 이동할까요?")
                .setPositiveButton("이동", (d, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY_PERMISSION) {
            // 오버레이 권한 확인 후 처리
            if (Settings.canDrawOverlays(this)) {
                // 접근성 서비스 확인
                if (!isAccessibilityServiceEnabled()) {
                    showAccessibilityPermissionDialog();
                }

                // 서비스 시작
                Intent serviceIntent = new Intent(this, GazeTrackingService.class);
                startForegroundService(serviceIntent);
            } else {
                showToast("오버레이 권한이 필요합니다", false);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 화면으로 돌아올 때마다 접근성 서비스 상태 확인
        if (Settings.canDrawOverlays(this) && !isAccessibilityServiceEnabled()) {
            showAccessibilityPermissionDialog();
        }
    }

    private void initViews() {
        TextView txtSDKVersion = findViewById(R.id.txt_sdk_version);
        txtSDKVersion.setText(GazeTracker.getVersionName());
        layoutProgress = findViewById(R.id.layout_progress);
        viewCalibration = findViewById(R.id.view_calibration);
        viewPoint = findViewById(R.id.view_point);
        btnStartTracking = findViewById(R.id.btn_start_tracking);
        btnStartTracking.setOnClickListener(onClickListener);
        btnStopTracking = findViewById(R.id.btn_stop_tracking);
        btnStopTracking.setOnClickListener(onClickListener);
        btnStartCalibration = findViewById(R.id.btn_start_calibration);
        btnStartCalibration.setOnClickListener(onClickListener);

        // 설정 버튼 추가
        btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        btnStartTracking.setEnabled(false);
        btnStopTracking.setEnabled(false);
        btnStartCalibration.setEnabled(false);
        viewPoint.setPosition(-999,-999);
        viewLayoutChecker.setOverlayView(viewPoint, (x, y) -> {
            viewPoint.setOffset(x, y);
            viewCalibration.setOffset(x, y);
        });
    }

    private void checkPermission() {
        if (hasPermissions()) {
            checkPermission(true);
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQ_PERMISSION);
        }
    }

    private boolean hasPermissions() {
        int result;
        for (String perms : PERMISSIONS) {
            if (perms.equals(Manifest.permission.SYSTEM_ALERT_WINDOW)) {
                if (!Settings.canDrawOverlays(this)) {
                    return false;
                }
            }
            result = ContextCompat.checkSelfPermission(this, perms);
            if (result == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    private void checkPermission(boolean isGranted) {
        if (!isGranted) {
            showToast("not granted permissions", true);
            finish();
        } else {
            permissionGranted();
        }
    }

    private void showToast(final String msg, final boolean isShort) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg,
                isShort ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show());
    }

    private void permissionGranted() {
        showProgress();
        initTracker();
    }

    private void initTracker() {
        GazeTrackerOptions options = new GazeTrackerOptions.Builder().build();
        GazeTracker.initGazeTracker(this, EYEDID_SDK_LICENSE, initializationCallback, options);
    }

    private void showProgress() {
        if (layoutProgress != null) {
            runOnUiThread(() -> layoutProgress.setVisibility(View.VISIBLE));
        }
    }

    private void hideProgress() {
        if (layoutProgress != null) {
            runOnUiThread(() -> layoutProgress.setVisibility(View.GONE));
        }
    }

    private void hideCalibrationView() {
        runOnUiThread(() -> {
            viewCalibration.setVisibility(View.INVISIBLE);
            btnStartCalibration.setEnabled(true);
            viewPoint.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSION) {
            if (grantResults.length > 0) {
                boolean cameraPermissionAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                checkPermission(cameraPermissionAccepted);
            }
        }
    }

    private void showCalibrationPointView(final float x, final float y){
        skipProgress = true;
        viewCalibration.setPointAnimationPower(0);
        viewCalibration.setEnableText(false);
        viewCalibration.nextPointColor();
        viewCalibration.setPointPosition(x, y);
        long delay = isFirstPoint ? 0 : 1200;

        backgroundHandler.postDelayed(() -> {
            if(gazeTracker != null)
                gazeTracker.startCollectSamples();
            skipProgress = false;
        }, delay);

        isFirstPoint = false;
    }

    private void startCalibration() {
        if (gazeTracker == null) return;
        boolean isSuccess = gazeTracker.startCalibration(calibrationType);
        if (isSuccess) {
            isFirstPoint = true;
            runOnUiThread(() -> {
                viewCalibration.setPointPosition(-9999, -9999);
                viewCalibration.setEnableText(true);
                viewPoint.setVisibility(View.INVISIBLE);
                btnStartCalibration.setEnabled(false);
            });
        } else {
            showToast("calibration start fail", false);
        }
    }
}