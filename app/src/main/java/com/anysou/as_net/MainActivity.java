package com.anysou.as_net;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jeremyliao.liveeventbus.LiveEventBus;

import java.io.StringReader;
import java.util.HashMap;

/***
 * Android-网络监控框架 NetworkCallback + BroadCastReceiver
 *  https://www.jianshu.com/p/86d347b2a12b
 *
 *  Android 网络管家ConnectivityManager
 *  https://blog.csdn.net/jason_wzn/article/details/71131544
 *
 *
 */


public class MainActivity extends AppCompatActivity  {

    private CheckBox checkBoxRoot;
    private Switch switchReMsg;
    private TextView textViewShell;
    private EditText editTextShell;
    private Boolean isRoot = false;
    private Boolean isReMsg = true;

    NetStateReceiver netStateReceiver = null; //网络变化广播接收


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkBoxRoot = (CheckBox) findViewById(R.id.checkBoxRoot);
        checkBoxRoot.setChecked(false);
        switchReMsg = (Switch) findViewById(R.id.switchReMsg);
        switchReMsg.setChecked(true);
        editTextShell = (EditText) findViewById(R.id.EditTextShell);
        textViewShell = (TextView) findViewById(R.id.textViewShell);
        textViewShell.setMovementMethod(new ScrollingMovementMethod()); //textview要能滚动 android:scrollbars="vertical"

        setMessageBus(); //设置消息总线
        getMessageBus("connect_change"); //获取网络变化信息"connect_change"

