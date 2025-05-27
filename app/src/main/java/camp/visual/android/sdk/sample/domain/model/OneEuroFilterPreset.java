package camp.visual.android.sdk.sample.domain.model;

public enum OneEuroFilterPreset {
    STABILITY("안정성 우선", "매우 부드럽고 안정적, 떨림 최소화", 30.0, 0.5, 0.0, 1.0),
    BALANCED("균형", "안정성과 반응성의 균형 (권장)", 30.0, 1.0, 0.0, 1.0),
    RESPONSIVE("반응성 우선", "빠른 반응, 약간의 떨림 허용", 30.0, 1.5, 0.1, 1.0),
    HIGH_RESPONSIVE("고반응성", "매우 빠른 반응, 떨림 더 많이 허용", 30.0, 2.0, 0.2, 1.0),
    CUSTOM("커스텀", "사용자 직접 설정", 30.0, 1.0, 0.0, 1.0);

    private final String displayName;
    private final String description;
    private final double freq;
    private final double minCutoff;
    private final double beta;
    private final double dCutoff;

    OneEuroFilterPreset(String displayName, String description, double freq, double minCutoff, double beta, double dCutoff) {
        this.displayName = displayName;
        this.description = description;
        this.freq = freq;
        this.minCutoff = minCutoff;
        this.beta = beta;
        this.dCutoff = dCutoff;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public double getFreq() {
        return freq;
    }

    public double getMinCutoff() {
        return minCutoff;
    }

    public double getBeta() {
        return beta;
    }

    public double getDCutoff() {
        return dCutoff;
    }

    public static OneEuroFilterPreset fromName(String name) {
        for (OneEuroFilterPreset preset : values()) {
            if (preset.name().equals(name)) {
                return preset;
            }
        }
        return BALANCED; // 기본값
    }
}