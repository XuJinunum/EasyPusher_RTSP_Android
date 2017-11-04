package org.easydarwin.video;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Created by John on 2016/3/12.
 */
public class RTSPClient implements Closeable {

    private static int sKey;

    public static final class FrameInfo {
        public int codec;			/* 音视频格式 */

        public int type;			/* 视频帧类型 */
        public byte fps;			/* 视频帧率 */
        public short width;			/* 视频宽 */
        public short height;			/* 视频高 */

        public int reserved1;			/* 保留参数1 */
        public int reserved2;			/* 保留参数2 */

        public int sample_rate;	/* 音频采样率 */
        public int channels;		/* 音频声道数 */
        public int bits_per_sample;	/* 音频采样精度 */

        public int length;			/* 音视频帧大小 */
        public long timestamp_usec;	/* 时间戳,微妙 */
        public long timestamp_sec;	/* 时间戳 秒 */

        public float bitrate;		/* 比特率 */
        public float losspacket;		/* 丢包率 */

        public long stamp;

        public byte[] buffer;
        public int offset = 0;
        public boolean audio;
    }

    public static final class MediaInfo {
//        Easy_U32 u32VideoCodec;				/*  ”∆µ±‡¬Î¿‡–Õ */
//        Easy_U32 u32VideoFps;				/*  ”∆µ÷°¬  */
//
//        Easy_U32 u32AudioCodec;				/* “Ù∆µ±‡¬Î¿‡–Õ */
//        Easy_U32 u32AudioSamplerate;		/* “Ù∆µ≤…—˘¬  */
//        Easy_U32 u32AudioChannel;			/* “Ù∆µÕ®µ¿ ˝ */
//        Easy_U32 u32AudioBitsPerSample;		/* “Ù∆µ≤…—˘æ´∂» */
//
//        Easy_U32 u32H264SpsLength;			/*  ”∆µsps÷°≥§∂» */
//        Easy_U32 u32H264PpsLength;			/*  ”∆µpps÷°≥§∂» */
//        Easy_U8	 u8H264Sps[128];			/*  ”∆µsps÷°ƒ⁄»› */
//        Easy_U8	 u8H264Pps[36];				/*  ”∆µsps÷°ƒ⁄»› */

        int videoCodec;
        int fps;
        int audioCodec;
        int sample;
        int channel;
        int bitPerSample;
        int spsLen;
        int ppsLen;
        byte[] sps;
        byte[] pps;


        @Override
        public String toString() {
            return "MediaInfo{" +
                    "videoCodec=" + videoCodec +
                    ", fps=" + fps +
                    ", audioCodec=" + audioCodec +
                    ", sample=" + sample +
                    ", channel=" + channel +
                    ", bitPerSample=" + bitPerSample +
                    ", spsLen=" + spsLen +
                    ", ppsLen=" + ppsLen +
                    '}';
        }
    }

    public interface RTSPSourceCallBack {
        void onRTSPSourceCallBack(int _channelId, int _channelPtr, int _frameType, FrameInfo frameInfo);

        void onMediaInfoCallBack(int _channelId, MediaInfo mi);

        void onEvent(int _channelId, int err, int info);
    }


    public static final int EASY_SDK_VIDEO_FRAME_FLAG = 0x01;
    public static final int EASY_SDK_AUDIO_FRAME_FLAG = 0x02;
    public static final int EASY_SDK_EVENT_FRAME_FLAG = 0x04;
    public static final int EASY_SDK_RTP_FRAME_FLAG = 0x08;		/* RTP帧标志 */
    public static final int EASY_SDK_SDP_FRAME_FLAG = 0x10;		/* SDP帧标志 */
    public static final int EASY_SDK_MEDIA_INFO_FLAG = 0x20;		/* 媒体类型标志*/

    public static final int EASY_SDK_EVENT_CODEC_ERROR = 0x63657272;	/* ERROR */
    public static final int EASY_SDK_EVENT_CODEC_EXIT = 0x65786974;	/* EXIT */

    public static final int TRANSTYPE_TCP = 1;
    public static final int TRANSTYPE_UDP = 2;
    private static final String TAG = RTSPClient.class.getSimpleName();

    static {
        System.loadLibrary("EasyRTSPClient");
    }

    private long mCtx;
    private static final SparseArray<RTSPSourceCallBack> sCallbacks = new SparseArray<>();

    RTSPClient(Context context, String key) {
        if (key == null) {
            throw new NullPointerException();
        }
        if (context == null) {
            throw new NullPointerException();
        }
        mCtx = init(context, key);
        if (mCtx == 0 || mCtx == -1) {
            throw new IllegalArgumentException("初始化失败，KEY不合法！");
        }
    }

    int registerCallback(RTSPSourceCallBack cb) {
        synchronized (sCallbacks) {
            sCallbacks.put(++sKey, cb);
            return sKey;
        }
    }

