import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class ChatReaderThread extends Thread{
	private Client client;
	private String groupAddress;
	private int port;
	public ChatReaderThread (Client client, String group, int port) {
		this.client = client;
		this.groupAddress = group;
		this.port = port;
	}
	

	public void run() {
		MulticastSocket socket = null;
		try {
			socket = new MulticastSocket(port);
			InetAddress group = InetAddress.getByName(groupAddress);
			socket.joinGroup(group);
		} catch (IOException e2) {
			e2.printStackTrace();
		}
		while (!currentThread().isInterrupted()) {
			byte[] buffer = new byte[1024];
			DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
			String message;
			try {
				socket.receive(datagram);
				if (!currentThread().isInterrupted()) {
					message = new String(buffer, 0, datagram.getLength(), "UTF-8");
					client.readMsgFromSocket(groupAddress, message); //Invia messaggio al client
				}
			} catch (IOException e) {
				System.out.println("Socket chiuso");
			}
		}
	}
}
