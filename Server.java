import java.nio.*;
import java.nio.channels.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.net.*;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;

public class Server extends RemoteServer implements RMIInterface{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * 
	 */
	private HashMap<String, String> listUtentiRegistrati;
	private ArrayList<String> listUtentiLoggati;
	private HashMap<String, String> statusUtentiCallBack; //HashMap per le callBack
	private HashMap<String, ClientNotifyInterface> clients;
	private ArrayList<Project> projectsList;
	private ArrayList<String> generatedIP;
	private final String projDirPath;
	private final String projFileListsPath;
	private final String usersFilePath;
	private File restorePath;
	private File projectListsFile;
	private File registeredUsersFile; 
	
	public Server(){
		super();
		listUtentiRegistrati = new HashMap<String, String>();
		listUtentiLoggati = new ArrayList<String>();
		clients = new HashMap<>();
		statusUtentiCallBack = new HashMap<>();
		generatedIP = new ArrayList<>();
		projectsList = new ArrayList<>(); 
		projDirPath = "./Data/Projects";
		restorePath = new File("./Data");
		projFileListsPath = "./Data/projectsList.json";
		usersFilePath = "./Data/registeredUsers.json";
		projectListsFile = new File(projFileListsPath);
		registeredUsersFile = new File(usersFilePath);
	}
	
	
	//Metodo per registrare un nuovo client alle CallBack
	@Override
	public synchronized HashMap<String, String> registerForCallback (ClientNotifyInterface clientInterface, String userName) throws RemoteException{
		if (!clients.containsValue(clientInterface)) {
			clients.put(userName, clientInterface);	
			System.out.println("Nuovo client registrato");
		}
		return statusUtentiCallBack;
	}
	
	@Override
	public synchronized void unregisterForCallback(String user) throws RemoteException {
		System.out.println("Un client si è disconnesso, bye-bye");
		clients.remove(user);
	}
	
	@Override
	//Tramite RMI Callback, invia al client l'indirizzo IP Multicast del progetto di cui fa parte
	public synchronized void sendAddress(String username) throws RemoteException {
		HashMap<String, String> yourProjAddresses = new HashMap<>(); 
		ClientNotifyInterface client = clients.get(username);
		if (projectsList == null) return; //Nessun progetto presente
		for (Project proj : projectsList) {
			if (proj.alreadyMember(username))
				yourProjAddresses.put(proj.getProjectName(), proj.getProjectAddress());
		}
		if (client != null && !yourProjAddresses.isEmpty()) client.notifyNewProjectChat(yourProjAddresses); //Notifico al client la presenza della chat
	}

	private synchronized void doCallbacks(HashMap<String, String> utenti) throws RemoteException {
		for (ClientNotifyInterface cli : clients.values()) {
			cli.notifyEvent(utenti);
		} 
	}
	
