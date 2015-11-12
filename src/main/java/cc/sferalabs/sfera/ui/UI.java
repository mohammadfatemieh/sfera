/**
 * 
 */
package cc.sferalabs.sfera.ui;

import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cc.sferalabs.sfera.events.Bus;
import cc.sferalabs.sfera.events.Node;
import cc.sferalabs.sfera.script.ScriptsEngine;

/**
 *
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
public class UI implements Node {

	private static final UI INSTANCE = new UI();

	private static final Logger logger = LoggerFactory.getLogger(UI.class);

	static {
		try {
			ScriptsEngine.putTypeInGlobalScope("ui", UI.class);
		} catch (ScriptException e) {
			logger.error("Error adding 'ui' to script engine", e);
		}
	}

	/**
	 * @return
	 */
	static UI getInstance() {
		return INSTANCE;
	}

	@Override
	public final String getId() {
		return "ui";
	}

	/**
	 * 
	 * @param id
	 *            ID of the addressed components
	 * @param attribute
	 *            attribute to set
	 * @param value
	 *            value to assign
	 */
	public static void set(String id, String attribute, String value) {
		Bus.post(new UISetEvent(id, attribute, value));
	}

}
