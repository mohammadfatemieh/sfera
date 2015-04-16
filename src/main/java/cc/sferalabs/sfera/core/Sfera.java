package cc.sferalabs.sfera.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import cc.sferalabs.sfera.apps.Application;
import cc.sferalabs.sfera.drivers.Driver;
import cc.sferalabs.sfera.events.Bus;
import cc.sferalabs.sfera.events.SystemEvent;
import cc.sferalabs.sfera.script.ScriptsEngine;

public class Sfera {

	static {
		try {
			System.setProperty("java.awt.headless", "true");
			Path log4j2Config = Configuration.CONFIG_DIR.resolve("log4j2.xml");
			if (Files.exists(log4j2Config)) {
				System.setProperty("log4j.configurationFile",
						log4j2Config.toString());
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private static final BufferedReader STD_INPUT = new BufferedReader(
			new InputStreamReader(System.in));
	private static boolean run = true;

	private static ConcurrentHashMap<String, Plugin> plugins;

	private static List<Driver> drivers;
	private static List<Application> applications;

	private static final Logger logger = LogManager.getLogger();
	public static final Marker SFERA_MARKER = MarkerManager.getMarker("SFERA");

	private static final ServiceLoader<SferaService> SERVICE_LOADER = ServiceLoader
			.load(SferaService.class);

	/**
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			Thread.setDefaultUncaughtExceptionHandler(new SystemExceptionHandler());

			logger.info("======== Started ========");

			Bus.post(new SystemEvent("state", "start"));
			init();
			Bus.post(new SystemEvent("state", "ready"));
			while (run) {
				checkStandardInput();
			}
			Bus.post(new SystemEvent("state", "quit"));
			quit();

		} catch (Throwable t) {
			logger.catching(Level.FATAL, t);
		}

		logger.info("Quitted");
		System.exit(0);
	}

	/**
	 * 
	 */
	private static void init() {
		try {
			Configuration.load();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		loadPlugins();

		for (SferaService service : SERVICE_LOADER) {
			try {
				service.init();
			} catch (Exception e) {
				logger.error("Error initiating service '" + service.getClass()
						+ "'", e);
			}
		}

		for (final Driver d : drivers) {
			d.start();
		}
	}

	/**
	 * 
	 */
	private static void quit() {
		logger.warn("System stopped");
		logger.info("Quitting modules...");

		try {
			STD_INPUT.close();
		} catch (Exception e) {
		}

		for (final Driver d : drivers) {
			d.disable();
		}

		logger.debug("Waiting 5 seconds for modules to quit...");

		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
		}

		logger.debug("Shutting down remaining threads...");

		try {
			TasksManager.DEFAULT.getExecutorService().shutdownNow();
			TasksManager.DEFAULT.getExecutorService().awaitTermination(5,
					TimeUnit.SECONDS);
		} catch (InterruptedException e) {
		}

		for (SferaService service : SERVICE_LOADER) {
			try {
				service.quit();
			} catch (Exception e) {
				logger.error("Error quitting service '" + service.getClass()
						+ "'", e);
			}
		}
	}

	/**
	 * 
	 * @throws IOException
	 */
	private static void loadPlugins() {
		plugins = new ConcurrentHashMap<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths
				.get("plugins"))) {
			for (Path file : stream) {
				try {
					if (!Files.isHidden(file)) {
						Plugin p = new Plugin(file);
						plugins.put(p.getId(), p);
						logger.info("Plugin '{}' loaded", p.getId());
					}
				} catch (Exception e) {
					logger.error("Error loading file '" + file
							+ "' in plugins folder", e);
				}
			}
		} catch (NoSuchFileException e) {
			logger.debug("No plugins directory found");
		} catch (IOException e) {
			logger.error("Error loading plugins", e);
		}

		loadDrivers();
		loadApplications();
	}

	/**
	 * 
	 */
	private static void loadDrivers() {
		drivers = new ArrayList<Driver>();
		Properties props = Configuration.getProperties();
		for (String propName : props.stringPropertyNames()) {
			if (propName.indexOf('.') < 0) {
				// it's a driver instance definition
				String driverType = props.getProperty(propName);
				if (driverType != null) {
					try {
						Driver driverInstance = getModuleInstance(driverType,
								propName);
						drivers.add(driverInstance);
						ScriptsEngine.putObjectInGlobalScope(
								driverInstance.getId(), driverInstance);
						if (driverInstance instanceof EventListener) {
							Bus.register((EventListener) driverInstance);
						}
						logger.info("Driver '{}' of type '{}' instantiated",
								propName, driverType);
					} catch (Throwable e) {
						logger.error("Error instantiating driver '" + propName
								+ "' of type '" + driverType + "'", e);
					}
				}
			}
		}
	}

	/**
	 * 
	 */
	private static void loadApplications() {
		applications = new ArrayList<Application>();
		String appList = Configuration.SYSTEM.getProperty("apps", null);
		if (appList != null) {
			for (String appName : appList.split(",")) {
				appName = appName.trim();
				try {
					Application appInstance = getModuleInstance(appName, null);
					applications.add(appInstance);
					if (appInstance instanceof EventListener) {
						Bus.register((EventListener) appInstance);
					}
					logger.info("App '{}' loaded", appName);
				} catch (Throwable e) {
					logger.error("Error instantiating app: " + appName, e);
				}
			}
		}
	}

	/**
	 * 
	 * @param type
	 * @param id
	 * @return
	 * @throws Exception
	 */
	private static <T> T getModuleInstance(String type, String id)
			throws Exception {
		Plugin plugin = plugins.get(type);
		String className = null;
		if (plugin != null) {
			className = plugin.getProperty("class");
		} else {
			// when in development
			logger.warn("Plugin '{}' not found. Looking in local resources...",
					type);
			Properties p = new Properties();
			InputStream is = Sfera.class.getClassLoader().getResourceAsStream(
					Plugin.PLUGIN_PROPERTIES_PATH);
			if (is != null) {
				p.load(is);
				String plugId = p.getProperty("id");
				if (type.equals(plugId)) {
					className = p.getProperty("class");
				}
			}
		}

		if (className == null) {
			throw new Exception("Plugin '" + type + "' not found");
		}

		Class<?> clazz = Class.forName(className);

		Object instance;
		if (id == null) {
			Constructor<?> constructor = clazz.getConstructor();
			instance = constructor.newInstance();
		} else {
			Constructor<?> constructor = clazz
					.getConstructor(new Class[] { String.class });
			instance = constructor.newInstance(id);
		}

		@SuppressWarnings("unchecked")
		T i = (T) instance;

		return i;
	}

	/**
	 * 
	 */
	private static void checkStandardInput() {
		try {
			String cmd;
			if ((cmd = STD_INPUT.readLine()) != null) {
				switch (cmd.trim().toLowerCase()) {
				case "quit":
					run = false;
					break;

				default:
					break;
				}
			}
		} catch (Exception e) {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException ie) {
			}
		}
	}

	/**
	 * 
	 * @return
	 */
	public static Map<String, Plugin> getPlugins() {
		return plugins;
	}
}
