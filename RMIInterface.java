import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;

public interface RMIInterface extends Remote{
	String register(String nickUtente, String password) throws RemoteException;
	public HashMap<String, String> registerForCallback (ClientNotifyInterface clientInterface, String userName) throws RemoteException;
	public void unregisterForCallback(String user) throws RemoteException;
	public void sendAddress(String username) throws RemoteException;
}
