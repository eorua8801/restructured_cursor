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

    // 1포인트 캘리브레이션 및 통합 오프셋 관련 변수
    private boolean isOnePointCalibration = false;
    private boolean offsetApplied = false;

    // 오프셋 계산 관련 변수들
    private boolean waitingForOffsetCalculation = false;
    private float targetX = 0f;
    private float targetY = 0f;
    private int validGazeCount = 0;
    private float sumGazeX = 0f;
    private float sumGazeY = 0f;

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

        // 자동 1포인트 캘리브레이션 실행 (설정 확인 후)
        handler.postDelayed(() -> {
            if (userSettings.isAutoOnePointCalibrationEnabled() &&
                    trackingRepository != null &&
                    trackingRepository.getTracker() != null &&
                    !isCalibrating) {
                startOnePointCalibrationWithOffset();
            }
        }, 3000);
    }

    private void initRepositories() {
        trackingRepository = new EyedidTrackingRepository();
        settingsRepository = new SharedPrefsSettingsRepository(this);
        userSettings = settingsRepository.getUserSettings();
    }

    private void initDetectors() {
        clickDetector = new ClickDetector(userSettings);
        edgeScrollDetector = new EdgeScrollDetector(userSettings, this);

        // OneEuroFilterManager를 프리셋 또는 사용자 설정값으로 초기화 (float 캐스팅)
        oneEuroFilterManager = new OneEuroFilterManager(
                2,  // count (x, y 좌표)
                (float) userSettings.getOneEuroFreq(),
                (float) userSettings.getOneEuroMinCutoff(),
                (float) userSettings.getOneEuroBeta(),
                (float) userSettings.getOneEuroDCutoff()
        );

        // 기존에 저장된 커서 오프셋이 있으면 바로 적용
        if (userSettings.getCursorOffsetX() != 0f || userSettings.getCursorOffsetY() != 0f) {
            offsetApplied = true;
            Log.d(TAG, "기존 커서 오프셋 적용: X=" + userSettings.getCursorOffsetX() + ", Y=" + userSettings.getCursorOffsetY());
        }

        Log.d(TAG, "OneEuroFilter 초기화 - 프리셋: " + userSettings.getOneEuroFilterPreset().getDisplayName());
        Log.d(TAG, "OneEuroFilter 파라미터 - freq: " + userSettings.getOneEuroFreq() +
                ", minCutoff: " + userSettings.getOneEuroMinCutoff() +
                ", beta: " + userSettings.getOneEuroBeta() +
                ", dCutoff: " + userSettings.getOneEuroDCutoff());
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

    // 1포인트 캘리브레이션 + 오프셋 계산 메서드 추가
    public void startOnePointCalibrationWithOffset() {
        Log.d(TAG, "1포인트 캘리브레이션 + 통합 오프셋 정렬 시작");

        if (trackingRepository == null || trackingRepository.getTracker() == null) {
            Log.e(TAG, "trackingRepository 또는 tracker가 null입니다");
            return;
        }

        if (isCalibrating) {
            Log.w(TAG, "이미 캘리브레이션 진행 중입니다");
            return;
        }

        isCalibrating = true;
        isOnePointCalibration = true;
        offsetApplied = false;

        overlayCursorView.setVisibility(View.INVISIBLE);
        calibrationViewer.setVisibility(View.VISIBLE);

        // 화면 중앙 계산
        DisplayMetrics dm = getResources().getDisplayMetrics();
        targetX = dm.widthPixels / 2f;
        targetY = dm.heightPixels / 2f;

        // 안내 메시지
        Toast.makeText(this, "잠시 후 나타나는 점을 응시해주세요", Toast.LENGTH_SHORT).show();

        // 1초 후 캘리브레이션 시작 (수동 포인트 표시 제거)
        handler.postDelayed(() -> {
            if (trackingRepository.getTracker() != null) {
                boolean ok = trackingRepository.getTracker().startCalibration(CalibrationModeType.ONE_POINT);
                if (!ok) {
                    resetCalibrationState();
                    Toast.makeText(GazeTrackingService.this, "1포인트 캘리브레이션 시작 실패", Toast.LENGTH_SHORT).show();
                } else {
                    Log.d(TAG, "1포인트 캘리브레이션 시작 성공");
                }
            }
        }, 1000);
    }

    // 통합 오프셋 계산 시작 메서드
    private void calculateIntegratedOffset() {
        waitingForOffsetCalculation = true;
        validGazeCount = 0;
        sumGazeX = 0f;
        sumGazeY = 0f;

        Log.d(TAG, "통합 오프셋 계산 시작 - 목표 위치: (" + targetX + ", " + targetY + ")");
        Toast.makeText(this, "시선을 보정 중입니다...", Toast.LENGTH_SHORT).show();

        // 5초 후에도 오프셋이 계산되지 않으면 강제 진행
        handler.postDelayed(() -> {
            if (waitingForOffsetCalculation) {
                waitingForOffsetCalculation = false;
                offsetApplied = true;
                overlayCursorView.setVisibility(View.VISIBLE);
                Log.w(TAG, "오프셋 계산 타임아웃 - 기존 설정 유지");
                Toast.makeText(GazeTrackingService.this, "시선 보정이 완료되었습니다", Toast.LENGTH_SHORT).show();
            }
        }, 5000);
    }

    // 상태 초기화 메서드
    private void resetCalibrationState() {
        isCalibrating = false;
        isOnePointCalibration = false;
        waitingForOffsetCalculation = false;
        calibrationViewer.setVisibility(View.INVISIBLE);
        overlayCursorView.setVisibility(View.VISIBLE);
    }

    private final TrackingCallback trackingCallback = new TrackingCallback() {
        @Override
        public void onMetrics(long timestamp, GazeInfo gazeInfo, FaceInfo faceInfo, BlinkInfo blinkInfo, UserStatusInfo userStatusInfo) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            float screenWidth = dm.widthPixels;
            float screenHeight = dm.heightPixels;

            // 시선 추적 성공 시
            if (gazeInfo.trackingState == TrackingState.SUCCESS) {
                // 통합 오프셋 계산 대기 중이라면
                if (waitingForOffsetCalculation) {
                    // 필터링 없이 원시 데이터 수집 (평균 계산용)
                    sumGazeX += gazeInfo.x;
                    sumGazeY += gazeInfo.y;
                    validGazeCount++;

                    // 10개 샘플 수집 후 평균 계산
                    if (validGazeCount >= 10) {
                        float avgGazeX = sumGazeX / validGazeCount;
                        float avgGazeY = sumGazeY / validGazeCount;

                        // 새로운 자동 오프셋 계산 (목표 위치 - 실제 시선 위치)
                        float newAutoOffsetX = targetX - avgGazeX;
                        float newAutoOffsetY = targetY - avgGazeY;

                        // 기존 사용자 오프셋과 새로운 자동 오프셋을 통합
                        float integratedOffsetX = userSettings.getCursorOffsetX() + newAutoOffsetX;
                        float integratedOffsetY = userSettings.getCursorOffsetY() + newAutoOffsetY;

                        // 오프셋 유효성 검증 (화면 크기의 30% 이내)
                        float maxOffset = Math.min(screenWidth, screenHeight) * 0.3f;

                        if (Math.abs(integratedOffsetX) <= maxOffset &&
                                Math.abs(integratedOffsetY) <= maxOffset) {

                            // 통합 오프셋을 설정에 저장
                            if (settingsRepository instanceof SharedPrefsSettingsRepository) {
                                ((SharedPrefsSettingsRepository) settingsRepository)
                                        .saveIntegratedCursorOffset(integratedOffsetX, integratedOffsetY);
                            }

                            // 설정 새로고침하여 통합 오프셋 적용
                            refreshSettings();
                            offsetApplied = true;

                            Log.d(TAG, "통합 오프셋 적용 완료: X=" + integratedOffsetX + ", Y=" + integratedOffsetY);
                            Log.d(TAG, "기존 사용자 오프셋: X=" + userSettings.getCursorOffsetX() + ", Y=" + userSettings.getCursorOffsetY());
                            Log.d(TAG, "새 자동 오프셋: X=" + newAutoOffsetX + ", Y=" + newAutoOffsetY);

                            Toast.makeText(GazeTrackingService.this, "시선 보정이 완료되었습니다", Toast.LENGTH_SHORT).show();
                        } else {
                            // 오프셋이 너무 크면 기존 설정 유지
                            offsetApplied = true;
                            Log.w(TAG, "계산된 오프셋이 너무 커서 기존 설정 유지");
                            Toast.makeText(GazeTrackingService.this, "시선 보정이 완료되었습니다", Toast.LENGTH_SHORT).show();
                        }

                        waitingForOffsetCalculation = false;
                        overlayCursorView.setVisibility(View.VISIBLE);
                    }
                    return; // 오프셋 계산 중에는 다른 처리 안함
                }

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

                // 통합 오프셋 적용 (사용자 설정에서 로드)
                if (offsetApplied) {
                    filteredX += userSettings.getCursorOffsetX();
                    filteredY += userSettings.getCursorOffsetY();
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
        Log.d(TAG, "클릭 실행 (커서 위치): (" + x + ", " + y + ")");

        // 🎯 커서가 표시된 위치에서 정확히 클릭하도록 함
        // 커서 위치는 이미 모든 오프셋이 적용된 상태
        float cursorX = x;
        float cursorY = y;

        // 화면 정보 수집
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int statusBarHeight = getStatusBarHeight();
        int navigationBarHeight = getNavigationBarHeight();

        Log.d(TAG, "앱 영역: " + dm.widthPixels + "x" + dm.heightPixels);
        Log.d(TAG, "상태바: " + statusBarHeight + "px, 네비게이션바: " + navigationBarHeight + "px");
        Log.d(TAG, "커서 위치 (오프셋 적용됨): (" + cursorX + ", " + cursorY + ")");

        // 커서는 앱 영역 기준이므로 접근성 서비스용으로 상태바 높이 추가
        float adjustedX = cursorX;
        float adjustedY = cursorY + statusBarHeight;

        Log.d(TAG, "클릭 실행 (최종 위치): (" + adjustedX + ", " + adjustedY + ")");

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
            if (isOnePointCalibration) {
                // 1포인트 캘리브레이션 완료
                hideCalibrationView();
                isCalibrating = false;
                isOnePointCalibration = false;

                // 통합 오프셋 계산 시작
                calculateIntegratedOffset();
                Log.d(TAG, "1포인트 캘리브레이션 완료 - 통합 오프셋 계산 시작");
            } else {
                // 기존 풀 캘리브레이션 완료
                hideCalibrationView();
                isCalibrating = false;
                Toast.makeText(GazeTrackingService.this, "정밀 캘리브레이션 완료", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onCalibrationCanceled(double[] calibrationData) {
            resetCalibrationState();
            Toast.makeText(GazeTrackingService.this, "캘리브레이션 취소됨", Toast.LENGTH_SHORT).show();
        }
    };

    private void showCalibrationPointView(final float x, final float y) {
        Log.d(TAG, "캘리브레이션 포인트 (SDK 좌표): (" + x + ", " + y + ")");

        // 캘리브레이션 포인트는 오버레이에 표시되므로
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
     * 서비스에서 5포인트 캘리브레이션을 트리거하는 메서드
     */
    public void triggerCalibration() {
        Log.d(TAG, "5포인트 캘리브레이션 트리거 요청됨");

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

        Log.d(TAG, "5포인트 캘리브레이션 시작 시도");

        // UI 업데이트 (메인 스레드에서)
        handler.post(() -> {
            isCalibrating = true;
            isOnePointCalibration = false; // 5포인트 캘리브레이션임을 명시
            overlayCursorView.setVisibility(View.INVISIBLE);

            // 캘리브레이션 시작
            boolean ok = trackingRepository.getTracker().startCalibration(CalibrationModeType.DEFAULT);
            Log.d(TAG, "GazeTracker.startCalibration() 결과: " + ok);

            if (!ok) {
                Log.e(TAG, "5포인트 캘리브레이션 시작 실패");
                resetCalibrationState();
                Toast.makeText(GazeTrackingService.this, "캘리브레이션 시작 실패", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "5포인트 캘리브레이션 시작 성공");
                Toast.makeText(GazeTrackingService.this, "정밀 캘리브레이션을 시작합니다", Toast.LENGTH_SHORT).show();
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

        // OneEuroFilterManager도 새 설정으로 재초기화 (float 캐스팅)
        oneEuroFilterManager = new OneEuroFilterManager(
                2,
                (float) userSettings.getOneEuroFreq(),
                (float) userSettings.getOneEuroMinCutoff(),
                (float) userSettings.getOneEuroBeta(),
                (float) userSettings.getOneEuroDCutoff()
        );

        Log.d(TAG, "사용자 설정이 새로고침되었습니다");
        Log.d(TAG, "현재 커서 오프셋: X=" + userSettings.getCursorOffsetX() + ", Y=" + userSettings.getCursorOffsetY());
        Log.d(TAG, "현재 OneEuroFilter 프리셋: " + userSettings.getOneEuroFilterPreset().getDisplayName());
        Log.d(TAG, "현재 OneEuroFilter 파라미터 - freq: " + userSettings.getOneEuroFreq() +
                ", minCutoff: " + userSettings.getOneEuroMinCutoff() +
                ", beta: " + userSettings.getOneEuroBeta() +
                ", dCutoff: " + userSettings.getOneEuroDCutoff());
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