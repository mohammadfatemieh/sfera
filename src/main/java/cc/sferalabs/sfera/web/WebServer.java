package cc.sferalabs.sfera.web;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletMapping;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cc.sferalabs.sfera.core.Configuration;
import cc.sferalabs.sfera.core.SystemNode;
import cc.sferalabs.sfera.core.services.AutoStartService;
import cc.sferalabs.sfera.web.api.http.servlets.CommandServlet;
import cc.sferalabs.sfera.web.api.http.servlets.ConnectServlet;
import cc.sferalabs.sfera.web.api.http.servlets.EventServlet;
import cc.sferalabs.sfera.web.api.http.servlets.LoginServlet;
import cc.sferalabs.sfera.web.api.http.servlets.LogoutServlet;
import cc.sferalabs.sfera.web.api.http.servlets.StateServlet;
import cc.sferalabs.sfera.web.api.http.servlets.SubscribeServlet;
import cc.sferalabs.sfera.web.api.http.servlets.access.AddAccessServlet;
import cc.sferalabs.sfera.web.api.http.servlets.access.ListUsersServlet;
import cc.sferalabs.sfera.web.api.http.servlets.access.RemoveAccessServlet;
import cc.sferalabs.sfera.web.api.http.servlets.access.UpdateAccessServlet;
import cc.sferalabs.sfera.web.api.http.servlets.files.CopyFilesServlet;
import cc.sferalabs.sfera.web.api.http.servlets.files.DeleteFilesServlet;
import cc.sferalabs.sfera.web.api.http.servlets.files.DownloadFilesServlet;
import cc.sferalabs.sfera.web.api.http.servlets.files.ListFilesServlet;
import cc.sferalabs.sfera.web.api.http.servlets.files.MkdirFileServlet;
import cc.sferalabs.sfera.web.api.http.servlets.files.MoveFilesServlet;
import cc.sferalabs.sfera.web.api.http.servlets.files.ReadFileServlet;
import cc.sferalabs.sfera.web.api.http.servlets.files.UploadFilesServlet;
import cc.sferalabs.sfera.web.api.http.servlets.files.WriteFileServlet;
import cc.sferalabs.sfera.web.api.websockets.ApiWebSocketServlet;

