package com.rnim.rn.audio.voice.android;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import com.rnim.rn.audio.voice.encoder.AudioMeta;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This class represent an audio recorder. It record the speech into a wave format PCM 16 bit.
 * The recorder only record wave data if it detect any speech on the byte stream
 */

public class VoiceRecorder {

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
   */
  public VoiceRecorder(AudioMeta audioMeta) {
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
    // Start processing the captured audio.
    thread = new HandlerThread("read-audio-buffer");
    Log.d(TAG, "start audio recorder");
    thread.start();
    Handler handler = new Handler(thread.getLooper());
    try {
      TransferFromAudioRecorder readAudioBuffer;
      final MediaFormat format =
          MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_FLAC, getSampleRate(),
              audioMeta.getChannel());
      format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5);
      readAudioBuffer = new TransferFromAudioRecorder(format);
      handler.post(readAudioBuffer);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Stops recording audio.
   */
  public void stop() {
    synchronized (lock) {
      if (stop) {
        if (thread != null) {
          thread.quit();
          thread = null;
        }
        if (audioRecord != null) {
          audioRecord.stop();
          audioRecord.release();
          audioRecord = null;
        }
      }
      stop = true;
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
   * Continuously processes the captured audio and notifies {@link #eventListener} of corresponding
   * events.
   */
  private class TransferFromAudioRecorder implements Runnable {

    MediaCodec.BufferInfo mInfo = new MediaCodec.BufferInfo();
    boolean mSawOutputEOS;
    boolean mSawInputEOS;
    private MediaCodec mCodec;
    // result stream
    private byte[] mBuf = null;
    private int mBufCounter = 0;

    TransferFromAudioRecorder(MediaFormat format) throws IOException {
      String mime = format.getString(MediaFormat.KEY_MIME);
      mCodec = MediaCodec.createEncoderByType(mime);
      mCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      mCodec.start();
    }

    private void onRecording(byte[] buffer, int size) {
      eventListener.onRecording(buffer, size);
    }

    @Override
    public void run() {
      Log.d(TAG, "read from audio record buffer");
      while (!mSawInputEOS) {
        mSawInputEOS = stop;
        synchronized (lock) {
          Log.i(TAG, "sending data to " + mCodec.getName());
          int index = mCodec.dequeueInputBuffer(5000);
          if (index < 0) {
            // no input buffer currently available
            continue;
          } else {
            ByteBuffer buf = mCodec.getInputBuffer(index);
            buf.clear();
            int inBufLen = buf.limit();
            long timestampUs = 0; // non-zero for MediaExtractor mode
            if (mBuf == null) {
              mBuf = new byte[inBufLen];
            }
            final int size = audioRecord.read(mBuf, 0, mBuf.length);
            buf.put(mBuf, 0, size);
            int flags = mSawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0;
            Log.i(TAG, "queuing input buffer " + index +
                ", size " + size + ", flags: " + flags +
                " on " + mCodec.getName());
            mCodec.queueInputBuffer(index,
                0 /* offset */,
                size,
                timestampUs /* presentationTimeUs */,
                flags);
            Log.i(TAG, "queued input buffer " + index + ", size " + size);

            index = mCodec.dequeueOutputBuffer(mInfo, 5000);
            if (index >= 0) {
              Log.i(TAG, "got " + mInfo.size + " bytes from " + mCodec.getName());
              ByteBuffer out = mCodec.getOutputBuffer(index);
              ByteBuffer ret = ByteBuffer.allocate(mInfo.size);
              ret.put(out);
              mCodec.releaseOutputBuffer(index, false /* render */);
              if ((mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.i(TAG, "saw output EOS on " + mCodec.getName());
                mSawOutputEOS = true;
              }
              ret.flip(); // prepare buffer for reading from it
              // XXX chck that first encoded buffer has CSD flags set
              if (mBufCounter++ == 0
                  && (mInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                Log.e(TAG, "first encoded buffer missing CSD flag");
              }
              final long now = System.currentTimeMillis();
              if (voiceHeardMillis == Long.MAX_VALUE) {
                eventListener.onRecordStart(audioMeta);
              }
              voiceHeardMillis = now;
              if (size > 0) {
                // if there is an exception occurs
                final byte[] array = ret.array();
                onRecording(array, array.length);
              }
              if (mSawOutputEOS) {
                end();
                endRecording(RECORD_END_BY_USER);
                break;
              }
            }
          }
        }
      }
    }

    private void endRecording(byte state) {
      if (voiceHeardMillis != Long.MAX_VALUE) {
        voiceHeardMillis = Long.MAX_VALUE;
      }
      try {
        if (mCodec != null) {
          mCodec.release();
        }
      } finally {
        mCodec = null;
      }
      eventListener.onRecordEnd(state);
    }

    // end the record
    private void end() {
      stop();
    }
  }
}
