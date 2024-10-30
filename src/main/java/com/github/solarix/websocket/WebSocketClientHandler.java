package com.github.solarix.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

  private static final Logger log = LoggerFactory.getLogger(WebSocketClientHandler.class);

  private WebSocketClientHandshaker handshaker;

  private final ReceivedMessage receivedMessage;

  private final URI uri;

  private ChannelPromise handshakeFuture;

  private ByteBuf buffer;

  private WebSocketClientHandshaker clientHandshaker(URI uri) {
    return WebSocketClientHandshakerFactory.newHandshaker(
        uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());
  }

  public WebSocketClientHandler(URI uri, ReceivedMessage receivedMessage) {
    this.uri = uri;
    this.handshaker = clientHandshaker(uri);
    this.receivedMessage = receivedMessage;
  }

  public WebSocketClientHandshaker getHandshaker() {
    return handshaker;
  }

  public ChannelFuture handshakeFuture() {
    return handshakeFuture;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
    log.info("handlerAdded ctx:{}", ctx.channel());
    handshakeFuture = ctx.newPromise();
    buffer = ctx.alloc().buffer();
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    log.info("Channel Active :{}", ctx.channel());
    handshaker.handshake(ctx.channel());
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    log.info("WebSocket Client disconnected! ctx:{}", ctx.channel());
    this.handshaker = null;
  }

  @Override
  public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
    log.warn("Sleeping for: " + WebsocketClient.RECONNECT_DELAY + 's');
    this.handshaker = clientHandshaker(this.uri);
    ctx.channel()
        .eventLoop()
        .schedule(
            () -> {
              log.info("Reconnecting ");
              WebsocketClient.connect();
            },
            WebsocketClient.RECONNECT_DELAY,
            TimeUnit.SECONDS);
  }

  @Override
  public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    Channel ch = ctx.channel();
    if (!handshaker.isHandshakeComplete()) {
      try {
        handshaker.finishHandshake(ch, (FullHttpResponse) msg);
        receivedMessage.onFinishHandshakeEvent();
        log.info("WebSocket Client connected!");
        handshakeFuture.setSuccess();
      } catch (WebSocketHandshakeException e) {
        log.info("WebSocket Client failed to connect");
        handshakeFuture.setFailure(e);
      }
      return;
    }

    if (msg instanceof FullHttpResponse response) {
      throw new IllegalStateException(
          "Unexpected FullHttpResponse (getStatus="
              + response.status()
              + ", content="
              + response.content().toString(CharsetUtil.UTF_8)
              + ')');
    }
    WebSocketFrame frame = (WebSocketFrame) msg;
    if (frame instanceof TextWebSocketFrame textFrame) {
      final String text = textFrame.text();
      if (textFrame.isFinalFragment()) {
        receivedMessage.onMessage(text);
      } else {
        // 开始处理分片消息，将片段内容写入 ByteBuf
        buffer.writeBytes(textFrame.content());
      }
    } else if (frame instanceof ContinuationWebSocketFrame continuationFrame) {
      buffer.writeBytes(continuationFrame.content());
      if (continuationFrame.isFinalFragment()) {
        String fullMessage = buffer.toString(CharsetUtil.UTF_8);
        buffer.clear();
        receivedMessage.onMessage(fullMessage);
      }
    } else if (frame instanceof PongWebSocketFrame) {
      log.trace("WebSocket Client received pong");
    } else if (frame instanceof CloseWebSocketFrame) {
      log.info("WebSocket Client received closing");
      ch.close();
    }
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    log.info("userEventTriggered ctx:{},evt:{}", ctx.channel(), evt);
    if (evt instanceof IdleStateEvent idleStateEvent) {
      final IdleState idleState = idleStateEvent.state();
      if (Objects.requireNonNull(idleState) == IdleState.READER_IDLE) {
        log.warn("Read idle timeout, close channel:{}", ctx.channel());
        ctx.close();
      } else if (idleState == IdleState.WRITER_IDLE) {
        ctx.writeAndFlush(new PingWebSocketFrame());
      }
    }
    super.userEventTriggered(ctx, evt);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    cause.printStackTrace();
    if (!handshakeFuture.isDone()) {
      handshakeFuture.setFailure(cause);
    }
    ctx.close();
  }
}
