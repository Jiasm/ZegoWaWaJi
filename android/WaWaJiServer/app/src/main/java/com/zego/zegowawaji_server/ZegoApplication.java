package com.zego.zegowawaji_server;

import android.app.Application;
import android.os.Build;
import android.text.TextUtils;
import android.widget.Toast;

import com.tencent.bugly.crashreport.CrashReport;
import com.zego.base.utils.AppLogger;
import com.zego.base.utils.DeviceIdUtil;
import com.zego.base.utils.OSUtils;
import com.zego.base.utils.PrefUtil;
import com.zego.base.utils.TimeUtil;
import com.zego.zegoliveroom.ZegoLiveRoom;
import com.zego.zegoliveroom.constants.ZegoAvConfig;
import com.zego.zegoliveroom.constants.ZegoConstants;

/**
 * <p>Copyright © 2017 Zego. All rights reserved.</p>
 *
 * @author realuei on 30/10/2017.
 */

public class ZegoApplication extends Application {

    static final private long APP_ID =  3177435262L;

    static final private byte[] APP_SIGN_KEY = new byte[] {
            (byte)0x16, (byte)0x6c, (byte)0x57, (byte)0x8b, (byte)0xb0, (byte)0xb5, (byte)0x51, (byte)0xfd,
            (byte)0xc4, (byte)0xd9, (byte)0xb7, (byte)0xaf, (byte)0x96, (byte)0x1f, (byte)0x13, (byte)0x82,
            (byte)0xc9, (byte)0xb6, (byte)0x2b, (byte)0x0f, (byte)0x99, (byte)0x75, (byte)0x3a, (byte)0xb3,
            (byte)0xc1, (byte)0x7e, (byte)0xc4, (byte)0x54, (byte)0x30, (byte)0x93, (byte)0x28, (byte)0xfa
    };

    static final private  String BUGLY_APP_KEY = "1e4b3a1ac0";

    static private ZegoApplication sInstance;

    private ZegoLiveRoom mZegoLiveRoom;

    static public ZegoApplication getAppContext() {
        return sInstance;
    }

    public ZegoLiveRoom getZegoLiveRoom() {
        return mZegoLiveRoom;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        sInstance = this;

        boolean isMainProcess = OSUtils.isMainProcess(this);

        AppLogger.getInstance().writeLog("******* Application (%s) onCreate *******", (isMainProcess ? "main" : "guard"));

        initCrashReport(isMainProcess);

        if (isMainProcess) {    // 仅在主进程中才初始化
            initUserInfo();

            setupZegoSDK();
        }
    }

    private void initUserInfo() {
        String userId = PrefUtil.getInstance().getUserId();
        String userName = PrefUtil.getInstance().getUserName();
        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(userName)) {
            userId = TimeUtil.getNowTimeStr();
            userName = String.format("WWJS_%s_%s", Build.MODEL.replaceAll(",", "."), userId);

            PrefUtil.getInstance().setUserId(userId);
            PrefUtil.getInstance().setUserName(userName);
        }
    }

    private void initCrashReport(boolean isMainProcess) {
        CrashReport.setSdkExtraData(this, "zego_liveroom_version", ZegoLiveRoom.version());
        CrashReport.setSdkExtraData(this, "zego_ve_version", ZegoLiveRoom.version2());
        CrashReport.setUserId(DeviceIdUtil.generateDeviceId(this));

        CrashReport.UserStrategy strategy = new CrashReport.UserStrategy(this);
        strategy.setBuglyLogUpload(isMainProcess);
        CrashReport.initCrashReport(this, BUGLY_APP_KEY, false, strategy);


    }

    private void setupZegoSDK() {
        String userId = PrefUtil.getInstance().getUserId();
        String userName = PrefUtil.getInstance().getUserName();
        AppLogger.getInstance().writeLog("set userId & userName with : %s, %s", userId, userName);
        ZegoLiveRoom.setUser(userId, userName);
        ZegoLiveRoom.requireHardwareEncoder(true);
        ZegoLiveRoom.requireHardwareDecoder(true);

        mZegoLiveRoom = new ZegoLiveRoom();
        mZegoLiveRoom.setSDKContext(new ZegoLiveRoom.SDKContext() {
            @Override
            public String getSoFullPath() {
                return null;
            }

            @Override
            public String getLogPath() {
                return null;
            }

            @Override
            public Application getAppContext() {
                return sInstance;
            }
        });

        initZegoSDK(mZegoLiveRoom);
    }

    private void initZegoSDK(ZegoLiveRoom liveRoom) {
        boolean success = liveRoom.initSDK(APP_ID, APP_SIGN_KEY);
        if (!success) {
            AppLogger.getInstance().writeLog("Init ZegoLiveRoom SDK failed");
            Toast.makeText(sInstance, "", Toast.LENGTH_LONG).show();
            return;
        }

        // 推荐使用如下参数配置推流以达到最佳均衡效果
        // 采集分辨率：720 * 1280
        // 编码分辨率：480 * 640
        // 推流码率：600 * 1000 bps （在合适范围内，码率对视频效果基本无影响）
        int resolutionLevel;
        ZegoAvConfig config;
        int level = PrefUtil.getInstance().getLiveQuality();
        if (level < 0) {
            // 默认设置级别为"标准"
            resolutionLevel = ZegoAvConfig.Level.Generic;

            config = new ZegoAvConfig(resolutionLevel);

            // 保存默认设置
            PrefUtil.getInstance().setLiveQuality(resolutionLevel);
            PrefUtil.getInstance().setLiveQualityResolution(resolutionLevel);
            PrefUtil.getInstance().setLiveQualityFps(15);
            PrefUtil.getInstance().setLiveQualityBitrate(600 * 1000);
        } else if (level > ZegoAvConfig.Level.SuperHigh) {
            resolutionLevel = PrefUtil.getInstance().getLiveQualityResolution();

            config = new ZegoAvConfig(ZegoAvConfig.Level.High);
            config.setVideoBitrate(PrefUtil.getInstance().getLiveQualityBitrate());
            config.setVideoFPS(PrefUtil.getInstance().getLiveQualityFps());
        } else {
            resolutionLevel = level;
            config = new ZegoAvConfig(level);
        }

        String resolutionText = getResources().getStringArray(R.array.zg_resolutions)[resolutionLevel];
        String[] strWidthHeight = resolutionText.split("x");

        int width = Integer.parseInt(strWidthHeight[0].trim());
        int height = Integer.parseInt(strWidthHeight[1].trim());
        // 默认使用 720 * 1280 采集分辨率以达到最大可视角度及最佳推流质量，再高的采集分辨率，目前所使用的摄像头不支持
        if (width < height) {
            config.setVideoCaptureResolution(720, 1280);
        } else {
            config.setVideoCaptureResolution(1280, 720);
        }
        config.setVideoEncodeResolution(width, height);

        liveRoom.setAVConfig(config);
        liveRoom.setAVConfig(config, ZegoConstants.PublishChannelIndex.AUX);
    }
}
