package ca.jetsphere.robocar.activities;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import ca.jetsphere.robocar.R;

/**
 *
 */
public class DriveActivity extends AppCompatActivity
{
    private static String TAG = "DriveActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_drive);

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
