/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.mcp.audit.JournalWriter;
import org.sikuli.mcp.crypto.KeyManager;
import org.sikuli.mcp.gate.AutoApproveGate;
import org.sikuli.mcp.server.McpDispatcher;
import org.sikuli.mcp.server.SessionStore;
import org.sikuli.mcp.tools.Tool;
import org.sikuli.mcp.tools.ToolRegistry;
import org.sikuli.mcp.transport.BearerAuth;
import org.sikuli.mcp.transport.HttpTransport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: bind Undertow on a random loopback port, exchange
 * MCP-shaped JSON-RPC requests, verify wire-level session + auth behavior.
 */
class HttpTransportTest {

  private static final class EchoTool implements Tool {
    @Override public String name() { return "echo"; }
    @Override public String description() { return "echo"; }
    @Override public JSONObject inputSchema() {
      return new JSONObject().put("type", "object");
    }
    @Override public JSONObject call(JSONObject args) {
      return Tool.textResult(args.toString());
    }
  }

  private static final class Server implements AutoCloseable {
    final HttpTransport http;
    final JournalWriter journal;
    final String url;
    final String token = "integration-test-token";

    Server(Path dir) throws Exception {
      KeyManager keys = KeyManager.loadOrInit(dir);
      this.journal = new JournalWriter(dir.resolve("journal"), keys, 100, 60_000);
      ToolRegistry r = new ToolRegistry();
      r.register(new EchoTool());
      McpDispatcher d = new McpDispatcher(r, new AutoApproveGate(), journal);
      this.http = new HttpTransport(d, new SessionStore(),
          new BearerAuth(token), "127.0.0.1", 0);
      http.start();
      this.url = "http://127.0.0.1:" + http.boundPort() + HttpTransport.PATH;
    }

    @Override
    public void close() throws Exception {
      http.close();
      journal.close();
    }
  }

  private static HttpRequest.Builder req(Server s) {
    return HttpRequest.newBuilder()
        .uri(URI.create(s.url))
        .timeout(Duration.ofSeconds(5))
        .header("Authorization", "Bearer " + s.token)
        .header("Content-Type", "application/json");
  }

