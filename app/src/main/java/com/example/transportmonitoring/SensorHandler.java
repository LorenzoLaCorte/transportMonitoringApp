package com.example.transportmonitoring;

import static android.content.Context.SENSOR_SERVICE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

public class SensorHandler implements SensorEventListener {
    private final static String TAG = "SENSOR_MANAGER";

    private final Activity activity;
    private final Handler handler;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final SensorListener sensorListener;
    private MediaRecorder mediaRecorder;
    private float[] accelerometerValues;
    private Location location;
    private final FusedLocationProviderClient fusedLocationClient;

    private final static int delay = 2000; //in milliseconds

    private final Runnable updateSensorDataRunnable = new Runnable() {
        @Override
        public void run() {
            sensorListener.onAccelerometerUpdate(accelerometerValues);
            handler.postDelayed(this, delay); // Adjust the interval as needed
        }
    };
    private final Runnable updateNoiseRunnable = new Runnable() {
        @Override
        public void run() {
            sensorListener.onNoiseUpdate(getNoiseLevel());
            handler.postDelayed(this, delay); // Adjust the interval as needed
        }
    };
    private final Runnable updateLocationRunnable = new Runnable() {
        @Override
        public void run() {
            if (location != null) {
                sensorListener.onLocationUpdate(location);
                handler.postDelayed(this, delay); // Adjust the interval as needed
            }
        }
    };


    public SensorHandler(SensorListener sensorListener, Activity activity){
        this.sensorListener = sensorListener;
        this.activity = activity;
        handler = new Handler();
        // Initialize sensor and location managers
        sensorManager = (SensorManager) activity.getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);
        mediaRecorder = new MediaRecorder();
    }

    public void registerListener(){
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }
    public void unregisterListener(){
        sensorManager.unregisterListener(this);
    }

    public void startRecording(){
        File audioDir = new File(activity.getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "AudioMemos");
        if(!audioDir.mkdirs()){
            Log.w(TAG, "file audioDir not created");
        }
        String audioDirPath = audioDir.getAbsolutePath();
        Date currentTime = Calendar.getInstance().getTime();
        String curTimeStr = currentTime.toString().replace(" ", "_");
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile(new File(audioDirPath + "/" + curTimeStr + ".m4a"));
        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            handler.postDelayed(updateNoiseRunnable, delay);
        } catch (IOException e) {
            e.printStackTrace();
        }
        handler.postDelayed(updateNoiseRunnable, delay); // Adjust the interval as needed

    }
    private double getNoiseLevel() {
        if (mediaRecorder != null) {
            int amplitude = mediaRecorder.getMaxAmplitude();
            return 20 * Math.log10(amplitude);
        }
        return 0.0;
    }
    public void stopRecording(){
        handler.removeCallbacks(updateNoiseRunnable);
    }
    public void stopRecordingAndReleaseResources() {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
            stopRecording();
        }
    }

    public void startAccelerometerUpdates(){
        handler.postDelayed(updateSensorDataRunnable, delay); // Adjust the interval as needed
    }
    public void stopAccelerometerUpdates() {
        handler.removeCallbacks(updateSensorDataRunnable);
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

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            location = locationResult.getLastLocation();
            Log.d("position", String.valueOf(location));
        }
    };


    @SuppressLint("MissingPermission")
    public void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000)  // Interval in milliseconds for location updates
                .setFastestInterval(0);  // Fastest interval, set to 0 for immediate updates

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        handler.postDelayed(updateLocationRunnable, delay);
    }

    public void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        handler.removeCallbacks(updateLocationRunnable);
    }

    public interface SensorListener {
        void onAccelerometerUpdate(float[] values);
        void onLocationUpdate(Location location);
        void onNoiseUpdate(double noiseLevel);
    }
}