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

/*
    // 눈 깜빡임 감지용 변수
    private boolean lastBlinkState = false;
    private long blinkStartTime = 0;
    private long totalBlinkDuration = 0;
    private int blinkCount = 0;

    // 과도한 깜빡임 탐지 변수
    private static final int RAPID_BLINK_COUNT = 5; // 5회 이상이면 과도한 깜빡임으로 간주
    private static final long RAPID_BLINK_WINDOW_MS = 3000; // 3초 내
    private long[] recentBlinkTimes = new long[RAPID_BLINK_COUNT];
    private int recentBlinkIndex = 0;
*/
    @Override
    public void onCreate() {
        super.onCreate();

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
/*
                    // 눈 깜빡임 처리 - 직접 구현
                    if (userSettings.isBlinkDetectionEnabled()) {
                        // UserStatusInfo에서 눈 상태 확인하여 깜빡임 감지
                        // 일반적으로 0.3 이하는 눈을 감은 상태로 간주
                        boolean isEyeOpen = (userStatusInfo.leftOpenness > 0.3 && userStatusInfo.rightOpenness > 0.3);
                        boolean isBlinking = !isEyeOpen;

                        // 깜빡임 시작 감지
                        if (isBlinking && !lastBlinkState) {
                            blinkStartTime = System.currentTimeMillis();
                            lastBlinkState = true;
                            Log.d(TAG, "눈 깜빡임 시작 감지");
                        }
                        // 깜빡임 종료 감지
                        else if (!isBlinking && lastBlinkState) {
                            long endTime = System.currentTimeMillis();
                            long duration = endTime - blinkStartTime;
                            totalBlinkDuration += duration;
                            blinkCount++;
                            lastBlinkState = false;

                            // 최근 깜빡임 기록
                            recentBlinkTimes[recentBlinkIndex] = endTime;
                            recentBlinkIndex = (recentBlinkIndex + 1) % RAPID_BLINK_COUNT;

                            Log.d(TAG, "눈 깜빡임 종료: " + duration + "ms, 총 " + blinkCount + "회");

                            // 과도한 깜빡임 탐지
                            if (isRapidBlinking()) {
                                Log.w(TAG, "과도한 눈 깜빡임 감지! " + RAPID_BLINK_COUNT + "회 / "
                                        + (RAPID_BLINK_WINDOW_MS/1000) + "초");

                                // 추가 조치가 필요한 경우 여기에 구현
                                vibrator.vibrate(new long[]{0, 100, 100, 100}, -1); // 특별한 진동 패턴
                            }
                        }
                    }
*/
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
/*
    // 과도한 깜빡임 탐지 메소드
    private boolean isRapidBlinking() {
        if (blinkCount < RAPID_BLINK_COUNT) {
            return false;
        }

        long latestTime = recentBlinkTimes[(recentBlinkIndex + RAPID_BLINK_COUNT - 1) % RAPID_BLINK_COUNT];
        long earliestTime = recentBlinkTimes[recentBlinkIndex];

        // 배열이 다 채워지지 않았을 때 초기값(0)인 경우 처리
        if (earliestTime == 0) {
            return false;
        }

        return (latestTime - earliestTime) < RAPID_BLINK_WINDOW_MS;
    }
*/
    private void resetAll() {
        edgeScrollDetector.resetAll();
        clickDetector.reset();
/*
        // 깜빡임 감지 변수 초기화
        lastBlinkState = false;
        blinkStartTime = 0;
*/
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
        Log.d(TAG, "Click at (" + x + ", " + y + ")");
        vibrator.vibrate(100);
        MyAccessibilityService.performClickAt(x, y);
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
        skipProgress = true;
        calibrationViewer.setPointAnimationPower(0);
        calibrationViewer.setEnableText(true);
        calibrationViewer.nextPointColor();
        calibrationViewer.setPointPosition(x, y);

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

    public void triggerCalibration() {
        if (trackingRepository.getTracker() != null) {
            isCalibrating = true;
            overlayCursorView.setVisibility(View.INVISIBLE);
            boolean ok = trackingRepository.getTracker().startCalibration(CalibrationModeType.DEFAULT);
            if (!ok) {
                isCalibrating = false;
                overlayCursorView.setVisibility(View.VISIBLE);
                Toast.makeText(this, "캘리브레이션 시작 실패", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 추가된 메소드: 접근성 서비스 활성화 여부 확인
    private void checkAccessibilityService() {
        if (MyAccessibilityService.getInstance() == null) {
            // 메인 액티비티가 없을 때도 작동하도록 Toast로 간단하게 알림
            Toast.makeText(this, "접근성 서비스가 활성화되지 않았습니다. 설정에서 활성화해주세요.", Toast.LENGTH_LONG).show();
            Log.d(TAG, "접근성 서비스가 활성화되지 않음. 기능 제한됨.");
        } else {
            Toast.makeText(this, "시선 추적 서비스가 시작되었습니다.", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "접근성 서비스 활성화됨. 모든 기능 사용 가능.");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayCursorView != null) {
            windowManager.removeView(overlayCursorView);
        }
        if (calibrationViewer != null) {
            windowManager.removeView(calibrationViewer);
        }
        if (trackingRepository.getTracker() != null) {
            trackingRepository.stopTracking();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "시선 추적 서비스 채널",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
}