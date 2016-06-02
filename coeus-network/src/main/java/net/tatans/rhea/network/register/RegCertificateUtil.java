package net.tatans.rhea.network.register;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import net.tatans.coeus.network.callback.HttpRequestCallBack;
import net.tatans.coeus.network.callback.HttpRequestParams;
import net.tatans.coeus.network.tools.TatansApp;
import net.tatans.coeus.network.tools.TatansCache;
import net.tatans.coeus.network.tools.TatansHttp;
import net.tatans.coeus.network.tools.TatansToast;
import net.tatans.coeus.network.utils.TatansDirPath;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by SiLiPing on 2016/5/25.
 */
public class RegCertificateUtil {

    private Context mCtx;
    private TatansCache tatansCache;
    private TirisDemo mTirisDemo;
    private TatansHttp fh;
    private String url = "http://115.29.11.17:8093/android/rest/v1.0/android/validaApp.do";
    private String sign;
    private String key = "certificate";

    public RegCertificateUtil(Activity ctx) {
        this.mCtx = ctx;
        if ((ctx.getIntent().getAction()).endsWith(".MAIN"))
            initCertificate();
    }

    private void initCertificate(){
        tatansCache = TatansCache.get(new File(TatansDirPath.getMyCacheDir("reg","ctr")).getPath());
        /*
        Log.d("imeiStr","imei："+imei);
        */
        boolean ctrFlag = tatansCache.isCacheExist(key);/**判断key是否存在*/
        if (ctrFlag){
             String ctrStr = tatansCache.getAsStringNotDelete(key);
            /**凭证真伪*/
            if(checkRegCode(ctrStr)){
                /**验证凭证是否过期*/
                boolean isCacheDue = tatansCache.isCacheDue(key);
                if (isCacheDue){
                    getTestService(false);
                }
            }else{
                tatansCache.remove(key);/**移除虚假信息*/
                getTestService(true);
                Log.e("certificate","err：虚假凭证!");
            }
        }else{
            Log.e("certificate","err：凭证不存在!");
            /**网络请求验证*/
            getTestService(true);
        }
    }

    /**
     * 在线验证服务
     */
    private void getTestService(boolean isOk){
        if (NetworkUtil.isNetwork()){
            fh = new TatansHttp();
            /**网络请求验证*/
            final String imei = getImei();//**String 获取IMEI*//*
            sign = getSignature();
            HttpRequestParams paramss = new HttpRequestParams();
            paramss.put("sign", sign);
            paramss.put("imei", imei);
            fh.get(url,paramss, new HttpRequestCallBack<String>() {

                @Override
                public void onFailure(Throwable t, String strMsg) {
                    super.onFailure(t, strMsg);
                    showHint();
                    TatansToast.showAndCancel("数据加载失败，请稍后再试");
                    Log.e("certificate","err：加载失败!t："+t+"，strMsg："+strMsg);
                }

                @Override
                public void onSuccess(String s) {
                    super.onSuccess(s);
                    try {
                        Log.e("certificate","返回值："+s);
                        /**在线验证失败*/
                        if (!s.equals("true")){
                            showHint();
                            TatansToast.showAndCancel("在线验证失败");
                            Log.e("certificate","在线验证失败");
                        }else{
                            tatansCache.put(key,getCertificate(imei));
                            Log.e("certificate","在线验证通过");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }else{
            if(isOk)showHint();
        }
    }
    private String getCertificate(String Ime){
        String code=Ime+"10010";
        return TatansApp.md5(code);
    }
    private String getImei(){
        TelephonyManager telephonyManager = (TelephonyManager) mCtx.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getDeviceId();
    }
    /**
     * 本地测试验证真伪
     * @return
     */
    private boolean checkRegCode(String ctrStr){
        boolean T_F = (getCertificate(getImei()).equals(ctrStr))?true:false;
        Log.d("certificate","验证真伪："+T_F);
        return T_F;
    }

    /**
     * 凭证过期、虚假凭证、凭证不存在均启动注册提示
     */
    private void showHint(){
        mTirisDemo = new TirisDemo(mCtx);
        mTirisDemo.setOnTirisListener(new onTirisListener() {
            @Override
            public void onRegistration() {
                getTestService(true);
                TatansToast.showAndCancel("在线注册");
            }

            @Override
            public void onContinue() {
                TatansToast.showAndCancel("继续使用");
            }
        });
    }

    /**
     * 获取应用签名
     * @return
     */
    private PackageManager manager;
    private PackageInfo packageInfo;
    private Signature[] signatures;
    private StringBuilder builder;
    private String signature;
    public String getSignature() {
        manager = mCtx.getPackageManager();
        builder = new StringBuilder();
        String pkgname = getAppInfo();
        boolean isEmpty = TextUtils.isEmpty(pkgname);
        if (isEmpty) {
            TatansToast.showAndCancel("应用程序的包名不能为空！");
        } else {
            try {
                /** 通过包管理器获得指定包名包含签名的包信息 **/
                packageInfo = manager.getPackageInfo(pkgname, PackageManager.GET_SIGNATURES);
                /******* 通过返回的包信息获得签名数组 *******/
                signatures = packageInfo.signatures;
                /******* 循环遍历签名数组拼接应用签名 *******/
                for (Signature signature : signatures) {
                    builder.append(signature.toCharsString());
                }
                /************** 得到应用签名 **************/
                signature = builder.toString();
                Log.d("signature","signature："+signature);
                return signature;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 获取应用包名
     * @return
     */
    private String getAppInfo() {
        try {
            String pkName = mCtx.getPackageName();
            String versionName = mCtx.getPackageManager().getPackageInfo(
                    pkName, 0).versionName;
            int versionCode = mCtx.getPackageManager().getPackageInfo(pkName, 0).versionCode;
            Log.d("signature","getAppInfo："+pkName + "   " + versionName + "  " + versionCode);
            return pkName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
