package com.production.chat;

//object to provide universal access to message data
//future releases may add timestamp etc
public class Message {

    private final String text;

    public Message(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}

