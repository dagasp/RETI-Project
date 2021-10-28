import java.util.ArrayList;

public class Project {
	private String projectName;
	private String projectAddress; //Indirizzo multicast - come da specifica diverso per ogni progetto
	private ArrayList<Card> todo;
	private ArrayList<Card> inProgress;
	private ArrayList<Card> toBeRevised;
	private ArrayList<Card> done;
	private ArrayList<String> cards; //Lista delle card già presenti nel progetto
	private ArrayList<String> membri = new ArrayList<>(); //Lista dei membri di un progetto
	
	public Project (String name, String member, String multicastAddress) {
		projectName = name;
		projectAddress = multicastAddress;
		membri = new ArrayList<>();
		membri.add(member);
		todo = new ArrayList<>();
		inProgress = new ArrayList<>();
		toBeRevised = new ArrayList<>();
		done = new ArrayList<>();
		cards = new ArrayList<>();
	}
	
	public String getProjectName() {
		return projectName;
	}
	
	public String getProjectAddress() {
		return projectAddress;
	}
	
	public boolean alreadyMember(String name) {
		return membri.contains(name);
	}
	 
	public ArrayList<String> getProjectMembers() {
		return membri;
	}
	
	public ArrayList<String> getCards(){
		return cards;
	}
	
	public Card getCard (String name) {
		if (!cards.contains(name)) return null; //La card non esiste
		//Visito tutte le liste per trovare la card
		for (Card card : todo) {
			if (card.getCardName().equals(name))
				return card;
		}
		for (Card card : inProgress) {
			if (card.getCardName().equals(name))
				return card;
		}
		for (Card card : toBeRevised) {
			if (card.getCardName().equals(name))
				return card;
		}
		for (Card card : done) {
			if (card.getCardName().equals(name))
				return card;
		}
		return null;
	}
	
	public boolean addCard(String name, String desc) {
		if (cards.contains(name))
			return false;
		Card card = new Card(name,desc);
		cards.add(name);
		todo.add(card);
		return true;
	}
	
	public void addMember(String name) {
		if (!alreadyMember(name))
			membri.add(name);
	}
	
	public Card pullCard (String cardName, ArrayList<Card> cardList) {
		for (Card card : cardList) {
			if (card.getCardName().equals(cardName)) {
				cardList.remove(card);
				return card;
			}
		}
		System.out.println("Card not found");
		return null;
	}
	
	public String moveCard(String cardName, String origin, String destination) {
		Card card;
		switch (origin) {
		case "todo":
			if (destination.equals("toberevised") || destination.equals("done")) return "Impossibile spostare card da " + origin + " a " + destination;
			card = pullCard(cardName, todo); //Torna la card da spostare
			if (card == null) return "Card non trovata";
			inProgress.add(card);
			card.updateAndChangeCardList(destination); //Aggiorno la card history e l'attuale lista della card
			break;
		case "inprogress":
			if (destination.equals("todo")) return "Impossibile spostare card da " + origin + " a " + destination;
			card = pullCard(cardName, inProgress);
			if (card == null) return "Card non trovata";
			if (destination.equals("toberevised"))
				toBeRevised.add(card);
			if (destination.equals("done"))
				done.add(card);
			card.updateAndChangeCardList(destination); //Aggiorno la card history e l'attuale lista della card
			break;
		case "toberevised":
			if (destination.equals("todo")) return "Impossibile spostare card da " + origin + " a " + destination;;
			card = pullCard(cardName, toBeRevised);
			if (card == null) return "Card non trovata";
			if (destination.equals("inprogress"))
				inProgress.add(card);
			if (destination.equals("done"))
				done.add(card);
			card.updateAndChangeCardList(destination); //Aggiorno la card history e l'attuale lista della card
			break;
		case "done":
			return "Non puoi spostare la card da done ad un'altra lista :(" ;
		default:
			return "Forse hai sbagliato a scrivere la lista, riprova!" ;
		}
		return "Card " + cardName + " spostata da " + origin + " a " + destination;
	}
	
	public boolean allCardsAreDone() {
		return todo.isEmpty() && inProgress.isEmpty() && toBeRevised.isEmpty();
	}
}
