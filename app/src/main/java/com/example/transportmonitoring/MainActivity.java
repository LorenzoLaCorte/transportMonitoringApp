package com.example.transportmonitoring;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements SensorHandler.SensorListener, MqttHandler.MqttDataGenerator {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "Main Activity";

    private boolean recording = false;
    private SensorHandler sensorHandler;
    private MqttHandler mqttHandler;
    private final String[] topics = {"/tm/accelerometer", "/tm/noise", "/tm/position"};

    private Button startButton;
    private Button stopButton;
    private TextView xValueTextView;
    private TextView yValueTextView;
    private TextView zValueTextView;
    private TextView xCoordinateTextView;
    private TextView yCoordinateTextView;
    private TextView noiseTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        xValueTextView = findViewById(R.id.xValueTextView);
        yValueTextView = findViewById(R.id.yValueTextView);
        zValueTextView = findViewById(R.id.zValueTextView);
        xCoordinateTextView = findViewById(R.id.xCoordinateTextView);
        yCoordinateTextView = findViewById(R.id.yCoordinateTextView);
        noiseTextView = findViewById(R.id.noiseTextView);

        mqttHandler = new MqttHandler(this, topics);
        sensorHandler = new SensorHandler(this, this);

        // Request necessary permissions
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.MODIFY_AUDIO_SETTINGS,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                PERMISSION_REQUEST_CODE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorHandler.registerListener();
        if(recording)
            startUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorHandler.unregisterListener();
        stopUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecordingAndReleaseResources();
        stopLocationUpdates();
        mqttHandler.disconnect();
    }

    public void startRecording(View v) {
        recording = true;
        startUpdates();
    }

    public void stopRecording(View v) {
        recording = false;
        stopUpdates();
    }

    private void stopRecordingAndReleaseResources() {
        sensorHandler.stopRecordingAndReleaseResources();
    }

    private void stopLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            sensorHandler.stopLocationUpdates();
        }
    }

    private void publishMessage(String topic, String message) {
        Log.d(TAG, "Publishing... Topic: " + topic + " - Value: " + message);
        mqttHandler.publish(topic,message);
    }

    private void startUpdates() {
        // Start periodic updates for sensor and location data
        sensorHandler.startAccelerometerUpdates();
        // Request GPS updates if permission is granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            sensorHandler.startLocationUpdates();
        }
        else
            Log.w(TAG, "GPS permission disabled");
        sensorHandler.startRecording();
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
    }

    private void stopUpdates() {
        sensorHandler.stopAccelerometerUpdates();
        sensorHandler.stopLocationUpdates();
        sensorHandler.stopRecording();
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    @Override
    @SuppressLint("DefaultLocale")
    public void onAccelerometerUpdate(float[] accelerometerValues) {
        String xValue = String.format("%.2f", accelerometerValues[0]);
        String yValue = String.format("%.2f", accelerometerValues[1]);
        String zValue = String.format("%.2f", accelerometerValues[2] - 9.8);    // we are considering G-force
        xValueTextView.setText(xValue);
        yValueTextView.setText(yValue);
        zValueTextView.setText(zValue);
        JSONObject accelerometerObject = new JSONObject();
        try {
            accelerometerObject.put("busId", 0);
            accelerometerObject.put("accelerometerX", xValue);
            accelerometerObject.put("accelerometerY", yValue);
            accelerometerObject.put("accelerometerZ", zValue);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        publishMessage(topics[0], accelerometerObject.toString());

        // Write data to log file
        DataLogger.writeToLogFile(accelerometerObject, this);
    }

    @Override
    @SuppressLint("DefaultLocale")
    public void onLocationUpdate(Location location) {
        String latitude = String.format("%.2f", location.getLatitude());
        String longitude = String.format("%.2f", location.getLongitude());
        xCoordinateTextView.setText(latitude);
        yCoordinateTextView.setText(longitude);
        JSONObject locationObject = new JSONObject();

        try {
            locationObject.put("busId", 0);
            locationObject.put("latitude", latitude);
            locationObject.put("longitude", longitude);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        publishMessage(topics[2], locationObject.toString());

        // Write data to log file
        DataLogger.writeToLogFile(locationObject, this);
    }

    @Override
    @SuppressLint("DefaultLocale")
    public void onNoiseUpdate(double noiseLevel) {

        String noise = String.format("%.2f", noiseLevel);
        if (noise.equals("-Infinity")) {
            noise = "0";
        }
        noiseTextView.setText(noise);
        // Construct JSON object for data logging
        JSONObject noiseObject = new JSONObject();
        try {
            noiseObject.put("busId", 0);
            noiseObject.put("noise", noise);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        publishMessage(topics[1], noiseObject.toString());

        // Write data to log file
        DataLogger.writeToLogFile(noiseObject, this);

    }

    @Override
    public void connected() {
        Toast.makeText(this, "Connected to Broker", Toast.LENGTH_LONG).show();
        startButton.setEnabled(true);
    }

    @Override
    public void disconnected() { // not called
        stopUpdates();
        startButton.setEnabled(false);
        startButton.setEnabled(false);

    }
}
