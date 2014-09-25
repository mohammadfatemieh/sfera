package com.homesystemsconsulting.drivers.webserver.access;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.homesystemsconsulting.drivers.webserver.HttpRequestHeader;

public abstract class Access {
	
	private static final  Map<String, User> users = new ConcurrentSkipListMap<String, User>(String.CASE_INSENSITIVE_ORDER);
	private static final  Map<String, Token> tokens = new ConcurrentHashMap<String, Token>();
	
	/**
	 * 
	 * @param username
	 * @param password
	 * @throws UsernameAlreadyUsedException
	 * @throws Exception
	 */
	public static void addUser(String username, String password) throws UsernameAlreadyUsedException, Exception {
		if (existsUser(username)) {
			throw new UsernameAlreadyUsedException();
		}
		
		byte[] salt = generateSalt();
		byte[] hashedPassword = getEncryptedPassword(password, salt);	
	
		users.put(username, new User(username, hashedPassword, salt));
	}
	
	/**
	 * 
	 * @param username
	 * @return
	 */
	private static boolean existsUser(String username) {
		return users.containsKey(username);
	}
	
	/**
	 * 
	 * @param username
	 * @param attemptedPassword
	 * @return
	 * @throws Exception
	 */
	public static User authenticate(String username, String attemptedPassword) throws Exception {
		User u = users.get(username);
		if (u == null) {
			return null;
		}
		
		byte[] hashedPassword = getEncryptedPassword(attemptedPassword, u.salt); 
		if (Arrays.equals(hashedPassword, u.hashedPassword)) {
			return u;
		}
		
		return null;
	}
	
	/**
	 * 
	 * @return
	 * @throws Exception
	 */
	private static byte[] generateSalt() throws Exception {
		SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
		byte[] salt = new byte[8];
		random.nextBytes(salt);

		return salt;
	}
	
	/**
	 * 
	 * @param password
	 * @param salt
	 * @return
	 * @throws Exception
	 */
	private static byte[] getEncryptedPassword(String password, byte[] salt) throws Exception {
		KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 20000, 20 * 8);
		SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");

		return f.generateSecret(spec).getEncoded();
	}

	/**
	 * 
	 * @param user
	 * @param httpRequestHeader 
	 * @return
	 */
	public static String assignToken(User user, HttpRequestHeader httpRequestHeader) {
		Token token = new Token(user, httpRequestHeader);
		tokens.put(token.getUUID(), token);
        return token.getUUID();
	}

	/**
	 * 
	 * @param tokenUUID
	 * @param httpRequestHeader
	 * @return
	 */
	public static Token getToken(String tokenUUID, HttpRequestHeader httpRequestHeader) {
		Token token = tokens.get(tokenUUID);
		if (token == null) {
			return null;
		}
		if (token.match(httpRequestHeader)) {
			return token;
		}
		
		return null;
	}

	/**
	 * 
	 * @param token
	 */
	public static void removeToken(String tokenUUID) {
		tokens.remove(tokenUUID);
	}
}