package com.github.solarix.websocket;

public interface ReceivedMessage {

  void onMessage(String message);

  default void onFinishHandshakeEvent() {}
}
