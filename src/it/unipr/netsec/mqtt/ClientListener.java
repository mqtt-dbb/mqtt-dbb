package it.unipr.netsec.mqtt;


public interface ClientListener {

	public void onSubscribing(Client client, String topic,int qos);

	public void onPublishing(Client client, String topic,int qos, byte[] payload);

	public void onMessageArrived(Client client, String topic,int qos, byte[] payload);

	public void onConnectionLost(Client client, Throwable cause);

}
