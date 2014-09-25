package com.homesystemsconsulting.drivers.webserver;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.Executors;

import com.homesystemsconsulting.core.Configuration;
import com.homesystemsconsulting.core.Task;
import com.homesystemsconsulting.core.TasksManager;
import com.homesystemsconsulting.drivers.webserver.access.Access;
import com.homesystemsconsulting.drivers.webserver.access.Token;
import com.homesystemsconsulting.drivers.webserver.access.User;

public class ConnectionHandler extends Task {
	private static final int SOCKET_TIMEOUT = 60000;

	private static TasksManager tasksManager;
	private static final Object tasksManagerLock = new Object();
	private static int passwordMaxAgeSeconds;

	private final Socket connection;
	private final String protocol;
	private PrintWriter out;
	private BufferedOutputStream dataOut;
	private BufferedReader in;
	
	/**
	 * 
	 * @param connection
	 * @param string 
	 * @throws SocketException 
	 */
	public ConnectionHandler(Socket connection, String protocol) throws SocketException {
		super(WebServer.WEB_SERVER_DRIVER_ID + "#" + connection.getInetAddress());
		this.connection = connection;
		this.connection.setSoTimeout(SOCKET_TIMEOUT);
		this.protocol = protocol;
		tasksManager.execute(this);
	}
	
	/**
	 * 
	 */
	public static void init() {
		synchronized (tasksManagerLock) {
			if (tasksManager == null) {
				Integer maxRequestThreads = Configuration.getIntProperty("web.max_threads", null);
				if (maxRequestThreads == null) {
					int availableProcessors = Runtime.getRuntime().availableProcessors();
					maxRequestThreads = availableProcessors * 128;
				}
				tasksManager = new TasksManager(Executors.newFixedThreadPool(maxRequestThreads));
				
				passwordMaxAgeSeconds = Configuration.getIntProperty("web.password_validity_hours", 5) * 60 * 60;
			}
		}
	}
	
	/**
	 * 
	 */
	public static void quit() {
		synchronized (tasksManagerLock) {			
			if (tasksManager != null) {
				tasksManager.getExecutorService().shutdownNow();
				tasksManager = null;
			}
		}
	}

	@Override
	public void execute() {
		try {
			out = new PrintWriter(connection.getOutputStream());
			dataOut = new BufferedOutputStream(connection.getOutputStream());
			in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			
			boolean keepAlive = true;
			while (keepAlive) {
				HttpRequestHeader httpRequestHeader = null;
				String reqLine;
				while ((reqLine = in.readLine()) != null) {
					if (reqLine.length() == 0) {
						if (httpRequestHeader == null) {
							keepAlive = false;
							
						} else {
							if (httpRequestHeader.getConnectionClose()) {
								keepAlive = false;
							} 
							
							try {
								if (!processRequest(httpRequestHeader)) {
									keepAlive = false;
								}
							} catch (Exception e) {
								WebServer.log.warning("error processing request: " + e);
								e.printStackTrace();
								keepAlive = false;
							}
						}
						
						break;
					}
					
					if (httpRequestHeader == null) {
						try {
							httpRequestHeader = new HttpRequestHeader(reqLine);
						} catch (NotImplementedRequestMethodException e) {
							notImplementedError();
							keepAlive = false;
							break;
						}
					} else {
						httpRequestHeader.addField(reqLine);
					}
				}
				
				if (reqLine == null) {
					keepAlive = false;
				}
			}
			
		} catch (Exception e) {
			WebServer.log.debug("connection exception: " + e);
			
		} finally {
			try { in.close(); } catch (Exception e) {}
			try { out.close(); } catch (Exception e) {}
			try { dataOut.close(); } catch (Exception e) {}
		}
		
		WebServer.log.debug("connection " + connection.getInetAddress() + " terminated");
	}

