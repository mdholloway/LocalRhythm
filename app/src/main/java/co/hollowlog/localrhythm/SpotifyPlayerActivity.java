package co.hollowlog.localrhythm;

/**
 * © 2014 michael david holloway
 */
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.spotify.sdk.android.Spotify;
import com.spotify.sdk.android.playback.Config;
import com.spotify.sdk.android.playback.ConnectionStateCallback;
import com.spotify.sdk.android.playback.Player;
import com.spotify.sdk.android.playback.PlayerNotificationCallback;
import com.spotify.sdk.android.playback.PlayerState;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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
    private ImageView mImageView;
    private ImageView artistInfoButton;
    private static SSLSocketFactory hollowlogFactory;
    private JSONArray artists;
    private AudioManager am;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;

    private Bitmap imgDisplayBmp;
    private boolean paused = false;
    private boolean playing = false;
    private boolean flushComplete = false;
    private boolean pauseEventComplete = false;
    private boolean haveRefreshToken = false;
    private boolean haveSong = false;
    private boolean noArtistsFound = false;
    private ProgressWheel wheel;
    private String artistName;
    private String artistNameUrlFormat;
    private String artistSpotifyId = "";
    private String trackName;
    private String trackUri = "";
    private String city;
    private String cityUrlFormat;
    private String accessToken = "";
    private String uid;
    private Uri uri;
    private Uri artistInfoUri;

    public volatile boolean parsingComplete = false;
    public volatile boolean refreshTokenCheckComplete = false;
    public volatile boolean callbackComplete = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        LandingActivity.la.finish();

        uid = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
        city = getIntent().getStringExtra("city");
        Log.d("test", "value of city: " + city);
        if (city == null) {
            finish();
        } else
            cityUrlFormat = city.replace(" ", "%20");

        playPauseButton = (ToggleButton) findViewById(R.id.playPauseButton);
        nextButton = (Button) findViewById(R.id.nextButton);
        mImageView = (ImageView) findViewById(R.id.album_art);
        mArtistField = (TextView) findViewById(R.id.artist_field);
        mTrackField = (TextView) findViewById(R.id.track_field);
        wheel = (ProgressWheel) findViewById(R.id.progress_wheel);
        artistInfoButton = (ImageView) findViewById(R.id.artist_info);
        mLocationField = (TextView) findViewById(R.id.location_field);
        mLocationField.setText(city);

        wheel.spin();

        playPauseButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
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
                    wheel.spin();
                    parsingComplete = false;
                    getSong(cityUrlFormat);

                    //pause execution until JSON is parsed & needed values extracted
                    while (!parsingComplete) ;

                    if (noArtistsFound)
                        Toast.makeText(SpotifyPlayerActivity.this, "No artists found!",
                                Toast.LENGTH_SHORT).show();
                    else {
                        mArtistField.setText(artistName);
                        mTrackField.setText(trackName);
                        mImageView.setImageBitmap(imgDisplayBmp);
                        wheel.stopSpinning();
                        mPlayer.play(trackUri);
                        playPauseButton.setBackgroundResource(R.drawable.ic_media_pause);

                        artistInfoButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent artistBrowser = new Intent("android.intent.action.VIEW",
                                        artistInfoUri);
                                SpotifyPlayerActivity.this.startActivity(artistBrowser);
                            }
                        });
                        artistInfoButton.setEnabled(true);

                        playing = true;
                    }
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
                wheel.spin();
                getSong(cityUrlFormat);

                //with audio flush and pause check to prevent death spiral
                while ((!parsingComplete) && (!flushComplete) && (!pauseEventComplete));

                do {
                    parsingComplete = false;
                    getSong(cityUrlFormat);
                    while (!parsingComplete);
                } while (!haveSong);

                mArtistField.setText(artistName);
                mTrackField.setText(trackName);
                mImageView.setImageBitmap(imgDisplayBmp);
                wheel.stopSpinning();
                mPlayer.play(trackUri);
                playing = true;
            }
        });

        refreshTokenCheckComplete = false;
        haveRefreshToken = false;

        am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(afChangeListener,
                // Use the music stream.
                AudioManager.STREAM_MUSIC,
                // Request permanent focus.
                AudioManager.AUDIOFOCUS_GAIN);

        trustHollowLog();
        checkRefreshToken();

        while (!refreshTokenCheckComplete);
        Log.d("auth", "value of accessToken:" + accessToken);

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

        Thread refreshTokenThread = new Thread(new Runnable() {
            @Override
            public void run() {
            try {
                URLConnection refreshConnection = new URL("https://hollowlog.co:8443/refresh_token?uid="
                        + uid).openConnection();
                ((HttpsURLConnection) refreshConnection).setSSLSocketFactory(hollowlogFactory);
                InputStream ips = refreshConnection.getInputStream();
                accessToken = convertStreamToString(ips);
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

        Log.d("Test", "onNewIntent reached, accessToken = " + accessToken + ", haveRefreshToken = " + haveRefreshToken);
        final Uri authResponse = intent.getData();
        if (!haveRefreshToken) {
            final String authCode = authResponse.getQueryParameter("code");
            // send authorization code to hollowlog.co/callback for access token
            Thread callbackThread = new Thread(new Runnable() {
                @Override
                public void run() {
                try {
                    URLConnection callbackConnection = new URL("https://hollowlog.co:8443/callback?uid="
                            + uid + "&code=" + authCode).openConnection();
                    ((HttpsURLConnection) callbackConnection).setSSLSocketFactory(hollowlogFactory);
                    InputStream ips = callbackConnection.getInputStream();
                    accessToken = convertStreamToString(ips);
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
                afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
                    public void onAudioFocusChange(int focusChange) {
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                           mPlayer.pause();
                        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                            mPlayer.resume();
                        }
                    }
                };

                mPlayer.addConnectionStateCallback(SpotifyPlayerActivity.this);
                mPlayer.addPlayerNotificationCallback(SpotifyPlayerActivity.this);
                playPauseButton.setEnabled(true);
                nextButton.setEnabled(true);
                wheel.stopSpinning();
            }

            @Override
            public void onError(Throwable throwable) {
                Log.e("SpotifyPlayerActivity", "Could not initialize player: "
                        + throwable.getMessage());
                uri = Uri.parse(authUrl);
                Intent launchAuthWindow = new Intent("android.intent.action.VIEW", uri);
                SpotifyPlayerActivity.this.startActivity(launchAuthWindow);
                Toast.makeText(SpotifyPlayerActivity.this,
                      "Error initializing player: Spotify Premium account required.",
                            Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void getSong(String loc) {

        artistSpotifyId = "";
        trackUri = "";

        final String urlBuilder = "https://developer.echonest.com/api/v4/artist/search?api_key="
            + ECHONEST_API_KEY
                + "&format=json&artist_location=city:" + loc + "&bucket=id:spotify&results=40";

        Thread thread = new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    URL url = new URL(urlBuilder);
                    URLConnection conn = url.openConnection();
                    conn.setReadTimeout(10000);
                    conn.setConnectTimeout(15000);
                    conn.setDoInput(true);
                    conn.connect();
                    InputStream echoNestStream = conn.getInputStream();
                    String data = convertStreamToString(echoNestStream);
                    try {
                        JSONObject reader = new JSONObject(data);
                        JSONObject response  = reader.getJSONObject("response");
                        artists = response.getJSONArray("artists");

                        if (artists.length() > 0) {
                            //get random artist from array
                            int idx = new Random().nextInt(artists.length());
                            JSONObject selectedArtist = (artists.getJSONObject(idx));
                            artistName = selectedArtist.getString("name");
                            artistNameUrlFormat = artistName.replace(" ", "%20");

                            JSONArray foreignIds = selectedArtist.getJSONArray("foreign_ids");
                            JSONObject foreignId = foreignIds.getJSONObject(0);
                            String str = foreignId.getString("foreign_id");
                            artistSpotifyId = str.split(":")[2];
                            artistInfoUri = Uri.parse("https://en.wikipedia.org/wiki/" +
                                    artistNameUrlFormat);
                        } else
                            noArtistsFound = true;

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    echoNestStream.close();

                    if (artists.length() > 0) {
                        final String urlBuilder2 = "https://api.spotify.com/v1/artists/"
                                + artistSpotifyId + "/top-tracks?country=US";
                        try {
                            URL url2 = new URL(urlBuilder2);
                            URLConnection conn2 = url2.openConnection();
                            conn2.setReadTimeout(10000);
                            conn2.setConnectTimeout(15000);
                            conn2.setDoInput(true);
                            conn2.connect();
                            InputStream spotifyStream = conn2.getInputStream();
                            String data2 = convertStreamToString(spotifyStream);
                            try {
                                JSONObject reader = new JSONObject(data2);
                                JSONArray tracks = reader.getJSONArray("tracks");

                                //get random track from array
                                if (tracks.length() > 0) {
                                    int idx2 = new Random().nextInt(tracks.length());
                                    JSONObject selectedTrack = (tracks.getJSONObject(idx2));

                                    trackName = selectedTrack.getString("name");
                                    trackUri = selectedTrack.getString("uri");

                                    JSONObject album = selectedTrack.getJSONObject("album");
                                    JSONArray images = album.getJSONArray("images");
                                    JSONObject bestImage = images.getJSONObject(0);
                                    URL imageUrl = new URL(bestImage.getString("url"));
                                    imgDisplayBmp = BitmapFactory.decodeStream(imageUrl.openConnection().
                                            getInputStream());
                                    haveSong = true;
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            spotifyStream.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                parsingComplete = true;
            }
        });
        thread.start();
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public static void trustHollowLog() {
        // Don't throw certificate chain validation exceptions for my own site with unrecognized issuing CA (Gandi.net)
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
                           String authType) throws java.security.cert.CertificateException {}

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
                           String authType) throws java.security.cert.CertificateException {}

            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[]{};
            }
        }};

        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            hollowlogFactory = sc.getSocketFactory();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            getSong(cityUrlFormat);

            //with audio flush and pause check to prevent death spiral
            while ((!parsingComplete) && (!flushComplete) && (!pauseEventComplete));

            mArtistField.setText(artistName);
            mTrackField.setText(trackName);
            mImageView.setImageBitmap(imgDisplayBmp);
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
