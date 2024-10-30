package com.github.solarix.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.solarix.websocket.listener.NotificationEventListener;
import com.github.solarix.websocket.rpc.RpcNotificationResult;
import com.github.solarix.websocket.rpc.RpcRequest;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubscriptionWebSocketMessage {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionWebSocketMessage.class);

  private final WebsocketClient websocketClient;

  private final ObjectMapper objectMapper;

  private static final Map<String, SubscriptionParams> subscriptions = new ConcurrentHashMap<>();

  private static final Map<String, Long> subscriptionIds = new ConcurrentHashMap<>();

  private final Map<Long, NotificationEventListener> subscriptionListeners =
      new ConcurrentHashMap<>();

  private final Map<String, SubscriptionParams> activeSubscriptions = new ConcurrentHashMap<>();

  private final Lock subscriptionLock = new ReentrantLock();

  private final Lock listenerLock = new ReentrantLock();

  public SubscriptionWebSocketMessage(String url) throws URISyntaxException, SSLException {
    this(url, new ObjectMapper());
  }

  public SubscriptionWebSocketMessage(String url, ObjectMapper objectMapper)
      throws URISyntaxException, SSLException {
    this.websocketClient = new WebsocketClient(url, new OnRpcMessageProcess(objectMapper, this));
    this.objectMapper = objectMapper;
  }

  public void connect() throws InterruptedException {
    this.websocketClient._connect();
  }

  public void close() throws Exception {
    this.websocketClient.close();
  }

  public record SubscriptionParams(RpcRequest request, NotificationEventListener listener) {}

  public String addSubscription(RpcRequest request, NotificationEventListener listener) {
    final String requestId = request.getId();
    subscriptionLock.lock();
    try {
      activeSubscriptions.put(requestId, new SubscriptionParams(request, listener));
      subscriptions.put(requestId, new SubscriptionParams(request, listener));
      subscriptionIds.put(requestId, 0L);
    } finally {
      subscriptionLock.unlock();
    }
    _sendMessage(_json(request));
    return requestId;
  }

  public String getSubscriptionId(String rpcRequestId) {
    final Long subscriptionId = subscriptionIds.get(rpcRequestId);
    return String.valueOf(subscriptionId);
  }

  public void unSubscription(String rpcRequestId) {
    final Long subscriptionId = subscriptionIds.get(rpcRequestId);
    if (subscriptionId == null) {
      log.warn("Attempted to unsubscribe from non-existent rpcRequestId: {}", rpcRequestId);
      return;
    }
    final SubscriptionParams subscriptionParams =
        activeSubscriptions.remove(String.valueOf(subscriptionId));
    if (subscriptionParams != null) {
      // Send an unsubscribe request to the server
      List<Object> unsubParams = new ArrayList<>();
      unsubParams.add(subscriptionId);
      RpcRequest unsubRequest =
          new RpcRequest(getUnsubscribeMethod(subscriptionParams.request.getMethod()), unsubParams);
      _sendMessage(_json(unsubRequest));
      // Remove the subscription from subscriptionListeners
      subscriptionListeners.remove(subscriptionId);
      log.info("Unsubscribed from subscription: {}", subscriptionId);
    } else {
      log.warn("Attempted to unsubscribe from non-existent subscription: {}", subscriptionId);
    }
  }

  private String getUnsubscribeMethod(String subscribeMethod) {
    return switch (subscribeMethod) {
      case "accountSubscribe" -> "accountUnsubscribe";
      case "logsSubscribe" -> "logsUnsubscribe";
      case "signatureSubscribe" -> "signatureUnsubscribe";
      case "blockSubscribe" -> "blockUnsubscribe";
      case "programSubscribe" -> "programUnsubscribe";
      case "rootSubscribe" -> "rootUnsubscribe";
      case "slotSubscribe" -> "slotUnsubscribe";
      case "slotsUpdatesSubscribe" -> "slotsUpdatesUnsubscribe";
      case "voteSubscribe" -> "voteUnsubscribe";
      // Add more cases for other subscription types as needed
      default -> throw new IllegalArgumentException("Unknown subscribe method: " + subscribeMethod);
    };
  }

  private String _json(RpcRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private void _sendMessage(String json) {
    log.info("Send Message:{}", json);
    websocketClient.sendTextMessage(json);
  }

  private void retrySubscription() {
    log.info("Resubscribing to all active subscriptions");
    cleanSubscriptions();
    final Map<String, SubscriptionParams> activeSubscriptionsResubscribe = new HashMap<>();
    for (Map.Entry<String, SubscriptionParams> entry : activeSubscriptions.entrySet()) {
      SubscriptionParams paramsOld = entry.getValue();
      final RpcRequest rpcRequest = paramsOld.request;
      final NotificationEventListener notificationEventListener = paramsOld.listener;
      final RpcRequest request = new RpcRequest(rpcRequest.getMethod(), rpcRequest.getParams());
      final String subscriptionId = request.getId();
      final SubscriptionParams params = new SubscriptionParams(request, notificationEventListener);
      subscriptions.put(subscriptionId, params);
      subscriptionIds.put(subscriptionId, 0L);
      activeSubscriptionsResubscribe.put(subscriptionId, params);
    }
    activeSubscriptions.clear();
    activeSubscriptions.putAll(activeSubscriptionsResubscribe);
    updateSubscriptions();
  }

  private void cleanSubscriptions() {
    subscriptions.clear();
    subscriptionIds.clear();
    subscriptionListeners.clear();
  }

  private void updateSubscriptions() {
    for (SubscriptionParams sub : subscriptions.values()) {
      _sendMessage(_json(sub.request));
    }
    for (Map.Entry<String, Long> entry : subscriptionIds.entrySet()) {
      if (entry.getValue() != 0L) {
        SubscriptionParams params = subscriptions.get(entry.getKey());
        if (params != null) {
          _sendMessage(_json(params.request));
        }
      }
    }
  }

  private void handleSubscriptionResponse(String rpcResultId, Long result) {
    if (subscriptionIds.containsKey(rpcResultId)) {
      subscriptionIds.put(rpcResultId, result);
      SubscriptionParams params = subscriptions.get(rpcResultId);
      if (params != null) {
        subscriptionListeners.put(result, params.listener);
        subscriptions.remove(rpcResultId);
        // Update the activeSubscriptions map with the new subscription ID
        activeSubscriptions.put(String.valueOf(result), params);
        activeSubscriptions.remove(rpcResultId);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void handleNotification(String message) throws Exception {
    final RpcNotificationResult rpcNotificationResult =
        objectMapper.readValue(message, RpcNotificationResult.class);
    if (rpcNotificationResult != null) {
      Long subscriptionId = rpcNotificationResult.getParams().getSubscription();
      listenerLock.lock();
      try {
        NotificationEventListener listener = subscriptionListeners.get(subscriptionId);
        if (listener != null) {
          Map<String, Object> value =
              (Map<String, Object>) rpcNotificationResult.getParams().getResult().getValue();
          switch (rpcNotificationResult.getMethod()) {
            case "accountNotification":
            case "logsNotification":
            case "blockNotification":
            case "programNotification":
            case "rootNotification":
            case "slotNotification":
            case "slotsUpdatesNotification":
            case "voteNotification":
              listener.onNotificationEvent(value);
              break;
            default:
              log.warn("Unknown notification method: {}", rpcNotificationResult.getMethod());
          }
        } else {
          log.warn("No listener found for subscription ID: {}", subscriptionId);
        }
      } finally {
        listenerLock.unlock();
      }
    } else {
      log.warn("Received null notification result");
    }
  }

  public static class OnRpcMessageProcess implements ReceivedMessage {

    private final ObjectMapper objectMapper;

    private final SubscriptionWebSocketMessage subscriptionWebSocketMessage;

    public OnRpcMessageProcess(
        ObjectMapper objectMapper, SubscriptionWebSocketMessage subscriptionWebSocketMessage) {
      this.objectMapper = objectMapper;
      this.subscriptionWebSocketMessage = subscriptionWebSocketMessage;
    }

    @Override
    public void onMessage(String message) {
      log.debug("Received RPC message: {}", message);
      try {
        final JsonNode messageNode = objectMapper.readTree(message);
        final boolean result = messageNode.hasNonNull("result");
        final boolean id = messageNode.hasNonNull("id");
        final boolean error = messageNode.hasNonNull("error");
        if (error) {
          throw new IllegalStateException(messageNode.findPath("error").toString());
        }
        if (result && id) {
          subscriptionWebSocketMessage.handleSubscriptionResponse(
              messageNode.findPath("id").asText(), messageNode.findPath("result").asLong());
        } else {
          subscriptionWebSocketMessage.handleNotification(message);
        }
      } catch (Exception e) {
        log.warn("Rpc response error", e);
      }
    }

    @Override
    public void onFinishHandshakeEvent() {
      log.info("onFinishHandshakeEvent");
      subscriptionWebSocketMessage.retrySubscription();
    }
  }
}
