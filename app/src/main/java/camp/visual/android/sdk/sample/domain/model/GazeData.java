package camp.visual.android.sdk.sample.domain.model;

import camp.visual.eyedid.gazetracker.metrics.state.TrackingState;

public class GazeData {
    private final float x;
    private final float y;
    private final long timestamp;
    private final TrackingState state;

    public GazeData(float x, float y, long timestamp, TrackingState state) {
        this.x = x;
        this.y = y;
        this.timestamp = timestamp;
        this.state = state;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public TrackingState getState() {
        return state;
    }

    public boolean isValid() {
        return state == TrackingState.SUCCESS;
    }
}