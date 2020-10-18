package com.example.spotifylyrics;

import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.spotify.sdk.android.auth.AuthorizationClient;
import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author  Elusive Fox
 * @version 1.0
 * @lastupdate 18-10-2020
 */

public class MainActivity extends AppCompatActivity {
    public static final String CLIENT_ID = "d48eba7ba15141d8b276ab2daf1f3fcb";
    public static final int AUTH_TOKEN_REQUEST_CODE = 0x10;

    private final OkHttpClient mOkHttpClient = new OkHttpClient();
    private String mAccessToken;

    private String q;
    private String lyrics_path;
    private String lyrics = "";

    /**
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        final AuthorizationRequest request = getAuthenticationRequest(AuthorizationResponse.Type.TOKEN);
        AuthorizationClient.openLoginActivity(this, AUTH_TOKEN_REQUEST_CODE, request);

        setContentView(R.layout.activity_main);
        getSupportActionBar().setTitle(String.format(
                Locale.US, "Spotify Lyrics by ElusiveFox "));
    }

    /**
     *
     * @param view
     * @throws IOException
     * @throws JSONException
     */
    public void onGetUserProfileClicked(View view) throws IOException, JSONException {
        // -=-=-=-=-=-=-=-=-=-=-=-= Stage 1 -> spotify current track =-=-=-=-=-=-=-=-=-=-=-=-
        lyrics = "";
        if (mAccessToken == null) {
            final Snackbar snackbar = Snackbar.make(findViewById(R.id.activity_main), R.string.warning_need_token, Snackbar.LENGTH_SHORT);
            snackbar.getView().setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent));
            snackbar.show();
            return;
        }
        q = doSpotifyRequest();
        // -=-=-=-=-=-=-=-=-=-=-=-= Stage 2 -> genius lyrics search =-=-=-=-=-=-=-=-=-=-=-=-
        //setResponse("Stage 2 -> genius lyrics search");
        String url = "https://api.genius.com/search?access_token=SKoRjfPWn_OKmkIUks5sFhKPUQ2UTbOlyhvsDVmnBzSBBSasOJW0Z_GqrD9Ul2sA&q="+q;
        String response2 = doGetRequest(url);

        JSONObject jsonObject = new JSONObject(response2);
        //response
        JSONObject responseJSONObject = jsonObject.getJSONObject("response");
        //hits
        JSONArray hits = responseJSONObject.getJSONArray("hits");
        //result
        JSONObject result = hits.getJSONObject(0);
        //path
        JSONObject result2 = result.getJSONObject("result");
        lyrics_path = result2.getString("path");

        // -=-=-=-=-=-=-=-=-=-=-=-= Stage 3 -> genius lyrics  =-=-=-=-=-=-=-=-=-=-=-=-
        String url2 = "https://genius.com"+lyrics_path;
        setResponse(url2);
        String response3 = doGetRequest(url2);
        Pattern p = Pattern.compile("(<div class=\\\"[l|L]yrics.*?>)(.+?)</div>", Pattern.DOTALL);
        Matcher m = p.matcher(response3);

        while (m.find()){
            lyrics += m.group(2);
        }
        if(lyrics == null){
            setResponse("Regex didnt match. URL: "+url2);
        }
        // -=-=-=-=-=-=-=-=-=-=-=-= Stage 4 -> beautify lyrics  =-=-=-=-=-=-=-=-=-=-=-=-
        setResponse(beautifyLyrics(lyrics));
    }

    /**
     *
     * @param type
     * @return
     */
    private AuthorizationRequest getAuthenticationRequest(AuthorizationResponse.Type type) {
        return new AuthorizationRequest.Builder(CLIENT_ID, type, "http://localhost:8888/callback")
                .setShowDialog(false)
                .setScopes(new String[]{"user-read-currently-playing"})
                .build();
    }

    /**
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final AuthorizationResponse response = AuthorizationClient.getResponse(resultCode, data);
        if (response.getError() != null && !response.getError().isEmpty()) {
            setResponse(response.getError());
        }
        mAccessToken = response.getAccessToken();
    }

    /**
     *
     * @param text
     */
    private void setResponse(final String text) {
        runOnUiThread(() -> {
            final TextView responseView = findViewById(R.id.response_text_view);
            responseView.setText(text);
        });
    }

    /**
     *
     * @param text
     */
    private void setTitle(final String text) {
        runOnUiThread(() -> {
            final TextView responseView = findViewById(R.id.tile);
            responseView.setText(text);
        });
    }

    /**
     *
     * @param url
     * @return
     * @throws IOException
     */
    String doGetRequest(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();
        Response response = mOkHttpClient.newCall(request).execute();
        return response.body().string();
    }

    /**
     *
     * @return
     * @throws IOException
     */
    String doSpotifyRequest() throws IOException {
        Request request = new Request.Builder()
                .url("https://api.spotify.com/v1/me/player/currently-playing")
                .addHeader("Authorization","Bearer " + mAccessToken)
                .build();
        Response response = mOkHttpClient.newCall(request).execute();
        try {
            JSONObject spotifyJsonObject = new JSONObject(response.body().string());
            //item
            JSONObject item = spotifyJsonObject.getJSONObject("item");
            //song name
            String songName = item.getString("name");
            if(songName.contains("-")){
                songName = songName.substring(0,songName.indexOf("-")-1);
            }
            //artists
            String artistsResult = "";
            JSONArray artists = item.getJSONArray("artists");
            for (int i = 0; i < artists.length(); i++){
                JSONObject artist = artists.getJSONObject(i);
                String artistName = artist.getString("name");
                artistsResult += " " + artistName;
            }
            String search = songName + artistsResult;
            setTitle("\""+songName+"\""+" by "+artistsResult);
            search = URLEncoder.encode(search, "utf-8");


            return search;
        } catch (JSONException e) {
            setResponse("Failed to parse data: " + e);
            return "error";
        }

    }

    /**
     *
     * @param lyrics
     * @return
     */
    String beautifyLyrics(String lyrics) {
        if (lyrics.contains("<!--sse-->")){
            lyrics = lyrics.substring(50);
            lyrics = lyrics.replaceAll("(?s)<a href=.+?>", "");
            lyrics = lyrics.replaceAll("<!--.+?-->", "");
            lyrics = lyrics.replaceAll("<.+?>", "");
            lyrics = lyrics.replaceAll("&quot;", "\"");
            lyrics = lyrics.replaceAll("&amp;", "&");
            return lyrics;
        }
        else{
            lyrics = lyrics.replaceAll("<br/>", "\n");
            lyrics = lyrics.replaceAll("(?s)<a href=.+?>", "");
            lyrics = lyrics.replaceAll("<.+?>", "");
            lyrics = lyrics.replaceAll("&#x27;", "'");
            lyrics = lyrics.replaceAll("&quot;", "\"");
            lyrics = lyrics.replaceAll("&amp;", "&");
            return lyrics;
        }
    }
}