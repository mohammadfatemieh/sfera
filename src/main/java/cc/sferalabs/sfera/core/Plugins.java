package cc.sferalabs.sfera.core;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cc.sferalabs.sfera.core.events.PluginsEvent;
import cc.sferalabs.sfera.events.Bus;
import cc.sferalabs.sfera.util.files.FilesWatcher;

/**
 * Utility class for managing plugins
 * 
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
public abstract class Plugins {

	private static final String DIR_PATH = "plugins";
	private static ConcurrentHashMap<String, Plugin> plugins;
	private static final Logger logger = LoggerFactory.getLogger(Plugins.class);

	/**
	 * Loads the installed plugins and registers the plugins directory to be
	 * monitored for changes.
	 */
	static void load() {
		doLoad();
		try {
			FilesWatcher.register(Paths.get(DIR_PATH), "Plugins loader", Plugins::doLoad, false,
					false);
		} catch (Exception e) {
			logger.error("Error watching plugins directory", e);
		}
	}

	/**
	 * Loads the installed plugins.
	 */
	private static void doLoad() {
		plugins = new ConcurrentHashMap<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(DIR_PATH))) {
			for (Path file : stream) {
				try {
					if (!Files.isHidden(file)) {
						Plugin p = new Plugin(file);
						plugins.put(p.getId(), p);
						logger.info("Plugin '{}' loaded", p.getId());
					}
				} catch (Throwable e) {
					logger.error("Error loading file '" + file + "' in plugins folder", e);
				}
			}
		} catch (NoSuchFileException e) {
			logger.debug("Plugins directory not found");
		} catch (Exception e) {
			logger.error("Error loading plugins", e);
			return;
		}
		Bus.post(PluginsEvent.RELOAD);
	}

	/**
	 * Returns a Map containing all the installed plugins indexed by ID.
	 * 
	 * @return a Map containing all the installed plugins indexed by ID
	 */
	public static Map<String, Plugin> getAll() {
		return new HashMap<String, Plugin>(plugins);
	}

	/**
	 * Returns the plugin with the specified id.
	 * 
	 * @param id
	 *            the ID of the plugin to be returned
	 * @return the plugin with the specified id
	 */
	public static Plugin get(String id) {
		return plugins.get(id);
	}

}
