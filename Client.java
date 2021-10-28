import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.StringTokenizer;

public class Client extends RemoteObject implements ClientNotifyInterface{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private SocketChannel client;
	private String nickName;
	private boolean logged = false;
	private boolean mustStop = false;
	private final int port;
	private HashMap<String, String> callbackUtenti; //Map per le callback aggiornate dal Server
	private HashMap<String, String> statusUtenti; //Map locale per lo stato degli utenti
	private HashMap<String, String> projectMulticastAddress; //Map per gli indirizzi Multicast dei progetti
	private HashMap<String, ArrayList<String>> chatMsgs; //Lista per i messaggi letti dalla chat
	private ArrayList<Thread> chatThreadsList; 
	public Client() {
		super();
		port = 7777;
		statusUtenti = new HashMap<>();
		projectMulticastAddress = new HashMap<>();
		chatMsgs = new HashMap<>();
		callbackUtenti = null;
		chatThreadsList = new ArrayList<>();
	}
	 public static void main(String[] args) throws IOException, InterruptedException {
		 	Client c = new Client();
		 	try {
				c.start();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
	    } 
	 
	//Metodo RMI Callback
		@Override
		//Aggiorna la lista degli utenti attivi e il loro stato
		public void notifyEvent(HashMap<String, String> utenti) throws RemoteException {
			if (callbackUtenti == null) 
				callbackUtenti = new HashMap<>();
			callbackUtenti = utenti;
		}	
		
		@Override
		//Crea un thread di ascolto per la chat del progetto
		public void notifyNewProjectChat(HashMap<String, String> projectMulticast) throws RemoteException {
			for (String proj : projectMulticast.keySet()) {
				if (!projectMulticastAddress.containsKey(proj)) { //Se l'indirizzo non esiste già
					Thread chatThread = new Thread(new ChatReaderThread(this, projectMulticast.get(proj), port));
			 		projectMulticastAddress.put(proj, projectMulticast.get(proj)); //Aggiorno la map dei progetti con i loro relativi indirizzi
			 		chatThreadsList.add(chatThread);
			 		chatThread.start(); 
				}
			}
			
		}
		
		
		//Aggiunge un messaggio nella lista dei messaggi della chat
		public void readMsgFromSocket(String address, String message) {
			ArrayList<String> list;
			synchronized (chatMsgs) {
				if (chatMsgs.containsKey(address)) {
					list = chatMsgs.get(address);
					list.add(message);
				}
				else {
					list = new ArrayList<String>();
					list.add(message);
					chatMsgs.put(address, list);
				}
			}	
		}
		
	 public String sendMessage (String msg) throws IOException{
		 	byte[] message = msg.getBytes();
	        ByteBuffer buffer = ByteBuffer.wrap(message);
	        client.write(buffer);
	        buffer.clear();
	        ByteBuffer readed = ByteBuffer.allocate(2048);
	        client.read(readed);
	        String toPrint = new String(readed.array()).trim();
	        return toPrint;
	 }
	 
	 //Stampa all'utente la lista dei comandi disponibili
	 public void usage() {
		 System.out.println("--- Lista comandi disponibili: --- "
				 + "--- RICORDA GLI SPAZI TRA UNA PAROLA ED UN'ALTRA! :) ---"
		 		+ "\nregister(nickUtente, password)" + "\nlogin(nickUtente, password)" + "\nlogout(nickUtente)" + "\nlistUsers"
				 + "\nlistOnlineUsers"
		 		+ "\nlistProjects" + "\ncreateProject(projectName)\naddMember(projectName, nickUtente)\nshowMembers(projectName)\n"
				 + "showCards(projectName)\nshowCard(projectName, cardName)\naddCard(projectName, cardName, descrizione)\nmoveCard(projectName, cardName, listaPartenza, listaDestinazione)"
				 + "\ngetCardHistory(projectName, cardName)\nreadChat(projectName)\nsendChatMsg(projectName, messaggio)\ncancelProject(projectName)"); 
	 }
	 
	 //Metodo invocato dal comando logout
	 public void logoutProcess() {
		 //Interrompo i thread
		 for (Thread t : chatThreadsList) {
			 t.interrupt();
		 }
		 //Pulisco le strutture dati
		 synchronized (chatMsgs) {
			 chatMsgs.clear();
		 }
		 projectMulticastAddress.clear();
		 chatThreadsList.clear();
		 //Le altre verranno cambiate con callBack RMI, non ho bisogno di effettuare un 'clear'
	 }
	 
	 public void start() throws IOException {
		 Registry registry;
		 RMIInterface stub;
		 ClientNotifyInterface callbackObj;
		 ClientNotifyInterface stubCallback;
		 try {
			 registry = LocateRegistry.getRegistry(9999);
			 stub = (RMIInterface) registry.lookup("RMIServer");
			 callbackObj = this;
			 stubCallback = (ClientNotifyInterface) UnicastRemoteObject.exportObject(callbackObj, 0);
		 } catch (RemoteException | NotBoundException re) {
			 System.out.println("Avviare il server prima!");
			 return;
		 }
		 
	     Scanner input = new Scanner(System.in);
	     while (!mustStop) {
		     String s = input.nextLine();
			 StringTokenizer st = new StringTokenizer(s);
			 String command = "";
			 String firstArg = "";
			 String secondArg = "";
			 if (st.hasMoreTokens()) {
				command = st.nextToken();
			 }
			 switch (command) {
			 	case "register": {
					if (st.hasMoreTokens()) {
						firstArg = st.nextToken();
					}
					if (st.hasMoreTokens()) {
						secondArg = st.nextToken();
					}
					System.out.println(stub.register(firstArg, secondArg));
					break;
			 	}
			 	case "login": {
			 		if (logged) {
			 			System.err.println("Utente già loggato!");
			 			break;
			 		}
				     try {
				    	 InetSocketAddress address = new InetSocketAddress("localhost", 1113);
						 client = SocketChannel.open(address);
					} catch (IOException e) {
						e.printStackTrace();
						return;
					}
				    System.out.println("Mi connetto al server...");
				    if (st.hasMoreTokens())
				    	firstArg = st.nextToken();
				    if (st.hasMoreTokens())
				    	secondArg = st.nextToken();
				    try {
		 				statusUtenti = stub.registerForCallback(stubCallback, firstArg); //Mi registro alle notifiche e ottengo la lista degli utenti registrati
		 			} catch (RemoteException e) {
		 				System.out.println("Impossibile registrarsi al servizio di notifica");
		 			}
			 		String reply = sendMessage(s);
			 		if (reply.contains("effettuato")) { //Il server ha effettuato il login, OK
				 		logged = true; //Mi ricordo che ho già effettuato il login
				 		System.out.println(reply);
				 		nickName = firstArg;
			 		} 
			 		
			 		else { //Login non effettuato, stampo messaggio d'errore ricevuto dal server
			 			System.err.println(reply);
			 			break;
			 		}
			 		
			 		if (statusUtenti != null) {
			 			System.out.println("Lista utenti registrati e loro stato:");
			 			for (String user : statusUtenti.keySet()) {
			 				System.out.println(user +  " " + callbackUtenti.get(user).toString()); 
			 			}
			 		}
			 		break;
			 	}
			 	case "logout": {
			 		if (!logged) { //Se non ho loggato nessuno
			 			System.err.println("Non sei loggato, effettua il login!");
			 			break;
			 		}
			 		String user = "";
			 		if (st.hasMoreTokens())
			 			user = st.nextToken();
			 		else {
			 			System.err.println("Inserisci l'username!");
			 			break;
			 		}
			 		if (!user.equals(nickName)) {
			 			System.err.println("Non puoi fare il logout di un altro account");
			 			break;
			 		}
			 		try {
			 			stub.unregisterForCallback(nickName);
			 		} catch (RemoteException re) {
			 			re.printStackTrace();
			 		}
			 		String res = sendMessage(s);
			 		System.out.println(res);
		 			logged = false;
		 			//Cancello tutti i dati e chiudo i thread attivi
		 			logoutProcess();
			 		break;
			 	}
			 	case "listusers": {
			 		if (!logged) {
			 			System.err.println("Non sei loggato, effettua il login!");
			 			break;
			 		}
			 		if (callbackUtenti == null) { //Lista utenti vuota
			 			System.err.println("Nessun utente registrato");
			 			break;
			 		}
			 		System.out.println("Lista utenti registrati al servizio e il loro stato");
			 		System.out.println(callbackUtenti.toString());
			 		/*for (String user : callbackUtenti.keySet()) {
			 			System.out.println(user +  " " + callbackUtenti.get(user).toString()); 
			 		}*/
			 		break;
			 	}
			 	case "listonlineusers": {
			 		if (!logged) {
			 			System.err.println("Non sei loggato, effettua il login!");
			 			break;
			 		}
			 		if (callbackUtenti == null) { //Lista utenti vuota
			 			System.err.println("Nessun utente online");
			 			break;
			 		}
			 		System.out.println("Lista utenti online");
			 		for (String user : callbackUtenti.keySet()) {
			 			if (callbackUtenti.get(user).equals("online"))
			 				System.out.println(user); 
			 		}
			 		break;
			 	}
			 	case "createproject": {
			 		if (!logged) {
			 			System.err.println("Non sei loggato, effettua il login!");
			 			break;
			 		}
			 		if (!st.hasMoreTokens()) {
			 			System.err.println("Il nome del progetto non può essere vuoto, riprova");
			 			break;
			 		}
			 		String toSend = s + " " + nickName;
			 		String getRep = sendMessage(toSend);
			 		System.out.println(getRep);
			 		break;
			 	}
			 	case "listprojects": {
			 		if (!logged) {
			 			System.err.println("Non sei loggato, effettua il login!");
			 			break;
			 		}
			 		String send = s + " " + nickName;
			 		String res = sendMessage(send);
			 		System.out.println(res);
			 		break;
			 	}
			 	case "addmember": {
			 		if (!logged) {
			 			System.err.println("Non sei loggato, effettua il login!");
			 			break;
			 		}
			 		String res = sendMessage(s + " " + nickName); 
			 		System.out.println(res);
			 		break;
			 	}
			 	case "addcard": {
			 		if (!logged) {
			 			System.err.println("Non sei loggato, effettua il login!");
			 			break;
			 		}
			 		String res = sendMessage(s + " " + nickName);
			 		System.out.println(res);
			 		break;
			 	}
			 	case "movecard": {
			 		if (!logged) {
			 			System.err.println("Non sei loggato, effettua il login!");
			 			break; 
			 		}
			 		String res = sendMessage(s + " " + nickName);
			 		System.out.println(res);
			 		
			 		//Scrivo in chat dello spostamento così che tutti gli utenti ne tengano conto
			 		String projName = "";
			 		String cardName = "";
			 		String origin = "";
			 		String destination = "";
			 		if (st.hasMoreTokens())
			 			projName = st.nextToken();
			 		if (st.hasMoreTokens())
			 			cardName = st.nextToken();
			 		if (st.hasMoreTokens())
			 			origin = st.nextToken();
			 		if (st.hasMoreTokens())
			 			destination = st.nextToken();
			 		String multicastAddress = projectMulticastAddress.get(projName); //Prendo l'indirizzo di questo progetto dalla map
			 		if (multicastAddress == null) {
			 			System.err.println("Indirizzo IP non esiste");
			 			break;
			 		}
			 		DatagramSocket socket = new DatagramSocket();
			 		String msgToSend = "Messaggio da WORTH: " + nickName + " ha spostato " + cardName + " da " + origin + " a " + destination; 
			 		byte[] buf = msgToSend.getBytes();
			 		InetAddress group = InetAddress.getByName(multicastAddress);
			 		if (!group.isMulticastAddress()) {
			 			System.err.println("Errore - l'indirizzo IP non è un IP Multicast valido");
			 			break;
			 		}
			 		DatagramPacket datagram = new DatagramPacket(buf, buf.length, group, port);
			 		try {
			 			socket.send(datagram);
			 		} catch (IOException e) {
			 			System.err.println("Messaggio non inviato");
			 			break;
			 		}
			 		socket.close();
			 		break;
			 	}
			 	case "cancelproject": {
			 		if (!logged) {
			 			System.err.println("Non sei loggato, effettua il login!");
			 			break; 
			 		}
			 		String res = sendMessage(s + " " + nickName);
			 		System.out.println(res);
			 		break;
			 	}
			 	case "showmembers": {
			 		if (!logged) {
			 			System.err.println("Non sei loggato, effettua il login!");
			 			break; 
			 		}
			 		System.out.println("Lista dei membri del progetto:");
			 		String res = sendMessage(s + " " + nickName);
			 		System.out.println(res);
			 		break;
			 	}
			 	case "showcards": {
			 		if (!logged) {
			 			System.err.println("Non sei loggato, effettua il login!");
			 			break; 
			 		}
			 		String res = sendMessage(s + " " + nickName);
			 		System.out.println("Lista delle cards del progetto:");
			 		System.out.println(res);
			 		break;
			 	}
			 	case "showcard": {
			 		if (!logged) {
			 			System.err.println("Non sei loggato, effettua il login!");
			 			break; 
			 		}
			 		String res = sendMessage (s + " " + nickName);
			 		System.out.println(res);
			 		break;
			 	}
			 	case "getcardhistory": {
			 		if (!logged) {
			 			System.err.println("Non sei loggato, effettua il login!");
			 			break; 
			 		}
			 		String res = sendMessage(s + " " + nickName);
			 		System.out.println("Card history: ");
			 		System.out.println(res);
			 		break;
			 	}
			 	case "sendchatmsg": {
			 		if (!logged) {
			 			System.err.println("Non sei loggato, effettua il login!");
			 			break; 
			 		}
			 		String projName ="";
			 		if (st.hasMoreTokens())
			 			projName = st.nextToken(); //estraggo il nome del progetto dal messaggio
			 		String msg = "";
			 		while (st.hasMoreTokens())
			 			msg = msg + " " + st.nextToken(); //Copio tutto il messaggio in 'msg' (se contiene spazi)
			 		String msgToSend = "Messaggio da '" + nickName + "':" + msg;
			 		String multicastAddress = projectMulticastAddress.get(projName); //Prendo l'indirizzo di questo progetto dalla map
			 		if (multicastAddress == null) {
			 			System.err.println("Indirizzo IP non esiste");
			 			break;
			 		}
			 		//System.out.println("INVIO MESSAGGIO ALL'INDIRIZZO " + multicastAddress);
			 		DatagramSocket socket = new DatagramSocket();
			 		byte[] buf = msgToSend.getBytes();
			 		InetAddress group = InetAddress.getByName(multicastAddress);
			 		if (!group.isMulticastAddress()) {
			 			System.err.println("Errore - l'indirizzo IP non è un IP Multicast valido");
			 			break;
			 		}
			 		DatagramPacket datagram = new DatagramPacket(buf, buf.length, group, port);
			 		try {
			 			socket.send(datagram);
			 		} catch (IOException e) {
			 			System.err.println("Messaggio non inviato");
			 			break;
			 		}
			 		System.out.println("Messaggio inviato alla chat del progetto " + projName);
			 		socket.close();
			 		break;
			 	}
			 	case "readchat": {
			 		if (!logged) {
			 			System.err.println("Non sei loggato, effettua il login!");
			 			break; 
			 		}
			 		//System.out.println(chatMsgs.toString());
			 		String pName = "";
			 		if (st.hasMoreTokens())
			 			pName = st.nextToken();
			 		else {
			 			System.err.println("Non hai inserito il nome del progetto, riprova!");
			 			break;
			 		}
			 		String projAddress = projectMulticastAddress.get(pName);
			 		if (projAddress == null) {
			 			System.err.println("Indirizzo IP non trovato");
			 			break;
			 		}
			 		//System.out.println("RICEVO MESSAGGIO ALL'INDIRIZZO " + projAddress);
			 		if (projAddress.isBlank()) {
			 			System.out.println("Errore nel prelevare l'indirizzo Multicast");
			 			break;
			 		}
			 		//String toRead ="";
			 		//System.out.println(chatMsgs.toString());
			 		ArrayList<String> msgs = null;
			 		synchronized (chatMsgs) {
				 		msgs = chatMsgs.get(projAddress);
			 			if (msgs == null) //La lista non esiste ancora
				 			System.out.println("Nessun messaggio da leggere");
				 		else {
				 			if (msgs.isEmpty()) { //Lista vuota
				 				System.out.println("Nessun messaggio da leggere");
				 				break;
				 			}
				 			System.out.println("--- Chat relativa al progetto " + pName + " ---\n");
				 			System.out.println(msgs.toString());
				 			System.out.println("\nFine messaggi");
				 			//Elimino i messaggi già letti
					 		ArrayList<String> list = chatMsgs.get(projAddress);
					 		list.clear();
				 		}
			 		}
			 		break;
			 	}
			 	case "help":
			 		usage();
			 		break;
			 	case "exit":
			 		if (logged) {
			 			System.err.println("Effettuare il logout prima di chiudere");
			 			break;
			 		}
			 		mustStop = true;
			 		String exit = command;
			 		sendMessage(exit);
			 		System.out.println("ADDIOS");
			 		break;
			 	default:
			 		System.out.println("Ooops, comando sbagliato, ecco a te la lista di quelli supportati");
			 		usage();
			 		break;
				}
		 }
	     input.close();
	     System.exit(0); 
	  }
	
	  
}
