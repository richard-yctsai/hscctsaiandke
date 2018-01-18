import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;

public class ServerThread {
	private Socket sock =null;
	
	public final static int FILE_SIZE =  1024 * 1024;
	// FIle recieve folder

	
	public ServerThread(Socket s){
		this.sock = s;
	}
	public void run(){
		FileOutputStream fos = null;
		BufferedOutputStream bos = null;
		
		
		try{
			int bytesRead;
			int current = 0;
			
			// receive file
			System.out.println("Ready to recieve file");
			byte[] mybytearray = new byte[FILE_SIZE];
			InputStream is = sock.getInputStream();
			DataInputStream dis =  new DataInputStream(is); 
			
			//initial name
			String name = dis.readLine();
			String FILE_TO_RECEIVED = "C:/Users/Public/Data/IMUData/"+name+".txt";
			
			//recieve file
			bytesRead = dis.read(mybytearray, 0, mybytearray.length);
			current = bytesRead;
			if (bytesRead > 1) {
				do {
					bytesRead = dis.read(mybytearray, current, (mybytearray.length - current));
					if (bytesRead >= 0)
						current += bytesRead;
					
				} while (bytesRead > -1);
				fos = new FileOutputStream(FILE_TO_RECEIVED);
				bos = new BufferedOutputStream(fos);
				bos.write(mybytearray, 0, current);
				
				bos.flush();
				System.out.println("\n**File from *" + name + "* has been downloaded (" + current + " bytes read)\n");
				bos.close();
				sock.shutdownInput();

			}
		}
		catch(IOException e){
			System.out.println("File download failed");
		}
	}
}
