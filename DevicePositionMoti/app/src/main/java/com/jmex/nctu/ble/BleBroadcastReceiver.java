package com.jmex.nctu.ble;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.nio.ByteBuffer;

import static android.content.Context.BIND_AUTO_CREATE;

public class BleBroadcastReceiver{
    private final static String TAG = BleBroadcastReceiver.class.getSimpleName();

    //////////////////////// HEADER & FOOTER /////////////////////////
    public static final byte COMMAND_HEADER = (byte) 0xFD;
    public static final byte COMMAND_FOOTER = (byte) 0xFE;
    //////////////////////// ACTION //////////////////////////
    public static final byte ACTION_TIMER_SYNC = (byte) 0x00;
    public static final byte ACTION_APP_REQUEST = (byte) 0x02;

    private static final String SYNC_TIME = "sync_time";
    public static final String START_RAW_DATA = "start_raw_data";
    public static final String STOP_RAW_DATA = "stop_raw_data";

    private Activity currentActivity;
    private BroadcastReceiver mGattUpdateReceiver;
    private Context mContext;
    public BluetoothLeService mBluetoothLeService;

    public BleBroadcastReceiver(Activity activity){
        currentActivity = activity;
        mContext = activity;
        initBroadcastReceiver();
        bindSer();
    }

    public void setCurrentActivity(Activity activity){
        currentActivity = activity;
//        mContext = (Context) activity;
    }

    private void initBroadcastReceiver(){
        mGattUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context,final Intent intent) {
                final String action = intent.getAction();
                if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                    if(currentActivity instanceof DeviceSearch)
                        ((DeviceSearch)currentActivity).connect();
                    Toast.makeText(currentActivity, "connected", Toast.LENGTH_SHORT).show();
                } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                    Toast.makeText(currentActivity, intent.getStringExtra("name") + " is disconnect", Toast.LENGTH_SHORT).show();
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG,intent.getStringExtra("name") + " is disconnect");
                            if(currentActivity instanceof DeviceSearch)
                                ((DeviceSearch)currentActivity).disconnect(intent.getStringExtra("name"));
                        }
                    }, 600);
                } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                    Log.d(TAG,intent.getStringExtra("name") + "  Discover service success");
                } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                    if(currentActivity instanceof DeviceSearch  && DeviceSearch.startRec==false) {
                        ((DeviceSearch) currentActivity).addLogData(intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA), intent.getStringExtra("name"));
                    }
                    else if(currentActivity instanceof DeviceSearch && DeviceSearch.startRec==true){
                        ((DeviceSearch) currentActivity).sendDataViaSocket(intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA), intent.getStringExtra("name"));
                    }
                    else if(currentActivity instanceof LogActivity)
                        ((LogActivity)currentActivity).addLogData(intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA), intent.getStringExtra("name"));

                }
            }
        };
        mContext.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    public static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private void bindSer(){
        if(mBluetoothLeService == null) {
            Intent gattServiceIntent = new Intent(mContext, BluetoothLeService.class);
            mContext.bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        }
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
            }
            Log.e(TAG, "Bind Service OK");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
            Log.e(TAG, "onServiceDisconnected");
        }
    };

    public void close(){
        mContext.unregisterReceiver(mGattUpdateReceiver);
        mContext.unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    //  send sync timer to device
    public static byte[] syncTimer() {
        byte[] syncTime = ByteBuffer.allocate(4).putInt((int) (System.currentTimeMillis() / 1000)).array();
        return getCommandData(SYNC_TIME, syncTime);
    }

    public static byte[] getCommandData(String type, byte[] data) {
        int i = 0;
        byte checkSum = 0;
        int dataLength = data.length;
        byte[] bytes = new byte[5 + dataLength];
        bytes[i++] = COMMAND_HEADER;

        switch (type) {
            case SYNC_TIME:
                bytes[i++] = ACTION_TIMER_SYNC;
                bytes[i++] = (byte) dataLength;
                for (byte b : data) {
                    bytes[i++] = b;
                }
                break;
            case START_RAW_DATA:
                bytes[i++] = ACTION_APP_REQUEST;
                bytes[i++] = (byte) dataLength;
                bytes[i++] = data[0];
                bytes[i++] = data[1];
                break;
            case STOP_RAW_DATA:
                bytes[i++] = ACTION_APP_REQUEST;
                bytes[i++] = (byte) dataLength;
                bytes[i++] = data[0];
                bytes[i++] = data[1];
                break;
        }

        for (int j = 1; j < i; j++)
            checkSum += bytes[j];

        bytes[i++] = (byte) ((checkSum) & 0xff);
        bytes[i] = COMMAND_FOOTER;

        return bytes;
    }
}
