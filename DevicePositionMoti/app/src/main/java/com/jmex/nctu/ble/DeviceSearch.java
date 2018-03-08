package com.jmex.nctu.ble;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static com.jmex.nctu.ble.BluetoothLeService.CHARAC_WRITE;
import static com.jmex.nctu.ble.BluetoothLeService.CUSTOM_SERVICE;

public class DeviceSearch extends AppCompatActivity {
    private final static String TAG = DeviceSearch.class.getSimpleName();
    //----BLE----//
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private Handler mHandler = new Handler();
    //Android 5.0 ↑
    private BluetoothLeScanner mBluetoothLeScanner;
    ScanCallback scanCallback;
    //Android 5.0 ↓
    private BluetoothAdapter.LeScanCallback myLEScanCallback;
    public BluetoothAdapter mBluetoothAdapter;

    public static BleBroadcastReceiver mReceiver;

    ArrayList<BLEDevice> BLEDevices = new ArrayList<>();

    // Stops scanning after 5 seconds.
    private static final long SCAN_PERIOD = 5000;
    //----BLE----//

    ListView lvDevice;
    private DeviceListAdapter lvDeviceAdapter;

    private SwipeRefreshLayout laySwipe;
    private SwipeRefreshLayout.OnRefreshListener onSwipeToRefresh = new SwipeRefreshLayout.OnRefreshListener() {
        @Override
        public void onRefresh() {
            laySwipe.setRefreshing(true);
            scanLeDevice(true);
        }
    };

    private boolean checkPermission = false;
    private boolean checkBLE = false;
    private ProgressDialog pd = null;

    public byte speed = 0x14;   //default 20ms

