package com.example.travel2;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity implements LocationListener, MapEventsReceiver {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final double SEOUL_LAT = 37.5665;
    private static final double SEOUL_LON = 126.9780;
    private static final long MIN_TIME_BETWEEN_UPDATES = 1000; // 1 second
    private static final float MIN_DISTANCE_CHANGE = 5; // 5 meters

    private MapView map;
    private EditText searchEditText;
    private LocationManager locationManager;
    private Marker userMarker;
    private Location lastKnownLocation;
    private MyLocationNewOverlay myLocationOverlay;
    private List<Marker> searchMarkers = new ArrayList<>();
    private boolean isMapReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize OSMdroid configuration
        Context ctx = getApplicationContext();
        Configuration.getInstance().setUserAgentValue(getPackageName());
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        setContentView(R.layout.activity_map);
        initializeMap();
        initializeUI();
        setupLocationServices();

        if (savedInstanceState != null) {
            restoreMapState(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (map != null) {
            outState.putDouble("center_lat", map.getMapCenter().getLatitude());
            outState.putDouble("center_lon", map.getMapCenter().getLongitude());
            outState.putDouble("zoom", map.getZoomLevelDouble());
        }
        if (lastKnownLocation != null) {
            outState.putParcelable("last_location", lastKnownLocation);
        }
    }

    private void restoreMapState(Bundle savedInstanceState) {
        if (map != null) {
            double lat = savedInstanceState.getDouble("center_lat", SEOUL_LAT);
            double lon = savedInstanceState.getDouble("center_lon", SEOUL_LON);
            double zoom = savedInstanceState.getDouble("zoom", 15.0);
            map.getController().setCenter(new GeoPoint(lat, lon));
            map.getController().setZoom(zoom);
        }
        lastKnownLocation = savedInstanceState.getParcelable("last_location");
        if (lastKnownLocation != null && userMarker != null) {
            updateUserMarker(lastKnownLocation);
        }
    }

    private void initializeMap() {
        map = findViewById(R.id.map);
        if (map == null) {
            Toast.makeText(this, "Error initializing map", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(15.0);
        map.getController().setCenter(new GeoPoint(SEOUL_LAT, SEOUL_LON));

        // Add map events overlay
        MapEventsOverlay mapEventsOverlay = new MapEventsOverlay(this);
        map.getOverlays().add(0, mapEventsOverlay);

        setupMyLocationOverlay();
        isMapReady = true;
    }

    private void setupMyLocationOverlay() {
        Context ctx = getApplicationContext();
        GpsMyLocationProvider provider = new GpsMyLocationProvider(ctx);
        provider.setLocationUpdateMinTime(MIN_TIME_BETWEEN_UPDATES);
        provider.setLocationUpdateMinDistance(MIN_DISTANCE_CHANGE);
        
        myLocationOverlay = new MyLocationNewOverlay(provider, map);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();
        map.getOverlays().add(myLocationOverlay);

        userMarker = new Marker(map);
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(userMarker);
    }

    private void initializeUI() {
        searchEditText = findViewById(R.id.searchEditText);
        Button searchButton = findViewById(R.id.searchButton);
        ImageButton myLocationButton = findViewById(R.id.myLocationButton);
        
        searchButton.setOnClickListener(v -> searchLocation());
        myLocationButton.setOnClickListener(v -> centerOnMyLocation());

        searchEditText.setHint(R.string.search_place);
    }

    private void setupLocationServices() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            Toast.makeText(this, "Location services not available", Toast.LENGTH_LONG).show();
            return;
        }
        checkLocationPermission();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;
        
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void centerOnMyLocation() {
        if (!isMapReady) {
            Toast.makeText(this, "Map not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
            map.getController().animateTo(myLocationOverlay.getMyLocation());
            map.getController().setZoom(17.0);
        } else if (lastKnownLocation != null) {
            GeoPoint point = new GeoPoint(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
            map.getController().animateTo(point);
            map.getController().setZoom(17.0);
        } else {
            Toast.makeText(this, R.string.location_not_found, Toast.LENGTH_SHORT).show();
            checkLocationPermission();
        }
    }

    private void searchLocation() {
        if (!isMapReady) {
            Toast.makeText(this, "Map not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isNetworkAvailable()) {
            Toast.makeText(this, R.string.weather_network_error, Toast.LENGTH_SHORT).show();
            return;
        }

        String query = searchEditText.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, R.string.search_place, Toast.LENGTH_SHORT).show();
            return;
        }

        // Clear previous search markers
        for (Marker marker : searchMarkers) {
            map.getOverlays().remove(marker);
        }
        searchMarkers.clear();

        // Example search result (replace with actual geocoding service)
        double lat = SEOUL_LAT + (Math.random() - 0.5) * 0.1;
        double lon = SEOUL_LON + (Math.random() - 0.5) * 0.1;
        
        GeoPoint point = new GeoPoint(lat, lon);
        map.getController().animateTo(point);
        
        Marker searchMarker = new Marker(map);
        searchMarker.setPosition(point);
        searchMarker.setTitle(query);
        searchMarker.setSnippet("Tap to remove");
        searchMarker.setOnMarkerClickListener((marker, mapView) -> {
            mapView.getOverlays().remove(marker);
            searchMarkers.remove(marker);
            return true;
        });
        
        map.getOverlays().add(searchMarker);
        searchMarkers.add(searchMarker);
        map.invalidate();
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    PERMISSION_REQUEST_CODE);
        } else {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (locationManager == null) return;

        // Request GPS updates
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_BETWEEN_UPDATES,
                    MIN_DISTANCE_CHANGE,
                    this
            );
        }
        
        // Request network location updates as backup
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    MIN_TIME_BETWEEN_UPDATES,
                    MIN_DISTANCE_CHANGE,
                    this
            );
        }
        
        // Try to get last known location
        Location gpsLocation = null;
        Location networkLocation = null;
        
        try {
            gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Use the most recent location
        if (gpsLocation != null && networkLocation != null) {
            lastKnownLocation = gpsLocation.getTime() > networkLocation.getTime() ? 
                    gpsLocation : networkLocation;
        } else {
            lastKnownLocation = gpsLocation != null ? gpsLocation : networkLocation;
        }

        if (lastKnownLocation != null) {
            updateUserMarker(lastKnownLocation);
        }
    }

    private void updateUserMarker(Location location) {
        if (!isMapReady || location == null) return;

        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
        userMarker.setPosition(point);
        userMarker.setTitle(getString(R.string.my_location));
        map.invalidate();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        lastKnownLocation = location;
        updateUserMarker(location);
    }

    @Override
    public boolean singleTapConfirmedHelper(GeoPoint p) {
        return false;
    }

    @Override
    public boolean longPressHelper(GeoPoint p) {
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) {
            map.onResume();
        }
        if (myLocationOverlay != null) {
            myLocationOverlay.enableMyLocation();
            myLocationOverlay.enableFollowLocation();
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) {
            map.onPause();
        }
        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
            myLocationOverlay.disableFollowLocation();
        }
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }
    

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    public void onClicked_function(View view) {
        Intent intent = null;
        if (view.getId() == R.id.btn_food) {
            if (lastKnownLocation != null) {
                double latitude = lastKnownLocation.getLatitude();
                double longitude = lastKnownLocation.getLongitude();
                if (latitude != 0.0 && longitude != 0.0) {
                    String mapUri = "https://map.naver.com/v5/search/맛집?c=15," + longitude + "," + latitude;
                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mapUri));
                } else {
                    Toast.makeText(this, "유효한 위치 정보가 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                Toast.makeText(this, "위치 정보를 가져오는 중입니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (intent != null) {
                startActivity(intent);
            }
        }
    }
}
