package it.unipr.netsec.mqtt.auth;


import org.zoolu.util.ByteUtils;


/** Access token.
 */
public class AccessToken {

	/** Access token header */
	public static final String TOKEN_HDR="ATHDR$";

	/** Token field delimiter */
	static final char DELIMITER='$';
	static final String DELIMITER_REGEX="\\x"+Integer.toHexString((int)DELIMITER);

	private String client_id;
	private String audience;
	private byte[] signature;

	
	/** Creates a new token.
	 * @param client_id client id
	 * @param audience audience
	 * @param signature signature */
	public AccessToken(String client_id, String audience, byte[] signature) {
		this.client_id=client_id;
		this.audience=audience;
		this.signature=signature;
	}
	
	/** Gets client id.
	 * @return the id */
	public String getClientId() {
		return client_id;
	}

	
	/** Gets audience.
	 * @return the audience */
	public String getAudience() {
		return audience;
	}

	
	/** Gets signature.
	 * @return the signature */
	public byte[] getSignature() {
		return signature;
	}

	/** Gets token.
	 * @return the token */
	public String getToken() {
		return TOKEN_HDR+client_id+DELIMITER+audience+DELIMITER+ByteUtils.asHex(signature);
	}

	/** Checks whether it is seems an access token.
	 * @param token the token
	 * @return 'true' if it is token */
	public static boolean containsToken(String token) {
		return token.startsWith(TOKEN_HDR);
	}
	
	/** Gets token.
	 * @param token the token */
	public static AccessToken parseToken(String token) {
		if (!containsToken(token)) throw new RuntimeException("Invalid token: "+token);
		// else
		token=token.substring(TOKEN_HDR.length());
		String[] fields=token.split(DELIMITER_REGEX);
		return new AccessToken(fields[0],fields[1],ByteUtils.hexStringToBytes(fields[2]));
	}
	
	@Override
	public String toString() {
		return getToken();
	}

}
