package it.unipr.netsec.mqtt.auth;


import org.zoolu.util.ByteUtils;

import it.unipr.netsec.util.SysUtils;


/** Payload with access token.
 */
public class AccessTokenPayload {

	private static final byte[] ACCESS_TOKEN_HDR=AccessToken.TOKEN_HDR.getBytes();

	private String token;
	private byte[] payload;

	
	/** Creates a new payload with access token.
	 * @param token access token
	 * @param payload payload */
	public AccessTokenPayload(AccessToken access_token, byte[] payload) {
		this.token=access_token.getToken();
		this.payload=payload;
	}
	
	/** Creates a new payload with access token.
	 * @param token access token
	 * @param payload payload */
	public AccessTokenPayload(String token, byte[] payload) {
		this.token=token;
		this.payload=payload;
	}
	
	/** Gets access token.
	 * @return the token */
	public String getToken() {
		return token;
	}

	
	/** Gets payload.
	 * @return the payload */
	public byte[] getPayload() {
		return payload;
	}

	
	/** Gets the access token and payload as an array of bytes.
	 * @return the byte array containing the payload with access token */
	public byte[] getBytes() {
		/*byte[] token_data=token.getBytes();
		byte[] data=new byte[2+token_data.length+payload.length];
		ByteUtils.intToTwoBytes(token_data.length,data,0);
		System.arraycopy(token_data,0,data,2,token_data.length);
		System.arraycopy(payload,0,data,2+token_data.length,payload.length);*/
		byte[] token_data=token.getBytes();
		byte[] data=new byte[token_data.length+1+payload.length];
		System.arraycopy(token_data,0,data,0,token_data.length);
		data[token_data.length]=AccessToken.DELIMITER;
		System.arraycopy(payload,0,data,token_data.length+1,payload.length);
		return data;
	}

	/** Checks whether an array of bytes contains an encoded access token.
	 * @param buf the array of bytes
	 * @return 'true' if it contains a token */
	public static boolean containsToken(byte[] buf) {
		/*int len=ACCESS_TOKEN_HDR.length;
		return ByteUtils.compare(buf,2,len,ACCESS_TOKEN_HDR,0,len)==0;*/
		int len=ACCESS_TOKEN_HDR.length;
		return ByteUtils.compare(buf,0,len,ACCESS_TOKEN_HDR,0,len)==0;
	}
	
	/** Gets token.
	 * @param token the token */
	public static AccessTokenPayload parseTokenPayload(byte[] data) {
		if (!containsToken(data)) throw new RuntimeException("Invalid payload with access token");
		// else
		/*int len=ByteUtils.twoBytesToInt(data);
		return new AccessTokenPayload(new String(data,2,len),ByteUtils.copy(data,2+len,data.length-2-len));*/
		int len=data.length-1;
		while (data[len]!=AccessToken.DELIMITER) len--;
		return new AccessTokenPayload(new String(data,0,len),ByteUtils.copy(data,len+1,data.length-1-len));
	}
	
	@Override
	public String toString() {
		return new String(getBytes());
	}

}
