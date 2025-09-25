package com.example.travel2;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private TextView tvKoreanTime;
    private TextView tvKoreanDate;
    private Button btnMap, btnWeather, btnGuide, btnSteps;
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private int stepCount = 0;
    private static final int CAMERA_PERMISSION_CODE = 100;
    private final Handler timeHandler = new Handler(Looper.getMainLooper());
    private final TimeUpdateRunnable timeUpdateRunnable;

    public MainActivity() {
        timeUpdateRunnable = new TimeUpdateRunnable(this);
    }

    private static class TimeUpdateRunnable implements Runnable {
        private final WeakReference<MainActivity> activityReference;

        TimeUpdateRunnable(MainActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            MainActivity activity = activityReference.get();
            if (activity == null) return;

            SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm");
            SimpleDateFormat dateSdf = new SimpleDateFormat("yyyy.MM.dd");
            timeSdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
            dateSdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
            String koreanTime = timeSdf.format(new Date());
            String koreanDate = dateSdf.format(new Date());
            activity.tvKoreanTime.setText(koreanTime);
            activity.tvKoreanDate.setText(koreanDate);
            activity.timeHandler.postDelayed(this, 1000);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvKoreanTime = findViewById(R.id.tvKoreanTime);
        tvKoreanDate = findViewById(R.id.tvKoreanDate);
        btnMap = findViewById(R.id.btnMap);
        btnWeather = findViewById(R.id.btnWeather);
        btnGuide = findViewById(R.id.btnGuide);
        btnSteps = findViewById(R.id.btnSteps);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        // Start updating Korean time
        updateKoreanTime();

        setupButtonClickListeners();
    }

    private void setupButtonClickListeners() {
        btnMap.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MapActivity.class);
            startActivity(intent);
        });

        btnWeather.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, WeatherActivity.class);
            startActivity(intent);
        });

        btnGuide.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, GuideActivity.class);
            startActivity(intent);
        });

        btnSteps.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, StepCounterActivity.class);
            startActivity(intent);
        });
    }

    private void updateKoreanTime() {
        timeHandler.post(timeUpdateRunnable);
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            new AlertDialog.Builder(this)
                    .setTitle("Camera Permission Needed")
                    .setMessage("This app needs the Camera permission to take photos")
                    .setPositiveButton("OK", (dialog, which) -> 
                        ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.CAMERA},
                            CAMERA_PERMISSION_CODE))
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openCamera() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Camera")
               .setItems(new CharSequence[]{"Back Camera", "Front Camera"}, (dialog, which) -> {
                   Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                   if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                       cameraIntent.putExtra("android.intent.extras.CAMERA_FACING", which);
                       startActivity(cameraIntent);
                   } else {
                       Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
                   }
               });
        builder.create().show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (stepSensor != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            stepCount = (int) event.values[0];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for this implementation
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeHandler.removeCallbacks(timeUpdateRunnable);
    }
}