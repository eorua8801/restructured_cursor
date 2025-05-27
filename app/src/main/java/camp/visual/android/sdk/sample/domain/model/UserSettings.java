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

    // OneEuroFilter 설정 - 프리셋 방식
    private final OneEuroFilterPreset oneEuroFilterPreset;
    private final double oneEuroFreq;
    private final double oneEuroMinCutoff;
    private final double oneEuroBeta;
    private final double oneEuroDCutoff;

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
        this.oneEuroFilterPreset = builder.oneEuroFilterPreset;

        // 프리셋이 커스텀인 경우 사용자 설정값 사용, 아니면 프리셋값 사용
        if (this.oneEuroFilterPreset == OneEuroFilterPreset.CUSTOM) {
            this.oneEuroFreq = builder.oneEuroFreq;
            this.oneEuroMinCutoff = builder.oneEuroMinCutoff;
            this.oneEuroBeta = builder.oneEuroBeta;
            this.oneEuroDCutoff = builder.oneEuroDCutoff;
        } else {
            this.oneEuroFreq = this.oneEuroFilterPreset.getFreq();
            this.oneEuroMinCutoff = this.oneEuroFilterPreset.getMinCutoff();
            this.oneEuroBeta = this.oneEuroFilterPreset.getBeta();
            this.oneEuroDCutoff = this.oneEuroFilterPreset.getDCutoff();
        }
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

    public OneEuroFilterPreset getOneEuroFilterPreset() {
        return oneEuroFilterPreset;
    }

    public double getOneEuroFreq() {
        return oneEuroFreq;
    }

    public double getOneEuroMinCutoff() {
        return oneEuroMinCutoff;
    }

    public double getOneEuroBeta() {
        return oneEuroBeta;
    }

    public double getOneEuroDCutoff() {
        return oneEuroDCutoff;
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
        private boolean autoOnePointCalibrationEnabled = true;
        private float cursorOffsetX = 0f;
        private float cursorOffsetY = 0f;

        // OneEuroFilter 기본값 - 균형 프리셋 (사진의 기본값과 일치)
        private OneEuroFilterPreset oneEuroFilterPreset = OneEuroFilterPreset.BALANCED;
        private double oneEuroFreq = 30.0;
        private double oneEuroMinCutoff = 1.0;
        private double oneEuroBeta = 0.007;  // 사진의 기본값으로 변경
        private double oneEuroDCutoff = 1.0;

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

        public Builder oneEuroFilterPreset(OneEuroFilterPreset val) {
            oneEuroFilterPreset = val;
            return this;
        }

        public Builder oneEuroFreq(double val) {
            oneEuroFreq = val;
            return this;
        }

        public Builder oneEuroMinCutoff(double val) {
            oneEuroMinCutoff = val;
            return this;
        }

        public Builder oneEuroBeta(double val) {
            oneEuroBeta = val;
            return this;
        }

        public Builder oneEuroDCutoff(double val) {
            oneEuroDCutoff = val;
            return this;
        }

        public UserSettings build() {
            return new UserSettings(this);
        }
    }
}