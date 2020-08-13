package test;


import java.util.Arrays;

import org.zoolu.util.SystemUtils;

import it.unipr.netsec.mqtt.Bridge;
import it.unipr.netsec.mqtt.Client;
import it.unipr.netsec.mqtt.ClientFactory;
import it.unipr.netsec.mqtt.ClientListener;
import it.unipr.netsec.mqtt.JmqttClient;
import it.unipr.netsec.mqtt.auth.AuthServer;
import it.unipr.netsec.mqtt.auth.AuthType;
import it.unipr.netsec.util.SysUtils;

import static java.lang.System.out;


/** X-Broker delay evaluation.
 */
public class DelayEvaluation {

	// CONFIG:
	private static int qos=0;
	private static int num_max=10; // 10 // maximum number of brokers
	private static int count_max=50; // 50 // number of published messages
	private static long time=1000; // inter-time
	private static String topic_name="test";
	private static boolean auth=true;
	//private static AuthType auth_type=null;
	private static AuthType auth_type=AuthType.TOPIC;
	//private static AuthType auth_type=AuthType.PAYLOAD;
	private static boolean vebose=false;
	//private static String mqtt_provider="jmqtt";
	private static String mqtt_provider="paho";
	private static boolean straight_mode=false;

	// ATTRIBUTES:
	private static int num=1; // 1 // current number of brokers
	private static int pub_send_count=0;
	private static int pub_recv_count=0;
	private static String auth_server=null;
	private static long[][] pub_send_times=new long[num_max+1][count_max];
	private static long[][] pub_recv_times=new long[num_max+1][count_max];
	private static double[] average_times=new double[num_max+1];

	
	/** No constructor is available. */
	private DelayEvaluation() {}

	
	public static String getTimestamp() {
		return String.valueOf(System.currentTimeMillis());
	}

	
	private static ClientListener client_listener=new ClientListener() {
		@Override
		public void onSubscribing(Client client, String topic, int qos) {
			out.println(getTimestamp()+"\tsubscribing\ttopic="+topic+", qos="+qos);
		}
		@Override
		public void onPublishing(Client client, String topic, int qos, byte[] payload) {
			out.println(getTimestamp()+"\tpublishing\ttopic="+topic+", qos="+qos+", msg="+new String(payload));
			pub_send_times[num][pub_send_count++]=System.currentTimeMillis();
		}
		@Override
		public void onMessageArrived(Client client, String topic, int qos, byte[] payload) {
			pub_recv_times[num][pub_recv_count++]=System.currentTimeMillis();
			out.println(getTimestamp()+"\treceived\ttopic="+topic+", qos="+qos+", msg="+new String(payload));
			if (pub_recv_count==count_max) processSingleRun();
		}
		@Override
		public void onConnectionLost(Client client, Throwable cause) {
			out.println(getTimestamp()+"\tconnection to broker lost!" + cause);
		}			
	};
	
	
	private static void processSingleRun() {
		pub_send_count=0;
		pub_recv_count=0;
		// RESULTS:
		average_times[num]=0;
		for (int i=0; i<count_max; i++) {
			average_times[num]+=pub_recv_times[num][i]-pub_send_times[num][i];
		}
		average_times[num]/=count_max;
		out.println("average time: "+average_times[num]);
		// NEW RUN:
		if (num<num_max) {
			num++;
			startSingleRun(false);
		}
		else finish();
	}

	
	private static void startSingleRun(boolean first) {
		out.println("n: "+num);
		try {
			// SUBSCRIBERS:
			if (straight_mode) {
				startSubscriber(auth_server,auth_type,topic_name,qos,num);
				SystemUtils.sleep(num*1500 + num_max*2*JmqttClient.SLEEP_TIME);
			}
			else
			if (first) {
				startSubscriber(auth_server,auth_type,topic_name,qos,num_max);
				SystemUtils.sleep(num*1500 + num_max*2*JmqttClient.SLEEP_TIME);
			}
			// PUBLISHERS:
			startPublisher(auth_server,auth_type,topic_name,qos,num,count_max,time);
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}	
	}

	
	private static void finish() {
		out.println("results:");
		long[][] unsorted_times=new long[num_max+1][count_max];
		long[][] sorted_times=new long[num_max+1][count_max];
		for (int n=1; n<=num_max; n++) {
			for (int i=0; i<count_max; i++) {
				sorted_times[n][i]=unsorted_times[n][i]=pub_recv_times[n][i]-pub_send_times[n][i];
			}
			Arrays.sort(sorted_times[n]);
		}
		for (int n=1; n<=num_max; n++) {
			StringBuffer sb=new StringBuffer();
			sb.append(n);
			for (int i=0; i<count_max; i++) {
				sb.append('\t').append(unsorted_times[n][i]);
			}	
			sb.append('\t').append(average_times[n]);
			out.println(sb.toString());
		}
		out.println("sorted values:");
		for (int n=1; n<=num_max; n++) {
			StringBuffer sb=new StringBuffer();
			sb.append(n);
			for (int i=0; i<count_max; i++) {
				sb.append('\t').append(sorted_times[n][i]);
			}	
			sb.append('\t').append(average_times[n]);
			out.println(sb.toString());
		}
		//for (int i=1; i<=num_max; i++) out.println(i+"\t"+average_times[i]);
		SystemUtils.exitAfter(2000);		
	}

		
	public static void startAS(int port) {
		String[] args=new String[] { String.valueOf(port) };
		SysUtils.run(()->AuthServer.main(args));
	}

	
	public static void startSubscriber(String auth_server, AuthType auth_type, String topic_name, int qos, int num) throws Exception {
		StringBuffer topic=new StringBuffer();
		topic.append(topic_name);
		for (int i=num; i>1; i--) topic.append("@127.0.0.1:").append(8000+i);
		SysUtils.run(()->MqttClient.main(null,auth_server,auth_type,"127.0.0.1:8001",null,null,"subscribe",topic.toString(),qos,null,-1,-1,client_listener));
	}

	
	public static void startPublisher(String auth_server, AuthType auth_type, String topic_name, int qos, int num, int count, long time) throws Exception {
		SysUtils.run(()->MqttClient.main(null,auth_server,auth_type,"127.0.0.1:"+String.valueOf(8000+num),null,null,"publish",topic_name,qos,"hello".getBytes(),count,1000,client_listener));
	}

	
	public static void startAll() throws Exception {
		// CONFIG:
		SysUtils.DEBUG=vebose;
		Bridge.DEFAULT_QOS=qos;
		ClientFactory.setProvider(mqtt_provider);
		// AS:
		if (auth) {
			auth_server="127.0.0.1:9000";
			startAS(9000);
		}
		// BROKERS:
		//startJmqttBrokers(8001,8501,2,num);
		//startMoquetteBrokers(8001,2,num);

		// SUBSCRIBERS AND PUBLISHERS: 
		SystemUtils.sleep(3000);
		startSingleRun(true);
	}


	public static void main(String[] args) throws Exception {
		startAll();
	}

}