	private synchronized void updateCallback(HashMap<String, String> utenti) throws RemoteException {
		doCallbacks(utenti);
	}

	
	//Ripristina i dati del server da file JSON
	public String restoreData(File path) {
		boolean usersRec = false;
		boolean projRec = false;
		if (!path.exists()) return "NoData";
		Gson gsonR = new Gson();
		//Ripristino la lista degli utenti registrati (se esiste)
		if (registeredUsersFile.exists()) {
			try {
				Type token = new TypeToken<HashMap<String, String>>() {}.getType();
				listUtentiRegistrati = gsonR.fromJson(new FileReader(usersFilePath), token);
			} catch (IOException e) {
				e.printStackTrace();
				return "error";
			}
			for (String user : listUtentiRegistrati.keySet()) {
				statusUtentiCallBack.put(user, "offline"); //Tutti vanno 'offline'
			}
			usersRec = true;
		}
		
		//Ripristino la lista dei progetti creati (se esiste) e le rispettive cards
		if (projectListsFile.exists()) {
			try {
				Type token = new TypeToken<ArrayList<Project>>() {}.getType();
				projectsList = gsonR.fromJson(new FileReader(projFileListsPath), token);
			} catch (IOException e) {
				e.printStackTrace();
				return "error";
			}/*
			//Ripristino history per ogni card di quel progetto
			for (Project proj : projectsList) {
				if (proj.getCards() != null) { //se il progetto contiene cards
					ArrayList<String> cards = proj.getCards();
					for (String card : cards) {
						Card c = proj.getCard(card);
						//ArrayList<String> cardHistory = new ArrayList<>();
						Path currPath = Paths.get(projDirPath);
						Path filePath = Paths.get(currPath.toString(), proj.getProjectName(), c.getCardName() + ".json");
						//System.out.println(filePath.toString());
						try {
							Type token = new TypeToken<Card>() {}.getType();
							c = gsonR.fromJson(new FileReader(filePath.toString()), token);
							//c.restoreCardHistory(cardHistory);
						} catch (IOException e) {
							e.printStackTrace();
							return false;
						}
					}
				}
			}*/
			//Ripristino la lista degli indirizzi Multicast di ogni progetto
			for (Project proj : projectsList) {
				generatedIP.add(proj.getProjectAddress());
			}
			projRec = true;
		}
		
		if (usersRec || projRec) return "recovered";
		else return "NoData";
	}
	
	
	//Genera un indirizzo IP Multicast diverso per ogni progetto
	//Incluso tra 224.0.0.0 e 239.255.255.255
	public String multicastIPGenerator () {
		Random r = new Random();
		int one = r.nextInt(239-224) + 224;
		int two = r.nextInt(255);
		int three = r.nextInt(255);
		int four = r.nextInt(255);
		String address = String.valueOf(one) + "." + String.valueOf(two) + "." + String.valueOf(three) + "." + String.valueOf(four);
		return address;
	}
	
	public void startServer () {
		String resp = restoreData(restorePath);
		if (resp.equals("error")) {
			System.err.println("Impossibile ripristinare i dati del Server, esco");
			return;
		}
		if (resp.equals("NoData")) System.out.println("Avvio Server, nessun dato da ripristinare");
		
		if (resp.equals("recovered")) System.out.println("Avvio Server, dati ripristinati correttamente");
		ServerSocketChannel serverChannel;
		Selector selector = null;
		try {
			serverChannel = ServerSocketChannel.open();
			ServerSocket ss = serverChannel.socket();
			InetSocketAddress address = new InetSocketAddress("localhost", 1113);
			ss.bind(address);
			serverChannel.configureBlocking(false);
			selector = Selector.open();
			serverChannel.register(selector, SelectionKey.OP_ACCEPT);
		} catch (IOException ex) {
			ex.printStackTrace();
			return;
		}
		//Creazione cartelle per implementare la persistenza
		File projStoringDir = new File(projDirPath);
		projStoringDir.mkdirs();
		
		System.out.println("SERVER PRONTO, IN ATTESA DI CONNESSIONI");
		while (true) {
			try {
				selector.select();
			} catch (IOException ex) {
				ex.printStackTrace();
				break;
			}
			Set <SelectionKey> readyKeys = selector.selectedKeys();
			Iterator <SelectionKey> iterator = readyKeys.iterator();
			while (iterator.hasNext()) {
				SelectionKey key = iterator.next();
				iterator.remove();
				try {
					if (key.isAcceptable()) {
					ServerSocketChannel server = (ServerSocketChannel) key.channel();
					SocketChannel client = server.accept();
					System.out.println("SERVER: Connessione accettata da " + client.getRemoteAddress());
					client.configureBlocking(false);
					client.register(selector, SelectionKey.OP_READ);
				}
				else if (key.isReadable()) {
					SocketChannel client = (SocketChannel) key.channel();
					ByteBuffer input = ByteBuffer.allocate(2048);
					client.read(input);
					input.flip();
					String request = new String (input.array()).trim();
					if (request.equals("exit")) { //Devo uscire
						System.out.println("EXIT RECEIVED");
						key.channel().close();
						key.cancel();
						continue;
					}
					String toSend = requestDispatcher (request);
					ByteBuffer reply = ByteBuffer.allocate(2048);
					reply.put(toSend.getBytes());
					reply.flip();
					client.register(selector, SelectionKey.OP_WRITE, toSend);
				}
				else if (key.isWritable()) {
					SocketChannel client = (SocketChannel) key.channel();
					String received = (String) key.attachment();
					ByteBuffer sendThis = ByteBuffer.wrap(received.getBytes());
					client.write(sendThis);
					client.register(selector, SelectionKey.OP_READ);
				} 
				}	catch (IOException ex) {
						key.cancel();
						try {
							key.channel().close();
						} catch (IOException e) {
							e.printStackTrace();
						}
				}
			}
		}
	}
	
