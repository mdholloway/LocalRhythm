package co.hollowlog.localrhythm;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;

/**
 * © 2014 michael david holloway
 */
public class LocationAgent {

    public static final String ACTION_LOCATION = "co.hollowlog.localrhythm.ACTION_LOCATION";

    protected static LocationAgent sLocationAgent;
    private Context mAppContext;
    protected LocationManager mLocationManager;

    private LocationAgent(Context appContext){
        mAppContext = appContext;
        mLocationManager = (LocationManager)mAppContext.getSystemService(Context.LOCATION_SERVICE);
    }

    public static LocationAgent get(Context c){
        if (sLocationAgent == null){
            sLocationAgent = new LocationAgent(c.getApplicationContext());
        }
        return sLocationAgent;
    }

    private PendingIntent getLocationPendingIntent(boolean shouldCreate){
        Intent broadcast = new Intent(ACTION_LOCATION);
        int flags = shouldCreate ? 0 : PendingIntent.FLAG_NO_CREATE;
        return PendingIntent.getBroadcast(mAppContext, 0, broadcast, flags);
    }

    public void startLocationUpdates() {
        PendingIntent pi = getLocationPendingIntent(true);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 100, pi);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 100, pi);
    }

    public void stopLocationUpdates() {
        PendingIntent pi = getLocationPendingIntent(false);
        if (pi != null){
            mLocationManager.removeUpdates(pi);
            pi.cancel();
        }
    }

    public boolean isTrackingLocation() {
        return getLocationPendingIntent(false) != null;
    }
}
