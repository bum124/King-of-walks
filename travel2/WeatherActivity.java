package com.example.travel2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class WeatherActivity extends AppCompatActivity {
    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/";
    private static final String PREFS_NAME = "WeatherPrefs";
    private static final String FAVORITES_KEY = "favorites";
    private static final String API_KEY = "e1d8e8cb7853df6ef8bf0fd2a2fa3b09";

    private static final String TAG = "WeatherActivity";

    private EditText cityEditText;
    private TextView weatherInfoText;
    private Button searchButton,btn_weather;
    private ProgressBar progressBar;
    private ImageButton favoriteButton;
    private ListView favoritesListView;
    private WeatherService weatherService;
    private SharedPreferences preferences;
    private Set<String> favoriteCities;
    private FavoriteCityAdapter favoritesAdapter;
    private String currentCity;
    private Map<String, FavoriteCityAdapter.WeatherInfo> weatherData;

    public interface WeatherService {
        @GET("weather")
        Call<WeatherResponse> getWeather(
            @Query("q") String city,
            @Query("appid") String apiKey,
            @Query("units") String units
        );
    }

    public static class WeatherResponse {
        public MainData main;
        public List<WeatherData> weather;
        public String name;

        public static class MainData {
            public float temp;
            public float feels_like;
            public int humidity;
            public float pressure;
        }

        public static class WeatherData {
            public String main;
            public String description;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather);

        if (savedInstanceState != null) {
            currentCity = savedInstanceState.getString("currentCity");
        }

        initializeViews();
        setupRetrofit();
        loadFavorites();
        setupClickListeners();

        if (currentCity != null && !currentCity.isEmpty()) {
            cityEditText.setText(currentCity);
            searchWeather();
        }
    }

    private void initializeViews() {
        cityEditText = findViewById(R.id.cityEditText);
        weatherInfoText = findViewById(R.id.weatherInfoText);
        searchButton = findViewById(R.id.searchButton);
        btn_weather = findViewById(R.id.btn_weather);
        progressBar = findViewById(R.id.progressBar);
        favoriteButton = findViewById(R.id.favoriteButton);
        favoritesListView = findViewById(R.id.favoritesListView);
    }

    private void setupRetrofit() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        weatherService = retrofit.create(WeatherService.class);
    }

    private void loadFavorites() {
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        favoriteCities = new HashSet<>(preferences.getStringSet(FAVORITES_KEY, new HashSet<>()));
        weatherData = new HashMap<>();

        List<String> favoritesList = new ArrayList<>(favoriteCities);
        favoritesAdapter = new FavoriteCityAdapter(this, favoritesList, weatherData);
        favoritesListView.setAdapter(favoritesAdapter);

        if (!favoriteCities.isEmpty() && isNetworkAvailable()) {
            for (String city : favoriteCities) {
                loadWeatherForCity(city);
            }
        }
    }

    private void setupClickListeners() {
        searchButton.setOnClickListener(v -> searchWeather());

        favoriteButton.setOnClickListener(v -> toggleFavorite());

        favoritesListView.setOnItemClickListener((parent, view, position, id) -> {
            String city = new ArrayList<>(favoriteCities).get(position);
            cityEditText.setText(city);
            searchWeather();
        });

        btn_weather.setOnClickListener(this::onClicked_function);
    }

    public void onClicked_function(View view) {
        Intent intent = null;

        if (view.getId() == R.id.btn_weather) {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://weather.naver.com/"));
        }
        if (intent != null) {
            startActivity(intent);
        }
    }

    private void searchWeather() {
        if (!isNetworkAvailable()) {
            showError(getString(R.string.weather_network_error));
            return;
        }

        currentCity = cityEditText.getText().toString().trim();
        if (currentCity.isEmpty()) {
            Toast.makeText(this, R.string.error_city_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        loadWeatherForCity(currentCity);
    }

    
    private void loadWeatherForCity(String city) {
        weatherService.getWeather(city, API_KEY, "metric").enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(@NonNull Call<WeatherResponse> call, @NonNull Response<WeatherResponse> response) {
                if (city.equals(currentCity)) {
                    showLoading(false);
                }
                
                if (response.isSuccessful() && response.body() != null) {
                    WeatherResponse weather = response.body();
                    if (weather.weather != null && !weather.weather.isEmpty()) {
                        String weatherInfo = String.format("%s\n%.1f°C\n%s",
                                weather.name,
                                weather.main.temp,
                                weather.weather.get(0).description);
                        
                        if (city.equals(currentCity)) {
                            weatherInfoText.setText(weatherInfo);
                            favoriteButton.setVisibility(View.VISIBLE);
                            updateFavoriteButton();
                        }

                        weatherData.put(city, new FavoriteCityAdapter.WeatherInfo(
                                weather.main.temp,
                                weather.weather.get(0).description
                        ));
                        favoritesAdapter.notifyDataSetChanged();
                    }
                } else {
                    if (city.equals(currentCity)) {
                        showError(getString(R.string.error_not_found));
                        favoriteButton.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<WeatherResponse> call, @NonNull Throwable t) {
                if (city.equals(currentCity)) {
                    showLoading(false);
                    showError(getString(R.string.error_occurred, t.getMessage()));
                    favoriteButton.setVisibility(View.GONE);
                }
            }
        });
    }

    private void toggleFavorite() {
        if (currentCity != null && !currentCity.isEmpty()) {
            if (favoriteCities.contains(currentCity)) {
                favoriteCities.remove(currentCity);
                weatherData.remove(currentCity);
            } else {
                favoriteCities.add(currentCity);
                loadWeatherForCity(currentCity);
            }
            
            preferences.edit()
                    .putStringSet(FAVORITES_KEY, favoriteCities)
                    .apply();
            
            updateFavoriteButton();
            favoritesAdapter.notifyDataSetChanged();
        }
    }

    private void updateFavoriteButton() {
        favoriteButton.setImageResource(
                favoriteCities.contains(currentCity) ?
                        R.drawable.ic_star_filled :
                        R.drawable.ic_star_border
        );
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;
        
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        weatherInfoText.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showError(String message) {
        weatherInfoText.setText(message);
        weatherInfoText.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("currentCity", currentCity);
    }
}
