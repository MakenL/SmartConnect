package com.embedhome.btlamp;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;

import static android.content.Context.VIBRATOR_SERVICE;

public class AppNotification {

    private volatile int app_notification;
    private Uri app_uri_rington;
    private Ringtone app_rington;
    private Vibrator app_vibro;
    private SharedPreferences mSettings;

    private static final String APP_PREFERENCES = "settings";
    private static final String APP_PREFERENCES_NOTIFICATION = "notification";

    public static final int NOTIFICATION_DISABLE = 0;
    public static final int NOTIFICATION_RINGTON = 1;
    public static final int NOTIFICATION_VIBRO = 2;

    public AppNotification(Context context) {

        // считывание сохраненных настроек
        mSettings = context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        if (mSettings.contains(APP_PREFERENCES_NOTIFICATION)) {
            app_notification = mSettings.getInt(APP_PREFERENCES_NOTIFICATION, NOTIFICATION_RINGTON);
        } else {
            app_notification = NOTIFICATION_RINGTON;
        }

        app_uri_rington = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (app_uri_rington != null){
            app_rington = RingtoneManager.getRingtone(context.getApplicationContext(), app_uri_rington);
        }
        app_vibro = (Vibrator)context.getSystemService(VIBRATOR_SERVICE);
    }

    public int getNotification(){
        return app_notification;
    }

    public void setNotification (int notification_type){

        if (notification_type <= NOTIFICATION_VIBRO ) {

            app_notification = notification_type;
            // сохранение настроек
            SharedPreferences.Editor editor = mSettings.edit();
            editor.putInt(APP_PREFERENCES_NOTIFICATION, app_notification);
            editor.apply();
        }
    }

    public int updateNotification(Context context){
        // считывание сохраненных настроек
        mSettings = context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        if (mSettings.contains(APP_PREFERENCES_NOTIFICATION)) {
            app_notification = mSettings.getInt(APP_PREFERENCES_NOTIFICATION, NOTIFICATION_RINGTON);
        } else {
            app_notification = NOTIFICATION_RINGTON;
        }

        return app_notification;
    }

    public void show(){

        switch (app_notification) {
            case NOTIFICATION_RINGTON:
                if (app_uri_rington != null){
                    if (!app_rington.isPlaying()){
                        app_rington.play();
                    }
                }
                break;
            case NOTIFICATION_VIBRO:
                if (app_vibro.hasVibrator()){
                    app_vibro.vibrate(350);
                }
                break;
            default:
                break;
        }
    }
}
