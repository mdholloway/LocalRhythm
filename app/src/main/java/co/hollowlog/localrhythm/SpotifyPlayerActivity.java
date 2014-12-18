package co.hollowlog.localrhythm;

/**
 * © 2014 michael david holloway
 */
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.spotify.sdk.android.Spotify;
import com.spotify.sdk.android.playback.Config;
import com.spotify.sdk.android.playback.ConnectionStateCallback;
import com.spotify.sdk.android.playback.Player;
import com.spotify.sdk.android.playback.PlayerNotificationCallback;
import com.spotify.sdk.android.playback.PlayerState;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;

public class SpotifyPlayerActivity extends Activity
        implements PlayerNotificationCallback, ConnectionStateCallback {

    private static final String ECHONEST_API_KEY = "MY-API-KEY";
    private static final String SPOTIFY_CLIENT_ID = "MY-CLIENT-ID";
    private static final String authUrl = "MY-AUTH-URL";

    private Player mPlayer;
    private Config playerConfig;
    private Spotify spotify;
    private ToggleButton playPauseButton;
    private Button nextButton;
    private PlayerState playerState;
    private TextView mArtistField;
    private TextView mTrackField;
    private TextView mLocationField;

    private boolean paused = false;
    private boolean playing = false;
    private boolean flushComplete = false;
    private boolean pauseEventComplete = false;
    private boolean haveRefreshToken;
    private String artistName;
    private String artistSpotifyId;
    private String trackName;
    private String trackUri;
    private String location;
    private String locationUrlFormat;
    private String accessToken = "";
    private String uid;
    private Uri uri;

    public volatile boolean parsingComplete = false;
    public volatile boolean refreshTokenCheckComplete = false;
    public volatile boolean callbackComplete = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        uid = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
        location = getIntent().getStringExtra("location");
        locationUrlFormat = location.replace(" ", "%20");

        playPauseButton = (ToggleButton) findViewById(R.id.playPauseButton);
        nextButton = (Button) findViewById(R.id.nextButton);
        mArtistField = (TextView) findViewById(R.id.artist_field);
        mTrackField = (TextView) findViewById(R.id.track_field);
        mLocationField = (TextView) findViewById(R.id.location_field);
        mLocationField.setText(location);

        playPauseButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if (paused) {
                    playPauseButton.setBackgroundResource(R.drawable.ic_media_pause);
                    paused = false;
                    playing = true;
                    mPlayer.resume();
                } else if (playing) {
                    playPauseButton.setBackgroundResource(R.drawable.ic_media_play);
                    playing = false;
                    paused = true;
                    mPlayer.pause();
                } else {
                    parsingComplete = false;
                    getSong(locationUrlFormat);

                    //pause execution until JSON is parsed & needed values extracted
                    while (!parsingComplete);

                    mArtistField.setText(artistName);
                    mTrackField.setText(trackName);
                    mPlayer.play(trackUri);
                    playPauseButton.setBackgroundResource(R.drawable.ic_media_pause);
                    playing = true;
                }
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                parsingComplete = false;
                flushComplete = false;
                pauseEventComplete = false;
                mPlayer.pause();
                getSong(locationUrlFormat);

                //with audio flush and pause check to prevent death spiral
                while ((!parsingComplete) && (!flushComplete) && (!pauseEventComplete));

                mArtistField.setText(artistName);
                mTrackField.setText(trackName);
                mPlayer.play(trackUri);
                playing = true;
            }
        });

        Toast.makeText(SpotifyPlayerActivity.this, "Authenticating...", Toast.LENGTH_SHORT).show();
        checkRefreshToken();

        while (!refreshTokenCheckComplete);

        if (accessToken.equals("nil") || accessToken.equals("")) {  // no valid refresh token, user must authorize
            haveRefreshToken = false;
            uri = Uri.parse(authUrl);
            Intent launchAuthWindow = new Intent("android.intent.action.VIEW", uri);
            this.startActivity(launchAuthWindow);
        } else {
            haveRefreshToken = true;
            getPlayer(accessToken);
        }
    }

    private void checkRefreshToken(){

        final String refreshTokenUrl = "http://hollowlog.co/refresh_token?uid=" + uid;

        Thread refreshTokenThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpGet refreshTokenGet = new HttpGet("http://hollowlog.co/refresh_token?uid="
                            + uid);
                    DefaultHttpClient client = new DefaultHttpClient();
                    HttpResponse response = client.execute(refreshTokenGet);
                    HttpEntity e = response.getEntity();
                    try {
                        accessToken = EntityUtils.toString(e);
                    } catch (Exception z){
                        Toast.makeText(SpotifyPlayerActivity.this, "Server error...",
                                Toast.LENGTH_LONG).show();
                    }
                    refreshTokenCheckComplete = true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        refreshTokenThread.start();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        final Uri authResponse = intent.getData();
        if (haveRefreshToken){
            accessToken = authResponse.getQueryParameter("access_token");
        } else {
            final String authCode = authResponse.getQueryParameter("code");
            // send authorization code to http://hollowlog.co/callback for access token
            Thread callbackThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        HttpGet tokenGet = new HttpGet("http://hollowlog.co/callback?uid="
                                + uid + "&code=" + authCode);
                        DefaultHttpClient client = new DefaultHttpClient();
                        HttpResponse response = client.execute(tokenGet);
                        HttpEntity e = response.getEntity();
                        accessToken = EntityUtils.toString(e);
                        callbackComplete = true;
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            });
            callbackThread.start();
            while (!callbackComplete);
        }
        getPlayer(accessToken);
    }

    private void getPlayer(String token) {
        playerConfig = new Config(SpotifyPlayerActivity.this, accessToken, SPOTIFY_CLIENT_ID);
        playerConfig.useCache(false);
        spotify = new Spotify();
        mPlayer = spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {

            @Override
            public void onInitialized() {
                mPlayer.addConnectionStateCallback(SpotifyPlayerActivity.this);
                mPlayer.addPlayerNotificationCallback(SpotifyPlayerActivity.this);
                playPauseButton.setEnabled(true);
                nextButton.setEnabled(true);
                Toast.makeText(SpotifyPlayerActivity.this, "Player ready!", Toast.LENGTH_SHORT)
                        .show();
            }

            @Override
            public void onError(Throwable throwable) {
                Log.e("SpotifyPlayerActivity", "Could not initialize player: "
                        + throwable.getMessage());
                uri = Uri.parse(authUrl);
                Intent launchAuthWindow = new Intent("android.intent.action.VIEW", uri);
                SpotifyPlayerActivity.this.startActivity(launchAuthWindow);
                Toast.makeText(SpotifyPlayerActivity.this, "Error initializing player...",
                        Toast.LENGTH_SHORT).show();
                haveRefreshToken = false;
            }
        });
    }

    private void getSong(String city) {
        Toast.makeText(SpotifyPlayerActivity.this, "Getting track...", Toast.LENGTH_SHORT).show();

        final String urlBuilder = "http://developer.echonest.com/api/v4/artist/search?api_key="
            + ECHONEST_API_KEY
                + "&format=json&artist_location=city:" + city + "&bucket=id:spotify&results=20";

        Thread thread = new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    URL url = new URL(urlBuilder);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setReadTimeout(10000);
                    conn.setConnectTimeout(15000);
                    conn.setRequestMethod("GET");
                    conn.setDoInput(true);
                    conn.connect();
                    InputStream echoNestStream = conn.getInputStream();
                    String data = convertStreamToString(echoNestStream);
                    try {
                        JSONObject reader = new JSONObject(data);
                        JSONObject response  = reader.getJSONObject("response");
                        JSONArray artists = response.getJSONArray("artists");

                        //get random artist from array
                        int idx = new Random().nextInt(artists.length());
                        JSONObject selectedArtist = (artists.getJSONObject(idx));
                        artistName = selectedArtist.getString("name");

                        JSONArray foreignIds = selectedArtist.getJSONArray("foreign_ids");
                        JSONObject foreignId = foreignIds.getJSONObject(0);
                        String str = foreignId.getString("foreign_id");
                        artistSpotifyId = str.split(":")[2];

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    echoNestStream.close();

                    final String urlBuilder2="https://api.spotify.com/v1/artists/"
                            + artistSpotifyId + "/top-tracks?country=US";
                    try {
                        URL url2 = new URL(urlBuilder2);
                        HttpURLConnection conn2 = (HttpURLConnection) url2.openConnection();
                        conn2.setReadTimeout(10000);
                        conn2.setConnectTimeout(15000);
                        conn2.setRequestMethod("GET");
                        conn2.setDoInput(true);
                        conn2.connect();
                        InputStream spotifyStream = conn2.getInputStream();
                        String data2 = convertStreamToString(spotifyStream);
                        try {
                            JSONObject reader = new JSONObject(data2);
                            JSONArray tracks  = reader.getJSONArray("tracks");

                            //get random track from array
                            int idx2 = new Random().nextInt(tracks.length());
                            JSONObject selectedTrack = (tracks.getJSONObject(idx2));

                            trackName = selectedTrack.getString("name");
                            trackUri = selectedTrack.getString("uri");

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        spotifyStream.close();
                        parsingComplete = true;

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    @Override
    public void onLoggedIn() {
        Log.d("MainActivity", "User logged in");
    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Throwable error) {
        Log.d("MainActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onNewCredentials(String s) {
        Log.d("MainActivity", "User credentials blob received");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    @Override
    public void onPlaybackEvent(EventType eventType, PlayerState ps) {
        Log.d("MainActivity", "Playback event received: " + eventType.name());
        playerState = ps;
        if (eventType.name().equals("END_OF_CONTEXT")) {
            pauseEventComplete = false;
            parsingComplete = false;
            flushComplete = false;
            mPlayer.pause();
            getSong(locationUrlFormat);

            //with audio flush and pause check to prevent death spiral
            while ((!parsingComplete) && (!flushComplete) && (!pauseEventComplete));

            mArtistField.setText(artistName);
            mTrackField.setText(trackName);
            mPlayer.play(trackUri);
        }
        if (eventType.name().equals("PAUSE"))
            pauseEventComplete = true;
        if (eventType.name().equals("AUDIO_FLUSH"))
            flushComplete = true;
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String errorDetails) {
        Log.d("MainActivity", "Playback error received: " + errorType.name());
    }

    @Override
    protected void onDestroy() {
        Spotify.destroyPlayer(this);
        super.onDestroy();
    }

}
