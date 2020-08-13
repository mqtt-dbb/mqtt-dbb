package it.unipr.netsec.mqtt;


import java.util.HashMap;
import java.util.Map;

import org.jmqtt.common.bean.Message;
import org.jmqtt.common.bean.MessageHeader;
import org.jmqtt.remoting.util.MessageUtil;
import org.jmqtt.remoting.util.NettyUtil;
import org.zoolu.util.SystemUtils;

import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttMessage;
import it.unipr.netsec.util.SysUtils;


/** MQTT dynamic proxing bridge.
 */
public class ExternalBridge {
	
	/** Unique client id */
	public static int DEFAULT_QOS=2;

	/** Unique client id */
	private String id;
	
	/** Clients connected to remote brokers */
	private HashMap<String,Client> clients=new HashMap<>();

		
	/** Creates a new bridge. */
	public ExternalBridge(String id) {
		log("ExternalBridge()");
		this.id=id;
	}

	/** Subscribes to a topic on a given broker.
	 * @param broker_url broker URL
	 * @param topic topic name
	 * @param qos QoS type 
	 * @param backward_channel channel to be used to publish in the backward direction
	 * @throws Exception */
	public void subscribe(String broker_url, String topic, int qos, final Channel backward_channel) {
		log("subscribe(): "+broker_url+", topic="+topic+", qos=qos");
		try {
			if (!clients.containsKey(broker_url)) {
				Client client=ClientFactory.createClient(id,broker_url,new ClientListener() {
					@Override
					public void onConnectionLost(Client client, Throwable cause) {
						log("connectionLost(): "+client.getBrokerUrl()+", "+cause.toString());
						cause.printStackTrace();
					}
					@Override
					public void onMessageArrived(Client client, String topic, int qos, byte[] payload) {
						log("messageArrived(): "+client.getBrokerUrl()+", topic="+topic+", msg="+new String(payload));
						try {
							topic=topic+'@'+client.getBrokerUrl().substring(6);
							log("messageArrived(): "+client.getBrokerUrl()+", relay backward with topic: "+topic);
							relayBackward(backward_channel,topic,qos,payload);
						}
						catch (Exception e) {
							log("messageArrived(): "+client.getBrokerUrl()+", error: "+e.getMessage());
							e.printStackTrace();
						}
					}
					@Override
					public void onSubscribing(Client client, String topic, int qos) {
						log("onSubscribing(): "+client.getBrokerUrl()+", topic="+topic+", qos="+qos);
					}
					@Override
					public void onPublishing(Client client, String topic, int qos, byte[] payload) {
						log("onPublishing(): "+client.getBrokerUrl()+", topic="+topic+", qos="+qos+", payload="+new String(payload));
					}
				});
				clients.put(broker_url,client);
				client.connect();
			}
			Client client=clients.get(broker_url);
			client.subscribe(topic,qos);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void relayBackward(Channel channel, String topic_name, int qos, byte[] payload) {
		Message innerMsg = new Message();
		innerMsg.setPayload(payload);
		innerMsg.setClientId(NettyUtil.getClientId(channel));
		innerMsg.setType(Message.Type.PUBLISH);
		Map<String,Object> headers = new HashMap<>();
		headers.put(MessageHeader.TOPIC,topic_name);
		headers.put(MessageHeader.QOS,qos);
		headers.put(MessageHeader.RETAIN,false);
		headers.put(MessageHeader.DUP,false);
		innerMsg.setHeaders(headers);
		//innerMsg.setMsgId(publishMessage.variableHeader().packetId());					
		innerMsg.setMsgId(JmqttClient.pickMessageId());										
		MqttMessage rePublishMessasge = MessageUtil.getPubMessage(innerMsg,false,qos,innerMsg.getMsgId());
		log("sending MQTT message: " + rePublishMessasge);
		channel.writeAndFlush(rePublishMessasge);
	}
	
	private void log(String msg) {
    	SysUtils.log(null,this.getClass().getSimpleName()+'['+id+"]: "+msg);
    }

}
