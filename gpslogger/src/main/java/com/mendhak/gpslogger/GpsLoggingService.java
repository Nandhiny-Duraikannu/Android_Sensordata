/*
 * Copyright (C) 2016 mendhak
 *
 * This file is part of GPSLogger for Android.
 *
 * GPSLogger for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * GPSLogger for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GPSLogger for Android.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mendhak.gpslogger;

import android.annotation.TargetApi;
import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.*;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import android.hardware.SensorManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.mendhak.gpslogger.common.*;
import com.mendhak.gpslogger.common.events.CommandEvents;
import com.mendhak.gpslogger.common.events.ProfileEvents;
import com.mendhak.gpslogger.common.events.ServiceEvents;
import com.mendhak.gpslogger.common.slf4j.Logs;
import com.mendhak.gpslogger.common.slf4j.SessionLogcatAppender;
import com.mendhak.gpslogger.loggers.FileLoggerFactory;
import com.mendhak.gpslogger.loggers.Files;
import com.mendhak.gpslogger.loggers.nmea.NmeaFileLogger;
import com.mendhak.gpslogger.senders.AlarmReceiver;
import com.mendhak.gpslogger.senders.FileSenderFactory;
import de.greenrobot.event.EventBus;
import android.support.v7.app.AppCompatActivity;
import org.slf4j.Logger;
import com.mendhak.gpslogger.SensorLogger;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;



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




public class GpsLoggingService extends Service implements SensorEventListener {
    private static NotificationManager notificationManager;
    private static int NOTIFICATION_ID = 8675309;
    private final IBinder binder = new GpsLoggingBinder();
    AlarmManager nextPointAlarmManager;
    private NotificationCompat.Builder nfc = null;
    float[] history = new float[2];
    Sensor accelerometer;
    Sensor gyro;
    Sensor magnetometer;
    SensorManager manager;
  //  Button button_stop;
  //  Button button_start;
    boolean checkbox_accelerometer;
    boolean checkbox_gyroscope;
    boolean checkbox_magnetometer;
    Button button_output;
   //EditText samplerate ;
    Time previousTime;
    boolean is_logging;
    boolean logged_once;
    int total_checked;
    int count_output_written = 0;
    int sampling_rate;
    int  sample;
    String row_to_be_written[] = {"", "", "", "", "", "", "", "", ""};

    private static final Logger LOG = Logs.of(GpsLoggingService.class);

    // ---------------------------------------------------
    // Helpers and managers
    // ---------------------------------------------------
    private PreferenceHelper preferenceHelper = PreferenceHelper.getInstance();
    private Session session = Session.getInstance();
    protected LocationManager gpsLocationManager;
    private LocationManager passiveLocationManager;
    private LocationManager towerLocationManager;
    private GeneralLocationListener gpsLocationListener;
    private GeneralLocationListener towerLocationListener;
    private GeneralLocationListener passiveLocationListener;
    private Intent alarmIntent;
    private Handler handler = new Handler();

    PendingIntent activityRecognitionPendingIntent;
    GoogleApiClient googleApiClient;
    // ---------------------------------------------------


    @Override
    public IBinder onBind(Intent arg0) {
        return binder;
    }

    @Override
    public void onCreate() {

        nextPointAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        registerEventBus();

    }

    private void requestActivityRecognitionUpdates() {
        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {

                    @Override
                    public void onConnectionSuspended(int arg) {
                    }

                    @Override
                    public void onConnected(Bundle arg0) {
                        try {
                            LOG.debug("Requesting activity recognition updates");
                            Intent intent = new Intent(getApplicationContext(), GpsLoggingService.class);
                            activityRecognitionPendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                            ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(googleApiClient, preferenceHelper.getMinimumLoggingInterval() * 1000, activityRecognitionPendingIntent);
                        }
                        catch(Throwable t){
                            LOG.warn(SessionLogcatAppender.MARKER_INTERNAL, "Can't connect to activity recognition service", t);
                        }

                    }

                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult arg0) {

                    }
                });

        googleApiClient = builder.build();

        googleApiClient.connect();
    }

    private void stopActivityRecognitionUpdates(){
        try{
            LOG.debug("Stopping activity recognition updates");
            if(googleApiClient != null && googleApiClient.isConnected()){
                ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(googleApiClient, activityRecognitionPendingIntent);
                googleApiClient.disconnect();

            }
        }
        catch(Throwable t){
            LOG.warn(SessionLogcatAppender.MARKER_INTERNAL, "Tried to stop activity recognition updates", t);

        }

    }

    private void registerEventBus() {
        EventBus.getDefault().registerSticky(this);
    }

    private void unregisterEventBus(){
        try {
            EventBus.getDefault().unregister(this);
        } catch (Throwable t){
            //this may crash if registration did not go through. just be safe
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        handleIntent(intent);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        LOG.warn(SessionLogcatAppender.MARKER_INTERNAL, "GpsLoggingService is being destroyed by Android OS.");
        unregisterEventBus();
        removeNotification();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        LOG.error("Android is low on memory!");

        super.onLowMemory();
    }

    private void handleIntent(Intent intent) {

        ActivityRecognitionResult arr = ActivityRecognitionResult.extractResult(intent);
        if(arr != null){
            EventBus.getDefault().post(new ServiceEvents.ActivityRecognitionEvent(arr));
            return;
        }

        if (intent != null) {
            Bundle bundle = intent.getExtras();

            if (bundle != null) {
                boolean needToStartGpsManager = false;

                if (bundle.getBoolean(IntentConstants.IMMEDIATE_START)) {
                    LOG.info("Intent received - Start Logging Now");

                    EventBus.getDefault().postSticky(new CommandEvents.RequestStartStop(true));
                }

                if (bundle.getBoolean(IntentConstants.IMMEDIATE_STOP)) {
                    LOG.info("Intent received - Stop logging now");
                    EventBus.getDefault().postSticky(new CommandEvents.RequestStartStop(false));
                }

                if (bundle.getBoolean(IntentConstants.AUTOSEND_NOW)) {
                    LOG.info("Intent received - Send Email Now");
                    EventBus.getDefault().postSticky(new CommandEvents.AutoSend(null));
                }

                if (bundle.getBoolean(IntentConstants.GET_NEXT_POINT)) {
                    LOG.info("Intent received - Get Next Point");
                    needToStartGpsManager = true;
                }

                if (bundle.getString(IntentConstants.SET_DESCRIPTION) != null) {
                    LOG.info("Intent received - Set Next Point Description: " + bundle.getString(IntentConstants.SET_DESCRIPTION));
                    EventBus.getDefault().post(new CommandEvents.Annotate(bundle.getString(IntentConstants.SET_DESCRIPTION)));
                }

                if(bundle.getString(IntentConstants.SWITCH_PROFILE) != null){
                    LOG.info("Intent received - switch profile: " + bundle.getString(IntentConstants.SWITCH_PROFILE));
                    EventBus.getDefault().post(new ProfileEvents.SwitchToProfile(bundle.getString(IntentConstants.SWITCH_PROFILE)));
                }

                if (bundle.get(IntentConstants.PREFER_CELLTOWER) != null) {
                    boolean preferCellTower = bundle.getBoolean(IntentConstants.PREFER_CELLTOWER);
                    LOG.debug("Intent received - Set Prefer Cell Tower: " + String.valueOf(preferCellTower));

                    if(preferCellTower){
                        preferenceHelper.setChosenListeners(0);
                    } else {
                        preferenceHelper.setChosenListeners(1,2);
                    }

                    needToStartGpsManager = true;
                }

                if (bundle.get(IntentConstants.TIME_BEFORE_LOGGING) != null) {
                    int timeBeforeLogging = bundle.getInt(IntentConstants.TIME_BEFORE_LOGGING);
                    LOG.debug("Intent received - logging interval: " + String.valueOf(timeBeforeLogging));
                    preferenceHelper.setMinimumLoggingInterval(timeBeforeLogging);
                    needToStartGpsManager = true;
                }

                if (bundle.get(IntentConstants.DISTANCE_BEFORE_LOGGING) != null) {
                    int distanceBeforeLogging = bundle.getInt(IntentConstants.DISTANCE_BEFORE_LOGGING);
                    LOG.debug("Intent received - Set Distance Before Logging: " + String.valueOf(distanceBeforeLogging));
                    preferenceHelper.setMinimumDistanceInMeters(distanceBeforeLogging);
                    needToStartGpsManager = true;
                }

                if (bundle.get(IntentConstants.GPS_ON_BETWEEN_FIX) != null) {
                    boolean keepBetweenFix = bundle.getBoolean(IntentConstants.GPS_ON_BETWEEN_FIX);
                    LOG.debug("Intent received - Set Keep Between Fix: " + String.valueOf(keepBetweenFix));
                    preferenceHelper.setShouldKeepGPSOnBetweenFixes(keepBetweenFix);
                    needToStartGpsManager = true;
                }

                if (bundle.get(IntentConstants.RETRY_TIME) != null) {
                    int retryTime = bundle.getInt(IntentConstants.RETRY_TIME);
                    LOG.debug("Intent received - Set duration to match accuracy: " + String.valueOf(retryTime));
                    preferenceHelper.setLoggingRetryPeriod(retryTime);
                    needToStartGpsManager = true;
                }

                if (bundle.get(IntentConstants.ABSOLUTE_TIMEOUT) != null) {
                    int absoluteTimeout = bundle.getInt(IntentConstants.ABSOLUTE_TIMEOUT);
                    LOG.debug("Intent received - Set absolute timeout: " + String.valueOf(absoluteTimeout));
                    preferenceHelper.setAbsoluteTimeoutForAcquiringPosition(absoluteTimeout);
                    needToStartGpsManager = true;
                }

                if(bundle.get(IntentConstants.LOG_ONCE) != null){
                    boolean logOnceIntent = bundle.getBoolean(IntentConstants.LOG_ONCE);
                    LOG.debug("Intent received - Log Once: " + String.valueOf(logOnceIntent));
                    needToStartGpsManager = false;
                    logOnce();
                }

                try {
                    if(bundle.get(Intent.EXTRA_ALARM_COUNT) != "0"){
                        needToStartGpsManager = true;
                    }
                }
                catch (Throwable t){
                    LOG.warn(SessionLogcatAppender.MARKER_INTERNAL, "Received a weird EXTRA_ALARM_COUNT value. Cannot continue.");
                    needToStartGpsManager = false;
                }


                if (needToStartGpsManager && session.isStarted()) {
                    startGpsManager();
                }
            }
        } else {
            // A null intent is passed in if the service has been killed and restarted.
            LOG.debug("Service restarted with null intent. Were we logging previously - " + session.isStarted());
            if(session.isStarted()){
                startLogging();
            }

        }
    }

    /**
     * Sets up the auto email timers based on user preferences.
     */
    @TargetApi(23)
    public void setupAutoSendTimers() {
        LOG.debug("Setting up autosend timers. Auto Send Enabled - " + String.valueOf(preferenceHelper.isAutoSendEnabled())
                + ", Auto Send Delay - " + String.valueOf(session.getAutoSendDelay()));

        if (preferenceHelper.isAutoSendEnabled() && session.getAutoSendDelay() > 0) {
            long triggerTime = System.currentTimeMillis() + (long) (session.getAutoSendDelay() * 60 * 1000);

            alarmIntent = new Intent(this, AlarmReceiver.class);
            cancelAlarm();

            PendingIntent sender = PendingIntent.getBroadcast(this, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if(Systems.isDozing(this)) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, sender);
            }
            else {
                am.set(AlarmManager.RTC_WAKEUP, triggerTime, sender);
            }
            LOG.debug("Autosend alarm has been set");

        } else {
            if (alarmIntent != null) {
                LOG.debug("alarmIntent was null, canceling alarm");
                cancelAlarm();
            }
        }
    }


    public void logOnce() {
        session.setSinglePointMode(true);

        if (session.isStarted()) {
            startGpsManager();
        } else {
            startLogging();
        }
    }

    private void cancelAlarm() {
        if (alarmIntent != null) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            PendingIntent sender = PendingIntent.getBroadcast(this, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            am.cancel(sender);
        }
    }

    /**
     * Method to be called if user has chosen to auto email log files when he
     * stops logging
     */
    private void autoSendLogFileOnStop() {
        if (preferenceHelper.isAutoSendEnabled() && preferenceHelper.shouldAutoSendOnStopLogging()) {
            autoSendLogFile(null);
        }
    }

    /**
     * Calls the Auto Senders which process the files and send it.
     */
    private void autoSendLogFile(@Nullable String formattedFileName) {

        LOG.debug("Filename: " + formattedFileName);

        if ( !Strings.isNullOrEmpty(formattedFileName) || !Strings.isNullOrEmpty(Strings.getFormattedFileName()) ) {
            String fileToSend = Strings.isNullOrEmpty(formattedFileName) ? Strings.getFormattedFileName() : formattedFileName;
            FileSenderFactory.autoSendFiles(fileToSend);
            setupAutoSendTimers();
        }
    }

    private void resetAutoSendTimersIfNecessary() {

        if (session.getAutoSendDelay() != preferenceHelper.getAutoSendInterval()) {
            session.setAutoSendDelay(preferenceHelper.getAutoSendInterval());
            setupAutoSendTimers();
        }
    }

    /**
     * Resets the form, resets file name if required, reobtains preferences
     */
    protected void startLogging() {
        LOG.debug(".");

        session.setAddNewTrackSegment(true);



        try {
            startForeground(NOTIFICATION_ID, new Notification());
        } catch (Exception ex) {
            LOG.error("Could not start GPSLoggingService in foreground. ", ex);
        }

        session.setStarted(true);

        resetAutoSendTimersIfNecessary();
        showNotification();
        setupAutoSendTimers();
        resetCurrentFileName(true);
        notifyClientStarted();
        startPassiveManager();
        startGpsManager();

        requestActivityRecognitionUpdates();

    }


    private void onCreateSensorLogStart() {


        final SensorEventListener context_listener = this;

        is_logging = false;

        total_checked = 0;

        logged_once = false;

        manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

       accelerometer = manager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
        gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);


                logged_once = true;
                total_checked = 3;
                sampling_rate = 10;

               manager.registerListener(context_listener, accelerometer, sampling_rate);
                manager.registerListener(context_listener, gyro, sampling_rate);
                manager.registerListener(context_listener, magnetometer, sampling_rate);
                is_logging = true;
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
        writeToFileSensor(initial_row_to_be_written);

        String next_row[] = {"","XChange","YChange","X","Y","Z","Azimuth Angle","Pitch Angle","Roll"};
       writeToFileSensor(next_row);

        //    Toast.makeText(SensorLogger.this, "Started Logging!", Toast.LENGTH_LONG).show();

    }

   /* protected void onResume() {
        super.onResume();
    }*/
    /**
     * Asks the main service client to clear its form.
     */
    private void notifyClientStarted() {
        LOG.info(getString(R.string.started));
        EventBus.getDefault().post(new ServiceEvents.LoggingStatus(true));
    }

    /**
     * Stops logging, removes notification, stops GPS manager, stops email timer
     */
    public void stopLogging() {
        LOG.debug(".");
        session.setAddNewTrackSegment(true);
        session.setTotalTravelled(0);
        session.setPreviousLocationInfo(null);
        session.setStarted(false);
        session.setUserStillSinceTimeStamp(0);
        session.setLatestTimeStamp(0);
        stopAbsoluteTimer();
        // Email log file before setting location info to null
        autoSendLogFileOnStop();
        cancelAlarm();
        session.setCurrentLocationInfo(null);
        session.setSinglePointMode(false);
        stopForeground(true);

        removeNotification();
        stopAlarm();
        stopGpsManager();
        stopPassiveManager();
        stopActivityRecognitionUpdates();
        notifyClientStopped();
        session.setCurrentFileName("");
        session.setCurrentFormattedFileName("");
    }

    /**
     * Hides the notification icon in the status bar if it's visible.
     */
    private void removeNotification() {
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    /**
     * Shows a notification icon in the status bar for GPS Logger
     */
    private void showNotification() {

        Intent stopLoggingIntent = new Intent(this, GpsLoggingService.class);
        stopLoggingIntent.setAction("NotificationButton_STOP");
        stopLoggingIntent.putExtra(IntentConstants.IMMEDIATE_STOP, true);
        PendingIntent piStop = PendingIntent.getService(this, 0, stopLoggingIntent, 0);

        Intent annotateIntent = new Intent(this, NotificationAnnotationActivity.class);
        annotateIntent.setAction("com.mendhak.gpslogger.NOTIFICATION_BUTTON");
        PendingIntent piAnnotate = PendingIntent.getActivity(this,0, annotateIntent,0);

        // What happens when the notification item is clicked
        Intent contentIntent = new Intent(this, GpsMainActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntent(contentIntent);

        PendingIntent pending = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        String contentText = getString(R.string.gpslogger_still_running);
        long notificationTime = System.currentTimeMillis();

        if (session.hasValidLocation()) {
            contentText =  Strings.getFormattedLatitude(session.getCurrentLatitude()) + ", "
                     + Strings.getFormattedLongitude(session.getCurrentLongitude());

            notificationTime = session.getCurrentLocationInfo().getTime();
        }

        if (nfc == null) {
            nfc = new NotificationCompat.Builder(getApplicationContext())
                    .setSmallIcon(R.drawable.notification)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.gpsloggericon3))
                    .setPriority( preferenceHelper.shouldHideNotificationFromStatusBar() ? NotificationCompat.PRIORITY_MIN : NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setVisibility(NotificationCompat.VISIBILITY_SECRET) //This hides the notification from lock screen
                    .setContentTitle(contentText)
                    .setOngoing(true)
                    .setContentIntent(pending);

            if(!preferenceHelper.shouldHideNotificationButtons()){
                nfc.addAction(R.drawable.annotate2, getString(R.string.menu_annotate), piAnnotate)
                        .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.shortcut_stop), piStop);
            }
        }



        nfc.setContentTitle(contentText);
        nfc.setContentText(getString(R.string.app_name));
        nfc.setWhen(notificationTime);

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, nfc.build());
    }

    @SuppressWarnings("ResourceType")
    private void startPassiveManager() {
        if(preferenceHelper.getChosenListeners().contains(LocationManager.PASSIVE_PROVIDER)){
            LOG.debug("Starting passive location listener");
            if(passiveLocationListener== null){
                passiveLocationListener = new GeneralLocationListener(this, "PASSIVE");
            }
            passiveLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            passiveLocationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1000, 0, passiveLocationListener);
        }
    }

    /**
     * Starts the location manager. There are two location managers - GPS and
     * Cell Tower. This code determines which manager to request updates from
     * based on user preference and whichever is enabled. If GPS is enabled on
     * the phone, that is used. But if the user has also specified that they
     * prefer cell towers, then cell towers are used. If neither is enabled,
     * then nothing is requested.
     */
    @SuppressWarnings("ResourceType")
    private void startGpsManager() {
        //nandhiny remove
        final SensorEventListener context_listener = this;

        is_logging = false;

        total_checked = 0;

        logged_once = false;
//nandhiny remove
        //If the user has been still for more than the minimum seconds
        if(userHasBeenStillForTooLong()) {
            LOG.info("No movement detected in the past interval, will not log");
            setAlarmForNextPoint();
            return;
        }

        if (gpsLocationListener == null) {
            gpsLocationListener = new GeneralLocationListener(this, "GPS");
        }

        if (towerLocationListener == null) {
            towerLocationListener = new GeneralLocationListener(this, "CELL");
        }

        gpsLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        towerLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        //nandhiny remove
        manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        accelerometer = manager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
        gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        //nandhiny-start
        is_logging = true;
        //nandhiny-end
        Log.i("new","in sensor log b4 onclick");

       //  samplerate =  (EditText)findViewById(R.id.samplerate);
    /*    LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.fragment_simple_view, null);
            sample = layout.findViewById(R.id.samplerate);
        LOG.debug( "1nandhiny sensorlogger inside3:"+sample);*/

        logged_once = true;
        total_checked = 3;
        sampling_rate = 10;


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
        writeToFileSensor(initial_row_to_be_written);

        String next_row[] = {"","XChange","YChange","X","Y","Z","Azimuth Angle","Pitch Angle","Roll"};
        writeToFileSensor(next_row);
        LOG.debug( "1nandhiny sensorlogger inside4:");

//nandhiny remove

        checkTowerAndGpsStatus();

        if (session.isGpsEnabled() && preferenceHelper.getChosenListeners().contains(LocationManager.GPS_PROVIDER)) {
            LOG.info("Requesting GPS location updates");
            // gps satellite based
            gpsLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsLocationListener);
            gpsLocationManager.addGpsStatusListener(gpsLocationListener);
            gpsLocationManager.addNmeaListener(gpsLocationListener);
            LOG.debug("nandhiny call listeners");
            manager.registerListener(context_listener, accelerometer, sampling_rate);
            manager.registerListener(context_listener, gyro, sampling_rate);
            manager.registerListener(context_listener, magnetometer, sampling_rate);
            is_logging = true;
            session.setUsingGps(true);

            startAbsoluteTimer();


            LOG.debug("nandhiny b4 sensor call");
         //  onCreateSensorLogStart();
            LOG.debug( "1nandhiny manager test:");



        }

        if (session.isTowerEnabled() &&  ( preferenceHelper.getChosenListeners().contains(LocationManager.NETWORK_PROVIDER)  || !session.isGpsEnabled() ) ) {
            LOG.info("Requesting cell and wifi location updates");
            session.setUsingGps(false);
            // Cell tower and wifi based
            towerLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, towerLocationListener);

            startAbsoluteTimer();
        }

        if(!session.isTowerEnabled() && !session.isGpsEnabled()) {
            LOG.error("No provider available!");
            session.setUsingGps(false);
            LOG.error(getString(R.string.gpsprovider_unavailable));
            stopLogging();
            setLocationServiceUnavailable();
            return;
        }

        EventBus.getDefault().post(new ServiceEvents.WaitingForLocation(true));
        session.setWaitingForLocation(true);
    }

    private boolean userHasBeenStillForTooLong() {
        return !session.hasDescription() && !session.isSinglePointMode() &&
                (session.getUserStillSinceTimeStamp() > 0 && (System.currentTimeMillis() - session.getUserStillSinceTimeStamp()) > (preferenceHelper.getMinimumLoggingInterval() * 1000));
    }

    private void startAbsoluteTimer() {
        if (preferenceHelper.getAbsoluteTimeoutForAcquiringPosition() >= 1) {
            handler.postDelayed(stopManagerRunnable, preferenceHelper.getAbsoluteTimeoutForAcquiringPosition() * 1000);

        }
    }

    private Runnable stopManagerRunnable = new Runnable() {
        @Override
        public void run() {
            LOG.warn("Absolute timeout reached, giving up on this point");
            stopManagerAndResetAlarm();
        }
    };

    private void stopAbsoluteTimer() {
        handler.removeCallbacks(stopManagerRunnable);
    }

    /**
     * This method is called periodically to determine whether the cell tower /
     * gps providers have been enabled, and sets class level variables to those
     * values.
     */
    private void checkTowerAndGpsStatus() {
        session.setTowerEnabled(towerLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        session.setGpsEnabled(gpsLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER));
    }

    /**
     * Stops the location managers
     */
    @SuppressWarnings("ResourceType")
    private void stopGpsManager() {

        if (towerLocationListener != null) {
            LOG.debug("Removing towerLocationManager updates");
            towerLocationManager.removeUpdates(towerLocationListener);
        }

        if (gpsLocationListener != null) {
            LOG.debug("Removing gpsLocationManager updates");
            gpsLocationManager.removeUpdates(gpsLocationListener);
            gpsLocationManager.removeGpsStatusListener(gpsLocationListener);
        }

        session.setWaitingForLocation(false);
        EventBus.getDefault().post(new ServiceEvents.WaitingForLocation(false));

    }

    @SuppressWarnings("ResourceType")
    private void stopPassiveManager(){
        if(passiveLocationManager!=null){
            LOG.debug("Removing passiveLocationManager updates");
            passiveLocationManager.removeUpdates(passiveLocationListener);
        }
    }

    /**
     * Sets the current file name based on user preference.
     */
    private void resetCurrentFileName(boolean newLogEachStart) {

        String oldFileName = session.getCurrentFormattedFileName();

        /* Update the file name, if required. (New day, Re-start service) */
        if (preferenceHelper.shouldCreateCustomFile()) {
            if(Strings.isNullOrEmpty(Strings.getFormattedFileName())){
                session.setCurrentFileName(preferenceHelper.getCustomFileName());
            }

            LOG.debug("Should change file name dynamically: " + preferenceHelper.shouldChangeFileNameDynamically());

            if(!preferenceHelper.shouldChangeFileNameDynamically()){
                session.setCurrentFileName(Strings.getFormattedFileName());
            }

        } else if (preferenceHelper.shouldCreateNewFileOnceADay()) {
            // 20100114.gpx
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            session.setCurrentFileName(sdf.format(new Date()));
        } else if (newLogEachStart) {
            // 20100114183329.gpx
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            session.setCurrentFileName(sdf.format(new Date()));
        }

        if(!Strings.isNullOrEmpty(oldFileName)
                && !oldFileName.equalsIgnoreCase(Strings.getFormattedFileName())
                && session.isStarted()){
            LOG.debug("New file name, should auto upload the old one");
            EventBus.getDefault().post(new CommandEvents.AutoSend(oldFileName));
        }

        session.setCurrentFormattedFileName(Strings.getFormattedFileName());

        LOG.info("Filename: " + Strings.getFormattedFileName());
        EventBus.getDefault().post(new ServiceEvents.FileNamed(Strings.getFormattedFileName()));

    }



    void setLocationServiceUnavailable(){
        EventBus.getDefault().post(new ServiceEvents.LocationServicesUnavailable());
    }


    /**
     * Notifies main form that logging has stopped
     */
    void notifyClientStopped() {
        LOG.info(getString(R.string.stopped));
        EventBus.getDefault().post(new ServiceEvents.LoggingStatus(false));
    }

    /**
     * Stops location manager, then starts it.
     */
    void restartGpsManagers() {
        LOG.debug("Restarting location managers");
        stopGpsManager();
        startGpsManager();
    }

    /**
     * This event is raised when the GeneralLocationListener has a new location.
     * This method in turn updates notification, writes to file, reobtains
     * preferences, notifies main service client and resets location managers.
     *
     * @param loc Location object
     */
    void onLocationChanged(Location loc) {
        if (!session.isStarted()) {
            LOG.debug("onLocationChanged called, but session.isStarted is false");
            stopLogging();
            return;
        }

        long currentTimeStamp = System.currentTimeMillis();

        LOG.debug("Has description? " + session.hasDescription() + ", Single point? " + session.isSinglePointMode() + ", Last timestamp: " + session.getLatestTimeStamp());

        // Don't log a point until the user-defined time has elapsed
        // However, if user has set an annotation, just log the point, disregard any filters
        if (!session.hasDescription() && !session.isSinglePointMode() && (currentTimeStamp - session.getLatestTimeStamp()) < (preferenceHelper.getMinimumLoggingInterval() * 1000)) {
            return;
        }

        //Don't log a point if user has been still
        // However, if user has set an annotation, just log the point, disregard any filters
        if(userHasBeenStillForTooLong()) {
            LOG.info("Received location but the user hasn't moved, ignoring");
            return;
        }

        if(!isFromValidListener(loc)){
            return;
        }


        boolean isPassiveLocation = loc.getExtras().getBoolean("PASSIVE");
        
        //check if we change of day and then write the last position of yesterday as the first position of today
        if (preferenceHelper.shouldCreateNewFileOnceADay()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            String today = sdf.format(new Date());
            if (!today.equals(Strings.getFormattedFileName())) {
                resetCurrentFileName(false);
            }
        }
        

        //Check if a ridiculous distance has been travelled since previous point - could be a bad GPS jump
        if(session.getCurrentLocationInfo() != null){
            double distanceTravelled = Maths.calculateDistance(loc.getLatitude(), loc.getLongitude(), session.getCurrentLocationInfo().getLatitude(), session.getCurrentLocationInfo().getLongitude());
            long timeDifference = Math.abs(loc.getTime() - session.getCurrentLocationInfo().getTime())/1000;
            double speed = distanceTravelled/timeDifference;
            if(speed > 357){ //357 m/s ~=  1285 km/h
                LOG.warn(String.format("Very large jump detected - %d meters in %d sec - discarding point", (long)distanceTravelled, timeDifference));
                return;
            }
        }

        // Don't do anything until the user-defined accuracy is reached
        // However, if user has set an annotation, just log the point, disregard any filters
        if (!session.hasDescription() &&  preferenceHelper.getMinimumAccuracy() > 0) {


            if(!loc.hasAccuracy() || loc.getAccuracy() == 0){
                return;
            }

            //Don't apply the retry interval to passive locations
            if (!isPassiveLocation && preferenceHelper.getMinimumAccuracy() < Math.abs(loc.getAccuracy())) {

                if(session.getFirstRetryTimeStamp() == 0){
                    session.setFirstRetryTimeStamp(System.currentTimeMillis());
                }

                if (currentTimeStamp - session.getFirstRetryTimeStamp() <= preferenceHelper.getLoggingRetryPeriod() * 1000) {
                    LOG.warn("Only accuracy of " + String.valueOf(loc.getAccuracy()) + " m. Point discarded." + getString(R.string.inaccurate_point_discarded));
                    //return and keep trying
                    return;
                }

                if (currentTimeStamp - session.getFirstRetryTimeStamp() > preferenceHelper.getLoggingRetryPeriod() * 1000) {
                    LOG.warn("Only accuracy of " + String.valueOf(loc.getAccuracy()) + " m and timeout reached." + getString(R.string.inaccurate_point_discarded));
                    //Give up for now
                    stopManagerAndResetAlarm();

                    //reset timestamp for next time.
                    session.setFirstRetryTimeStamp(0);
                    return;
                }

                //Success, reset timestamp for next time.
                session.setFirstRetryTimeStamp(0);
            }
        }

        //Don't do anything until the user-defined distance has been traversed
        // However, if user has set an annotation, just log the point, disregard any filters
        if (!session.hasDescription() && !session.isSinglePointMode() && preferenceHelper.getMinimumDistanceInterval() > 0 && session.hasValidLocation()) {

            double distanceTraveled = Maths.calculateDistance(loc.getLatitude(), loc.getLongitude(),
                    session.getCurrentLatitude(), session.getCurrentLongitude());

            if (preferenceHelper.getMinimumDistanceInterval() > distanceTraveled) {
                LOG.warn(String.format(getString(R.string.not_enough_distance_traveled), String.valueOf(Math.floor(distanceTraveled))) + ", point discarded");
                stopManagerAndResetAlarm();
                return;
            }
        }


        LOG.info(SessionLogcatAppender.MARKER_LOCATION, String.valueOf(loc.getLatitude()) + "," + String.valueOf(loc.getLongitude()));
        loc = Locations.getLocationWithAdjustedAltitude(loc, preferenceHelper);
        resetCurrentFileName(false);
        session.setLatestTimeStamp(System.currentTimeMillis());
        session.setFirstRetryTimeStamp(0);
        session.setCurrentLocationInfo(loc);
        setDistanceTraveled(loc);
        showNotification();

        if(isPassiveLocation){
            LOG.debug("Logging passive location to file");
        }
     //   LOG.debug("nandhiny b4 sensor call");
      //  onCreateSensorLogStart();
        writeToFile(loc);
        resetAutoSendTimersIfNecessary();
        stopManagerAndResetAlarm();

        EventBus.getDefault().post(new ServiceEvents.LocationUpdate(loc));

        if (session.isSinglePointMode()) {
            LOG.debug("Single point mode - stopping now");
            stopLogging();
        }
    }

    private boolean isFromValidListener(Location loc) {

        if(!preferenceHelper.getChosenListeners().contains(LocationManager.GPS_PROVIDER) && !preferenceHelper.getChosenListeners().contains(LocationManager.NETWORK_PROVIDER)){
            return true;
        }

        if(!preferenceHelper.getChosenListeners().contains(LocationManager.NETWORK_PROVIDER)){
            return loc.getProvider().equalsIgnoreCase(LocationManager.GPS_PROVIDER);
        }

        if(!preferenceHelper.getChosenListeners().contains(LocationManager.GPS_PROVIDER)){
            return !loc.getProvider().equalsIgnoreCase(LocationManager.GPS_PROVIDER);
        }

        return true;
    }

    private void setDistanceTraveled(Location loc) {
        // Distance
        if (session.getPreviousLocationInfo() == null) {
            session.setPreviousLocationInfo(loc);
        }
        // Calculate this location and the previous location location and add to the current running total distance.
        // NOTE: Should be used in conjunction with 'distance required before logging' for more realistic values.
        double distance = Maths.calculateDistance(
                session.getPreviousLatitude(),
                session.getPreviousLongitude(),
                loc.getLatitude(),
                loc.getLongitude());
        session.setPreviousLocationInfo(loc);
        session.setTotalTravelled(session.getTotalTravelled() + distance);
    }

    protected void stopManagerAndResetAlarm() {
        if (!preferenceHelper.shouldKeepGPSOnBetweenFixes()) {
            stopGpsManager();
        }

        stopAbsoluteTimer();
        setAlarmForNextPoint();
    }


    private void stopAlarm() {
        Intent i = new Intent(this, GpsLoggingService.class);
        i.putExtra(IntentConstants.GET_NEXT_POINT, true);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        nextPointAlarmManager.cancel(pi);
    }

    @TargetApi(23)
    private void setAlarmForNextPoint() {
        LOG.debug("Set alarm for " + preferenceHelper.getMinimumLoggingInterval() + " seconds");

        Intent i = new Intent(this, GpsLoggingService.class);
        i.putExtra(IntentConstants.GET_NEXT_POINT, true);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        nextPointAlarmManager.cancel(pi);

        if(Systems.isDozing(this)){
            //Only invoked once per 15 minutes in doze mode
            LOG.warn("Device is dozing, using infrequent alarm");
            nextPointAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + preferenceHelper.getMinimumLoggingInterval() * 1000, pi);
        }
        else {
            nextPointAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + preferenceHelper.getMinimumLoggingInterval() * 1000, pi);
        }
    }


    /**
     * Calls file helper to write a given location to a file.
     *
     * @param loc Location object
     */
    private void writeToFile(Location loc) {
        session.setAddNewTrackSegment(false);

        try {
            LOG.debug("Calling file writers");
            FileLoggerFactory.write(getApplicationContext(), loc);

            if (session.hasDescription()) {
                LOG.info("Writing annotation: " + session.getDescription());
                FileLoggerFactory.annotate(getApplicationContext(), session.getDescription(), loc);
            }
        }
        catch(Exception e){
             LOG.error(getString(R.string.could_not_write_to_file), e);
        }

        session.clearDescription();
        EventBus.getDefault().post(new ServiceEvents.AnnotationStatus(true));
    }

    /**
     * Informs the main service client of the number of visible satellites.
     *
     * @param count Number of Satellites
     */
    void setSatelliteInfo(int count) {
        session.setVisibleSatelliteCount(count);
        EventBus.getDefault().post(new ServiceEvents.SatellitesVisible(count));
    }

    public void onNmeaSentence(long timestamp, String nmeaSentence) {

        if (preferenceHelper.shouldLogToNmea()) {
            NmeaFileLogger nmeaLogger = new NmeaFileLogger(Strings.getFormattedFileName());
            nmeaLogger.write(timestamp, nmeaSentence);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
    //    LOG.debug("nandhiny sensor changed event");
        checkbox_accelerometer=true;
        checkbox_magnetometer=true;
        checkbox_gyroscope=true;
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

        if (output_to_file && is_logging && checkbox_magnetometer && event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            if (count_output_written == total_checked - 1){
                row_to_be_written[6] =  Double.toString(event.values[0]);
                row_to_be_written[7] = Double.toString(event.values[1]);
                row_to_be_written[8] = Double.toString(event.values[2]);
                row_to_be_written[0] = currentDateandTime;
                writeToFileSensor(row_to_be_written);
                for (int i =0;i<9;i++){
                    row_to_be_written[i] = "";
                }
            }
            else{
                row_to_be_written[6] =  Double.toString(event.values[0]);
                row_to_be_written[7] = Double.toString(event.values[1]);
                row_to_be_written[8] = Double.toString(event.values[2]);
            }

        } else if (output_to_file && is_logging && checkbox_gyroscope && event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            if(count_output_written == total_checked - 1){
                row_to_be_written[3] =  Double.toString(event.values[0]);
                row_to_be_written[4] = Double.toString(event.values[1]);
                row_to_be_written[5] = Double.toString(event.values[2]);
                row_to_be_written[0] = currentDateandTime;
                writeToFileSensor(row_to_be_written);
                for (int i =0;i<9;i++){
                    row_to_be_written[i] = "";
                }
            }
            else{
                row_to_be_written[3] =  Double.toString(event.values[0]);
                row_to_be_written[4] = Double.toString(event.values[1]);
                row_to_be_written[5] = Double.toString(event.values[2]);
            }

        } else if (output_to_file && is_logging && checkbox_accelerometer && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float xChange = history[0] - event.values[0];
            float yChange = history[1] - event.values[1];

            history[0] = event.values[0];
            history[1] = event.values[1];

            if(count_output_written == total_checked - 1){
                row_to_be_written[1] =  Double.toString(xChange);
                row_to_be_written[2] = Double.toString(yChange);
                row_to_be_written[0] = currentDateandTime;
                writeToFileSensor(row_to_be_written);
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

    private void writeToFileSensor(String row[]) {

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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /**
     * Can be used from calling classes as the go-between for methods and
     * properties.
     */
    public class GpsLoggingBinder extends Binder {
        public GpsLoggingService getService() {
            return GpsLoggingService.this;
        }
    }


    @EventBusHook
    public void onEvent(CommandEvents.RequestToggle requestToggle){
        if (session.isStarted()) {
            stopLogging();
        } else {
            startLogging();
        }
    }

    @EventBusHook
    public void onEvent(CommandEvents.RequestStartStop startStop){
        if(startStop.start){
            startLogging();
        }
        else {
            stopLogging();
        }

        EventBus.getDefault().removeStickyEvent(CommandEvents.RequestStartStop.class);
    }

    @EventBusHook
    public void onEvent(CommandEvents.AutoSend autoSend){
        autoSendLogFile(autoSend.formattedFileName);

        EventBus.getDefault().removeStickyEvent(CommandEvents.AutoSend.class);
    }

    @EventBusHook
    public void onEvent(CommandEvents.Annotate annotate){
        final String desc = Strings.cleanDescription(annotate.annotation);
        if (desc.length() == 0) {
            LOG.debug("Clearing annotation");
            session.clearDescription();
        } else {
            LOG.debug("Pending annotation: " + desc);
            session.setDescription(desc);
            EventBus.getDefault().post(new ServiceEvents.AnnotationStatus(false));

            if(session.isStarted()){
                startGpsManager();
            }
            else {
                logOnce();
            }
        }

        EventBus.getDefault().removeStickyEvent(CommandEvents.Annotate.class);
    }

    @EventBusHook
    public void onEvent(CommandEvents.LogOnce logOnce){
        logOnce();
    }

    @EventBusHook
    public void onEvent(ServiceEvents.ActivityRecognitionEvent activityRecognitionEvent){

        session.setLatestDetectedActivity(activityRecognitionEvent.result.getMostProbableActivity());

        if(!preferenceHelper.shouldNotLogIfUserIsStill()){
            session.setUserStillSinceTimeStamp(0);
            return;
        }

        if(activityRecognitionEvent.result.getMostProbableActivity().getType() == DetectedActivity.STILL){
            LOG.debug(activityRecognitionEvent.result.getMostProbableActivity().toString());
            if(session.getUserStillSinceTimeStamp() == 0){
                LOG.debug("Just entered still state, attempt to log");
                startGpsManager();
                session.setUserStillSinceTimeStamp(System.currentTimeMillis());
            }

        }
        else {
            LOG.debug(activityRecognitionEvent.result.getMostProbableActivity().toString());
            //Reset the still-since timestamp
            session.setUserStillSinceTimeStamp(0);
            LOG.debug("Just exited still state, attempt to log");
            startGpsManager();
        }
    }

    @EventBusHook
    public void onEvent(ProfileEvents.SwitchToProfile switchToProfileEvent){
        try {

            if(preferenceHelper.getCurrentProfileName().equals(switchToProfileEvent.newProfileName)){
                return;
            }

            LOG.debug("Switching to profile: " + switchToProfileEvent.newProfileName);

            //Save the current settings to a file (overwrite)
            File f = new File(Files.storageFolder(GpsLoggingService.this), preferenceHelper.getCurrentProfileName()+".properties");
            preferenceHelper.savePropertiesFromPreferences(f);

            //Read from a possibly existing file and load those preferences in
            File newProfile = new File(Files.storageFolder(GpsLoggingService.this), switchToProfileEvent.newProfileName+".properties");
            if(newProfile.exists()){
                preferenceHelper.setPreferenceFromPropertiesFile(newProfile);
            }

            //Switch current profile name
            preferenceHelper.setCurrentProfileName(switchToProfileEvent.newProfileName);

        } catch (IOException e) {
            LOG.error("Could not save profile to file", e);
        }
    }

}
