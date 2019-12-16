package com.rnim.rn.audio.voice.encoder;

/**
 * A callback listener when encode is done
 */
public interface EncodingReady {

  /**
   * Called when encode is done
   *
   * @param buffer a new buffer encoded data
   */
  void onEncoded(byte[] buffer, int size);
}
