package ca.jetsphere.robocar.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.florescu.android.rangeseekbar.RangeSeekBar;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
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

import java.util.ArrayList;
import java.util.List;

import ca.jetsphere.robocar.R;
import ca.jetsphere.robocar.devices.Joystick;
import ca.jetsphere.robocar.devices.MyJavaCameraView;
import ca.jetsphere.robocar.services.BluetoothService;

/**
 *
 */
public class MainActivity extends AbstractActivity implements CameraBridgeViewBase.CvCameraViewListener2
{
    private static String TAG = "MainActivity";
    private static final String FRAGMENT_DIALOG = "dialog";
    private static final int REQUEST_CAMERA_PERMISSION = 1;

    private enum FrameSource { RAW, THRESHOLD };
    private enum State { STOP, FORWARD, REVERSE, TURN_LEFT, TURN_RIGHT, FORWARD_LEFT, FORWARD_RIGHT };

    BluetoothService mService = null;
    private Intent mIntent;
    private boolean mBound;

    private View mTrackGroup, mDriveGroup, mHsvGroup;

    MyJavaCameraView javaCameraView;
    Mat mRgba, imgBlurred, imgThreshold, imgTemp;
    Mat erodeElement, dilateElement;
    FrameSource frameSource = FrameSource.RAW;

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

    /**
     *
     */
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final ToggleButton btnConnect = findViewById(R.id.btnConnect);
            btnConnect.setChecked(intent.getBooleanExtra("connected", false));
        }
    };

    /**
     *
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
            mBound = false;
        }
    };

    /**
     *
     */
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

        mTrackGroup = findViewById(R.id.trackGroup);
        mDriveGroup = findViewById(R.id.driveGroup);
        mHsvGroup = findViewById(R.id.hsvGroup);

        final ToggleButton btnConnect = findViewById(R.id.btnConnect);
        final ToggleButton btnTorch = findViewById(R.id.btnTorch);
        final ImageButton  btnDrive = findViewById(R.id.btnDrive);
        final ImageButton  btnAuto = findViewById(R.id.btnAuto);

        final ToggleButton btnRawImage = findViewById(R.id.btnRawImage);
        final ToggleButton btnThresholdImage = findViewById(R.id.btnThresholdImage);
        final ToggleButton btnTrackImage = findViewById(R.id.btnTrackImage);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mBound) mService.toggle(btnConnect.isChecked());
            }
        });

        btnTorch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (btnTorch.isChecked()) javaCameraView.torchOn(); else javaCameraView.torchOff();
            }
        });

        btnDrive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mTrackGroup.setVisibility(View.GONE);
                mDriveGroup.setVisibility(View.VISIBLE);
            }
        });

        btnAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDriveGroup.setVisibility(View.GONE);
                mTrackGroup.setVisibility(View.VISIBLE);
            }
        });

        btnRawImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnRawImage.setChecked(true);
                btnThresholdImage.setChecked(false);
                frameSource = FrameSource.RAW;
            }
        });

        btnThresholdImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnRawImage.setChecked(false);
                btnThresholdImage.setChecked(true);

                mTrackGroup.setVisibility(View.GONE);
                mHsvGroup.setVisibility(View.VISIBLE);
                rsbHue.setVisibility(View.VISIBLE);

                frameSource = FrameSource.THRESHOLD;
            }
        });

        btnTrackImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isTracking = !isTracking;
                btnTrackImage.setChecked(isTracking);
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

        final TextView textView1 = findViewById(R.id.textView1);
        final TextView textView2 = findViewById(R.id.textView2);
        final TextView textView3 = findViewById(R.id.textView3);
        final TextView textView4 = findViewById(R.id.textView4);
        final TextView textView5 = findViewById(R.id.textView5);

        final RelativeLayout rlJoystick = findViewById(R.id.layout_joystick);
        final Joystick joystick = new Joystick(getApplicationContext(), rlJoystick, R.drawable.joystick_button);
        joystick.setStickSize(300, 300);
        joystick.setLayoutSize(900, 900);
        joystick.setLayoutAlpha(150);
        joystick.setStickAlpha(100);
        joystick.setOffset(90);
        joystick.setMinDistance(50);

        rlJoystick.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View arg0, MotionEvent arg1) {
                joystick.drawStick(arg1);
                if(arg1.getAction() == MotionEvent.ACTION_DOWN || arg1.getAction() == MotionEvent.ACTION_MOVE) {
                    int direction = joystick.get8Direction();
                    Log.i(TAG, "Direction: " + direction);

                    textView1.setText("X: " + String.valueOf(joystick.getX()));
                    textView2.setText("Y: " + String.valueOf(joystick.getY()));
                    textView3.setText("Angle: " + String.valueOf(joystick.getAngle()));
                    textView4.setText("Distance: " + String.valueOf(joystick.getDistance()));
                    textView5.setText("Direction: " + direction);

                    if (mBound) {
                        mService.sendMessage(String.valueOf(joystick.getX()) + "," + String.valueOf(joystick.getY()));
                    }
                }
                return true;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (javaCameraView != null) {
            javaCameraView.torchOff();
            javaCameraView.disableView();

            ToggleButton btnTorch = findViewById(R.id.btnTorch);
            btnTorch.setChecked(false);
        }

        unregisterReceiver(broadcastReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (javaCameraView != null) {
            javaCameraView.torchOff();
            javaCameraView.disableView();

            ToggleButton btnTorch = findViewById(R.id.btnTorch);
            btnTorch.setChecked(false);
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

        startService(mIntent);
        registerReceiver(broadcastReceiver, new IntentFilter(BluetoothService.BROADCAST_ACTION));
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

        if (frameSource == FrameSource.THRESHOLD || isTracking ) trackObject(mRgba);

        switch (frameSource) {
            case RAW        : return mRgba;
            case THRESHOLD  : return imgThreshold;
            default         : return mRgba;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        mIntent = new Intent(this, BluetoothService.class);
        bindService(mIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mBound) {
            unbindService(mConnection);
            mBound = false;
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
                if (mTrackGroup.getVisibility() == View.VISIBLE) {
                    mTrackGroup.setVisibility(View.GONE);
                } else if (mDriveGroup.getVisibility() == View.VISIBLE) {
                    // do nothing if we are in drive mode
                } else if (mHsvGroup.getVisibility() == View.VISIBLE) {
                    mHsvGroup.setVisibility(View.GONE);
                    mTrackGroup.setVisibility(View.VISIBLE);
                    rsbHue.setVisibility(View.INVISIBLE);
                    rsbSaturation.setVisibility(View.INVISIBLE);
                    rsbValue.setVisibility(View.INVISIBLE);
                } else {
                    mTrackGroup.setVisibility(View.VISIBLE);
                }
            }
        });
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