/**
 * HTTP Server
 * 
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
public class WebServer implements AutoStartService {

	private static final String KEYSTORE_PATH = "data/http/sfera.keys";
	private static final String SESSIONS_STORE_DIR = "data/http/sessions";

	private static final Logger logger = LoggerFactory.getLogger(WebServer.class);
	private static final String[] EXCLUDED_PROTOCOLS = { "SSL", "SSLv2", "SSLv2Hello", "SSLv3" };
	private static final String[] EXCLUDED_CIPHER_SUITES = { ".*NULL.*", ".*RC4.*", ".*MD5.*",
			".*DES.*", ".*DSS.*" };

	private static Server server;
	private static ServletContextHandler contexts;

	@Override
	public void init() throws Exception {
		Configuration config = SystemNode.getConfiguration();
		Integer http_port = null;
		Integer https_port = null;
		if (config != null) {
			http_port = config.get("http_port", null);
			https_port = config.get("https_port", null);
		}

		if (http_port == null && https_port == null) {
			logger.warn("No HTTP port defined in configuration. Server disabled");
			return;
		}

		int maxThreads = config.get("http_max_threads",
				Runtime.getRuntime().availableProcessors() * 128);
		int minThreads = config.get("http_min_threads", 8);
		int idleTimeout = config.get("http_threads_idle_timeout", 60000);

		QueuedThreadPool threadPool = new QueuedThreadPool(maxThreads, minThreads, idleTimeout);

		server = new Server(threadPool);

		if (http_port != null) {
			ServerConnector http = new ServerConnector(server);
			http.setName("http");
			http.setPort(http_port);
			server.addConnector(http);
			logger.info("Starting HTTP server on port {}", http_port);
		}

		if (https_port != null) {
			String keyStorePassword = config.get("keystore_password", null);
			if (keyStorePassword == null) {
				throw new Exception("'keystore_password' not specified in configuration");
			}
			Path keystorePath = Paths.get(KEYSTORE_PATH);
			if (!Files.exists(keystorePath)) {
				throw new NoSuchFileException(KEYSTORE_PATH);
			}

			SslContextFactory sslContextFactory = new SslContextFactory();
			sslContextFactory.setKeyStorePath(KEYSTORE_PATH);
			sslContextFactory.setKeyStorePassword(keyStorePassword);
			String keyManagerPassword = config.get("keymanager_password", null);
			if (keyManagerPassword != null) {
				sslContextFactory.setKeyManagerPassword(keyManagerPassword);
			}
			sslContextFactory.addExcludeProtocols(EXCLUDED_PROTOCOLS);
			sslContextFactory.setExcludeCipherSuites(EXCLUDED_CIPHER_SUITES);
			sslContextFactory.setRenegotiationAllowed(false);
			sslContextFactory.setUseCipherSuitesOrder(false);

			HttpConfiguration https_config = new HttpConfiguration();
			https_config.setSecurePort(https_port);
			https_config.addCustomizer(new SecureRequestCustomizer());

			ServerConnector https = new ServerConnector(server,
					new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
					new HttpConnectionFactory(https_config));
			https.setName("https");
			https.setPort(https_port);

			server.addConnector(https);
			logger.info("Starting HTTPS server on port {}", https_port);
		}

		HashSessionManager hsm = new HashSessionManager();
		hsm.setStoreDirectory(new File(SESSIONS_STORE_DIR));
		// TODO try to make session restorable when server not stopped properly
		hsm.setSessionCookie("session");
		int maxInactiveInterval = config.get("http_session_max_inactive", 3600);
		hsm.setMaxInactiveInterval(maxInactiveInterval);
		SessionHandler sessionHandler = new SessionHandler(hsm);
		sessionHandler.addEventListener(new HttpSessionDestroyer());

		contexts = new ServletContextHandler(server, "/", ServletContextHandler.SESSIONS);
		contexts.setSessionHandler(sessionHandler);
		contexts.addFilter(AuthenticationFilter.class, "/*", null);
		contexts.addServlet(DefaultErrorServlet.class, "/*");

		registerApiServlets();

		try {
			server.start();
		} catch (Exception e) {
			throw new Exception("Error starting server: " + e.getLocalizedMessage(), e);
		}
	}

	/**
	 * 
	 * @throws WebServerException
	 */
	private void registerApiServlets() throws WebServerException {
		addServlet(LoginServlet.class, LoginServlet.PATH);
		addServlet(LogoutServlet.class, LogoutServlet.PATH);
		addServlet(ConnectServlet.class, ConnectServlet.PATH);
		addServlet(SubscribeServlet.class, SubscribeServlet.PATH);
		addServlet(StateServlet.class, StateServlet.PATH);
		addServlet(CommandServlet.class, CommandServlet.PATH);
		addServlet(EventServlet.class, EventServlet.PATH);
		addServlet(ApiWebSocketServlet.class, ApiWebSocketServlet.PATH);

		// files
		addServlet(ListFilesServlet.class, ListFilesServlet.PATH);
		addServlet(ReadFileServlet.class, ReadFileServlet.PATH);
		addServlet(WriteFileServlet.class, WriteFileServlet.PATH);
		addServlet(MoveFilesServlet.class, MoveFilesServlet.PATH);
		addServlet(CopyFilesServlet.class, CopyFilesServlet.PATH);
		addServlet(DeleteFilesServlet.class, DeleteFilesServlet.PATH);
		addServlet(MkdirFileServlet.class, MkdirFileServlet.PATH);
		addServlet(DownloadFilesServlet.class, DownloadFilesServlet.PATH);
		addServlet(UploadFilesServlet.class, UploadFilesServlet.PATH);

		// access
		addServlet(ListUsersServlet.class, ListUsersServlet.PATH);
		addServlet(AddAccessServlet.class, AddAccessServlet.PATH);
		addServlet(RemoveAccessServlet.class, RemoveAccessServlet.PATH);
		addServlet(UpdateAccessServlet.class, UpdateAccessServlet.PATH);
	}

	/**
	 * Registers the specified servlet class to handle the requests on paths
	 * matching the specified path spec
	 * 
	 * @param servlet
	 *            the servlet class
	 * @param pathSpec
	 *            the path spec to map servlet to
	 * @throws WebServerException
	 *             if an error occurs
	 */
	public synchronized static void addServlet(Class<? extends Servlet> servlet, String pathSpec)
			throws WebServerException {
		addServlet((Object) servlet, pathSpec);
	}

	/**
	 * Registers the servlet contained by the servlet holder to handle the
	 * requests on paths matching the specified path spec
	 * 
	 * @param servlet
	 *            the servlet holder
	 * @param pathSpec
	 *            the path spec to map servlet to
	 * @throws WebServerException
	 *             if an error occurs
	 */
	public synchronized static void addServlet(ServletHolder servlet, String pathSpec)
			throws WebServerException {
		addServlet((Object) servlet, pathSpec);
	}

	/**
	 * 
	 * @param servlet
	 * @param pathSpec
	 * @throws WebServerException
	 */
	@SuppressWarnings("unchecked")
	private static void addServlet(Object servlet, String pathSpec) throws WebServerException {
		Objects.requireNonNull(servlet, "servlet must not be null");
		Objects.requireNonNull(pathSpec, "pathSpec must not be null");
		if (contexts != null) {
			try {
				if (servlet instanceof ServletHolder) {
					contexts.addServlet((ServletHolder) servlet, pathSpec);
				} else if (servlet instanceof Class) {
					contexts.addServlet((Class<? extends Servlet>) servlet, pathSpec);
				} else {
					contexts.addServlet((String) servlet, pathSpec);
				}
				logger.debug("Added servlet for path {}", pathSpec);
			} catch (Exception e) {
				throw new WebServerException(e);
			}
		} else {
			throw new WebServerException("HTTP server service not available");
		}
	}

	/**
	 * Removes the previously registered servlet from the specified path
	 * 
	 * @param pathSpec
	 *            the path spec to remove
	 * @throws WebServerException
	 *             if an error occurs
	 */
	public synchronized static void removeServlet(String pathSpec) throws WebServerException {
		if (contexts != null) {
			try {
				ServletHandler handler = contexts.getServletHandler();
				List<ServletHolder> servlets = new ArrayList<>();
				List<ServletMapping> mappings = new ArrayList<>();

				Map<String, List<ServletHolder>> servletsMap = new HashMap<>();
				for (ServletHolder sh : handler.getServlets()) {
					List<ServletHolder> list = servletsMap.get(sh.getName());
					if (list == null) {
						list = new ArrayList<>();
						servletsMap.put(sh.getName(), list);
					}
					list.add(sh);
				}

				for (ServletMapping mapping : handler.getServletMappings()) {
					List<String> pathSpecs = new ArrayList<>();
					for (String path : mapping.getPathSpecs()) {
						if (!pathSpec.equals(path)) {
							pathSpecs.add(path);
						}
					}
					if (!pathSpecs.isEmpty()) {
						mapping.setPathSpecs(pathSpecs.toArray(new String[pathSpecs.size()]));
						mappings.add(mapping);
					} else {
						List<ServletHolder> list = servletsMap.get(mapping.getServletName());
						if (list != null && !list.isEmpty()) {
							list.remove(0);
						}
					}
				}

				for (List<ServletHolder> list : servletsMap.values()) {
					servlets.addAll(list);
				}

				handler.setServletMappings(mappings.toArray(new ServletMapping[mappings.size()]));
				handler.setServlets(servlets.toArray(new ServletHolder[servlets.size()]));
			} catch (Exception e) {
				throw new WebServerException(e);
			}
		}
	}

	/**
	 * Removes the previously registered servlet holder
	 * 
	 * @param servlet
	 *            the servlet holder
	 * @throws WebServerException
	 *             if an error occurs
	 */
	public synchronized static void removeServlet(ServletHolder servlet) throws WebServerException {
		if (contexts != null) {
			try {
				ServletHandler handler = contexts.getServletHandler();
				List<ServletHolder> servlets = new ArrayList<>();
				List<ServletMapping> mappings = new ArrayList<>();

				for (ServletHolder sh : handler.getServlets()) {
					if (servlet != sh) {
						servlets.add(sh);
					}
				}

				for (ServletMapping mapping : handler.getServletMappings()) {
					if (!mapping.getServletName().equals(servlet.getName())) {
						mappings.add(mapping);
					} else {
						if (logger.isDebugEnabled()) {
							for (String path : mapping.getPathSpecs()) {
								logger.debug("Removed servlet for path {}", path);
							}
						}
					}
				}

				handler.setServletMappings(mappings.toArray(new ServletMapping[mappings.size()]));
				handler.setServlets(servlets.toArray(new ServletHolder[servlets.size()]));
			} catch (Exception e) {
				throw new WebServerException(e);
			}
		}
	}

	/**
	 * Removes the previously registered servlets of the specified class
	 * 
	 * @param servlet
	 *            the servlet class
	 * @throws WebServerException
	 *             if an error occurs
	 */
	public synchronized static void removeServlet(Class<? extends Servlet> servlet)
			throws WebServerException {
		if (contexts != null) {
			ServletHandler handler = contexts.getServletHandler();
			if (handler != null) {
				for (ServletHolder sh : handler.getServlets()) {
					Servlet s;
					try {
						s = sh.getServlet();
					} catch (ServletException e) {
						throw new WebServerException(e);
					}
					if (servlet.isInstance(s)) {
						removeServlet(sh);
					}
				}
			}
		}
	}

	@Override
	public void quit() throws Exception {
		if (server != null)
			server.stop();
	}

}