        //==================  检测网络变化 ===================================================
        //API 21    android 5.0 Lollipop    棒棒糖
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // 5.0以下，动态注册广播接收，注意动态注册要销毁
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
            netStateReceiver = new NetStateReceiver();
            registerReceiver(netStateReceiver,intentFilter);  //注册
        } else{
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(this.CONNECTIVITY_SERVICE);
            // 请注意这里会有一个版本适配bug，所以请在这里添加非空判断
            if (connectivityManager != null) {
                NetworkCallbackImpl networkCallback = new NetworkCallbackImpl();  //回调处理类
                NetworkRequest request = new NetworkRequest.Builder().build();    //网络请求
                connectivityManager.registerNetworkCallback(request,networkCallback);
            }
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(netStateReceiver!=null)
            unregisterReceiver(netStateReceiver);  //注销
    }

    public void ToastString(String msg){
        Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();
    }


    //==============  设置 LiveEventBus 消息事件总线框架（Android组件间通信工具） =========
    public void setMessageBus(){

        /**LiveEventBus，一款具有生命周期感知能力的消息事件总线框架（Android组件间通信工具）
         * Andoird中LiveEventBus的使用——用LiveEventBus替代RxBus、EventBus https://blog.csdn.net/qq_43143981/article/details/101678528
         * 消息总线，基于LiveData，具有生命周期感知能力，支持Sticky，支持AndroidX，支持跨进程，支持跨APP
         * https://github.com/JeremyLiao/LiveEventBus
         *
         * 1、build.gradle 中引用  implementation 'com.jeremyliao:live-event-bus-x:1.5.7'
         * 2、初始化 LiveEventBus
         *   1）supportBroadcast 配置支持跨进程、跨APP通信
         *   2）配置 lifecycleObserverAlwaysActive 接收消息的模式（默认值true）：
         *      true：整个生命周期（从onCreate到onDestroy）都可以实时收到消息
         *      false：激活状态（Started）可以实时收到消息，非激活状态（Stoped）无法实时收到消息，需等到Activity重新变成激活状态，方可收到消息
         *   3) autoClear 配置在没有Observer关联的时候是否自动清除LiveEvent以释放内存（默认值false）
         * 3、发送消息：
         *    LiveEventBus.get("key").post("value");  //发送一条即时消息
         *    LiveEventBus.get("key").postDelay("value",3000);  //发送一条延时消息 3秒跳转
         * 4、接受消息，注册一个订阅，在需要接受消息的地方
         *   LiveEventBus.get("key",String.class).observe(this, new Observer<String>() {
         *      @Override
         *      public void onChanged(@Nullable String s) {
         *              Log.i(TAG,s);
         *      }
         *   });
         * */
        LiveEventBus.config()
                .supportBroadcast(this)
                .lifecycleObserverAlwaysActive(true)
                .autoClear(false);
    }

    // 获取消息总线的信息变化
    private void getMessageBus(String key){
        LiveEventBus.get(key,String.class).observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                ToastString(s);
            }
        });
    }

    //===============================  按键功能 ===============================================================

    //调用 Linux Shell 执行结果
    public void callShell(View view) {
        String comstr = editTextShell.getText().toString();
        Toast.makeText(this,comstr,Toast.LENGTH_LONG).show();
        textViewShell.setText("");
        if(comstr == null || comstr.equals("")){
            textViewShell.setText("请输入你要执行的SHELL命令,多条命令用；分号分隔！举例：\nping -c 1 233.5.5.5");
        }else{
            String[] comstrs = comstr.split(";");
            ShellUtil.CommandResult result = ShellUtil.execCmd(comstrs,isRoot,isReMsg);
            String remsg = isRoot ? "Root；":"No Root；";
            remsg += isReMsg ? "ReMsg":"No ReMsg";
            remsg += "\n返回值="+result.result;
            remsg += result.result==0 ? "成功":"失败";
            remsg += "\n成功信息="+result.successMsg+"\n失败信息="+result.errorMsg;
            textViewShell.setText(remsg);
        }
    }
    // 勾选是否要用ROOT权限
    public void onCheckBoxRoot(View view) {
        Toast.makeText(this,""+checkBoxRoot.isChecked(),Toast.LENGTH_LONG).show();
        if(checkBoxRoot.isChecked()){
            isRoot = true;
        }else{
            isRoot = false;
        }
    }
    //滑动是否有效
    public void onSwitchReMsg(View view) {
        Toast.makeText(this,""+switchReMsg.isChecked(),Toast.LENGTH_LONG).show();
        if(switchReMsg.isChecked()){
            isReMsg = true;
        }else{
            isReMsg = false;
        }
    }
    private void TextShow(String msg,Boolean bool){
        String remsg = bool ? "是/成功":"否/失败";
        textViewShell.setText(msg+remsg);
    }



    public void iConnected(View view) {
        Boolean bool = NetUtils.iConnected(this);
        Toast.makeText(this,""+bool,Toast.LENGTH_LONG).show();
        TextShow("是否连接网络：",bool);
    }

    public void isPing(View view) {
        Boolean bool = NetUtils.isPing(null);
        Toast.makeText(this,""+bool,Toast.LENGTH_LONG).show();
        TextShow("是否连接公网Ping：",bool);
    }


    public void isWifiConnected(View view) {
        Boolean bool = NetUtils.isWifiConnected(this);
        Toast.makeText(this,""+bool,Toast.LENGTH_LONG).show();
        TextShow("是否为WIFI连接：",bool);
    }

    public void isWifiEnabled(View view) {
        Boolean bool = NetUtils.isWifiEnabled(this);
        Toast.makeText(this,""+bool,Toast.LENGTH_LONG).show();
        TextShow("WIFI是否打开：",bool);
    }

    public void isWifiAvailable(View view) {
        Boolean bool = NetUtils.isWifiAvailable(this);
        Toast.makeText(this,""+bool,Toast.LENGTH_LONG).show();
        TextShow("WIFI是否可连接公网：",bool);
    }

    public void setWifiEnabled(View view) {
        if(NetUtils.isWifiAvailable(this)){
            boolean bool = NetUtils.setWifiEnabled(this,false);
            TextShow("WIFI开关切换 关 后，注意看手机上方的WIFI图标是否消失。",bool);
        } else {
            boolean bool = NetUtils.setWifiEnabled(this,true);
            TextShow("WIFI开关切换 开 后，注意看手机上方的WIFI图标是否出现。",bool);
        }
    }

    public void isMobileData(View view) {
        Boolean bool = NetUtils.isMobileData(this);
        Toast.makeText(this,""+bool,Toast.LENGTH_LONG).show();
        TextShow("是否为流量连接：",bool);
    }

    public void isMobileDataEnabled(View view) {
        Boolean bool = NetUtils.isMobileDataEnabled(this);
        Toast.makeText(this,""+bool,Toast.LENGTH_LONG).show();
        TextShow("流量是否为打开：",bool);
    }


    public void setMobileDataEnabled(View view) {
        if (PackageManager.PERMISSION_GRANTED == this.checkCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE")) {
            if (NetUtils.isMobileDataEnabled(this)) {
                boolean bool = NetUtils.setMobileDataEnabled(this, false);
                TextShow("流量开关切换(要系统权限) 关 后：", bool);
            } else {
                boolean bool = NetUtils.setMobileDataEnabled(this, true);
                TextShow("流量开关切换(要系统权限) 开 后：", bool);
            }
        } else {
            textViewShell.setText("切换流量开关功能需要获取系统权限！\n方法两个：一是把app放在/system/priv-app；二是或者对app进行系统签名均可获得系统权限");
        }
    }

    public void getNetworkOperatorName(View view) {
        //Toast.makeText(this,NetUtils.getNetworkOperatorName(this),Toast.LENGTH_LONG).show();
        TextShow("运营商名称："+NetUtils.getNetworkOperatorName(this),true);
    }

    public void getNetworkType(View view) {
        //Toast.makeText(this,NetUtils.getNetworkType(this).toString(),Toast.LENGTH_LONG).show();
        TextShow("网络类型："+NetUtils.getNetworkType(this).toString(),true);
    }

    public void getIpAddressByWifi(View view) {
        //Toast.makeText(this,NetUtils.getIpAddressByWifi(this),Toast.LENGTH_LONG).show();
        TextShow("WIFI获取本IP地址："+NetUtils.getIpAddressByWifi(this),true);
    }

    private Thread newThread; //声明一个子线程

    public void getDomainAddress(View view) {
        newThread = new Thread(new Runnable() {
            @Override
            public void run() {
                //这里写入子线程需要做的工作
                textViewShell.setText(NetUtils.getDomainAddress("www.baidu.com"));
            }
        });
        newThread.start(); //启动线程
    }

    public void getIPAddressIpv4(View view) {
        String IP = NetUtils.getIPAddress(true);
        //Toast.makeText(this,IP,Toast.LENGTH_LONG).show();
        TextShow("获取IP地址 By IPV4："+IP,true);
    }
    public void getIPAddressNotIpv4(View view) {
        //Toast.makeText(this,NetUtils.getIPAddress(false),Toast.LENGTH_LONG).show();
        TextShow("获取IP地址 By IPV4："+NetUtils.getIPAddress(false),true);
    }


    public void GET_MAC(View view) {
        TextShow("获取网卡MAC："+NetUtils.getMac(this),true);
    }

    public void openSetting1(View view) {
        NetUtils.openSetting(this);
    }

    public void openSetting2(View view) {
        NetUtils.openNetworkSetting(this);
    }

    public void Set1(View view) {
        NetUtils.openROAMINGSetting(this);
    }

    public void Set2(View view) {
        NetUtils.openWifiSetting(this);
    }
}
