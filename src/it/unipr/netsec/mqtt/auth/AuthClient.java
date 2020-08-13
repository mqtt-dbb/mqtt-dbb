package it.unipr.netsec.mqtt.auth;

import java.io.IOException;
import java.net.SocketException;
import java.net.URISyntaxException;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import it.unipr.netsec.coap.CoapContentFormats;
import it.unipr.netsec.mjcoap.coap.client.CoapClient;
import it.unipr.netsec.mjcoap.coap.message.CoapRequestMethod;
import it.unipr.netsec.mjcoap.coap.message.CoapResponse;
import it.unipr.netsec.mjcoap.coap.option.CoapOption;
import it.unipr.netsec.mjcoap.coap.option.CoapOptionNumber;
import it.unipr.netsec.mjcoap.coap.option.ContentFormatOption;
import it.unipr.netsec.mjcoap.coap.provider.CoapURI;


/** Client for requesting authorization tokens.
 */
public class AuthClient {
	
	/** Client ID */
	String client_id;

	/** CoAP client */
	CoapClient coap_client;
	

	/** Creates a new client. */
	public AuthClient(String client_id) throws SocketException {
		this.client_id=client_id;
		coap_client=new CoapClient();
	}

	/** Requests an authorization token.
	 * @param server_soaddr the socket address of the AS
	 * @param audience the name of the target service or resource where the client intends to use the requested token
	 * @return the authorization token 
	 * @throws ParseException 
	 * @throws URISyntaxException */
	public String requestToken(String server_soaddr, String audience) throws IOException, ParseException, URISyntaxException {
		String resource_uri="coap://"+server_soaddr+"/token/";
		JSONObject json=new JSONObject();
		json.put("client_id",client_id);
		json.put("audience",audience);
		String cbor=json.toString();
		CoapResponse resp=coap_client.request(CoapRequestMethod.POST,new CoapURI(resource_uri),CoapContentFormats.FORMAT_APP_JSON,cbor.getBytes());
		if (!resp.getResponseCode().isSuccess()) {
			throw new IOException("Failure response from AS: "+resp.getCodeAsString());
		}
		CoapOption opt=resp.getOption(CoapOptionNumber.ContentFormat);
		if (opt==null || new ContentFormatOption(opt).getContentFormatIdentifier()!=CoapContentFormats.FORMAT_APP_JSON) {
			throw new IOException("Content format is not 'application/ace+cbor'");
		}
		cbor=new String(resp.getPayload());
		json=(JSONObject)JSONValue.parseWithException(cbor);
		String token=(String)json.get("access_token");
		return token;
	}
	
	/** Closes the client. */
	public void close() {
		coap_client.halt();
	}

}
