package toy.android.com.toy.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.Date;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import toy.android.com.toy.bean.ActiveToyReqBean;
import toy.android.com.toy.bean.ActiveToyResBean;
import toy.android.com.toy.bean.ToyLoginReqBean;
import toy.android.com.toy.bean.ToyLoginResBean;
import toy.android.com.toy.interf.MyInterface;
import toy.android.com.toy.internet.Constants;
import toy.android.com.toy.utils.SPUtils;

public class KeepLiveService extends Service {
    private static final String TAG = "tag";
    private String mDevicecode;
    private String mDeviceid;
    private String mMic;
    private String mVersionName;
    private int mVersionCode;
    private int mWifiRssi;
    private int mCurrent;
    private int mMusicVoice;
    private int mSystemVoice;


    public KeepLiveService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mDeviceid = intent.getStringExtra("deviceid");
        mDevicecode = intent.getStringExtra("devicecode");
        initDeviceInfo();
        ToyLogin(mDeviceid);

        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSelf();
    }

    private void ToyLogin(String deviceId) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Constants.baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        MyInterface anInterface = retrofit.create(MyInterface.class);
        ToyLoginReqBean.BODYBean bodyBean = new ToyLoginReqBean.BODYBean(deviceId, "", mDevicecode, "A",
//                mVersionName);
                "1.0");
        ToyLoginReqBean toyLoginReqBean = new ToyLoginReqBean("REQ", "LOG", "", new SimpleDateFormat
                ("yyyyMMddHHmmssSSS").format(new Date()), bodyBean, "", "", "1");
        Gson gson = new Gson();
        String s = gson.toJson(toyLoginReqBean);
        Log.i(TAG, s);
        Call<ToyLoginResBean> toyLoginResBeanCall = anInterface.TOY_LOGIN_RES_BEAN_CALL(s);

        toyLoginResBeanCall.enqueue(new Callback<ToyLoginResBean>() {
            @Override
            public void onResponse(Call<ToyLoginResBean> call, Response<ToyLoginResBean> response) {
//                ToastUtil.showToast(MainActivity.this, response.message());
                Log.i(TAG, "onResponse: toy login" + response.body().getTOKEN());
                Log.i(TAG, "onResponse: toy login" + response.body().getBODY());
                Log.i(TAG, "onResponse: toy login" + "成功了");
                String token = response.body().getTOKEN();
                SPUtils.putString(KeepLiveService.this, "token", token);
                toyHeart(mVersionName, mWifiRssi, mDevicecode, token);
            }

            @Override
            public void onFailure(Call<ToyLoginResBean> call, Throwable t) {
                Log.i(TAG, "onFailure: " + t);
            }
        });
    }

    //初始化玩具的信息 (信号<wifi,4g>,音量<音乐,通话>,电量,麦克风,摄像头)
    private void initDeviceInfo() {

        WifiManager systemService = (WifiManager) getSystemService(WIFI_SERVICE);
        //获取wifi信息API
        WifiInfo connectionInfo = systemService.getConnectionInfo();
        //wifi信号强度
        mWifiRssi = connectionInfo.getRssi();
        Log.d(TAG, "init: wifi" + mWifiRssi);

        //获取音量API
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        //当前音乐音量
        mMusicVoice = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        //当前通话音量
        int callVoice = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        //当前铃声音量
        int ringVoice = audioManager.getStreamVolume(AudioManager.STREAM_RING);
        //当前提示音音量
        int alarmVoice = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        //当前系统音量
        mSystemVoice = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
        Log.d(TAG, "init: musicvoice=" + mMusicVoice + ";callvoice=" + callVoice + ";ringvoice=" + ringVoice + ";alarmvoice=" + alarmVoice);
        Log.i(TAG, "onCreate: " + mDeviceid + ";");

        //获取版本信息:
        PackageManager packageManager = getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(this.getPackageName(), 0);
            mVersionCode = packageInfo.versionCode;
            mVersionName = packageInfo.versionName;
            Log.d(TAG, "init: packinfo:versioncode:" + mVersionCode + ",versionname:" + mVersionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        //判断麦克风是否打开?
        boolean microphoneMute = audioManager.isMicrophoneMute();

        if (microphoneMute) {
            //打开了给值"1",代表当前的话筒正被占用
            mMic = "1";
        } else {
            //关闭的状态,给值"2",代表当前的话筒未被占用
            mMic = "2";
        }
        //通过broadcastreceiver获取当前设备的电量
        BatteryReceiver batteryReceiver = new BatteryReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, intentFilter);

    }

    //获取当前设备的电量
    public class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mCurrent = intent.getIntExtra("level", -1);
            int total = intent.getIntExtra("scale", -1);
            Log.d(TAG, "onReceive的currentBattery:" + mCurrent + "total:" + total);
            unregisterReceiver(this);
        }
    }

    //心跳接口3.4.6
    private void toyHeart(String versionName, int wifiRssi, String rid, String token) {

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Constants.baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        MyInterface myInterface = retrofit.create(MyInterface.class);
        ActiveToyReqBean.BODYBean bodyBean = new ActiveToyReqBean.BODYBean("A", "TOY", versionName, mCurrent + "",
                wifiRssi + "", mMic, "", mMusicVoice + "", rid);
        ActiveToyReqBean activeToyReqBean = new ActiveToyReqBean("REQ", "HEART", "", new SimpleDateFormat
                ("yyyyMMddHHmmssSSS").format(new Date()), bodyBean, "", token, "1");
        Gson gson = new Gson();
        String s = gson.toJson(activeToyReqBean);
        Log.d(TAG, "toyHeart: 数据信息:" + s.toString());
        Call<ActiveToyResBean> activeToyResBeanCall = myInterface.ACTIVE_TOY_RES_BEAN_CALL(s);
        activeToyResBeanCall.enqueue(new Callback<ActiveToyResBean>() {
            @Override
            public void onResponse(Call<ActiveToyResBean> call, Response<ActiveToyResBean> response) {

                Log.i(TAG, "onResponse:init toy" + response.message());
                Log.i(TAG, "onResponse:init toy " + response.body().toString());

            }

            @Override
            public void onFailure(Call<ActiveToyResBean> call, Throwable t) {
                Log.i(TAG, "onFailure: " + t);
            }
        });
    }
}
