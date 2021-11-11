package com.production.chat;

import java.util.concurrent.Executor;

//used in thread execute as a base executor
//basic executor used within thread execute and to execute threads that aren't sent messages
public class SubExecute implements Executor {

    //creates a new thread for the executed runnable
    @Override
    public void execute(Runnable command) {
        Thread thread = new Thread(command);
        thread.start();
    }
}
