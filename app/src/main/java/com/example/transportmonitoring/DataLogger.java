package com.example.transportmonitoring;

import android.content.Context;
import org.json.JSONObject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class DataLogger {
    private static final String FILE_NAME = "data_log.json";

    public static void writeToLogFile(JSONObject data, Context context) {
        File externalDir =  context.getExternalFilesDir(null);
        if (externalDir != null) {
            File file = new File(externalDir, FILE_NAME);

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
}
