package it.unipr.netsec.mqtt.auth;


import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.zoolu.net.InetAddrUtils;
import org.zoolu.util.ByteUtils;
import org.zoolu.util.Flags;
import org.zoolu.util.LoggerLevel;
import org.zoolu.util.LoggerWriter;
import org.zoolu.util.SystemUtils;

import it.unipr.netsec.coap.CoapContentFormats;
import it.unipr.netsec.mjcoap.coap.message.CoapRequest;
import it.unipr.netsec.mjcoap.coap.message.CoapResponseCode;
import it.unipr.netsec.mjcoap.coap.option.CoapOption;
import it.unipr.netsec.mjcoap.coap.option.CoapOptionNumber;
import it.unipr.netsec.mjcoap.coap.option.ContentFormatOption;
import it.unipr.netsec.mjcoap.coap.option.UriPathOption;
import it.unipr.netsec.mjcoap.coap.provider.CoapProvider;
import it.unipr.netsec.mjcoap.coap.server.AbstractCoapServer;

import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;


/** Authorization Server.
 */
public class AuthServer extends AbstractCoapServer {

	/** Debug mode */
	public static boolean DEBUG=false;
	
	/** Logs a debug message. */
	private void debug(String str) {
		SystemUtils.log(LoggerLevel.INFO,getClass(),str);
	}

	
	/** Signature data delimiter */
	//static final String DELIMITER=AccessToken.DELIMITER;
	static final String DELIMITER=""; // none

	/** Signing algorithm */
	public static final String SIGN_ALGO="RSA";
	
	/** Hash algorithm */
	public static final String HASH_ALGO="SHA256";

	/** AS key modulus */
	public static final BigInteger MODULS=new BigInteger("18486084451098360693385384206647442093586142325140646323653361349888045595965341824296528455659740163204555871650866404610182378596307553669521271729254097140464859738945202786752200948428837163284632231648403530908598353074157762575780141469309710302414817983692568889312897632022943433232550830568602149050764492424433840452195212259379837727821967309556009807508579602382806654629563124293256726334428514988737762287235303668049268554088348995201402571610675535627197797941134708303092828043328920284399218836740643678915840258768012591723317452466065254903289398273712109287898683673609874541910297333780565030059");

	/** AS key public exponent */
	public static final BigInteger PUB_EXPONENT=new BigInteger("65537");

	/** AS key private exponent */
	private static final BigInteger PRI_EXPONENT=new BigInteger("7726487713573802525492659782847654513137794079226278042899627295972249949870800360882104879036751797465541517558944299480936199620215397563445483851987857681486998763586782958248261110202827526079812107501305667312332363205629330492329060456342700834700195221162196118468329217638470887027267533621239148371012507532613673698233068200953309648279553792155801460027564324017363013390177139593562672188303435808614469860552758392758428583910708438610566797943648924359494290804411155418669008380773465150879769833126241522295328430165980799118308471790475529323747233188186206180843593106065172051375084479250584942593");

	/** Signature generator */
	private static Signature SIGNER;
	
	/** Verify generator */
	private static Signature VERIFIER;

	static {
		try {
			String hash_sig_algo=HASH_ALGO+"with"+SIGN_ALGO; 
			KeyFactory kf=KeyFactory.getInstance(SIGN_ALGO);
			PublicKey pub_key=kf.generatePublic(new RSAPublicKeySpec(MODULS,PUB_EXPONENT));
			PrivateKey pri_key=kf.generatePrivate(new RSAPrivateKeySpec(MODULS,PRI_EXPONENT));
			SIGNER=Signature.getInstance(hash_sig_algo);
			SIGNER.initSign(pri_key);	
			VERIFIER=Signature.getInstance(hash_sig_algo);
			VERIFIER.initVerify(pub_key);
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	};
	

	/** Signs some data.
	 * @param data data to be signed
	 * @return signature 
	 * @throws SignatureException */
	public static synchronized byte[] sign(byte[] data) throws SignatureException {
		SIGNER.update(data);
		return SIGNER.sign();
	}

	
	/** Verifies a signature.
	 * @param data data that has been signed
	 * @param signature the signature to be verified
	 * @return true if it is a valid signature 
	 * @throws SignatureException */
	public static synchronized boolean verify(byte[] data, byte[] signature) throws SignatureException {
		VERIFIER.update(data);
		return VERIFIER.verify(signature);
	}

	
	/** Creates a new server.
	 * @param port the server port
	 * @throws SocketException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeySpecException 
	 * @throws InvalidKeyException */
	public AuthServer(int port) throws SocketException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
		super(port);
	}

