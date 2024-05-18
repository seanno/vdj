//
// EXEC.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class Exec
{
	private final static int SHUTDOWN_FIRSTWAIT_SECONDS = 1;
	private final static int SHUTDOWN_SECONDWAIT_SECONDS = 2;
	
	private static ExecutorService pool;
	protected static ExecutorService getPool() { return(pool); }

	static {

		pool = Executors.newCachedThreadPool();

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				try {
					pool.shutdown();
					pool.awaitTermination(SHUTDOWN_FIRSTWAIT_SECONDS, TimeUnit.SECONDS);
					pool.shutdownNow();
					pool.awaitTermination(SHUTDOWN_SECONDWAIT_SECONDS, TimeUnit.SECONDS);
				}
				catch (InterruptedException e) {
					/* eat it */
				}
			}
		});
	}
	
}
