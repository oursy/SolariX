package com.github.solarix.websocket.rpc;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RpcResponse<T> {

  public static class Error {
    @JsonProperty(value = "code")
    private long code;

    @JsonProperty(value = "message")
    private String message;

    public long getCode() {
      return code;
    }

    public void setCode(long code) {
      this.code = code;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    @Override
    public String toString() {
      return "Error{" + "code=" + code + ", message='" + message + '\'' + '}';
    }
  }

  @JsonProperty(value = "jsonrpc")
  private String jsonrpc;

  @JsonProperty(value = "result")
  private T result;

  @JsonProperty(value = "id")
  private String id;

  @JsonProperty(value = "method")
  private String method;

  public String getJsonrpc() {
    return jsonrpc;
  }

  public void setJsonrpc(String jsonrpc) {
    this.jsonrpc = jsonrpc;
  }

  public T getResult() {
    return result;
  }

  public void setResult(T result) {
    this.result = result;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  @Override
  public String toString() {
    return "RpcResponse{"
        + "jsonrpc='"
        + jsonrpc
        + '\''
        + ", result="
        + result
        + ", id='"
        + id
        + '\''
        + ", method='"
        + method
        + '\''
        + '}';
  }
}
