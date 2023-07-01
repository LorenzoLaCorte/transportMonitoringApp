package com.example.transportmonitoring;

import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.content.pm.PackageManager;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.media.MediaRecorder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    // TODO: refactor everything: put any logic within its class

    // MQTT Related
    private static final String BROKER_URL = "tcp://192.168.112.1:1883"; // ipaddr to get the ip
    private static final String CLIENT_ID = "mqtt_1";
    private MqttHandler mqttHandler;
    private String[] topics = {"/tm/accelerometerX", "/tm/accelerometerY", "/tm/accelerometerZ", "/tm/noise", "/tm/positionLatitude", "/tm/positionLongitude"};

    // Sensors Related
    private final int acquisitionInterval = 10000; // millis
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
    // Logging TAGs
    private String MQTT_TAG = "MQTT" + "_" + this.getClass().getName();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // MQTT Setup
        mqttHandler = new MqttHandler();
        mqttHandler.connect(BROKER_URL,CLIENT_ID);

        for (String topic : topics) {
            subscribeToTopic(topic);
        }

        // Sensors Setup
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
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                1);

        // Request GPS updates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
        startRecording();
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
    protected void onDestroy() {        // Stop recording and release resources
        super.onDestroy();
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
        mqttHandler.disconnect();
    }

    private void publishMessage(String topic, String message){
        Log.d(MQTT_TAG, "Publishing... Topic: " + topic + " - Value: " + message);
        mqttHandler.publish(topic,message);
    }
    private void subscribeToTopic(String topic){
        Log.d(MQTT_TAG, "Subscribing to topic "+ topic);
        mqttHandler.subscribe(topic);
    }

    void startRecording() {
        File audioDir = new File(this.getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "AudioMemos");
        audioDir.mkdirs();
        String audioDirPath = audioDir.getAbsolutePath();
        Date currentTime = Calendar.getInstance().getTime(); // current time
        String curTimeStr = currentTime.toString().replace(" ", "_");
        recordingFile = new File(audioDirPath + "/" + curTimeStr + ".m4a");

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(recordingFile);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            Log.e("Media Recorder", "prepare() failed");
        }
        mediaRecorder.start();
        double audioLevel = mediaRecorder.getMaxAmplitude();
        Log.d("Media Recorder", "Registered Audio Level" + audioLevel);
    }
    private double getNoiseLevel() {
        int amplitude = mediaRecorder.getMaxAmplitude();
        double noiseLevel = 20 * Math.log10(amplitude);
        return noiseLevel;
    }

    private void writeToLogFile(JSONObject data) {
        String fileName = "data_log.json"; // Name of the log file

        File externalDir = getExternalFilesDir(null);
        if (externalDir != null) {
            File file = new File(externalDir, fileName);

            try {
                FileWriter fileWriter = new FileWriter(file, true); // "true" for appending data
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                bufferedWriter.write(data.toString());
                bufferedWriter.newLine();
                bufferedWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private void handleDataAcquisition() {
        // Print Values
        xValueTextView.setText(String.format("%.2f", accelerometerValues[0]));
        yValueTextView.setText(String.format("%.2f", accelerometerValues[1]));
        zValueTextView.setText(String.format("%.2f", accelerometerValues[2]));
        noiseTextView.setText(String.format("%.2f", noiseLevel));
        xCoordinateTextView.setText(String.format("%.2f", latitude));
        yCoordinateTextView.setText(String.format("%.2f", longitude));

        // Send data over MQTT channel
        publishMessage(topics[0], String.format("%.2f", accelerometerValues[0]));
        publishMessage(topics[1], String.format("%.2f", accelerometerValues[1]));
        publishMessage(topics[2], String.format("%.2f", accelerometerValues[2]));
        publishMessage(topics[3], String.format("%.2f", noiseLevel));
        publishMessage(topics[4], String.format("%.2f", latitude));
        publishMessage(topics[5], String.format("%.2f", longitude));

        // Construct JSON object
        JSONObject jsonData = new JSONObject();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String currentDateTime = sdf.format(new Date());
        try {
            jsonData.put("dateTime", currentDateTime);
            jsonData.put("xValue", String.format("%.2f", accelerometerValues[0]));
            jsonData.put("xValue", String.format("%.2f", accelerometerValues[0]));
            jsonData.put("yValue", String.format("%.2f", accelerometerValues[1]));
            jsonData.put("zValue", String.format("%.2f", accelerometerValues[2]));
            jsonData.put("noise", String.format("%.2f", noiseLevel));
            jsonData.put("xCoordinate", String.format("%.2f", latitude));
            jsonData.put("yCoordinate", String.format("%.2f", longitude));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Write data to file
        writeToLogFile(jsonData);
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
