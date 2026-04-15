/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.transport;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import org.json.JSONObject;
import org.sikuli.mcp.server.JsonRpc;
import org.sikuli.mcp.server.McpDispatcher;
import org.sikuli.mcp.server.SessionHandle;
import org.sikuli.mcp.server.SessionStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Streamable HTTP transport for MCP over Undertow.
 *
 * <p>Implements the subset of the 2024-11-05 Streamable HTTP spec that
 * the MCP Inspector and the official MCP TypeScript SDK require:
 * <ul>
 *   <li>{@code POST /mcp} — one JSON-RPC message in, one JSON response out.
 *       Notifications return {@code 202 Accepted} with an empty body.</li>
 *   <li>{@code GET /mcp} — minimal server-to-client SSE stream. The server
 *       does not currently push notifications, so the stream stays open
 *       until the client disconnects; a comment line is emitted on open
 *       so that the Inspector's stream check succeeds.</li>
 *   <li>{@code DELETE /mcp} — terminate a session.</li>
 * </ul>
 *
 * <p>Session tracking uses the {@code Mcp-Session-Id} header:
 * <ul>
 *   <li>On {@code initialize}, the server mints a session id and returns
 *       it in the response header.</li>
 *   <li>All subsequent requests must echo that id. Unknown ids yield
 *       {@code 404 Not Found}.</li>
 * </ul>
 *
 * <p>Every request except the initial handshake requires an
 * {@code Authorization: Bearer <token>} header matching the configured
 * {@link BearerAuth}. Missing / wrong tokens yield {@code 401}.
 */
public final class HttpTransport implements AutoCloseable {

  public static final String PATH = "/mcp";
  public static final String SESSION_HEADER = "Mcp-Session-Id";
  private static final HttpString SESSION_HTTP_STR = new HttpString(SESSION_HEADER);

  private final McpDispatcher dispatcher;
  private final SessionStore sessions;
  private final BearerAuth auth;
  private final String host;
  private final int port;

  private Undertow server;

  public HttpTransport(McpDispatcher dispatcher, SessionStore sessions,
                       BearerAuth auth, String host, int port) {
    this.dispatcher = dispatcher;
    this.sessions = sessions;
    this.auth = auth;
    this.host = host;
    this.port = port;
  }

  public synchronized void start() {
    if (server != null) return;
    HttpHandler handler = this::handle;
    server = Undertow.builder()
        .addHttpListener(port, host)
        .setHandler(handler)
        .build();
    server.start();
  }

  /** Actual bound port (useful when {@code port == 0}). */
  public synchronized int boundPort() {
    if (server == null) return -1;
    return ((java.net.InetSocketAddress)
        server.getListenerInfo().get(0).getAddress()).getPort();
  }

  @Override
  public synchronized void close() {
    if (server != null) {
      server.stop();
      server = null;
    }
  }

  // ── Handler ──

  private void handle(HttpServerExchange ex) {
    try {
      handleInternal(ex);
    } catch (Throwable t) {
      // Undertow swallows exceptions thrown from handlers, returning a blank
      // 500 that offers no diagnostic. Capturing here turns server-side bugs
      // into loud stderr while keeping the client contract stable.
      System.err.println("[oculix-mcp] handler error: " + t);
      t.printStackTrace(System.err);
      if (!ex.isResponseStarted()) {
        ex.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
        ex.getResponseSender().send("{\"error\":\"internal\"}");
      }
    }
  }

  private void handleInternal(HttpServerExchange ex) throws Exception {
    if (!PATH.equals(ex.getRequestPath())) {
      ex.setStatusCode(StatusCodes.NOT_FOUND);
      return;
    }

    // Undertow dispatches requests on a non-blocking IO thread. Reading the
    // request body or running tool calls (which may click/type synchronously)
    // would deadlock there, so we pivot to a worker thread before any
    // blocking work.
    if (ex.isInIoThread()) {
      ex.dispatch(this::handle);
      return;
    }

    HeaderMap reqHeaders = ex.getRequestHeaders();
    String authHeader = firstHeader(reqHeaders, "Authorization");
    if (!auth.accepts(authHeader)) {
      ex.setStatusCode(StatusCodes.UNAUTHORIZED);
      ex.getResponseHeaders().put(new HttpString("WWW-Authenticate"),
          "Bearer realm=\"oculix-mcp\"");
      ex.getResponseSender().send(
          "{\"error\":\"missing or invalid bearer token\"}");
      return;
    }

    HttpString method = ex.getRequestMethod();
    if (Methods.POST.equals(method)) {
      handlePost(ex);
    } else if (Methods.GET.equals(method)) {
      handleGet(ex);
    } else if (Methods.DELETE.equals(method)) {
      handleDelete(ex);
    } else {
      ex.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
      ex.getResponseHeaders().put(new HttpString("Allow"), "POST, GET, DELETE");
    }
  }

