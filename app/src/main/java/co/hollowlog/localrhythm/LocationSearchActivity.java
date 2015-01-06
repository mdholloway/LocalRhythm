package co.hollowlog.localrhythm;

import android.app.Activity;
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
                mCurrentLat = loc.getLatitude();
                mCurrentLong = loc.getLongitude();

                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                try {
                    mAddresses = geocoder.getFromLocation(mCurrentLat, mCurrentLong, 1);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (mAddresses != null) {
                    mStreetAddress = mAddresses.get(0).getAddressLine(0);
                    mCityStateZip = mAddresses.get(0).getAddressLine(1);
                    mCityName = mCityStateZip.split(",")[0];

                    Toast.makeText(context, "Found location: " + mStreetAddress + ", "
                            + mCityStateZip, Toast.LENGTH_SHORT).show();

                    Intent launchPlayer = new Intent(LocationSearchActivity.this,
                            SpotifyPlayerActivity.class);
                    launchPlayer.putExtra("city", mCityName);
                    startActivity(launchPlayer);
                    finish();
                } else
                    Toast.makeText(LocationSearchActivity.this, "Error finding location...",
                            Toast.LENGTH_SHORT).show();
            }
        }
    };

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

        mLocationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        this.registerReceiver(mBroadcastReceiver, new IntentFilter(ACTION_LOCATION));
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
        }

        PendingIntent pi = getLocationPendingIntent(true);
        mLocationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, pi);
        mLocationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, pi);
    }

    @Override
    protected void onStop() {
        super.onStop();
        this.unregisterReceiver(mBroadcastReceiver);
    }
}