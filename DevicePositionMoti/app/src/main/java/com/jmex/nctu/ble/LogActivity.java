package com.jmex.nctu.ble;

import android.hardware.SensorManager;
import android.os.Bundle;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class LogActivity extends AppCompatActivity{
    LinearLayout llContent;
    boolean startOrPause = false;
    boolean startCount = false;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    ArrayList<String> devices = new ArrayList<>();
    ArrayList<Integer> counts = new ArrayList<>();

    Handler mHandler = new Handler();
    Runnable r = new Runnable() {
        @Override
        public void run() {
            showCount();
            if(startCount)
                mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate( Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        llContent = (LinearLayout)findViewById(R.id.llContent);
        DeviceSearch.mReceiver.setCurrentActivity(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public void StartOrPause(View v) {
        if (v.getTag() == null) {
            v.setTag("pause");
            ((Button) v).setText("Pause");
            if(v.getId() == R.id.tvRawData)
                startOrPause = true;
            else if(v.getId() == R.id.tvCount) {
                startCount = true;
                mHandler.postDelayed(r, 1000);
            }
        } else {
            v.setTag(null);
            if(v.getId() == R.id.tvRawData) {
                ((Button) v).setText("Raw Data");
                startOrPause = false;
            }
            else if(v.getId() == R.id.tvCount) {
                ((Button) v).setText("Count");
                startCount = false;
                mHandler.removeCallbacks(r);
            }
        }
    }

    public void Clear(View v){
        llContent.removeAllViews();
    }

    public void addLogData(byte[] byteArrayExtra, String name){
        if(byteArrayExtra.length == 18 && startOrPause) {
            String data = name;
            long time = ByteBuffer.wrap(byteArrayExtra, 2, 4).getInt();
            time *= 1000;
            data += sdf.format(new Date(time)) + "\n";

            data += "Acc\n";
            double xAcc =  ByteBuffer.wrap(byteArrayExtra, 6, 2).getShort()/4096.0;
            double yAcc =  ByteBuffer.wrap(byteArrayExtra, 8, 2).getShort()/4096.0;
            double zAcc =  ByteBuffer.wrap(byteArrayExtra, 10, 2).getShort()/4096.0;

            data += "x: " + String.valueOf(xAcc) + "\n";
            data += "y: " + String.valueOf(yAcc) + "\n";
            data += "z: " +String.valueOf(zAcc) + "\n";

            data += "Gyro\n";
            data += "x: " + ByteBuffer.wrap(byteArrayExtra, 12, 2).getShort() + "\n";
            data += "y: " + ByteBuffer.wrap(byteArrayExtra, 14, 2).getShort() + "\n";
            data += "z: " + ByteBuffer.wrap(byteArrayExtra, 16, 2).getShort() + "\n";

            TextView tv = new TextView(this);
            tv.setText(data);
            llContent.addView(tv, 0);
        }


        String time;
        String raw;
        double xAcc =  ByteBuffer.wrap(byteArrayExtra, 6, 2).getShort()/4096.0;
        double yAcc =  ByteBuffer.wrap(byteArrayExtra, 8, 2).getShort()/4096.0;
        double zAcc =  ByteBuffer.wrap(byteArrayExtra, 10, 2).getShort()/4096.0;
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");
        Date curDate =  new Date(System.currentTimeMillis()); // 獲取當前時間
        time = formatter.format(curDate);
        raw = String.valueOf(xAcc + "," + yAcc + "," + zAcc + ","
                + time + "\n");
        float[] R = new float[16], I = new float[16], earthAcc = new float[16];


        DeviceSearch.client.sendDataString(raw);
        System.out.println(raw);

        if(!devices.contains(name)){
            devices.add(name);
            counts.add(1);
        }else{
            int c = counts.get(devices.indexOf(name)) + 1;
            counts.set(devices.indexOf(name), c);
        }
    }

    void showCount(){
        String count = "";
        for (int i = 0; i < devices.size(); i++) {
            count += devices.get(i).trim() + " : " + counts.get(i) + "\n";
//            Log.d("COUNT",devices.get(i).trim() + " : " + counts.get(i));
        }
//        Log.d("COUNT","-------------------------------------");

        TextView tv = new TextView(this);
        tv.setText(count);
        llContent.addView(tv, 0);
        clean();
    }

    void clean(){
        for (int i = 0; i < counts.size(); i++) {
            counts.set(i, 0);
        }
    }
}
