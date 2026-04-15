/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.server;

import org.json.JSONObject;
import org.sikuli.mcp.audit.JournalWriter;
import org.sikuli.mcp.gate.ActionGate;
import org.sikuli.mcp.tools.ToolRegistry;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Stdio MCP server: one process, one session, JSON-RPC over line-delimited stdio.
 *
 * <p>Kept as a thin façade over {@link McpDispatcher} for backward compatibility
 * with the {@code run} subcommand and existing tests. For the HTTP transport
 * see {@code org.sikuli.mcp.transport.HttpTransport}.
 */
public final class McpServer {

  public static final String PROTOCOL_VERSION = McpDispatcher.PROTOCOL_VERSION;
  public static final String SERVER_NAME = McpDispatcher.SERVER_NAME;
  public static final String SERVER_VERSION = McpDispatcher.SERVER_VERSION;

  private final McpDispatcher dispatcher;
  private final SessionHandle handle = new SessionHandle();
  private final BufferedReader in;
  private final BufferedWriter out;

  public McpServer(ToolRegistry tools, ActionGate gate, JournalWriter journal,
                   InputStream stdin, OutputStream stdout) {
    this.dispatcher = new McpDispatcher(tools, gate, journal);
    this.in = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8));
    this.out = new BufferedWriter(new OutputStreamWriter(stdout, StandardCharsets.UTF_8));
  }

  /**
   * Run the stdio dispatch loop. Blocks until stdin is closed.
   */
  public void run() throws IOException {
    String line;
    while ((line = in.readLine()) != null) {
      if (line.isBlank()) continue;
      JSONObject response;
      try {
        JSONObject req = new JSONObject(line);
        response = dispatcher.dispatch(req, handle);
      } catch (McpDispatcher.AuditFailure audit) {
        System.err.println("[oculix-mcp] FATAL: " + audit.getMessage()
            + ": " + audit.getCause());
        System.exit(2);
        return;
      } catch (Exception e) {
        response = JsonRpc.error(JSONObject.NULL, JsonRpc.PARSE_ERROR,
            "Failed to parse request: " + e.getMessage());
      }
      if (response != null) {
        out.write(response.toString());
        out.newLine();
        out.flush();
      }
    }
  }
}
