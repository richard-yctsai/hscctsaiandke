package com.example.weichun.deviceposition;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.w3c.dom.Text;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.SimpleTimeZone;


public class MainActivity extends AppCompatActivity {
    //Client socket set
    private ClientSocket client;
    private final int PORT = 8221;
    private boolean connectedAvailable = false;
    private String defaultName = "";
    private String defaultIP = "";
    public String dir_path_arguements = "";
    public File dir_arguements;
    public File file_arguements;

    public Handler handler;

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Context mContext;

    private DataOutputStream out_acc;
    private DataOutputStream out_arguments;
    private DataInputStream in_arguments;

    private TextView text;
    private static ToggleButton btn_record;
    private static ToggleButton btn_recordPeriod;
    private static SeekBar skb_timestamp;
    private static TextView txv_timestampValue;
    private int timestamp;

    private boolean startRec = false;

    private Calendar calendar;

    private float[] gravityValues = null;
    private float[] magneticValues = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this.getApplicationContext();
        text = (TextView) findViewById(R.id.log);
        btn_record = (ToggleButton) findViewById(R.id.btn_record);
        btn_recordPeriod = (ToggleButton) findViewById(R.id.btn_recordPeriod);
        skb_timestamp = (SeekBar) findViewById(R.id.skb_timestamp);
        txv_timestampValue = (TextView) findViewById(R.id.txv_timestampValue);

        dir_path_arguements = Environment.getExternalStorageDirectory().getAbsolutePath() + "/AbsAccCollection";
        dir_arguements = new File(dir_path_arguements);
        if (!dir_arguements.exists()) {
            dir_arguements.mkdir();
        }
        try {
            file_arguements = new File(dir_arguements, "raw_arguements.txt");
            in_arguments = new DataInputStream(new FileInputStream(file_arguements));
            defaultName = in_arguments.readLine();
            defaultIP = in_arguments.readLine();
        } catch (Exception e) {
            e.printStackTrace();
        }


