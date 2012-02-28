package st.forre.ufw_login;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.ArrayList;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.widget.TextView;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.content.SharedPreferences;
import android.text.format.Formatter;
import java.lang.StringBuilder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.URI;
import android.preference.PreferenceActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.preference.PreferenceManager;

public class ufw_login extends Activity
{
    public static class Preferences extends PreferenceActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);   
        }
    }
    
    public static String slurp(final InputStream file) throws IOException {
        StringBuilder result = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(file));
        try {
            char[] buf = new char[1024];
            int r = 0;
            while ((r = reader.read(buf)) != -1) {
                result.append(buf, 0, r);
            }
        }
        finally {
            reader.close();
        }
        return result.toString();
    }
    
    public static class WatchService extends Service {
        private final IBinder mBinder = new LocalBinder();
        
        private class AuthTask extends AsyncTask<String, Void, String> {
            protected String doInBackground(String... x) { // url, ip address, username, password
                List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
                nameValuePairs.add(new BasicNameValuePair("compact", "true"));
                nameValuePairs.add(new BasicNameValuePair("index", "0"));
                nameValuePairs.add(new BasicNameValuePair("password", x[3]));
                nameValuePairs.add(new BasicNameValuePair("pageid", "-1"));
                nameValuePairs.add(new BasicNameValuePair("passwordLabel", "Password"));
                nameValuePairs.add(new BasicNameValuePair("cm", "ws32vklm"));
                nameValuePairs.add(new BasicNameValuePair("registerGuest", "NO"));
                nameValuePairs.add(new BasicNameValuePair("username", x[2]));
                nameValuePairs.add(new BasicNameValuePair("uri", "http://www.google.com/search?client=ubuntu"));
                nameValuePairs.add(new BasicNameValuePair("reqFrom", "perfigo_frame_login.jsp"));
                nameValuePairs.add(new BasicNameValuePair("guestUserNameLabel", "Guest ID"));
                nameValuePairs.add(new BasicNameValuePair("provider", "Radius"));
                nameValuePairs.add(new BasicNameValuePair("userip", x[1]));
                nameValuePairs.add(new BasicNameValuePair("guestPasswordLabel", "Password"));
                nameValuePairs.add(new BasicNameValuePair("userNameLabel", "Username:"));
                nameValuePairs.add(new BasicNameValuePair("pm", "Linux x86_64"));
                
                HttpPost hp = new HttpPost(x[0]);
                try {
                    hp.setEntity(new UrlEncodedFormEntity(nameValuePairs));
                } catch(UnsupportedEncodingException e) {
                    return "UnsupportedEncodingException happened: " + e.getMessage();
                }
                
                DefaultHttpClient c = new DefaultHttpClient();
                HttpResponse r;
                try {
                    r = c.execute(hp);
                } catch(IOException e) {
                    return "IOException happened: " + e.getMessage();
                }
                try {
                    return slurp(r.getEntity().getContent());
                } catch(IOException e) {
                    return "IOException error: " + e.getMessage();
                }
            }
            
            protected void onPostExecute(String result) {
                WatchService.this.status += "\n\n" + result;
            }
        }
        
        private String auth_started_userpass = null;
        private String status = "just started";
        private RefreshHandler2 mRedrawHandler2 = new RefreshHandler2();
        class RefreshHandler2 extends Handler {
            @Override
            public void handleMessage(Message msg) {
                this.removeMessages(0);
                
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(WatchService.this);
                String username = sp.getString("username", "");
                String password = sp.getString("password", "");
                
                WifiInfo wi = ((WifiManager)getSystemService(WIFI_SERVICE)).getConnectionInfo();
                boolean on_wifi_now = wi != null && wi.getSSID() != null && wi.getSSID().equals("ufw");
                String ip = Formatter.formatIpAddress(wi.getIpAddress());
                
                if(username.equals("") || password.equals("")) {
                    status = "Username/password not yet set! Open the preferences window by pressing the menu button.";
                    auth_started_userpass = null;
                } else if(!on_wifi_now) {
                    status = "Not on \"ufw\".";
                    auth_started_userpass = null;
                } else if(auth_started_userpass == null || !auth_started_userpass.equals(username + ":" + password)) {
                    (new WatchService.AuthTask()).execute("https://nac-serv-1.ns.ufl.edu/auth/perfigo_cm_validate.jsp", ip, username, password);
                    (new WatchService.AuthTask()).execute("https://nac-serv-2.ns.ufl.edu/auth/perfigo_cm_validate.jsp", ip, username, password);
                    (new WatchService.AuthTask()).execute("https://nac-serv-3.ns.ufl.edu/auth/perfigo_cm_validate.jsp", ip, username, password);
                    (new WatchService.AuthTask()).execute("https://nac-serv-4.ns.ufl.edu/auth/perfigo_cm_validate.jsp", ip, username, password);
                    
                    status = "On \"ufw\". Attempting authentication...";
                    auth_started_userpass = username + ":" + password;
                }
                
                sendMessageDelayed(obtainMessage(0), 1000);
            }
        };
        
        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            mRedrawHandler2.handleMessage(null);
            return START_STICKY;
        }
        
        @Override
        public IBinder onBind(Intent intent) {
            return mBinder;
        }
        
        public class LocalBinder extends Binder {
            String getStatus() {
                WifiInfo wi = ((WifiManager)getSystemService(WIFI_SERVICE)).getConnectionInfo();
                return "SSID: " + wi.getSSID() + "\nRSSI: " + wi.getRssi() + "\nStatus: " + status;
            }
        }
    }
    
    private RefreshHandler mRedrawHandler = new RefreshHandler();
    
    class RefreshHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            this.removeMessages(0);
            if(!running)
                return;
            if(mService == null)
                ((TextView)ufw_login.this.findViewById(R.id.text)).setText("service not connected?");
            else
                ((TextView)ufw_login.this.findViewById(R.id.text)).setText(mService.getStatus());
            sendMessageDelayed(obtainMessage(0), 100);
        }
    };
    
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 0, 0, "Preferences");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                startActivity(new Intent(this, Preferences.class));
                return true;
        }
        return false;
    }
    
    private boolean running = false;
    
    @Override
    protected void onStart() {
        super.onStart();
        
        startService(new Intent(ufw_login.this, WatchService.class));
        bindService(new Intent(this, WatchService.class), mConnection, BIND_AUTO_CREATE);
        
        running = true;
        
        mRedrawHandler.handleMessage(null);
        
        /*Editor e = getSharedPreferences("login", Context.MODE_PRIVATE).edit();
        e.putString("password", mPassword);
        e.commit();*/
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        
        running = false;
        
        if (mConnection != null)
            unbindService(mConnection);
    }
    
    
    WatchService.LocalBinder mService = null;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = (WatchService.LocalBinder)service;
        }
        
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
        }
    };
}
