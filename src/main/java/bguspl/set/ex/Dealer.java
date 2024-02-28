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

    private int AlmostSeconds = 940;
    private int AlmostTenMillis = 9;
    
    public enum Num {
        NegONE(-1),
        ZERO(0),
        ONE(1);

        public final int value;
    
        Num(int value) {
            this.value = value;
        }
    }
   

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(Num.ZERO.value, env.config.deckSize).boxed().collect(Collectors.toList());
        setsToCheck = new LinkedBlockingQueue<>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        table.lockTable();
        createPlayerThreads();
        while (!shouldFinish()) {
            placeCardsOnTable();
            updateTimerDisplay(true);
            timerLoop();
            removeAllCardsFromTable();
        }
        announceWinners();
        terminate();
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
            if (table.GetEmptySlots().size() > Num.ZERO.value) 
                placeCardsOnTable();
            }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // treminate each player thread in reverse order, then the dealer thread
        for (int i = players.length - 1; i >= 0; i--) {
                players[i].terminate();
            }  
        terminate = true;
        dealerThread.interrupt();
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, Num.ONE.value).size() == Num.ZERO.value;
    }

    private void executeSetCheck() {
        table.lockTable();
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
            players[set.getPlayerId()].notifyResult();
        }
        table.unlockTable();
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        table.lockTable();
        Random rand = new Random();
        while (table.GetEmptySlots().size() > Num.ZERO.value && deck.size() > Num.ZERO.value) {
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
                    wait(AlmostSeconds);
                }
                else {
                    wait(AlmostTenMillis);
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
        if (gap > Num.ZERO.value) {
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), warn);
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
            if (table.getCard(slot) != Num.NegONE.value) {
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
        for (int i = Num.ZERO.value; i < players.length; i++) {
            playerThreads[i] = new ThreadLogger(players[i], "Player's ID: " + players[i].id, env.logger);
            playerThreads[i].startWithLog();
        }
    }

    private int maxScore() {
        int maxScore = Num.ZERO.value;
        for (Player player: players) {
            if (player.score() > maxScore) {
                maxScore = player.score();
            }
        }
        return maxScore;
    }
    private int numOfWinners(int maxScore) {
        int numWinners = Num.ZERO.value;
        for (Player player: players) {
            if (player.score() == maxScore) {
                numWinners++;
            }
        }
        return numWinners;
    }

    public synchronized void addSetToCheck(CardSet set) {
        setsToCheck.offer(set);
        notifyAll();
    }
}
