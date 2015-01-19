package co.hollowlog.localrhythm;

/**
 * © 2015 michael david holloway
 */
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Random;

public class PlayerActivity extends Activity {

    private static final String ECHONEST_API_KEY = "MY-API-KEY";
    private static final String FMA_API_KEY = "ANOTHER-API-KEY";

    private boolean paused = false;
    private boolean playing = false;
    private boolean noArtistsFound = false;

    private String artistName;
    private String artistNameUrlFormat;
    private String artistFmaId;
    private String trackName;
    private String trackUrl;
    private String trackFile;
    private String city;
    private String cityUrlFormat;

    private MediaPlayer mPlayer;
    private AudioManager am;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;

    private ToggleButton playPauseButton;
    private Button nextButton;
    private TextView mArtistField;
    private TextView mTrackField;
    private TextView mLocationField;
    private ImageView mImageView;
    private ImageView artistInfoButton;
    private Bitmap imgDisplayBmp;
    private JSONArray artists;
    private Uri artistInfoUri;
    private URL imageUrl;

    private volatile boolean parsingComplete = false;
    private volatile boolean haveTrackFile = false;

    private void getLocation() {
        city = getIntent().getStringExtra("city");
        if (city == null) {
            finish();
        } else
            cityUrlFormat = city.replace(" ", "%20");
    }

    private void getAudioManager() {
        am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(afChangeListener,
                // Use the music stream.
                AudioManager.STREAM_MUSIC,
                // Request permanent focus.
                AudioManager.AUDIOFOCUS_GAIN);
    }

