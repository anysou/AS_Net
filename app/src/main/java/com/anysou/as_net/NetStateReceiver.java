package com.anysou.as_net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;

/**
 * 网络发生变化的广播处理。 为兼容 5.0 以下的版本
 *
 */


public class NetStateReceiver extends BroadcastReceiver {

    private static final String TAG = "NetStateReceiver";
    private static String intentConnectChange = "android.net.conn.CONNECTIVITY_CHANGE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Log.e(TAG, "nothing receive");
            return;
        }
        //equalsIgnoreCase 忽略大小写的比较, 是网络发生变化的广播
        if (intentConnectChange.equalsIgnoreCase(intent.getAction())) {
            Log.e(TAG, "网络发生了改变");
            NetUtils.NetworkType networkType = NetUtils.getNetworkType(context);
            Toast.makeText(context,"网络发生变化："+networkType.toString(),Toast.LENGTH_SHORT).show();
        }
    }
}
