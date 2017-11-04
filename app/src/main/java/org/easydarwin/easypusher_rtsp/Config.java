/*
	Copyright (c) 2012-2017 EasyDarwin.ORG.  All rights reserved.
	Github: https://github.com/EasyDarwin
	WEChat: EasyDarwin
	Website: http://www.easydarwin.org
*/

package org.easydarwin.easypusher_rtsp;

/**
 * 类Config的实现描述：
 */
public class Config {
    public static final String RTSP_URL = "RTSPUrl";
    public static final String DEFAULT_RTSP_URL = "rtsp://admin:admin12345@192.168.1.227/Streaming/Channels/101";

    public static final String SERVER_IP = "serverIP";
    public static final String DEFAULT_SERVER_IP = "cloud.easydarwin.org";

    public static final String SERVER_PORT = "serverPort";
    public static final String DEFAULT_SERVER_PORT = "554";

    public static final String STREAM_NAME = "streamName";
    public static final String DEFAULT_STREAM_NAME = "stream_"+String.valueOf((int) (Math.random() * 1000000 + 100000))+".sdp";

}
