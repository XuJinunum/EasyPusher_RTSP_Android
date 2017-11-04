/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.easydarwin.util;

import android.media.AudioFormat;
import android.media.MediaCodec;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.UUID;

/**
 * Defines constants used by the library.
 */
public final class C {

  private C() {}

  /**
   * Special constant representing a time corresponding to the end of a source. Suitable for use in
   * any time base.
   */
  public static final long TIME_END_OF_SOURCE = Long.MIN_VALUE;

  /**
   * Special constant representing an unset or unknown time or duration. Suitable for use in any
   * time base.
   */
  public static final long TIME_UNSET = Long.MIN_VALUE + 1;

  /**
   * Represents an unset or unknown index.
   */
  public static final int INDEX_UNSET = -1;

  /**
   * Represents an unset or unknown position.
   */
  public static final int POSITION_UNSET = -1;

  /**
   * Represents an unset or unknown length.
   */
  public static final int LENGTH_UNSET = -1;

  /**
   * The number of microseconds in one second.
   */
  public static final long MICROS_PER_SECOND = 1000000L;

  /**
   * The number of nanoseconds in one second.
   */
  public static final long NANOS_PER_SECOND = 1000000000L;

  /**
   * The name of the UTF-8 charset.
   */
  public static final String UTF8_NAME = "UTF-8";

  /**
   * @see MediaCodec#CRYPTO_MODE_UNENCRYPTED
   */
  @SuppressWarnings("InlinedApi")
  public static final int CRYPTO_MODE_UNENCRYPTED = MediaCodec.CRYPTO_MODE_UNENCRYPTED;
  /**
   * @see MediaCodec#CRYPTO_MODE_AES_CTR
   */
  @SuppressWarnings("InlinedApi")
  public static final int CRYPTO_MODE_AES_CTR = MediaCodec.CRYPTO_MODE_AES_CTR;

  /**
   * @see AudioFormat#ENCODING_INVALID
   */
  public static final int ENCODING_INVALID = AudioFormat.ENCODING_INVALID;
  /**
   * @see AudioFormat#ENCODING_PCM_8BIT
   */
  public static final int ENCODING_PCM_8BIT = AudioFormat.ENCODING_PCM_8BIT;
  /**
   * @see AudioFormat#ENCODING_PCM_16BIT
   */
  public static final int ENCODING_PCM_16BIT = AudioFormat.ENCODING_PCM_16BIT;
  /**
   * PCM encoding with 24 bits per sample.
   */
  public static final int ENCODING_PCM_24BIT = 0x80000000;
  /**
   * PCM encoding with 32 bits per sample.
   */
  public static final int ENCODING_PCM_32BIT = 0x40000000;
  /**
   * @see AudioFormat#ENCODING_AC3
   */
  @SuppressWarnings("InlinedApi")
  public static final int ENCODING_AC3 = AudioFormat.ENCODING_AC3;
  /**
   * @see AudioFormat#ENCODING_E_AC3
   */
  @SuppressWarnings("InlinedApi")
  public static final int ENCODING_E_AC3 = AudioFormat.ENCODING_E_AC3;
  /**
   * @see AudioFormat#ENCODING_DTS
   */
  @SuppressWarnings("InlinedApi")
  public static final int ENCODING_DTS = AudioFormat.ENCODING_DTS;
  /**
   * @see AudioFormat#ENCODING_DTS_HD
   */
  @SuppressWarnings("InlinedApi")
  public static final int ENCODING_DTS_HD = AudioFormat.ENCODING_DTS_HD;

