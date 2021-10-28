import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class ServerMain {
	public static void main (String[] args) throws Exception {
		Server server = new Server();
		try {
			RMIInterface stub;
			Registry registry;
			stub = (RMIInterface) UnicastRemoteObject.exportObject(server, 0);
			LocateRegistry.createRegistry(9999);
			registry = LocateRegistry.getRegistry(9999);
			registry.rebind("RMIServer", stub);
		} catch (RemoteException Re) {
			Re.printStackTrace();
			return;
		}
		server.startServer();
	}
}
