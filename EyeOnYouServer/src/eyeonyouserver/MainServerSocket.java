package eyeonyouserver;
import java.io.IOException;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Timer;
import java.util.TimerTask;

import eyeonyouserver.SocketClientRunPID;

public class MainServerSocket {
	private int port;
	private ServerSocket servsock;
	
	private static SocketClientRunPID clientRunPID = null;
    static String ipaddress = "localhost";
    
	private Timer timerStartPairing = new Timer();
	private Timer timerEndPairing = new Timer();
	public static boolean isPairing = false;
	public int collectInterval = 3000;
	public int pairingInterval =100;
	
	public MainServerSocket(int port) throws IOException {
		this.port = port;
		this.servsock = new ServerSocket(port);
		
		System.out.println("EyeOnYouServer starts!");
	}

	public void run() {
		clientRunPID = new SocketClientRunPID(ipaddress);
		
		timerStartPairing.scheduleAtFixedRate(new TimerTask() {
			 @Override
			 public void run() {
				 isPairing = true;
				 clientRunPID.requestKinectRunPID();
				 }
			 }, 500, collectInterval);
		 timerEndPairing.scheduleAtFixedRate(new TimerTask() {
			 @Override
			 public void run() {
				 isPairing = false;
				 }
			 }, 500+collectInterval+pairingInterval, collectInterval);
		 
		ExecutorService executor = Executors.newFixedThreadPool(5);
		while (true)// �û�����
		{
			Socket sock = null;
			try {
				System.out.println("waiting client...");
				sock = servsock.accept();
				System.out.println("Accepted connection : " + sock);
			} catch (java.io.IOException e) {
				e.printStackTrace();
			}
			
			 Runnable worker = new FileRecieveThread(sock);
			 executor.execute(worker);
		}
	}
	
	public void close() throws IOException{
		this.servsock.close();
	}
}