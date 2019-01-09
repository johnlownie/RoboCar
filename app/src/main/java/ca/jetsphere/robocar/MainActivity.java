package ca.jetsphere.robocar;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.florescu.android.rangeseekbar.RangeSeekBar;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 *
 */
public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static String TAG = "MainActivity";
    private static final String FRAGMENT_DIALOG = "dialog";
    private static final int REQUEST_CAMERA_PERMISSION = 1;

    public enum State { STOP, FORWARD, REVERSE, TURN_LEFT, TURN_RIGHT, FORWARD_LEFT, FORWARD_RIGHT };

    private final String DEVICE_ADDRESS = "98:D3:31:FC:69:F7";
    private final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private OutputStream outputStream;
    private BluetoothDevice bluetoothDevice;
    private BluetoothSocket bluetoothSocket;

    private View mImgGroup, mHsvGroup;

    JavaCameraView javaCameraView;
    Mat mRgba, imgBlurred, imgThreshold, imgTemp;
    Mat erodeElement, dilateElement;
    int iImgType = 0;

    final int requestedWidth = 1024; // 1280;
    final int requestedHeight = 576; //  720;
    int actualWidth = 0;
    int actualHeight = 0;
    Point screenCenter, targetCenter;
    State direction = State.STOP;
    boolean isTracking = false;

    RangeSeekBar rsbHue, rsbSaturation,  rsbValue;

    BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case BaseLoaderCallback.SUCCESS : javaCameraView.enableView(); break;
                default : super.onManagerConnected(status);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_main);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.hide();

        tryStartCamera();

        rsbHue = findViewById(R.id.rsbHue);
        rsbHue.setRangeValues(0, 255);
        rsbHue.setSelectedMinValue(76);
        rsbHue.setSelectedMaxValue(96);

        rsbSaturation = findViewById(R.id.rsbSaturation);
        rsbSaturation.setRangeValues(0, 255);
        rsbSaturation.setSelectedMinValue(68);
        rsbSaturation.setSelectedMaxValue(255);

        rsbValue = findViewById(R.id.rsbValue);
        rsbValue.setRangeValues(0, 255);
        rsbValue.setSelectedMinValue(6);
        rsbValue.setSelectedMaxValue(255);

        mImgGroup = findViewById(R.id.imgGroup);
        mHsvGroup = findViewById(R.id.hsvGroup);

        final ToggleButton btnRawImage = findViewById(R.id.btnRawImage);
        final ToggleButton btnThresholdImage = findViewById(R.id.btnThresholdImage);
        final ToggleButton btnTrackImage = findViewById(R.id.btnTrackImage);
        final ToggleButton btnConnectImage = findViewById(R.id.btnConnectImage);

        btnRawImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnRawImage.setChecked(true);
                btnThresholdImage.setChecked(false);
                iImgType = 0;
            }
        });

        btnThresholdImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnRawImage.setChecked(false);
