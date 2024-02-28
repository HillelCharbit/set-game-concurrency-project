package bguspl.set.ex;

import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import bguspl.set.Env;
import bguspl.set.ex.Dealer.Num;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The dealer object.
     */
    private final Dealer dealer;

    /**
     * The thread representing the current player.
     */
    public Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;
    
    /**
     * 
     */
    private volatile BlockingQueue<Integer> actions;

    private int OneHundredMillis = 100;
    public enum State {
        Free,
        Point,
        Penalty
    }

    State freezeState;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.dealer = dealer;
        this.human = human;
        freezeState = State.Free;
        this.actions = new ArrayBlockingQueue<Integer>(env.config.featureSize);
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();
        playerThread = Thread.currentThread();
        while (!terminate) {
            if (human) {
                waitForAction();
            }
            while (shouldExecuteAction()) {
                boolean succeded = executeAction();
                if (succeded && table.playerHasMaxTokens(id)) {
                    deliverSetToDealer();
                    waitForDealerResult();
                    acceptDealerResult();
                }
            }
            if (!human) {
                notifyAiThreads();
            }
        }
        
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                Random random = new Random();
                try {
                    synchronized (aiThread) { 
                        aiThread.wait();
                        if (shouldAllowOffer()) {
                            actions.offer(random.nextInt(table.getTableSize()));
                        }

                    }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        playerThread.interrupt();
        try {
            playerThread.join();
        } catch (InterruptedException ignored) {}     
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (human) {
            if (shouldAllowOffer() == false) {
                env.logger.warning("error: trying to press a key while blocked");
                return;
            }
            try {
                synchronized (actions) {
                    actions.put(slot);
                }
                synchronized (this) 
                { 
                    notifyAll(); 
                }
            } catch (InterruptedException ignored) {}
        }
        else {
            env.logger.warning("error: trying to press a key on a computer player");
        }
    }

    /**
        * Execute the next action in the queue.
        */
    private boolean executeAction() {
        synchronized (actions) {
            int slot = actions.poll();
            return table.placeOrRemoveToken(id, slot);
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        env.ui.setScore(id, ++score);
        long freezeTime = System.currentTimeMillis() + env.config.pointFreezeMillis;
        while (System.currentTimeMillis() < freezeTime) {
            env.ui.setFreeze(id, freezeTime - System.currentTimeMillis());
            try {
                Thread.sleep(OneHundredMillis);
            } catch (InterruptedException ignored) {}
        }
        env.ui.setFreeze(id, Num.ZERO.value);
        freezeState = State.Free;
        // int ignored = table.countCards(); // this part is just for demonstration in the unit tests
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        long freezeTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
        while (System.currentTimeMillis() < freezeTime) {
            env.ui.setFreeze(id, freezeTime - System.currentTimeMillis());
            try {
                Thread.sleep(OneHundredMillis);
            } catch (InterruptedException ignored) {}
        }
        freezeState = State.Free;
        env.ui.setFreeze(id, Num.ZERO.value);
    }

    public int score() {
        return score;
    }
    
    public void setFreezeState(State state) {
        freezeState = state;
    }

    public boolean shouldExecuteAction() {
        return !actions.isEmpty() && table.isBusy() == false;
    }

    public boolean shouldAllowOffer() {
        return freezeState == State.Free 
                && table.isBusy() == false;
    }

    private void waitForAction() {
        while (actions.isEmpty()) {
            try {
                synchronized (this) { wait(); }
            } catch (InterruptedException ignored) {}
        }
    }
        
    private void acceptDealerResult() {
        if (freezeState == State.Point) {
            point();
        }
        else if (freezeState == State.Penalty) {
            penalty();
        }
        synchronized (this) { notifyAll(); }
    }

    private void notifyAiThreads() {
        synchronized (aiThread) {
            aiThread.notifyAll();
        }
    }
    private void waitForDealerResult() {
        if (freezeState == State.Free) {
            try {
                synchronized (this) { wait(); }
            }
            catch (InterruptedException ignored) {}
        }
    }
    private void deliverSetToDealer() {
        env.logger.info("player " + id + " has placed all tokens");
        int[] slots = table.getPlayerSlots(id);
        CardSet set = new CardSet(slots, id);
        dealer.addSetToCheck(set);
    }

    public void notifyResult() {
        synchronized (this) { notifyAll(); }
    }
        
}
