package com.rnim.rn.audio.voice.android;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import com.rnim.rn.audio.voice.encoder.AudioMeta;
import com.rnim.rn.audio.voice.encoder.EncodingException;
import com.rnim.rn.audio.voice.encoder.EncodingReady;

/**
 * This class represent an audio recorder. It record the speech into a wave format PCM 16 bit.
 * The recorder only record wave data if it detect any speech on the byte stream
 */

public class VoiceRecorder implements EncodingReady {

  private static final String TAG = "VoiceRecorder";

  static final int[] SAMPLE_RATE_CANDIDATES = new int[] { 44100, 22050, 16000, 11025 };

  private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
  private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

  /**
   * A state indicate there is an error during record or encoding cause the recording process to
   * stop
   */
  public static final byte RECORD_END_BY_INTERRUPTED = 0;

  /**
   * A state indicate that the audio recording was ended by the user
   */
  public static final byte RECORD_END_BY_USER = 1;

  /**
   * event audio recorder listener
   */
  public static abstract class EventListener {

    /**
     * Called when the recorder starts hearing com.rnim.rn.audio.voice.
     */
    public void onRecordStart(AudioMeta audioMeta) {
    }

    /**
     * Called when the recorder is hearing com.rnim.rn.audio.voice.
     *
     * @param data The audio data in {@link AudioFormat#ENCODING_PCM_16BIT}.
     * @param size The size of the actual data in {@code data}.
     */
    public void onRecording(byte[] data, int size) {
    }

    /**
     * Called when the encoder encounter an exception during encode the audio
     *
     * @param throwable exception cause by encoder
     */
    public void onRecordError(Throwable throwable) {
    }

    /**
     * Called when the recorder stops hearing com.rnim.rn.audio.voice.
     */
    public void onRecordEnd(byte state) {
    }
  }

  //
  private AudioRecord audioRecord;

  //
  AudioMeta audioMeta;
  int sizeInBytes;
  private RecordStrategy recordStrategy;
  //
  private HandlerThread thread;
  private final Object lock = new Object();
  private boolean stop;
  // internal callback
  private EventListener eventListener;

  private long voiceHeardMillis = Long.MAX_VALUE;

  /**
   * Create VoiceRecorder
   *
   * @param recordStrategy a strategy to record the audio
   */
  public VoiceRecorder(RecordStrategy recordStrategy) {
    this(recordStrategy, new AudioMeta(0, 2, 16));
  }

  /**
   * Create VoiceRecorder
   *
   * @param recordStrategy a strategy to record the audio
   */
  public VoiceRecorder(RecordStrategy recordStrategy, AudioMeta audioMeta) {
    this.recordStrategy = recordStrategy;
    this.audioMeta = audioMeta;
  }

  /**
   * Set event callback
   *
   * @param eventListener a callback
   */
  public void setRecorderCallback(EventListener eventListener) {
    this.eventListener = eventListener;
  }

  /**
   * Starts recording com.rnim.rn.audio.voice and caller must call stop later.
   */
  public void start() {
    audioRecord = createAudioRecord();
    if (audioRecord == null) {
      throw new RuntimeException("Cannot instantiate VoiceRecorder");
    }
    // Start recording.
    audioRecord.startRecording();
    // assign the callback
    recordStrategy.getEncoder().setEncodingReady(this);
    // Start processing the captured audio.
    thread = new HandlerThread("read-audio-buffer");
    Log.d(TAG, "start audio recorder");
    thread.start();
    Handler handler = new Handler(thread.getLooper());
    TransferFromAudioRecorder readAudioBuffer = new TransferFromAudioRecorder();
    handler.post(readAudioBuffer);
  }

  /**
   * Stops recording audio.
   */
  public void stop() {
    synchronized (lock) {
      if (thread != null) {
        stop = true;
        thread.quit();
        thread = null;
      }
      if (audioRecord != null) {
        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;
      }
      Log.d(TAG, "stop audio recorder");
    }
  }

  /**
   * Retrieves the sample rate currently used to record audio.
   *
   * @return The sample rate of recorded audio.
   */
  int getSampleRate() {
    return audioMeta.getSampleRate();
  }

  /**
   * Creates a new {@link AudioRecord}.
   *
   * @return A newly created {@link AudioRecord}, or null if it cannot be created due to no permission
   * or no microphone available.
   */
  AudioRecord createAudioRecord() {
    for (int sampleRate : SAMPLE_RATE_CANDIDATES) {
      final int sizeInBytes = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING);
      if (sizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
        continue;
      }
      final AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
          sampleRate, CHANNEL, ENCODING, sizeInBytes);
      if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
        this.sizeInBytes = sizeInBytes;
        this.audioMeta.setSampleRate(sampleRate);
        return audioRecord;
      } else {
        audioRecord.release();
      }
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void onEncoded(byte[] buffer, int size) {
    eventListener.onRecording(buffer, size);
  }

  /**
   * Continuously processes the captured audio and notifies {@link #eventListener} of corresponding
   * events.
   */
  private class TransferFromAudioRecorder implements Runnable {

    private boolean onRecording(byte[] buffer, int size) {
      try {
        recordStrategy.getEncoder().encode(buffer, size);
        return false;
      } catch (EncodingException e) {
        e.printStackTrace();
        eventListener.onRecordError(e);
        return true;
      }
    }

    @Override
    public void run() {
      Log.d(TAG, "read from audio record buffer");
      byte[] buffer = new byte[sizeInBytes];
      while (true) {
        synchronized (lock) {
          if (stop) {
            endRecording(RECORD_END_BY_USER);
            return;
          }
          final int size = audioRecord.read(buffer, 0, buffer.length);
          if (size > 0 && !stop) {
            final long now = System.currentTimeMillis();
            if (voiceHeardMillis == Long.MAX_VALUE) {
              eventListener.onRecordStart(audioMeta);
              recordStrategy.getEncoder().initialize(audioMeta);
            }
            // if there is an exception occurs
            if (onRecording(buffer, size)) {
              end();
              endRecording(RECORD_END_BY_INTERRUPTED);
              return;
            }
            voiceHeardMillis = now;
          }
        }
      }
    }

    private void endRecording(byte state) {
      if (voiceHeardMillis != Long.MAX_VALUE) {
        voiceHeardMillis = Long.MAX_VALUE;
        recordStrategy.getEncoder().release();
      }
      eventListener.onRecordEnd(state);
    }

    // end the record
    private void end() {
      stop();
    }
  }
}
