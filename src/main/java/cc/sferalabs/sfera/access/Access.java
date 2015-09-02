package cc.sferalabs.sfera.access;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class for users authorization and authentication
 *
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 * 
 */
public abstract class Access {

	/** Users credentials configuration file */
	private static final String USERS_FILE_PATH = "data/access/passwd";

	/** Map of existing users indexed by username */
	private static final Map<String, User> users = new ConcurrentSkipListMap<String, User>(
			String.CASE_INSENSITIVE_ORDER);

	private static final Logger logger = LogManager.getLogger();

	/**
	 * Initialize user credentials reading from the configuration file
	 * 
	 * @throws IOException
	 */
	public synchronized static void init() throws IOException {
		List<String> lines;
		try {
			lines = Files.readAllLines(Paths.get(USERS_FILE_PATH), StandardCharsets.UTF_8);
		} catch (NoSuchFileException e) {
			logger.debug("File '{}' not found", USERS_FILE_PATH);
			return;
		}
		int lineNum = 0;
		for (String line : lines) {
			line = line.trim();
			if (line.length() > 0) {
				try {
					String[] splitted = line.split(":");
					User u = new User(splitted[0], splitted[1], splitted[2],
							splitted[3].split(","));
					users.put(u.getUsername(), u);
					logger.debug("User '{}' created", u.getUsername());
				} catch (Exception e) {
					logger.error("Error reading file '" + USERS_FILE_PATH + "' on line " + lineNum,
							e);
				}
			}
			lineNum++;
		}
	}

	/**
	 * Creates a new user and adds it to the list
	 * 
	 * @param username
	 *            username for the new user
	 * @param plainPassword
	 *            plain-text password
	 * @param roles
	 *            list of roles the user belongs to
	 * 
	 * @throws UsernameAlreadyUsedException
	 *             if a user with the specified username already exists
	 * @throws IOException
	 *             if an I/O error occurs writing to the configuration file
	 */
	public synchronized static void addUser(String username, String plainPassword, String[] roles)
			throws UsernameAlreadyUsedException, IOException {
		if (users.containsKey(username)) {
			throw new UsernameAlreadyUsedException();
		}

		byte[] salt = generateSalt();
		byte[] hashedPassword = getEncryptedPassword(plainPassword, salt);

		users.put(username, new User(username, hashedPassword, salt, roles));

		String userLine = username + ":";
		userLine += Base64.getEncoder().encodeToString(hashedPassword) + ":";
		userLine += Base64.getEncoder().encodeToString(salt) + ":";
		for (int i = 0; i < roles.length; i++) {
			if (i != 0) {
				userLine += ",";
			}
			userLine += roles[i];
		}
		userLine += "\n";

		Files.write(Paths.get(USERS_FILE_PATH), userLine.getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.APPEND);
	}

	/**
	 * Returns the user with the specified username
	 * 
	 * @param username
	 *            the username
	 * @return
	 */
	public synchronized static User getUser(String username) {
		return users.get(username);
	}

	/**
	 * Returns a set containing all the existing users
	 * 
	 * @return
	 */
	public synchronized static Set<User> getUsers() {
		return new HashSet<User>(users.values());
	}

	/**
	 * Returns whether or not the <code>attemptedPassword</code> corresponds to
	 * the actual password of the user identified by <code>username</code>
	 * 
	 * @param username
	 *            the username
	 * @param attemptedPassword
	 *            the attempted password
	 * @return <code>true</code> if <code>attemptedPassword</code> corresponds
	 *         to the actual password of the user identified by
	 *         <code>username</code>, <code>false</code> otherwise
	 */
	public synchronized static boolean authenticate(String username, String attemptedPassword) {
		User u = users.get(username);
		if (u == null) {
			return false;
		}

		byte[] hashedPassword = getEncryptedPassword(attemptedPassword, u.getSalt());
		if (Arrays.equals(hashedPassword, u.getHashedPassword())) {
			return true;
		}

		return false;
	}

	/**
	 * 
	 * @return random bytes sequence to be used as salt
	 */
	private static byte[] generateSalt() {
		try {
			SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
			byte[] salt = new byte[8];
			random.nextBytes(salt);

			return salt;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 
	 * @param password
	 *            plain-text password
	 * @param salt
	 *            the salt
	 * @return bytes sequence representing the encrypted password
	 */
	private static byte[] getEncryptedPassword(String password, byte[] salt) {
		try {
			KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 20000, 20 * 8);
			SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");

			return f.generateSecret(spec).getEncoded();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
