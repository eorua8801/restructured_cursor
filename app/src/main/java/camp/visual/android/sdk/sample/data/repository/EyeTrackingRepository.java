package camp.visual.android.sdk.sample.data.repository;

import android.content.Context;

import camp.visual.eyedid.gazetracker.GazeTracker;
import camp.visual.eyedid.gazetracker.callback.CalibrationCallback;
import camp.visual.eyedid.gazetracker.callback.InitializationCallback;
import camp.visual.eyedid.gazetracker.callback.StatusCallback;
import camp.visual.eyedid.gazetracker.callback.TrackingCallback;
import camp.visual.eyedid.gazetracker.constant.CalibrationModeType;

public interface EyeTrackingRepository {
    void initialize(Context context, InitializationCallback callback);
    void startTracking();
    void stopTracking();
    void startCalibration(CalibrationModeType type);
    void setTrackingCallback(TrackingCallback callback);
    void setCalibrationCallback(CalibrationCallback callback);
    void setStatusCallback(StatusCallback callback);
    GazeTracker getTracker();
}