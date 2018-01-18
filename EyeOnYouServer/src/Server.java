import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
	public static ServerSocket servsock = null;
	public static Socket sock = null;

	public final static int SOCKET_PORT = 8221; //
	// public final static String FILE_TO_SEND =
	// "C:/Users/TingYuanKeke/Desktop/test/test01.txt"; // you may change this
	public final static int FILE_SIZE = 1024 * 1024;
	// FIle recieve folder

	public static void main(String[] args) throws IOException, InterruptedException {

		try {
			// showing IP
			ServerSocket servsock = new ServerSocket(SOCKET_PORT);
			InetAddress ip;
			ip = InetAddress.getLocalHost();
			System.out.println("Current IP address : " + ip.getHostAddress());

			while (true) {
				System.out.println("\nWaiting client ...");
				sock = servsock.accept();

				System.out.println("Accepted connection : " + sock);
				new ServerThread(sock).run();

			}

		} finally {
			if (servsock != null)
				servsock.close();
		}

	}

}