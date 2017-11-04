package org.easydarwin.push;

import android.content.Context;


public interface Pusher {

    public static class MSG {
        //0 - 100位EasyRTSPClient msg    100 - 200位pusher msg
        public static final int Easy_VideoInfo = 100;
    }

    public static class FrameType {
        public static final int FRAME_TYPE_AUDIO = 0;
        public static final int FRAME_TYPE_VIDEO = 1;
    }

    public void stop() ;

    public  void initPush(final String serverIP, final String serverPort, final String streamName, final Context context, final InitCallback callback);
    public  void initPush(final String url, final Context context, final InitCallback callback, int pts);
    public  void initPush(final String url, final Context context, final InitCallback callback);

    public  void push(byte[] data, int offset, int length, long timestamp, int type);

    public  void push(byte[] data, long timestamp, int type);
}
