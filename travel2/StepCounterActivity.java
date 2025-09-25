package com.example.travel2;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.*;

public class StepCounterActivity extends AppCompatActivity implements SensorEventListener {

    private TextView tvTimer, tvStepCount, tvPreviousSteps, modeText, speedText;
    private Button btnStartStop, btnSave, btnCamera;
    private ImageButton walkButton, bikeButton;
    private ListView lvStepHistory;
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private int stepCount = 0;
    private boolean isRunning = false;
    private long startTime = 0L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long timeInMilliseconds = 0L;
    private SharedPreferences preferences;
    private ArrayAdapter<String> historyAdapter;
    private ArrayList<String> historyItems;
    private String currentMode = "";
    private static final float STEP_LENGTH_METERS = 0.75f;
    private static final float BIKE_SPEED_MULTIPLIER = 3.5f;
    private final Handler speedUpdateHandler = new Handler(Looper.getMainLooper());
    private int nextRecordId = 1;
    private TreeMap<Integer, StepRecord> records = new TreeMap<>();
    private ImageView backgroundImage;
    private final UpdateTimerRunnable updateTimerRunnable;
    private final SpeedUpdateRunnable speedUpdateRunnable;

    private Vibrator vibrator;
    private static final int REQUEST_IMAGE_CAPTURE = 1;

    public StepCounterActivity() {
        updateTimerRunnable = new UpdateTimerRunnable(this);
        speedUpdateRunnable = new SpeedUpdateRunnable(this);
    }

    private static class UpdateTimerRunnable implements Runnable {
        private final WeakReference<StepCounterActivity> activityRef;

        UpdateTimerRunnable(StepCounterActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            StepCounterActivity activity = activityRef.get();
            if (activity == null || !activity.isRunning) return;

            activity.timeInMilliseconds = SystemClock.elapsedRealtime() - activity.startTime;
            int secs = (int) (activity.timeInMilliseconds / 1000);
            int mins = secs / 60;
            int hours = mins / 60;
            secs = secs % 60;
            mins = mins % 60;
            activity.tvTimer.setText(String.format("%02d:%02d:%02d", hours, mins, secs));
            activity.handler.postDelayed(this, 1000);
        }
    }

    private static class SpeedUpdateRunnable implements Runnable {
        private final WeakReference<StepCounterActivity> activityRef;

        SpeedUpdateRunnable(StepCounterActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            StepCounterActivity activity = activityRef.get();
            if (activity == null || !activity.isRunning) return;

            activity.updateSpeed();
            activity.speedUpdateHandler.postDelayed(this, 1000);
        }
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_counter);

