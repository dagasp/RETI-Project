import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;


public interface ClientNotifyInterface extends Remote{
	public void notifyEvent(HashMap<String, String> utenti) throws RemoteException;
	public void notifyNewProjectChat(HashMap<String, String> projectMulticast) throws RemoteException;
}
