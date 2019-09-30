package com.rnim.rn.audio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

public class KeepAliveService extends Service {
  public static final String KEY_COMMAND = "com.rnim.rn.audio.key.COMMAND";
  public static final int COMMAND_START = 1;
  public static final int COMMAND_STOP = 2;

  private static final String CHANNEL_ID = "record_audio";
  private static final int ONGOING_NOTIFICATION_ID = 100;

  public static Intent getIntent(Context context, int command) {
    return new Intent(context, KeepAliveService.class)
        .putExtra(KEY_COMMAND, command);
  }

  @Nullable @Override public IBinder onBind(Intent intent) {
    return null;
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    int command = intent.getIntExtra(KEY_COMMAND, 0);
    if (command == COMMAND_START) {
      bringToFront();
    } else if (command == COMMAND_STOP) {
      stop();
    } else {
      stop();
    }
    return START_NOT_STICKY;
  }

  private void bringToFront() {
    createNotification(this);
  }

  private void stop() {
    stopForeground(true);
    stopSelf();
  }

  public void createNotification(Service context) {

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      createChannel(context);
    }
    // Create Pending Intents.
    final Intent launcherIntent = new Intent();
    launcherIntent.setComponent(getLauncherActivityComponent(context));
    launcherIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    PendingIntent piLaunchMainActivity =
        PendingIntent.getActivity(context, 100, launcherIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    // Create a notification.
    Notification mNotification =
        new NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Recording in progress")
            .setSmallIcon(getNotificationSmallIcon(context))
            .setContentIntent(piLaunchMainActivity)
            .setAutoCancel(false)
            .build();

    startForeground(ONGOING_NOTIFICATION_ID, mNotification);
  }

  @RequiresApi(api = Build.VERSION_CODES.O) @NonNull
  private String createChannel(Context context) {
    // Create a channel.
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    CharSequence channelName = "Audio Recording";
    int importance = NotificationManager.IMPORTANCE_LOW;
    NotificationChannel notificationChannel =
        new NotificationChannel(CHANNEL_ID, channelName, importance);
    notificationManager.createNotificationChannel(notificationChannel);
    return CHANNEL_ID;
  }

  private ComponentName getLauncherActivityComponent(Context context) {
    return context.getPackageManager().getLaunchIntentForPackage(getPackageName()).getComponent();
  }

  @Nullable
  private int getNotificationSmallIcon(Context context) {
    try {
      ApplicationInfo app = context.getPackageManager().getApplicationInfo(context.getPackageName(),
          PackageManager.GET_META_DATA);
      Bundle bundle = app.metaData;
      return bundle.getInt(
          "com.rnim.rn.audio.notification_small_icon", 0);
    } catch (PackageManager.NameNotFoundException e) {
      // Ignoring as we are requesting app info of the app which has included this module.
      // That gives us guarantee that this exception will never be thrown.
      return 0;
    }
  }
}
