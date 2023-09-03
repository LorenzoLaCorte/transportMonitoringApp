package com.example.transportmonitoring;

import static android.content.Context.LOCATION_SERVICE;
import static android.content.Context.SENSOR_SERVICE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

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
    private final LocationManager locationManager;
    private final LocationListener locationListener;
    private MediaRecorder mediaRecorder;

    private final Runnable updateSensorDataRunnable = new Runnable() {
        @Override
        public void run() {
            //handleDataAcquisition();
            handler.postDelayed(this, 10000); // Adjust the interval as needed
        }
    };
    private final Runnable updateNoiseRunnable = new Runnable() {
        @Override
        public void run() {
            sensorListener.onNoiseUpdate(getNoiseLevel());
            handler.postDelayed(this, 10000); // Adjust the interval as needed
        }
    };
    private final Runnable updateLocationRunnable = new Runnable() {
        @Override
        public void run() {
            //handleDataAcquisition();
            handler.postDelayed(this, 10000); // Adjust the interval as needed
        }
    };

    public SensorHandler(SensorListener sensorListener, Activity activity){
        this.sensorListener = sensorListener;
        this.activity = activity;
        handler = new Handler();
        // Initialize sensor and location managers
        sensorManager = (SensorManager) activity.getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        locationManager = (LocationManager) activity.getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                sensorListener.onLocationUpdate(location);
            }
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override
            public void onProviderEnabled(String provider) {}
            @Override
            public void onProviderDisabled(String provider) {}
        };
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
            sensorListener.onNoiseUpdate(getNoiseLevel());
        } catch (IOException e) {
            e.printStackTrace();
        }
        handler.postDelayed(updateNoiseRunnable, 10000); // Adjust the interval as needed

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
        handler.postDelayed(updateSensorDataRunnable, 10000); // Adjust the interval as needed
    }
    public void stopAccelerometerUpdates() {
        handler.removeCallbacks(updateSensorDataRunnable);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            sensorListener.onAccelerometerUpdate(event.values);
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @SuppressLint("MissingPermission")
    public void startLocationUpdates(){
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        handler.postDelayed(updateLocationRunnable, 10000); // Adjust the interval as needed
    }
    public void stopLocationUpdates(){
        locationManager.removeUpdates(locationListener);
        handler.removeCallbacks(updateLocationRunnable);

    }

    public interface SensorListener {
        void onAccelerometerUpdate(float[] values);
        void onLocationUpdate(Location location);
        void onNoiseUpdate(double noiseLevel);
    }
}