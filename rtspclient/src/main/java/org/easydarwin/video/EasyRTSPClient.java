package org.easydarwin.video;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.AudioTrack;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import org.easydarwin.audio.AudioCodec;
import org.easydarwin.audio.EasyAACMuxer;
import org.easydarwin.push.InitCallback;
import org.easydarwin.push.Pusher;
import org.easydarwin.util.CodecSpecificDataUtil;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static org.easydarwin.util.CodecSpecificDataUtil.AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE;
import static org.easydarwin.video.RTSPClient.TRANSTYPE_TCP;

public class EasyRTSPClient implements RTSPClient.RTSPSourceCallBack {
    private static final long LEAST_FRAME_INTERVAL = 10000l;

    /* 视频编码 */
    public static final int EASY_SDK_VIDEO_CODEC_H264 = 0x1C;		/* H264  */
    public static final int EASY_SDK_VIDEO_CODEC_MJPEG = 0x08;/* MJPEG */
    public static final int EASY_SDK_VIDEO_CODEC_MPEG4 = 0x0D;/* MPEG4 */

    /* 音频编码 */
    public static final int EASY_SDK_AUDIO_CODEC_AAC = 0x15002;		/* AAC */
    public static final int EASY_SDK_AUDIO_CODEC_G711U = 0x10006;		/* G711 ulaw*/
    public static final int EASY_SDK_AUDIO_CODEC_G711A = 0x10007;	/* G711 alaw*/
    public static final int EASY_SDK_AUDIO_CODEC_G726 = 0x1100B;	/* G726 */

    /*
    *0 - 100位EasyRTSPClient msg    100 - 200位RTMP msg
    * /
     */
    /**
     * 表示视频显示出来了
     */
    public static final int RESULT_VIDEO_DISPLAYED = 01;
    /**
     * 表示视频的尺寸获取到了。具体尺寸见 EXTRA_VIDEO_WIDTH、EXTRA_VIDEO_HEIGHT
     */
    public static final int RESULT_VIDEO_SIZE = 02;
    /**
     * 表示KEY的可用播放时间已用完
     */
    public static final int RESULT_TIMEOUT = 03;
    /**
     * 表示KEY的可用播放时间已用完
     */
    public static final int RESULT_EVENT = 04;
    public static final int RESULT_UNSUPPORTED_VIDEO = 05;
    public static final int RESULT_UNSUPPORTED_AUDIO = 06;
    public static final int RESULT_RECORD_BEGIN = 7;
    public static final int RESULT_RECORD_END = 8;

    /**
     * 表示第一帧数据已经收到
     */
    public static final int RESULT_FRAME_RECVED = 9;

    private static final String TAG = EasyRTSPClient.class.getSimpleName();
    /**
     * 表示视频的宽度
     */
    public static final String EXTRA_VIDEO_WIDTH = "extra-video-width";
    /**
     * 表示视频的高度
     */
    public static final String EXTRA_VIDEO_HEIGHT = "extra-video-height";

    private Pusher mPusher;
    private String mServerIP;
    private String mServerPort;
    private String mStreamName;
    public static String EASYPUSHER_KEY;
    private InitCallback mRtmpCallBack;

    private final String mKey;
//    private final SurfaceTexture mTexture;
    private Surface mSurface;
    private volatile Thread mThread, mAudioThread;
    private final ResultReceiver mRR;
    private RTSPClient mClient;
    private boolean mAudioEnable = true;
    private volatile long mReceivedDataLength;
    private AudioTrack mAudioTrack;
    private String mRecordingPath;
    private EasyAACMuxer mObject;
    private RTSPClient.MediaInfo mMediaInfo;
    private short mHeight = 0;
    short mWidth = 0;
    private ByteBuffer mCSD0;
    private ByteBuffer mCSD1;

//    private RtmpClient mRTMPClient = new RtmpClient();

    public boolean isRecording() {
        return !TextUtils.isEmpty(mRecordingPath);
    }

    private static class FrameInfoQueue extends PriorityQueue<RTSPClient.FrameInfo> {
        public static final int CAPACITY = 500;
        public static final int INITIAL_CAPACITY = 300;

        public FrameInfoQueue() {
            super(INITIAL_CAPACITY, new Comparator<RTSPClient.FrameInfo>() {

                @Override
                public int compare(RTSPClient.FrameInfo frameInfo, RTSPClient.FrameInfo t1) {
                    return (int) (frameInfo.stamp - t1.stamp);
                }
            });
        }

        final ReentrantLock lock = new ReentrantLock();
        final Condition notFull = lock.newCondition();
        final Condition notVideo = lock.newCondition();
        final Condition notAudio = lock.newCondition();

