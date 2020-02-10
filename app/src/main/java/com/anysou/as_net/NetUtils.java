package com.anysou.as_net;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Enumeration;

import static androidx.core.content.ContextCompat.startActivity;

/**
 * 网络相关工具类

 1. 需要获取网络信息状态权限：
 <uses-permission android:name="android.permission.INTERNET"/> android9.0以后，只能访问https
 <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
 <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE"/>
 <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
 <uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>
 <uses-permission android:name="android.permission.READ_PHONE_STATE" />
 <!--注意：下面是系统权限。方法两个：一是把app放在/system/priv-app；二是或者对app进行系统签名均可获得系统权限 -->
 <uses-permission android:name="android.permission.MODIFY_PHONE_STATE" tools:ignore="ProtectedPermissions" />
 <uses-permission android:name="android.permission.UPDATE_DEVICE_STATS" tools:ignore="ProtectedPermissions" />
 <uses-permission android:name="android.permission.WRITE_SETTINGS" tools:ignore="ProtectedPermissions" />

 android9.0以后，只能访问https 的处理方法：
 1) 清单文件的,application里添加： android:networkSecurityConfig="@xml/network_security_config"
 2）对应在res/xml下建立文件：network_security_config.xml,内容如下：
 <?xml version="1.0" encoding="utf-8"?>
 <network-security-config>
 <!-- 允许明文传输=true -->
 <base-config cleartextTrafficPermitted="true" />
 </network-security-config>

 2、NetworkInfo 在 6.0 时已通过，NetworkCapabilities 替代 注意兼容性。

 3、判断网络是否可用，增加了ping的验证方法。启动用到了ShellUtil类。

 使用注意问题：
 1、在5.0以前，我们都是广播BroadcastReceiver，注册跟网络变化相关的广播。
 2、在安卓5.0以上对网络的监听进行了优化，那就是通过 NetworkCallback 回调的方式。
 *
 */

public final class NetUtils {

    private static final String TAG = NetUtils.class.getClass().getSimpleName();

    private NetUtils() {
        //定义一个 不支持操作异常
        throw new UnsupportedOperationException("cannot be instantiated ; NetworkUtils不能实例化");
    }