	/**
	 * 
	 * @param httpRequestHeader
	 * @return
	 * @throws Exception
	 */
	private boolean processRequest(HttpRequestHeader httpRequestHeader) throws Exception {
		String uri = httpRequestHeader.getURI();
		Token token = getToken(httpRequestHeader);
		WebServer.log.debug("processing request from: " + connection.getInetAddress() + " URI: " + uri + (token == null ? "" : " User: " + token.getUser().getUsername()));
		
		int qmIdx = uri.indexOf('?');
		String query;
		if (qmIdx >= 0) {
			query = uri.substring(qmIdx);
			uri = uri.substring(0, qmIdx);
		} else {
			query = null;
		}
		
		if (uri.startsWith(WebServer.API_BASE_URI)) {
			return processApiRequest(uri.substring(WebServer.API_BASE_URI.length()), token, query, httpRequestHeader);
		
		} else {
			return InterfaceCache.processFileRequest(uri, token, httpRequestHeader, this);
		}
	}

	/**
	 * 
	 * @param httpRequestHeader
	 * @return
	 */
	private Token getToken(HttpRequestHeader httpRequestHeader) {
		String token = getCookieValue("token", httpRequestHeader.getCookies());
		if (token == null) {
			return null;
		}
		return Access.getToken(token, httpRequestHeader);
	}

	/**
	 * 
	 * @param command
	 * @param token
	 * @param query
	 * @param httpRequestHeader
	 * @return
	 * @throws Exception
	 */
	private boolean processApiRequest(String command, Token token, String query, HttpRequestHeader httpRequestHeader) throws Exception {
		
		System.out.println("command: " + command);
		System.out.println("query: " + query);
		
		if (command.equals("login")) {
			login(token, query, httpRequestHeader);
			return true;
		}
		
		if (token == null) {
			notAuthorizedError();
			return false;
		}
		
		if (command.equals("logout")) {
			logout(token);
			return false;
		}
		
		if (command.equals("subscribe")) {
			subscribe(token, query);
			return true;
		}
		
		if (command.startsWith("status/")) {
			status(command.substring(7), token, query);
			return true;
		}
		
		WebServer.log.warning("unknown API request: " + command);
		return false;
	}

	/**
	 * 
	 * @param substring
	 * @param token
	 * @param query
	 */
	private void status(String substring, Token token, String query) {

		// TODO
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {}
		
		ok(null, null);
	}

	/**
	 * 
	 * @param token
	 * @param query
	 */
	private void subscribe(Token token, String query) {
		//TODO
		String id = getQueryValue("id", query);
		if (id == null) {
			id = UUID.randomUUID().toString();
		}
		ok("{\"id\":\"" + id + "\"}", "application/json");
	}

	/**
	 * 
	 * @param token
	 * @param query
	 * @param httpRequestHeader
	 * @throws Exception
	 */
	private void login(Token token, String query, HttpRequestHeader httpRequestHeader) throws Exception {
		User user = null;
		if (query == null) {
			if (token != null) {
				ok(null, null);
			} else {
				notAuthorizedError();
			}
			
		} else {
			String username = getQueryValue("user", query);
			String password = getQueryValue("password", query);
			
			if (token != null) {
				Access.removeToken(token.getUUID());
			}
			user = Access.authenticate(username, password);
			if (user != null) {
				String tokenUUID = Access.assignToken(user, httpRequestHeader);
				setTokenCookie(tokenUUID);
				WebServer.log.info("login: " + username);
				
			} else {
				WebServer.log.warning("failed login attempt - username: " + username);
				notAuthorizedError();
			}
		}
	}
	
	/**
	 * 
	 * @param token
	 */
	private void logout(Token token) {
		Access.removeToken(token.getUUID());
		WebServer.log.info("logout: " + token.getUser().getUsername());
		setTokenCookie(null);
	}

	/**
	 * 
	 */
	public void notAuthorizedError() {
		out.print("HTTP/1.1 401 Unauthorized\r\n");
		out.print("Date: " + DateUtil.now() + "\r\n");
		out.print("Server: " + WebServer.HTTP_HEADER_FIELD_SERVER + "\r\n");
		out.write("Cache-Control: max-age=0, no-cache, no-store\r\n");
		out.print("Content-length: 0\r\n");
		out.print("\r\n");
		out.flush();
	}
	
	/**
	 * 
	 */
	public void notImplementedError() {
		out.print("HTTP/1.1 501 Not implemented\r\n");
		out.print("Date: " + DateUtil.now() + "\r\n");
		out.print("Server: " + WebServer.HTTP_HEADER_FIELD_SERVER + "\r\n");
		out.print("Content-length: 0\r\n");
		out.print("\r\n");
		out.flush();
	}
	
