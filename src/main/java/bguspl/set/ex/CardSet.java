package bguspl.set.ex;

public class CardSet {

    private int[] slots;
    private int playerId;

    public CardSet(int [] slots, int playerId) {
        this.slots = slots;
        this.playerId = playerId;
    }

    public int[] getSlots() {
        return slots;
    }

    public int getPlayerId() {
        return playerId;
    }

}
