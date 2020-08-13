package test;


import java.util.Date;
import java.util.Random;

import org.zoolu.util.DateFormat;
import org.zoolu.util.Flags;
import org.zoolu.util.SystemUtils;

import it.unipr.netsec.mqtt.Client;
import it.unipr.netsec.mqtt.ClientFactory;
import it.unipr.netsec.mqtt.ClientListener;
import it.unipr.netsec.mqtt.auth.AccessTokenPayload;
import it.unipr.netsec.mqtt.auth.AuthClient;
import it.unipr.netsec.mqtt.auth.AuthType;
import it.unipr.netsec.util.SysUtils;


/** MQTT client
 */
public class MqttClient {
	
	
	public static ClientListener DEFAULT_LISTENER=new ClientListener() {
		@Override
		public void onSubscribing(Client client, String topic, int qos) {
			System.out.println(getTimestamp()+": Subscribing: topic="+topic+", qos="+qos);
		}
		@Override
		public void onPublishing(Client client, String topic, int qos, byte[] payload) {
			System.out.println(getTimestamp()+": Publishing: topic="+topic+", qos="+qos+", msg="+new String(payload));
		}
		@Override
		public void onMessageArrived(Client client, String topic, int qos, byte[] payload) {
			System.out.println(getTimestamp()+": Received: topic="+topic+", qos="+qos+", msg="+new String(payload));
		}
		@Override
		public void onConnectionLost(Client client, Throwable cause) {
			System.out.println(getTimestamp()+": Connection to broker lost!" + cause);
		}			
	};

	
	private static String getTimestamp() {
		return DateFormat.formatHHmmssSSS(new Date(System.currentTimeMillis()));
	}

	
	/** Main method. 
	 * @throws Exception */
	public static void main(String[] args) throws Exception {
		Flags flags=new Flags(args);
		String action=flags.getString("-a","action","publish","action: 'publish' or 'subscribe'");
		String topic=flags.getString("-t","topic","test","topic name");
		String message=flags.getString("-m","message","hello","text payload");
		int qos=flags.getInteger("-q","qos",2,"QoS mode: 0, 1, or 2");
		String broker=flags.getString("-b","broker",null,"broker address");
		String client_id=flags.getString("-i","id",null,"client ID");
		String auth_server=flags.getString("-s","as",null,"AS address");
		String password=flags.getString("-w","password",null,"user's password");
		String username=flags.getString("-u","username",null,"user's name");
		String auth_type=flags.getString("--auth","type",null,"authentication type (USERNAME, TOPIC, PAYLOAD)");
		int count=flags.getInteger("-c","num",1,"re-publishes num times");
		int time=flags.getInteger("--time","msecs",1000,"publish inter time");
		String mqtt_provider=flags.getString("--provider","name","paho","MQTT provider (default is 'paho')");
		boolean help=flags.getBoolean("-h","prints this help message");
		boolean verbose=flags.getBoolean("-v","verbose");
		
		if (help) {
			printHelp(flags);
			return;
		}	
		if (broker==null) {
			System.out.println("You have to specify a broker.");
			printHelp(flags);
			return;
		}
		if (!action.equals("publish") && !action.equals("subscribe")) {
			System.out.println("Invalid action: "+action);
			System.out.println(flags.toUsageString(MqttClient.class.getName()));
			return;
		}
		if (qos<0 || qos>2) {
			System.out.println("Invalid QoS: "+qos);
			printHelp(flags);
			return;
		}

		SysUtils.DEBUG=verbose;
		ClientFactory.setProvider(mqtt_provider);

		main(client_id,auth_server,AuthType.valueOf(auth_type.toUpperCase()),broker,username,password,action,topic,qos,message.getBytes(),count,time,DEFAULT_LISTENER);
	}

	
	public static void main(String client_id, String auth_server, AuthType auth_type, String broker, String username, String password, String action, String topic, int qos, byte[] payload, int count, long time, ClientListener listener) throws Exception {
		if (client_id==null) {
			client_id="MyClient"+new Random().nextInt(100000000);
		}		
		String access_token=null;
		if (auth_server!=null) {
			AuthClient auth_client=new AuthClient(client_id);
			String audience=topic.split("@")[0];
			access_token=auth_client.requestToken(auth_server,audience);
			log("access-token: "+access_token);
			auth_client.close();
			if (auth_type==null) auth_type=AuthType.USERNAME;
			switch (auth_type) {
				case USERNAME :
					username=access_token;
					break;
				case TOPIC :
					int index=topic.indexOf("@");
					if (index>0) topic=access_token+topic.substring(index);
					else topic=access_token;
					break;
				case PAYLOAD :
					//if (payload!=null) payload=new AccessTokenPayload(access_token,payload).getBytes();
					break;
			}		
		}
				
		final ClientListener this_listener=new ClientListener(){
			@Override
			public void onConnectionLost(Client client, Throwable cause) {
				listener.onConnectionLost(client,cause);
				System.exit(1);
			}
			@Override
			public void onMessageArrived(Client client, String topic, int qos, byte[] payload) {
				if (AccessTokenPayload.containsToken(payload)) {
					payload=AccessTokenPayload.parseTokenPayload(payload).getPayload();
				}
				listener.onMessageArrived(client,topic,qos,payload);
			}
			@Override
			public void onSubscribing(Client client, String topic, int qos) {
				listener.onSubscribing(client,topic,qos);
			}
			@Override
			public void onPublishing(Client client, String topic, int qos, byte[] payload) {
				listener.onPublishing(client,topic,qos,payload);
			}
		};
		
		Client mqtt_client=ClientFactory.createClient(client_id,"tcp://"+broker,username,password,this_listener);
		mqtt_client.connect();
		if (action.equals("publish")) {
			for (int i=0; i<count; i++) {
				if (payload==null || count>1) payload=String.valueOf(i).getBytes();
				if (auth_type==AuthType.PAYLOAD) payload=new AccessTokenPayload(access_token,payload).getBytes();
				final byte[] final_payload=payload;
				final String final_topic=topic;
				SysUtils.run(()->mqtt_client.publish(final_topic,qos,final_payload));
				//mqtt_client.publish(topic,qos,message.getBytes());
				SystemUtils.sleep(time);
			}
			mqtt_client.disconnect();
		}
		else {
			mqtt_client.subscribe(topic,qos);
			System.out.println("Press <Enter> to exit");
			SystemUtils.readLine();
			mqtt_client.disconnect();
			SystemUtils.exitAfter(1000); // jmqtt didn't exit
		}		
	}

	
	/** Prints help message. */
	private static void printHelp(Flags flags) {
		System.out.println(flags.toUsageString(MqttClient.class.getName()));		
	}

	
	/** Prints a log message. */
	private static void log(String message) {
		SysUtils.log(null,MqttClient.class.getSimpleName()+":"+message);
	}
}
