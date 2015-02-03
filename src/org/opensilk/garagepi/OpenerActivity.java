package org.opensilk.garagepi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
import android.widget.TextView;
import android.widget.Toast;

public class OpenerActivity extends Activity {

	private Button mOpenDoor;
	private TextView mToken;
	private TextView mTokenId;
	
	private boolean mBypass = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_opener);
		
		mOpenDoor = (Button) findViewById(R.id.button1);
		mOpenDoor.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mBypass = false;
				new HttpAsyncTask().execute("http://192.168.200.121/rest/getToken/andrew");
			}
		});
		
		mToken = (TextView) findViewById(R.id.txt_token);
		mTokenId = (TextView) findViewById(R.id.txt_token_id);
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
        	String token = "";
        	String token_id = "";
            Toast.makeText(getBaseContext(), "Received!", Toast.LENGTH_LONG).show();
            Log.d("GaragePi", result);
            if (!mBypass) {
	            try {
					JSONObject json = new JSONObject(result);
					token = json.getString("token");
					token_id = json.getString("token_id");
					
					mToken.setText(token);
					mTokenId.setText(token_id);
				} catch (JSONException e) {
					e.printStackTrace();
				}
	            
	            String md5sum = md5("blah123" + token);
	            mBypass = true;
	            new HttpAsyncTask().execute("http://192.168.200.121/rest/openDoor/" + token_id + "/" + md5sum);
            }
       }
    }
	
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
