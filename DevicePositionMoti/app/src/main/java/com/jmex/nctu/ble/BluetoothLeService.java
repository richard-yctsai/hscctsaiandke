package com.jmex.nctu.ble;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt = null;
    private final IBinder mBinder = new LocalBinder();
    private int mConnectionState = STATE_DISCONNECTED;
    private Queue<Request> mInitQueue;
    private boolean mInitInProgress;
    private Handler mHandler = new Handler();

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.jmex.nctu.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.jmex.nctu.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.jmex.nctu.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.jmex.nctu.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.jmex.nctu.bluetooth.le.EXTRA_DATA";

    public static final UUID CUSTOM_SERVICE = UUID.fromString("00005054-0000-1000-8000-00805f9b34fb");
    public static final UUID CHARAC_WRITE = UUID.fromString("00005055-0000-1000-8000-00805f9b34fb");
    public static final UUID CHARAC_READ_NOTIFY = UUID.fromString("00005056-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static final UUID CHARAC_BATTERY = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    // Various callback methods defined by the BLE API.
    private final BluetoothGattCallback mGattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(final BluetoothGatt gatt, int status,
                                                    int newState) {
                    String intentAction;
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        intentAction = ACTION_GATT_CONNECTED;
                        mConnectionState = STATE_CONNECTED;
                        broadcastUpdate(intentAction, gatt.getDevice().getName());
                        Log.i(TAG, "Connected to GATT server.");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            if (mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH))
                                Log.i(TAG, "CONNECTION_PRIORITY_HIGH OK");
                            else
                                Log.i(TAG, "CONNECTION_PRIORITY_HIGH FAIL");
                        }
                        try {
                            mHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.i(TAG," Start service discovery...");
                                        mBluetoothGatt.discoverServices();
                                    }
                                }, 500);
                        }catch (NullPointerException e){
                            disconnect();
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        intentAction = ACTION_GATT_DISCONNECTED;
                        mConnectionState = STATE_DISCONNECTED;
                        Log.i(TAG, "Disconnected from GATT server.");
                        broadcastUpdate(intentAction, gatt.getDevice().getName());
                        close(gatt);
                    }
                }

                @Override
                // New services discovered
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED, gatt.getDevice().getName());
                        //  initialize requests queue
                        mInitInProgress = true;
                        mInitQueue = initGatt(gatt);

                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                nextRequest();
                            }
                        },500);
                    } else {
                        Log.w(TAG, "onServicesDiscovered received: " + status);
                    }
                }

                @Override
                // Result of a characteristic read operation
                public void onCharacteristicRead(BluetoothGatt gatt,
                                                 BluetoothGattCharacteristic characteristic,
                                                 int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, gatt.getDevice().getName());
                        nextRequest();
                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt,
                                                    BluetoothGattCharacteristic characteristic) {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, gatt.getDevice().getName());
                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    Log.d(TAG, "write value to " + characteristic.getUuid() + " , status = " + (status == 0 ? "success" : "failed"));

                    nextRequest();
                }

                @Override
                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "onDescriptorWrite success.");
                        nextRequest();
                    }else{
                        Log.i(TAG, "onDescriptorWrite fail. " + status);
                    }
                }

                @Override
                public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                    super.onMtuChanged(gatt, mtu, status);
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "onMtuChanged success.");
                    }else{
                        Log.i(TAG, "onMtuChanged fail.");
                    }
                }
            };

    private void broadcastUpdate(final String action,
                                 String name) {
        final Intent intent = new Intent(action);
        intent.putExtra("name", name);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic, String name) {
        final Intent intent = new Intent(action);

        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0)
            intent.putExtra(EXTRA_DATA, data);
        intent.putExtra("name", name);

        sendBroadcast(intent);
    }

    protected Queue<Request> initGatt(final BluetoothGatt gatt) {
        BluetoothGattCharacteristic customReadCharacteristic = gatt.getService(CUSTOM_SERVICE).getCharacteristic(CHARAC_READ_NOTIFY);
        BluetoothGattCharacteristic customWriteCharacteristic = gatt.getService(CUSTOM_SERVICE).getCharacteristic(CHARAC_WRITE);
        BluetoothGattCharacteristic batteryCharacterstic = gatt.getService(BATTERY_SERVICE).getCharacteristic(CHARAC_BATTERY);

        //  push requests to the head of this queue
        //  enable notification and read/write some requests for initialize
        final LinkedList<Request> requests = new LinkedList<>();
        requests.add(Request.newEnableNotificationsRequest(customReadCharacteristic, gatt));
        requests.add(Request.newWriteRequest(customWriteCharacteristic, BleBroadcastReceiver.syncTimer(), gatt));
        requests.add(Request.newReadRequest(batteryCharacterstic, gatt));
        requests.add(Request.newEnableNotificationsRequest(batteryCharacterstic, gatt));
        return requests;
    }

    protected static final class Request {
        private enum Type {
            WRITE,
            READ,
            ENABLE_NOTIFICATIONS,
            ENABLE_INDICATIONS
        }

        private final Type type;
        private final BluetoothGattCharacteristic characteristic;
        private final byte[] value;

        private BluetoothGatt gatt;

        private Request(final Type type, final BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {
            this.type = type;
            this.characteristic = characteristic;
            this.value = null;
            this.gatt = gatt;
        }

        private Request(final Type type, final BluetoothGattCharacteristic characteristic, final byte[] value, BluetoothGatt gatt) {
            this.type = type;
            this.characteristic = characteristic;
            this.value = value;
            this.gatt = gatt;
        }

        public static Request newReadRequest(final BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {
            return new Request(Type.READ, characteristic, gatt);
        }

        public static Request newWriteRequest(final BluetoothGattCharacteristic characteristic, final byte[] value, BluetoothGatt gatt) {
            return new Request(Type.WRITE, characteristic, value, gatt);
        }

        public static Request newEnableNotificationsRequest(final BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {
            return new Request(Type.ENABLE_NOTIFICATIONS, characteristic, gatt);
        }

        public static Request newEnableIndicationsRequest(final BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {
            return new Request(Type.ENABLE_INDICATIONS, characteristic, gatt);
        }
    }

    private void nextRequest() {
        final Queue<Request> requests = mInitQueue;

        // Get the first request from the queue
        final Request request = requests != null ? requests.poll() : null;

        // Are we done?
        if (request == null) {
            if (mInitInProgress) {
                mInitInProgress = false;
            }
            return;
        }

        switch (request.type) {
            case READ: {
                readCharacteristic(request.characteristic, request.gatt);
                break;
            }
            case WRITE: {
                final BluetoothGattCharacteristic characteristic = request.characteristic;
                characteristic.setValue(request.value);
                writeCharacteristic(characteristic, request.gatt);
                break;
            }
            case ENABLE_NOTIFICATIONS: {
                setCharacteristicNotification(request.characteristic, request.gatt);
                break;
            }
            case ENABLE_INDICATIONS: {
                setCharacteristicIndication(request.characteristic, request.gatt);
                break;
            }
        }
    }

    public boolean connect(DeviceSearch.BLEDevice mBLED){
        if (mBluetoothAdapter == null || mBLED == null) {
            Log.d(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mBLED.device.getAddress());
        if (device == null) {
            Log.d(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        mBLED.gatt = mBluetoothGatt;
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = device.getAddress();
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    public void disconnect() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
        }
    }

    public void close(BluetoothGatt gatt) {
        if (gatt == null) {
            return;
        }
        gatt.close();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {
        if (mBluetoothAdapter == null || gatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        gatt.readCharacteristic(characteristic);
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {
        if (mBluetoothAdapter == null || gatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            Log.w(TAG, "mBluetoothAdapter = " + mBluetoothAdapter + " gatt = " + gatt);
            return;
        }
        gatt.writeCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     */
    public boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {
        if (mBluetoothAdapter == null || gatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        gatt.setCharacteristicNotification(characteristic, true);

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        boolean b = gatt.writeDescriptor(descriptor);
        if (b) {
            Log.d(TAG, "notification success");
            return true;
        } else {
            Log.d(TAG, "notification failed");
            return false;
        }
    }

    public boolean setCharacteristicIndication(BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {
        if (mBluetoothAdapter == null || gatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        gatt.setCharacteristicNotification(characteristic, true);


        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        boolean b = gatt.writeDescriptor(descriptor);
        if (b) {
            Log.d(TAG, "indication success");
            return true;
        } else {
            Log.d(TAG, "indication failed");
            return false;
        }
    }

    public void writeGattCharacteristic(LinkedList<Request> requests){
        mInitQueue = requests;
        nextRequest();
    }
}
