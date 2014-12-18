package co.hollowlog.localrhythm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * © 2014 michael david holloway
 */
public class LocationReceiver extends BroadcastReceiver {

    private Context mContext;

    public LocationReceiver (Context context){
        mContext = context;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.hasExtra(LocationManager.KEY_PROVIDER_ENABLED)){
            boolean enabled = intent.getBooleanExtra(LocationManager.KEY_PROVIDER_ENABLED, false);
            onProviderEnabledChanged(enabled, context);
        }
    }

    protected void onProviderEnabledChanged(boolean enabled, Context context){
        Toast.makeText(context, "Location services " + (enabled ? "enabled" : "disabled"),
                Toast.LENGTH_SHORT).show();
    }
}