    //----Cleint-----//
    public static  ClientSocket client;
    private final int PORT = 8888;
    private boolean connectedAvailable = false;
    private String defaultName = "";
    private String defaultIP = "";
    public File file_arguements;
    private DataOutputStream out_arguments;

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    public static  boolean startRec = false;
    private Button btStartRecord;
    private Button btStopRecord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_search);

        check();
        checkBLE();
        inputIPAddress();
        inputName();
        initView();

        mReceiver = new BleBroadcastReceiver(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    BluetoothDevice bluetoothDevice;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        bluetoothDevice = result.getDevice();
                        if(bluetoothDevice.getName() != null && bluetoothDevice.getName().contains("MOTi_")) {
                            lvDeviceAdapter.addDevice(bluetoothDevice);
                            lvDeviceAdapter.notifyDataSetChanged();
                        }
                    }
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    super.onBatchScanResults(results);
                }
                @Override
                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);
                }
            };
        }else{
            myLEScanCallback = new BluetoothAdapter.LeScanCallback()
            {
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord)
                {
                    if(device.getName() != null && device.getName().contains("MOTi_")) {
                        lvDeviceAdapter.addDevice(device);
                        lvDeviceAdapter.notifyDataSetChanged();
                    }
                }

            };
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mReceiver.setCurrentActivity(this);

        if(checkPermission && checkBLE) {
            laySwipe.setRefreshing(true);
            scanLeDevice(true);
        }
    }
    public void inputName(){
        //建立一個POP OUT視窗要求使用者輸入User name
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this,R.style.AlertDialogCustom);
        builder.setCancelable(false);
        builder.setTitle("Enter your name:");

        // 設定開始畫面Input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(defaultName);
        // 建立開始畫面name Button
        this.runOnUiThread(new Runnable() {
            public void run() {
                builder.setView(input)
                        .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                defaultName = input.getText().toString();
                                try {
                                        out_arguments = new DataOutputStream(new FileOutputStream(file_arguements, false));
                                    String nameTemp = (defaultName + "\n");
                                    out_arguments.write(nameTemp.getBytes());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        })
                        .create().setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                    }
                });
                builder.show().getWindow().setLayout(800, 600);
            }
        });
    }

    protected void inputIPAddress(){
        //建立一個POP OUT視窗要求使用者輸入IP Address
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this,R.style.AlertDialogCustom);
        builder.setCancelable(false);
        builder.setTitle("請輸入Server IP位置");

        // 設定開始畫面Input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setText(defaultIP);
        // 建立開始畫面Connect Button
        this.runOnUiThread(new Runnable() {
            public void run() {
                builder.setView(input)
                        .setPositiveButton("Connect", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                defaultIP = input.getText().toString();
                                try {
                                    out_arguments = new DataOutputStream(new FileOutputStream(file_arguements, true));
                                    String ipTemp = (defaultIP + "\n");
                                    out_arguments.write(ipTemp.getBytes());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                //建立與client端地連線
                                connectClient();
                                //check if connected
                                if (connectedAvailable == false) //returns true if internet available
                                {
                                    Toast.makeText(DeviceSearch.this, "唉呦  好像沒有連上喔", Toast.LENGTH_LONG).show();
                                    inputIPAddress();
                                } else {
                                    Toast.makeText(DeviceSearch.this, "Connected showed from MainActivity!!", Toast.LENGTH_LONG).show();
                                }
                            }
                        })
                        .create().setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                    }
                });
                builder.show().getWindow().setLayout(800, 600);
            }
        });
    }
    private void connectClient() {
        //新增一個ClientSocket為client
        setClient(new ClientSocket(defaultIP, PORT, defaultName));
        //將client連  線設定為背景執行
        client.execute();
        connectedAvailable =true;
    }


    public void setClient(ClientSocket client) {
        this.client = client;
    }

    public void sendDataViaSocket(byte[] byteArrayExtra, String name){
        String data = name;
        long time = ByteBuffer.wrap(byteArrayExtra, 2, 4).getInt();
        time *= 1000;
        data += sdf.format(new Date(time)) + "\n";

        data += "Acc\n";
        double xAcc =  ByteBuffer.wrap(byteArrayExtra, 6, 2).getShort()/4096.0;
        double yAcc =  ByteBuffer.wrap(byteArrayExtra, 8, 2).getShort()/4096.0;
        double zAcc =  ByteBuffer.wrap(byteArrayExtra, 10, 2).getShort()/4096.0;

        String timeD;
        String raw;
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");
        Date curDate =  new Date(System.currentTimeMillis()); // 獲取當前時間
        timeD = formatter.format(curDate);
        raw = String.valueOf(xAcc + "," + yAcc + "," + zAcc + ","
                + timeD + "\n");
        float[] R = new float[16], I = new float[16], earthAcc = new float[16];


        DeviceSearch.client.sendDataString(raw);
        System.out.println(raw);

    }
    private void initView(){
        btStartRecord=(Button)findViewById(R.id.btStartRecord);
        btStopRecord=(Button)findViewById(R.id.btStopRecord);
        btStartRecord.setClickable(true);
        btStopRecord.setClickable(false);

        laySwipe = (SwipeRefreshLayout) findViewById(R.id.laySwipe);
        laySwipe.setOnRefreshListener(onSwipeToRefresh);
        laySwipe.setColorSchemeResources(
                android.R.color.darker_gray);

        lvDevice = (ListView)findViewById(R.id.lvDevice);
        lvDeviceAdapter = new DeviceListAdapter(this);
        lvDevice.setAdapter(lvDeviceAdapter);
        lvDeviceAdapter.updateListData();
        lvDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(lvDeviceAdapter.getItem(position) instanceof BluetoothDevice) {
                    if (mReceiver.mBluetoothLeService == null)
                        return;
                    laySwipe.setRefreshing(false);
                    scanLeDevice(false);
                    BluetoothDevice device = (BluetoothDevice)lvDeviceAdapter.getItem(position);
                    final BLEDevice d = new BLEDevice(DeviceSearch.this, device);
                    BLEDevices.add(d);
                    connectBle(d);
                    lvDeviceAdapter.remove(device);
                    lvDeviceAdapter.updateConnectedDevice(BLEDevices);

                    pd = new ProgressDialog(DeviceSearch.this);
                    pd.setMessage("Connecting");
                    pd.setCancelable(false);
                    pd.show();
                }
            }
        });
        lvDevice.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (firstVisibleItem == 0) {
                    laySwipe.setEnabled(true);
                }else{
                    laySwipe.setEnabled(false);
                }
            }
        });
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            clear();
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(r, SCAN_PERIOD);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ScanSettings settings = new ScanSettings.Builder()
                        .setReportDelay(0)
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                ArrayList<ScanFilter> filters = new ArrayList<>();
                mBluetoothLeScanner.startScan(filters, settings ,scanCallback);
            }else{
                mBluetoothAdapter.startLeScan(myLEScanCallback);
            }
        } else {
            mHandler.removeCallbacks(r);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mBluetoothLeScanner.stopScan(scanCallback);
            }else {
                mBluetoothAdapter.stopLeScan(myLEScanCallback);
            }
        }
    }

    private void clear(){
        lvDeviceAdapter.clear();
        lvDeviceAdapter.notifyDataSetChanged();
    }

    private Runnable r = new Runnable() {
        @Override
        public void run() {
//            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mBluetoothLeScanner.stopScan(scanCallback);
            }else{
                mBluetoothAdapter.stopLeScan(myLEScanCallback);
            }
            laySwipe.setRefreshing(false);
        }
    };

