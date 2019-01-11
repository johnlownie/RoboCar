package ca.jetsphere.robocar.devices;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;

import org.opencv.android.JavaCameraView;

import java.util.List;

public class MyJavaCameraView extends JavaCameraView
{
    private static String TAG = "MyJavaCameraView";

    /**
     *
     */
    public MyJavaCameraView (Context context, int cameraId) {
        super(context, cameraId);
    }

    /**
     *
     */
    public MyJavaCameraView (Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     *
     */
    public void turnOffTheFlash() {
        if (mCamera == null) {
            Log.i(TAG, "Camera is NULL");
            return;
        }

        Camera.Parameters params = mCamera.getParameters();
        params.setFlashMode(params.FLASH_MODE_OFF);
        mCamera.setParameters(params);
    }

    /**
     *
     */
    public void turnOnTheFlash() {
        if (mCamera == null) {
            try {
                mCamera = android.hardware.Camera.open();
            } catch (Exception e) {
                Log.i(TAG, "Camera is NULL");
                return;
            }
        }

        Camera.Parameters params = mCamera.getParameters();
        List<String> FlashModes = params.getSupportedFlashModes();

        params.setFlashMode(params.FLASH_MODE_TORCH);
        mCamera.setParameters(params);
        mCamera.startPreview();
    }
}
