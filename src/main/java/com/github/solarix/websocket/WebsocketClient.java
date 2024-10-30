package com.github.solarix.websocket;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.IdleStateHandler;
import java.net.URI;
import java.net.URISyntaxException;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebsocketClient implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(WebsocketClient.class);

  private final URI uri;

  private final ReceivedMessage receivedMessage;

  private static Channel channel = null;

  private static final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();

  private static final Bootstrap bs = new Bootstrap();

  public static final int RECONNECT_DELAY = 5;

  private int readerIdleTimeSeconds = 30;

  private int writerIdleTimeSeconds = 10;

  public int getReaderIdleTimeSeconds() {
    return readerIdleTimeSeconds;
  }

  public void setReaderIdleTimeSeconds(int readerIdleTimeSeconds) {
    this.readerIdleTimeSeconds = readerIdleTimeSeconds;
  }

  public int getWriterIdleTimeSeconds() {
    return writerIdleTimeSeconds;
  }

  public void setWriterIdleTimeSeconds(int writerIdleTimeSeconds) {
    this.writerIdleTimeSeconds = writerIdleTimeSeconds;
  }

  public static EventLoopGroup getEventLoopGroup() {
    return eventLoopGroup;
  }

  public static Channel getChannel() {
    return channel;
  }

  public static void setChannel(Channel channel) {
    WebsocketClient.channel = channel;
  }

  private static final ReceivedMessage LOG_RECEIVED_MESSAGE =
      message -> log.info("Receive message:{}", message);

  public WebsocketClient(String url) throws URISyntaxException, SSLException {
    this(url, LOG_RECEIVED_MESSAGE);
  }

  public WebsocketClient(String url, ReceivedMessage receivedMessage)
      throws URISyntaxException, SSLException {
    this.uri = new URI(url);
    this.receivedMessage = receivedMessage;
    bsGroupCreate();
  }

  private void bsGroupCreate() throws SSLException {
    String scheme = uri.getScheme() == null ? "ws" : uri.getScheme();
    final String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
    final int port;
    if (uri.getPort() == -1) {
      if ("ws".equalsIgnoreCase(scheme)) {
        port = 80;
      } else if ("wss".equalsIgnoreCase(scheme)) {
        port = 443;
      } else {
        port = -1;
      }
    } else {
      port = uri.getPort();
    }

    if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
      log.error("Only WS(S) is supported.");
      throw new IllegalStateException("Only WS(S) is supported.");
    }

    final boolean ssl = "wss".equalsIgnoreCase(scheme);

    final SslContext sslCtx;
    if (ssl) {
      sslCtx =
          SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
    } else {
      sslCtx = null;
    }

    final WebSocketClientHandler webSocketClientHandler =
        new WebSocketClientHandler(uri, receivedMessage);
    bs.group(eventLoopGroup)
        .channel(NioSocketChannel.class)
        .remoteAddress(host, port)
        .handler(
            new ChannelInitializer<>() {
              @Override
              protected void initChannel(Channel ch) {
                ChannelPipeline p = ch.pipeline();
                if (sslCtx != null) {
                  p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                }
                p.addLast(
                    new IdleStateHandler(
                        getReaderIdleTimeSeconds(), getWriterIdleTimeSeconds(), 0));
                p.addLast(
                    new HttpClientCodec(512, 512, 512),
                    new HttpObjectAggregator(65536),
                    WebSocketClientCompressionHandler.INSTANCE,
                    webSocketClientHandler);
              }
            });
  }

  @Override
  public void close() {
    log.info("close webSocket client");
    channel.close().syncUninterruptibly();
    eventLoopGroup.shutdownGracefully();
    log.info("shutdownGracefully webSocket");
  }

  public void sendTextMessage(String message) {
    sendMessage(new TextWebSocketFrame(message));
  }

  public static void connect() {
    log.info("connect websocket client");
    bs.connect()
        .addListener(
            (ChannelFutureListener)
                future -> {
                  if (future.cause() != null) {
                    log.error("Failed to connect: {}", String.valueOf(future.cause()));
                  } else {
                    final Channel channelConnect = future.channel();
                    final ChannelPipeline pipeline = channelConnect.pipeline();
                    final WebSocketClientHandler webSocketClientHandler =
                        pipeline.get(WebSocketClientHandler.class);
                    handshaker(webSocketClientHandler);
                    channel = channelConnect;
                    log.info("connected successfully");
                  }
                });
  }

  private static void handshaker(WebSocketClientHandler webSocketClientHandler) {
    if (!webSocketClientHandler.getHandshaker().isHandshakeComplete()) {
      log.info("Connect Handshaker ....");
      webSocketClientHandler
          .handshakeFuture()
          .addListener(
              (ChannelFutureListener)
                  future -> {
                    if (future.cause() != null) {
                      log.error("websocket handshaker error", future.cause());
                    } else {
                      log.info("Handshaker success");
                    }
                  });
    }
  }

  private static void handshakerSyn(WebSocketClientHandler webSocketClientHandler)
      throws InterruptedException {
    if (!webSocketClientHandler.getHandshaker().isHandshakeComplete()) {
      log.info("Connect Handshaker ....");
      webSocketClientHandler.handshakeFuture().sync();
    }
  }

  public static void connectBlocking() throws InterruptedException {
    log.info("connectBlocking websocket client");
    channel = bs.connect().sync().channel();
    final ChannelPipeline pipeline = channel.pipeline();
    final WebSocketClientHandler webSocketClientHandler =
        pipeline.get(WebSocketClientHandler.class);
    handshakerSyn(webSocketClientHandler);
  }

  public void sendMessage(WebSocketFrame webSocketFrame) {
    if (channel == null || !channel.isActive()) {
      log.warn("channel not active");
      return;
    }
    channel.writeAndFlush(webSocketFrame);
  }

  public void handshake() throws InterruptedException {
    connectBlocking();
  }

  public void _connect() {
    connect();
  }

  public static void main(String[] args) throws URISyntaxException, SSLException {
    try (WebsocketClient websocketClient =
        new WebsocketClient("wss://api.mainnet-beta.solana.com")) {
      log.info("Send Ping");
      websocketClient.sendMessage(new PingWebSocketFrame());
    }
  }
}
