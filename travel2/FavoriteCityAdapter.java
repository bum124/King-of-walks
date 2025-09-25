package com.example.travel2;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Map;

public class FavoriteCityAdapter extends ArrayAdapter<String> {
    private final Context context;
    private final List<String> cities;
    private final Map<String, WeatherInfo> weatherData;

    public static class WeatherInfo {
        public final float temperature;
        public final String condition;

        public WeatherInfo(float temperature, String condition) {
            this.temperature = temperature;
            this.condition = condition;
        }
    }

    public FavoriteCityAdapter(Context context, List<String> cities, Map<String, WeatherInfo> weatherData) {
        super(context, R.layout.item_favorite_city, cities);
        this.context = context;
        this.cities = cities;
        this.weatherData = weatherData;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_favorite_city, parent, false);
        }

        TextView cityName = convertView.findViewById(R.id.cityName);
        TextView temperature = convertView.findViewById(R.id.temperature);
        ImageView weatherIcon = convertView.findViewById(R.id.weatherIcon);

        String city = cities.get(position);
        cityName.setText(city);

        WeatherInfo info = weatherData.get(city);
        if (info != null) {
            temperature.setText(String.format("%d°C", Math.round(info.temperature)));
            weatherIcon.setImageResource(getWeatherIcon(info.condition));
        } else {
            temperature.setText("--°C");
            weatherIcon.setImageResource(R.drawable.ic_weather_clouds);
        }

        return convertView;
    }

    private int getWeatherIcon(String condition) {
        switch (condition.toLowerCase()) {
            case "clear sky":
                return R.drawable.ic_weather_clear;
            case "few clouds":
            case "scattered clouds":
            case "broken clouds":
                return R.drawable.ic_weather_clouds;
            case "shower rain":
            case "rain":
            case "thunderstorm":
                return R.drawable.ic_weather_rain;
            default:
                return R.drawable.ic_weather_clouds;
        }
    }
} 