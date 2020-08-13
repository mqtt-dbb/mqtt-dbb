package it.unipr.netsec.mqtt;


import java.util.HashMap;

import it.unipr.netsec.util.SysUtils;


/** MQTT dynamic bridge.
 */
public class Bridge {
	
    /** Whether the subscribe request is relayed by the Bridge ('external bridging' mode)  or is forwarded through the broker engine */
    public static boolean EXTERNAL_BRIDGING=false;

    /** Whether the locally register topic is the actual topic name without the addresses of the next brokers ('complete bridging' mode) or the mangled topic including the broker addresses */
    public static boolean COMPLETE_BRIDGING=true;

    /** Unique client id */
	public static int DEFAULT_QOS=2;

	/** Unique client id */
	private String id;
	
	/** Client connected to the local broker */
	private Client stub;

	/** Clients connected to remote brokers */
	private HashMap<String,Client> clients=new HashMap<>();

		
	/** Creates a new bridge.
	 * @param port TCP port of the broker */
	public Bridge(int port) {
		this("tcp://127.0.0.1:"+port);
	}
	
	/** Creates a new bridge.
	 * @param broker_url URL of the local broker (e.g. tcp://127.0.0.1:1883) */
	public Bridge(String broker_url) {
		//id="MyClient"+String.valueOf(new Random().nextInt(10000000));	
		id=broker_url.split(":")[2]; // URL port
		log("Bridge("+broker_url+")");
		try {
			stub=ClientFactory.createClient(id,broker_url,null);
			stub.connect();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void processSubscribe(String topic_name) {
		try {
			String next_topic=getNextTopic(topic_name);
			String next_broker=getNextBroker(topic_name);
	    	log("processSubscribe(): subscribe topic '"+next_topic+"' to broker "+next_broker);
	    	subscribe("tcp://"+next_broker,next_topic,Bridge.DEFAULT_QOS);
		}
		catch (Exception e) {
			log("processRequest(): "+e.getMessage());
			e.printStackTrace();
		}
	}
	
	/** Subscribes to a topic on a given broker.
	 * @param broker_url broker URL
	 * @param topic topic name
	 * @param qos QoS type 
	 * @throws Exception */
	public void subscribe(String broker_url, String topic, int qos) {
		try {
			if (!clients.containsKey(broker_url)) {
				Client client=ClientFactory.createClient(id,broker_url,new ClientListener() {
					@Override
					public void onConnectionLost(Client client, Throwable cause) {
						log("connectionLost(): "+client+": "+cause.toString());
						cause.printStackTrace();
					}
					@Override
					public void onMessageArrived(Client client, String topic, int qos, byte[] payload) {
						log("messageArrived(): "+client+": topic="+topic+", msg="+new String(payload));
						try {
							if (!COMPLETE_BRIDGING) topic=topic+'@'+client.getBrokerUrl().substring(6); 
							log("messageArrived(): "+client+": re-published to topic: "+topic);
							stub.publish(topic,DEFAULT_QOS,payload);
						}
						catch (Exception e) {
							log("messageArrived(): "+client+": error: "+e.getMessage());
							e.printStackTrace();
						}
					}
					@Override
					public void onSubscribing(Client client, String topic, int qos) {
					}
					@Override
					public void onPublishing(Client client, String topic, int qos, byte[] payload) {
					}
				});
				clients.put(broker_url,client);
				try {
					client.connect();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
			Client client=clients.get(broker_url);
			client.subscribe(topic,qos);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	public static String getNextBroker(String topic_name) {
		int index=topic_name.indexOf('@');
		while (topic_name.indexOf('@',index+1)>0) index=topic_name.indexOf('@',index+1);
		return topic_name.substring(index+1);
	}

	
	public static String getNextTopic(String topic_name) {
		int index=topic_name.indexOf('@');
		while (topic_name.indexOf('@',index+1)>0) index=topic_name.indexOf('@',index+1);
		return topic_name.substring(0,index);
	}

	
    public void log(String msg) {
    	SysUtils.log(null,this.getClass().getSimpleName()+'['+id+"]: "+msg);
    }

}
