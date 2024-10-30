package com.github.solarix.websocket.rpc;

import com.fasterxml.jackson.annotation.JsonProperty;



public class RpcNotificationResult {


  public static class Result {

    @JsonProperty(value = "value")
    private Object value;

    @JsonProperty(value = "context")
    protected Context context;


    public static class Context {
      @JsonProperty(value = "slot")
      private long slot;

      public long getSlot() {
        return slot;
      }

      public void setSlot(long slot) {
        this.slot = slot;
      }
    }

    public Object getValue() {
      return value;
    }

    public void setValue(Object value) {
      this.value = value;
    }

    public Context getContext() {
      return context;
    }

    public void setContext(Context context) {
      this.context = context;
    }
  }


  public static class Params {

    @JsonProperty(value = "result")
    private Result result;

    @JsonProperty(value = "subscription")
    private long subscription;

    public Result getResult() {
      return result;
    }

    public void setResult(Result result) {
      this.result = result;
    }

    public long getSubscription() {
      return subscription;
    }

    public void setSubscription(long subscription) {
      this.subscription = subscription;
    }
  }

  @JsonProperty(value = "jsonrpc")
  private String jsonrpc;

  @JsonProperty(value = "method")
  private String method;

  @JsonProperty(value = "params")
  private Params params;

  public String getJsonrpc() {
    return jsonrpc;
  }

  public void setJsonrpc(String jsonrpc) {
    this.jsonrpc = jsonrpc;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public Params getParams() {
    return params;
  }

  public void setParams(Params params) {
    this.params = params;
  }

  @Override
  public String toString() {
    return "RpcNotificationResult{" +
           "jsonrpc='" + jsonrpc + '\'' +
           ", method='" + method + '\'' +
           ", params=" + params +
           '}';
  }
}
