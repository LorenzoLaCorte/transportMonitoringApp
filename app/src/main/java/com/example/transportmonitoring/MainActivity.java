package com.example.transportmonitoring;

import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.content.pm.PackageManager;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.media.MediaRecorder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private final int acquisitionInterval = 5000; // millis
    private Handler handler;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private LocationManager locationManager;
    private MediaRecorder mediaRecorder;
    private float[] accelerometerValues;
    private double noiseLevel;
    private double latitude;
    private double longitude;

    private Button startButton;
    private Button stopButton;
    private TextView xValueTextView, yValueTextView, zValueTextView;
    private TextView xCoordinateTextView, yCoordinateTextView, noiseTextView;
    private LocationListener locationListener;
    private File recordingFile;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        accelerometerValues = new float[3]; // X, Y, Z

        handler = new Handler();

        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);

        xValueTextView = findViewById(R.id.xValueTextView);
        yValueTextView = findViewById(R.id.yValueTextView);
        zValueTextView = findViewById(R.id.zValueTextView);
        xCoordinateTextView = findViewById(R.id.xCoordinateTextView);
        yCoordinateTextView = findViewById(R.id.yCoordinateTextView);
        noiseTextView = findViewById(R.id.noiseTextView);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };

        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.MODIFY_AUDIO_SETTINGS,

                },
                1);

        // Request GPS updates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        startPeriodicUpdate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        stopPeriodicUpdate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop recording and release resources
        mediaRecorder.stop();
        mediaRecorder.reset();
        mediaRecorder.release();
        mediaRecorder = null;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(locationListener);
        }

        locationManager.removeUpdates(locationListener);
        handler.removeCallbacks(updateAccelerometerRunnable);
        handler.removeCallbacks(updateNoiseRunnable);
        handler.removeCallbacks(locationUpdateRunnable);
    }

    private double getNoiseLevel() {
        try {
            String state = Environment.getExternalStorageState();
            Context ctx = this.getApplicationContext();
            File audioDir = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "AudioMemos");
            audioDir.mkdirs();
            String audioDirPath = audioDir.getAbsolutePath();
            Date currentTime = Calendar.getInstance().getTime(); // current time
            String curTimeStr = currentTime.toString().replace(" ", "_");
            recordingFile = new File(audioDirPath + "/" + curTimeStr + ".m4a");

            mediaRecorder = new MediaRecorder();
            // Setup Media Recorder
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
            mediaRecorder.setOutputFile(recordingFile.getAbsolutePath());

            // Prepare and start the MediaRecorder
            mediaRecorder.prepare();
            mediaRecorder.start();

            // Calculate the average amplitude over a short duration to estimate noise level
            int amplitude = mediaRecorder.getMaxAmplitude();
            double noiseLevel = 20 * Math.log10(amplitude / 32767.0);

            return noiseLevel;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    @SuppressLint("DefaultLocale")
    private void handleDataAcquisition() {
        // Process the accelerometer data array as per your requirements
        // This method will be called every 10 seconds with updated accelerometer data

        xValueTextView.setText(String.format("%.2f", accelerometerValues[0]));
        yValueTextView.setText(String.format("%.2f", accelerometerValues[1]));
        zValueTextView.setText(String.format("%.2f", accelerometerValues[2]));

        noiseTextView.setText(String.format("%.2f", noiseLevel));

        xCoordinateTextView.setText(String.format("%.2f", latitude));
        yCoordinateTextView.setText(String.format("%.2f", longitude));

        // TODO: send data over MQTT channel
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometerValues = event.values;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void startRecording(View v) {
        startPeriodicUpdate();
    }

    public void stopRecording(View v) {
        stopPeriodicUpdate();
    }

    private void startPeriodicUpdate() {
        handler.postDelayed(updateAccelerometerRunnable, acquisitionInterval);
        handler.postDelayed(updateNoiseRunnable, acquisitionInterval);
        handler.postDelayed(locationUpdateRunnable, acquisitionInterval);

        startButton.setEnabled(false);
        stopButton.setEnabled(true);
    }

    private void stopPeriodicUpdate() {
        handler.removeCallbacks(updateAccelerometerRunnable);
        handler.removeCallbacks(updateNoiseRunnable);
        handler.removeCallbacks(locationUpdateRunnable);

        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    private final Runnable updateAccelerometerRunnable = new Runnable() {
        @Override
        public void run() {
            handleDataAcquisition();
            handler.postDelayed(this, acquisitionInterval);
        }
    };

    private final Runnable updateNoiseRunnable = new Runnable() {
        @Override
        public void run() {
            noiseLevel = getNoiseLevel();
            handleDataAcquisition();

            handler.postDelayed(this, acquisitionInterval);
        }
    };

    private final Runnable locationUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            handleDataAcquisition();
            handler.postDelayed(this, acquisitionInterval);
        }
    };

}