        preferences = getSharedPreferences("StepCounter", MODE_PRIVATE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        initializeViews();
        setupSensors();
        loadHistory();
        setupClickListeners();

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }
    }

    private void initializeViews() {
        tvTimer = findViewById(R.id.tvTimer);
        tvStepCount = findViewById(R.id.tvStepCount);
        tvPreviousSteps = findViewById(R.id.tvPreviousSteps);
        btnStartStop = findViewById(R.id.btnStartStop);
        btnSave = findViewById(R.id.btnSave);
        lvStepHistory = findViewById(R.id.lvStepHistory);
        modeText = findViewById(R.id.modeText);
        speedText = findViewById(R.id.speedText);
        walkButton = findViewById(R.id.walkButton);
        bikeButton = findViewById(R.id.bikeButton);
        backgroundImage = findViewById(R.id.backgroundImage);

        historyItems = new ArrayList<>();
        historyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, historyItems);
        lvStepHistory.setAdapter(historyAdapter);

        updateButtonStates(false);
        btnSave.setEnabled(false);
        btnCamera = findViewById(R.id.btnCamera);
        btnCamera.setVisibility(View.GONE); // Boshlanishida yashirin

        btnCamera.setOnClickListener(v -> {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(cameraIntent, REQUEST_IMAGE_CAPTURE);
            }
        });

    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        if (stepSensor == null) {
            Toast.makeText(this, "Step sensor not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupClickListeners() {
        walkButton.setOnClickListener(v -> setMode("walk"));
        bikeButton.setOnClickListener(v -> setMode("bike"));

        btnStartStop.setOnClickListener(v -> {
            if (!isRunning) {
                startTracking();
            } else {
                stopTracking();
            }
        });

        btnSave.setOnClickListener(v -> saveRecord());

        lvStepHistory.setOnItemLongClickListener((parent, view, position, id) -> {
            showDeleteDialog(position);
            return true;
        });
    }

    private void setMode(String mode) {
        currentMode = mode;
        modeText.setText(mode.equals("walk") ? R.string.walk_mode : R.string.bike_mode);
        updateButtonStates(false);
        walkButton.setEnabled(mode.equals("bike"));
        bikeButton.setEnabled(mode.equals("walk"));
        btnStartStop.setEnabled(true);
        btnStartStop.setText(R.string.btn_start);

        backgroundImage.setImageResource(mode.equals("walk") ?
                R.drawable.hiking_background : R.drawable.cycling_background);
        backgroundImage.setVisibility(View.VISIBLE);
    }

    private void startTracking() {
        if (currentMode.isEmpty()) {
            Toast.makeText(this, R.string.mode_not_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        if (stepSensor == null) {
            Toast.makeText(this, "Step sensor not available", Toast.LENGTH_SHORT).show();
            return;
        }

        isRunning = true;
        startTime = SystemClock.elapsedRealtime();
        handler.post(updateTimerRunnable);
        speedUpdateHandler.post(speedUpdateRunnable);
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        btnStartStop.setText(R.string.btn_stop);
        updateButtonStates(true);
        btnSave.setEnabled(true);
    }

    private void stopTracking() {
        isRunning = false;
        handler.removeCallbacks(updateTimerRunnable);
        speedUpdateHandler.removeCallbacks(speedUpdateRunnable);
        sensorManager.unregisterListener(this);
        btnStartStop.setText(R.string.btn_start);
        updateButtonStates(false);
        resetMode();
    }

    private void updateButtonStates(boolean isTracking) {
        walkButton.setEnabled(!isTracking);
        bikeButton.setEnabled(!isTracking);
        btnSave.setEnabled(isTracking || stepCount > 0);
    }

    private void updateSpeed() {
        if (!isRunning) return;
        long elapsedTime = SystemClock.elapsedRealtime() - startTime;
        float distanceKm = calculateDistance();
        float speedKmh = (distanceKm / elapsedTime) * 3600000;
        speedText.setText(getString(R.string.current_speed, speedKmh));
    }

    private float calculateDistance() {
        if (currentMode.equals("walk")) {
            return stepCount * STEP_LENGTH_METERS / 1000f;
        } else {
            return stepCount * STEP_LENGTH_METERS * BIKE_SPEED_MULTIPLIER / 1000f;
        }
    }

    private void triggerVibrationAndCamera() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(500);
        }
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR && isRunning) {
            stepCount++;
            tvStepCount.setText(getString(R.string.current_steps, stepCount));
            updateSpeed();

            if (stepCount == 10) {
                // Vibratsiya
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(500);
                }

                // Kamera tugmasini ko‘rsatamiz
                btnCamera.setVisibility(View.VISIBLE);

                // Toast orqali bildirishnoma
                Toast.makeText(this, "📷 Kameradan foydalanishingiz mumkin!", Toast.LENGTH_LONG).show();
            }

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            // You can show or save imageBitmap here
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void resetMode() {
        currentMode = "";
        modeText.setText("");
        walkButton.setEnabled(true);
        bikeButton.setEnabled(true);
        backgroundImage.setVisibility(View.GONE);
    }

    private void loadHistory() {
        records.clear();
        historyItems.clear();
        Map<String, ?> allPrefs = preferences.getAll();
        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            if (entry.getKey().startsWith("record_")) {
                String[] parts = entry.getValue().toString().split("\\|");
                if (parts.length == 3) {
                    int id = Integer.parseInt(entry.getKey().substring(7));
                    records.put(id, new StepRecord(id, parts[0], parts[1], parts[2]));
                }
            }
        }
        updateHistoryList();
    }

    private void updateHistoryList() {
        historyItems.clear();
        for (Map.Entry<Integer, StepRecord> entry : records.entrySet()) {
            StepRecord r = entry.getValue();
            String item = getString(R.string.steps_format, r.id, r.timestamp, r.value + (r.mode.equals("walk") ? " 🚶" : " 🚲"));
            historyItems.add(item);
        }
        historyAdapter.notifyDataSetChanged();
        saveRecordsToPreferences();
    }

    private void saveRecord() {
        if (stepCount == 0) {
            Toast.makeText(this, R.string.no_steps_to_save, Toast.LENGTH_SHORT).show();
            return;
        }

        float distance = calculateDistance();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        String record = String.format(Locale.getDefault(), "%.2f km", distance);

        StepRecord stepRecord = new StepRecord(nextRecordId, currentMode, timestamp, record);
        records.put(nextRecordId, stepRecord);
        nextRecordId++;

        updateHistoryList();

        stepCount = 0;
        tvStepCount.setText(getString(R.string.current_steps, 0));
        speedText.setText("");
        timeInMilliseconds = 0L;
        tvTimer.setText("00:00:00");

        Toast.makeText(this, R.string.record_saved, Toast.LENGTH_SHORT).show();
    }

    private void saveRecordsToPreferences() {
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<Integer, StepRecord> entry : records.entrySet()) {
            StepRecord record = entry.getValue();
            editor.putString("record_" + entry.getKey(), record.mode + "|" + record.timestamp + "|" + record.value);
        }
        editor.apply();
    }

    private void showDeleteDialog(int position) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(R.string.delete_message)
                .setPositiveButton(R.string.delete_yes, (dialog, which) -> {
                    int recordId = (Integer) records.keySet().toArray()[position];
                    records.remove(recordId);
                    preferences.edit().remove("record_" + recordId).apply();
                    updateHistoryList();
                    Toast.makeText(this, R.string.delete_success, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.delete_no, null)
                .show();
    }

    private static class StepRecord {
        int id;
        String mode;
        String timestamp;
        String value;

        StepRecord(int id, String mode, String timestamp, String value) {
            this.id = id;
            this.mode = mode;
            this.timestamp = timestamp;
            this.value = value;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isRunning) sensorManager.unregisterListener(this);
        handler.removeCallbacks(updateTimerRunnable);
        speedUpdateHandler.removeCallbacks(speedUpdateRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isRunning && stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
            handler.post(updateTimerRunnable);
            speedUpdateHandler.post(speedUpdateRunnable);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("stepCount", stepCount);
        outState.putBoolean("isRunning", isRunning);
        outState.putLong("startTime", startTime);
        outState.putLong("timeInMilliseconds", timeInMilliseconds);
        outState.putString("currentMode", currentMode);
    }

    private void restoreState(Bundle savedInstanceState) {
        stepCount = savedInstanceState.getInt("stepCount", 0);
        isRunning = savedInstanceState.getBoolean("isRunning", false);
        startTime = savedInstanceState.getLong("startTime", 0L);
        timeInMilliseconds = savedInstanceState.getLong("timeInMilliseconds", 0L);
        currentMode = savedInstanceState.getString("currentMode", "");

        if (isRunning) {
            handler.post(updateTimerRunnable);
            speedUpdateHandler.post(speedUpdateRunnable);
            btnStartStop.setText(R.string.btn_stop);
        }

        tvStepCount.setText(getString(R.string.current_steps, stepCount));
        if (!currentMode.isEmpty()) {
            modeText.setText(currentMode.equals("walk") ? R.string.walk_mode : R.string.bike_mode);
            updateButtonStates(isRunning);
        }
    }
}


