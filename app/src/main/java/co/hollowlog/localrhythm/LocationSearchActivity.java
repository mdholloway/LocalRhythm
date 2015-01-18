package co.hollowlog.localrhythm;

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
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * © 2014 michael david holloway
 */
public class LocationSearchActivity extends Activity {

    public static Activity la;
    private LocationManager mLocationManager;
    private LocationListener locationListener;
    private List<Address> mAddresses;
    private double mCurrentLat;
    private double mCurrentLong;
    private String mStreetAddress;
    private String mCityStateZip;
    private String mCityName;

    private static final String ACTION_LOCATION = "co.hollowlog.localrhythm.ACTION_LOCATION";

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
            mStreetAddress = mAddresses.get(0).getAddressLine(0);
            mCityStateZip = mAddresses.get(0).getAddressLine(1);
            mCityName = mCityStateZip.split(",")[0];

            Toast.makeText(this, "Found location: " + mStreetAddress + ", "
                    + mCityStateZip, Toast.LENGTH_SHORT).show();

            Intent launchPlayer = new Intent(LocationSearchActivity.this,
                    PlayerActivity.class);
            launchPlayer.putExtra("city", mCityName);
            startActivity(launchPlayer);
            finish();
        } else
            Toast.makeText(LocationSearchActivity.this, "Error finding location...",
                    Toast.LENGTH_SHORT).show();
    }

    private PendingIntent getLocationPendingIntent(boolean shouldCreate){
        Intent broadcast = new Intent(ACTION_LOCATION);
        int flags = shouldCreate ? 0 : PendingIntent.FLAG_NO_CREATE;
        return PendingIntent.getBroadcast(this, 0, broadcast, flags);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landing);
        la = this;
    }

    @Override
    protected void onStart() {
        super.onStart();
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
    protected void onStop() {
        super.onStop();
        this.unregisterReceiver(mBroadcastReceiver);
    }
}