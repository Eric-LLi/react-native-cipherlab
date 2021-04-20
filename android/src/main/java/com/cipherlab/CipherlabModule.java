package com.cipherlab;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.Nullable;

import com.cipherlab.barcode.ReaderManager;
import com.cipherlab.barcode.decoder.KeyboardEmulationType;
import com.cipherlab.barcode.decoder.OutputEnterChar;
import com.cipherlab.barcode.decoder.OutputEnterWay;
import com.cipherlab.barcode.decoderparams.ReaderOutputConfiguration;
import com.cipherlab.barcodebase.ReaderCallback;
import com.cipherlab.rfid.BeepType;
import com.cipherlab.rfid.ClResult;
import com.cipherlab.rfid.DeviceEvent;
import com.cipherlab.rfid.DeviceInfo;
import com.cipherlab.rfid.DeviceResponse;
import com.cipherlab.rfid.DeviceVoltageInfo;
import com.cipherlab.rfid.Enable_State;
import com.cipherlab.rfid.Gen2Settings;
import com.cipherlab.rfid.GeneralString;
import com.cipherlab.rfid.InventoryStatusSettings;
import com.cipherlab.rfid.NotificationParams;
import com.cipherlab.rfid.PowerMode;
import com.cipherlab.rfid.RFIDMemoryBank;
import com.cipherlab.rfid.RFIDMode;
import com.cipherlab.rfid.RFLink;
import com.cipherlab.rfid.SLFlagSettings;
import com.cipherlab.rfid.SessionSettings;
import com.cipherlab.rfid.WorkMode;
import com.cipherlab.rfidapi.RfidManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;

