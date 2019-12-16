package com.rnim.rn.audio.voice.encoder;

import com.rnim.rn.audio.voice.android.LibFlac;

/**
 * A Flac audio encoder, it's encode from wav to flac format.
 * Flac naturally support streaming encoding
 */
public class FlacEncoder {

  private LibFlac libFlac;
  private int compression;
  private EncodingReady encodingReady;

  /**
   * Create Flac encoder with default compression to 5
   */
  public FlacEncoder() {
    this(5);
  }

  /**
   * Create flac encoder
   *
   * @param compression a compression level range from 0 to 8
   */
  public FlacEncoder(int compression) {
    this.compression = compression;
  }

  /**
   * Set EncodingReady callback.
   *
   * @param encodingReady a callback
   */
  public void setEncodingReady(EncodingReady encodingReady) {
    if (encodingReady == null) {
      throw new IllegalArgumentException("encodingReady is cannot be null");
    }
    this.encodingReady = encodingReady;
  }

  /**
   * Initialize encoder based on the provide audio meta data
   *
   * @param audioMeta an audio meta data
   */
  public void initialize(AudioMeta audioMeta) {
    if (libFlac == null) {
      this.libFlac = new LibFlac();
      libFlac.setFlacEncodeCallback(new LibFlac.EncoderCallback() {
        @Override
        public void onEncoded(byte[] data, int sized) {
          encodingReady.onEncoded(data, sized);
        }
      });
    }
    libFlac.initialize(audioMeta.getSampleRate(), audioMeta.getChannel(),
        audioMeta.getBitPerSecond(), compression);
  }

  /**
   * Encode the wav pcm 16bit data into an flac format.
   *
   * @param buffer a wav pcm 16bit buffer data
   */
  public void encode(byte[] buffer, int size) throws EncodingException {
    if (libFlac == null) {
      throw new IllegalStateException("initialize has not been called");
    }
    libFlac.encode(buffer, size);
  }

  /**
   * Finalize encoder information that the encoding operation is done, we should release any
   * resource at this point or depose it. Release all C & C++ resource.
   */
  public void release() {
    if (libFlac != null) {
      libFlac.finish();
      libFlac.release();
      libFlac = null;
    }
  }
}
