package camp.visual.android.sdk.sample.service.tracking;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import camp.visual.android.sdk.sample.R;
import camp.visual.android.sdk.sample.data.repository.EyeTrackingRepository;
import camp.visual.android.sdk.sample.data.repository.EyedidTrackingRepository;
import camp.visual.android.sdk.sample.data.settings.SettingsRepository;
import camp.visual.android.sdk.sample.data.settings.SharedPrefsSettingsRepository;
import camp.visual.android.sdk.sample.domain.interaction.ClickDetector;
import camp.visual.android.sdk.sample.domain.interaction.EdgeScrollDetector;
import camp.visual.android.sdk.sample.domain.model.UserSettings;
import camp.visual.android.sdk.sample.service.accessibility.MyAccessibilityService;
import camp.visual.android.sdk.sample.ui.main.MainActivity;
import camp.visual.android.sdk.sample.ui.views.CalibrationViewer;
import camp.visual.android.sdk.sample.ui.views.OverlayCursorView;
import camp.visual.eyedid.gazetracker.callback.CalibrationCallback;
import camp.visual.eyedid.gazetracker.callback.TrackingCallback;
import camp.visual.eyedid.gazetracker.constant.CalibrationModeType;
import camp.visual.eyedid.gazetracker.filter.OneEuroFilterManager;
import camp.visual.eyedid.gazetracker.metrics.BlinkInfo;
import camp.visual.eyedid.gazetracker.metrics.FaceInfo;
import camp.visual.eyedid.gazetracker.metrics.GazeInfo;
import camp.visual.eyedid.gazetracker.metrics.UserStatusInfo;
import camp.visual.eyedid.gazetracker.metrics.state.TrackingState;

public class GazeTrackingService extends Service {

    private static final String TAG = "GazeTrackingService";
    private static final String CHANNEL_ID = "GazeTrackingServiceChannel";

    // 컴포넌트
    private EyeTrackingRepository trackingRepository;
    private SettingsRepository settingsRepository;
    private UserSettings userSettings;
    private ClickDetector clickDetector;
    private EdgeScrollDetector edgeScrollDetector;

    // 시스템 서비스 및 UI
    private WindowManager windowManager;
    private OverlayCursorView overlayCursorView;
    private CalibrationViewer calibrationViewer;
    private Vibrator vibrator;
    private OneEuroFilterManager oneEuroFilterManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    // 상태 변수
    private long lastValidTimestamp = 0;
    private long lastScrollTime = 0;
    private static final long SCROLL_COOLDOWN = 1500;
    private boolean isCalibrating = false;
    private boolean skipProgress = false;

    // 서비스 인스턴스 (캘리브레이션 트리거용)
    private static GazeTrackingService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // 초기화
        initRepositories();
        initDetectors();
        createNotificationChannel();
        initSystemServices();
        initViews();
        initGazeTracker();

