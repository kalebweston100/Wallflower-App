package com.production.chat;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;

//synchronized executor for sending messages to prevent concurrent writing to socket channel
public class ThreadExecute implements Executor {

    //basic sub executor
    protected final Executor executor;
    //store queued thread tasks
    protected final Queue<Runnable> tasks;
    //currently running thread
    protected Runnable active;

    //initialize active to null so that start next will be called on execute
    public ThreadExecute() {
        executor = new Executor() {
            @Override
            public void execute(Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.start();
            }
        };
        //capacity is probably unnecessary but best to prevent issues
        tasks = new ArrayBlockingQueue<>(100);
        active = null;
    }

    //synchronized method so that only one thread is run at a time
    @Override
    public synchronized void execute(Runnable command) {
        //creates runnable to store thread to execute
        tasks.offer(new Runnable() {
            @Override
            public void run() {
                try {
                   command.run();
                }
                finally {
                    startNext();
                }
            }
        });
        //if no active thread then start the next one
        if (active == null) {
            startNext();
        }
    }

    //protected because it must be accessed within another thread
    //assign active to the next thread in the queue and use the basic executor to run
    protected synchronized void startNext() {
        if ((active = tasks.poll()) != null) {
            executor.execute(active);
        }
    }
}
