package com.example.weichun.deviceposition;

/**
 * Created by TingYuanKeke on 2017/11/16.
 */

import android.os.AsyncTask;

import java.io.BufferedInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.net.Socket;
import java.net.UnknownHostException;

import android.os.Environment;

/**
 * Created by TingYuanKeklle on 2015/11/09.
 */
public class ClientSocket extends AsyncTask<String, Void, String> {
    private String serverIP;
    private int serverPort;
    private String clientName ;

    private Socket socket;

    public static boolean connected = false;

    public ClientSocket(String server, int port,String name) {
        serverIP = server;
        serverPort = port;
        clientName = name;
    }

    public void sendFile() {

        if (connected) {
            new Thread(new Runnable(){
                @Override
                public void run() {
                    try {
                        String dir_path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/AbsAccCollection/raw_acc.txt";
                        File myFile = new File (dir_path);
                        byte[] mybytearray = new byte[(int) myFile.length()];
                        FileInputStream fis = new FileInputStream(myFile);
                        BufferedInputStream bis = new BufferedInputStream(fis);
                        bis.read(mybytearray, 0, mybytearray.length);
                        OutputStream os =  socket.getOutputStream();
                        System.out.println("Sending...");


                        os.write(mybytearray, 0, mybytearray.length);
                        System.out.println("have been sent");
                        os.flush();
                        os.close();
                        bis.close();
                        fis.close();

                        socket.close();

                        socket = new Socket(serverIP, serverPort);
                    }
                    catch(IOException e){
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    public void disconnect(){
        new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    if(connected){
                        socket.close();
                        connected = false;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
    public void checkSocketConnection(){
        new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    if(!connected){
                        socket = new Socket(serverIP, serverPort);
                        connected = true;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
    @Override
    protected String doInBackground(String... params) {
        try {
            System.out.println("Attempting to connect to " + serverIP + ":" + serverPort);
            socket = new Socket(serverIP, serverPort);
            connected = true;

        } catch (UnknownHostException uhe) {
            System.out.println("Host unknown: " + uhe.getMessage());
        } catch (IOException ioe) {
            System.out.println("Unexpected IO exception: " + ioe.getMessage());
        } catch (Exception fe) {
            System.out.println("Unexpected fatal exception: " + fe);
        }
        // TODO Auto-generated method stub
        return null;
    }

    public Socket getSocket(){
        return this.socket;
    }
}