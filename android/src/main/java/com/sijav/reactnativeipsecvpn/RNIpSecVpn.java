package com.sijav.reactnativeipsecvpn;

import static android.app.Activity.RESULT_OK;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.logic.VpnStateService;
import org.strongswan.android.manager.VpnActivityWrapper;
import org.strongswan.android.manager.VpnInfoData;
import org.strongswan.android.manager.VpnManagerImplNew;

import androidx.appcompat.app.AppCompatActivity;


public class RNIpSecVpn extends ReactContextBaseJavaModule {

    @SuppressLint("StaticFieldLeak")
    private static ReactApplicationContext reactContext;

    private RNIpSecVpnStateHandler _RNIpSecVpnStateHandler;
    private static VpnManagerImplNew vpnManager;

    RNIpSecVpn(ReactApplicationContext context) {
        super(context);
        // Load charon bridge
        System.loadLibrary("androidbridge");
        reactContext = context;
        vpnManager = VpnManagerImplNew.getInstance();
        Intent vpnStateServiceIntent = new Intent(context, VpnStateService.class);
        _RNIpSecVpnStateHandler = new RNIpSecVpnStateHandler(this);
        context.bindService(vpnStateServiceIntent, _RNIpSecVpnStateHandler, Service.BIND_AUTO_CREATE);
        loadPrepare();
    }


    void sendEvent(String eventName, @Nullable WritableMap params) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
    }

    @Override
    public String getName() {
        return "RNIpSecVpn";
    }


    void loadPrepare(){
        VpnManagerImplNew.setActivityWrapper(new VpnActivityWrapper(reactContext, new VpnActivityWrapper.StateListener() {
            @Override
            public void disabled() {

                Log.e("stswan:reportError:", "DISABLED");

            }

            @Override
            public void stateChanged() {
                Log.e("stswan:reportError:", "stateChanged");
                _RNIpSecVpnStateHandler.stateChanged();
            }


            @Override
            public void connecting() {

                Log.e("stswan:reportError:", "CONNECTING");
            }

            @Override
            public void connected() {

                Log.e("stswan:reportError:", "CONNECTED");

            }

            @Override
            public void disconnecting() {

                Log.e("stswan:reportError:", "DISCONNECTING");
            }
        }));

        vpnManager.getActivityWrapper().onStart();
        vpnManager.getActivityWrapper().bindService();
    }

    @ReactMethod
    public void prepare(final Promise promise) {
        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {
            promise.reject("E_ACTIVITY_DOES_NOT_EXIST", "Activity doesn't exist");
            return;
        }
        Intent intent = VpnService.prepare(currentActivity);
        if (intent != null) {
            reactContext.addActivityEventListener(new BaseActivityEventListener() {
                public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
                    if(requestCode == 0 && resultCode == RESULT_OK){
                        promise.resolve(null);
                    } else {
                        promise.reject("PrepareError", "Failed to prepare");
                    }
                }
            });
            currentActivity.startActivityForResult(intent, 0);
        }else{
          //  loadPrepare();
            promise.resolve(null);
        }
    }

    @ReactMethod
    public void connect(String address, String username, String password,  Promise promise) {
        if(_RNIpSecVpnStateHandler.vpnStateService == null){
            promise.reject("E_SERVICE_NOT_STARTED", "Service not started yet");
            return;
        }

        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {
            promise.reject("E_ACTIVITY_DOES_NOT_EXIST", "Activity doesn't exist");
            return;
        }

        Intent intent = VpnService.prepare(currentActivity);
        if (intent != null) {
            promise.reject("PrepareError", "Not prepared");
            return;
        }

        VpnInfoData profile = new VpnInfoData("NetKeeply", address, username, password);
        VpnProfile vpnProfile = VpnManagerImplNew.getInstance().createVpnProfile(profile);

        vpnManager.getActivityWrapper().connectVpn(vpnProfile);

        promise.resolve(null);
    }

    @ReactMethod
    public void getCurrentState(Promise promise){
        if(_RNIpSecVpnStateHandler.vpnStateService == null){
            promise.reject("E_SERVICE_NOT_STARTED", "Service not started yet");
            return;
        }
        VpnStateService.ErrorState errorState = _RNIpSecVpnStateHandler.vpnStateService.getErrorState();
        VpnStateService.State state = _RNIpSecVpnStateHandler.vpnStateService.getState();
        if(errorState == VpnStateService.ErrorState.NO_ERROR){
            promise.resolve(state != null ? state.ordinal() : 4);
        } else {
            promise.resolve(4);
        }
    }

    @ReactMethod
    public void getCharonErrorState(Promise promise){
        if(_RNIpSecVpnStateHandler.vpnStateService == null){
            promise.reject("E_SERVICE_NOT_STARTED", "Service not started yet");
            return;
        }
        VpnStateService.ErrorState errorState = _RNIpSecVpnStateHandler.vpnStateService.getErrorState();
        promise.resolve(errorState != null ? errorState.ordinal() : 8);
    }

    @ReactMethod
    public void disconnect(Promise promise){
        if(_RNIpSecVpnStateHandler.vpnStateService != null){
            _RNIpSecVpnStateHandler.vpnStateService.disconnect();
        }
        promise.resolve(null);
    }
    
    private int listenerCount = 0;

    @ReactMethod
    public void addListener(String eventName) {
      if (listenerCount == 0) {
        // Set up any upstream listeners or background tasks as necessary
      }

      listenerCount += 1;
    }

    @ReactMethod
    public void removeListeners(Integer count) {
      listenerCount -= count;
      if (listenerCount == 0) {
        // Remove upstream listeners, stop unnecessary background tasks
      }
    }
}
