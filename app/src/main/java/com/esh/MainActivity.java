package com.esh;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "HeartRateMonitor";
    private static final AtomicBoolean processing = new AtomicBoolean(false);

    private static SurfaceView preview = null;
    private static SurfaceHolder previewHolder = null;
    private static Camera camera = null;
    private static View image = null;
    private static TextView text = null;

    private static PowerManager.WakeLock wakeLock = null;

    private static int averageIndex = 0;
    private static final int averageArraySize = 4;
    private static final int[] averageArray = new int[averageArraySize];

    public static enum TYPE {
        GREEN, RED
    }

    private static TYPE currentType = TYPE.GREEN;

    public static TYPE getCurrent() {
        return currentType;
    }

    private static int beatsIndex = 0;
    private static final int beatsArraySize = 3;
    private static final int[] beatsArray = new int[beatsArraySize];
    private static double beats = 0;
    private static long startTime = 0;

    public static String heartRate;
    private LinearLayout measuringRHRLinearLayout;
    private static LinearLayout calculatingTHRLinearLayout;
    private static LinearLayout cardListLinearLayout;
    private static int maximumHeartRate;
    private static int heartRateReserve;
    private static boolean calculatingTHR = false;
    public static int rhr = 0;


    @SuppressLint("InvalidWakeLockTag")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preview = (SurfaceView) findViewById(R.id.preview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        image = findViewById(R.id.image);
        text = (TextView) findViewById(R.id.text);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intentThatStartedThisActivity = getIntent();
        if (intentThatStartedThisActivity.hasExtra("age")) {
            measuringRHRLinearLayout = findViewById(R.id.ll_measuringRHR);
            calculatingTHRLinearLayout = findViewById(R.id.ll_calculatingTHR);
            cardListLinearLayout = findViewById(R.id.ll_cardList);

            int age = Integer.parseInt(intentThatStartedThisActivity.getStringExtra("age"));
            rhr = Integer.parseInt(intentThatStartedThisActivity.getStringExtra("restingHeartRate"));

            calculateTargetHeartRate(age);
        }
    }

    private void calculateTargetHeartRate(int age) {
        measuringRHRLinearLayout.setVisibility(View.GONE);
        calculatingTHRLinearLayout.setVisibility(View.VISIBLE);
        calculatingTHR = true;

        maximumHeartRate = 220 - age;
        TextView MHRTextView = findViewById(R.id.tv_MHR);
        MHRTextView.setText(String.valueOf(maximumHeartRate));

        heartRateReserve = maximumHeartRate - rhr;
        TextView HRRTextView = (TextView) findViewById(R.id.tv_HRR);
        HRRTextView.setText(String.valueOf(heartRateReserve));

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.action_retry:
                startCam();
                return true;
            case R.id.action_ok:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onResume() {
        super.onResume();

        wakeLock.acquire();

        camera = Camera.open();

        startTime = System.currentTimeMillis();
    }

    @Override
    public void onPause() {
        super.onPause();

        wakeLock.release();

        camera.setPreviewCallback(null);
        camera.stopPreview();
        camera.release();
        camera = null;
    }

    private static final Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {

        @Override
        public void onPreviewFrame(byte[] data, Camera cam) {

            if (data == null) throw new NullPointerException();
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) throw new NullPointerException();

            if (!processing.compareAndSet(false, true)) return;

            int width = size.width;
            int height = size.height;

            int imgAvg = MModel.decodeYUV420SPtoRedAvg(data.clone(), height, width);
            Log.i(TAG, "imgAvg=" + imgAvg);
            if (imgAvg == 0 || imgAvg == 255) {
                processing.set(false);
                return;
            }

            int averageArrayAvg = 0;
            int averageArrayCnt = 0;
            for (int j : averageArray) {
                if (j > 0) {
                    averageArrayAvg += j;
                    averageArrayCnt++;
                }
            }

            int rollingAverage = (averageArrayCnt > 0) ? (averageArrayAvg / averageArrayCnt) : 0;
            TYPE newType = currentType;
            if (imgAvg < rollingAverage) {
                newType = TYPE.RED;
                if (newType != currentType) {
                    beats++;
                    Log.d(TAG, "BEAT!! beats=" + beats);
                }
            } else if (imgAvg > rollingAverage) {
                newType = TYPE.GREEN;
            }

            if (averageIndex == averageArraySize) averageIndex = 0;
            averageArray[averageIndex] = imgAvg;
            averageIndex++;

            // Transitioned from one state to another to the same
            if (newType != currentType) {
                currentType = newType;
                image.postInvalidate();
            }

            long endTime = System.currentTimeMillis();
            double totalTimeInSecs = (endTime - startTime) / 1000d;
            if (totalTimeInSecs >= 10) {
                double bps = (beats / totalTimeInSecs);
                int dpm = (int) (bps * 60d);
                if (dpm < 30 || dpm > 180) {
                    startTime = System.currentTimeMillis();
                    beats = 0;
                    processing.set(false);
                    return;
                }

                Log.d(TAG, "totalTimeInSecs=" + totalTimeInSecs + " beats=" + beats);

                if (beatsIndex == beatsArraySize) beatsIndex = 0;
                beatsArray[beatsIndex] = dpm;
                beatsIndex++;

                int beatsArrayAvg = 0;
                int beatsArrayCnt = 0;
                for (int j : beatsArray) {
                    if (j > 0) {
                        beatsArrayAvg += j;
                        beatsArrayCnt++;
                    }
                }
                int beatsAvg = (beatsArrayAvg / beatsArrayCnt);
                heartRate = String.valueOf(beatsAvg);
                text.setText(heartRate);
                startTime = System.currentTimeMillis();
                beats = 0;
                stopCam();
            }
            processing.set(false);
        }
    };

    public static void stopCam() {
        camera.stopPreview();
        if (calculatingTHR) {
            setCard();
        }
    }

    private static void setCard() {

        int index = 0;
        int workoutHeartRate = Integer.parseInt(heartRate);

        for (double i = 0.6; i < 1; i = i + 0.1) {
            //actual Karvonen's formula: (workoutHeartRate > heartRateReserve*i + restingHeartRate)
            if (workoutHeartRate > maximumHeartRate * i) index++;
            else break;
        }

        for (int i = 0; i < 5; i++) {
            TextView card = (TextView) cardListLinearLayout.getChildAt(i);
            card.setBackgroundColor(Color.parseColor("white"));
            card.setTextColor(Color.parseColor("red"));
        }


        TextView card = (TextView) cardListLinearLayout.getChildAt(index);
        card.setBackgroundColor(Color.parseColor("red"));
        card.setTextColor(Color.parseColor("white"));
    }

    public static void startCam() {
        camera.startPreview();
        startTime = System.currentTimeMillis();
    }

    public void finish() {
        Intent data = new Intent();
        data.putExtra("restingHeartRate", heartRate);
        setResult(RESULT_OK, data);
        super.finish();
    }


    private static final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                camera.setPreviewDisplay(previewHolder);
                camera.setPreviewCallback(previewCallback);
            } catch (Throwable t) {
                Log.e("surfaceCallbackDemo", "Exception in setPreviewDisplay()", t);
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Camera.Parameters parameters = camera.getParameters();
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            Camera.Size size = getSmallestPreviewSize(width, height, parameters);
            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
                Log.d(TAG, "Using width=" + size.width + " height=" + size.height);
            }
            camera.setParameters(parameters);
            camera.startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // Ignore
        }
    };


    private static Camera.Size getSmallestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;

                    if (newArea < resultArea) result = size;
                }
            }
        }

        return result;
    }

}