	//METODI
	
	@Override
	public synchronized String register(String nickUtente, String password) throws RemoteException {
		if (password.isBlank()) {
			return "Password non valida, non può essere vuota!";
		}
		if (!listUtentiRegistrati.containsKey(nickUtente)) {
			listUtentiRegistrati.put(nickUtente, password);
			statusUtentiCallBack.put(nickUtente, "offline");
			updateCallback(statusUtentiCallBack);
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			try {
				Writer w = new FileWriter(usersFilePath);
				gson.toJson(listUtentiRegistrati, w);
				w.flush();
				w.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (JsonIOException j) {
				j.printStackTrace();
			}
			return "Utente " + nickUtente + " registrato correttamente";
		}
		return "Utente " + nickUtente + " già registrato";
	}
	
	private String requestDispatcher (String request) {
		StringTokenizer st = new StringTokenizer(request);
		String command = "";
		String firstArg = "";
		String secondArg = "";
		String thirdArg = "";
		String fourthArg = "";
		String fifthArg = "";
		command = st.nextToken(); //requested command
		if (st.hasMoreTokens())
			firstArg = st.nextToken(); //could be nickname or another argument
		if (st.hasMoreTokens()) 
			secondArg = st.nextToken(); //could be password or another argument
		if (st.hasMoreTokens())
			thirdArg = st.nextToken(); 
		if (st.hasMoreTokens())
			fourthArg = st.nextToken(); 
		if (st.hasMoreTokens())
			fifthArg = st.nextToken(); 
		switch (command) {
		case "login":
			if (!listUtentiRegistrati.containsKey(firstArg)) {
				return "Utente non registrato"; //Utente non registrato
			}
			if (listUtentiRegistrati.containsKey(firstArg) && !listUtentiRegistrati.get(firstArg).equals(secondArg)) {
				return "La password inserita è errata"; //Password errata
			}
			if (!listUtentiLoggati.contains(firstArg)) {
				listUtentiLoggati.add(firstArg);
				statusUtentiCallBack.replace(firstArg, "online"); //Mette l'utente online
				try {
					//RMI CallBack
					updateCallback(statusUtentiCallBack); 
					sendAddress(firstArg); //invio al client gli indirizzi delle chat dei suoi progetti
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				return "Login di " + firstArg + " effettuato";
			}
			return "Utente già loggato";
		case "logout":
			if (!listUtentiLoggati.contains(firstArg)) {
				return "L'utente non è loggato, impossibile effettuare logout";
			}
			if (listUtentiLoggati.remove(firstArg)) {
				statusUtentiCallBack.replace(firstArg, "offline"); //Metto l'utente offline
				try {
					updateCallback(statusUtentiCallBack); //Mando notifica
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				return "Logout di " + firstArg + " effettuato";
			}
		case "createproject": {
			for (Project proj : projectsList) {
				if (proj.getProjectName().equals(firstArg)) {
					return "Progetto con questo nome già presente, inserisci un altro nome!";
				}
			}
			String address = multicastIPGenerator();
			//Controllo che non esista già - in caso ne creo uno nuovo
			if (generatedIP.contains(address)) {
				System.out.println("IP already exists, generating a new one..");
				String newAdd = multicastIPGenerator();
				while (generatedIP.contains(newAdd)) {
					newAdd = multicastIPGenerator();
				}
				address = newAdd;
			}
			//Lo aggiungo alla lista degli IP già generati
			generatedIP.add(address);
			Project project = new Project (firstArg, secondArg, address); 
			projectsList.add(project);
			try {
				sendAddress(secondArg); //Notifica al client l'indirizzo ip della chat del progetto
			} catch (RemoteException re) {
				re.printStackTrace();
			}
			//Aggiungo il progetto al file json
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			try {
				Writer w = new FileWriter(projFileListsPath);
				gson.toJson(projectsList, w);
				w.flush();
				w.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (JsonIOException j) {
				j.printStackTrace();
			}
			//Creo directory per il nuovo progetto
			File projc = new File(projDirPath + "/" + firstArg);
			projc.mkdirs();
			return "Progetto " + firstArg + " creato";
		}
		case "listprojects":
			String toSend = "";
			for (Project proj : projectsList) {
				if (proj.alreadyMember(firstArg)) {
					toSend = toSend +  " " + proj.getProjectName() + ","; 
				}
			}
			if (toSend.isBlank()) return "Non fai parte di nessun progetto :(";
			return "Lista di progetti di cui fai parte:"+ toSend;
		case "addmember": {
			if (projectsList == null) return "Nessun progetto presente";
			String projectName = firstArg;
			String nickUtente = secondArg; //Utente da inserire
			String member = thirdArg; //utente già membro del progetto che chiede l'inserimento
			for (Project proj : projectsList) { //Controllo che l'utente da aggiungere non sia già membro
				if (proj.getProjectName().equals(projectName)) {
					if (proj.alreadyMember(nickUtente)) return "L'utente " + nickUtente + " è già membro del progetto " + proj.getProjectName();
					else {
						if (!listUtentiRegistrati.containsKey(nickUtente)) //Se l'utente non è registrato non lo aggiungo
							return "L'utente non è registrato, impossibile aggiungerlo al progetto";
						else {
							if (proj.alreadyMember(member)) { //Se l'utente è membro del progetto può aggiungere altri membri
								proj.addMember(nickUtente); //Lo aggiungo
								try {
									sendAddress(nickUtente); //Notifica al client l'indirizzo ip della chat del progetto
								} catch (RemoteException re) {
									re.printStackTrace();
								}
								Gson gson = new GsonBuilder().setPrettyPrinting().create();
								try {
									Writer w = new FileWriter(projFileListsPath);
									gson.toJson(projectsList, w);
									w.flush();
									w.close();
								} catch (IOException e) {
									e.printStackTrace();
								} catch (JsonIOException j) {
									j.printStackTrace();
								}
								return "Utente " + nickUtente + " aggiunto al progetto " + proj.getProjectName();
							}
							else return "Non sei membro del progetto richiesto, non puoi aggiungere membri";
						}
					}
				}
			}
			return "Progetto " + projectName + " non trovato";
		}
		case "addcard": {
			String projName = firstArg;
			String cardName = secondArg;
			String description = thirdArg;
			String membro = fourthArg; //Utente che chiede l'aggiunta della card
			if (membro.isBlank()) return "Non hai inserito tutte le info sulla card, riprova-> \naddCard nomeProgetto nomeCard descrizione ";
			for (Project proj : projectsList) {
				if (proj.getProjectName().equals(projName)) {
					if (proj.alreadyMember(membro)) {
						if (proj.addCard(cardName, description)) {
							Card card = proj.getCard(cardName);
							Gson gsonObj = new GsonBuilder().setPrettyPrinting().create();
							try {
								//Ricavo il path della directory del progetto corrente
								Path currPath = Paths.get(projDirPath);
								Path filePath = Paths.get(currPath.toString(), proj.getProjectName(), cardName + ".json");
								Writer w = new FileWriter(filePath.toString());
								Writer projWriter = new FileWriter(projFileListsPath);
								gsonObj.toJson(card, w);
								gsonObj.toJson(projectsList, projWriter);
								w.flush();
								w.close();
								projWriter.flush();
								projWriter.close();
							} catch (IOException e) {
								e.printStackTrace();
							} catch (JsonIOException j) {
								j.printStackTrace();
							}
							return "Card " + cardName + " aggiunta al progetto " + projName;
						}	
						else 
							return "La card esiste già";
					}
					else return "Non sei membro del progetto, non puoi aggiungere card";
				}
			}
			return "Progetto " + projName + " non trovato";
		}
		case "movecard": {
			String projName = firstArg;
			String cardName = secondArg;
			String origin = thirdArg;
			String destination = fourthArg;
			String membro = fifthArg;
			for (Project proj : projectsList) {
				if (proj.getProjectName().equals(projName)) {
					if (proj.alreadyMember(membro)) {
						Gson gsonObj = new GsonBuilder().setPrettyPrinting().create();
						Card card = proj.getCard(cardName);
						String resp = proj.moveCard(cardName, origin, destination);
						if (resp.contains("Impossibile")) { //La card non è stata spostata, evito di scrivere sul file JSON
							return resp;
						}
						try {
							//Ricavo il path della directory del progetto corrente
							Path currPath = Paths.get(projDirPath);
							Path filePath = Paths.get(currPath.toString(), proj.getProjectName(), cardName + ".json");
							Writer w = new FileWriter(filePath.toString());
							Writer projWriter = new FileWriter(projFileListsPath);
							gsonObj.toJson(card, w);
							gsonObj.toJson(projectsList, projWriter);
							w.flush();
							w.close();
							projWriter.flush();
							projWriter.close();
						} catch (IOException e) {
							e.printStackTrace();
						} catch (JsonIOException j) {
							j.printStackTrace();
						}
						return resp;
					}
					else return "Non sei membro del progetto, non puoi spostare una card";
				}
			}
			return "Progetto " + projName + " non trovato";
		}
		case "cancelproject": {
			String projName = firstArg;
			String membro = secondArg;
			for (Project proj : projectsList) {
				if (proj.getProjectName().equals(projName)) {
					if (proj.alreadyMember(membro)) {
						if (proj.allCardsAreDone()) { //Se tutte le cards sono su "done" cancello il progetto
							String addressToRemove = proj.getProjectAddress();
							generatedIP.remove(addressToRemove); //L'indirizzo IP torna disponibile
							projectsList.remove(proj);
							Gson gsonObj = new GsonBuilder().setPrettyPrinting().create();
							Writer projWriter;
							try {
								projWriter = new FileWriter(projFileListsPath);
								gsonObj.toJson(projectsList, projWriter);
								projWriter.flush();
								projWriter.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
							return "Progetto " + projName + " rimosso";
							
						}
						else return "Non tutte le cards sono su done, impossibile cancellare il progetto " + projName; 
					}
					else return "Non sei membro del progetto, non puoi rimuoverlo";
				}
			}
			return "Progetto " + projName + " non trovato";
		}
		case "showmembers": {
			String projName = firstArg;
			String membro = secondArg;
			String projMembers = "";
			for (Project proj : projectsList) {
				if (proj.getProjectName().equals(projName)) {
					if (proj.alreadyMember(membro)) {
						projMembers = proj.getProjectMembers().toString();
						return projMembers;
					}
					else return "Non sei membro di questo progetto, non puoi vedere i membri";
				}
			}
			return "Progetto " + projName + " non trovato";
		}
		case "showcards": {
			String projName = firstArg;
			String membro = secondArg;
			String cardsList = "";
			for (Project proj : projectsList) {
				if (proj.getProjectName().equals(projName)) {
					if (proj.alreadyMember(membro)) {
						cardsList = proj.getCards().toString();
						return cardsList;
					}
					else return "-NothingFound-";
				}
			}
			return "-NothingFound-";
		}
		case "showcard": {
			String projName = firstArg;
			String cardName = secondArg;
			String membro = thirdArg;
			String cardInfo = "";
			for (Project proj : projectsList) {
				if (proj.getProjectName().equals(projName)) {
					if (proj.alreadyMember(membro)) {
						Card card = proj.getCard(cardName);
						if (card == null) return "La card " + cardName + " non esiste";
						else {
							cardInfo = "Nome card=" + card.getCardName() + "\ndescrizione testuale=" + card.getDescrizioneCard() + "\nstato=" + card.getCurrentList(cardName);
							return cardInfo;
						}
					}
					else return "Non sei membro di questo progetto, non hai accesso alle info sulla card";
				}
			}
			return "Progetto " + projName + " non trovato";
		}
		case "getcardhistory": {
			String projName = firstArg;
			String cardName = secondArg;
			String membro = thirdArg;
			String cardHistory = "";
			for (Project proj : projectsList) {
				if (proj.getProjectName().equals(projName)) {
					if (proj.alreadyMember(membro)) {
						Card card = proj.getCard(cardName);
						if (card == null) return "La card " + cardName + " non esiste";
						else {
							cardHistory = card.getCardHistory(cardName).toString();
							return cardHistory;
						}
					} else return "Non sei membro di questo progetto, non hai accesso alle info sulla card";
				}
			}
			return "Progetto " + projName + " non trovato";
		}
		} //chiusura switch
		return "Errore generico, riprova!";
	}
}
