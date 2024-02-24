package bguspl.set.ex;

import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import bguspl.set.Env;

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
     * The thread representing the current player.
     */
    private Thread playerThread;

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
        this.human = human;
        this.actions = new ArrayBlockingQueue<Integer>(env.config.featureSize);
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {    
            if (human) {
                while (actions.isEmpty()) {
                    try {
                        synchronized (this) { wait(); }
                    } catch (InterruptedException ignored) {}
                }
            }
    
            while (!actions.isEmpty()) {
                executeAction();
            }

            if (!human) {
             synchronized (aiThread) {
                 aiThread.notifyAll();
                }
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
        Random random = new Random();
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {

                try {
                    Thread.sleep(500);
                    synchronized (aiThread) { 
                        aiThread.wait();
                        actions.offer(random.nextInt(table.getTableSize()));
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
        synchronized (this) 
        { 
            notifyAll(); 
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (human) {
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
    private void executeAction() {
        synchronized (actions) {
            int slot = actions.poll();
            table.placeOrRemoveToken(id, slot);
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
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
        }
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        long freezeTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
        while (System.currentTimeMillis() < freezeTime) {
            env.ui.setFreeze(id, freezeTime - System.currentTimeMillis());
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
        }
    }

    public int score() {
        return score;
    }
}