  /**
   * Indicates that a buffer holds a synchronization sample.
   */
  @SuppressWarnings("InlinedApi")
  public static final int BUFFER_FLAG_KEY_FRAME = MediaCodec.BUFFER_FLAG_KEY_FRAME;
  /**
   * Flag for empty buffers that signal that the end of the stream was reached.
   */
  @SuppressWarnings("InlinedApi")
  public static final int BUFFER_FLAG_END_OF_STREAM = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
  /**
   * Indicates that a buffer is (at least partially) encrypted.
   */
  public static final int BUFFER_FLAG_ENCRYPTED = 0x40000000;
  /**
   * Indicates that a buffer should be decoded but not rendered.
   */
  public static final int BUFFER_FLAG_DECODE_ONLY = 0x80000000;

  /**
   * Indicates that the track should be selected if user preferences do not state otherwise.
   */
  public static final int SELECTION_FLAG_DEFAULT = 1;
  /**
   * Indicates that the track must be displayed. Only applies to text tracks.
   */
  public static final int SELECTION_FLAG_FORCED = 2;
  /**
   * Indicates that the player may choose to play the track in absence of an explicit user
   * preference.
   */
  public static final int SELECTION_FLAG_AUTOSELECT = 4;


  /**
   * A return value for methods where the end of an input was encountered.
   */
  public static final int RESULT_END_OF_INPUT = -1;
  /**
   * A return value for methods where the length of parsed data exceeds the maximum length allowed.
   */
  public static final int RESULT_MAX_LENGTH_EXCEEDED = -2;
  /**
   * A return value for methods where nothing was read.
   */
  public static final int RESULT_NOTHING_READ = -3;
  /**
   * A return value for methods where a buffer was read.
   */
  public static final int RESULT_BUFFER_READ = -4;
  /**
   * A return value for methods where a format was read.
   */
  public static final int RESULT_FORMAT_READ = -5;

  /**
   * A data type constant for data of unknown or unspecified type.
   */
  public static final int DATA_TYPE_UNKNOWN = 0;
  /**
   * A data type constant for media, typically containing media samples.
   */
  public static final int DATA_TYPE_MEDIA = 1;
  /**
   * A data type constant for media, typically containing only initialization data.
   */
  public static final int DATA_TYPE_MEDIA_INITIALIZATION = 2;
  /**
   * A data type constant for drm or encryption data.
   */
  public static final int DATA_TYPE_DRM = 3;
  /**
   * A data type constant for a manifest file.
   */
  public static final int DATA_TYPE_MANIFEST = 4;
  /**
   * A data type constant for time synchronization data.
   */
  public static final int DATA_TYPE_TIME_SYNCHRONIZATION = 5;
  /**
   * Applications or extensions may define custom {@code DATA_TYPE_*} constants greater than or
   * equal to this value.
   */
  public static final int DATA_TYPE_CUSTOM_BASE = 10000;

  /**
   * A type constant for tracks of unknown type.
   */
  public static final int TRACK_TYPE_UNKNOWN = -1;
  /**
   * A type constant for tracks of some default type, where the type itself is unknown.
   */
  public static final int TRACK_TYPE_DEFAULT = 0;
  /**
   * A type constant for audio tracks.
   */
  public static final int TRACK_TYPE_AUDIO = 1;
  /**
   * A type constant for video tracks.
   */
  public static final int TRACK_TYPE_VIDEO = 2;
  /**
   * A type constant for text tracks.
   */
  public static final int TRACK_TYPE_TEXT = 3;
  /**
   * A type constant for metadata tracks.
   */
  public static final int TRACK_TYPE_METADATA = 4;
  /**
   * Applications or extensions may define custom {@code TRACK_TYPE_*} constants greater than or
   * equal to this value.
   */
  public static final int TRACK_TYPE_CUSTOM_BASE = 10000;

