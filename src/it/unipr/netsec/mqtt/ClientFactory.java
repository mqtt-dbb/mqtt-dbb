package it.unipr.netsec.mqtt;



public class ClientFactory {

	/** Provider name */
	protected static String PROVIDER_NAME="paho";
	
	
	/** No default constructor is available. */
	private ClientFactory() {}

	/** Gets the provider.
	 * @return the provider name */
	public static String getProvider() {
		return PROVIDER_NAME;
	}

	
	/** Sets the provider.
	 * @param provider_type provider name */
	public static void setProvider(String provider_name) {
		PROVIDER_NAME=provider_name;
	}

	
	/** Creates a new client
	 * @param client_id the client id to connect with
	 * @param broker_url the url of the server to connect to
	 * @param listener client listener
	 * @throws Exception */
	public static Client createClient(String client_id, String broker_url, final ClientListener listener) throws Exception {
		return createClient(client_id,broker_url,null,null,listener);
	}

	/** Creates a new client
	 * @param client_id the client id to connect with
	 * @param broker_url the url of the server to connect to
	 * @param username the username to connect with
	 * @param password the password for the user
	 * @param listener client listener
	 * @throws Exception */
	public static Client createClient(String client_id, String broker_url, String username, String password, final ClientListener listener) throws Exception {
		if (PROVIDER_NAME.equalsIgnoreCase("paho")) return new PahoClient(client_id, broker_url, username, password, listener);
		if (PROVIDER_NAME.equalsIgnoreCase("jmqtt")) return new JmqttClient(client_id, broker_url, username, password, listener);
		// otherwise
		throw new RuntimeException("Provider '"+PROVIDER_NAME+"' not supported");
	}

}