  private void handlePost(HttpServerExchange ex) throws IOException {
    ex.startBlocking();
    String body = readAll(ex.getInputStream());

    JSONObject req;
    try {
      req = new JSONObject(body);
    } catch (Exception e) {
      writeJson(ex, StatusCodes.BAD_REQUEST,
          JsonRpc.error(JSONObject.NULL, JsonRpc.PARSE_ERROR,
              "Malformed JSON: " + e.getMessage()));
      return;
    }

    String method = req.optString("method", "");
    String inboundSession = firstHeader(ex.getRequestHeaders(), SESSION_HEADER);

    SessionHandle handle;
    String sessionId;
    if ("initialize".equals(method)) {
      // Initialize may or may not carry a session id. Either way we mint
      // a new one so the client and server agree from here on.
      sessionId = UUID.randomUUID().toString();
      handle = new SessionHandle();
      sessions.put(sessionId, handle);
    } else {
      if (inboundSession == null) {
        writeJson(ex, StatusCodes.BAD_REQUEST,
            JsonRpc.error(req.opt("id"), JsonRpc.INVALID_REQUEST,
                "Missing " + SESSION_HEADER + " header; call initialize first"));
        return;
      }
      handle = sessions.get(inboundSession);
      if (handle == null) {
        ex.setStatusCode(StatusCodes.NOT_FOUND);
        writeJson(ex, StatusCodes.NOT_FOUND,
            JsonRpc.error(req.opt("id"), JsonRpc.INVALID_REQUEST,
                "Unknown session id — initialize first"));
        return;
      }
      sessionId = inboundSession;
    }

    JSONObject resp;
    try {
      resp = dispatcher.dispatch(req, handle);
    } catch (McpDispatcher.AuditFailure audit) {
      // Audit write failures are fatal — tear the whole server down.
      System.err.println("[oculix-mcp] FATAL: " + audit.getMessage()
          + ": " + audit.getCause());
      ex.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
      ex.getResponseSender().send("{\"error\":\"audit write failed\"}");
      // Async shutdown to let this response flush.
      new Thread(() -> { try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                         System.exit(2); }, "oculix-mcp-audit-shutdown").start();
      return;
    }

    ex.getResponseHeaders().put(SESSION_HTTP_STR, sessionId);

    if (resp == null) {
      // Notification — no body.
      ex.setStatusCode(StatusCodes.ACCEPTED);
    } else {
      writeJson(ex, StatusCodes.OK, resp);
    }
  }

  private void handleGet(HttpServerExchange ex) {
    // Spec allows a server-initiated SSE stream. We don't currently push
    // notifications, but we keep the endpoint open so the Inspector's
    // "stream ready" check passes.
    String sessionId = firstHeader(ex.getRequestHeaders(), SESSION_HEADER);
    if (sessionId == null || sessions.get(sessionId) == null) {
      ex.setStatusCode(StatusCodes.NOT_FOUND);
      return;
    }
    ex.getResponseHeaders().put(new HttpString("Content-Type"), "text/event-stream");
    ex.getResponseHeaders().put(new HttpString("Cache-Control"), "no-cache");
    ex.getResponseHeaders().put(new HttpString("Connection"), "keep-alive");
    ex.setPersistent(false);
    // Emit a comment so the client sees the stream as "open" immediately.
    ex.getResponseSender().send(": oculix-mcp ready\n\n");
    // Leaving the exchange open — Undertow will close it when the client
    // disconnects. No further pushes until we implement server notifications.
  }

  private void handleDelete(HttpServerExchange ex) {
    String sessionId = firstHeader(ex.getRequestHeaders(), SESSION_HEADER);
    if (sessionId == null) {
      ex.setStatusCode(StatusCodes.BAD_REQUEST);
      return;
    }
    SessionHandle removed = sessions.remove(sessionId);
    ex.setStatusCode(removed == null ? StatusCodes.NOT_FOUND : StatusCodes.NO_CONTENT);
  }

  // ── Helpers ──

  private static void writeJson(HttpServerExchange ex, int status, JSONObject body) {
    ex.setStatusCode(status);
    ex.getResponseHeaders().put(new HttpString("Content-Type"), "application/json");
    ex.getResponseSender().send(body.toString(), StandardCharsets.UTF_8);
  }

  private static String firstHeader(HeaderMap headers, String name) {
    var values = headers.get(name);
    return values == null || values.isEmpty() ? null : values.getFirst();
  }

  private static String readAll(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[4096];
    int n;
    while ((n = in.read(buf)) >= 0) {
      out.write(buf, 0, n);
    }
    return out.toString(StandardCharsets.UTF_8);
  }
}
