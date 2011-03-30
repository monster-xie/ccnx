package org.ccnx.android.examples.startup;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import org.ccnx.android.ccnlib.CCNxConfiguration;
import org.ccnx.android.ccnlib.CCNxServiceCallback;
import org.ccnx.android.ccnlib.CCNxServiceControl;
import org.ccnx.android.ccnlib.CCNxServiceStatus.SERVICE_STATUS;
import org.ccnx.android.ccnlib.CcndWrapper.CCND_OPTIONS;
import org.ccnx.android.ccnlib.RepoWrapper.REPO_OPTIONS;
import org.ccnx.ccn.config.UserConfiguration;
import org.ccnx.ccn.profiles.ccnd.CCNDaemonException;
import org.ccnx.ccn.profiles.ccnd.SimpleFaceControl;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class NonBlockingStartup extends StartupBase {
	protected String TAG="ChatScreen";

	// ===========================================================================
	// Process control Methods

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		TextView title = (TextView) findViewById(R.id.tvTitle);
		title.setText("NonBlockingStartup (not implemented yet)");
	}

	@Override
	public void onStart() {
		super.onStart();	
		Log.i(TAG,"onStart");
		_worker = new NonBlockingWorker();
		_thd = new Thread(_worker);
		_thd.start();
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "onDestroy()");

		_worker.stop();
		super.onDestroy();
	}

	// ===========================================================================
	// UI Methods

	@Override
	void doExit() {
		
	}
	
	@Override
	void doShutdown() {
		_worker.shutdown();
	}

	// ====================================================================
	// Internal implementation

	protected NonBlockingWorker _worker = null;
	protected Thread _thd = null;

	// ===============================================
	protected class NonBlockingWorker implements Runnable, CCNxServiceCallback {
		protected final static String TAG="ChatWorker";

		/**
		 * Create a worker thread to handle all the CCNx calls.
		 */
		public NonBlockingWorker() {
			_context = NonBlockingStartup.this.getBaseContext();

			postToUI("Setting CCNxConfiguration");
			
			// Use a shared key directory
			CCNxConfiguration.config(_context, false);

			File ff = getDir("storage", Context.MODE_WORLD_READABLE);
			postToUI("Setting setUserConfigurationDirectory: " + ff.getAbsolutePath());
			
			Log.i(TAG,"getDir = " + ff.getAbsolutePath());
			UserConfiguration.setUserConfigurationDirectory( ff.getAbsolutePath() );
			
			// Do these CCNx operations after we created ChatWorker
			ScreenOutput("User name = " + UserConfiguration.userName());
			ScreenOutput("ccnDir    = " + UserConfiguration.userConfigurationDirectory());
			ScreenOutput("Waiting for CCN Services to become ready");
		}

		/**
		 * Exit the worker thread, but keep services running
		 */
		public synchronized void stop() {
			// this is called form onDestroy too, so only do something
			// if the user didn't select a menu option to exit or shutdown.
			if( _latch.getCount() > 0 ) {
				_latch.countDown();
				_ccnxService.disconnect();
			}
		}

		/**
		 * Exit the worker thread and shutdown services
		 */
		public synchronized void shutdown() {
			_latch.countDown();
			try {
				_ccnxService.stoptAll();
			} catch(Exception e) {
				e.printStackTrace();
			}
		}

		/**
		 * Runnable method
		 */
		@Override
		public void run() {
			// Startup CCNx in a blocking call
			if( !initializeCCNx() ) {
				Log.e(TAG, "Could not start CCNx services!");
			} else {
				Log.i(TAG,"Starting ccnChatNet.listen() loop");


			}

			// wait for shutdown
			while( _latch.getCount() > 0 ) {
				try {
					_latch.await();
				} catch (InterruptedException e) {
				}
			}
			
			Log.i(TAG, "service_run() exits");		}

		// ==============================================================================
		// Internal implementation
		protected final CountDownLatch _latch = new CountDownLatch(1);
		protected final Context _context;
		protected CCNxServiceControl _ccnxService;

		/*********************************************/
		// These are all run in the CCN thread

		private boolean initializeCCNx() {
			_ccnxService = new CCNxServiceControl(_context);
			_ccnxService.registerCallback(this);
			_ccnxService.setCcndOption(CCND_OPTIONS.CCND_DEBUG, "1");
			_ccnxService.setRepoOption(REPO_OPTIONS.REPO_DEBUG, "WARNING");
			postToUI("calling _ccnxService.startAll");
			return _ccnxService.startAll();
		}

		/**
		 * Called from CCNxServiceControl
		 */
		@Override
		public void newCCNxStatus(SERVICE_STATUS st) {
			postToUI("CCNxStatus: " + st.toString());
			
			switch(st) {

			case START_ALL_DONE:
				try {
					postToUI("Calling SimpleFaceControl");
					SimpleFaceControl.getInstance().openMulicastInterface();
					postToUI("Finished SimpleFaceControl");

				} catch (CCNDaemonException e) {
					e.printStackTrace();
					postToUI("SimpleFaceControl error: " + e.getMessage());
				}

				break;
			case START_ALL_ERROR:
				postToUI("CCNxStatus ERROR");
				break;
			}
		}
	}
}
