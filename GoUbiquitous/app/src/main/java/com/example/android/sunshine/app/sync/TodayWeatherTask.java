package com.example.android.sunshine.app.sync;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.example.android.sunshine.app.BuildConfig;
import com.example.android.sunshine.app.SunshineApplication;
import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.Weather;
import com.google.android.gms.wearable.DataMap;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author Carlos Piñan
 */
public class TodayWeatherTask extends AsyncTask<Void, Void, Weather> {

    private static final String LOG_TAG = "TodayWeatherTask";

    public static final String KEY_WEATHER_LOW_TEMP = "low_temp";
    public static final String KEY_WEATHER_MAX_TEMP = "max_temp";
    public static final String KEY_WEATHER_ICON = "weather_icon";

    private IWeatherListener listener;

    public TodayWeatherTask(IWeatherListener listener) {
        this.listener = listener;
    }

    @Override
    protected Weather doInBackground(Void... params) {
        String locationQuery = Utility.getPreferredLocation(SunshineApplication.getContext());

        // These two need to be declared outside the try/catch
        // so that they can be closed in the finally block.
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        // Will contain the raw JSON response as a string.
        String forecastJsonStr;

        String format = "json";
        String units = "metric";

        try {
            final String FORECAST_BASE_URL =
                    "http://api.openweathermap.org/data/2.5/weather?";
            final String QUERY_PARAM = "q";
            final String FORMAT_PARAM = "mode";
            final String UNITS_PARAM = "units";
            final String APPID_PARAM = "APPID";

            Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                    .appendQueryParameter(QUERY_PARAM, locationQuery)
                    .appendQueryParameter(FORMAT_PARAM, format)
                    .appendQueryParameter(UNITS_PARAM, units)
                    .appendQueryParameter(APPID_PARAM, BuildConfig.OPEN_WEATHER_MAP_API_KEY)
                    .build();

            URL url = new URL(builtUri.toString());

            // Create the request to OpenWeatherMap, and open the connection
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            // Read the input stream into a String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                // Nothing to do.
                return null;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                // But it does make debugging a *lot* easier if you print out the completed
                // buffer for debugging.
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                return null;
            }
            forecastJsonStr = buffer.toString();
            return getWeatherDataFromJson(forecastJsonStr);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error ", e);
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing stream", e);
                }
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Weather weather) {
        Context context = SunshineApplication.getContext();
        int weatherId = weather.getWeatherId();
        DataMap config = new DataMap();
        config.putString(KEY_WEATHER_LOW_TEMP, weather.getLow());
        config.putString(KEY_WEATHER_MAX_TEMP, weather.getHigh());
        int artResourceId = Utility.getArtResourceForWeatherCondition(weatherId);
        Bitmap bitmap = Utility.getResizedBitmap(BitmapFactory.decodeResource(context.getResources(), artResourceId), 40, 40);
        listener.sendWeatherData(config);
        listener.sendWeatherIcon(bitmap);
//        listener.sendWeather(bitmap, config);
    }

    private Weather getWeatherDataFromJson(String forecastJsonStr)
            throws JSONException {
        final String OWM_COORD = "coord";

        // Location coordinate
        final String OWM_LATITUDE = "lat";
        final String OWM_LONGITUDE = "lon";

        // All temperatures are children of the "temp" object.
        final String OWM_TEMPERATURE = "main";
        final String OWM_MAX = "temp_max";
        final String OWM_MIN = "temp_min";

        final String OWM_WEATHER = "weather";
        final String OWM_WEATHER_ID = "id";

        final String OWM_MESSAGE_CODE = "cod";

        try {
            JSONObject forecastJson = new JSONObject(forecastJsonStr);

            if (forecastJson.has(OWM_MESSAGE_CODE)) {
                int errorCode = forecastJson.getInt(OWM_MESSAGE_CODE);

                switch (errorCode) {
                    case HttpURLConnection.HTTP_OK:
                        break;
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        return null;
                    default:
                        return null;
                }
            }

            JSONObject cityCoord = forecastJson.getJSONObject(OWM_COORD);
            double cityLatitude = cityCoord.getDouble(OWM_LATITUDE);
            double cityLongitude = cityCoord.getDouble(OWM_LONGITUDE);

            double high;
            double low;

            int weatherId;

            JSONObject weatherObject = forecastJson.getJSONArray(OWM_WEATHER).getJSONObject(0);
            weatherId = weatherObject.getInt(OWM_WEATHER_ID);

            JSONObject temperatureObject = forecastJson.getJSONObject(OWM_TEMPERATURE);
            high = temperatureObject.getDouble(OWM_MAX);
            low = temperatureObject.getDouble(OWM_MIN);

            Weather weather = new Weather(weatherId, cityLatitude, cityLongitude, Utility.formatTemperature(SunshineApplication.getContext(), high), Utility.formatTemperature(SunshineApplication.getContext(), low));
            return weather;
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
            return null;
        }
    }

}