  /**
   * A selection reason constant for selections whose reasons are unknown or unspecified.
   */
  public static final int SELECTION_REASON_UNKNOWN = 0;
  /**
   * A selection reason constant for an initial track selection.
   */
  public static final int SELECTION_REASON_INITIAL = 1;
  /**
   * A selection reason constant for an manual (i.e. user initiated) track selection.
   */
  public static final int SELECTION_REASON_MANUAL = 2;
  /**
   * A selection reason constant for an adaptive track selection.
   */
  public static final int SELECTION_REASON_ADAPTIVE = 3;
  /**
   * A selection reason constant for a trick play track selection.
   */
  public static final int SELECTION_REASON_TRICK_PLAY = 4;
  /**
   * Applications or extensions may define custom {@code SELECTION_REASON_*} constants greater than
   * or equal to this value.
   */
  public static final int SELECTION_REASON_CUSTOM_BASE = 10000;

  /**
   * A default size in bytes for an individual allocation that forms part of a larger buffer.
   */
  public static final int DEFAULT_BUFFER_SEGMENT_SIZE = 64 * 1024;

  /**
   * A default size in bytes for a video buffer.
   */
  public static final int DEFAULT_VIDEO_BUFFER_SIZE = 200 * DEFAULT_BUFFER_SEGMENT_SIZE;

  /**
   * A default size in bytes for an audio buffer.
   */
  public static final int DEFAULT_AUDIO_BUFFER_SIZE = 54 * DEFAULT_BUFFER_SEGMENT_SIZE;

  /**
   * A default size in bytes for a text buffer.
   */
  public static final int DEFAULT_TEXT_BUFFER_SIZE = 2 * DEFAULT_BUFFER_SEGMENT_SIZE;

  /**
   * A default size in bytes for a metadata buffer.
   */
  public static final int DEFAULT_METADATA_BUFFER_SIZE = 2 * DEFAULT_BUFFER_SEGMENT_SIZE;

  /**
   * A default size in bytes for a muxed buffer (e.g. containing video, audio and text).
   */
  public static final int DEFAULT_MUXED_BUFFER_SIZE = DEFAULT_VIDEO_BUFFER_SIZE
      + DEFAULT_AUDIO_BUFFER_SIZE + DEFAULT_TEXT_BUFFER_SIZE;

  /**
   * The Nil UUID as defined by
   * <a href="https://tools.ietf.org/html/rfc4122#section-4.1.7">RFC4122</a>.
   */
  public static final UUID UUID_NIL = new UUID(0L, 0L);

  /**
   * UUID for the Widevine DRM scheme.
   * <p></p>
   * Widevine is supported on Android devices running Android 4.3 (API Level 18) and up.
   */
  public static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);

  /**
   * UUID for the PlayReady DRM scheme.
   * <p>
   * PlayReady is supported on all AndroidTV devices. Note that most other Android devices do not
   * provide PlayReady support.
   */
  public static final UUID PLAYREADY_UUID = new UUID(0x9A04F07998404286L, 0xAB92E65BE0885F95L);

  /**
   * Indicates Monoscopic stereo layout, used with 360/3D/VR videos.
   */
  public static final int STEREO_MODE_MONO = 0;
  /**
   * Indicates Top-Bottom stereo layout, used with 360/3D/VR videos.
   */
  public static final int STEREO_MODE_TOP_BOTTOM = 1;
  /**
   * Indicates Left-Right stereo layout, used with 360/3D/VR videos.
   */
  public static final int STEREO_MODE_LEFT_RIGHT = 2;

  /**
   * Converts a time in microseconds to the corresponding time in milliseconds, preserving
   * {@link #TIME_UNSET} values.
   *
   * @param timeUs The time in microseconds.
   * @return The corresponding time in milliseconds.
   */
  public static long usToMs(long timeUs) {
    return timeUs == TIME_UNSET ? TIME_UNSET : (timeUs / 1000);
  }

  /**
   * Converts a time in milliseconds to the corresponding time in microseconds, preserving
   * {@link #TIME_UNSET} values.
   *
   * @param timeMs The time in milliseconds.
   * @return The corresponding time in microseconds.
   */
  public static long msToUs(long timeMs) {
    return timeMs == TIME_UNSET ? TIME_UNSET : (timeMs * 1000);
  }

}
