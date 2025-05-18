package camp.visual.android.sdk.sample.data.repository;

import android.content.Context;
import android.util.Log;

import camp.visual.eyedid.gazetracker.GazeTracker;
import camp.visual.eyedid.gazetracker.callback.CalibrationCallback;
import camp.visual.eyedid.gazetracker.callback.InitializationCallback;
import camp.visual.eyedid.gazetracker.callback.StatusCallback;
import camp.visual.eyedid.gazetracker.callback.TrackingCallback;
import camp.visual.eyedid.gazetracker.constant.CalibrationModeType;
import camp.visual.eyedid.gazetracker.constant.GazeTrackerOptions;

public class EyedidTrackingRepository implements EyeTrackingRepository {
    private static final String TAG = "EyedidTracking";
    private static final String LICENSE_KEY = "dev_plnp4o1ya7d0tif2rmgko169l1z4jnali2q4f63f";

    private GazeTracker gazeTracker;

    @Override
    public void initialize(Context context, InitializationCallback callback) {
        GazeTrackerOptions options = new GazeTrackerOptions.Builder().build();
        GazeTracker.initGazeTracker(context, LICENSE_KEY, (tracker, error) -> {
            if (tracker != null) {
                gazeTracker = tracker;
                Log.d(TAG, "시선 추적 SDK 초기화 성공");
            } else {
                Log.e(TAG, "시선 추적 SDK 초기화 실패: " + error);
            }
            callback.onInitialized(tracker, error);
        }, options);
    }

    @Override
    public void startTracking() {
        if (gazeTracker != null) {
            gazeTracker.startTracking();
        }
    }

    @Override
    public void stopTracking() {
        if (gazeTracker != null) {
            gazeTracker.stopTracking();
        }
    }

    @Override
    public void startCalibration(CalibrationModeType type) {
        if (gazeTracker != null) {
            gazeTracker.startCalibration(type);
        }
    }

    @Override
    public void setTrackingCallback(TrackingCallback callback) {
        if (gazeTracker != null) {
            gazeTracker.setTrackingCallback(callback);
        }
    }

    @Override
    public void setCalibrationCallback(CalibrationCallback callback) {
        if (gazeTracker != null) {
            gazeTracker.setCalibrationCallback(callback);
        }
    }

    @Override
    public void setStatusCallback(StatusCallback callback) {
        if (gazeTracker != null) {
            gazeTracker.setStatusCallback(callback);
        }
    }

    @Override
    public GazeTracker getTracker() {
        return gazeTracker;
    }
}