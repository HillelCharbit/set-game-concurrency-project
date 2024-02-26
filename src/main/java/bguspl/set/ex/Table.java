package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * Mapping between a slot and the player's id if a token is placed in it (null if none).
     */
    protected volatile Set<Integer>[] slotToPlayerToken; // player token per slot (if any)

    private boolean isBusy;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {
        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        slotToPlayerToken = new Set[env.config.tableSize];
        for (int i = 0; i < env.config.tableSize; i++) {
            slotToPlayerToken[i] = new HashSet<>();
        }        
        this.isBusy = false;
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public synchronized void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        // if there is a token on the card, remove it
        if (slotToPlayerToken[slot] != null) {
            slotToPlayerToken[slot].clear();
        }
        // remove the card from the table
        int card = slotToCard[slot];
        slotToCard[slot] = null;
        cardToSlot[card] = null;
        env.ui.removeCard(slot);
    }

    public synchronized boolean placeOrRemoveToken(int player, int slot) {
        if (samePlayerTokenOnSlot(player, slot)) {
            return removeToken(player, slot);
        } else {
            return placeToken(player, slot);
        }
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public boolean placeToken(int player, int slot) {
        synchronized (slotToPlayerToken[slot]){       
            if (!slotHasCard(slot)) {
                env.logger.warning("error: trying to place a token on an empty slot");
                return false;
            }
            if (playerHasMaxTokens(player)) {
                env.logger.warning("error: trying to place a token when the player already has the maximum number of tokens");
                return false;
            }
                slotToPlayerToken[slot].add(player);
                env.ui.placeToken(player, slot);
                return true;
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        synchronized (slotToPlayerToken[slot]){
            if (slotToCard[slot] == null) {
                env.logger.warning("error: trying to remove a token from an empty slot");
                return false;
            }
            slotToPlayerToken[slot].remove(player);
            env.ui.removeToken(player, slot);
            return true;
        }
    }

    public int getTableSize() { 
        return env.config.tableSize;
    }

    public int getCard(int slot) {
        if (slotToCard[slot] == null) {
            return -1;
        }
        return slotToCard[slot];
    }
    
    public ArrayList<Integer> GetEmptySlots() {
        ArrayList<Integer> emptySlots = new ArrayList<>();
        for (int i = 0; i < slotToCard.length; i++) 
        {
            if (slotToCard[i] == null)
                emptySlots.add(i);
        }
        return emptySlots;
    }

    private boolean slotHasCard(int slot) {
        return slotToCard[slot] != null;
    }

    private boolean samePlayerTokenOnSlot(int player, int slot) {
        return slotToPlayerToken[slot] != null && slotToPlayerToken[slot].contains(player);
    }
    public boolean playerHasMaxTokens(int player) {
        int tokens = 0;
        for (int i = 0; i < slotToPlayerToken.length; i++) {
            if (slotToPlayerToken[i] != null && slotToPlayerToken[i].contains(player)) {
                tokens++;
            }
        }
        return tokens == env.config.featureSize;
    }

    public int[] getPlayerSlots(int player) {
        int[] slots = new int[env.config.featureSize];
        int j = 0;
        for (int i = 0; i < slotToPlayerToken.length; i++) {
            if (slotToPlayerToken[i] != null && slotToPlayerToken[i].contains(player)) {
                slots[j++] = i;
            }
        }
        return slots;
    }

    public int[] slotsToCards(int[] slots) {
        int[] cards = new int[slots.length];
        for (int i = 0; i < slots.length; i++) {
            cards[i] = slotToCard[slots[i]];
        }
        return cards;
    }

    // Check if there is a token of the player on the slots
    public boolean isLegalSet(int[] slots) {
        synchronized (slotToPlayerToken) {
            for (int slot : slots) {
                if (slotToPlayerToken[slot] == null || slotToPlayerToken[slot].size() == 0) {
                    return false;
                }
            }
            return true;
        }
    }
    
    public void lockTable() {
        isBusy = true;
    }

    public void unlockTable() {
        isBusy = false;
    }

    public boolean isBusy() {
        return isBusy;
    }

}
