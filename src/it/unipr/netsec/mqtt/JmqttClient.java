package it.unipr.netsec.mqtt;


import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.jmqtt.common.bean.Message;
import org.jmqtt.common.bean.MessageHeader;
import org.jmqtt.remoting.util.MessageUtil;
import org.jmqtt.remoting.util.NettyUtil;
import org.zoolu.util.SystemUtils;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectPayload;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttSubscribePayload;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.timeout.IdleStateHandler;
import it.unipr.netsec.util.SysUtils;


public class JmqttClient implements Client {

	private static final String PROTOCOL_NAME_MQTT_3_1_1 = "MQTT";
	private static final int PROTOCOL_VERSION_MQTT_3_1_1 = 4;
	private static final MqttQoS SUBSCRIBE_RESERVED_CODE = MqttQoS.valueOf(1); // shifted it becomes 0010
	public static final long SLEEP_TIME = 500; 

	private String client_id;
	private String broker_url;
	private String username;
	private String password;
	
	private ClientListener listener;
	Channel channel;
  
	
	public JmqttClient(String client_id, String broker_url, String username, String password, ClientListener listener) throws Exception {
		this.client_id=client_id;
		this.broker_url=broker_url;
		this.username=username;
		this.password=password;
		this.listener=listener;
		
		EventLoopGroup workerGroup = new NioEventLoopGroup();

		try {
			Bootstrap b = new Bootstrap();
			b.group(workerGroup);
			b.channel(NioSocketChannel.class);
			b.handler(new ChannelInitializer<SocketChannel>() {
				protected void initChannel(SocketChannel ch) throws Exception {
					ch.pipeline().addLast("encoder", MqttEncoder.INSTANCE);
					ch.pipeline().addLast("decoder", new MqttDecoder());
                    ch.pipeline().addLast("heartbeatHandler", new IdleStateHandler(0, 20, 0, TimeUnit.SECONDS));
					ch.pipeline().addLast("handler", new JmqttClientChannelInboundHandler(JmqttClient.this,listener) {
					});
				}
			});

			String[] url_split=broker_url.split(":");
			String addr=url_split[1].substring(2);
			int port=Integer.parseInt(url_split[2]);
			log("TCP connecting to '"+addr+":"+port);
			ChannelFuture f = b.connect(addr,port).sync();
			SystemUtils.sleep(SLEEP_TIME);
			//f.channel().closeFuture().sync();
		}
		finally {
			//workerGroup.shutdownGracefully();
		}
	}
	
	@Override
	public String getId() {
		return client_id;
	}

	
	@Override
	public String getBrokerUrl() {
		return broker_url;
	}

	
	public void setActiveChannel(Channel channel) {
		this.channel=channel;
	}

	
	@Override
	public void connect() {
		MqttFixedHeader fixed_hdr = new MqttFixedHeader(MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);
		MqttConnectVariableHeader variable_hdr = new MqttConnectVariableHeader(PROTOCOL_NAME_MQTT_3_1_1, PROTOCOL_VERSION_MQTT_3_1_1, true, true, false, 0, false, false, 20);
		MqttConnectPayload payload = new MqttConnectPayload(client_id,null,null,username,password==null?null:password.getBytes());
		MqttConnectMessage message = new MqttConnectMessage(fixed_hdr, variable_hdr, payload);
		log("connect(): Sending MQTT message: " + message);
		channel.writeAndFlush(message);
		SystemUtils.sleep(SLEEP_TIME);
   }

	
	@Override
	public void disconnect() throws Exception {
		log("disconnect()");
		MqttFixedHeader fixed_hdr = new MqttFixedHeader(MqttMessageType.DISCONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);
		MqttConnectMessage message = new MqttConnectMessage(fixed_hdr, null, null);
		log("disconnect(): Sending MQTT message: " + message);
		channel.writeAndFlush(message);
	}

	
	@Override
	public void subscribe(String topic_name, int qos) {
		List<MqttTopicSubscription> topics=Arrays.asList(new MqttTopicSubscription[] { new MqttTopicSubscription(topic_name,MqttQoS.valueOf(qos)) });
		MqttFixedHeader fixed_hdr = new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, SUBSCRIBE_RESERVED_CODE, false, 0);
		MqttMessageIdVariableHeader variable_hdr = MqttMessageIdVariableHeader.from(1); // or we need to pickMessageId()?
		MqttSubscribePayload payload = new MqttSubscribePayload(topics);
		MqttSubscribeMessage mqtt_msg = new MqttSubscribeMessage(fixed_hdr, variable_hdr, payload);
		
		log("subscribe(): Sending MQTT message: " + mqtt_msg);
		if (listener!=null) listener.onSubscribing(this,topic_name,qos);
		channel.writeAndFlush(mqtt_msg);
   }

	
	@Override
	public void publish(String topic_name, int qos, byte[] payload) throws Exception {
		Message inner_msg = new Message();
		inner_msg.setPayload(payload);
		inner_msg.setClientId(NettyUtil.getClientId(channel));
		inner_msg.setType(Message.Type.PUBLISH);
		Map<String,Object> headers = new HashMap<>();
		headers.put(MessageHeader.TOPIC,topic_name);
		headers.put(MessageHeader.QOS,qos);
		headers.put(MessageHeader.RETAIN,false);
		headers.put(MessageHeader.DUP,false);
		inner_msg.setHeaders(headers);
		inner_msg.setMsgId(pickMessageId());										
		MqttMessage mqtt_msg = MessageUtil.getPubMessage(inner_msg,false,qos,inner_msg.getMsgId());
		log("publish(): sending MQTT message: " + mqtt_msg);
		if (listener!=null) listener.onPublishing(this,topic_name,qos,payload);
		channel.writeAndFlush(mqtt_msg);
	}

	
	/** Gets a new message id.
	 * @return the id */
	static int pickMessageId() {
		return new Random().nextInt(100);
	}

	
	/** Prints a log message.
	 * @param message the message */
	private void log(String message) {
		SysUtils.log(null,getClass().getSimpleName()+"["+client_id+">"+broker_url.split(":")[2]+"]: "+message);
	}

}
