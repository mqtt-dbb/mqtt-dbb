package org.jmqtt.broker.processor;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import it.unipr.netsec.mqtt.Bridge;
import it.unipr.netsec.mqtt.ExternalBridge;
import it.unipr.netsec.mqtt.auth.AccessToken;
import it.unipr.netsec.mqtt.auth.AuthServer;
import it.unipr.netsec.util.SysUtils;
import test.MqttClient;

import org.jmqtt.broker.BrokerController;
import org.jmqtt.broker.BrokerStartup;
import org.jmqtt.broker.acl.PubSubPermission;
import org.jmqtt.broker.subscribe.SubscriptionMatcher;
import org.jmqtt.common.bean.*;
import org.jmqtt.common.log.LoggerName;
import org.jmqtt.remoting.netty.RequestProcessor;
import org.jmqtt.remoting.session.ClientSession;
import org.jmqtt.remoting.session.ConnectManager;
import org.jmqtt.remoting.util.MessageUtil;
import org.jmqtt.remoting.util.NettyUtil;
import org.jmqtt.store.FlowMessageStore;
import org.jmqtt.store.RetainMessageStore;
import org.jmqtt.store.SubscriptionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SubscribeProcessor implements RequestProcessor {

    private static final Logger log = LoggerFactory.getLogger(LoggerName.MESSAGE_TRACE);

    private SubscriptionMatcher subscriptionMatcher;
    private RetainMessageStore retainMessageStore;
    private FlowMessageStore flowMessageStore;
    private SubscriptionStore subscriptionStore;
    private PubSubPermission pubSubPermission;
  
    private int port;
    private Bridge bridge;
    private ExternalBridge external_bridge;
  	

    public SubscribeProcessor(BrokerController controller){
        this.subscriptionMatcher = controller.getSubscriptionMatcher();
        this.retainMessageStore = controller.getRetainMessageStore();
        this.flowMessageStore = controller.getFlowMessageStore();
        this.subscriptionStore = controller.getSubscriptionStore();
        this.pubSubPermission = controller.getPubSubPermission();
        this.port = controller.getNettyConfig().getTcpPort();
    }
    
    
    @Override
    public void processRequest(ChannelHandlerContext ctx, MqttMessage mqttMessage) {
        MqttSubscribeMessage subscribeMessage = (MqttSubscribeMessage) mqttMessage;
        String clientId = NettyUtil.getClientId(ctx.channel());
        int messageId = subscribeMessage.variableHeader().messageId();
        ClientSession clientSession = ConnectManager.getInstance().getClient(clientId);
        List<MqttTopicSubscription> topics = subscribeMessage.payload().topicSubscriptions();
        List<Topic> validTopicList = validTopics(clientSession,topics);
    	String topic_name=validTopicList.get(0).getTopicName();
    	
    	log("processRequest(): subscribe topic: "+topic_name);
    	if (topic_name.contains("@")) {
        	if (Bridge.EXTERNAL_BRIDGING) {
            	log("processRequest(): 'external proxing' mode");
    	    	if (external_bridge==null) external_bridge=new ExternalBridge(String.valueOf(port));
        		processProxingSubscribeRequest(ctx,topic_name,subscribeMessage);
        		return;
        	}
        	// else
        	log("processRequest(): 'internal proxing' mode");
        	if (bridge==null) bridge=new Bridge(port);
       	    bridge.processSubscribe(topic_name);

       		int index=topic_name.indexOf("@");
       	    String topic_part=topic_name.substring(0,index);
    		String proxy_part=topic_name.substring(index);
        	if (AccessToken.containsToken(topic_part)) {
        		AccessToken access_token=AccessToken.parseToken(topic_part);
        		validAccessToken(access_token);
        		topic_part=access_token.getAudience();
        	}
        	topic_name=Bridge.COMPLETE_BRIDGING? topic_part : topic_part+proxy_part;
        	log("processRequest(): registering topic: "+topic_name);
        	validTopicList.get(0).setTopicName(topic_name);
    	}
    	else {
        	if (AccessToken.containsToken(topic_name)) {
        		AccessToken access_token=AccessToken.parseToken(topic_name);
        		validAccessToken(access_token);
        		topic_name=access_token.getAudience();
            	log("processRequest(): registering topic: "+topic_name);
            	validTopicList.get(0).setTopicName(topic_name);   
        	}
    	}

        if(validTopicList == null || validTopicList.size() == 0){
            log.warn("[Subscribe] -> Valid all subscribe topic failure,clientId:{}",clientId);
            return;
        }
        List<Integer> ackQos = getTopicQos(validTopicList);
        MqttMessage subAckMessage = MessageUtil.getSubAckMessage(messageId,ackQos);
        ctx.writeAndFlush(subAckMessage);
        // send retain messages
        List<Message> retainMessages = subscribe(clientSession,validTopicList);
        dispatcherRetainMessage(clientSession,retainMessages);
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

    private List<Integer> getTopicQos(List<Topic> topics){
        List<Integer> qoss = new ArrayList<>(topics.size());
        for(Topic topic : topics){
            qoss.add(topic.getQos());
        }
        return qoss;
    }

    private void processProxingSubscribeRequest(ChannelHandlerContext ctx, String topic_name, MqttSubscribeMessage subscribeMessage) {
    	log("processProxingSubscribeRequest()");
		try {
			String next_topic=Bridge.getNextTopic(topic_name);
			String next_broker=Bridge.getNextBroker(topic_name);
	    	log("processProxingSubscribeRequest(): subscribe topic '"+next_topic+"' on broker "+next_broker);
	    	external_bridge.subscribe("tcp://"+next_broker,next_topic,Bridge.DEFAULT_QOS,ctx.channel());
		}
		catch (Exception e) {
			log("processProxingSubscribeRequest(): "+e.getMessage());
			e.printStackTrace();
		}
    }

    
    private List<Message> subscribe(ClientSession clientSession,List<Topic> validTopicList){
        Collection<Message> retainMessages = null;
        List<Message> needDispatcher = new ArrayList<>();
        for(Topic topic : validTopicList){
            Subscription subscription = new Subscription(clientSession.getClientId(),topic.getTopicName(),topic.getQos());
            boolean subRs = this.subscriptionMatcher.subscribe(subscription);
            if(subRs){
                if(retainMessages == null){
                    retainMessages = retainMessageStore.getAllRetainMessage();
                }
                for(Message retainMsg : retainMessages){
                    String pubTopic = (String) retainMsg.getHeader(MessageHeader.TOPIC);
                    if(subscriptionMatcher.isMatch(pubTopic,subscription.getTopic())){
                        int minQos = MessageUtil.getMinQos((int)retainMsg.getHeader(MessageHeader.QOS),topic.getQos());
                        retainMsg.putHeader(MessageHeader.QOS,minQos);
                        needDispatcher.add(retainMsg);
                    }
                }
                this.subscriptionStore.storeSubscription(clientSession.getClientId(),subscription);
            }
        }
        retainMessages = null;
        return needDispatcher;
    }

    private List<Topic> validTopics(ClientSession clientSession,List<MqttTopicSubscription> topics){
        List<Topic> topicList = new ArrayList<>();
        for(MqttTopicSubscription subscription : topics){
            if(!pubSubPermission.subscribeVerfy(clientSession.getClientId(),subscription.topicName())){
                log.warn("[SubPermission] this clientId:{} have no permission to subscribe this topic:{}",clientSession.getClientId(),subscription.topicName());
                clientSession.getCtx().close();
                return null;
            }
            Topic topic = new Topic(subscription.topicName(),subscription.qualityOfService().value());
            topicList.add(topic);
        }
        return topicList;
    }

    private void dispatcherRetainMessage(ClientSession clientSession,List<Message> messages){
        for(Message message : messages){
            message.putHeader(MessageHeader.RETAIN,true);
            int qos = (int) message.getHeader(MessageHeader.QOS);
            if(qos > 0){
                flowMessageStore.cacheSendMsg(clientSession.getClientId(),message);
            }
            MqttPublishMessage publishMessage = MessageUtil.getPubMessage(message,false,qos,clientSession.generateMessageId());
            clientSession.getCtx().writeAndFlush(publishMessage);
        }
    }

    public void log(String msg) {
    	SysUtils.log(null,this.getClass().getSimpleName()+'['+port+"]: "+msg);
    }

}