//    private boolean connectBle(String address) {
//        return mBluetoothLeService.connect(address);
//    }

    private boolean connectBle(BLEDevice bled) {
        return mReceiver.mBluetoothLeService.connect(bled);
    }

    public void connect() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                pd.cancel();
            }
        }, 1000);
    }

    public void disconnect(String name) {
        if(pd != null)
            pd.cancel();
        for (BLEDevice bleDevice : BLEDevices){
            if(bleDevice.device.getName() != null && bleDevice.device.getName().equals(name)) {
                BLEDevices.remove(bleDevice);
                lvDeviceAdapter.updateConnectedDevice(BLEDevices);
                return;
            }
        }
    }

    public void addLogData(byte[] byteArrayExtra, String name) {
        if(byteArrayExtra.length == 1) {
            for (BLEDevice device : BLEDevices) {
                if (device.device.getName().equals(name)) {
                    device.battery = (int) byteArrayExtra[0];
                    lvDeviceAdapter.updateListData();
                }
            }
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mReceiver.close();
    }

    private void checkBLE(){
        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }else{
            checkBLE = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            }else {
                this.mBluetoothAdapter = mBluetoothAdapter;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            checkBLE();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void check(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int permissionCoarseLocation = ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION);
            if (permissionCoarseLocation != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{ACCESS_COARSE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            }else{
                checkPermission = true;
            }
        }else
            checkPermission = true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch(requestCode) {
            case REQUEST_LOCATION_PERMISSION:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //取得權限，進行檔案存取
                    checkPermission = true;
                } else {
                    check();
                }
                break;
        }
    }

    private class DeviceListAdapter extends BaseAdapter {
        private LayoutInflater layoutInflater;
        ArrayList<Object> allDevice = new ArrayList<>();
        ArrayList<BLEDevice> connected_devices = new ArrayList<>();
        ArrayList<BluetoothDevice> devices = new ArrayList<>();

        DeviceListAdapter (Context context){
            layoutInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return allDevice.size();
        }

        @Override
        public Object getItem(int position) {
            return allDevice.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            DeviceListAdapter.ViewHolder holder = null;
            if(convertView==null){
                convertView = layoutInflater.inflate(R.layout.device_list_item, null);
                holder = new DeviceListAdapter.ViewHolder((TextView) convertView.findViewById(R.id.tvDeviceName),
                                                            (Button) convertView.findViewById(R.id.btStart),
                                                            (Button) convertView.findViewById(R.id.btDisconnect),
                                                            (TextView) convertView.findViewById(R.id.tvBattery));
                convertView.setTag(holder);
            }else{
                holder = (DeviceListAdapter.ViewHolder) convertView.getTag();
            }

            if(allDevice.get(position) instanceof String){
                holder.btStart.setVisibility(View.GONE);
                holder.btDisconnect.setVisibility(View.GONE);
                holder.tvBattery.setVisibility(View.GONE);
                holder.tvDeviceName.setText((String)allDevice.get(position));
                holder.tvDeviceName.setBackgroundColor(Color.LTGRAY);
            }else if(allDevice.get(position) instanceof BLEDevice){
                holder.btStart.setVisibility(View.VISIBLE);
                holder.btDisconnect.setVisibility(View.VISIBLE);
                holder.tvBattery.setVisibility(View.VISIBLE);
                holder.tvDeviceName.setBackgroundColor(Color.TRANSPARENT);

                final BLEDevice device = (BLEDevice)allDevice.get(position);
                holder.tvDeviceName.setText(device.device.getName());
                holder.tvBattery.setText(device.battery + "%");
                holder.btStart.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(v.getTag() == null){
                            v.setTag("stop");
                            ((Button)v).setText("Stop");
                            writeGattCharacteristic(device, BleBroadcastReceiver.START_RAW_DATA, new byte[]{0x07, speed});
                        }else{
                            v.setTag(null);
                            ((Button)v).setText("Start");

                            writeGattCharacteristic(device, BleBroadcastReceiver.STOP_RAW_DATA, new byte[]{0x08, 0x00});
                        }
                    }
                });
                holder.btDisconnect.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        device.gatt.disconnect();
                    }
                });
            }else if(allDevice.get(position) instanceof BluetoothDevice){
                holder.btStart.setVisibility(View.GONE);
                holder.btDisconnect.setVisibility(View.GONE);
                holder.tvBattery.setVisibility(View.GONE);
                holder.tvDeviceName.setBackgroundColor(Color.TRANSPARENT);

                BluetoothDevice device = (BluetoothDevice)allDevice.get(position);
                if("".equals(device.getName()) || device.getName() == null)
                    holder.tvDeviceName.setText("No Name");
                else
                    holder.tvDeviceName.setText(device.getName() + " " + device.getAddress());
            }

            return convertView;
        }

        void updateConnectedDevice(ArrayList<BLEDevice> device){
            connected_devices.clear();
            connected_devices.addAll(device);
            updateListData();
        }

        void addDevice(BluetoothDevice device){
            if(check(device.getAddress())) {
                devices.add(device);
                updateListData();
            }
        }

        void updateListData(){
            allDevice.clear();
            allDevice.add("Connected");
            allDevice.addAll(connected_devices);
            allDevice.add("Search Device");
            allDevice.addAll(devices);
            notifyDataSetChanged();
        }

        void remove(Object data){
            if(data instanceof BLEDevice)
                connected_devices.remove(data);
            else if(data instanceof BluetoothDevice)
                devices.remove(data);
            else
                Log.d(TAG, "Remove Fail");
        }

        void clear(){
            devices.clear();
        }

        private boolean check(String address){
            for (int i = 0; i < devices.size(); i++) {
                if((devices.get(i)).getAddress().equals(address))
                    return false;
            }
            return true;
        }

        private class ViewHolder{
            TextView tvDeviceName;
            Button btStart;
            Button btDisconnect;
            TextView tvBattery;

            ViewHolder(TextView tvDeviceName, Button btStart, Button btDisconnect, TextView tvBattery){
                this.tvDeviceName = tvDeviceName;
                this.btStart = btStart;
                this.btDisconnect = btDisconnect;
                this.tvBattery = tvBattery;
            }
        }
    }

    public class BLEDevice{
        Context mContext;
        BluetoothDevice device;
        BluetoothGatt gatt;
        int battery = 0;

        BLEDevice(Context mContext, BluetoothDevice device){
            this.device = device;
            this.mContext = mContext;
        }
    }

    public void OnStartClick(View v){
        String nameTemp = (defaultName + "\n");
        Log.d("SS","start recording!!");
        client.sendDataString(nameTemp);
        startRec = true;
        btStartRecord.setClickable(false);
        btStopRecord.setClickable(true);
        writeGattCharacteristic(BleBroadcastReceiver.START_RAW_DATA, new byte[]{0x07, speed});

    }

    public void OnStopClick(View v){
        startRec = false;
        client.disconnect();
        connectClient();
        btStartRecord.setClickable(true);
        btStopRecord.setClickable(false);
        writeGattCharacteristic(BleBroadcastReceiver.START_RAW_DATA, new byte[]{0x08, 0x00});
    }

    public void OnSettingClick(View v){
        final EditText etSpeed = new EditText(this);
        etSpeed.setHint(">= 10ms");
        new AlertDialog.Builder(this)
                .setTitle("設定資料傳送速度")
                .setView(etSpeed)
                .setPositiveButton("確定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try{
                            int input = Integer.parseInt(etSpeed.getText().toString().trim());
                            if(input < 10) {
                                Toast.makeText(DeviceSearch.this, "速度需>=10ms", Toast.LENGTH_SHORT).show();
                            }else{
                                speed = (byte) input;
                            }
                        }catch (Exception e){
                            Log.d(TAG, "Setting speed fail");
                            speed = 0x14;
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    public void OnShowLogClick(View v){
        startActivity(new Intent(this, LogActivity.class));
    }

    private void writeGattCharacteristic(String type, byte[] value){
        LinkedList<BluetoothLeService.Request> requests = new LinkedList<>();
        for(BLEDevice device : BLEDevices){
            BluetoothGattCharacteristic characteristic = device.gatt.getService(CUSTOM_SERVICE).getCharacteristic(CHARAC_WRITE);
            requests.add(BluetoothLeService.Request.newWriteRequest(characteristic, BleBroadcastReceiver.getCommandData(type, value), device.gatt));
        }
        mReceiver.mBluetoothLeService.writeGattCharacteristic(requests);
    }

    private void writeGattCharacteristic(BLEDevice device, String type, byte[] value){
        LinkedList<BluetoothLeService.Request> requests = new LinkedList<>();
        BluetoothGattCharacteristic characteristic = device.gatt.getService(CUSTOM_SERVICE).getCharacteristic(CHARAC_WRITE);
        requests.add(BluetoothLeService.Request.newWriteRequest(characteristic, BleBroadcastReceiver.getCommandData(type, value), device.gatt));
        mReceiver.mBluetoothLeService.writeGattCharacteristic(requests);
    }
}
