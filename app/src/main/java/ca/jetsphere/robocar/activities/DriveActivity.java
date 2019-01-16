package ca.jetsphere.robocar.activities;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import ca.jetsphere.robocar.R;
import ca.jetsphere.robocar.devices.Joystick;
import ca.jetsphere.robocar.services.BluetoothService;

/**
 *
 */
public class DriveActivity extends AppCompatActivity
{
    private static String TAG = "DriveActivity";

    BluetoothService mService = null;
    private Intent mIntent;
    private boolean mBound;
    int sendCount = 0;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_drive);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.hide();

        final ToggleButton btnConnect = findViewById(R.id.btnConnect);
        final Button btnTrack = findViewById(R.id.btnTrack);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mBound)  mService.toggle(btnConnect.isChecked());
            }
        });

        btnTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent myIntent = new Intent(DriveActivity.this, MainActivity.class);
                DriveActivity.this.startActivity(myIntent);
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

                    if (sendCount++ >= 10 && mBound) {
                        mService.sendMessage(String.valueOf(joystick.getX()) + "," + String.valueOf(joystick.getY()));
                        sendCount = 0;
                    }
                }
                return true;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(broadcastReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();

        startService(mIntent);
        registerReceiver(broadcastReceiver, new IntentFilter(BluetoothService.BROADCAST_ACTION));
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
}
