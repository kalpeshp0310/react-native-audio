package com.rnim.rn.audio;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.rnim.rn.audio.voice.android.RecordStrategy;
import com.rnim.rn.audio.voice.android.VoiceRecorder;
import com.rnim.rn.audio.voice.encoder.AudioMeta;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

class AudioRecorderManager extends ReactContextBaseJavaModule {

  private static final String TAG = "ReactNativeAudio";

  private Context context;
  private VoiceRecorder recorder;
  private String currentOutputFilePath;
  private File currentOutputFile;
  private FileOutputStream currentOutputStream;
  private boolean isRecording = false;
  private boolean isPaused = false;
  private Timer timer;
  private StopWatch stopWatch;
  private RecordStrategy recordStrategy = new RecordStrategy();
  private AudioMeta audioMeta;

  public AudioRecorderManager(ReactApplicationContext reactContext) {
    super(reactContext);
    this.context = reactContext;
    stopWatch = new StopWatch();
  }

  @Override
  public String getName() {
    return "AudioRecorderManager";
  }

  @ReactMethod
  public void checkAuthorizationStatus(Promise promise) {
    int permissionCheck = ContextCompat.checkSelfPermission(getCurrentActivity(),
        Manifest.permission.RECORD_AUDIO);
    boolean permissionGranted = permissionCheck == PackageManager.PERMISSION_GRANTED;
    promise.resolve(permissionGranted);
  }

  @ReactMethod
  public void prepareRecordingAtPath(String recordingPath, ReadableMap recordingSettings,
      Promise promise) {
    if (isRecording) {
      logAndRejectPromise(promise, "INVALID_STATE",
          "Please call stopRecording before starting recording");
    }
    File destFile = new File(recordingPath);
    if (destFile.getParentFile() != null) {
      destFile.getParentFile().mkdirs();
    }
    audioMeta =
        new AudioMeta(recordingSettings.getInt("SampleRate"), recordingSettings.getInt("Channels"),
            16);

    currentOutputFilePath = recordingPath;
    currentOutputFile = new File(currentOutputFilePath);
    if (!currentOutputFile.exists()) {
      try {
        currentOutputFile.createNewFile();
      } catch (IOException e) {
        e.printStackTrace();
        logAndRejectPromise(promise, "COULDNT_PREPARE_RECORDING_AT_PATH" + recordingPath,
            e.getMessage());
      }
    }
    try {
      currentOutputStream = new FileOutputStream(currentOutputFile);
    } catch (IOException e) {
      e.printStackTrace();
      logAndRejectPromise(promise, "COULDNT_PREPARE_RECORDING_AT_PATH" + recordingPath,
          e.getMessage());
    }
    promise.resolve(currentOutputFilePath);
  }

  @ReactMethod
  public void startRecording(Promise promise) {
    if (audioMeta == null) {
      logAndRejectPromise(promise, "RECORDING_NOT_PREPARED",
          "Please call prepareRecordingAtPath before starting recording");
      return;
    }
    if (isRecording) {
      logAndRejectPromise(promise, "INVALID_STATE",
          "Please call stopRecording before starting recording");
      return;
    }
    startRecording();
    stopWatch.reset();
    stopWatch.start();
    isRecording = true;
    isPaused = false;
    startTimer();
    promise.resolve(currentOutputFilePath);
    bringToForeground();
  }

  @ReactMethod
  public void stopRecording(Promise promise) {
    if (!isRecording) {
      logAndRejectPromise(promise, "INVALID_STATE",
          "Please call startRecording before stopping recording");
      return;
    }

    stopTimer();
    isRecording = false;
    boolean wasPaused = isPaused;
    isPaused = false;

    stopRecording();
    stopWatch.stop();

    promise.resolve(currentOutputFilePath);

    WritableMap result = Arguments.createMap();
    result.putString("status", "OK");
    result.putString("audioFileURL", "file://" + currentOutputFilePath);

    sendEvent("recordingFinished", result);
    if (!wasPaused) {
      removeFromForeground();
    }
  }

  @ReactMethod
  public void pauseRecording(Promise promise) {
    if (!isPaused) {
      stopRecording();
      stopWatch.stop();
    }

    isPaused = true;
    promise.resolve(null);
    removeFromForeground();
  }

  @ReactMethod
  public void resumeRecording(Promise promise) {
    if (isPaused) {
      startRecording();
      stopWatch.start();
    }

    isPaused = false;
    promise.resolve(null);
    bringToForeground();
  }

  public void startRecording() {
    if (this.recorder != null) {
      this.recorder.stop();
    }
    this.recorder = new VoiceRecorder(recordStrategy, audioMeta);
    // internal com.rnim.rn.audio.voice recorder listeners
    VoiceRecorder.EventListener eventListener = new VoiceRecorder.EventListener() {

      /**
       * {@inheritDoc}
       */
      @Override
      public void onRecordStart(AudioMeta audioMeta) {
        Log.d(TAG, "onRecordStart() called with: audioMeta = [" + audioMeta + "]");
        if (currentOutputStream == null) {
          try {
            currentOutputStream = new FileOutputStream(currentOutputFile);
          } catch (FileNotFoundException e) {
            e.printStackTrace();
          }
        }
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public void onRecording(byte[] data, int size) {
        if (isRecording && !isPaused) {
          try {
            currentOutputStream.write(data, 0, size);
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public void onRecordEnd(byte state) {
        if (currentOutputStream != null) {
          try {
            currentOutputStream.flush();
            currentOutputStream.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
        recorder = null;
      }
    };
    this.recorder.setRecorderCallback(eventListener);
    this.recorder.start();
  }

  public void stopRecording() {
    Log.d(TAG, "stopRecording() called");
    if (recorder != null) {
      recorder.stop();
      recorder = null;
      Log.d(TAG, "stop com.rnim.rn.audio.voice recorder thread");
    }
  }

  private void startTimer() {
    timer = new Timer();
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        if (!isPaused) {
          WritableMap body = Arguments.createMap();
          body.putDouble("currentTime", stopWatch.getTimeSeconds());
          sendEvent("recordingProgress", body);
        }
      }
    }, 0, 1000);
  }

  private void stopTimer() {
    if (timer != null) {
      timer.cancel();
      timer.purge();
      timer = null;
    }
  }

  private void sendEvent(String eventName, Object params) {
    getReactApplicationContext()
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }

  private void logAndRejectPromise(Promise promise, String errorCode, String errorMessage) {
    Log.e(TAG, errorMessage);
    promise.reject(errorCode, errorMessage);
  }

  private void bringToForeground() {
    Intent intent =
        KeepAliveService.getIntent(context, KeepAliveService.COMMAND_START);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(intent);
    } else {
      context.startService(intent);
    }
  }

  private void removeFromForeground() {
    Intent intent =
        KeepAliveService.getIntent(context, KeepAliveService.COMMAND_STOP);
    context.startService(intent);
  }
}
