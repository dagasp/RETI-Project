import java.util.ArrayList;

public class Card {
	private String cardName;
	private String descrizioneCard;
	private ArrayList<String> cardHistory;
	private String currentList;
	public Card(String name, String descrizione) {
		this.cardName = name;
		this.descrizioneCard = descrizione;
		cardHistory = new ArrayList<>(); //Card history list
		cardHistory.add("todo");
		currentList = "todo"; //Come specifica, di default va su todo
	}
	
	public void updateAndChangeCardList(String destination) {
		cardHistory.add(destination);
		changeCardList(destination);
	}
	
	public void changeCardList(String destination) {
		currentList = destination;
	}
	
	public String getCurrentList(String cardName) {
		return currentList;
	}
	
	public ArrayList<String> getCardHistory (String cardName) {
		return cardHistory;
	}
	
	public String getCardName() {
		return cardName;
	}
	public String getDescrizioneCard() {
		return descrizioneCard;
	}
}