        @Override
        public int size() {
            lock.lock();
            try {
                return super.size();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void clear() {
            lock.lock();
            try {
                int size = super.size();
                super.clear();
                int k = size;
                for (; k > 0 && lock.hasWaiters(notFull); k--)
                    notFull.signal();
            } finally {
                lock.unlock();
            }
        }

        public void put(RTSPClient.FrameInfo x) throws InterruptedException {
            lock.lockInterruptibly();
            try {
                int size;
                while ((size = super.size()) == CAPACITY) {
                    Log.v(TAG, "queue full:" + CAPACITY);
                    notFull.await();
                }
                offer(x);
//                Log.d(TAG, String.format("queue size : " + size));
                // 这里是乱序的。并非只有空的queue才丢到首位。因此不能做限制 if (size == 0)
                {

                    if (x.audio) {
                        notAudio.signal();
                    } else {
                        notVideo.signal();
                    }
                }

            } finally {
                lock.unlock();
            }
        }

        public RTSPClient.FrameInfo takeVideoFrame() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (true) {
                    RTSPClient.FrameInfo x = peek();
                    if (x == null) {
                        notVideo.await();
                    } else {
                        if (!x.audio) {
                            remove();
                            notFull.signal();
                            notAudio.signal();
                            return x;
                        } else {
                            notVideo.await();
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        public RTSPClient.FrameInfo takeVideoFrame(long ms) throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (true) {
                    RTSPClient.FrameInfo x = peek();
                    if (x == null) {
                        if (!notVideo.await(ms, TimeUnit.MILLISECONDS)) return null;
                    } else {
                        if (!x.audio) {
                            remove();
                            notFull.signal();
                            notAudio.signal();
                            return x;
                        } else {
                            notVideo.await();
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        public RTSPClient.FrameInfo takeAudioFrame() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (true) {
                    RTSPClient.FrameInfo x = peek();
                    if (x == null) {
                        notAudio.await();
                    } else {
                        if (x.audio) {
                            remove();
                            notFull.signal();
                            notVideo.signal();
                            return x;
                        } else {
                            notAudio.await();
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private FrameInfoQueue mQueue = new FrameInfoQueue();


    private final Context mContext;
    private boolean mWaitingKeyFrame;
    private boolean mTimeout;
    private boolean mNotSupportedVideoCB, mNotSupportedAudioCB;

    /**
     * 创建SDK对象
     *
     * @param context 上下文对象
     * @param rtspclientKey   EasyRTSPClient SDK key
     * @param surface 显示视频用的surface
     */
    public EasyRTSPClient(Context context, String rtspclientKey, SurfaceTexture surface, ResultReceiver receiver) {
//        mTexture = surface;
//        mSurface = new Surface(surface);
        mContext = context;
        mKey = rtspclientKey;
        mRR = receiver;
    }


    /**
     * 启动播放
     *
     * @param url
     * @param type
     * @param mediaType
     * @param user
     * @param pwd
     * @return
     */
    public int start(final String url, int type, int mediaType, String user, String pwd) {
        return start(url, type, mediaType, user, pwd, null);
    }

    /**
     * 启动播放
     *
     * @param url
     * @param type
     * @param mediaType
     * @param user
     * @param pwd
     * @return
     */
    public int start(final String url, int type, int mediaType, String user, String pwd, String recordPath) {
        if (url == null) {
            throw new NullPointerException("url is null");
        }
        if (type == 0)
            type = TRANSTYPE_TCP;

        mWaitingKeyFrame = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("waiting_i_frame", true);
        mWidth = mHeight = 0;
        mQueue.clear();
//        startCodec();
        startAudio();
        mTimeout = false;
        mNotSupportedVideoCB = mNotSupportedAudioCB = false;
        mReceivedDataLength = 0;
        mClient = new RTSPClient(mContext, mKey);
        int channel = mClient.registerCallback(this);
        mRecordingPath = recordPath;
        Log.i(TAG, String.format("playing url:\n%s\n", url));
        return mClient.openStream(channel, url, type, mediaType, user, pwd);
    }

    public boolean isAudioEnable() {
        return mAudioEnable;
    }

    public void setAudioEnable(boolean enable) {
        mAudioEnable = enable;
        AudioTrack at = mAudioTrack;
        if (at != null) {
            synchronized (at) {
                if (!enable) {
                    at.pause();
                    at.flush();
                } else {
                    at.flush();
                    at.play();
                }
            }
        }
    }

    /**
     * 终止播放
     */
    public void stop() {
        Thread t = mThread;
//        mThread = null;
//        t.interrupt();
//        try {
//            t.join();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

        t = mAudioThread;
        mAudioThread = null;
        t.interrupt();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        stopRecord();

        mQueue.clear();
        if (mClient != null) {
            mClient.unrigisterCallback(this);
            mClient.closeStream();
            try {
                mClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mQueue.clear();
        mClient = null;

        if(mPusher != null){
            mPusher.stop();
            mPusher = null;
        }
    }

    public long receivedDataLength() {
        return mReceivedDataLength;
    }

    private void startAudio() {
        mAudioThread = new Thread("AUDIO_CONSUMER") {

            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void run() {
                {
                    RTSPClient.FrameInfo frameInfo;
                    long handle = 0;
//                    final AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
//                    AudioManager.OnAudioFocusChangeListener l = new AudioManager.OnAudioFocusChangeListener() {
//                        @Override
//                        public void onAudioFocusChange(int focusChange) {
//                            if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
//                                AudioTrack audioTrack = mAudioTrack;
//                                if (audioTrack != null) {
//                                    audioTrack.setStereoVolume(1.0f, 1.0f);
//                                    if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PAUSED) {
//                                        audioTrack.flush();
//                                        audioTrack.play();
//                                    }
//                                }
//                            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
//                                AudioTrack audioTrack = mAudioTrack;
//                                if (audioTrack != null) {
//                                    if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
//                                        audioTrack.pause();
//                                    }
//                                }
//                            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
//                                AudioTrack audioTrack = mAudioTrack;
//                                if (audioTrack != null) {
//                                    audioTrack.setStereoVolume(0.5f, 0.5f);
//                                }
//                            }
//                        }
//                    };
                    try {
                        frameInfo = mQueue.takeAudioFrame();
//                        final Thread t = Thread.currentThread();
//                        int requestCode = am.requestAudioFocus(l, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
//                        if (requestCode !=  AUDIOFOCUS_REQUEST_GRANTED){
//                            return;
//                        }
//                        if (mAudioTrack == null) {
//                            int sampleRateInHz = (int) (frameInfo.sample_rate * 1.05);
//                            int channelConfig = frameInfo.channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
//                            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
//                            int bfSize = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat) * 4;
//                            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz, channelConfig, audioFormat, bfSize, AudioTrack.MODE_STREAM);
//                        }
//                        mAudioTrack.play();
                        handle = AudioCodec.create(frameInfo.codec, frameInfo.sample_rate, frameInfo.channels, 16);

                        Log.w(TAG, String.format("POST VIDEO_DISPLAYED IN AUDIO THREAD!!!"));
                        ResultReceiver rr = mRR;
                        if (rr != null) rr.send(RESULT_VIDEO_DISPLAYED, null);

                        // 半秒钟的数据缓存
                        byte[] mBufferReuse = new byte[16000];
                        int[] outLen = new int[1];
                        while (mAudioThread != null) {
                            if (frameInfo == null) {
                                frameInfo = mQueue.takeAudioFrame();
                            }
                            if (frameInfo.codec == EASY_SDK_AUDIO_CODEC_AAC) {
                                //pumpAACSample(frameInfo);
                                continue;
                            }
                            outLen[0] = mBufferReuse.length;
                            long ms = SystemClock.currentThreadTimeMillis();
                            int nRet = AudioCodec.decode((int) handle, frameInfo.buffer, 0, frameInfo.length, mBufferReuse, outLen);
                            if (nRet == 0) {
                                if (frameInfo.codec != EASY_SDK_AUDIO_CODEC_AAC) {
                                    pumpPCMSample(mBufferReuse, outLen[0], frameInfo.stamp);
                                }
                                //mAudioTrack.write(mBufferReuse, 0, outLen[0]);
                            }
                            frameInfo = null;
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        //am.abandonAudioFocus(l);
                        if (handle != 0) {
                            AudioCodec.close((int) handle);
                        }
//                        AudioTrack track = mAudioTrack;
//                        if (track != null) {
//                            synchronized (track) {
//                                mAudioTrack = null;
//                                track.release();
//                            }
//                        }
                    }
                }
            }
        };

        mAudioThread.start();
    }


    private static void save2path(byte[] buffer, int offset, int length, String path, boolean append) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(path, append);
            fos.write(buffer, offset, length);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static int getXPS(byte[] data, int offset, int length, byte[] dataOut, int[] outLen, int type) {
        int i;
        int pos0;
        int pos1;
        pos0 = -1;
        for (i = offset; i < length - 4; i++) {
            if ((0 == data[i]) && (0 == data[i + 1]) && (1 == data[i + 2]) && (type == (0x0F & data[i + 3]))) {
                pos0 = i;
                break;
            }
        }
        if (-1 == pos0) {
            return -1;
        }
        if (pos0 > 0 && data[pos0-1] == 0){ // 0 0 0 1
            pos0 = pos0-1;
        }
        pos1 = -1;
        for (i = pos0 + 4; i < length - 4; i++) {
            if ((0 == data[i]) && (0 == data[i + 1]) && (1 == data[i + 2])) {
                pos1 = i;
                break;
            }
        }
        if (-1 == pos1 || pos1 == 0) {
            return -2;
        }
        if (data[pos1 - 1] == 0) {
            pos1 -= 1;
        }
        if (pos1 - pos0 > outLen[0]) {
            return -3; // 输入缓冲区太小
        }
        dataOut[0] = 0;
        System.arraycopy(data, pos0, dataOut, 0, pos1 - pos0);
        // memcpy(pXPS+1, pES+pos0, pos1-pos0);
        // *pMaxXPSLen = pos1-pos0+1;
        outLen[0] = pos1 - pos0 ;
        return pos1;
    }

    private static boolean codecMatch(String mimeType, MediaCodecInfo codecInfo) {
        String[] types = codecInfo.getSupportedTypes();
        for (String type : types) {
            if (type.equalsIgnoreCase(mimeType)) {
                return true;
            }
        }
        return false;
    }

    private static String codecName() {
        ArrayList<String> array = new ArrayList<>();
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i1 = 0; i1 < numCodecs; i1++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i1);
            if (codecInfo.isEncoder()) {
                continue;
            }

            if (codecMatch("video/avc", codecInfo)) {
                String name = codecInfo.getName();
                Log.d("DECODER", String.format("decoder:%s", name));
                array.add(name);
            }
        }
//        if (array.remove("OMX.qcom.video.decoder.avc")) {
//            array.add("OMX.qcom.video.decoder.avc");
//        }
//        if (array.remove("OMX.amlogic.avc.decoder.awesome")) {
//            array.add("OMX.amlogic.avc.decoder.awesome");
//        }
        if (array.isEmpty()) {
            return "";
        }
        return array.get(0);
    }

//    private void startCodec() {
//        final int delayUS = PreferenceManager.getDefaultSharedPreferences(mContext).getInt("delayUs", 0);
//        mThread = new Thread("VIDEO_CONSUMER") {
//
//            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
//            @Override
//            public void run() {
//                Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
//                MediaCodec mCodec = null;
//                VideoCodec.VideoDecoderLite mDecoder = null;
//                try {
//                    boolean pushBlankBuffersOnStop = true;
//
//                    int index = 0;
//                    long previewStampUs = 0l;
//                    long previewTickUs = 0l;
//                    long differ = 0;
//
//                    long previewStampUs1 = 0;
//                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
//                    while (mThread != null) {
//                        RTSPClient.FrameInfo frameInfo;
//                        if (mCodec == null && mDecoder == null) {
//                            frameInfo = mQueue.takeVideoFrame();
//                            try {
//
//                                MediaFormat format = MediaFormat.createVideoFormat("video/avc", mWidth, mHeight);
//                                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
//                                format.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, pushBlankBuffersOnStop ? 1 : 0);
//                                if (mCSD0 != null) {
//                                    format.setByteBuffer("csd-0", mCSD0);
//                                } else {
//                                    throw new InvalidParameterException("csd-0 is invalid.");
//                                }
//                                if (mCSD1 != null) {
//                                    format.setByteBuffer("csd-1", mCSD1);
//                                } else {
//                                    throw new InvalidParameterException("csd-1 is invalid.");
//                                }
//                                MediaCodec codec = MediaCodec.createDecoderByType("video/avc");
//                                Log.i(TAG, String.format("config codec:%s", format));
//                                codec.configure(format, mSurface, null, 0);
//                                codec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
//                                codec.start();
//                                mCodec = codec;
//                            } catch (Throwable e) {
//                                Log.e(TAG, String.format("init codec error due to %s", e.getMessage()));
//                                e.fillInStackTrace();
//                                final VideoCodec.VideoDecoderLite decoder = new VideoCodec.VideoDecoderLite();
//                                decoder.create(frameInfo.width, frameInfo.height, mSurface);
//                                mDecoder = decoder;
//                            }
////                            previewTickUs = mTexture.getTimestamp();
//                            differ = previewTickUs - frameInfo.stamp;
//                        } else {
//                            frameInfo = mQueue.takeVideoFrame(5);
//                        }
//                        if (frameInfo != null) {
//                            Log.d(TAG, "video " + frameInfo.stamp + " take[" + (frameInfo.stamp - previewStampUs1) + "]");
//                            previewStampUs1 = frameInfo.stamp;
//                        }
//
//                        if (mDecoder != null) {
//                            long decodeBegin = System.currentTimeMillis();
//                            mDecoder.decodeAndSnapAndDisplay(frameInfo);
//                            long decodeSpend = System.currentTimeMillis() - decodeBegin;
//
//                            boolean firstFrame = previewStampUs == 0l;
//                            if (firstFrame) {
//                                Log.i(TAG, String.format("POST VIDEO_DISPLAYED!!!"));
//                                ResultReceiver rr = mRR;
//                                if (rr != null) rr.send(RESULT_VIDEO_DISPLAYED, null);
//                            }
//                            long current = frameInfo.stamp;
//
//                            if (previewStampUs != 0l) {
//                                long sleepTime = current - previewStampUs - decodeSpend * 1000;
//                                if (sleepTime > 0) {
//                                    long cache = mNewestStample - frameInfo.stamp;
//                                    sleepTime = fixSleepTime(sleepTime * 1000, cache, 0);
//                                    if (sleepTime > 0) {
//                                        Thread.sleep(sleepTime / 1000);
//                                    }
//                                }
//                            }
//                            previewStampUs = current;
//                        } else {
//                            do {
//                                if (frameInfo != null) {
//                                    byte[] pBuf = frameInfo.buffer;
//                                    pumpVideoSample(frameInfo);
//                                    index = mCodec.dequeueInputBuffer(10);
//                                    if (index >= 0) {
//                                        ByteBuffer buffer = mCodec.getInputBuffers()[index];
//                                        buffer.clear();
//                                        if (pBuf.length > buffer.remaining()) {
//                                            mCodec.queueInputBuffer(index, 0, 0, frameInfo.stamp, 0);
//                                        } else {
//                                            buffer.put(pBuf, frameInfo.offset, frameInfo.length);
//                                            mCodec.queueInputBuffer(index, 0, buffer.position(), frameInfo.stamp + differ, 0);
//                                        }
//                                        frameInfo = null;
//                                    }
//                                }
//                                index = mCodec.dequeueOutputBuffer(info, 10); //
//                                switch (index) {
//                                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
//                                        Log.i(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
//                                        break;
//                                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
//                                        MediaFormat mf = mCodec.getOutputFormat();
//                                        Log.i(TAG, "INFO_OUTPUT_FORMAT_CHANGED ：" +mf);
//                                        break;
//                                    case MediaCodec.INFO_TRY_AGAIN_LATER:
//                                        // 输出为空
//                                        break;
//                                    default:
//                                        // 输出队列不为空
//                                        // -1表示为第一帧数据
//                                        long newSleepUs = -1;
//                                        boolean firstTime = previewStampUs == 0l;
//                                        if (!firstTime) {
//                                            long sleepUs = (info.presentationTimeUs - previewStampUs);
//                                            if (sleepUs > 1000000) {
//                                                // 时间戳异常，可能服务器丢帧了。
//                                                newSleepUs = 0l;
//                                            } else {
//                                                long cache = mNewestStample - previewStampUs;
//                                                newSleepUs = fixSleepTime(sleepUs, cache, 000000);
////                                        Log.d(TAG, String.format("sleepUs:%d,newSleepUs:%d,Cache:%d", sleepUs, newSleepUs, cache));
//                                            }
//                                        }
//                                        previewStampUs = info.presentationTimeUs;
//
//                                        if (true && Build.VERSION.SDK_INT >= 21) {
//                                            Log.d(TAG, String.format("releaseoutputbuffer:%d,stampUs:%d", index, previewStampUs));
//                                            mCodec.releaseOutputBuffer(index, previewStampUs);
//                                        } else {
//                                            if (newSleepUs < 0) {
//                                                newSleepUs = 0;
//                                            }
//                                            Thread.sleep(newSleepUs / 1000);
//                                            mCodec.releaseOutputBuffer(index, true);
//                                        }
//                                        if (firstTime) {
//                                            Log.i(TAG, String.format("POST VIDEO_DISPLAYED!!!"));
//                                            ResultReceiver rr = mRR;
//                                            if (rr != null) rr.send(RESULT_VIDEO_DISPLAYED, null);
//                                        }
//                                }
//                            } while (frameInfo != null || index < MediaCodec.INFO_TRY_AGAIN_LATER);
//                        }
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                } finally {
//                    if (mCodec != null) {
//                        mCodec.stop();
//                        mCodec.release();
//                    }
//                    if (mDecoder != null) {
//                        mDecoder.close();
//                    }
//                }
//            }
//        };
//        mThread.start();
//    }

    private static final long fixSleepTime(long sleepTimeUs, long totalTimestampDifferUs, long delayUs) {
        double dValue = ((double) (delayUs - totalTimestampDifferUs)) / 1000000d;
        double radio = Math.exp(dValue);
        final double r = sleepTimeUs * radio + 0.5f;
        return (long) r;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public synchronized void startRecord(String path) {
        if (mMediaInfo == null)
            return;
        mRecordingPath = path;

        MediaFormat format = new MediaFormat();
//        format.setInteger(MediaFormat.KEY_WIDTH, mWidth);
//        format.setInteger(MediaFormat.KEY_HEIGHT, mHeight);
//        mCSD0.clear();
//        format.setByteBuffer("csd-0", mCSD0);
//        mCSD1.clear();
//        format.setByteBuffer("csd-1", mCSD1);
//        format.setString(MediaFormat.KEY_MIME, "video/avc");
//        format.setInteger(MediaFormat.KEY_FRAME_RATE, 0);
////        format.setInteger(MediaFormat.KEY_BIT_RATE, mWidth*mHeight*0.7*2);
//        mObject.addTrack(format, true);
//
//        format = new MediaFormat();
        int audioObjectType = 2;
        int sampleRateIndex = getSampleIndex(mMediaInfo.sample);
        int channelConfig = mMediaInfo.channel;
        byte[] audioSpecificConfig = CodecSpecificDataUtil.buildAacAudioSpecificConfig(audioObjectType, sampleRateIndex, channelConfig);
        Pair<Integer, Integer> audioParams = CodecSpecificDataUtil.parseAacAudioSpecificConfig(audioSpecificConfig);
//                                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, audioParams.second);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, audioParams.first);

        List<byte[]> bytes = Collections.singletonList(audioSpecificConfig);
        for (int j = 0; j < bytes.size(); j++) {
            format.setByteBuffer("csd-" + j, ByteBuffer.wrap(bytes.get(j)));
        }
        mObject = new EasyAACMuxer(format);
        //mObject.addTrack(format, false);

        mObject.SetPusher(mPusher);

        ResultReceiver rr = mRR;
        if (rr != null) {
            rr.send(RESULT_RECORD_BEGIN, null);
        }
    }

    private static int getSampleIndex(int sample) {
        for (int i = 0; i < AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE.length; i++) {
            if (sample == AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[i]) {
                return i;
            }
        }
        return -1;
    }

//    private void pumpAACSample(RTSPClient.FrameInfo frameInfo) {
//        EasyMuxer muxer = mObject;
//        if (muxer == null) return;
//        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
//        bi.offset = frameInfo.offset;
//        bi.size = frameInfo.length;
//        ByteBuffer buffer = ByteBuffer.wrap(frameInfo.buffer, bi.offset, bi.size);
//        bi.presentationTimeUs = frameInfo.stamp;
//
//        try {
//            if (!frameInfo.audio) {
//                throw new IllegalArgumentException("frame should be audio!");
//            }
//            if (frameInfo.codec != EASY_SDK_AUDIO_CODEC_AAC){
//                throw new IllegalArgumentException("audio codec should be aac!");
//            }
//            bi.offset += 7;
//            bi.size -= 7;
//            muxer.pumpStream(buffer, bi, false);
//        } catch (IllegalStateException ex) {
//            ex.printStackTrace();
//        }
//    }


    private void pumpPCMSample(byte[] pcm, int length, long stampUS) {
        EasyAACMuxer muxer = mObject;
        if (muxer == null) return;
        try {
            muxer.pumpPCMStream(pcm, length, stampUS);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


//    private void pumpVideoSample(RTSPClient.FrameInfo frameInfo) {
//        EasyMuxer muxer = mObject;
//        if (muxer == null) return;
//        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
//        bi.offset = frameInfo.offset;
//        bi.size = frameInfo.length;
//        ByteBuffer buffer = ByteBuffer.wrap(frameInfo.buffer, bi.offset, bi.size);
//        bi.presentationTimeUs = frameInfo.stamp;
//        try {
//            if (frameInfo.audio) {
//                throw new IllegalArgumentException("frame should be video!");
//            }
//            if (frameInfo.type != 1) {
//                bi.flags = 0;
//            } else {
//                bi.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
//            }
//            muxer.pumpStream(buffer, bi, true);
//        } catch (IllegalStateException ex) {
//            ex.printStackTrace();
//        }
//    }

    public synchronized void stopRecord() {
        mRecordingPath = null;
        if (mObject == null) return;
        mObject.release();
        mObject = null;
        ResultReceiver rr = mRR;
        if (rr != null) {
            rr.send(RESULT_RECORD_END, null);
        }
    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onRTSPSourceCallBack(int _channelId, int _channelPtr, int _frameType, RTSPClient.FrameInfo frameInfo) {
        Thread.currentThread().setName("PRODUCER_THREAD");
        if (frameInfo != null) {
            mReceivedDataLength += frameInfo.length;
        }
        if (_frameType == RTSPClient.EASY_SDK_VIDEO_FRAME_FLAG) {
            if (frameInfo.codec != EASY_SDK_VIDEO_CODEC_H264) {
                ResultReceiver rr = mRR;
                if (!mNotSupportedVideoCB && rr != null) {
                    mNotSupportedVideoCB = true;
                    rr.send(RESULT_UNSUPPORTED_VIDEO, null);
                }
                return;
            }
//            save2path(frameInfo.buffer, 0, frameInfo.length, "/sdcard/264.h264", true);
            if (frameInfo.width == 0 || frameInfo.height == 0) {
                return;
            }

            if (frameInfo.length >= 4) {
                if (frameInfo.buffer[0] == 0 && frameInfo.buffer[1] == 0 && frameInfo.buffer[2] == 0 && frameInfo.buffer[3] == 1) {
                    if (frameInfo.length >= 8) {
                        if (frameInfo.buffer[4] == 0 && frameInfo.buffer[5] == 0 && frameInfo.buffer[6] == 0 && frameInfo.buffer[7] == 1) {
                            frameInfo.offset += 4;
                            frameInfo.length -= 4;
                        }
                    }
                }
            }

//            int offset = frameInfo.offset;
//            byte nal_unit_type = (byte) (frameInfo.buffer[offset + 4] & (byte) 0x1F);
//            if (nal_unit_type == 7 || nal_unit_type == 5) {
//                Log.i(TAG,String.format("recv I frame"));
//            }

            if (frameInfo.type == 1) {
                Log.i(TAG, String.format("recv I frame"));
            }

//            boolean firstFrame = mNewestStample == 0;
            frameInfo.stamp = frameInfo.timestamp_sec*1000+frameInfo.timestamp_usec;
            frameInfo.audio = false;
            if (mWaitingKeyFrame) {

                ResultReceiver rr = mRR;
                Bundle bundle = new Bundle();
                bundle.putInt(EXTRA_VIDEO_WIDTH, frameInfo.width);
                bundle.putInt(EXTRA_VIDEO_HEIGHT, frameInfo.height);
                mWidth = frameInfo.width;
                mHeight = frameInfo.height;


                Log.i(TAG, String.format("width:%d,height:%d", mWidth, mHeight));

//                byte[] dataOut = new byte[128];
//                int[] outLen = new int[]{128};
//                int result = getXPS(frameInfo.buffer, 0, frameInfo.buffer.length, dataOut, outLen, 7);
//                if (result >= 0) {
//                    ByteBuffer csd0 = ByteBuffer.allocate(outLen[0]);
//                    csd0.put(dataOut, 0, outLen[0]);
//                    csd0.clear();
//                    mCSD0 = csd0;
//                    Log.i(TAG, String.format("CSD-0 searched"));
//                }
//                outLen[0] = 128;
//                result = getXPS(frameInfo.buffer, 0, frameInfo.buffer.length, dataOut, outLen, 8);
//                if (result >= 0) {
//                    ByteBuffer csd1 = ByteBuffer.allocate(outLen[0]);
//                    csd1.put(dataOut, 0, outLen[0]);
//                    csd1.clear();
//                    mCSD1 = csd1;
//                    Log.i(TAG, String.format("CSD-1 searched"));
//                }
//
//                if (false) {
//                    int off = (result - frameInfo.offset);
//                    frameInfo.offset += off;
//                    frameInfo.length -= off;
//                }
                Log.i(TAG, String.format("RESULT_VIDEO_SIZE:%d*%d", frameInfo.width, frameInfo.height));
                if (rr != null) rr.send(RESULT_VIDEO_SIZE, bundle);

                if (frameInfo.type != 1) {
                    Log.w(TAG, String.format("discard p frame."));
                    return;
                }
                mWaitingKeyFrame = false;
                synchronized (this) {
                    if (!TextUtils.isEmpty(mRecordingPath) && mObject == null) {
                        startRecord(mRecordingPath);
                    }
                }

                mPusher.initPush(mServerIP, mServerPort, mStreamName, mContext, mRtmpCallBack);
            }
//            Log.d(TAG, String.format("queue size :%d", mQueue.size()));
//            try {
//                mQueue.put(frameInfo);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            if(mPusher != null) {
                mPusher.push(frameInfo.buffer, frameInfo.offset, frameInfo.length, frameInfo.stamp, Pusher.FrameType.FRAME_TYPE_VIDEO);
            }
        } else if (_frameType == RTSPClient.EASY_SDK_AUDIO_FRAME_FLAG) {
            frameInfo.audio = true;
            frameInfo.stamp = frameInfo.timestamp_sec*1000+frameInfo.timestamp_usec;

            if(frameInfo.codec == EASY_SDK_AUDIO_CODEC_AAC){
                if(mPusher != null) {
                    mPusher.push(frameInfo.buffer, frameInfo.offset, frameInfo.length, frameInfo.stamp, Pusher.FrameType.FRAME_TYPE_AUDIO);
                }
            } else if(frameInfo.codec == EASY_SDK_AUDIO_CODEC_G711A ||
                    frameInfo.codec == EASY_SDK_AUDIO_CODEC_G711U ||
                    frameInfo.codec == EASY_SDK_AUDIO_CODEC_G726){
                synchronized (this) {
                    if (mObject == null) {
                        startRecord(null);
                    }
                }

                try {
                    mQueue.put(frameInfo);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                ResultReceiver rr = mRR;
                if (!mNotSupportedAudioCB && rr != null) {
                    mNotSupportedAudioCB = true;
                    if (rr != null) {
                        rr.send(RESULT_UNSUPPORTED_AUDIO, null);
                    }
                }
                return;
            }
        } else if (_frameType == 0) {
            // time out...
            if (!mTimeout) {
                mTimeout = true;

                ResultReceiver rr = mRR;
                if (rr != null) rr.send(RESULT_TIMEOUT, null);
            }
        } else if (_frameType == RTSPClient.EASY_SDK_EVENT_FRAME_FLAG) {
            ResultReceiver rr = mRR;
            Bundle resultData = new Bundle();
            resultData.putString("event-msg", new String(frameInfo.buffer));
            if (rr != null) rr.send(RESULT_EVENT, null);
        } else if(_frameType == RTSPClient.EASY_SDK_MEDIA_INFO_FLAG) {

        }
    }

    @Override
    public void onMediaInfoCallBack(int _channelId, RTSPClient.MediaInfo mi) {
        mMediaInfo = mi;
        Log.i(TAG, String.format("MediaInfo fetchd\n%s", mi));
    }

    @Override
    public void onEvent(int channel, int err, int info) {
        ResultReceiver rr = mRR;
        Bundle resultData = new Bundle();
        /*
            int state = 0;
        int err = EasyRTSP_GetErrCode(fRTSPHandle);
		// EasyRTSPClient开始进行连接，建立EasyRTSPClient连接线程
		if (NULL == _pBuf && NULL == _frameInfo)
		{
			LOGD("Recv Event: Connecting...");
			state = 1;
		}

		// EasyRTSPClient RTSPClient连接错误，错误码通过EasyRTSP_GetErrCode()接口获取，比如404
		else if (NULL != _frameInfo && _frameInfo->codec == EASY_SDK_EVENT_CODEC_ERROR)
		{
			LOGD("Recv Event: Error:%d ...\n", err);
			state = 2;
		}

		// EasyRTSPClient连接线程退出，此时上层应该停止相关调用，复位连接按钮等状态
		else if (NULL != _frameInfo && _frameInfo->codec == EASY_SDK_EVENT_CODEC_EXIT)
		{
			LOGD("Recv Event: Exit,Error:%d ...", err);
			state = 3;
		}

        * */
        switch (info) {
            case 1:
                resultData.putString("event-msg", "EasyRTSPClient 连接中...");
                break;
            case 2:
                resultData.putInt("errorcode", err);
                resultData.putString("event-msg", String.format("EasyRTSPClient 错误：%d", err));
                break;
            case 3:
                resultData.putInt("errorcode", err);
                resultData.putString("event-msg", String.format("EasyRTSPClient 线程退出。%d", err));
                break;
        }
        if (rr != null) rr.send(RESULT_EVENT, resultData);
    }

    public void setRTMPInfo(Pusher pusher, String serverIp, String serverPort, String streamName, String key, InitCallback callBack){
        mPusher = pusher;
        mServerIP = serverIp;
        mServerPort = serverPort;
        mStreamName = streamName;
        EASYPUSHER_KEY = key;
        mRtmpCallBack = callBack;
    }
}