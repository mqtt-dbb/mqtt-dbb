package it.unipr.netsec.mqtt;



/** MQTT Client.
 */
public interface Client {

	/** Gets the client identifier.
	 * @return the identifier */
	public String getId();

	/** Gets the broker URL.
	 * @return the URL */
	public String getBrokerUrl();

	/** Connect the client. 
	 * @throws MqttException 
	 * @throws MqttSecurityException */
	public void connect() throws Exception;
	
	/** Disconnect the client. 
	 * @throws MqttException 
	 * @throws MqttSecurityException */
	public void disconnect() throws Exception;
  
	/** Publish / send a message to an MQTT server
	 * @param topic_name the name of the topic to publish to
	 * @param qos the quality of service to delivery the message at (0,1,2)
	 * @param payload the set of bytes to send to the MQTT server
	 * @throws MqttException */
	public void publish(String topic_name, int qos, byte[] payload) throws Exception;

	/** Subscribe to a topic on an MQTT server
	 * Once subscribed this method waits for the messages to arrive from the server
	 * that match the subscription. It continues listening for messages until the enter key is
	 * pressed.
	 * @param topic_name to subscribe to (can be wild carded)
	 * @param qos the maximum quality of service to receive messages at for this subscription
	 * @throws MqttException */
	public void subscribe(String topic_name, int qos) throws Exception;
	
}