        ConnectCheck();
        inputName();

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> deviceSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);

        // Show all supported sensor
        for (int i = 0; i < deviceSensors.size(); i++) {
            Log.d("[SO]", deviceSensors.get(i).getName());
        }
        /**
         *              Set all sensor
         */
        if ((mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)) != null) {
            mSensorManager.registerListener(mSensorListener, mSensor, 10000);
        } else {
            Toast.makeText(mContext, "ACCELEROMETER is not supported!", Toast.LENGTH_SHORT).show();
        }

        if ((mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)) != null) {
            mSensorManager.registerListener(mSensorListener, mSensor, 10000);
        } else {
            Toast.makeText(mContext, "GYROSCOPE is not supported!", Toast.LENGTH_SHORT).show();
        }

        if ((mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)) != null) {
            mSensorManager.registerListener(mSensorListener, mSensor, 10000);
        } else {
            Toast.makeText(mContext, "MAGNETOMETER is not supported!", Toast.LENGTH_SHORT).show();
        }
  /* get time */

    }
    private SensorEventListener mSensorListener = new SensorEventListener() {
        public final void onSensorChanged(SensorEvent event) {
            String raw;
            String time;

            if (startRec) {
                if ((gravityValues != null) && (magneticValues != null)
                        && (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION)) {
                    try {

                        float[] deviceRelativeAcceleration = new float[4];
                        deviceRelativeAcceleration[0] = event.values[0];
                        deviceRelativeAcceleration[1] = event.values[1];
                        deviceRelativeAcceleration[2] = event.values[2];
                        deviceRelativeAcceleration[3] = 0;

                        // Change the device relative acceleration values to earth relative values
                        // X axis -> East
                        // Y axis -> North Pole
                        // Z axis -> Sky

                        float[] R = new float[16], I = new float[16], earthAcc = new float[16];

                        SensorManager.getRotationMatrix(R, I, gravityValues, magneticValues);

                        float[] inv = new float[16];

                        android.opengl.Matrix.invertM(inv, 0, R, 0);
                        android.opengl.Matrix.multiplyMV(earthAcc, 0, inv, 0, deviceRelativeAcceleration, 0);
                        //Log.d("Acceleration", "Values: (" + earthAcc[0] + ", " + earthAcc[1] + ", " + earthAcc[2] + ")");
                        //text.append("Values: (" + earthAcc[0] + ", " + earthAcc[1] + ", " + earthAcc[2] + ")\n");

                        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");
                        Date curDate = new Date(System.currentTimeMillis()); // 獲取當前時間
                        time = formatter.format(curDate);
                        raw = String.valueOf(earthAcc[0]) + "," + String.valueOf(earthAcc[1]) + "," + String.valueOf(earthAcc[2]) + ","
                                + time + "\n";
                        out_acc.write(raw.getBytes());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
                    gravityValues = event.values;
                } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    magneticValues = event.values;
                }
            }
        }

        @Override
        public final void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Do something here if sensor accuracy changes.
        }
    };
    protected void ConnectCheck(){
        //建立一個POP OUT視窗要求使用者輸入IP Address
        final AlertDialog.Builder builder = new AlertDialog.Builder(this,R.style.AlertDialogCustom);
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
                            try {
                                out_arguments.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            //建立與client端地連線
                            connectClient(input.getText().toString());
                            //check if connected
                            if (connectedAvailable == false) //returns true if internet available
                            {
                                Toast.makeText(MainActivity.this, "唉呦  好像沒有連上喔", Toast.LENGTH_LONG).show();
                                ConnectCheck();
                            } else {
                                Toast.makeText(MainActivity.this, "Connected showed from MainActivity!!", Toast.LENGTH_LONG).show();
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
    public void inputName(){
        //建立一個POP OUT視窗要求使用者輸入User name
        final AlertDialog.Builder builder = new AlertDialog.Builder(this,R.style.AlertDialogCustom);
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
    private void connectClient(String ip) {
        //新增一個ClientSocket為client
        setClient(new ClientSocket(ip, PORT, defaultName));
        //將client連  線設定為背景執行
        getClient().execute();
        connectedAvailable =true;
    }

    public void setClient(ClientSocket client) {
        this.client = client;
    }

    public ClientSocket getClient() {
        return client;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(mSensorListener, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        SimpleTimeZone pdt = new SimpleTimeZone(8 * 60 * 60 * 1000, "Asia/Taipei");
        calendar = new GregorianCalendar(pdt);
        handler = new Handler();

        timestamp = skb_timestamp.getProgress();
        skb_timestamp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if(i<2){
                    timestamp = 1;
                }
                else{
                    timestamp = i;
                }
                txv_timestampValue.setText(String.valueOf(timestamp));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        btn_recordPeriod.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isCheck) {
                if(isCheck){
                    text.setText("");
                    skb_timestamp.setClickable(false);
                    btn_record.setClickable(false);
                    handler.post(runnable);
                }
                else{
                    //shutdown handler
                    handler.removeCallbacksAndMessages(null);
                    startRec=false;
                    try {
                        client.disconnect();
                        out_acc.close();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    skb_timestamp.setClickable(true);
                    btn_record.setClickable(true);
                }
            }
        });

        btn_record.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    text.setText("");
                    client.checkSocketConnection();
                    startRecording();
                    skb_timestamp.setClickable(false);
                    btn_recordPeriod.setClickable(false);
                } else {
                    endRecording();
                    btn_recordPeriod.setClickable(true);
                    skb_timestamp.setClickable(true);
                }
            }
        });
    }

    private void startRecording(){
        startRec = true;
        Date trialTime = new Date();
        calendar.setTime(trialTime);
        text.append(" Start: " + calendar.get(Calendar.HOUR_OF_DAY) + ":" +
                calendar.get(Calendar.MINUTE) + ":" +
                calendar.get(Calendar.SECOND));

        //create file
        try {
            String dir_path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/AbsAccCollection";
            File dir = new File(dir_path);
            if (!dir.exists()) {
                dir.mkdir();
            }
            File file_acc = new File(dir, "raw_acc_buffer.txt");
            if (file_acc.exists()) {
                file_acc.delete();
            }
            out_acc = new DataOutputStream(new FileOutputStream(file_acc, true));

            //initial name
            String nameTemp = (defaultName + "\n");
            //write file
            out_acc.write(nameTemp.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void endRecording(){
        startRec = false;
        String dir_path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/AbsAccCollection";
        File dir = new File(dir_path);
        try (InputStream in = new FileInputStream(dir_path + "/raw_acc_buffer.txt")) {
            try (OutputStream out = new FileOutputStream(dir_path + "/raw_acc.txt")) {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Date trialTime = new Date();
        calendar.setTime(trialTime);
        text.append(" End: " + calendar.get(Calendar.HOUR_OF_DAY) + ":" +
                calendar.get(Calendar.MINUTE) + ":" +
                calendar.get(Calendar.SECOND));

        try {
            //send file via socket
            client.sendFile();
            out_acc.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if(!startRec){
                client.checkSocketConnection();
                startRecording();
            }
            else{
                endRecording();
                startRecording();
            }
            handler.postDelayed(this,timestamp*1000);
        }
    };


    @Override
    protected void onPause() {
        super.onPause();

    }
}
