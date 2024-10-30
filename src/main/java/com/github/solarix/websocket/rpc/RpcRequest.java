package com.github.solarix.websocket.rpc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public class RpcRequest {

  @JsonProperty(value = "jsonrpc")
  private String jsonRpc = "2.0";

  @JsonProperty(value = "method")
  private String method;

  @JsonProperty(value = "params")
  private List<Object> params;

  @JsonProperty(value = "id")
  private String id = UUID.randomUUID().toString();

  public RpcRequest(String method) {
    this(method, null);
  }

  public RpcRequest(String method, List<Object> params) {
    this.method = method;
    this.params = params;
  }

  public String getJsonRpc() {
    return jsonRpc;
  }

  public void setJsonRpc(String jsonRpc) {
    this.jsonRpc = jsonRpc;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public List<Object> getParams() {
    return params;
  }

  public void setParams(List<Object> params) {
    this.params = params;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
