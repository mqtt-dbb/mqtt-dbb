package it.unipr.netsec.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Collection of some static utility methods.
 */
public class SysUtils {
	
	/** Debug mode */
	public static boolean DEBUG=true;
	
	/** Prints a log message.
	 * @param obj the object that sends the message
	 * @param msg the message */
	public static void log(Object obj, String msg) {
		if (DEBUG) System.out.println("DEBUG: "+(obj!=null?obj.getClass().getSimpleName()+": ":"")+msg);
	}
	
	
	/** Exits after pressing 'Enter'. */
	public static void exitWhenPressingEnter() {
		System.out.println("Press <Enter> to exit");
		run(()->{
			new BufferedReader(new InputStreamReader(System.in)).readLine();
			System.exit(0);
		});
	}
	
	/** Runs in a new thread.
	 * If an exception is thrown, the stack trace is printed to the standard error. */
	public static void run(RunnableTE r) {
		new Thread(()->{
			try { r.run(); } catch (Exception e) { e.printStackTrace(); }
		}).start();		
	}

	/** Runnable that may throw exceptions. */
	public static interface RunnableTE {
		public void run() throws Exception;
	}
}