    void unrigisterCallback(RTSPSourceCallBack cb) {
        synchronized (sCallbacks) {
            int idx = sCallbacks.indexOfValue(cb);
            if (idx != -1) {
                sCallbacks.removeAt(idx);
            }
        }
    }

    public int getLastErrorCode() {
        return getErrorCode(mCtx);
    }

    public int openStream(int channel, String url, int type, int mediaType, String user, String pwd) {
        return openStream(mCtx, channel, url, type, mediaType, user, pwd);
    }

    public void closeStream() {
        closeStream(mCtx);
    }

    private static native int getErrorCode(long context);

    private native long init(Context context, String key);

    private native int deInit(long context);

    private int openStream(long context, int channel, String url, int trans_type, int mediaType, String user, String pwd) {
        if (null == url) {
            throw new NullPointerException();
        }
        return openStream(context, channel, url, trans_type, mediaType, user, pwd, 1000, 0);
    }

    private native int openStream(long context, int channel, String url, int type, int mediaType, String user, String pwd, int reconn, int outRtpPacket);

//    private native int startRecord(int context, String path);
//
//    private native void stopRecord(int context);

    private native void closeStream(long context);

    private static void onRTSPSourceCallBack(int _channelId, int _channelPtr, int _frameType, byte[] pBuf, byte[] frameBuffer) {
        if (_frameType == 0) {
            synchronized (sCallbacks) {
                final RTSPSourceCallBack callBack = sCallbacks.get(_channelId);
                if (callBack != null) {
                    callBack.onRTSPSourceCallBack(_channelId, _channelPtr, _frameType, null);
                }
            }
            return;
        }

        if (_frameType == EASY_SDK_MEDIA_INFO_FLAG) {
            synchronized (sCallbacks) {
                final RTSPSourceCallBack callBack = sCallbacks.get(_channelId);
                if (callBack != null) {
                    MediaInfo mi = new MediaInfo();

                    ByteBuffer buffer = ByteBuffer.wrap(pBuf);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    mi.videoCodec = buffer.getInt();
                    mi.fps = buffer.getInt();
                    mi.audioCodec = buffer.getInt();
                    mi.sample = buffer.getInt();
                    mi.channel = buffer.getInt();
                    mi.bitPerSample = buffer.getInt();
                    mi.spsLen = buffer.getInt();
                    mi.ppsLen = buffer.getInt();
                    mi.sps = new byte[128];
                    mi.pps = new byte[36];

                    buffer.get(mi.sps);
                    buffer.get(mi.pps);
//                    int videoCodec;int fps;
//                    int audioCodec;int sample;int channel;int bitPerSample;
//                    int spsLen;
//                    int ppsLen;
//                    byte[]sps;
//                    byte[]pps;

                    callBack.onMediaInfoCallBack(_channelId, mi);
                }
            }
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(frameBuffer);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        FrameInfo fi = new FrameInfo();
        fi.codec = buffer.getInt();
        fi.type = buffer.getInt();
        fi.fps = buffer.get();
        buffer.get();
        fi.width = buffer.getShort();
        fi.height = buffer.getShort();
        buffer.getInt();
        buffer.getInt();
        buffer.getShort();
        fi.sample_rate = buffer.getInt();
        fi.channels = buffer.getInt();
        fi.bits_per_sample = buffer.getInt();
        fi.length = buffer.getInt();
        fi.timestamp_usec = buffer.getInt();
        fi.timestamp_sec = buffer.getInt();

        long sec = fi.timestamp_sec < 0 ? Integer.MAX_VALUE - Integer.MIN_VALUE + 1 + fi.timestamp_sec : fi.timestamp_sec;
        long usec = fi.timestamp_usec < 0 ? Integer.MAX_VALUE - Integer.MIN_VALUE + 1 + fi.timestamp_usec : fi.timestamp_usec;
        fi.stamp = sec * 1000000 + usec;

//        long differ = fi.stamp - mPreviewStamp;
//        Log.d(TAG, String.format("%s:%d,%d,%d, %d", EASY_SDK_VIDEO_FRAME_FLAG == _frameType ? "视频" : "音频", fi.stamp, fi.timestamp_sec, fi.timestamp_usec, differ));
        fi.buffer = pBuf;

        synchronized (sCallbacks) {
            final RTSPSourceCallBack callBack = sCallbacks.get(_channelId);
            if (callBack != null) {
                callBack.onRTSPSourceCallBack(_channelId, _channelPtr, _frameType, fi);
            }
        }
    }

    private static void onEvent(int channel, int err, int state) {
        //state :  1  Connecting     2 : 连接错误    3 : 连接线程退出
        Log.e(TAG, String.format("__RTSPClientCallBack onEvent: err=%d, state=%d", err, state));

        synchronized (sCallbacks) {
            final RTSPSourceCallBack callBack = sCallbacks.get(channel);
            if (callBack != null) {
                callBack.onEvent(channel, err, state);
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (mCtx == 0) throw new IOException("not opened or already closed");
        deInit(mCtx);
        mCtx = 0;
    }
}
