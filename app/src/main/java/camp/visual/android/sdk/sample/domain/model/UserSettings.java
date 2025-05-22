package camp.visual.android.sdk.sample.domain.model;

public class UserSettings {
    // 고정 클릭 설정
    private final float fixationDurationMs;
    private final float aoiRadius;

    // 스크롤 설정
    private final boolean scrollEnabled;
    private final float edgeMarginRatio;
    private final long edgeTriggerMs;
    private final int continuousScrollCount;

    // 기능 활성화 설정
    private final boolean clickEnabled;
    private final boolean edgeScrollEnabled;
    private final boolean blinkDetectionEnabled;

    // 캘리브레이션 설정
    private final boolean autoOnePointCalibrationEnabled;

    // 커서 위치 오프셋 설정 (통합 오프셋)
    private final float cursorOffsetX;
    private final float cursorOffsetY;

    private UserSettings(Builder builder) {
        this.fixationDurationMs = builder.fixationDurationMs;
        this.aoiRadius = builder.aoiRadius;
        this.scrollEnabled = builder.scrollEnabled;
        this.edgeMarginRatio = builder.edgeMarginRatio;
        this.edgeTriggerMs = builder.edgeTriggerMs;
        this.continuousScrollCount = builder.continuousScrollCount;
        this.clickEnabled = builder.clickEnabled;
        this.edgeScrollEnabled = builder.edgeScrollEnabled;
        this.blinkDetectionEnabled = builder.blinkDetectionEnabled;
        this.autoOnePointCalibrationEnabled = builder.autoOnePointCalibrationEnabled;
        this.cursorOffsetX = builder.cursorOffsetX;
        this.cursorOffsetY = builder.cursorOffsetY;
    }

    // Getters
    public float getFixationDurationMs() {
        return fixationDurationMs;
    }

    public float getAoiRadius() {
        return aoiRadius;
    }

    public boolean isScrollEnabled() {
        return scrollEnabled;
    }

    public float getEdgeMarginRatio() {
        return edgeMarginRatio;
    }

    public long getEdgeTriggerMs() {
        return edgeTriggerMs;
    }

    public int getContinuousScrollCount() {
        return continuousScrollCount;
    }

    public boolean isClickEnabled() {
        return clickEnabled;
    }

    public boolean isEdgeScrollEnabled() {
        return edgeScrollEnabled;
    }

    public boolean isBlinkDetectionEnabled() {
        return blinkDetectionEnabled;
    }

    public boolean isAutoOnePointCalibrationEnabled() {
        return autoOnePointCalibrationEnabled;
    }

    public float getCursorOffsetX() {
        return cursorOffsetX;
    }

    public float getCursorOffsetY() {
        return cursorOffsetY;
    }

    // Builder 패턴 구현
    public static class Builder {
        // 기본값 설정
        private float fixationDurationMs = 1000f;
        private float aoiRadius = 40f;
        private boolean scrollEnabled = true;
        private float edgeMarginRatio = 0.01f;
        private long edgeTriggerMs = 3000;
        private int continuousScrollCount = 2;
        private boolean clickEnabled = true;
        private boolean edgeScrollEnabled = true;
        private boolean blinkDetectionEnabled = false;
        private boolean autoOnePointCalibrationEnabled = true; // 기본값 true
        private float cursorOffsetX = 0f; // 기본값 0 (오프셋 없음)
        private float cursorOffsetY = 0f; // 기본값 0 (오프셋 없음)

        public Builder() {}

        public Builder fixationDurationMs(float val) {
            fixationDurationMs = val;
            return this;
        }

        public Builder aoiRadius(float val) {
            aoiRadius = val;
            return this;
        }

        public Builder scrollEnabled(boolean val) {
            scrollEnabled = val;
            return this;
        }

        public Builder edgeMarginRatio(float val) {
            edgeMarginRatio = val;
            return this;
        }

        public Builder edgeTriggerMs(long val) {
            edgeTriggerMs = val;
            return this;
        }

        public Builder continuousScrollCount(int val) {
            continuousScrollCount = val;
            return this;
        }

        public Builder clickEnabled(boolean val) {
            clickEnabled = val;
            return this;
        }

        public Builder edgeScrollEnabled(boolean val) {
            edgeScrollEnabled = val;
            return this;
        }

        public Builder blinkDetectionEnabled(boolean val) {
            blinkDetectionEnabled = val;
            return this;
        }

        public Builder autoOnePointCalibrationEnabled(boolean val) {
            autoOnePointCalibrationEnabled = val;
            return this;
        }

        public Builder cursorOffsetX(float val) {
            cursorOffsetX = val;
            return this;
        }

        public Builder cursorOffsetY(float val) {
            cursorOffsetY = val;
            return this;
        }

        public UserSettings build() {
            return new UserSettings(this);
        }
    }
}