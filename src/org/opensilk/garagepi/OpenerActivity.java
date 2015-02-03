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
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class OpenerActivity extends Activity {

    private static final int RETURN_TOKEN   = 0;
    private static final int RETURN_OPEN    = 1;
    private static final int RETURN_TOKENS  = 2;

    private static final int HASH_ACCEPTED = 0;
    private static final int HASH_REJECTED = 1;
    
    private static final boolean DEBUG = false;
    
	private Button mOpenDoor;
    private TextView mLogText;
    private ProgressBar mProgressBar;

	private ArrayList<String> mLogBuffer = new ArrayList<String>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_opener);

		mOpenDoor = (Button) findViewById(R.id.button1);
		mOpenDoor.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
			    mProgressBar.setVisibility(View.VISIBLE);
				new HttpAsyncTask().execute("http://192.168.200.121/rest/getToken/andrew");
			}
		});

        mLogText = (TextView) findViewById(R.id.txt_log);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mProgressBar.setVisibility(View.INVISIBLE);
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
                    result = "Did not work!";
            } catch (Exception e) {
                Log.d("GaragePi", e.getLocalizedMessage());
            }
            return result;
        }
        @Override
        protected void onPostExecute(String result) {
            if (DEBUG) {
                addToLog("GaragePi: " + result);
                Log.d("GaragePi", result);
            }

            JSONObject json = null;
            try {
				json = new JSONObject(result);

				int retType = json.getInt("return_type");

	            switch (retType) {
                    case RETURN_TOKEN:
                        String token = json.getString("token");
                        String token_id = json.getString("token_id");

                        addToLog("Received token: " + token);

                        addToLog("Calculating password has with token, for user: " + "andrew"); //TODO
                        String md5sum = md5("blah123" + token);

                        addToLog("Sending hashed password!");
                        new HttpAsyncTask().execute("http://192.168.200.121/rest/openDoor/" + token_id + "/" + md5sum);
                        break;
                    case RETURN_OPEN:
                        int retValue = json.getInt("return_value");
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

}
