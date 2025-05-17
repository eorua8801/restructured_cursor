package camp.visual.android.sdk.sample.domain.interaction;

import android.util.Log;

import camp.visual.android.sdk.sample.domain.model.GazeData;
import camp.visual.android.sdk.sample.domain.model.UserSettings;

public class ClickDetector {
    private static final String TAG = "ClickDetector";

    private final UserSettings settings;

    private float fixationCenterX = -1;
    private float fixationCenterY = -1;
    private long fixationStartTime = 0;
    private boolean isFixating = false;

    public ClickDetector(UserSettings settings) {
        this.settings = settings;
    }

    public float getProgress() {
        if (!isFixating || fixationStartTime == 0) {
            return 0f;
        }

        long duration = System.currentTimeMillis() - fixationStartTime;
        return Math.min((float) duration / settings.getFixationDurationMs(), 1.0f);
    }

    public boolean update(float x, float y) {
        if (!settings.isClickEnabled()) {
            return false;
        }

        // 관심 영역(AOI) 내에 있는지 확인
        boolean insideAOI = (fixationCenterX >= 0 && fixationCenterY >= 0) &&
                (Math.abs(x - fixationCenterX) < settings.getAoiRadius()) &&
                (Math.abs(y - fixationCenterY) < settings.getAoiRadius());

        // 처음 시작이거나 AOI 밖으로 나갔을 경우
        if (fixationCenterX < 0 || fixationCenterY < 0 || !insideAOI) {
            fixationCenterX = x;
            fixationCenterY = y;
            fixationStartTime = System.currentTimeMillis();
            isFixating = true;
            return false;
        }

        // AOI 내에서 계속 응시 중인 경우
        long duration = System.currentTimeMillis() - fixationStartTime;

        // 응시 시간이 충분하면 클릭 신호 반환
        if (duration >= settings.getFixationDurationMs()) {
            Log.d(TAG, "클릭 감지: (" + x + ", " + y + ")");
            reset(); // 클릭 후 상태 리셋
            return true;
        }

        return false;
    }

    public float getFixationX() {
        return fixationCenterX;
    }

    public float getFixationY() {
        return fixationCenterY;
    }

    public void reset() {
        fixationCenterX = -1;
        fixationCenterY = -1;
        fixationStartTime = 0;
        isFixating = false;
    }
}