//                btnHSVImage.setChecked(false);
                btnThresholdImage.setChecked(true);

                mImgGroup.setVisibility(View.GONE);
                mHsvGroup.setVisibility(View.VISIBLE);
                rsbHue.setVisibility(View.VISIBLE);

                iImgType = 2;
            }
        });

        btnTrackImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isTracking = !isTracking;
                btnTrackImage.setChecked(isTracking);
            }
        });

        btnConnectImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "BT Connect clicked...");
                if ( bluetoothDevice == null || !bluetoothSocket.isConnected()) {
                    if ( bluetoothDevice == null) bluetoothDevice = BTInit();
                    btnConnectImage.setChecked(bluetoothDevice == null ? false : BTConnect());
                }
                else {
                    BTDisconnect();
                    btnConnectImage.setChecked(false);
                }
            }
        });

        final ToggleButton btnHue = findViewById(R.id.btnHue);
        final ToggleButton btnSaturation = findViewById(R.id.btnSaturation);
        final ToggleButton btnValue = findViewById(R.id.btnValue);
        btnHue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rsbHue.setVisibility(View.VISIBLE);
                rsbSaturation.setVisibility(View.INVISIBLE);
                rsbValue.setVisibility(View.INVISIBLE);

                btnHue.setChecked(true);
                btnSaturation.setChecked(false);
                btnValue.setChecked(false);
            }
        });

        btnSaturation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rsbHue.setVisibility(View.INVISIBLE);
                rsbSaturation.setVisibility(View.VISIBLE);
                rsbValue.setVisibility(View.INVISIBLE);

                btnHue.setChecked(false);
                btnSaturation.setChecked(true);
                btnValue.setChecked(false);
            }
        });

        btnValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rsbHue.setVisibility(View.INVISIBLE);
                rsbSaturation.setVisibility(View.INVISIBLE);
                rsbValue.setVisibility(View.VISIBLE);

                btnHue.setChecked(false);
                btnSaturation.setChecked(false);
                btnValue.setChecked(true);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (javaCameraView != null) {
            javaCameraView.disableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (javaCameraView != null) {
            javaCameraView.disableView();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
//        toggle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCv loaded successfully");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        } else {
            Log.i(TAG, "OpenCv failed to load");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        actualWidth = width; actualHeight = height;
        screenCenter = new Point(actualWidth / 2, actualHeight / 2);
        targetCenter = new Point(actualWidth / 2, actualHeight / 2);

        mRgba = new Mat(height, width, CvType.CV_8UC4);
        imgBlurred = new Mat(height, width, CvType.CV_8UC4);
        imgThreshold = new Mat(height, width, CvType.CV_8UC4);

        erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(8, 8));
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();

        if (isTracking ) trackObject(mRgba);

        switch (iImgType) {
            case 0 : return mRgba;
            case 2 : return imgThreshold;
            default: return mRgba;
        }
    }

    /**
     *
     */
    private void trackObject(Mat cameraFeed) {
        Imgproc.GaussianBlur(mRgba, imgBlurred, new Size(11, 11), 0);
        Imgproc.cvtColor(imgBlurred, imgBlurred, Imgproc.COLOR_BGR2HSV);
        Core.inRange(imgBlurred, new Scalar(((int) rsbHue.getSelectedMinValue()), ((int) rsbSaturation.getSelectedMinValue()), ((int)rsbValue.getSelectedMinValue())), new Scalar(((int) rsbHue.getSelectedMaxValue()), ((int) rsbSaturation.getSelectedMaxValue()), ((int) rsbValue.getSelectedMaxValue())), imgThreshold);

        Imgproc.erode(imgThreshold, imgThreshold, erodeElement, new Point(-1, -1), 2);
        Imgproc.dilate(imgThreshold, imgThreshold, dilateElement, new Point(-1, -1), 2);

        imgTemp = new Mat(); imgThreshold.copyTo(imgTemp);

        trackFilteredObject(mRgba, imgTemp);

        double dX = targetCenter.x - screenCenter.x;
        double dY = targetCenter.y - screenCenter.y;
        double slope = dY / dX;
        double degrees = Math.toDegrees(Math.atan(slope));

        String s = "dX: " + String.format("%.2f", dX) + ", dY: " + String.format("%.2f", dY) + ", S: " + String.format("%.2f", slope) + ", D: " + String.format("%.2f", degrees);

        Log.i(TAG, s);
        Imgproc.putText(mRgba, s, new Point(10, actualHeight - 10), Core.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255));