    private void play(String url) {
        try {
            mPlayer.setDataSource(url);
            mPlayer.prepare(); // might take long! (for buffering, etc)
            mPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        LocationSearchActivity.la.finish();
        getLocation();

        playPauseButton = (ToggleButton) findViewById(R.id.playPauseButton);
        nextButton = (Button) findViewById(R.id.nextButton);
        mImageView = (ImageView) findViewById(R.id.album_art);
        mArtistField = (TextView) findViewById(R.id.artist_field);
        mTrackField = (TextView) findViewById(R.id.track_field);
        artistInfoButton = (ImageView) findViewById(R.id.artist_info);
        mLocationField = (TextView) findViewById(R.id.location_field);
        mLocationField.setText(city);

        playPauseButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if (paused) {
                    playPauseButton.setBackgroundResource(R.drawable.ic_media_pause);
                    paused = false;
                    playing = true;
                    mPlayer.start();
                } else if (playing) {
                    playPauseButton.setBackgroundResource(R.drawable.ic_media_play);
                    playing = false;
                    paused = true;
                    mPlayer.pause();
                } else {
                    parsingComplete = false;
                    getSong(cityUrlFormat);

                    //pause execution until JSON is parsed & needed values extracted
                    while (!parsingComplete);

                    if (noArtistsFound)
                        Toast.makeText(PlayerActivity.this, "No artists found!",
                                Toast.LENGTH_SHORT).show();
                    else {
                        mArtistField.setText(artistName);
                        mTrackField.setText(trackName);
                        mImageView.setImageBitmap(imgDisplayBmp);
                        play(trackFile);
                        playPauseButton.setBackgroundResource(R.drawable.ic_media_pause);

                        artistInfoButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent artistBrowser = new Intent("android.intent.action.VIEW",
                                        artistInfoUri);
                                PlayerActivity.this.startActivity(artistBrowser);
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
                mPlayer.reset();
                getSong(cityUrlFormat);

                //with audio flush and pause check to prevent death spiral
                while (!parsingComplete);

                mArtistField.setText(artistName);
                mTrackField.setText(trackName);
                mImageView.setImageBitmap(imgDisplayBmp);
                play(trackFile);
                playing = true;
            }
        });

        getAudioManager();
        mPlayer = new MediaPlayer();
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                getNewTrack();
            }
        });
    }

    private void getSong(String loc) {
        final String echoNestUrl = "https://developer.echonest.com/api/v4/artist/search?api_key="
            + ECHONEST_API_KEY
                + "&format=json&artist_location=city:" + loc + "&bucket=id:fma&results=40";

        Thread thread = new Thread(new Runnable(){
            @Override
            public void run() {
                callApi("echonest", echoNestUrl);
                if (!(artistFmaId.equals(""))) {
                    String fmaUrl = "http://freemusicarchive.org/api/get/tracks.json?api_key=" +
                            FMA_API_KEY + "&artist_id=" + artistFmaId;
                    callApi("fma", fmaUrl);
                }
                parsingComplete = true;
            }
        });
        thread.start();
    }

    private void callApi(String service, String urlString){
        try {
            URL url = new URL(urlString);
            URLConnection urlConn = url.openConnection();
            urlConn.setReadTimeout(10000);
            urlConn.setConnectTimeout(15000);
            urlConn.setDoInput(true);
            urlConn.connect();
            InputStream responseStream = urlConn.getInputStream();
            String responseString = convertStreamToString(responseStream);
            JSONObject responseJson = new JSONObject(responseString);
            if (service.equals("echonest"))
                parseEchoNestJSON(responseJson);
            else if (service.equals("fma"))
                parseFmaJSON(responseJson);
            responseStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseEchoNestJSON(JSONObject echoNestResponse){
        try {
            JSONObject response  = echoNestResponse.getJSONObject("response");
            artists = response.getJSONArray("artists");
            if (artists.length() > 0) {
                //get random artist from array
                int idx = new Random().nextInt(artists.length());
                JSONObject selectedArtist = (artists.getJSONObject(idx));
                //get artist info
                artistName = selectedArtist.getString("name");
                artistNameUrlFormat = artistName.replace(" ", "%20");
                JSONArray foreignIds = selectedArtist.getJSONArray("foreign_ids");
                JSONObject foreignId = foreignIds.getJSONObject(0);
                String str = foreignId.getString("foreign_id");
                artistFmaId = str.split(":")[2];
                artistInfoUri = Uri.parse("https://en.wikipedia.org/wiki/" + artistNameUrlFormat);
            } else
                noArtistsFound = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseFmaJSON(JSONObject fmaResponse){
        try {
            JSONArray dataset = fmaResponse.getJSONArray("dataset");
            if (dataset.length() > 0) {
                //get random track
                int idx2 = new Random().nextInt(dataset.length());
                JSONObject selectedTrack = (dataset.getJSONObject(idx2));
                //get track info
                trackName = selectedTrack.getString("track_title");
                trackUrl = selectedTrack.getString("track_url");
                haveTrackFile = false;
                getTrackFile();
                while(!haveTrackFile);
                imageUrl = new URL(selectedTrack.getString("track_image_file"));
                imgDisplayBmp = BitmapFactory.decodeStream(imageUrl.openConnection().
                        getInputStream());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getTrackFile() {
        Thread getTrackFileThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URLConnection refreshConnection = new URL(trackUrl).openConnection();
                    InputStream ips = refreshConnection.getInputStream();
                    String pageHtml = convertStreamToString(ips);
                    int begin = pageHtml.indexOf("<a href=\"http://freemusicarchive.org/music/download/");
                    int end = pageHtml.indexOf("\"", begin + 10);
                    trackFile = pageHtml.substring(begin + 9, end);
                    haveTrackFile = true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        getTrackFileThread.start();
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    private void getNewTrack(){
        parsingComplete = false;
        mPlayer.reset();
        getSong(cityUrlFormat);

        //pause execution until JSON is parsed & needed values extracted
        while (!parsingComplete);

        mArtistField.setText(artistName);
        mTrackField.setText(trackName);
        mImageView.setImageBitmap(imgDisplayBmp);
        play(trackFile);
        playPauseButton.setBackgroundResource(R.drawable.ic_media_pause);
        playing = true;
    }

}
