package it.unipr.netsec.mqtt;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jmqtt.common.bean.Message;
import org.jmqtt.common.bean.MessageHeader;
import org.jmqtt.remoting.util.MessageUtil;
import org.jmqtt.remoting.util.NettyUtil;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import it.unipr.netsec.util.SysUtils;


public class JmqttServerChannelInboundHandler extends ChannelInboundHandlerAdapter {
	

	HashMap<String,ArrayList<Channel>> subscribers;

	public JmqttServerChannelInboundHandler(HashMap<String,ArrayList<Channel>> subscribers) {
		this.subscribers=subscribers;
	}	
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		MqttMessage mqttMessage = (MqttMessage) msg;
		log("received MQTT message: " + mqttMessage);
		String clientId;
		MqttQoS qos;
		String topic_name;
		switch (mqttMessage.fixedHeader().messageType()) {
		case CONNECT:
			MqttFixedHeader connackFixedHeader = new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
			MqttConnAckVariableHeader mqttConnAckVariableHeader = new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED, false);
			MqttConnAckMessage connack = new MqttConnAckMessage(connackFixedHeader, mqttConnAckVariableHeader);
			log("sending CONNACK");
			ctx.writeAndFlush(connack);
			break;
		case PINGREQ:
			MqttFixedHeader pingreqFixedHeader = new MqttFixedHeader(MqttMessageType.PINGRESP, false,  MqttQoS.AT_MOST_ONCE, false, 0);
			MqttMessage pingResp = new MqttMessage(pingreqFixedHeader);
			log("sending PINGRESP");
			ctx.writeAndFlush(pingResp);
			break;
		case PUBLISH:
			MqttPublishMessage publishMessage = (MqttPublishMessage) mqttMessage;
			qos = publishMessage.fixedHeader().qosLevel();
			clientId = NettyUtil.getClientId(ctx.channel());
			topic_name = publishMessage.variableHeader().topicName();
			byte[] payload = MessageUtil.readBytesFromByteBuf(((MqttPublishMessage)mqttMessage).payload());
			log("topic: "+topic_name);
						
			if (subscribers.containsKey(topic_name)) {
				for (Channel channel : subscribers.get(topic_name)) {
					Message innerMsg = new Message();
					innerMsg.setPayload(payload);
					innerMsg.setClientId(NettyUtil.getClientId(channel));
					innerMsg.setType(Message.Type.PUBLISH);
					Map<String,Object> headers = new HashMap<>();
					headers.put(MessageHeader.TOPIC,topic_name);
					headers.put(MessageHeader.QOS,qos.value());
					headers.put(MessageHeader.RETAIN,false);
					headers.put(MessageHeader.DUP,false);
					innerMsg.setHeaders(headers);
					//innerMsg.setMsgId(publishMessage.variableHeader().packetId());					
					innerMsg.setMsgId(JmqttClient.pickMessageId());										
					MqttMessage rePublishMessasge = MessageUtil.getPubMessage(innerMsg,false,qos.value(),innerMsg.getMsgId());
					log("sending MQTT message: " + rePublishMessasge);
					channel.writeAndFlush(rePublishMessasge);
				}	
			}
			else {
				log("no subscriber found");				
			}
			break;
		case SUBSCRIBE:
			MqttSubscribeMessage subscribeMessage = (MqttSubscribeMessage) mqttMessage;
			clientId = NettyUtil.getClientId(ctx.channel());
			int messageId = subscribeMessage.variableHeader().messageId();
			List<MqttTopicSubscription> topics=subscribeMessage.payload().topicSubscriptions();
			topic_name=topics.get(0).topicName();
			log("topic: "+topic_name);
			if (!subscribers.containsKey(topic_name)) subscribers.put(topic_name,new ArrayList<Channel>());
			List<Channel> list=subscribers.get(topic_name);
			list.add(ctx.channel());
			qos = subscribeMessage.fixedHeader().qosLevel();
			MqttMessage subAckMessage = MessageUtil.getSubAckMessage(messageId,Arrays.asList(new Integer[]{qos.value()}));
			log("ack");
			ctx.writeAndFlush(subAckMessage);		
			break;
		case DISCONNECT:
			log("client disconnected");
			ctx.close();
			break;
		case PUBACK:
			log("received PUBACK: silently discarded");
			break;
		default:
			log("unexpected message type: " + mqttMessage.fixedHeader().messageType());
			ReferenceCountUtil.release(msg);
			ctx.close();
		}
	}
	
	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		log("channel heartBeat lost");
		if (evt instanceof IdleStateEvent && IdleState.READER_IDLE == ((IdleStateEvent) evt).state()) {
			ctx.close();
		}
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace();
		ctx.close();
	}

	/** Prints a log message.
	 * @param message the message */
	private void log(String message) {
		SysUtils.log(this,message);
	}

}
