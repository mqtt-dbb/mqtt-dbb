package it.unipr.netsec.mqtt;


import java.util.ArrayList;
import java.util.HashMap;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;


public class JmqttServer {
	
	public JmqttServer(int port) {
		EventLoopGroup bossGroup = new NioEventLoopGroup(1);
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		HashMap<String,ArrayList<Channel>> subscribers=new HashMap<>();
		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup);
			b.option(ChannelOption.SO_BACKLOG, 1024);
			b.channel(NioServerSocketChannel.class);
			b.childHandler(new ChannelInitializer<SocketChannel>() {
				protected void initChannel(SocketChannel ch) throws Exception {
					ch.pipeline().addLast("encoder", MqttEncoder.INSTANCE);
					ch.pipeline().addLast("decoder", new MqttDecoder());
					//ch.pipeline().addLast("serverHandler", new IdleStateHandler(45, 0, 0, TimeUnit.SECONDS));
					ch.pipeline().addLast("handler", new JmqttServerChannelInboundHandler(subscribers));
				}
			});

			ChannelFuture f = b.bind(port).sync();
			System.out.println("DEBUG: Server: Broker initiated...");

			f.channel().closeFuture().sync();
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
		finally {
			workerGroup.shutdownGracefully();
			bossGroup.shutdownGracefully();
		}
	}
	
	
	public static void main(String[] args) {
		JmqttServer server=new JmqttServer(1883);
	}

}
