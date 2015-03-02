package org.opensilk.garagepi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.opensilk.garagepi.R;

public class OpenerActivity extends Activity {

    private static final int RETURN_TOKEN        = 0;
    private static final int RETURN_OPEN         = 1;
    private static final int RETURN_TOKENS       = 2;
    private static final int RETURN_DOOR_STATUS  = 3;

    private static final int USERNAME_ACCEPTED = 0;
    private static final int USERNAME_MISSING  = 1;

    private static final int HASH_ACCEPTED = 0;
    private static final int HASH_REJECTED = 1;

    private static final int DOOR_CLOSED = 0;
    private static final int DOOR_OPEN   = 1;
    
    private static final boolean DEBUG = false;

    private String mUsername;
    private String mPassword;
    private String mServerIp;
    private String mServerPort;

    private Button mOpenDoor;
    private TextView mLogText;
    private ProgressBar mProgressBar;
    private ScrollView mScrollView;

    private PollDoorStatus mDoorStatusThread;

    Handler mHandler = new Handler();

    private ArrayList<String> mLogBuffer = new ArrayList<String>();

	SharedPreferences mPrefs;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_opener);

		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		mOpenDoor = (Button) findViewById(R.id.button_open);
		mOpenDoor.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
			    mProgressBar.setVisibility(View.VISIBLE);

                mUsername = mPrefs.getString("pref_username" , "");
                mPassword = mPrefs.getString("pref_password" , "");
			    mServerIp = mPrefs.getString("pref_server_ip", "");
			    mServerPort = mPrefs.getString("pref_server_port", "60598");
	
			    addToLog("Connecting with: " + mServerIp + ":" + mServerPort);

				new HttpAsyncTask().execute("http://" + mServerIp + ":" + mServerPort + "/getToken/" + mUsername);
			}
		});

        mScrollView = (ScrollView) findViewById(R.id.log_scrollview);

        mLogText = (TextView) findViewById(R.id.txt_log);
        mLogText.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                    int count) { /* Nothing */ }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) { /* Nothing */ }

            @Override
            public void afterTextChanged(Editable s) {
                mScrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        mScrollView.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });

        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mProgressBar.setVisibility(View.INVISIBLE);

        if (mDoorStatusThread == null) {
            mDoorStatusThread = new PollDoorStatus();
            mDoorStatusThread.start();
        }
	}

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mDoorStatusThread != null) {
            mDoorStatusThread.stop();
            mDoorStatusThread = null;
        }
    }

	@Override
	public void onPause() {
	    super.onPause();
        if (mDoorStatusThread != null) {
            mDoorStatusThread.stop();
            mDoorStatusThread = null;
        }
	}

    @Override
    public void onResume() {
        super.onResume();
        if (mDoorStatusThread == null) {
            mDoorStatusThread = new PollDoorStatus();
            mDoorStatusThread.start();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mDoorStatusThread != null) {
            mDoorStatusThread.stop();
            mDoorStatusThread = null;
        }
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.opener, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings) {
		    Intent settingsIntent = new Intent(this, SettingsActivity.class);
	        startActivity(settingsIntent);
			return true;
		} else if (id == R.id.action_clear_log) {
		    mLogBuffer.clear();
		    mLogText.setText("");
	        return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void addToLog(String text) {
	    mLogBuffer.add(text);
	    StringBuilder builder = new StringBuilder();
	    for (String line : mLogBuffer) {
	        builder.append(line + "\n");
	    }

	    mLogText.setText(builder.toString());
	}

    private static String convertInputStreamToString(InputStream inputStream) throws IOException{
        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while((line = bufferedReader.readLine()) != null)
            result += line;
 
        inputStream.close();
        return result;
 
    }

    private class HttpAsyncTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            InputStream inputStream = null;
            String result = "";
            try {
                HttpClient httpclient = new DefaultHttpClient();
                HttpResponse httpResponse = httpclient.execute(new HttpGet(urls[0]));
                inputStream = httpResponse.getEntity().getContent();
                if(inputStream != null)
                    result = convertInputStreamToString(inputStream);
                else
                    result = "Error: Did not work!";
            } catch (Exception e) {
                Log.d("GaragePi", e.getLocalizedMessage());
                result = "Error: " + e.getLocalizedMessage();
            }
            return result;
        }
        @Override
        protected void onPostExecute(String result) {

            if (result.startsWith("Error")) {
                addToLog(result);
                mProgressBar.setVisibility(View.INVISIBLE);
                return;
            }

            if (DEBUG) {
                addToLog("GaragePi: " + result);
                Log.d("GaragePi", result);
            }

            JSONObject json = null;
            try {
				json = new JSONObject(result);

				int retType = json.getInt("return_type");
				int retValue = json.getInt("return_value");

	            switch (retType) {
                    case RETURN_TOKEN:
                        switch (retValue) {
                            case USERNAME_ACCEPTED:
                                String token = json.getString("token");
                                String token_id = json.getString("token_id");

                                addToLog("Received token: " + token);

                                addToLog("Calculating password hash with token, for user: " + mUsername); //TODO
                                String md5sum = md5(mPassword + token);

                                addToLog("Sending hashed password!");
                                new HttpAsyncTask().execute("http://" + mServerIp + ":" + mServerPort + "/openDoor/" + token_id + "/" + md5sum);
                                break;
                            case USERNAME_MISSING:
                                String user = json.getString("user");
                                addToLog("Username not accepted! - " + user);
                                mProgressBar.setVisibility(View.INVISIBLE);
                                break;
                        }
                        break;
                    case RETURN_OPEN:
                        String retMessage = json.getString("return_message");
                        switch (retValue) {
                            case HASH_ACCEPTED:
                                addToLog("Hash accepted, opening door!");
                                break;
                            case HASH_REJECTED:
                                addToLog("Hash rejected -- " + retMessage);
                                break;
                            default:
                                addToLog("Unknown rejection -- " + retMessage);
                        }

                        mProgressBar.setVisibility(View.INVISIBLE);
                        break;
                    case RETURN_TOKENS:
                        break;
                    case RETURN_DOOR_STATUS:
                        switch (retValue) {
                            case DOOR_OPEN:
                                mOpenDoor.setText("Close Door");
                                mOpenDoor.setBackgroundColor(Color.RED);
                                break;
                            case DOOR_CLOSED:
                                mOpenDoor.setText("Open Door");
                                mOpenDoor.setBackgroundColor(Color.GREEN);
                                break;
                            default:
                                addToLog("Unknown door status: " + retValue);
                        }

                        mProgressBar.setVisibility(View.INVISIBLE);
                        break;
                    default:
                        break;
	            }
			} catch (JSONException e) {
				e.printStackTrace();
			}
       }
    }

	// http://stackoverflow.com/a/4846511
    public static final String md5(final String s) {
        final String MD5 = "MD5";
        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest
                    .getInstance(MD5);
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest) {
                String h = Integer.toHexString(0xFF & aMessageDigest);
                while (h.length() < 2)
                    h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    class PollDoorStatus implements Runnable {

        Thread backgroundThread;

        public void start() {
           if( backgroundThread == null ) {
              backgroundThread = new Thread( this );
              backgroundThread.start();
           }
        }

        public void stop() {
           if( backgroundThread != null ) {
              backgroundThread.interrupt();
           }
        }

        public void run() {
            try {
               Log.d("GaragePi","Thread starting.");
               while( !backgroundThread.interrupted() ) {
                   mServerIp = mPrefs.getString("pref_server_ip", "");
                   mServerPort = mPrefs.getString("pref_server_port", "60598");
                   new HttpAsyncTask().execute("http://" + mServerIp + ":" + mServerPort + "/getDoorStatus");
                   backgroundThread.sleep(5000);
               }
               Log.i("GaragePi","Thread stopping.");
            } catch( InterruptedException ex ) {
               // important you respond to the InterruptedException and stop processing
               // when its thrown!  Notice this is outside the while loop.
               Log.i("GaragePi","Thread shutting down as it was requested to stop.");
            } finally {
               backgroundThread = null;
            }
        }
    }

}
