package com.rnim.rn.audio.voice.android;

import com.rnim.rn.audio.voice.encoder.FlacEncoder;

/**
 * The record strategy define a rule how the recording should work
 */
public class RecordStrategy {

  private FlacEncoder encoder;
  private int[] sampleRatesCandidate;

  /**
   * Create record strategy
   */
  public RecordStrategy() {
    encoder = new FlacEncoder();
    sampleRatesCandidate = VoiceRecorder.SAMPLE_RATE_CANDIDATES;
  }

  /**
   * Get audio encoder
   *
   * @return current Encoder
   * @see {@link FlacEncoder}
   */
  public FlacEncoder getEncoder() {
    return encoder;
  }

  /**
   * Get a list of sample rate
   *
   * @return current prefer sample rate
   */
  public int[] getSampleRatesCandidate() {
    return sampleRatesCandidate;
  }
}