//        if (degrees > 0 && degrees < 30) direction = State.FORWARD_RIGHT; else
//        if (degrees > 0 && degrees < 30) direction = State.FORWARD_RIGHT; else
//        if (degrees > 0 && degrees < 30) direction = State.FORWARD_RIGHT; else
//        if (degrees > 0 && degrees < 30) direction = State.FORWARD_RIGHT; else
//        if (degrees > 0 && degrees < 30) direction = State.FORWARD_RIGHT; else

    }

    /**
     *
     */
    private void trackFilteredObject(Mat cameraFeed, Mat threshold) {
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(threshold, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        if (contours == null || contours.isEmpty()) {
            targetCenter.x = screenCenter.x;
            targetCenter.y = screenCenter.y;
            return;
        }

        int maxIndex = getMaxContour(contours);
        if (maxIndex == -1) return;

        MatOfPoint maxContour = contours.get(maxIndex);
        Point point = new Point(); float[] radius = new float[contours.size()];
        Imgproc.minEnclosingCircle(new MatOfPoint2f(maxContour.toArray()), point, radius);

        Moments moment = Imgproc.moments(maxContour);
        int x = (int) (moment.m10/moment.m00);
        int y = (int) (moment.m01/moment.m00);
        targetCenter = new Point(x, y);

        if (radius != null && radius.length > 0 && radius[0] > 10) {
            Imgproc.circle(cameraFeed, new Point(x, y), (int) radius[0], new Scalar(0, 255, 0), 2);
            Imgproc.circle(cameraFeed, targetCenter, 5, new Scalar(0, 0, 255), -1);
        }
        Imgproc.circle(cameraFeed, screenCenter, 5, new Scalar(0, 255, 255), 1);
    }

    /**
     *
     */
    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(parent.getActivity(),
                                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }

    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            new ConfirmationDialog().show(this.getFragmentManager(), FRAGMENT_DIALOG);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void tryStartCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }

        javaCameraView = findViewById(R.id.java_camera_view);
        javaCameraView.setMaxFrameSize(requestedWidth, requestedHeight);
        javaCameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        javaCameraView.setVisibility(SurfaceView.VISIBLE);
        javaCameraView.setCvCameraViewListener(this);
        javaCameraView.enableFpsMeter();

        javaCameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });
    }

    private void toggle() {
        if (mImgGroup.getVisibility() == View.VISIBLE) {
            mImgGroup.setVisibility(View.GONE);
        } else if (mHsvGroup.getVisibility() == View.VISIBLE) {
            mHsvGroup.setVisibility(View.GONE);
            mImgGroup.setVisibility(View.VISIBLE);
            rsbHue.setVisibility(View.INVISIBLE);
            rsbSaturation.setVisibility(View.INVISIBLE);
            rsbValue.setVisibility(View.INVISIBLE);
        } else {
            mImgGroup.setVisibility(View.VISIBLE);
        }
    }

    /**
     *
     */
    private BluetoothDevice BTInit() {
        Log.i(TAG, "Initializing Bluetooth...");
        Toast.makeText(getApplicationContext(), "Initializing Bluetooth...", Toast.LENGTH_SHORT).show();

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(bluetoothAdapter == null) { Toast.makeText(getApplicationContext(), "Device doesn't support bluetooth", Toast.LENGTH_SHORT).show(); return null; }

        if(!bluetoothAdapter.isEnabled()) {
            Intent enableAdapter = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableAdapter, 0);

            try { Thread.sleep(1000); } catch(InterruptedException e) { e.printStackTrace(); }
        }

        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

        if(bondedDevices.isEmpty())

        { Toast.makeText(getApplicationContext(), "Please pair the device first", Toast.LENGTH_SHORT).show(); return null; }

        for(BluetoothDevice device : bondedDevices) {
            if(device.getAddress().equals(DEVICE_ADDRESS)) {
                Log.i(TAG, "Initialized.");
                return device;
            }
        }

        return null;
    }

    /**
     *
     */
    private boolean BTConnect()
    {
        Log.i(TAG, "Connecting Bluetooth...");
        Toast.makeText(getApplicationContext(), "Connecting Bluetooth...", Toast.LENGTH_SHORT).show();

        boolean connected = true;

        try {
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(PORT_UUID); //Creates a socket to handle the outgoing connection
            bluetoothSocket.connect();

            Log.i(TAG, "Connected.");
            Toast.makeText(getApplicationContext(), "Connection to bluetooth device successful", Toast.LENGTH_LONG).show();
        }
        catch(IOException e) { e.printStackTrace(); Log.i(TAG, "Failed."); connected = false; }

        if (connected) {
            try { outputStream = bluetoothSocket.getOutputStream(); } catch (IOException e) { e.printStackTrace(); }
        }

        return connected;
    }

    /**
     *
     */
    private void BTDisconnect()
    {
        Log.i(TAG, "Disconnecting Bluetooth...");
        try {

            outputStream.flush(); outputStream.close(); bluetoothSocket.close(); bluetoothDevice = null;

        } catch (IOException e) { e.printStackTrace(); }
    }

    /**
     *
     */
    private void SendCommand(String command)
    {
        try { outputStream.write(command.getBytes()); } catch (IOException e) { e.printStackTrace(); }
    }

    /**
     *
     */
    private int getMaxContour(List<MatOfPoint> contours)
    {
        int currentIndex = 0, maxIndex = -1;
        double maxArea = 0.0;

        for (MatOfPoint mop : contours) {
            double area = Imgproc.contourArea(mop);
            if (area > maxArea) { maxArea = area; maxIndex = currentIndex; }
            currentIndex++;
        }

        return maxIndex;
    }

}