public class CipherlabModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

    private final ReactApplicationContext reactContext;
    private static RfidManager mRfidManager;
    private static ReaderManager mReaderManager;
    private static ReaderCallback mReaderCallback;

    private static ArrayList<String> cacheTags = new ArrayList<>();
    private static boolean isSingleRead = false;

    private static CipherlabModule instance = null;

    private final String LOG = "[CipherLab]";
    private final String READER_STATUS = "READER_STATUS";
    private final String TRIGGER_STATUS = "TRIGGER_STATUS";
    private final String WRITE_TAG_STATUS = "WRITE_TAG_STATUS";
    private final String BATTERY_STATUS = "BATTERY_STATUS";
    private final String TAG = "TAG";
    private final String LOCATE_TAG = "LOCATE_TAG";
    private final String BARCODE = "BARCODE";

    public CipherlabModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.reactContext.addLifecycleEventListener(this);

        instance = this;
    }

    public static CipherlabModule getInstance() {
        return instance;
    }

    private void sendEvent(String eventName, @Nullable WritableMap params) {
        this.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private void sendEvent(String eventName, String msg) {
        this.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, msg);
    }

    public void onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == 545) {
            WritableMap map = Arguments.createMap();
            map.putBoolean("status", false);
            sendEvent(TRIGGER_STATUS, map);
        }
    }

    public void onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == 545) {
            WritableMap map = Arguments.createMap();
            map.putBoolean("status", true);
            sendEvent(TRIGGER_STATUS, map);
        }
    }

    @Override
    public String getName() {
        return "Cipherlab";
    }

    @Override
    public void onHostResume() {

    }

    @Override
    public void onHostPause() {

    }

    @Override
    public void onHostDestroy() {
        doDisconnect();
    }

    @ReactMethod
    public void connect(Promise promise) {
        try {
            doConnect();

            promise.resolve(true);
        } catch (Exception err) {
            promise.reject(err);
        }
    }

    @ReactMethod
    public void reconnect() {
        try {
            doConnect();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    @ReactMethod
    public void disconnect(Promise promise) {
        try {
            doDisconnect();

            promise.resolve(true);
        } catch (Exception err) {
            promise.reject(err);
        }
    }

    @ReactMethod
    public void isConnected(Promise promise) {
        try {
            boolean status = false;

            if (mRfidManager != null) {
                status = mRfidManager.GetConnectionStatus();
            }

            promise.resolve(status);
        } catch (Exception err) {
            promise.reject(err);
        }
    }

    @ReactMethod
    public void getDeviceDetails(Promise promise) {
        Log.d(LOG, "getDeviceDetails");

        if (mRfidManager != null && mRfidManager.GetConnectionStatus()) {
            DeviceVoltageInfo volt = new DeviceVoltageInfo();
            int powerResult = mRfidManager.GetBatteryLifePercent(volt);
            int power = -1;

            if (powerResult == ClResult.S_OK.ordinal()) {
                power = volt.Percentage;
            }

            DeviceInfo info = mRfidManager.GetDeviceInfo();
            int antennaLevel = mRfidManager.GetTxPower();
            WritableMap map = Arguments.createMap();
            map.putString("name", "Cipher Lab");
            map.putString("mac", info.SerialNumber);
            map.putInt("antennaLevel", antennaLevel);
            map.putInt("power", power);

            promise.resolve(map);
        } else {
            promise.reject(LOG, "Fail to retrieve device details");
        }
    }

    @ReactMethod
    public void setAntennaLevel(int antennaLevel, Promise promise) {
        Log.d(LOG, "setAntennaLevel");

        String error = null;

        if (mRfidManager != null && mRfidManager.GetConnectionStatus()) {
            int level = mRfidManager.SetTxPower(antennaLevel);

            if (level != ClResult.S_OK.ordinal()) {
                error = mRfidManager.GetLastError();
                promise.reject(LOG, error);
            } else {
                promise.resolve(level);
            }
        }

        promise.reject(LOG, "Fail to change antenna level");
    }

    @ReactMethod
    public void clear() {
        Log.d(LOG, "clear");

        cacheTags = new ArrayList<>();
    }

    @ReactMethod
    public void setSingleRead(boolean enable) {
        Log.d(LOG, "setSingleRead");

        isSingleRead = enable;
    }

    @ReactMethod
    public void setEnabled(boolean enable, Promise promise) {
        if (mRfidManager != null && mRfidManager.GetConnectionStatus()) {
            String error = null;
            int re = mRfidManager.EnableDeviceTrigger(enable);

            if (re != ClResult.S_OK.ordinal()) {
                error = mRfidManager.GetLastError();
            }

            if (error != null) {
                promise.reject(LOG, error);
            } else {
                promise.resolve(true);
            }
        }
    }

    @ReactMethod
    public void programTag(String oldTag, String newTag, Promise promise) {
        try {
            if (mRfidManager != null && mRfidManager.GetConnectionStatus()) {
                byte[] oldData = StringToBytes(oldTag);
                byte[] newData = StringToBytes(newTag);
                byte[] password = StringToBytes("00000000");
                DeviceResponse re = mRfidManager.RFIDDirectWriteTagByEPC(password, oldData, RFIDMemoryBank.EPC, 2, 3, newData); // set kill password

                WritableMap map = Arguments.createMap();
                map.putBoolean("status", re == DeviceResponse.OperationSuccess);
                map.putString("error", re.toString());
                sendEvent(WRITE_TAG_STATUS, map);
            }
        } catch (Exception err) {
            promise.reject(err);
        }
    }

    private byte[] StringToBytes(String s) {
        int var1 = s.length();
        byte[] var2 = new byte[var1 / 2];

        for (int var3 = 0; var3 < var1; var3 += 2) {
            var2[var3 / 2] = (byte) ((Character.digit(s.charAt(var3), 16) << 4) + Character.digit(s.charAt(var3 + 1), 16));
        }

        return var2;
    }

    private void RFIDConfigureReader() throws Exception {
        if (mRfidManager != null && mRfidManager.GetConnectionStatus()) {
            SetDefault();

            SetNotification();

            SetGen2();

            SetWorkMode(WorkMode.ComprehensiveMode);

            SetRFIDMode(RFIDMode.Inventory_EPC_TID);

            SetPowerMode(PowerMode.Normal);

            SetRFLink(RFLink.PR_ASK_Miller4_300KHz);
        }
    }

    private void BarcodeConfigureReader() throws Exception {
        if (mReaderManager != null) {
            String error = null;
            if (com.cipherlab.barcode.decoder.ClResult.S_ERR == mReaderManager.ResetReaderToDefault()) {
                error = mReaderManager.GetLastError();
            }

            ReaderOutputConfiguration settings = new ReaderOutputConfiguration();
            mReaderManager.Get_ReaderOutputConfiguration(settings);
            settings.enableKeyboardEmulation = KeyboardEmulationType.None;
            settings.autoEnterWay = OutputEnterWay.Disable;
            settings.autoEnterChar = OutputEnterChar.None;
            settings.showCodeLen = com.cipherlab.barcode.decoder.Enable_State.FALSE;
            settings.showCodeType = com.cipherlab.barcode.decoder.Enable_State.FALSE;
            settings.szPrefixCode = "";
            settings.szSuffixCode = "";
            settings.useDelim = ':';

            if (com.cipherlab.barcode.decoder.ClResult.S_ERR == mReaderManager.Set_ReaderOutputConfiguration(settings)) {
                error = mReaderManager.GetLastError();
            }

            com.cipherlab.barcode.decoderparams.NotificationParams settings2 = new com.cipherlab.barcode.decoderparams.NotificationParams();
            mReaderManager.Get_NotificationParams(settings2);
            settings2.ReaderBeep = com.cipherlab.barcode.decoder.BeepType.Mute;
            if (com.cipherlab.barcode.decoder.ClResult.S_ERR == mReaderManager.Set_NotificationParams(settings2)) {
                error = mReaderManager.GetLastError();
            }

            if (error != null) {
                throw new Exception(error);
            } else {
                mReaderManager.SetActive(true);
            }
        }
    }

    private void doConnect() {
//        if (mRfidManager == null) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mRfidManager = RfidManager.InitInstance(reactContext);
                mReaderManager = ReaderManager.InitInstance(reactContext);
                mReaderCallback = new barcodeCallback();

                IntentFilter filter = new IntentFilter();
                filter.addAction(GeneralString.Intent_RFIDSERVICE_CONNECTED);
                filter.addAction(GeneralString.Intent_RFIDSERVICE_TAG_DATA);
                filter.addAction(GeneralString.Intent_RFIDSERVICE_EVENT);
                filter.addAction(GeneralString.Intent_FWUpdate_ErrorMessage);
                filter.addAction(GeneralString.Intent_FWUpdate_Percent);
                filter.addAction(GeneralString.Intent_FWUpdate_Finish);
                filter.addAction(GeneralString.Intent_GUN_Attached);
                filter.addAction(GeneralString.Intent_GUN_Unattached);
                filter.addAction(GeneralString.Intent_GUN_Power);

                filter.addAction(com.cipherlab.barcode.GeneralString.Intent_SOFTTRIGGER_DATA);
                filter.addAction(com.cipherlab.barcode.GeneralString.Intent_PASS_TO_APP);
                filter.addAction(com.cipherlab.barcode.GeneralString.Intent_READERSERVICE_CONNECTED);

                reactContext.registerReceiver(myDataReceiver, filter);
            }
        }).start();
