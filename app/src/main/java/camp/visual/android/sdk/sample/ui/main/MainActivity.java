package camp.visual.android.sdk.sample.ui.main;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
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
    private Button btnStartCalibration, btnSettings;
    private CalibrationViewer viewCalibration;
    private final ViewLayoutChecker viewLayoutChecker = new ViewLayoutChecker();
    private Handler backgroundHandler;
    private final HandlerThread backgroundThread = new HandlerThread("background");

    // 서비스에서 캘리브레이션을 트리거하기 위한 인스턴스 참조
    private static MainActivity instance;

    public static MainActivity getInstance() {
        return instance;
    }

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
            showToast("캘리브레이션 완료", true);
        }

        @Override
        public void onCalibrationCanceled(double[] doubles) {
            hideCalibrationView();
            showToast("캘리브레이션 취소됨", true);
        }
    };

    private final StatusCallback statusCallback = new StatusCallback() {
        @Override
        public void onStarted() {
            // 추적이 시작되면 캘리브레이션 가능
            runOnUiThread(() -> {
                btnStartCalibration.setEnabled(true);
            });
        }

        @Override
        public void onStopped(StatusErrorType error) {
            runOnUiThread(() -> {
                btnStartCalibration.setEnabled(false);
            });
            if (error != StatusErrorType.ERROR_NONE) {
                if (error == StatusErrorType.ERROR_CAMERA_START) {
                    showToast("카메라 시작 오류", false);
                } else if (error == StatusErrorType.ERROR_CAMERA_INTERRUPT) {
                    showToast("카메라 중단 오류", false);
                }
            }
        }
    };

    private final View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d("MainActivity", "버튼 클릭됨: " + v.getId());

            if (v == btnStartCalibration) {
                Log.d("MainActivity", "캘리브레이션 버튼 클릭됨");

                if (gazeTracker != null || isServiceRunning()) {
                    startCalibration();
                } else {
                    Log.w("MainActivity", "GazeTracker와 서비스 모두 사용 불가");
                    showToast("시선 추적 시스템을 초기화하는 중입니다. 잠시 후 다시 시도해주세요.", false);

                    // 초기화 재시도
                    showProgress();
                    initTracker();
                }
            }
        }
    };

    private final InitializationCallback initializationCallback = (gazeTracker, error) -> {
        if (gazeTracker == null) {
            showToast("초기화 오류: " + error.name(), true);
            hideProgress();
        } else {
            // 서비스가 이미 실행 중이면 MainActivity에서는 SDK 사용하지 않음
            if (isServiceRunning()) {
                Log.d("MainActivity", "서비스가 실행 중이므로 MainActivity SDK 사용하지 않음");
                gazeTracker.stopTracking();
                btnStartCalibration.setEnabled(true);
            } else {
                // 서비스가 없는 경우에만 MainActivity에서 SDK 사용
                this.gazeTracker = gazeTracker;
                this.gazeTracker.setTrackingCallback(trackingCallback);
                this.gazeTracker.setCalibrationCallback(calibrationCallback);
                this.gazeTracker.setStatusCallback(statusCallback);

                // 자동으로 추적 시작
                this.gazeTracker.startTracking();

                // UI 업데이트
                runOnUiThread(() -> {
                    btnStartCalibration.setEnabled(true);
                });
            }
        }
        hideProgress();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

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

        // 서비스 시작 및 권한 확인
        startServicesAndCheckPermissions();
    }

    // 서비스 시작 및 권한 확인
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

    // 오버레이 권한 요청 다이얼로그
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

    // 접근성 서비스 확인
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

    // 접근성 권한 요청 다이얼로그
    private void showAccessibilityPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("접근성 권한 설정 필요")
                .setMessage("시선 클릭 기능을 사용하려면 접근성 권한 설정이 필요합니다.\n\n" +
                        "설정 화면에서 'EyedidSampleApp'을 찾아 활성화해주세요.\n" +
                        "'사용 안 함'을 '사용 중'으로 바꾸고 확인을 누르세요.\n\n" +
                        "지금 설정 화면으로 이동할까요?")
                .setPositiveButton("이동", (d, which) -> {
                    openAccessibilitySettings();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // 접근성 설정 열기 - 가능하면 앱별 설정으로 직접 이동
    private void openAccessibilitySettings() {
        try {
            // 방법 1: 특정 접근성 서비스 설정으로 직접 이동 시도
            ComponentName componentName = new ComponentName(getPackageName(),
                    MyAccessibilityService.class.getName());
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);

            // Android 5.0+ (API 21+)에서 지원하는 특정 서비스로 이동
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Bundle bundle = new Bundle();
                String showArgs = componentName.flattenToString();
                bundle.putString(":settings:fragment_args_key", showArgs);
                intent.putExtra(":settings:show_fragment_args", bundle);
                intent.putExtra(":settings:fragment_args_key", showArgs);
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            // 설정 화면 이동 성공 메시지
            showToast("'EyedidSampleApp'을 찾아 활성화해주세요", false);

        } catch (Exception e) {
            // 실패시 일반 접근성 설정으로 이동
            Log.d("MainActivity", "특정 서비스 설정 이동 실패, 일반 설정으로 이동");
            try {
                Intent fallbackIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallbackIntent);
                showToast("접근성 설정에서 'EyedidSampleApp'을 찾아 활성화해주세요", false);
            } catch (Exception ex) {
                showToast("설정 화면을 열 수 없습니다", false);
            }
        }
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

        // 권한 상태 확인
        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog();
            return;
        }

        if (!isAccessibilityServiceEnabled()) {
            showToast("접근성 서비스를 활성화해주세요", true);
            return;
        }

        // 서비스 상태 확인 및 연동
        if (isServiceRunning()) {
            // 서비스가 실행 중이면 UI 활성화
            Log.d("MainActivity", "서비스 실행 중 - UI 활성화");
            btnStartCalibration.setEnabled(true);
            hideProgress();

            // 서비스에 이미 SDK가 있으면 MainActivity의 tracker는 해제
            if (gazeTracker != null) {
                Log.d("MainActivity", "서비스 실행 중이므로 MainActivity tracker 해제");
                gazeTracker.stopTracking();
                gazeTracker = null;
            }
        } else {
            // 서비스가 없으면 새로 시작
            Log.d("MainActivity", "서비스 시작");
            Intent serviceIntent = new Intent(this, GazeTrackingService.class);
            startForegroundService(serviceIntent);

            // 서비스 시작 후 잠시 대기 후 상태 확인
            backgroundHandler.postDelayed(() -> {
                runOnUiThread(() -> {
                    if (isServiceRunning()) {
                        btnStartCalibration.setEnabled(true);
                        hideProgress();
                    }
                });
            }, 1000);
        }
    }

    private void initViews() {
        TextView txtSDKVersion = findViewById(R.id.txt_sdk_version);
        txtSDKVersion.setText(GazeTracker.getVersionName());
        layoutProgress = findViewById(R.id.layout_progress);
        viewCalibration = findViewById(R.id.view_calibration);
        viewPoint = findViewById(R.id.view_point);

        // 캘리브레이션 버튼만 활성화
        btnStartCalibration = findViewById(R.id.btn_start_calibration);
        btnStartCalibration.setOnClickListener(onClickListener);
        btnStartCalibration.setText("캘리브레이션 시작");

        // 설정 버튼
        btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // 초기 상태 설정
        btnStartCalibration.setEnabled(false);
        viewPoint.setPosition(-999,-999);

        // 오프셋 설정 개선 - 뷰가 완전히 그려진 후 계산
        viewCalibration.post(() -> {
            viewLayoutChecker.setOverlayView(viewPoint, (x, y) -> {
                viewPoint.setOffset(x, y);
                viewCalibration.setOffset(x, y);
                Log.d("MainActivity", "Offset 설정됨: x=" + x + ", y=" + y);
            });
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
            showToast("권한이 필요합니다", true);
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
        // 서비스가 이미 실행 중인지 확인
        if (isServiceRunning()) {
            Log.d("MainActivity", "서비스가 이미 실행 중입니다. SDK 재초기화를 건너뜁니다.");
            // 서비스가 실행 중이면 바로 UI 활성화
            hideProgress();
            btnStartCalibration.setEnabled(true);
            showToast("시선 추적 서비스 연결됨", true);
        } else {
            // 서비스가 없으면 새로 초기화
            showProgress();
            initTracker();
        }
    }

    private boolean isServiceRunning() {
        return GazeTrackingService.getInstance() != null;
    }

    private void initTracker() {
        // 서비스에서 SDK를 관리하므로 MainActivity에서는 간단하게 처리
        // 서비스가 시작되었다면 callback 설정만
        if (isServiceRunning()) {
            Log.d("MainActivity", "서비스 연결 완료");
            btnStartCalibration.setEnabled(true);
            hideProgress();
        } else {
            // 서비스가 없는 경우에만 SDK 초기화
            Log.d("MainActivity", "새로운 SDK 초기화 시작");
            GazeTrackerOptions options = new GazeTrackerOptions.Builder().build();
            GazeTracker.initGazeTracker(this, EYEDID_SDK_LICENSE, initializationCallback, options);
        }
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
        Log.d("MainActivity", "캘리브레이션 시작 요청");

        // 1. 먼저 서비스 상태 확인
        boolean serviceRunning = isServiceRunning();
        Log.d("MainActivity", "서비스 실행 상태: " + serviceRunning);

        if (serviceRunning) {
            // 서비스가 실행 중이면 서비스에서 캘리브레이션 실행
            Log.d("MainActivity", "서비스에서 캘리브레이션 실행 시도");
            try {
                GazeTrackingService service = GazeTrackingService.getInstance();
                Log.d("MainActivity", "서비스 인스턴스: " + (service != null ? "OK" : "NULL"));

                if (service != null) {
                    Log.d("MainActivity", "서비스 triggerCalibration() 호출 시작");
                    service.triggerCalibration();
                    Log.d("MainActivity", "서비스 triggerCalibration() 호출 완료");
                } else {
                    Log.e("MainActivity", "서비스 인스턴스가 null입니다");
                    showToast("서비스에 연결할 수 없습니다", false);

                    // 서비스 재시작 시도
                    Intent serviceIntent = new Intent(this, GazeTrackingService.class);
                    startForegroundService(serviceIntent);
                }
            } catch (Exception e) {
                Log.e("MainActivity", "서비스 캘리브레이션 호출 중 오류: " + e.getMessage(), e);
                showToast("캘리브레이션 실행 오류: " + e.getMessage(), false);

                // 오류 발생시 MainActivity에서 실행 시도
                attemptMainActivityCalibration();
            }
            return;
        }

        // 2. 서비스가 없는 경우 MainActivity에서 실행
        attemptMainActivityCalibration();
    }

    private void attemptMainActivityCalibration() {
        Log.d("MainActivity", "MainActivity에서 캘리브레이션 실행 시도");

        if (gazeTracker == null) {
            Log.e("MainActivity", "GazeTracker가 null입니다");
            showToast("시선 추적기가 초기화되지 않았습니다", false);

            // 다시 초기화 시도
            showProgress();
            initTracker();
            return;
        }

        Log.d("MainActivity", "GazeTracker로 캘리브레이션 시작");
        boolean isSuccess = gazeTracker.startCalibration(calibrationType);
        Log.d("MainActivity", "캘리브레이션 시작 결과: " + isSuccess);

        if (isSuccess) {
            isFirstPoint = true;
            runOnUiThread(() -> {
                viewCalibration.setPointPosition(-9999, -9999);
                viewCalibration.setEnableText(true);
                viewPoint.setVisibility(View.INVISIBLE);
                btnStartCalibration.setEnabled(false);
                Log.d("MainActivity", "캘리브레이션 UI 설정 완료");
            });
        } else {
            showToast("캘리브레이션 시작 실패", false);
            Log.e("MainActivity", "GazeTracker.startCalibration() 실패");
        }
    }

    // 서비스에서 호출할 수 있는 캘리브레이션 메서드
    public void triggerCalibrationFromService() {
        runOnUiThread(() -> {
            if (btnStartCalibration.isEnabled()) {
                startCalibration();
            } else {
                showToast("캘리브레이션을 시작할 수 없습니다", false);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gazeTracker != null) {
            gazeTracker.stopTracking();
        }
        backgroundThread.quitSafely();
        instance = null;
    }
}