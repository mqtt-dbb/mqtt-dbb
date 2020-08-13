package it.unipr.netsec.mqtt;


import org.jmqtt.remoting.util.MessageUtil;
import org.jmqtt.remoting.util.NettyUtil;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import it.unipr.netsec.mqtt.auth.AccessTokenPayload;
import it.unipr.netsec.util.SysUtils;


public class JmqttClientChannelInboundHandler extends ChannelInboundHandlerAdapter {

	JmqttClient client;
	ClientListener listener;
	
	JmqttClientChannelInboundHandler(JmqttClient client, ClientListener listener) {
		this.client=client;
		this.listener=listener;
	}
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		MqttMessage mqttMessage = (MqttMessage) msg;
		log("received MQTT message: " + mqttMessage);
		switch (mqttMessage.fixedHeader().messageType()) {
		case CONNACK:
			log("connected");
			break;
		case PUBLISH:
			log("received: PUBLISH");
			MqttPublishMessage publishMessage = (MqttPublishMessage) mqttMessage;
			int qos = publishMessage.fixedHeader().qosLevel().value();
			String clientId = NettyUtil.getClientId(ctx.channel());
			String topic_name = publishMessage.variableHeader().topicName();
			byte[] payload = MessageUtil.readBytesFromByteBuf(((MqttPublishMessage)mqttMessage).payload());
			listener.onMessageArrived(client,topic_name,qos,payload);
			break;
		case SUBACK:
			log("received: SUBACK");
			break;
		case PINGRESP:
			//log("received: PINGRESP");
			break;
		default:
			log("unexpected message type: " + mqttMessage.fixedHeader().messageType());
			ReferenceCountUtil.release(msg);
			ctx.close();
		}
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		log("channel is active");
		client.setActiveChannel(ctx.channel());
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent) {
			MqttFixedHeader pingreqFixedHeader = new MqttFixedHeader(MqttMessageType.PINGREQ, false, MqttQoS.AT_MOST_ONCE, false, 0);
			MqttMessage pingreqMessage = new MqttMessage(pingreqFixedHeader);
			ctx.writeAndFlush(pingreqMessage);
			log("sent PINGREQ");
		} else {
			super.userEventTriggered(ctx, evt);
		}
	}

	/** Prints a log message.
	 * @param message the message */
	private void log(String message) {
		SysUtils.log(null,"JmqttClientChannelInboundHandler: "+message);
	}
}
