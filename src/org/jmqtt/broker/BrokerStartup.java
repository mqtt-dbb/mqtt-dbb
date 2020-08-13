package org.jmqtt.broker;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import it.unipr.netsec.mqtt.Bridge;
import it.unipr.netsec.mqtt.ClientFactory;
import it.unipr.netsec.util.SysUtils;

import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;
import org.jmqtt.common.config.BrokerConfig;
import org.jmqtt.common.config.ClusterConfig;
import org.jmqtt.common.config.NettyConfig;
import org.jmqtt.common.config.StoreConfig;
import org.jmqtt.common.helper.MixAll;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

public class BrokerStartup {

	
	public static void main(String[] args) {
        try {
            start(args);
        } catch (Exception e) {
            System.out.println("Jmqtt start failure,cause = " + e);
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public static BrokerController start(String[] args) throws Exception {

        Options options = buildOptions();
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = parser.parse(options,args);
        String jmqttHome = null;
        String jmqttConfigPath = null;
        String tcpPort = null;
        String grpPort = null;
        String qos = null;
        String provider = "jmqtt";
        boolean external_bridge = false;
        boolean exit = false;
        boolean verbose = false;
        BrokerConfig brokerConfig = new BrokerConfig();
        NettyConfig nettyConfig = new NettyConfig();
        StoreConfig storeConfig = new StoreConfig();
        ClusterConfig clusterConfig =new ClusterConfig();
        if(commandLine != null){
            jmqttHome = commandLine.getOptionValue("h");
            jmqttConfigPath = commandLine.getOptionValue("c");
            tcpPort = commandLine.getOptionValue("p");
            grpPort = commandLine.getOptionValue("g");
            qos = commandLine.getOptionValue("q");
            if (commandLine.hasOption("provider")) provider = commandLine.getOptionValue("provider");
            external_bridge = commandLine.hasOption("b");
            exit = commandLine.hasOption("x");
            verbose = commandLine.hasOption("v");
         }
        if(StringUtils.isNotEmpty(jmqttConfigPath)){
            initConfig(jmqttConfigPath,brokerConfig,nettyConfig,storeConfig, clusterConfig);
        }
        if(StringUtils.isEmpty(jmqttHome)){
            jmqttHome = brokerConfig.getJmqttHome();
        }
        if(StringUtils.isEmpty(jmqttHome)){
            throw new Exception("please set JMQTT_HOME.");
        }
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(lc);
        lc.reset();
        configurator.doConfigure(jmqttHome + "/conf/logback_broker.xml");
        
        if (StringUtils.isNotEmpty(tcpPort)) nettyConfig.setTcpPort(Integer.parseInt(tcpPort));
        if (StringUtils.isNotEmpty(grpPort)) clusterConfig.setGroupServerPort(Integer.parseInt(grpPort));
        nettyConfig.setStartWebsocket(false);
        nettyConfig.setStartSslTcp(false);
        nettyConfig.setStartSslWebsocket(false);
        if (qos!=null) Bridge.DEFAULT_QOS = Integer.valueOf(qos);
        Bridge.EXTERNAL_BRIDGING = external_bridge;
        SysUtils.DEBUG=verbose;
        ClientFactory.setProvider(provider);

        BrokerController brokerController = new BrokerController(brokerConfig,nettyConfig, storeConfig, clusterConfig);
        brokerController.start();
        
        System.out.println("Modified MQTT broker (jmqtt+"+provider+") started on port "+nettyConfig.getTcpPort());
        if (external_bridge) System.out.println("External bridging mode is active");
        if (exit) SysUtils.exitWhenPressingEnter();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                brokerController.shutdown();
            }
        }));
        
        return brokerController;
    }

    private static Options buildOptions(){
        Options options = new Options();
        Option opt = new Option("h",true,"jmqttHome,eg: /wls/xxx");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("c",true,"jmqtt.properties path,eg: /wls/xxx/xxx.properties");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("p",true,"broker TCP port");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("g",true,"group server port");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("q",true,"internal QoS value");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("provider",true,"MQTT bridging provider (default is jmqtt)");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("b",false,"external bridge mode");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("v",false,"verbose mode");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("x",false,"prompt to exit");
        opt.setRequired(false);
        options.addOption(opt);

        return options;
    }

    private static void initConfig(String jmqttConfigPath, BrokerConfig brokerConfig, NettyConfig nettyConfig, StoreConfig storeConfig, ClusterConfig clusterConfig){
        Properties properties = new Properties();
        BufferedReader  bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(jmqttConfigPath));
            properties.load(bufferedReader);
            MixAll.properties2POJO(properties,brokerConfig);
            MixAll.properties2POJO(properties,nettyConfig);
            MixAll.properties2POJO(properties,storeConfig);
            MixAll.properties2POJO(properties, clusterConfig);
        } catch (FileNotFoundException e) {
            System.out.println("jmqtt.properties cannot find,cause = " + e);
        } catch (IOException e) {
            System.out.println("Handle jmqttConfig IO exception,cause = " + e);
        } finally {
            try {
                if(Objects.nonNull(bufferedReader)){
                    bufferedReader.close();
                }
            } catch (IOException e) {
                System.out.println("Handle jmqttConfig IO exception,cause = " + e);
            }
        }
    }

}