  private static HttpClient client() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  @Test
  void postWithoutBearerReturns401(@TempDir Path dir) throws Exception {
    try (Server s = new Server(dir)) {
      HttpResponse<String> resp = client().send(
          HttpRequest.newBuilder().uri(URI.create(s.url))
              .timeout(Duration.ofSeconds(5))
              .POST(HttpRequest.BodyPublishers.ofString("{}"))
              .build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(401, resp.statusCode());
      assertTrue(resp.headers().firstValue("WWW-Authenticate").isPresent());
    }
  }

  @Test
  void postWithWrongTokenReturns401(@TempDir Path dir) throws Exception {
    try (Server s = new Server(dir)) {
      HttpResponse<String> resp = client().send(
          HttpRequest.newBuilder().uri(URI.create(s.url))
              .timeout(Duration.ofSeconds(5))
              .header("Authorization", "Bearer wrong")
              .POST(HttpRequest.BodyPublishers.ofString("{}"))
              .build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(401, resp.statusCode());
    }
  }

  @Test
  void initializeReturnsSessionHeader(@TempDir Path dir) throws Exception {
    try (Server s = new Server(dir)) {
      JSONObject init = new JSONObject()
          .put("jsonrpc", "2.0").put("id", 1).put("method", "initialize")
          .put("params", new JSONObject()
              .put("clientInfo", new JSONObject().put("name", "t")));

      HttpResponse<String> resp = client().send(
          req(s).POST(HttpRequest.BodyPublishers.ofString(init.toString())).build(),
          HttpResponse.BodyHandlers.ofString());

      assertEquals(200, resp.statusCode());
      assertTrue(resp.headers().firstValue(HttpTransport.SESSION_HEADER).isPresent(),
          "initialize must emit " + HttpTransport.SESSION_HEADER);
      JSONObject body = new JSONObject(resp.body());
      assertEquals(McpDispatcher.PROTOCOL_VERSION,
          body.getJSONObject("result").getString("protocolVersion"));
    }
  }

  @Test
  void nonInitializeWithoutSessionIs400(@TempDir Path dir) throws Exception {
    try (Server s = new Server(dir)) {
      JSONObject ping = new JSONObject()
          .put("jsonrpc", "2.0").put("id", 1).put("method", "ping");
      HttpResponse<String> resp = client().send(
          req(s).POST(HttpRequest.BodyPublishers.ofString(ping.toString())).build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(400, resp.statusCode());
    }
  }

  @Test
  void nonInitializeWithUnknownSessionIs404(@TempDir Path dir) throws Exception {
    try (Server s = new Server(dir)) {
      JSONObject ping = new JSONObject()
          .put("jsonrpc", "2.0").put("id", 1).put("method", "ping");
      HttpResponse<String> resp = client().send(
          req(s).header(HttpTransport.SESSION_HEADER, "bogus-session-id")
              .POST(HttpRequest.BodyPublishers.ofString(ping.toString())).build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(404, resp.statusCode());
    }
  }

  @Test
  void fullHandshakeListAndCall(@TempDir Path dir) throws Exception {
    try (Server s = new Server(dir)) {
      HttpClient c = client();

      // 1. initialize
      JSONObject init = new JSONObject()
          .put("jsonrpc", "2.0").put("id", 1).put("method", "initialize")
          .put("params", new JSONObject()
              .put("clientInfo", new JSONObject().put("name", "inspector")));
      HttpResponse<String> initResp = c.send(
          req(s).POST(HttpRequest.BodyPublishers.ofString(init.toString())).build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, initResp.statusCode());
      String session = initResp.headers().firstValue(HttpTransport.SESSION_HEADER)
          .orElseThrow();

      // 2. tools/list
      JSONObject list = new JSONObject()
          .put("jsonrpc", "2.0").put("id", 2).put("method", "tools/list");
      HttpResponse<String> listResp = c.send(
          req(s).header(HttpTransport.SESSION_HEADER, session)
              .POST(HttpRequest.BodyPublishers.ofString(list.toString())).build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, listResp.statusCode());
      assertEquals(1, new JSONObject(listResp.body())
          .getJSONObject("result").getJSONArray("tools").length());

      // 3. tools/call
      JSONObject call = new JSONObject()
          .put("jsonrpc", "2.0").put("id", 3).put("method", "tools/call")
          .put("params", new JSONObject()
              .put("name", "echo")
              .put("arguments", new JSONObject().put("payload", "hello")));
      HttpResponse<String> callResp = c.send(
          req(s).header(HttpTransport.SESSION_HEADER, session)
              .POST(HttpRequest.BodyPublishers.ofString(call.toString())).build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, callResp.statusCode());
      JSONObject result = new JSONObject(callResp.body()).getJSONObject("result");
      assertFalse(result.getBoolean("isError"));
    }
  }

  @Test
  void deleteReleasesSession(@TempDir Path dir) throws Exception {
    try (Server s = new Server(dir)) {
      HttpClient c = client();

      JSONObject init = new JSONObject()
          .put("jsonrpc", "2.0").put("id", 1).put("method", "initialize")
          .put("params", new JSONObject());
      HttpResponse<String> initResp = c.send(
          req(s).POST(HttpRequest.BodyPublishers.ofString(init.toString())).build(),
          HttpResponse.BodyHandlers.ofString());
      String session = initResp.headers().firstValue(HttpTransport.SESSION_HEADER)
          .orElseThrow();

      HttpResponse<String> delResp = c.send(
          req(s).header(HttpTransport.SESSION_HEADER, session)
              .DELETE().build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(204, delResp.statusCode());

      // Reusing the same session id after delete must fail.
      JSONObject ping = new JSONObject()
          .put("jsonrpc", "2.0").put("id", 2).put("method", "ping");
      HttpResponse<String> pingResp = c.send(
          req(s).header(HttpTransport.SESSION_HEADER, session)
              .POST(HttpRequest.BodyPublishers.ofString(ping.toString())).build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(404, pingResp.statusCode());
    }
  }
}
