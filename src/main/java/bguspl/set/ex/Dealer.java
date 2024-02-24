package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private ThreadLogger[] playerThreads;

        /**
     * The thread representing the current dealer.
     */
    private Thread dealerThread;

    /**
     * True iff the table is busy (i.e. true during a table setup change).
     */
    public volatile boolean isTableBusy;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        isTableBusy = false;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        createPlayerThreads();
        while (!shouldFinish()) {
            placeCardsOnTable();
            updateTimerDisplay(true);
            timerLoop();
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        synchronized(this) {
            notifyAll();
        }
        for (Player player: players) {
            player.terminate();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        isTableBusy = true;
        Random rand = new Random();
        while (table.GetEmptySlots().size() > 0 && deck.size() > 0) {
            int slotIndex = rand.nextInt(table.GetEmptySlots().size());
            int card = rand.nextInt(deck.size());
            table.placeCard(deck.get(card), table.GetEmptySlots().get(slotIndex));
            deck.remove(card);
        }
        isTableBusy = false;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            synchronized(this) {
                if (reshuffleTime - System.currentTimeMillis() > env.config.turnTimeoutWarningMillis) {
                    Thread.sleep(950);
                }
                else {
                    Thread.sleep(9);
                }
            }
        } 
        catch (InterruptedException ignored) {
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        }
        long gap = reshuffleTime - System.currentTimeMillis();
        boolean warn = false;
        if (gap < env.config.turnTimeoutWarningMillis) {
            warn = true;
        }       
        if (gap > 0) {
            env.ui.setCountdown(reshuffleTime-System.currentTimeMillis(), warn);
        } 
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        isTableBusy = true;
        Random randomSlot = new Random();
        while (table.GetEmptySlots().size() > 0) {
            int slot = randomSlot.nextInt(table.getTableSize());
            deck.add(table.getCard(slot));
            table.removeCard(slot);
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxScore = maxScore();
        int numWinners = numOfWinners(maxScore);

        int[] winners = new int[numWinners];

        for (Player player: players) {
            if (player.score() == maxScore)
                winners[--numWinners] = player.id;
            
        }
        env.ui.announceWinner(winners);
    }

    private void createPlayerThreads() {
        playerThreads = new ThreadLogger[players.length];
        for (int i = 0; i < players.length; i++) {
            playerThreads[i] = new ThreadLogger(players[i], "Player's ID: " + players[i].id, env.logger);
            playerThreads[i].startWithLog();
        }
    }

    private int maxScore() {
        int maxScore = 0;
        for (Player player: players) {
            if (player.score() > maxScore) {
                maxScore = player.score();
            }
        }
        return maxScore;
    }
    private int numOfWinners(int maxScore) {
        int numWinners = 0;
        for (Player player: players) {
            if (player.score() == maxScore) {
                numWinners++;
            }
        }
        return numWinners;
    }

}
