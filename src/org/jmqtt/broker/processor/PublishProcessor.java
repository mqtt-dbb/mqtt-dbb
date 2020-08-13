package org.jmqtt.broker.processor;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.ReferenceCountUtil;
import it.unipr.netsec.mqtt.Bridge;
import it.unipr.netsec.mqtt.ExternalBridge;
import it.unipr.netsec.mqtt.auth.AccessToken;
import it.unipr.netsec.mqtt.auth.AccessTokenPayload;
import it.unipr.netsec.mqtt.auth.AuthServer;
import it.unipr.netsec.util.SysUtils;

import org.jmqtt.broker.BrokerController;
import org.jmqtt.broker.acl.PubSubPermission;
import org.jmqtt.store.FlowMessageStore;
import org.jmqtt.remoting.session.ClientSession;
import org.jmqtt.common.bean.Message;
import org.jmqtt.common.bean.MessageHeader;
import org.jmqtt.common.log.LoggerName;
import org.jmqtt.remoting.netty.RequestProcessor;
import org.jmqtt.remoting.session.ConnectManager;
import org.jmqtt.remoting.util.MessageUtil;
import org.jmqtt.remoting.util.NettyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class PublishProcessor extends AbstractMessageProcessor implements RequestProcessor {
    private Logger log = LoggerFactory.getLogger(LoggerName.MESSAGE_TRACE);

    private FlowMessageStore flowMessageStore;

    private PubSubPermission pubSubPermission;

    private int port;

    
    public PublishProcessor(BrokerController controller){
        super(controller.getMessageDispatcher(),controller.getRetainMessageStore(),controller.getInnerMessageTransfer());
        this.flowMessageStore = controller.getFlowMessageStore();
        this.pubSubPermission = controller.getPubSubPermission();
        this.port = controller.getNettyConfig().getTcpPort();
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, MqttMessage mqttMessage) {
        try{
            MqttPublishMessage publishMessage = (MqttPublishMessage) mqttMessage;
            MqttQoS qos = publishMessage.fixedHeader().qosLevel();
            Message innerMsg = new Message();
            String clientId = NettyUtil.getClientId(ctx.channel());
            ClientSession clientSession = ConnectManager.getInstance().getClient(clientId);
            String topic_name = publishMessage.variableHeader().topicName();

        	log("processRequest(): publish topic: "+topic_name);
        	if (AccessToken.containsToken(topic_name)) {
        		AccessToken access_token=AccessToken.parseToken(topic_name);
        		validAccessToken(access_token);
        		topic_name=access_token.getAudience();
            	log("processRequest(): real topic: "+topic_name);
        	}
        	byte[] payload = MessageUtil.readBytesFromByteBuf(publishMessage.payload().copy());
        	if (payload!=null && AccessTokenPayload.containsToken(payload)) {
        		AccessTokenPayload atp=AccessTokenPayload.parseTokenPayload(payload);
        		validAccessToken(AccessToken.parseToken(atp.getToken()));
        		log("processRequest(): real payload: "+new String(atp.getPayload()));
        	}
            
            if(!this.pubSubPermission.publishVerfy(clientId,topic_name)){
                log.warn("[PubMessage] permission is not allowed");
                clientSession.getCtx().close();
                return;
            }
            innerMsg.setPayload(MessageUtil.readBytesFromByteBuf(((MqttPublishMessage) mqttMessage).payload()));
            innerMsg.setClientId(clientId);
            innerMsg.setType(Message.Type.valueOf(mqttMessage.fixedHeader().messageType().value()));
            Map<String,Object> headers = new HashMap<>();
            //headers.put(MessageHeader.TOPIC,publishMessage.variableHeader().topicName());
            headers.put(MessageHeader.TOPIC,topic_name);
            headers.put(MessageHeader.QOS,publishMessage.fixedHeader().qosLevel().value());
            headers.put(MessageHeader.RETAIN,publishMessage.fixedHeader().isRetain());
            headers.put(MessageHeader.DUP,publishMessage.fixedHeader().isDup());
            innerMsg.setHeaders(headers);
            innerMsg.setMsgId(publishMessage.variableHeader().packetId());
            switch (qos){
                case AT_MOST_ONCE:
                    processMessage(innerMsg);
                    break;
                case AT_LEAST_ONCE:
                    processQos1(ctx,innerMsg);
                    break;
                case EXACTLY_ONCE:
                    processQos2(ctx,innerMsg);
                    break;
                default:
                    log.warn("[PubMessage] -> Wrong mqtt message,clientId={}", clientId);
            }
        }catch (Throwable tr){
            log.warn("[PubMessage] -> Solve mqtt pub message exception:{}",tr);
        }finally {
            ReferenceCountUtil.release(mqttMessage.payload());
        }
    }

    private void validAccessToken(AccessToken access_token) {
		try {
			if (!AuthServer.verifyAccessToken(access_token)) {
				log("processRequest(): WARNING: access token verification failed!");
				return;
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			log("processRequest(): WARNING: access token verification failed!");
			return;
		}
    }

    private void processQos2(ChannelHandlerContext ctx,Message innerMsg){
        log.debug("[PubMessage] -> Process qos2 message,clientId={}",innerMsg.getClientId());
        boolean flag = flowMessageStore.cacheRecMsg(innerMsg.getClientId(),innerMsg);
        if(!flag){
            log.warn("[PubMessage] -> cache qos2 pub message failure,clientId={}",innerMsg.getClientId());
        }
        MqttMessage pubRecMessage = MessageUtil.getPubRecMessage(innerMsg.getMsgId());
        ctx.writeAndFlush(pubRecMessage);
    }

    private void processQos1(ChannelHandlerContext ctx,Message innerMsg){
        processMessage(innerMsg);
        log.debug("[PubMessage] -> Process qos1 message,clientId={}",innerMsg.getClientId());
        MqttPubAckMessage pubAckMessage = MessageUtil.getPubAckMessage(innerMsg.getMsgId());
        ctx.writeAndFlush(pubAckMessage);
    }

    public void log(String msg) {
    	SysUtils.log(null,this.getClass().getSimpleName()+'['+port+"]: "+msg);
    }

}