//        }
    }

    private void doDisconnect() {
        if (mRfidManager != null || mReaderManager != null) {
            reactContext.unregisterReceiver(myDataReceiver);

            if (mRfidManager != null) {
                mRfidManager.Release();

                mRfidManager = null;
            }

            if (mReaderManager != null) {
                mReaderManager.Release();

                mReaderManager = null;
            }

            WritableMap map = Arguments.createMap();
            map.putBoolean("status", false);
            sendEvent(READER_STATUS, map);
        }
    }

    private void SetRFIDMode(RFIDMode mode) throws Exception {
        String error = null;

        int re = mRfidManager.SetRFIDMode(mode);
        if (re != ClResult.S_OK.ordinal()) {
            error = mRfidManager.GetLastError();
        }

        if (error != null) {
            throw new Exception(error);
        }

    }

    private void SetPowerMode(PowerMode mode) throws Exception {
        String error = null;

        int re = mRfidManager.SetPowerMode(mode);
        if (re != ClResult.S_OK.ordinal()) {
            error = mRfidManager.GetLastError();
        }

        if (error != null) {
            throw new Exception(error);
        }
    }

    private void SetDefault() throws Exception {
        String error = null;

        int re = mRfidManager.ResetToDefault();
        if (re != ClResult.S_OK.ordinal()) {
            error = mRfidManager.GetLastError();
        }

        if (error != null) {
            throw new Exception(error);
        }
    }

    private void SetNotification() throws Exception {
        String error = null;

        NotificationParams settings = new NotificationParams();
        mRfidManager.GetNotification(settings);

        settings.ReaderBeep = BeepType.Mute;
        settings.BatteryLED = Enable_State.TRUE;
        settings.BatteryBeep = Enable_State.TRUE;
        settings.ModuleTemperature = Enable_State.TRUE;

        int re = mRfidManager.SetNotification(settings);
        if (re != ClResult.S_OK.ordinal()) {
            error = mRfidManager.GetLastError();
        }

        if (error != null) {
            throw new Exception(error);
        }
    }

    private void SetGen2() throws Exception {
        String error = null;
        Gen2Settings settings = new Gen2Settings();

        settings.Session = SessionSettings.S0;
        settings.InventoryStatus_Action = InventoryStatusSettings.STATE_A;
        settings.SL_Flag = SLFlagSettings.All;

        int re = mRfidManager.SetGen2(settings);
        if (re != ClResult.S_OK.ordinal()) {
            error = mRfidManager.GetLastError();
        }

        if (error != null) {
            throw new Exception(error);
        }
    }

    private void SetWorkMode(WorkMode mode) throws Exception {
        String error = null;
        int re = mRfidManager.SetWorkMode(mode);

        if (re != ClResult.S_OK.ordinal()) {
            error = mRfidManager.GetLastError();
        }

        if (error != null) {
            throw new Exception(error);
        }
    }

    private void SetRFLink(RFLink link) throws Exception {
        String error = null;
        int re = mRfidManager.SetRFLink(link);

        if (re != ClResult.S_OK.ordinal()) {
            error = mRfidManager.GetLastError();
        }

        if (error != null) {
            throw new Exception(error);
        }
    }

    private boolean addTagToList(String strEPC) {
        if (strEPC != null) {
            if (!cacheTags.contains(strEPC)) {
                cacheTags.add(strEPC);
                return true;
            }
        }
        return false;
    }

    private class barcodeCallback implements ReaderCallback {
        @Override
        public void onDecodeComplete(String s) throws RemoteException {
            //
        }

        @Override
        public IBinder asBinder() {
            return null;
        }
        //
    }

    private final BroadcastReceiver myDataReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case GeneralString.Intent_RFIDSERVICE_CONNECTED:
                    Log.d(LOG, "Intent_RFIDSERVICE_CONNECTED");
                    try {
                        RFIDConfigureReader();

                        WritableMap map = Arguments.createMap();
                        map.putBoolean("status", true);
                        sendEvent(READER_STATUS, map);
                    } catch (Exception err) {
                        WritableMap map = Arguments.createMap();
                        map.putBoolean("status", false);
                        map.putString("error", err.getMessage());
                        sendEvent(READER_STATUS, map);
                    }
                    break;
                case GeneralString.Intent_GUN_Attached:
                    Log.d(LOG, "Intent_GUN_Attached");

                    doConnect();
                    break;
                case GeneralString.Intent_GUN_Unattached:
                    Log.d(LOG, "Intent_GUN_Unattached");
                    //doDisconnect();
                    WritableMap map = Arguments.createMap();
                    map.putBoolean("status", false);
                    sendEvent(READER_STATUS, map);
                    break;
                case GeneralString.Intent_GUN_Power:
                    Log.d(LOG, "Intent_GUN_Power");
                    boolean AC = intent.getBooleanExtra(GeneralString.Data_GUN_ACPower, false);
                    boolean Connect = intent.getBooleanExtra(GeneralString.Data_GUN_Connect, false);
                    break;
                case GeneralString.Intent_RFIDSERVICE_EVENT:
                    int event = intent.getIntExtra(GeneralString.EXTRA_EVENT_MASK, -1);
                    Log.d(TAG, "[Intent_RFIDSERVICE_EVENT] DeviceEvent=" + event);
                    if (event == DeviceEvent.PowerSavingMode.getValue()) {
                        Log.i(GeneralString.TAG, "PowerSavingMode ");
                    } else if (event == DeviceEvent.LowBattery.getValue()) {
                        Log.i(GeneralString.TAG, "LowBattery ");
                    } else if (event == DeviceEvent.ScannerFailure.getValue()) {
                        Log.i(GeneralString.TAG, "ScannerFailure ");
                    } else if (event == DeviceEvent.BatteryLose.getValue()) {
                        Log.i(GeneralString.TAG, "BatteryLose ");
                    } else if (event == DeviceEvent.OverTemperature.getValue()) {
                        Log.i(GeneralString.TAG, "OverTemperature ");
                    } else if (event == DeviceEvent.Battery_Re_Plug.getValue()) {
                        Log.i(GeneralString.TAG, "Battery_Re_Plug ");
                    }
                    break;
                case GeneralString.Intent_RFIDSERVICE_TAG_DATA:
                    /*
                     * type : 0=Normal scan (Press Trigger Key to receive the data) ; 1=Inventory EPC ; 2=Inventory ECP TID ; 3=Reader tag ; 5=Write tag ; 6=Lock tag ; 7=Kill tag ; 8=Authenticate tag ; 9=Untraceable tag
                     * response : 0=RESPONSE_OPERATION_SUCCESS ; 1=RESPONSE_OPERATION_FINISH ; 2=RESPONSE_OPERATION_TIMEOUT_FAIL ; 6=RESPONSE_PASSWORD_FAIL ; 7=RESPONSE_OPERATION_FAIL ;251=DEVICE_BUSY
                     * */

                    int type = intent.getIntExtra(GeneralString.EXTRA_DATA_TYPE, -1);
                    int response = intent.getIntExtra(GeneralString.EXTRA_RESPONSE, -1);
                    double data_rssi = intent.getDoubleExtra(GeneralString.EXTRA_DATA_RSSI, 0);

                    String PC = intent.getStringExtra(GeneralString.EXTRA_PC);
                    String EPC = intent.getStringExtra(GeneralString.EXTRA_EPC);
                    String TID = intent.getStringExtra(GeneralString.EXTRA_TID);
                    String ReadData = intent.getStringExtra(GeneralString.EXTRA_ReadData);
                    int EPC_length = intent.getIntExtra(GeneralString.EXTRA_EPC_LENGTH, 0);
                    int TID_length = intent.getIntExtra(GeneralString.EXTRA_TID_LENGTH, 0);
                    int ReadData_length = intent.getIntExtra(GeneralString.EXTRA_ReadData_LENGTH, 0);

                    if (isSingleRead) {
                        if (data_rssi > -40) {
                            mRfidManager.SoftScanTrigger(false);
                            sendEvent(TAG, EPC);
                        }
                    } else {
                        if (addTagToList(EPC)) {
                            sendEvent(TAG, EPC);
                        }
                    }
                    break;
                case com.cipherlab.barcode.GeneralString.Intent_READERSERVICE_CONNECTED:
                    // Make sure this app bind to barcode reader service , then user can use APIs to get/set settings from barcode reader service
                    Log.d(LOG, "Intent_READERSERVICE_CONNECTED");

                    try {
                        BarcodeConfigureReader();
                    } catch (Exception err) {
                        WritableMap map2 = Arguments.createMap();
                        map2.putBoolean("status", false);
                        map2.putString("error", err.getMessage());
                        sendEvent(READER_STATUS, map2);
                    }

                    break;
                case com.cipherlab.barcode.GeneralString.Intent_PASS_TO_APP:
                case com.cipherlab.barcode.GeneralString.Intent_SOFTTRIGGER_DATA:
                    // extra string from intent
                    String barcode = intent.getStringExtra(com.cipherlab.barcode.GeneralString.BcReaderData);

                    sendEvent(BARCODE, barcode);
                    break;
            }
        }
    };
}
