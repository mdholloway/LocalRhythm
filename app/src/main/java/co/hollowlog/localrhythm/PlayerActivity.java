package co.hollowlog.localrhythm;

/**
 * © 2015 michael david holloway
 */
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class PlayerActivity extends Activity {

    private static final String ACTION_LOCATION = "co.hollowlog.localrhythm.ACTION_LOCATION";

    protected static boolean paused = false;
    protected static boolean playing = false;
    protected static boolean noArtistsFound = false;

    private double mCurrentLat;
    private double mCurrentLong;

    private String mCityStateZip;
    private String mCityName;
    private String cityUrlFormat;

    private static MediaPlayer mPlayer;
    private AudioManager am;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;

    protected static ToggleButton playPauseButton;
    private Button nextButton;
    protected static TextView mArtistField;
    protected static TextView mTrackField;
    private TextView mLocationField;
    protected static ImageView mImageView;
    private ImageView artistInfoButton;
    protected static Uri artistInfoUri;

    private LocationManager mLocationManager;
    private LocationListener locationListener;
    private List<Address> mAddresses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        playPauseButton = (ToggleButton) findViewById(R.id.playPauseButton);
        nextButton = (Button) findViewById(R.id.nextButton);
        mImageView = (ImageView) findViewById(R.id.album_art);
        mArtistField = (TextView) findViewById(R.id.artist_field);
        mTrackField = (TextView) findViewById(R.id.track_field);
        artistInfoButton = (ImageView) findViewById(R.id.artist_info);
        mLocationField = (TextView) findViewById(R.id.location_field);

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
                    new GetSongAsync(PlayerActivity.this).execute(cityUrlFormat);

                    if (noArtistsFound)
                        Toast.makeText(PlayerActivity.this, "No artists found!",
                                Toast.LENGTH_SHORT).show();
                    else {
                        artistInfoButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent artistBrowser = new Intent("android.intent.action.VIEW",
                                        artistInfoUri);
                                PlayerActivity.this.startActivity(artistBrowser);
                            }
                        });
                        artistInfoButton.setEnabled(true);
                    }
                }
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                mPlayer.reset();
                new GetSongAsync(PlayerActivity.this).execute(cityUrlFormat);
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

        this.registerReceiver(mBroadcastReceiver, new IntentFilter(ACTION_LOCATION));
        mLocationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        boolean gps_enabled = false, network_enabled = false;

        //check to see if location providers are enabled
        gps_enabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        network_enabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        // prompt user to enable location provider(s) if disabled
        if(!gps_enabled && !network_enabled){
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setMessage("LocalRhythm requires access to your location. " +
                    "Open settings now to enable?");
            // if yes, go to settings
            dialog.setPositiveButton("Open",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                            Intent myIntent = new Intent(Settings.ACTION_SETTINGS);
                            startActivity(myIntent);
                        }
                    });
            // quit if user declines to enable a location provider
            dialog.setNegativeButton("Cancel",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                            finish();
                        }
                    });
            dialog.show();
        } else {
            // Iterate through all the providers on the system, keeping
            // note of the most accurate result within the acceptable time limit.
            // If no result is found within maxTime, return the newest Location.
            List<String> matchingProviders = mLocationManager.getAllProviders();
            Location bestResult = null;
            float bestAccuracy = Float.MAX_VALUE;
            long bestTime = Long.MIN_VALUE;
            long minTime = System.currentTimeMillis() - AlarmManager.INTERVAL_FIFTEEN_MINUTES;

            for (String provider : matchingProviders) {
                Location location = mLocationManager.getLastKnownLocation(provider);
                if (location != null) {
                    float storedLocAccuracy = location.getAccuracy();
                    long storedLocTime = location.getTime();

                    if ((storedLocTime > minTime && storedLocAccuracy < bestAccuracy)) {
                        bestResult = location;
                        bestAccuracy = storedLocAccuracy;
                        bestTime = storedLocTime;
                    } else if (storedLocTime < minTime && bestAccuracy == Float.MAX_VALUE && storedLocTime > bestTime) {
                        bestResult = location;
                        bestTime = storedLocTime;
                    }
                }
            }

            if (bestResult != null && (!(locationListener != null && (bestTime < minTime || bestAccuracy > 1000)))) {
                parseLocData(bestResult);
            } else {
                PendingIntent pi = getLocationPendingIntent(true);
                mLocationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, pi);
                mLocationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, pi);
            }
        }
    }

    @Override
    protected void onDestroy() {
        mPlayer.release();
        this.unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    private void getNewTrack(){
        mPlayer.reset();
        new GetSongAsync(this).execute(cityUrlFormat);
    }

    protected static void play(String url) {
        try {
            mPlayer.setDataSource(url);
            mPlayer.prepare(); // might take long! (for buffering, etc)
            mPlayer.start();
            playPauseButton.setBackgroundResource(R.drawable.ic_media_pause);
            playing = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private PendingIntent getLocationPendingIntent(boolean shouldCreate){
        Intent broadcast = new Intent(ACTION_LOCATION);
        int flags = shouldCreate ? 0 : PendingIntent.FLAG_NO_CREATE;
        return PendingIntent.getBroadcast(this, 0, broadcast, flags);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent){
            Location loc = intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED);
            if (loc != null){
                parseLocData(loc);
            }
        }
    };

    private void parseLocData(Location loc){
        mCurrentLat = loc.getLatitude();
        mCurrentLong = loc.getLongitude();

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            mAddresses = geocoder.getFromLocation(mCurrentLat, mCurrentLong, 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (mAddresses != null) {
            //mStreetAddress = mAddresses.get(0).getAddressLine(0);
            mCityStateZip = mAddresses.get(0).getAddressLine(1);
            mCityName = mCityStateZip.split(",")[0];
            mLocationField.setText(mCityName);
            cityUrlFormat = mCityName.replace(" ", "%20");

            Toast.makeText(this, "Found location: " + mCityStateZip, Toast.LENGTH_SHORT).show();
        } else
            Toast.makeText(PlayerActivity.this, "Error finding location...",
                    Toast.LENGTH_SHORT).show();
    }

    private void getAudioManager() {
        am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);

        afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            public void onAudioFocusChange(int focusChange) {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    mPlayer.pause();
                } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                    mPlayer.start();
                }
            }
        };

        am.requestAudioFocus(afChangeListener,
                // Use the music stream.
                AudioManager.STREAM_MUSIC,
                // Request permanent focus.
                AudioManager.AUDIOFOCUS_GAIN);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about:
                Intent launchPlayer = new Intent(PlayerActivity.this, AboutActivity.class);
                startActivity(launchPlayer);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
