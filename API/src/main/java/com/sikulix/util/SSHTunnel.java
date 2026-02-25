/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 * SSH Tunnel for VNC connections via JSch
 */
package com.sikulix.util;

import com.jcraft.jsch.*;
import com.sikulix.util.SikuliLogger;

import java.io.Closeable;

/**
 * Integrated SSH tunnel for VNC connections through a bastion/remote server.
 * Eliminates the dependency on WSL/sshpass.
 *
 * Uses JSch (pure Java SSH) for maximum portability.
 *
 * Usage:
 * <pre>
 *   try (SSHTunnel tunnel = SSHTunnel.open("192.168.1.100", "user", "password")) {
 *       VNCScreen vnc = VNCScreen.start("127.0.0.1", tunnel.getLocalPort(), 10, 1000);
 *       // ... use VNC
 *       vnc.stop();
 *   }
 * </pre>
 */
public class SSHTunnel implements Closeable {

    private static final int DEFAULT_SSH_PORT = 22;
    private static final int DEFAULT_VNC_PORT = 5900;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;
    private static final int DEFAULT_SERVER_ALIVE_INTERVAL = 30;
    private static final int DEFAULT_SERVER_ALIVE_COUNT_MAX = 3;

    private Session session;
    private int localPort;

    private SSHTunnel() {
    }

    /**
     * Create and start an SSH tunnel with local port forwarding.
     *
     * @param sshHost      SSH server address (e.g., "192.168.1.100")
     * @param sshPort      SSH port (typically 22)
     * @param sshUser      SSH username (e.g., "root")
     * @param sshPassword  SSH password
     * @param remoteHost   remote host for forwarding (typically "localhost")
     * @param remotePort   remote port to forward (e.g., 5900 for VNC)
     * @param localPort    local port to bind (e.g., 5900, or 0 for auto)
     * @return the SSHTunnel instance with the effective local port
     * @throws JSchException if SSH connection fails
     */
    public static SSHTunnel open(String sshHost, int sshPort, String sshUser,
                                  String sshPassword, String remoteHost,
                                  int remotePort, int localPort) throws JSchException {
        SSHTunnel tunnel = new SSHTunnel();

        SikuliLogger.info("[SSH] Opening tunnel to " + sshHost + ":" + sshPort
            + " -> " + remoteHost + ":" + remotePort);

        JSch jsch = new JSch();
        Session session = jsch.getSession(sshUser, sshHost, sshPort);
        session.setPassword(sshPassword);

        // Disable strict host key checking (hosts change keys frequently)
        session.setConfig("StrictHostKeyChecking", "no");

        // Configure ciphers compatible with old servers (SUSE 12)
        session.setConfig("cipher.s2c", "aes128-ctr,aes128-cbc,3des-cbc,aes192-ctr,aes256-ctr");
        session.setConfig("cipher.c2s", "aes128-ctr,aes128-cbc,3des-cbc,aes192-ctr,aes256-ctr");
        session.setConfig("mac.s2c", "hmac-sha2-256,hmac-sha1");
        session.setConfig("mac.c2s", "hmac-sha2-256,hmac-sha1");

        // Key exchange algorithms compatible with older servers
        session.setConfig("kex", "ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,"
            + "diffie-hellman-group-exchange-sha256,diffie-hellman-group14-sha1,"
            + "diffie-hellman-group-exchange-sha1,diffie-hellman-group1-sha1");

        // Host key types
        session.setConfig("server_host_key", "ssh-rsa,ssh-dss,ecdsa-sha2-nistp256,ssh-ed25519");

        // ServerAliveInterval instead of TCPKeepAlive (more reliable through firewalls)
        session.setServerAliveInterval(DEFAULT_SERVER_ALIVE_INTERVAL * 1000);
        session.setServerAliveCountMax(DEFAULT_SERVER_ALIVE_COUNT_MAX);

        // Disable TCP keepalive (we use ServerAlive instead)
        session.setConfig("TCPKeepAlive", "no");

        // Connect with timeout
        session.connect(DEFAULT_CONNECT_TIMEOUT_MS);

        // Set up port forwarding
        int assignedPort = session.setPortForwardingL(localPort, remoteHost, remotePort);

        tunnel.session = session;
        tunnel.localPort = assignedPort;

        SikuliLogger.info("[SSH] Tunnel established: localhost:" + assignedPort
            + " -> " + remoteHost + ":" + remotePort);

        return tunnel;
    }

    /**
     * Simplified overload: tunnel to localhost:5900 on local port 5900.
     *
     * @param sshHost     SSH server address
     * @param sshUser     SSH username
     * @param sshPassword SSH password
     * @return the SSHTunnel instance
     * @throws JSchException if SSH connection fails
     */
    public static SSHTunnel open(String sshHost, String sshUser, String sshPassword)
            throws JSchException {
        return open(sshHost, DEFAULT_SSH_PORT, sshUser, sshPassword,
                    "localhost", DEFAULT_VNC_PORT, DEFAULT_VNC_PORT);
    }

    /**
     * Overload with custom SSH port.
     *
     * @param sshHost     SSH server address
     * @param sshPort     SSH port
     * @param sshUser     SSH username
     * @param sshPassword SSH password
     * @return the SSHTunnel instance
     * @throws JSchException if SSH connection fails
     */
    public static SSHTunnel open(String sshHost, int sshPort, String sshUser,
                                  String sshPassword) throws JSchException {
        return open(sshHost, sshPort, sshUser, sshPassword,
                    "localhost", DEFAULT_VNC_PORT, DEFAULT_VNC_PORT);
    }

    /**
     * Overload with auto-assigned local port.
     *
     * @param sshHost     SSH server address
     * @param sshPort     SSH port
     * @param sshUser     SSH username
     * @param sshPassword SSH password
     * @param remoteHost  remote host for forwarding
     * @param remotePort  remote port to forward
     * @return the SSHTunnel instance
     * @throws JSchException if SSH connection fails
     */
    public static SSHTunnel openAutoPort(String sshHost, int sshPort, String sshUser,
                                          String sshPassword, String remoteHost,
                                          int remotePort) throws JSchException {
        return open(sshHost, sshPort, sshUser, sshPassword, remoteHost, remotePort, 0);
    }

    /**
     * Get the local port the tunnel is listening on.
     */
    public int getLocalPort() {
        return localPort;
    }

    /**
     * Check if the tunnel is active.
     */
    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    /**
     * Close the tunnel and SSH session.
     */
    @Override
    public void close() {
        if (session != null && session.isConnected()) {
            try {
                session.delPortForwardingL(localPort);
            } catch (JSchException e) {
                // Ignore cleanup errors
            }
            session.disconnect();
            SikuliLogger.info("[SSH] Tunnel closed (port " + localPort + ")");
        }
    }
}
