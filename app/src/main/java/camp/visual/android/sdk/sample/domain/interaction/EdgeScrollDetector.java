package camp.visual.android.sdk.sample.domain.interaction;

import android.content.Context;
import android.os.Vibrator;
import android.util.Log;

import camp.visual.android.sdk.sample.domain.model.UserSettings;

public class EdgeScrollDetector {
    private static final String TAG = "EdgeScrollDetector";

    private final UserSettings settings;
    private final Vibrator vibrator;

    public enum Edge {
        TOP, BOTTOM, NONE
    }

    public enum ScrollAction {
        SCROLL_UP, SCROLL_DOWN, NONE
    }

    // 상단 응시 관련 변수
    private int topGazeConsecutiveFrames = 0;
    private long topGazeStartTime = 0;
    private boolean topGazeVibrated1s = false;
    private boolean topGazeVibrated2s = false;
    private boolean topGazeTriggered = false;

    // 하단 응시 관련 변수
    private int bottomGazeConsecutiveFrames = 0;
    private long bottomGazeStartTime = 0;
    private boolean bottomGazeVibrated1s = false;
    private boolean bottomGazeVibrated2s = false;
    private boolean bottomGazeTriggered = false;

    // 현재 감지된 엣지
    private Edge currentEdge = Edge.NONE;

    private static final int EDGE_THRESHOLD_FRAMES = 5; // 연속 5프레임 이상

    public EdgeScrollDetector(UserSettings settings, Context context) {
        this.settings = settings;
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public Edge update(float y, float screenHeight) {
        if (!settings.isEdgeScrollEnabled() || !settings.isScrollEnabled()) {
            return Edge.NONE;
        }

        float topMargin = screenHeight * settings.getEdgeMarginRatio();
        float bottomMargin = screenHeight * (1 - settings.getEdgeMarginRatio());

        boolean isInTopEdge = y < topMargin;
        boolean isInBottomEdge = y > bottomMargin;

        // 엣지 영역이 바뀌면 모든 상태 초기화
        if (isInTopEdge && currentEdge != Edge.TOP) {
            resetBottom();
            currentEdge = Edge.TOP;
        } else if (isInBottomEdge && currentEdge != Edge.BOTTOM) {
            resetTop();
            currentEdge = Edge.BOTTOM;
        } else if (!isInTopEdge && !isInBottomEdge) {
            resetAll();
            currentEdge = Edge.NONE;
            return Edge.NONE;
        }

        // 현재 감지된 엣지 반환
        return currentEdge;
    }

    public ScrollAction processTopEdge() {
        // 상단 연속 프레임 카운팅
        topGazeConsecutiveFrames++;

        if (topGazeConsecutiveFrames >= EDGE_THRESHOLD_FRAMES) {
            if (topGazeStartTime == 0) {
                topGazeStartTime = System.currentTimeMillis();
                topGazeVibrated1s = false;
                topGazeVibrated2s = false;
                topGazeTriggered = false;
                Log.d(TAG, "상단 응시 감지 - 타이머 시작");
                vibrator.vibrate(50); // 시작 알림 진동
                return ScrollAction.NONE;
            } else {
                long duration = System.currentTimeMillis() - topGazeStartTime;

                // 1초 경과 - 진동 추가
                if (duration > 1000 && !topGazeVibrated1s) {
                    vibrator.vibrate(100); // 1초 알림 진동
                    topGazeVibrated1s = true;
                    Log.d(TAG, "상단 응시 1초 경과");
                    return ScrollAction.NONE;
                }
                // 2초 경과 - 진동 추가
                else if (duration > 2000 && !topGazeVibrated2s) {
                    vibrator.vibrate(100); // 2초 알림 진동
                    topGazeVibrated2s = true;
                    Log.d(TAG, "상단 응시 2초 경과");
                    return ScrollAction.NONE;
                }

                // 설정된 시간(기본 3초) 이상 응시하면 스크롤 다운
                if (duration >= settings.getEdgeTriggerMs() && !topGazeTriggered) {
                    Log.d(TAG, "상단 응시 " + (settings.getEdgeTriggerMs()/1000) + "초 완료 - 하단 스크롤 실행");
                    topGazeTriggered = true;
                    vibrator.vibrate(300); // 스크롤 실행 알림 진동
                    return ScrollAction.SCROLL_DOWN;
                }
            }
        }

        return ScrollAction.NONE;
    }

    public ScrollAction processBottomEdge() {
        // 하단 연속 프레임 카운팅
        bottomGazeConsecutiveFrames++;

        if (bottomGazeConsecutiveFrames >= EDGE_THRESHOLD_FRAMES) {
            if (bottomGazeStartTime == 0) {
                bottomGazeStartTime = System.currentTimeMillis();
                bottomGazeVibrated1s = false;
                bottomGazeVibrated2s = false;
                bottomGazeTriggered = false;
                Log.d(TAG, "하단 응시 감지 - 타이머 시작");
                vibrator.vibrate(50); // 시작 알림 진동
                return ScrollAction.NONE;
            } else {
                long duration = System.currentTimeMillis() - bottomGazeStartTime;

                // 1초 경과 - 진동 추가
                if (duration > 1000 && !bottomGazeVibrated1s) {
                    vibrator.vibrate(100); // 1초 알림 진동
                    bottomGazeVibrated1s = true;
                    Log.d(TAG, "하단 응시 1초 경과");
                    return ScrollAction.NONE;
                }
                // 2초 경과 - 진동 추가
                else if (duration > 2000 && !bottomGazeVibrated2s) {
                    vibrator.vibrate(100); // 2초 알림 진동
                    bottomGazeVibrated2s = true;
                    Log.d(TAG, "하단 응시 2초 경과");
                    return ScrollAction.NONE;
                }

                // 설정된 시간(기본 3초) 이상 응시하면 스크롤 업
                if (duration >= settings.getEdgeTriggerMs() && !bottomGazeTriggered) {
                    Log.d(TAG, "하단 응시 " + (settings.getEdgeTriggerMs()/1000) + "초 완료 - 상단 스크롤 실행");
                    bottomGazeTriggered = true;
                    vibrator.vibrate(300); // 스크롤 실행 알림 진동
                    return ScrollAction.SCROLL_UP;
                }
            }
        }

        return ScrollAction.NONE;
    }

    public String getEdgeStateText() {
        if (currentEdge == Edge.TOP) {
            if (topGazeStartTime == 0) return "▲";

            long duration = System.currentTimeMillis() - topGazeStartTime;
            if (duration > 2000) return "②";
            if (duration > 1000) return "①";
            return "▲";
        } else if (currentEdge == Edge.BOTTOM) {
            if (bottomGazeStartTime == 0) return "▼";

            long duration = System.currentTimeMillis() - bottomGazeStartTime;
            if (duration > 2000) return "②";
            if (duration > 1000) return "①";
            return "▼";
        }

        return "●";
    }

    private void resetTop() {
        topGazeConsecutiveFrames = 0;
        topGazeStartTime = 0;
        topGazeVibrated1s = false;
        topGazeVibrated2s = false;
        topGazeTriggered = false;
    }

    private void resetBottom() {
        bottomGazeConsecutiveFrames = 0;
        bottomGazeStartTime = 0;
        bottomGazeVibrated1s = false;
        bottomGazeVibrated2s = false;
        bottomGazeTriggered = false;
    }

    public void resetAll() {
        resetTop();
        resetBottom();
        currentEdge = Edge.NONE;
    }

    public boolean isActive() {
        return currentEdge != Edge.NONE;
    }
}