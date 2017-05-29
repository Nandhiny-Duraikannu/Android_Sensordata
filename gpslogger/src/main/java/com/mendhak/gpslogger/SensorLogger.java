package com.mendhak.gpslogger;

/**
 * Created by nandh on 5/24/2017.
 */


import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Time;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.opencsv.CSVWriter;


import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class SensorLogger extends AppCompatActivity implements SensorEventListener {
    float[] history = new float[2];
    private Sensor accelerometer;
    private Sensor gyro;
    private Sensor magnetometer;
    SensorManager manager;
    Button button_stop;
    Button button_start;
    CheckBox checkbox_accelerometer;
    CheckBox checkbox_gyroscope;
    CheckBox checkbox_magnetometer;
    Button button_output;
    EditText edittext_sampling_rate;
    Time previousTime;
    boolean is_logging;
    boolean logged_once;
    int total_checked;
    int count_output_written = 0;
    int sampling_rate;
    String row_to_be_written[] = {"", "", "", "", "", "", "", "", ""};

  //  @Override
   // public void onCreateSensorLog(Bundle savedInstanceState) {
   public void onCreateSensorLog() {
//        super.onCreate(savedInstanceState);
       // setContentView(R.layout.activity_main);

        final SensorEventListener context_listener = this;
        is_logging = false;
        total_checked = 0;
        logged_once = false;

       Log.d(String.valueOf(is_logging), "nandhiny sensorlogger inside1:");
      // button_start = (Button) findViewById(R.id.btnStartStop);
       // button_stop = (Button) findViewById(R.id.s);
     /*   checkbox_accelerometer = (CheckBox) findViewById(R.id.checkbox_accelerometer);
        checkbox_gyroscope = (CheckBox) findViewById(R.id.checkbox_gyroscope);
        checkbox_magnetometer = (CheckBox) findViewById(R.id.checkbox_magnetometer);
        edittext_sampling_rate = (EditText) findViewById(R.id.edittext_sampling_rate);
        button_output = (Button)findViewById(R.id.button_view_output);*/

        manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
       Log.d(String.valueOf(is_logging), "nandhiny sensorlogger inside3:");
        accelerometer = manager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
        gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
      //  Log.i("new","in sensor log b4 onclick");
   //     button_start.setOnClickListener(new View.OnClickListener() {
     //       @Override
       //     public void onClick(View v) {
                logged_once = true;
                total_checked = 3;
                sampling_rate = 15;

       Log.d(String.valueOf(is_logging), "nandhiny sensorlogger inside4:");
                manager.registerListener(context_listener, accelerometer, sampling_rate);
                manager.registerListener(context_listener, gyro, sampling_rate);
                manager.registerListener(context_listener, magnetometer, sampling_rate);
                is_logging = true;
            //    Toast.makeText(SensorLogger.this, "Started Logging!", Toast.LENGTH_LONG).show();
    //    }
      //  });

       /* button_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                is_logging = false;
                checkbox_accelerometer.setChecked(false);
                checkbox_magnetometer.setChecked(false);
                checkbox_gyroscope.setChecked(false);
                //Toast.makeText(SensorLogger.this, "Stopped Logging!", Toast.LENGTH_LONG).show();
            }
        });*/

     /*   button_output.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!logged_once){
                    Toast.makeText(SensorLogger.this, "Please Log some data first!", Toast.LENGTH_LONG).show();
                    return;
                }
                File sdcard = Environment.getExternalStorageDirectory();
                File file = new File(sdcard + "/sensorlog.xml");
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(file),"text/xml");
                Intent intent2 = Intent.createChooser(intent, "Choose an application to open with :- ");
                startActivity(intent2);
            }
        });*/
       File sdcard = new File("/storage/sdcard0/Android/data/Logger/files");
     //  File file = new File(sdcard + "/sensorlog.xml");
      //  File sdcard = Environment.getExternalStorageDirectory();
        File file = new File(sdcard + "/sensorlog.csv");
       Log.d(String.valueOf(is_logging), "nandhiny sensorlogger insidefile:");

        if (file.exists())
        {
            file.delete();
        }

        String initial_row_to_be_written[] = {"Timestamp", "Accelerometer","","","Gyroscope","","","Magnetometer",""};
        writeToFile(initial_row_to_be_written);

        String next_row[] = {"","XChange","YChange","X","Y","Z","Azimuth Angle","Pitch Angle","Roll"};
        writeToFile(next_row);

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        boolean output_to_file = false;
        Time currentTime = new Time();
        currentTime.setToNow();

        if(previousTime == null){
            previousTime = currentTime;

        }
        long difference = TimeUnit.MILLISECONDS.toSeconds(currentTime.toMillis(true) - previousTime.toMillis(true));

        if (difference >= sampling_rate) {
            if (count_output_written < total_checked){
                output_to_file = true;
                count_output_written += 1;
            }
            else{
                previousTime = currentTime;
                count_output_written = 0;
            }
        }

        else{
            return;
        }

        String currentDateandTime = new SimpleDateFormat("yyyy-MM-dd;HH:mm:ss").format(Calendar.getInstance().getTime());

        if (output_to_file && is_logging && checkbox_magnetometer.isChecked() && event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            if (count_output_written == total_checked - 1){
                row_to_be_written[6] =  Double.toString(event.values[0]);
                row_to_be_written[7] = Double.toString(event.values[1]);
                row_to_be_written[8] = Double.toString(event.values[2]);
                row_to_be_written[0] = currentDateandTime;
                writeToFile(row_to_be_written);
                for (int i =0;i<9;i++){
                    row_to_be_written[i] = "";
                }
            }
            else{
                row_to_be_written[6] =  Double.toString(event.values[0]);
                row_to_be_written[7] = Double.toString(event.values[1]);
                row_to_be_written[8] = Double.toString(event.values[2]);
            }

        } else if (output_to_file && is_logging && checkbox_gyroscope.isChecked() && event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            if(count_output_written == total_checked - 1){
                row_to_be_written[3] =  Double.toString(event.values[0]);
                row_to_be_written[4] = Double.toString(event.values[1]);
                row_to_be_written[5] = Double.toString(event.values[2]);
                row_to_be_written[0] = currentDateandTime;
                writeToFile(row_to_be_written);
                for (int i =0;i<9;i++){
                    row_to_be_written[i] = "";
                }
            }
            else{
                row_to_be_written[3] =  Double.toString(event.values[0]);
                row_to_be_written[4] = Double.toString(event.values[1]);
                row_to_be_written[5] = Double.toString(event.values[2]);
            }

        } else if (output_to_file && is_logging && checkbox_accelerometer.isChecked() && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float xChange = history[0] - event.values[0];
            float yChange = history[1] - event.values[1];

            history[0] = event.values[0];
            history[1] = event.values[1];

            if(count_output_written == total_checked - 1){
                row_to_be_written[1] =  Double.toString(xChange);
                row_to_be_written[2] = Double.toString(yChange);
                row_to_be_written[0] = currentDateandTime;
                writeToFile(row_to_be_written);
                for (int i =0;i<9;i++){
                    row_to_be_written[i] = "";
                }
            }
            else{
                row_to_be_written[1] =  Double.toString(xChange);
                row_to_be_written[2] = Double.toString(yChange);
            }

        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // nothing to do here
    }

  /*  private void writeToFile(String row[]) {

        try
        {
          //  File sdcard = Environment.getExternalStorageDirectory();
            File sdcard = new File("/storage/sdcard0/Android/data/Logger/files");
            FileOutputStream fos = new FileOutputStream(sdcard + "/sensorlog.xml");
            FileOutputStream fileos= getApplicationContext().openFileOutput(String.valueOf(fos), Context.MODE_PRIVATE);
            XmlSerializer xmlSerializer = Xml.newSerializer();
            StringWriter writer = new StringWriter();
            xmlSerializer.setOutput(writer);
            xmlSerializer.startDocument("UTF-8", true);
            xmlSerializer.startTag(null, "value");

            xmlSerializer.text(String.valueOf(row));
            xmlSerializer.endTag(null, "value");

            xmlSerializer.endDocument();
            xmlSerializer.flush();
            String dataWrite = writer.toString();
            fileos.write(dataWrite.getBytes());
            fileos.close();

        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

    }*/
  private void writeToFile(String row[]) {

      CSVWriter writer = null;
      Log.d(String.valueOf(writer), "nandhiny sensorlogger inside writetofile:");
      try
      {
       //   File sdcard = Environment.getExternalStorageDirectory();
          File sdcard = new File("/storage/sdcard0/Android/data/Logger/files");

          writer = new CSVWriter(new FileWriter(sdcard + "/sensorlog.csv", true), ',');

          writer.writeNext(row);
          Log.d(String.valueOf(is_logging), "nandhiny sensorlogger inside:");
          writer.close();
      }
      catch (IOException e)
      {
          e.printStackTrace();
      }

  }
}