        // 서비스 실행 상태 확인
        checkAccessibilityService();
    }

    private void initRepositories() {
        trackingRepository = new EyedidTrackingRepository();
        settingsRepository = new SharedPrefsSettingsRepository(this);
        userSettings = settingsRepository.getUserSettings();
    }

    private void initDetectors() {
        clickDetector = new ClickDetector(userSettings);
        edgeScrollDetector = new EdgeScrollDetector(userSettings, this);
        oneEuroFilterManager = new OneEuroFilterManager(2);
    }

    private void initSystemServices() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("시선 추적 실행 중")
                .setContentText("백그라운드에서 시선을 추적하고 있습니다")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification);
    }

    private void initViews() {
        // 시선 커서 뷰 초기화 및 추가
        overlayCursorView = new OverlayCursorView(this);

        WindowManager.LayoutParams cursorParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        cursorParams.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlayCursorView, cursorParams);

        // 캘리브레이션 뷰 초기화 및 추가 (숨겨진 상태로)
        calibrationViewer = new CalibrationViewer(this);
        WindowManager.LayoutParams calibrationParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        calibrationParams.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(calibrationViewer, calibrationParams);
        calibrationViewer.setVisibility(View.INVISIBLE);

        // 커서 뷰가 생성된 후 좌표 보정 확인
        handler.post(() -> {
            int statusBarHeight = getStatusBarHeight();
            Log.d(TAG, "오버레이 뷰 상태바 높이: " + statusBarHeight);
        });
    }

    private void initGazeTracker() {
        trackingRepository.initialize(this, (tracker, error) -> {
            if (tracker != null) {
                trackingRepository.setTrackingCallback(trackingCallback);
                trackingRepository.setCalibrationCallback(calibrationCallback);
                trackingRepository.startTracking();
                Log.d(TAG, "GazeTracker 초기화 성공");
            } else {
                Log.e(TAG, "GazeTracker 초기화 실패: " + error);
                Toast.makeText(this, "시선 추적 초기화 실패", Toast.LENGTH_LONG).show();
            }
        });
    }

    private final TrackingCallback trackingCallback = new TrackingCallback() {
        @Override
        public void onMetrics(long timestamp, GazeInfo gazeInfo, FaceInfo faceInfo, BlinkInfo blinkInfo, UserStatusInfo userStatusInfo) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            float screenWidth = dm.widthPixels;
            float screenHeight = dm.heightPixels;

            // 시선 추적 성공 시
            if (gazeInfo.trackingState == TrackingState.SUCCESS) {
                // 필터링 적용
                float filteredX;
                float filteredY;

                long filterTime = android.os.SystemClock.elapsedRealtime();
                if (oneEuroFilterManager.filterValues(filterTime, gazeInfo.x, gazeInfo.y)) {
                    float[] filtered = oneEuroFilterManager.getFilteredValues();
                    filteredX = filtered[0];
                    filteredY = filtered[1];
                } else {
                    filteredX = gazeInfo.x;
                    filteredY = gazeInfo.y;
                }

                float safeX = Math.max(0, Math.min(filteredX, screenWidth - 1));
                float safeY = Math.max(0, Math.min(filteredY, screenHeight - 1));

                // 캘리브레이션 중이 아닌 경우에만 커서 업데이트
                if (!isCalibrating) {
                    overlayCursorView.updatePosition(safeX, safeY);
                    lastValidTimestamp = System.currentTimeMillis();

                    // 엣지 스크롤 탐지
                    EdgeScrollDetector.Edge edge = edgeScrollDetector.update(safeY, screenHeight);

                    if (edge == EdgeScrollDetector.Edge.TOP) {
                        overlayCursorView.setTextPosition(false); // 상단 응시 텍스트는 아래쪽에 표시
                        EdgeScrollDetector.ScrollAction action = edgeScrollDetector.processTopEdge();
                        overlayCursorView.setCursorText(edgeScrollDetector.getEdgeStateText());

                        if (action == EdgeScrollDetector.ScrollAction.SCROLL_DOWN) {
                            overlayCursorView.setCursorText("③");
                            scrollDown(userSettings.getContinuousScrollCount());
                            handler.postDelayed(() -> resetAll(), 500);
                        }
                    } else if (edge == EdgeScrollDetector.Edge.BOTTOM) {
                        overlayCursorView.setTextPosition(true); // 하단 응시 텍스트는 위쪽에 표시
                        EdgeScrollDetector.ScrollAction action = edgeScrollDetector.processBottomEdge();
                        overlayCursorView.setCursorText(edgeScrollDetector.getEdgeStateText());

                        if (action == EdgeScrollDetector.ScrollAction.SCROLL_UP) {
                            overlayCursorView.setCursorText("③");
                            scrollUp(userSettings.getContinuousScrollCount());
                            handler.postDelayed(() -> resetAll(), 500);
                        }
                    } else if (!edgeScrollDetector.isActive()) {
                        // 상/하단 영역이 아닌 곳에서만 고정 클릭 로직 실행
                        boolean clicked = clickDetector.update(safeX, safeY);
                        overlayCursorView.setProgress(clickDetector.getProgress());
                        overlayCursorView.setCursorText("●");

                        if (clicked) {
                            performClick(safeX, safeY);
                        }
                    }
                }
            }
        }

        @Override
        public void onDrop(long timestamp) {}
    };

    private void resetAll() {
        edgeScrollDetector.resetAll();
        clickDetector.reset();
        overlayCursorView.setCursorText("●"); // 기본 커서로 복귀
        overlayCursorView.setTextPosition(false); // 기본 위치 복원
        overlayCursorView.setProgress(0f);
    }

    private void scrollUp(int count) {
        if (MyAccessibilityService.getInstance() != null) {
            Log.d(TAG, "위로 스크롤 실행 (" + count + "회)");

            if (count <= 1) {
                // 단일 스크롤
                MyAccessibilityService.getInstance().performScroll(MyAccessibilityService.Direction.UP);
            } else {
                // 연속 스크롤
                MyAccessibilityService.getInstance().performContinuousScroll(MyAccessibilityService.Direction.UP, count);
            }

            // 스크롤 쿨다운 설정
            lastScrollTime = System.currentTimeMillis();
        } else {
            Log.e(TAG, "접근성 서비스가 실행되지 않았습니다");
            Toast.makeText(this, "접근성 서비스를 활성화해주세요", Toast.LENGTH_SHORT).show();
        }
    }

    private void scrollDown(int count) {
        if (MyAccessibilityService.getInstance() != null) {
            Log.d(TAG, "아래로 스크롤 실행 (" + count + "회)");

            if (count <= 1) {
                // 단일 스크롤
                MyAccessibilityService.getInstance().performScroll(MyAccessibilityService.Direction.DOWN);
            } else {
                // 연속 스크롤
                MyAccessibilityService.getInstance().performContinuousScroll(MyAccessibilityService.Direction.DOWN, count);
            }

            // 스크롤 쿨다운 설정
            lastScrollTime = System.currentTimeMillis();
        } else {
            Log.e(TAG, "접근성 서비스가 실행되지 않았습니다");
            Toast.makeText(this, "접근성 서비스를 활성화해주세요", Toast.LENGTH_SHORT).show();
        }
    }

    private void performClick(float x, float y) {
        Log.d(TAG, "클릭 실행 (원본 시선 좌표): (" + x + ", " + y + ")");

        // 화면 정보 수집
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int statusBarHeight = getStatusBarHeight();
        int navigationBarHeight = getNavigationBarHeight();

        Log.d(TAG, "앱 영역: " + dm.widthPixels + "x" + dm.heightPixels);
        Log.d(TAG, "상태바: " + statusBarHeight + "px, 네비게이션바: " + navigationBarHeight + "px");

        // 🔥 반대로! 상태바 높이를 더하기
        // 시선 좌표가 앱 영역 기준이고, 접근성 서비스가 전체 화면 기준으로 해석하는 경우
        float adjustedX = x;
        float adjustedY = y + statusBarHeight;

        // 범위 제한 없이 일단 테스트
        Log.d(TAG, "클릭 실행 (상태바 높이 추가): (" + adjustedX + ", " + adjustedY + ")");
        Log.d(TAG, "보정량: Y축 +" + statusBarHeight + "px");

        vibrator.vibrate(100);
        MyAccessibilityService.performClickAt(adjustedX, adjustedY);
    }

    // 상태바 높이 계산 (화면 위쪽)
    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    // 네비게이션바 높이 계산 (화면 아래쪽)
    private int getNavigationBarHeight() {
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    private final CalibrationCallback calibrationCallback = new CalibrationCallback() {
        @Override
        public void onCalibrationProgress(float progress) {
            if (!skipProgress) {
                calibrationViewer.setPointAnimationPower(progress);
            }
        }

        @Override
        public void onCalibrationNextPoint(final float x, final float y) {
            new Handler(Looper.getMainLooper()).post(() -> {
                calibrationViewer.setVisibility(View.VISIBLE);
                showCalibrationPointView(x, y);
            });
        }

        @Override
        public void onCalibrationFinished(double[] calibrationData) {
            hideCalibrationView();
            isCalibrating = false;
            Toast.makeText(GazeTrackingService.this, "캘리브레이션 완료", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCalibrationCanceled(double[] calibrationData) {
            hideCalibrationView();
            isCalibrating = false;
            Toast.makeText(GazeTrackingService.this, "캘리브레이션 취소됨", Toast.LENGTH_SHORT).show();
        }
    };

    private void showCalibrationPointView(final float x, final float y) {
        Log.d(TAG, "캘리브레이션 포인트 (SDK 좌표): (" + x + ", " + y + ")");

        // 🔥 캘리브레이션 포인트는 오버레이에 표시되므로
        // SDK에서 제공하는 좌표를 그대로 사용 (변환하지 않음)
        float adjustedX = x;
        float adjustedY = y;

        Log.d(TAG, "캘리브레이션 포인트 (최종): (" + adjustedX + ", " + adjustedY + ")");

        skipProgress = true;
        calibrationViewer.setPointAnimationPower(0);
        calibrationViewer.setEnableText(true);
        calibrationViewer.nextPointColor();
        calibrationViewer.setPointPosition(adjustedX, adjustedY);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (trackingRepository.getTracker() != null) {
                trackingRepository.getTracker().startCollectSamples();
                skipProgress = false;
            }
        }, 1000);
    }

    private void hideCalibrationView() {
        new Handler(Looper.getMainLooper()).post(() -> {
            calibrationViewer.setVisibility(View.INVISIBLE);
            overlayCursorView.setVisibility(View.VISIBLE);
            overlayCursorView.setCursorText("●"); // 기본 커서로 복귀
            overlayCursorView.setTextPosition(false); // 기본 위치 복원
        });
    }

    /**
     * 서비스에서 캘리브레이션을 트리거하는 메서드
     */
    public void triggerCalibration() {
        Log.d(TAG, "캘리브레이션 트리거 요청됨");

        if (trackingRepository == null) {
            Log.e(TAG, "trackingRepository가 null입니다");
            Toast.makeText(this, "시선 추적 시스템이 초기화되지 않았습니다", Toast.LENGTH_SHORT).show();
            return;
        }

        if (trackingRepository.getTracker() == null) {
            Log.e(TAG, "GazeTracker가 null입니다");
            Toast.makeText(this, "시선 추적기가 초기화되지 않았습니다", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isCalibrating) {
            Log.w(TAG, "이미 캘리브레이션 진행 중입니다");
            Toast.makeText(this, "이미 캘리브레이션이 진행 중입니다", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "캘리브레이션 시작 시도");

        // UI 업데이트 (메인 스레드에서)
        handler.post(() -> {
            isCalibrating = true;
            overlayCursorView.setVisibility(View.INVISIBLE);

            // 캘리브레이션 시작
            boolean ok = trackingRepository.getTracker().startCalibration(CalibrationModeType.DEFAULT);
            Log.d(TAG, "GazeTracker.startCalibration() 결과: " + ok);

            if (!ok) {
                Log.e(TAG, "캘리브레이션 시작 실패");
                isCalibrating = false;
                overlayCursorView.setVisibility(View.VISIBLE);
                Toast.makeText(GazeTrackingService.this, "캘리브레이션 시작 실패", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "캘리브레이션 시작 성공");
                Toast.makeText(GazeTrackingService.this, "캘리브레이션을 시작합니다", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * MainActivity에서 캘리브레이션을 실행할 수 있도록 하는 메서드
     */
    public static void triggerMainActivityCalibration() {
        if (MainActivity.getInstance() != null) {
            MainActivity.getInstance().triggerCalibrationFromService();
        } else {
            Log.w(TAG, "MainActivity 인스턴스를 찾을 수 없습니다");
        }
    }

    /**
     * 현재 서비스 인스턴스를 반환
     */
    public static GazeTrackingService getInstance() {
        return instance;
    }

    /**
     * 사용자 설정을 새로고침하는 메서드
     */
    public void refreshSettings() {
        userSettings = settingsRepository.getUserSettings();
        clickDetector = new ClickDetector(userSettings);
        edgeScrollDetector = new EdgeScrollDetector(userSettings, this);
        Log.d(TAG, "사용자 설정이 새로고침되었습니다");
    }

    // 추가된 메소드: 접근성 서비스 활성화 여부 확인
    private void checkAccessibilityService() {
        if (MyAccessibilityService.getInstance() == null) {
            // 메인 액티비티가 없을 때도 작동하도록 Toast로 간단하게 알림
            Toast.makeText(this, "접근성 서비스가 활성화되지 않았습니다. 설정에서 활성화해주세요.", Toast.LENGTH_LONG).show();
            Log.w(TAG, "접근성 서비스가 활성화되지 않음. 기능 제한됨.");
        } else {
            Toast.makeText(this, "시선 추적 서비스가 시작되었습니다.", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "접근성 서비스 활성화됨. 모든 기능 사용 가능.");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "서비스 시작됨");
        return START_STICKY; // 시스템에 의해 종료되어도 자동 재시작
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "서비스 종료됨");

        // 뷰 제거
        if (overlayCursorView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayCursorView);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "커서 뷰 제거 중 오류: " + e.getMessage());
            }
        }
        if (calibrationViewer != null && windowManager != null) {
            try {
                windowManager.removeView(calibrationViewer);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "캘리브레이션 뷰 제거 중 오류: " + e.getMessage());
            }
        }

        // 시선 추적 중지
        if (trackingRepository != null && trackingRepository.getTracker() != null) {
            trackingRepository.stopTracking();
        }

        // 인스턴스 정리
        instance = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // 바인드 서비스가 아님
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "시선 추적 서비스 채널",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("시선 추적 서비스가 백그라운드에서 실행 중입니다");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}