    /**
     * 网络是否已连接 【但不一定可用，可能是WIFI局域网，但没上公网】 （兼容版本）
     * @return true:已连接 false:未连接
     */
    @RequiresPermission("android.permission.ACCESS_NETWORK_STATE")  //该注解作用是表明方法所执行的内容需要权限
    @SuppressWarnings("deprecation")  //表示不检测过期的方法
    public static boolean iConnected(@NonNull Context context) {
        // 定义连接管理器
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            //API 23    android 6.0 Marshmallow    棉花糖
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 获取网络状态
                NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
                if (networkCapabilities != null) {
                    return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)           //WIFI
                            || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)   //流量
                            || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);  //以太网
                }
            } else {
                NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                return networkInfo != null && networkInfo.isConnected();
            }
        }
        return false;
    }

    /**
     * 通过PING 的方式确定是否能上网 （无版本问题）
     *  @param ip ip 地址（自己服务器 ip），如果为空，ip 为阿里巴巴公共 ip   .如果可以传入NUll值，则标记为nullbale
     * @return true:已连接 false:未连接
     */
    public static boolean isPing(@Nullable String ip) {
        if (ip == null || ip.length() <= 0) {
            ip = "223.5.5.5"; // 阿里巴巴公共 ip
        }
        ShellUtil.CommandResult result = ShellUtil.execCmd(String.format("ping -c 1 %s", ip), false);
        boolean ret = result.result == 0;
        if (result.successMsg != null) {
            Log.d("NetUtil", "isPing() called successMsg：" + result.successMsg);
        }
        if (result.errorMsg != null) {
            Log.d("NetUtil", "isPing() called errorMsg：" + result.errorMsg);
        }
        return ret;
    }


    /**
     * 通过PING 域名  是否成功
     *  @param hostname  域名   .如果可以传入NUll值，则标记为nullbale
     * @return true:已连接 false:未连接
     */
    public static boolean isPingHostname(@Nullable String hostname) {
        if (hostname == null || hostname.length() <= 0) {
            hostname = "www.baidu.com"; // 阿里巴巴公共 ip
        }
        try {
            return 0 == Runtime.getRuntime().exec("ping -c 1 " + hostname).waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 直接使用http连接网络，如果能成功连接则表示网络可用  是否成功 (注意，url.openStream() 不能工作在主线程)
     *  @param urlstr  http url   .如果可以传入NUll值，则标记为nullbale
     * @return true:已连接 false:未连接
     */
    public static boolean isgetUrl(@Nullable String urlstr) {
        if (urlstr == null || urlstr.length() <= 0) {
            urlstr = "https://www.baidu.com"; // 阿里巴巴公共 ip
        }
        try {
            URL url = new URL(urlstr);
            InputStream stream = url.openStream();
            return true;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Wifi是否已连接，【但不一定可以上公网】   （兼容版本）
     * @return true:已连接 false:未连接
     */
    @RequiresPermission("android.permission.ACCESS_NETWORK_STATE")  //该注解作用是表明方法所执行的内容需要权限
    @SuppressWarnings("deprecation")
    public static boolean isWifiConnected(@NonNull Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities networkCapabilities = manager.getNetworkCapabilities(manager.getActiveNetwork());
                if (networkCapabilities != null) {
                    return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                }
            } else {
                NetworkInfo networkInfo = manager.getActiveNetworkInfo();
                return networkInfo != null && networkInfo.isConnected() && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
            }
        }
        return false;
    }

    /**
     * 判断 wifi 是否打开
     *
     * @return {@code true}: 是<br>{@code false}: 否
     */
    public static boolean isWifiEnabled(@NonNull Context context) {
        @SuppressLint("WifiManagerLeak")
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return wifiManager.isWifiEnabled();
    }

    /**
     * 判断 wifi 数据是否可用
     * @return {@code true}: 是<br>{@code false}: 否
     */
    public static boolean isWifiAvailable(@NonNull Context context) {
        return isWifiEnabled(context) && isPing(null);
    }

    /**
     * 打开或关闭 wifi
     * @param enabled {@code true}: 打开<br>{@code false}: 关闭
     */
    @RequiresPermission("android.permission.CHANGE_WIFI_STATE")  //该注解作用是表明方法所执行的内容需要权限
    public static boolean setWifiEnabled(@NonNull Context context, boolean enabled) {
        try {
            @SuppressLint("WifiManagerLeak")
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (enabled) {
                if (!wifiManager.isWifiEnabled()) {
                    wifiManager.setWifiEnabled(true);
                }
            } else {
                if (wifiManager.isWifiEnabled()) {
                    wifiManager.setWifiEnabled(false);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * 是否为流量连接  （兼容版本）
     * @return true:已连接 false:未连接
     */
    @RequiresPermission("android.permission.ACCESS_NETWORK_STATE")
    @SuppressWarnings("deprecation")
    public static boolean isMobileData(@NonNull Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities networkCapabilities = manager.getNetworkCapabilities(manager.getActiveNetwork());
                if (networkCapabilities != null) {
                    return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                }
            } else {
                NetworkInfo networkInfo = manager.getActiveNetworkInfo();
                return networkInfo != null && networkInfo.isConnected() && networkInfo.getType() == ConnectivityManager.TYPE_MOBILE;
            }
        }
        return false;
    }


    /**
     * 判断移动数据是否打开
     * @return {@code true}: 是<br>{@code false}: 否
     */
    public static boolean isMobileDataEnabled(@NonNull Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            Method getMobileDataEnabledMethod = tm.getClass().getDeclaredMethod("getDataEnabled");
            if (getMobileDataEnabledMethod != null) {
                return (boolean) getMobileDataEnabledMethod.invoke(tm);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 打开或关闭移动数据
     * 注意：获取这个系统权限需要app为系统应用，把app放在/system/priv-app或者对app进行系统签名均可获得系统权限
     * @param enabled {@code true}: 打开<br>{@code false}: 关闭
     */
    public static boolean setMobileDataEnabled(@NonNull Context context, boolean enabled) {
        if (PackageManager.PERMISSION_GRANTED == context.checkCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE")) {
            try {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                Method setMobileDataEnabledMethod = tm.getClass().getDeclaredMethod("setDataEnabled", boolean.class);
                if (null != setMobileDataEnabledMethod) {
                    setMobileDataEnabledMethod.invoke(tm, enabled);
                }
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
    }


    /**
     * 获取网络运营商名称
     * <p>中国移动、如中国联通、中国电信</p>
     * @return 运营商名称
     */
    public static String getNetworkOperatorName(@NonNull Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return tm != null ? tm.getNetworkOperatorName() : null;
    }

    // 定义枚举类型 网络状态
    public enum NetworkType {
        NETWORK_WIFI("WiFi"),
        NETWORK_4G("4G"),
        NETWORK_3G("3G"),
        NETWORK_2G("2G"),
        NETWORK_UNKNOWN("Unknown"),
        NETWORK_NO("NO");
        private String desc;
        NetworkType(String desc) {
            this.desc = desc;
        }
        @Override
        public String toString() {
            return desc;
        }
    }
    /**
     * 获取当前网络类型 （WIFI,4G,2G,3G,Unknown,No network）
     */
    @RequiresPermission("android.permission.ACCESS_NETWORK_STATE")  //该注解作用是表明方法所执行的内容需要权限
    @SuppressWarnings("deprecation")  //表示不检测过期的方法
    public static NetworkType getNetworkType(@NonNull Context context) {
        NetworkType netType = NetworkType.NETWORK_NO;
        NetworkInfo info = null;
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null) {
            info = manager.getActiveNetworkInfo();
        }
        if (info != null && info.isAvailable()) {
            if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                netType = NetworkType.NETWORK_WIFI;
            } else if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                switch (info.getSubtype()) {
                    case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                    case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    case TelephonyManager.NETWORK_TYPE_UMTS:
                    case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    case TelephonyManager.NETWORK_TYPE_HSDPA:
                    case TelephonyManager.NETWORK_TYPE_HSUPA:
                    case TelephonyManager.NETWORK_TYPE_HSPA:
                    case TelephonyManager.NETWORK_TYPE_EVDO_B:
                    case TelephonyManager.NETWORK_TYPE_EHRPD:
                    case TelephonyManager.NETWORK_TYPE_HSPAP:
                        netType = NetworkType.NETWORK_3G;
                        break;
                    case TelephonyManager.NETWORK_TYPE_LTE:
                    case TelephonyManager.NETWORK_TYPE_IWLAN:
                        netType = NetworkType.NETWORK_4G;
                        break;
                    case TelephonyManager.NETWORK_TYPE_GSM:
                    case TelephonyManager.NETWORK_TYPE_GPRS:
                    case TelephonyManager.NETWORK_TYPE_CDMA:
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                    case TelephonyManager.NETWORK_TYPE_1xRTT:
                    case TelephonyManager.NETWORK_TYPE_IDEN:
                        netType = NetworkType.NETWORK_2G;
                        break;
                    default:
                        String subtypeName = info.getSubtypeName();
                        if (subtypeName.equalsIgnoreCase("TD-SCDMA")
                                || subtypeName.equalsIgnoreCase("WCDMA")
                                || subtypeName.equalsIgnoreCase("CDMA2000")) {
                            netType = NetworkType.NETWORK_3G;
                        } else {
                            netType = NetworkType.NETWORK_UNKNOWN;
                        }
                        break;
                }
            } else {
                netType = NetworkType.NETWORK_UNKNOWN;
            }
        }
        return netType;
    }


    /**
     * 通过 wifi 获取本地 IP 地址
     * @return IP 地址
     */
    @RequiresPermission(allOf ={"android.permission.ACCESS_WIFI_STATE","android.permission.CHANGE_WIFI_STATE"})
    public static String getIpAddressByWifi(@NonNull Context context) {
        // 获取wifi服务
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        // 判断wifi是否开启
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        return intToIp(ipAddress);
    }
    private static String intToIp(int i) {
        return (i & 0xFF) + "." +
                ((i >> 8) & 0xFF) + "." +
                ((i >> 16) & 0xFF) + "." +
                (i >> 24 & 0xFF);
    }


    /**
     * 获取 IP 地址
     *
     * @param useIPv4 是否用 IPv4
     * @return IP 地址
     */
    public static String getIPAddress(boolean useIPv4) {
        try {
            for (Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces(); nis.hasMoreElements(); ) {
                NetworkInterface ni = nis.nextElement();
                // 防止小米手机返回 10.0.2.15
                if (!ni.isUp()) continue;
                for (Enumeration<InetAddress> addresses = ni.getInetAddresses(); addresses.hasMoreElements(); ) {
                    InetAddress inetAddress = addresses.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        String hostAddress = inetAddress.getHostAddress();
                        boolean isIPv4 = hostAddress.indexOf(':') < 0;
                        if (useIPv4) {
                            if (isIPv4) return hostAddress;
                        } else {
                            if (!isIPv4) {
                                int index = hostAddress.indexOf('%');
                                return index < 0 ? hostAddress.toUpperCase() : hostAddress.substring(0, index).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取域名 IP 地址 (注意，InetAddress.getByName 不能工作在主线程)
     *
     * @param domain 域名  如果可以传入NUll值，则标记为nullbale
     * @return IP 地址
     */
    public static String getDomainAddress(@Nullable String domain) {
        if(Thread.currentThread() == Looper.getMainLooper().getThread()){
            return "本函数不可以工作在主线程！";
        }
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(domain);
            String remsg = inetAddress.getHostName();
            remsg += ","+inetAddress.getHostAddress();
            remsg += ",本地IP="+InetAddress.getLocalHost().getHostAddress();
            return  remsg;
            //return inetAddress.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace(); //无效域名
            return e.toString();
        } catch (Exception e){
            e.printStackTrace();
            if(e.toString().equals("android.os.NetworkOnMainThreadException")){
                return "本函数不能工作在主线程";
            }
            return e.toString();
        }
    }



    //============ MAC地址也叫物理地址、硬件地址,每个网卡都需要并会有一个唯一的MAC地址 ===================
    public static String getMac(Context context) {
        String strMac = null;
        //API 24    android 7.0 Nougat    牛轧糖 以下的
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            strMac = getMacAddress(context);
            return strMac;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //方法一、通过ip地址来获取绑定的mac地址
            if (!TextUtils.isEmpty(getMacAddress(context))) {
                 strMac = getMacAddress_1();
                return strMac;
            } else if (!TextUtils.isEmpty(getMachineHardwareAddress())) {
            //方法二、扫描各个网络接口获取mac地址
                strMac = getMachineHardwareAddress();
                return strMac;
            } else {
            //方法三、通过busybox获取本地存储的mac地址
                strMac = getLocalMacAddressFromBusybox();
                return strMac;
            }
        }
        return "02:00:00:00:00:00";
    }


    //================ android 7.0以下 获取mac地址 ===================================
    /**
     *  android 7.0以下 获取mac地址
     * @param context
     * @return
     */
    public static String getMacAddress(Context context) {
        // 如果是6.0以下，直接通过wifimanager获取
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            String macAddress0 = getMacAddress0(context);
            if (!TextUtils.isEmpty(macAddress0)) {
                return macAddress0;
            }
        }
        String str = "";
        String macSerial = "";
        try {
            Process pp = Runtime.getRuntime().exec("cat /sys/class/net/wlan0/address");
            InputStreamReader ir = new InputStreamReader(pp.getInputStream());
            LineNumberReader input = new LineNumberReader(ir);
            for (; null != str; ) {
                str = input.readLine();
                if (str != null) {
                    macSerial = str.trim();// 去空格
                    break;
                }
            }
        } catch (Exception ex) {
            Log.e("----->" + "NetInfoManager", "getMacAddress:" + ex.toString());
        }
        if (macSerial == null || "".equals(macSerial)) {
            try {
                return loadFileAsString("/sys/class/net/eth0/address")
                        .toUpperCase().substring(0, 17);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("----->" + "NetInfoManager",
                        "getMacAddress:" + e.toString());
            }
        }
        return macSerial;
    }
    // 6.0以下获取的方法
    private static String getMacAddress0(Context context) {
        if (isAccessWifiStateAuthorized(context)) {
            WifiManager wifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = null;
            try {
                wifiInfo = wifiMgr.getConnectionInfo();
                return wifiInfo.getMacAddress();
            } catch (Exception e) {
                Log.e("----->" + "NetInfoManager","getMacAddress0:" + e.toString());
            }
        }
        return "";
    }
    /**
     * 检查是否允许访问wifi状态,判断其他权限方法通过
     */
    private static boolean isAccessWifiStateAuthorized(Context context) {
        if (PackageManager.PERMISSION_GRANTED == context.checkCallingOrSelfPermission("android.permission.ACCESS_WIFI_STATE")) {
            return true;
        } else
            return false;
    }
    // 读取文件转为字符串
    private static String loadFileAsString(String fileName) throws Exception {
        FileReader reader = new FileReader(fileName);
        String text = loadReaderAsString(reader);
        reader.close();
        return text;
    }
    // 读取字节转为字符串
    private static String loadReaderAsString(Reader reader) throws Exception {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[4096];
        int readLength = reader.read(buffer);
        while (readLength >= 0) {
            builder.append(buffer, 0, readLength);
            readLength = reader.read(buffer);
        }
        return builder.toString();
    }

    //===============  android 7.0以上 获取mac地址 方法一、通过ip地址来获取绑定的mac地址 =========================
    /**
     * 根据IP地址获取MAC地址
     *
     * @return
     */
    public static String getMacAddress_1() {
        String strMacAddr = null;
        try {
            // 获取移动设备本地IP
            InetAddress ip = getLocalInetAddress();
            byte[] b = NetworkInterface.getByInetAddress(ip).getHardwareAddress();
            StringBuffer buffer = new StringBuffer();
            for (int i = 0; i < b.length; i++) {
                if (i != 0) {
                    buffer.append(':');
                }
                String str = Integer.toHexString(b[i] & 0xFF);
                buffer.append(str.length() == 1 ? 0 + str : str);
            }
            strMacAddr = buffer.toString().toUpperCase();
        } catch (Exception e) {
        }
        return strMacAddr;
    }
    /**
     * 获取设备本地IP
     *
     * @return
     */
    private static InetAddress getLocalInetAddress() {
        InetAddress ip = null;
        try { // 列举
            Enumeration<NetworkInterface> en_netInterface = NetworkInterface.getNetworkInterfaces();
            while (en_netInterface.hasMoreElements()) {// 是否还有元素
                NetworkInterface ni = (NetworkInterface) en_netInterface.nextElement();// 得到下一个元素
                Enumeration<InetAddress> en_ip = ni.getInetAddresses();// 得到一个ip地址的列举
                while (en_ip.hasMoreElements()) {
                    ip = en_ip.nextElement();
                    if (!ip.isLoopbackAddress()  && ip.getHostAddress().indexOf(":") == -1)
                        break;
                    else
                        ip = null;
                }
                if (ip != null) {
                    break;
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return ip;
    }

    //===============  android 7.0以上 获取mac地址 方法二、扫描各个网络接口获取mac地址 =========================
    /**
     * 获取设备HardwareAddress地址
     *
     * @return
     */
    public static String getMachineHardwareAddress() {
        Enumeration<NetworkInterface> interfaces = null;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        String hardWareAddress = null;
        NetworkInterface iF = null;
        if (interfaces == null) {
            return null;
        }
        while (interfaces.hasMoreElements()) {
            iF = interfaces.nextElement();
            try {
                hardWareAddress = bytesToString(iF.getHardwareAddress());
                if (hardWareAddress != null)
                    break;
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }
        return hardWareAddress;
    }
    /***
     * byte转为String
     *
     * @param bytes
     * @return
     */
    private static String bytesToString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        StringBuilder buf = new StringBuilder();
        for (byte b : bytes) {
            buf.append(String.format("%02X:", b));
        }
        if (buf.length() > 0) {
            buf.deleteCharAt(buf.length() - 1);
        }
        return buf.toString();
    }

    //===============  android 7.0以上 获取mac地址 方法三、通过busybox获取本地存储的mac地址 =========================
    /**
     * 根据busybox获取本地Mac
     *
     * @return
     */
    public static String getLocalMacAddressFromBusybox() {
        String result = "";
        String Mac = "";
        result = callCmd("busybox ifconfig", "HWaddr");
        // 如果返回的result == null，则说明网络不可取
        if (result == null) {
            return "网络异常";
        }
        // 对该行数据进行解析
        // 例如：eth0 Link encap:Ethernet HWaddr 00:16:E8:3E:DF:67
        if (result.length() > 0 && result.contains("HWaddr") == true) {
            Mac = result.substring(result.indexOf("HWaddr") + 6,
                    result.length() - 1);
            result = Mac;
        }
        return result;
    }
    // 调取cmd命令
    private static String callCmd(String cmd, String filter) {
        String result = "";
        String line = "";
        try {
            Process proc = Runtime.getRuntime().exec(cmd);
            InputStreamReader is = new InputStreamReader(proc.getInputStream());
            BufferedReader br = new BufferedReader(is);
            while ((line = br.readLine()) != null  && line.contains(filter) == false) {
                result += line;
            }
            result = line;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }



    //============== 打开相关网络设置 ===================================================

    /**
     * 打开网络设置界面
     */
    public static void openSetting(Context context){
        Intent mIntent = new Intent();
        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 9) {
            mIntent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
            mIntent.setData(Uri.fromParts("package", context.getPackageName(), null));
        } else if (Build.VERSION.SDK_INT <= 8) {
            mIntent.setAction(Intent.ACTION_VIEW);
            mIntent.setClassName("com.android.settings", "com.android.setting.InstalledAppDetails");
            mIntent.putExtra("com.android.settings.ApplicationPkgName", context.getPackageName());
        }
        context.startActivity(mIntent);
    }

    /**
     * 打开流量设置界面
     */
    public static void openNetworkSetting(Context context){
        Intent intent = new Intent(android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS);
        context.startActivity(intent);
    }

    /**
     * 打开流量设置界面
     */
    public static void openROAMINGSetting(Context context){
        Intent intent = new Intent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS);
        context.startActivity(intent);
    }

    /**
     * 打开WIFI设置界面
     */
    public static void openWifiSetting(Context context){
        Intent intent = new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
        context.startActivity(intent);
    }
}