	@Override
	protected void handlePostRequest(CoapRequest req) {
		if (DEBUG) debug("handlePostRequest(): received CoAP request from: "+InetAddrUtils.toString(req.getRemoteSoAddress())+": "+req.toString());	
		try {
			CoapOption[] uri_path_opts=req.getOptions(CoapOptionNumber.UriPath);			
			String key_type=new UriPathOption(uri_path_opts[0]).getPath();
			if (key_type.equals("token")) {
				CoapOption opt=req.getOption(CoapOptionNumber.ContentFormat);
				if (opt==null || new ContentFormatOption(opt).getContentFormatIdentifier()!=CoapContentFormats.FORMAT_APP_JSON) {
					throw new IOException("Content format is not 'application/ace+cbor'");
				}
				String cbor=new String(req.getPayload());
				JSONObject json=(JSONObject)JSONValue.parseWithException(cbor);
				String client_id=(String)json.get("client_id");
				String audience=(String)json.get("audience");
				json=new JSONObject();
				AccessToken access_token=getAccessToken(client_id,audience);
				json.put("access_token",access_token.getToken());
				json.put("expires_in","3600");
				cbor=json.toString();
				respond(req,CoapResponseCode._2_05_Content,CoapContentFormats.FORMAT_APP_JSON,cbor.getBytes());
				return;
			}
			respond(req,CoapResponseCode._4_04_Not_Found);
			return;
		}
		catch (Exception e) {
			e.printStackTrace();
			respond(req,CoapResponseCode._4_00_Bad_Request);
			return;
		}
	}


	/** Gets an access token.
	 * @param client_id client id
	 * @param audience audience
	 * @return the access token
	 * @throws SignatureException */
	public static AccessToken getAccessToken(String client_id, String audience) throws SignatureException {
		String access_token=client_id+DELIMITER+audience;
		byte[] signature=sign(access_token.getBytes());
		return new AccessToken(client_id,audience,signature);
	}

	
	/** Verifies an access token.
	 * @param client_id client id
	 * @param audience audience
	 * @param audience the signature
	 * @return true if it is a valid token 
	 * @throws SignatureException */
	public static boolean verifyAccessToken(String token) throws SignatureException {
    	return verifyAccessToken(AccessToken.parseToken(token));
	}

	/** Verifies an access token.
	 * @param client_id client id
	 * @param audience audience
	 * @param audience the signature
	 * @return true if it is a valid token 
	 * @throws SignatureException */
	public static boolean verifyAccessToken(AccessToken token) throws SignatureException {
    	byte[] data=(token.getClientId()+DELIMITER+token.getAudience()).getBytes();
    	return AuthServer.verify(data,token.getSignature());
	}
	
	
	/** The main method. 
	 * @throws Exception */
	public static void main(String[] args) throws Exception {

		Flags flags=new Flags(args);
		boolean help=flags.getBoolean("-h","print this help message");
		boolean verbose=flags.getBoolean("-v","verbose mode");
		boolean exit=flags.getBoolean("-x","prompt to exit");
		int port=flags.getInteger(null,"<port>",CoapProvider.DEFAUL_PORT,"server port");

		if (help) {
			System.out.println(flags.toUsageString(AuthServer.class.getName()));
			return;
		}
		// else
		if (verbose) {
			SystemUtils.setDefaultLogger(new LoggerWriter(System.out,LoggerLevel.INFO));
			AuthServer.DEBUG=true;
		}
		AuthServer as=new AuthServer(port);
		
		if (exit) {
			System.out.println("Press <Enter> to exit");
			SystemUtils.readLine();
			as.halt();
			SystemUtils.exitAfter(2000);
		}
	}

}