	/**
	 * 
	 * @param out
	 */
	public void notFoundError() {
		out.print("HTTP/1.1 404 Not Found\r\n");
		out.print("Date: " + DateUtil.now() + "\r\n");
		out.print("Server: " + WebServer.HTTP_HEADER_FIELD_SERVER + "\r\n");
		out.write("Cache-Control: max-age=0, no-cache, no-store\r\n");
		out.print("Content-length: 0\r\n");
		out.print("\r\n");
		out.flush();
	}
	
	/**
	 * 
	 * @param body
	 * @param contentType
	 */
	private void ok(String body, String contentType) {
		out.print("HTTP/1.1 200 OK\r\n");
        out.print("Date: " + DateUtil.now() + "\r\n");
        out.print("Server: " + WebServer.HTTP_HEADER_FIELD_SERVER + "\r\n");
        out.write("Cache-Control: max-age=0, no-cache, no-store\r\n");
        if (contentType != null) {
        	out.print("Content-type: " + contentType + "\r\n");
        }
        if (body != null) {
        	out.print("Content-length: " + body.getBytes(Charset.forName("UTF-8")).length + "\r\n");
        } else {
        	out.print("Content-length: 0\r\n");
        }
        out.print("\r\n");
        if (body != null) {
        	out.print(body);
        }
        out.flush();
	}
	
	/**
	 * 
	 * @param page
	 * @param httpRequestHeader
	 */
	public void redirectTo(String page, HttpRequestHeader httpRequestHeader) {
		out.print("HTTP/1.1 307 Temporary Redirect\r\n");
		out.print("Location: ");
		if (httpRequestHeader.getHost() != null) {				
			out.print(protocol);
			out.print("://");
			out.print(httpRequestHeader.getHost());
		}
		out.print('/');
		out.print(page);
		out.print("\r\n");
		out.write("Cache-Control: max-age=0, no-cache, no-store\r\n");
		out.print("Content-length: 0\r\n");
		out.print("\r\n");
		out.flush();
	}
	
	/**
	 * 
	 * @param tokenUUID
	 */
	private void setTokenCookie(String tokenUUID) {
		out.print("HTTP/1.1 200 OK\r\n");
		out.print("Date: " + DateUtil.now() + "\r\n");
		out.print("Server: " + WebServer.HTTP_HEADER_FIELD_SERVER + "\r\n");
		if (tokenUUID == null) {
			out.print("Set-Cookie: token=removed; Path=/; Max-Age=0\r\n");
		} else {
			out.print("Set-Cookie: token=" + tokenUUID + "; Path=/; Max-Age=" + passwordMaxAgeSeconds + "\r\n");
		}
		out.write("Cache-Control: max-age=0, no-cache, no-store\r\n");
		out.print("Content-length: 0\r\n");
		out.print("\r\n");
		out.flush();
	}

	/**
	 * 
	 * @param key
	 * @param query
	 * @return
	 */
	private String getQueryValue(String key, String query) {
		if (query == null) {
			return null;
		}
		int start = query.indexOf("?" + key + "=");
		if (start < 0) {
			start = query.indexOf("&" + key + "=");
			if (start < 0) {
				return null;
			}
		}
		start += key.length() + 2;
		int end = query.indexOf('&', start);
		if (end < 0) {
			end = query.length();
		}
		return query.substring(start, end);
	}
	
	/**
	 * 
	 * @param key
	 * @param cookies
	 * @return
	 */
	private String getCookieValue(String key, String cookies) {
		if (cookies == null) {
			return null;
		}
		String[] entries = cookies.split("[ ,;]+");
		for (String entry : entries) {
			if (entry.startsWith(key + "=")) {
				return entry.substring(key.length() + 1);
			}
		}
		return null;
	}

	/**
	 * 
	 * @param s
	 */
	public void write(String s) {
		out.print(s);
	}
	
	/**
	 * 
	 * @param b
	 * @throws IOException
	 */
	public void write(byte[] b) throws IOException {
		dataOut.write(b, 0, b.length);
	}

	/**
	 * 
	 * @throws IOException
	 */
	public void flush() throws IOException {
		out.flush();
		dataOut.flush();
	}
}