package com.example.transportmonitoring;

import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttHandler implements MqttCallback {
    private static final String MQTT_TAG = "MQTT_HANDLER";
    private MqttAsyncClient client;
    private static final String BROKER_URL = "tcp://192.168.178.188:1883"; // ipaddr to get the ip
    private static final String CLIENT_ID = "mqtt_1";

    public MqttHandler(String... topics){
        this.connect();
        for(String t: topics) {
            Log.d(MQTT_TAG, "Subscribing to topic " + t);
            subscribe(t);
        }
    }
    public void connect() {
        try {
            // Set up the persistence layer
            MemoryPersistence persistence = new MemoryPersistence();

            // Initialize the MQTT client
            client = new MqttAsyncClient(BROKER_URL, CLIENT_ID, persistence);

            // Set up the connection options
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true);
            client.setCallback(this);

            // Connect to the broker
            client.connect(connectOptions).waitForCompletion(30000);
            Log.d("MQTT", "Connected");
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
    private void subscribeToTopic(String topic){
        this.subscribe(topic);
    }

    public void disconnect() {
        try {
            client.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void publish(String topic, String message) {
        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            client.publish(topic, mqttMessage);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void subscribe(String topic) {
        try {
            client.subscribe(topic, 2);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.d(MQTT_TAG, "connectionLost");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message){
        Log.d("MQTT", "messageArrived");
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        Log.d("MQTT", "deliveryComplete");
    }
}