/*
	Copyright (c) 2012-2017 EasyDarwin.ORG.  All rights reserved.
	Github: https://github.com/EasyDarwin
	WEChat: EasyDarwin
	Website: http://www.easydarwin.org
*/
package org.easydarwin.easypusher_rtsp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.easydarwin.easyrtmp_rtsp.R;
import org.easydarwin.push.EasyPusher;
import org.easydarwin.push.InitCallback;
import org.easydarwin.push.Pusher;
import org.easydarwin.video.EasyRTSPClient;
import org.easydarwin.video.RTSPClient;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    /*
    *本Key为3个月临时授权License，如需商业使用，请邮件至support@easydarwin.org申请此产品的授权。
    */
    public static final String EasyRTSPClient_KEY = "79393674363536526D34324144766C5A7067627745754676636D63755A57467A65575268636E64706269356C59584E356348567A6147567958334A3063334170567778576F50365334456468646D6C754A6B4A68596D397A595541794D4445325257467A65555268636E6470626C526C5957316C59584E35";
    public static final String EasyPusher_KEY = "6A36334A743536526D34324144766C5A706762784A655A76636D63755A57467A65575268636E64706269356C59584E356347786865575679567778576F502B6C34456468646D6C754A6B4A68596D397A595541794D4445325257467A65555268636E6470626C526C5957316C59584E35";

    public EditText etRtspUrl;
    public EditText etServerIP;
    public EditText etServerPort;
    public EditText etStreamName;
    public Button   btStartPush;
    public TextView tvPlayAddr;
    public StatusInfoView mDbgInfoPrint;
    public LinearLayout mViewContainer;
    public TextView tvVideoInfo;

    protected EasyRTSPClient mStreamHamal;
    protected ResultReceiver mResultReceiver;
    protected Pusher mPusher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar tlToolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(tlToolbar);

        mViewContainer = (LinearLayout)findViewById(R.id.option_bar_container);

        tvVideoInfo = (TextView)findViewById(R.id.tvVideoInfo);

        etRtspUrl = (EditText)findViewById(R.id.rtsp_url);
        etServerIP = (EditText)findViewById(R.id.serverIp);
        etServerPort = (EditText)findViewById(R.id.serverPort);
        etStreamName = (EditText)findViewById(R.id.rtspname);
        tvPlayAddr = (TextView)findViewById(R.id.playaddr);
        String rtsp = EasyApplication.getEasyApplication().getRTSPUrl();
        String serverIp = EasyApplication.getEasyApplication().getServerIp();
        String serverPort = EasyApplication.getEasyApplication().getServerPort();
        String streamName = EasyApplication.getEasyApplication().getStreamName();
        etRtspUrl.setText(rtsp);
        etServerIP.setText(serverIp);
        etServerPort.setText(serverPort);
        etStreamName.setText(streamName);

        btStartPush = (Button)findViewById(R.id.btnStartPush);
        btStartPush.setOnClickListener(this);

        if(EasyApplication.getPushState()) {
            btStartPush.setText("停止推送");
            tvPlayAddr.setText("播放地址：rtsp://"+serverIp+":"+serverPort+"/"+streamName);
        } else {
            btStartPush.setText("开始推送");
            tvPlayAddr.setText("");
        }

        mDbgInfoPrint = (StatusInfoView)findViewById(R.id.tvEventMsg);
        initDbgInfoView();

        mResultReceiver = new ResultReceiver(new Handler()) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                super.onReceiveResult(resultCode, resultData);
                switch (resultCode){
                    case EasyRTSPClient.RESULT_VIDEO_SIZE:
                        int width = resultData.getInt(EasyRTSPClient.EXTRA_VIDEO_WIDTH);
                        int height = resultData.getInt(EasyRTSPClient.EXTRA_VIDEO_HEIGHT);
                        MainActivity.this.onEvent(String.format("Video Size: %d x %d", width, height));
                        break;
                    case EasyRTSPClient.RESULT_UNSUPPORTED_AUDIO:
                        new AlertDialog.Builder(MainActivity.this).setMessage("音频格式不支持").setTitle("SORRY").setPositiveButton(android.R.string.ok, null).show();
                        break;
                    case EasyRTSPClient.RESULT_UNSUPPORTED_VIDEO:
                        new AlertDialog.Builder(MainActivity.this).setMessage("视频格式不支持").setTitle("SORRY").setPositiveButton(android.R.string.ok, null).show();
                        break;
                    case EasyRTSPClient.RESULT_EVENT:
                        int errorcode = resultData.getInt("errorcode");
//                        if (errorcode != 0){
//                            StopPushing();
//                        }
                        MainActivity.this.onEvent(resultData.getString("event-msg"));
                        break;
                    case Pusher.MSG.Easy_VideoInfo:
                        tvVideoInfo.setText(resultData.getString("event-msg"));
                        break;
                }
            }
        };
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btnStartPush:
                if(EasyApplication.getPushState()) {
                    EasyApplication.setPushState(false);
                    tvPlayAddr.setText("");
                    StopPushing();
                    btStartPush.setText("开始推送");
                }else{
                    EasyApplication.setPushState(true);
                    String rtspValue = etRtspUrl.getText().toString();
                    String serverIp = etServerIP.getText().toString();
                    String serverPort = etServerPort.getText().toString();
                    String streamName = etStreamName.getText().toString();
                    if (TextUtils.isEmpty(rtspValue)) {
                        rtspValue = Config.DEFAULT_RTSP_URL;
                    }
                    if (TextUtils.isEmpty(serverIp)) {
                        serverIp = Config.DEFAULT_SERVER_IP;
                    }
                    if (TextUtils.isEmpty(serverPort)) {
                        serverPort = Config.DEFAULT_SERVER_PORT;
                    }
                    if (TextUtils.isEmpty(streamName)) {
                        streamName = Config.DEFAULT_STREAM_NAME;
                    }
                    EasyApplication.getEasyApplication().saveStringIntoPref(Config.RTSP_URL, rtspValue);
                    EasyApplication.getEasyApplication().saveStringIntoPref(Config.SERVER_IP, serverIp);
                    EasyApplication.getEasyApplication().saveStringIntoPref(Config.SERVER_PORT, serverPort);
                    EasyApplication.getEasyApplication().saveStringIntoPref(Config.STREAM_NAME, streamName);

                    btStartPush.setText("停止推送");
                    tvPlayAddr.setText("播放地址：rtsp://"+serverIp+":"+serverPort+"/"+streamName);

                    StartPushing();
                }
                break;
        }
    }

    private void initDbgInfoView() {
        if (mDbgInfoPrint == null)
            return;
        int w = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int h = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        mViewContainer.measure(w, h);
        int height = mViewContainer.getMeasuredHeight();
        int width = mViewContainer.getMeasuredWidth();

        int[] location = new int[2];
        mViewContainer.getLocationOnScreen(location);

        ViewGroup.LayoutParams lp = mDbgInfoPrint.getLayoutParams();
        lp.height = getResources().getDisplayMetrics().heightPixels - height - location[1] - 300;
        mDbgInfoPrint.setLayoutParams(lp);
        mDbgInfoPrint.requestLayout();
        mDbgInfoPrint.setInstence(mDbgInfoPrint);
    }

    public void onEvent(String msg) {
        Intent intent = new Intent(StatusInfoView.DBG_MSG);
        intent.putExtra(StatusInfoView.DBG_DATA, String.format("[%s]\t%s\n",new SimpleDateFormat("HH:mm:ss").format(new Date()),msg));
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    void StartPushing(){
        mStreamHamal = new EasyRTSPClient(this, EasyRTSPClient_KEY, null, mResultReceiver);
        String rtsp = EasyApplication.getEasyApplication().getRTSPUrl();
        String serverIp = EasyApplication.getEasyApplication().getServerIp();
        String serverPort = EasyApplication.getEasyApplication().getServerPort();
        String streamName = EasyApplication.getEasyApplication().getStreamName();
        mPusher = new EasyPusher(mResultReceiver);
        mStreamHamal.setRTMPInfo(mPusher, serverIp, serverPort, streamName, EasyPusher_KEY, new InitCallback(){

            @Override
            public void onCallback(int code) {
                Bundle resultData = new Bundle();
                switch (code) {
                    case EasyPusher.OnInitPusherCallback.CODE.EASY_ACTIVATE_INVALID_KEY:
                        resultData.putString("event-msg", "EasyPusher 无效Key");
                        break;
                    case EasyPusher.OnInitPusherCallback.CODE.EASY_ACTIVATE_SUCCESS:
                        resultData.putString("event-msg", "EasyPusher 激活成功");
                        break;
                    case EasyPusher.OnInitPusherCallback.CODE.EASY_PUSH_STATE_CONNECTING:
                        resultData.putString("event-msg", "EasyPusher 连接中");
                        break;
                    case EasyPusher.OnInitPusherCallback.CODE.EASY_PUSH_STATE_CONNECTED:
                        resultData.putString("event-msg", "EasyPusher 连接成功");
                        break;
                    case EasyPusher.OnInitPusherCallback.CODE.EASY_PUSH_STATE_CONNECT_FAILED:
                        resultData.putString("event-msg", "EasyPusher 连接失败");
                        break;
                    case EasyPusher.OnInitPusherCallback.CODE.EASY_PUSH_STATE_CONNECT_ABORT:
                        resultData.putString("event-msg", "EasyPusher 连接异常中断");
                        break;
                    case EasyPusher.OnInitPusherCallback.CODE.EASY_PUSH_STATE_PUSHING:
                        resultData.putString("event-msg", "EasyPusher 推流中");
                        break;
                    case EasyPusher.OnInitPusherCallback.CODE.EASY_PUSH_STATE_DISCONNECTED:
                        resultData.putString("event-msg", "EasyPusher 断开连接");
                        break;
                    case EasyPusher.OnInitPusherCallback.CODE.EASY_ACTIVATE_PLATFORM_ERR:
                        resultData.putString("event-msg", "EasyPusher 平台不匹配");
                        break;
                    case EasyPusher.OnInitPusherCallback.CODE.EASY_ACTIVATE_COMPANY_ID_LEN_ERR:
                        resultData.putString("event-msg", "EasyPusher 断授权使用商不匹配");
                        break;
                    case EasyPusher.OnInitPusherCallback.CODE.EASY_ACTIVATE_PROCESS_NAME_LEN_ERR:
                        resultData.putString("event-msg", "EasyPusher 进程名称长度不匹配");
                        break;
                    default:
                        break;
                }
                mResultReceiver.send(EasyRTSPClient.RESULT_EVENT, resultData);
            }
        });
        mStreamHamal.start(rtsp, RTSPClient.TRANSTYPE_TCP, RTSPClient.EASY_SDK_VIDEO_FRAME_FLAG | RTSPClient.EASY_SDK_AUDIO_FRAME_FLAG, "", "");
    }

    void StopPushing(){
        if(mStreamHamal != null) {
            mStreamHamal.stop();
            mStreamHamal = null;
        }
    }
}
