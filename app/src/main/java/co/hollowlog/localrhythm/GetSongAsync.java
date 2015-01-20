package co.hollowlog.localrhythm;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.View;
import android.widget.ProgressBar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Random;

/**
 * Created by michaeldavidholloway on 1/19/15.
 */
public class GetSongAsync extends AsyncTask<String, Void, Void> {

    private static final String ECHONEST_API_KEY = "YBQ15XC2DALJZWBUC";
    private static final String FMA_API_KEY = "VR0OE2AKNKZSMNYX";

    protected String artistFmaId;
    protected String artistNameUrlFormat;
    protected String trackUrl;
    protected String trackFile;
    protected String artistName;
    protected String trackName;

    private PlayerActivity activity;
    private ProgressBar mProgWheel;
    private JSONArray artists;
    private URL imageUrl;
    private Bitmap imgDisplayBmp;

    private volatile boolean haveTrackFile = false;

    public GetSongAsync(PlayerActivity activity) {
        this.activity = activity;
        this.mProgWheel = (ProgressBar) this.activity.findViewById(R.id.progbar);
    }

    protected void onPreExecute() {
        mProgWheel.setVisibility(View.VISIBLE);
    }

    protected Void doInBackground(String... location) {
        getSong(location[0]);
        return null;
    }

    protected void onPostExecute(Void result) {
        mProgWheel.setVisibility(View.GONE);
        PlayerActivity.mArtistField.setText(artistName);
        PlayerActivity.mTrackField.setText(trackName);
        PlayerActivity.mImageView.setImageBitmap(imgDisplayBmp);
        PlayerActivity.play(trackFile);
    }

    private void getSong(String loc) {
        final String echoNestUrl = "https://developer.echonest.com/api/v4/artist/search?api_key="
            + ECHONEST_API_KEY
                + "&format=json&artist_location=city:" + loc + "&bucket=id:fma&results=40";

        callApi("echonest", echoNestUrl);
        if (!(artistFmaId.equals(""))) {
            String fmaUrl = "http://freemusicarchive.org/api/get/tracks.json?api_key=" +
                    FMA_API_KEY + "&artist_id=" + artistFmaId;
            callApi("fma", fmaUrl);
        }
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
                PlayerActivity.artistInfoUri = Uri.parse("https://en.wikipedia.org/wiki/" + artistNameUrlFormat);
            } else
                PlayerActivity.noArtistsFound = true;
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

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}
