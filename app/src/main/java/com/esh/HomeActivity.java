package com.esh;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class HomeActivity extends AppCompatActivity {

    EditText ageEditText;
    TextView rhrTextView;
    LinearLayout rhrLinearLayout;

    LinearLayout thrLinearLayout;

    private SensorManager sensorManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        setUpPermissions();

        ageEditText = findViewById(R.id.et_age);
        rhrLinearLayout = findViewById(R.id.ll_restingHeartRate);
        rhrTextView = findViewById(R.id.tv_restingHeartRate);
        thrLinearLayout = findViewById(R.id.ll_targetHeartRate);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_HEART_BEAT) != null) {
            ageEditText.setText("HEART_BEAT supports");
        } else {
            ageEditText.setText("no HEART_BEAT supports");
        }

        if (savedInstanceState != null) {
            ageEditText.setText(savedInstanceState.getString("storedAge"));
            rhrTextView.setText(savedInstanceState.getString("storedRHR"));
        }

        rhrLinearLayout.setOnClickListener(view -> getRestingHeartRate());
        thrLinearLayout.setOnClickListener(view -> calculateTargetHeartRate());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        String currentAge = ageEditText.getText().toString();
        outState.putString("storedAge", currentAge);
        String currentRHR = rhrTextView.getText().toString();
        outState.putString("storedRHR", currentRHR);
    }

    private void calculateTargetHeartRate() {
        String age = ageEditText.getText().toString();
        if (age.matches("")) {
            Toast.makeText(this, "Please enter your age!", Toast.LENGTH_SHORT).show();
            return;
        }

        String restingHeartRate = rhrTextView.getText().toString();
        if (restingHeartRate.matches("")) {
            Toast.makeText(this, "Please calculate your RHR first!", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intentToCalculateTHR = new Intent(HomeActivity.this, MainActivity.class);

        intentToCalculateTHR.putExtra("age", age);
        intentToCalculateTHR.putExtra("restingHeartRate", restingHeartRate);

        startActivity(intentToCalculateTHR);
    }

    public void getRestingHeartRate() {
        Intent intentToGetRHR = new Intent(HomeActivity.this, MainActivity.class);
        startActivityForResult(intentToGetRHR, 1);
    }

    private void setUpPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            String[] permissionsWeNeed = new String[]{Manifest.permission.CAMERA};
            requestPermissions(permissionsWeNeed, 88);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 88) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // The permission was granted!
                //set up whatever required the permissions

            } else {
                Toast.makeText(this, "Permission for camera not granted. HeartBeat Monitor can't run.", Toast.LENGTH_LONG).show();
                finish();
                // The permission was denied, so we can show a message why we can't run the app
                // and then close the app.
            }
            // Other permissions could go down here
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            String restingHeartRate = data.getStringExtra("restingHeartRate");
            rhrTextView.setText(restingHeartRate);
        }
    }
}
