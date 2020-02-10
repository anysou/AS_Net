package com.anysou.as_net;

import android.app.Application;
import android.content.Context;

public class MainApplication extends Application {
    public static Context mContext;  //全局变量 APP的上下文
    public static Context getAppContext(){
        return mContext;
    } //全局方法，获取 APP的上下文

    @Override
    public void onCreate() {
        super.onCreate();

        mContext = getApplicationContext();
    }
}
