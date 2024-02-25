package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;
import bguspl.set.ex.Player.State;

import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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

    private BlockingQueue<CardSet> setsToCheck;

    private Thread dealerThread;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        setsToCheck = new LinkedBlockingQueue<>();
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
            if (!setsToCheck.isEmpty()) 
                executeSetCheck();
            if (table.GetEmptySlots().size() > 0) 
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

    private void executeSetCheck() {
        CardSet set = setsToCheck.poll();
        synchronized (players[set.getPlayerId()]) {        
            if (table.isLegalSet(set.getSlots())) {
                int[] cards = table.slotsToCards(set.getSlots());
                if (env.util.testSet(cards)) {
                    players[set.getPlayerId()].setFreezeState(State.Point);
                    updateTimerDisplay(true);
                    for (int slot: set.getSlots()) {
                        table.removeCard(slot);
                    }
                }
                else {
                    players[set.getPlayerId()].setFreezeState(State.Penalty);
                }
            }
            players[set.getPlayerId()].notifyAll();
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        table.lockTable();
        Random rand = new Random();
        while (table.GetEmptySlots().size() > 0 && deck.size() > 0) {
            int slotIndex = rand.nextInt(table.GetEmptySlots().size());
            int card = rand.nextInt(deck.size());
            table.placeCard(deck.get(card), table.GetEmptySlots().get(slotIndex));
            deck.remove(card);
        }
        table.unlockTable();
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
        table.lockTable();
        Random randomSlot = new Random();
        while (table.GetEmptySlots().size() != table.getTableSize()) {
            int slot = randomSlot.nextInt(table.getTableSize());
            if (table.getCard(slot) != -1) {
                deck.add(table.getCard(slot));
                table.removeCard(slot);
            }
        }
        table.unlockTable();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        table.lockTable();
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

    public synchronized void addSetToCheck(CardSet set) {
        setsToCheck.offer(set);
        dealerThread.interrupt();
    }
}
