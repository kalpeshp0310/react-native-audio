package com.rnim.rn.audio.voice.encoder;

/**
 * A basic information about audio
 */
public class AudioMeta {

  private int sampleRate;
  private int channel;

  /**
   * Create an audio meta
   *
   * @param sampleRate audio sample rate
   * @param channel audio channel
   */
  public AudioMeta(int sampleRate, int channel) {
    this.sampleRate = sampleRate;
    this.channel = channel;
  }

  /**
   * Set audio sample rate
   *
   * @param sampleRate a sample rate
   * @return self object
   */
  public AudioMeta setSampleRate(int sampleRate) {
    this.sampleRate = sampleRate;
    return this;
  }

  /**
   * Set number of channel
   *
   * @param channel number of channel
   * @return self object
   */
  public AudioMeta setChannel(int channel) {
    this.channel = channel;
    return this;
  }

  /**
   * Get audio sample rate
   *
   * @return a sample rate
   */
  public int getSampleRate() {
    return sampleRate;
  }

  /**
   * Get audio channel, 1 mean mono and 2 mean stereo other will be different
   *
   * @return integer represent audio channel
   */
  public int getChannel() {
    return channel;
  }

  @Override public String toString() {
    return "AudioMeta{" +
        "sampleRate=" + sampleRate +
        ", channel=" + channel +
        '}';
  }
}
