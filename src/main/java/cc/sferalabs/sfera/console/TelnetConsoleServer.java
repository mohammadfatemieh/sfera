/**
 * 
 */
package cc.sferalabs.sfera.console;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cc.sferalabs.sfera.core.services.TasksManager;

/**
 *
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
public class TelnetConsoleServer {

	private static final Logger logger = LoggerFactory.getLogger(TelnetConsoleServer.class);

	private final static List<TelnetConsoleSession> sessions = new ArrayList<>();
	private final ServerSocket socket;
	private boolean run;

	/**
	 * @param port
	 * @throws IOException
	 */
	public TelnetConsoleServer(int port) throws IOException {
		ServerSocketChannel ssc = ServerSocketChannel.open();
		ssc.bind(new InetSocketAddress(port));
		this.socket = ssc.socket();
		run = true;
		TasksManager.executeSystem("Telnet Console server", this::acceptTelnetConnection);
	}

	/**
	 * 
	 */
	private void acceptTelnetConnection() {
		logger.info("Telnet Console server accepting connections on port {}",
				socket.getLocalPort());
		while (run) {
			Socket s;
			try {
				s = socket.accept();
				TelnetConsoleSession tcs = new TelnetConsoleSession(s);
				synchronized (sessions) {
					sessions.add(tcs);
				}
				tcs.start();
			} catch (IOException e) {
				if (socket.isClosed()) {
					break;
				}
				logger.error("Telnet server error", e);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
				}
			}
		}
		logger.debug("Telnet Console server quitted");
	}

	/**
	 * @param session
	 */
	static void removeSession(TelnetConsoleSession session) {
		synchronized (sessions) {
			sessions.remove(session);
		}
	}

	/**
	 * 
	 * @throws IOException
	 */
	public void quit() throws IOException {
		run = false;
		synchronized (sessions) {
			for (TelnetConsoleSession s : sessions) {
				s.quit();
			}
		}
		socket.close();
	}

}
