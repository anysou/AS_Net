package com.anysou.as_net;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.jeremyliao.liveeventbus.LiveEventBus;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class NetworkCallbackImpl  extends ConnectivityManager.NetworkCallback{

    private static final String TAG = "connect_change";


    /**
     * 网络可用的回调
     * */
    @Override
    public void onAvailable(Network network) {
        super.onAvailable(network);
        Log.e(TAG, "onAvailable");
        LiveEventBus.get(TAG).post("网络连接了");  //发送一条即时消息
    }

    /**
     * 网络丢失的回调
     * */
    @Override
    public void onLost(Network network) {
        super.onLost(network);
        Log.e(TAG, "onLost");
        LiveEventBus.get(TAG).post("网络断开了");
    }

    /**
     * 当建立网络连接时，回调连接的属性
     * */
    @Override
    public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
        super.onLinkPropertiesChanged(network, linkProperties);
        Log.e(TAG, "onLinkPropertiesChanged");
        LiveEventBus.get(TAG).post("当建立网络连接时，回调连接的属性");
    }

    /**
     *  按照官方的字面意思是，当我们的网络的某个能力发生了变化回调，那么也就是说可能会回调多次
     *
     *  之后在仔细的研究
     * */
    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
        super.onCapabilitiesChanged(network, networkCapabilities);
        Log.e(TAG, "onCapabilitiesChanged");
        //获取网络状态类型
        NetUtils.NetworkType networkType = NetUtils.getNetworkType(MainApplication.getAppContext());

        if(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)){
            if(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){
                LiveEventBus.get(TAG).post("wifi网络已连接"+networkType.toString());
            }else {
                LiveEventBus.get(TAG).post("移动网络已连接"+networkType.toString());
            }
        } else {
            LiveEventBus.get(TAG).post("当我们的网络的某个能力发生了变化回调，那么也就是说可能会回调多次."+networkType.toString());
        }
    }

    /**
     * 在网络失去连接的时候回调，但是如果是一个生硬的断开，他可能不回调
     * */
    @Override
    public void onLosing(Network network, int maxMsToLive) {
        super.onLosing(network, maxMsToLive);
        Log.e(TAG, "onLosing");
        LiveEventBus.get(TAG).post("在网络失去连接的时候回调，但是如果是一个生硬的断开，他可能不回调");
    }

    /**
     * 按照官方注释的解释，是指如果在超时时间内都没有找到可用的网络时进行回调
     * */
    @Override
    public void onUnavailable() {
        super.onUnavailable();
        Log.e(TAG, "onUnavailable");
        LiveEventBus.get(TAG).post("是指如果在超时时间内都没有找到可用的网络时进行回调");
    }


}
