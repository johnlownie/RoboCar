package ca.jetsphere.robocar.activities;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;

import ca.jetsphere.robocar.R;
import ca.jetsphere.robocar.devices.Joystick;

/**
 *
 */
public class DriveActivity extends AppCompatActivity
{
    private static String TAG = "DriveActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_drive);

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
                }
                return true;
            }
        });

        final Button btnTrack = findViewById(R.id.btnTrack);
        btnTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "BT Connect clicked...");

                Intent myIntent = new Intent(DriveActivity.this, MainActivity.class);
                DriveActivity.this.startActivity(myIntent);
            }
        });
    }
}
