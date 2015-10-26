package org.vertx.java.core.eventbus;

/**
 * Created with IntelliJ IDEA.
 * User: nevesm
 * Date: 08/04/2015
 * Time: 12:53
 */
public interface EventBusHook {

	/**
	 * Called before receiving a message from eventbus.
	 * 
	 * @param message
	 * @return true if you want to cancel the delivery
	 */
	public <T> boolean beforeReceive(Message<T> message);

	/**
	 * Called after a successful delivery
	 * @param message
	 */
	public <T> void delivered(Message<T> message);

	/**
	 * Called after a receive attempt. Typically the delivered method is always called, but if a message is sent and 
	 * the handler removed before it is received the delivered will be false. 
	 * @param message
	 * @param delivered
	 */
	public <T> void afterReceive(Message<T> message, boolean delivered);
}
