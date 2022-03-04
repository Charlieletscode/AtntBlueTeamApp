package com.ookla.speedtest.sampleapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.ookla.speedtest.sampleapp.databinding.ActivityMapsBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
//import org.matthiaszimmermann.location.Location;
import org.matthiaszimmermann.location.egm96.Geoid;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;

    Location currentLocation;
    private static final int REQUEST_CODE = 101;
    FusedLocationProviderClient fusedLocationProviderClient;
    TextView latitudeTextView, longitTextView, eleTextView;
    int input;
    public float longi, lati,temp;
    EditText txt;
    Button b;
    private LocationManager mLocationManager;
    private float mLastMslAltitude;
    private Context mContext;
    public static boolean flag = false;

    /**
     * convert the wgs84 altitude to the egm96 altitude at the provided location.
     * @return
     */
    public double transAltitude(double wgs84altitude, double latitude, double longitude) {
        return wgs84altitude + Geoid.getOffset(new org.matthiaszimmermann.location.Location(latitude,longitude));
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        Geoid.init();
        latitudeTextView = findViewById(R.id.lat);
        longitTextView = findViewById(R.id.lon);
        eleTextView = findViewById(R.id.ele);

        txt = (EditText) findViewById(R.id.editText);
        b = findViewById(R.id.showInput);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Code here executes on main thread after user presses button
//                input = Integer.parseInt(txt.getText().toString());
                fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(MapsActivity.this);
                fetchLocation();
                long start = System.currentTimeMillis();
                try {
                    Thread.sleep(2000);
                    Intent intent = new Intent(MapsActivity.this, MainActivity.class);
                    startActivity(intent);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // Showing the user input
                Toast.makeText(getApplicationContext(), "refreshed", Toast.LENGTH_SHORT).show();
            }
        });

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        fetchLocation();
    }

    private GpsStatus.NmeaListener mNmeaListener = new GpsStatus.NmeaListener() {
        @Override
        public void onNmeaReceived(long timestamp, String nmea) {
            parseNmeaString(nmea);
        }
    };

    public void registerLocationManager(Context context) {
        mLocationManager = (LocationManager) mContext.getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mLocationManager.addNmeaListener(mNmeaListener);
    }

    private void parseNmeaString(String line) {
        if (line.startsWith("$")) {
            String[] tokens = line.split(",");
            String type = tokens[0];

            // Parse altitude above sea level, Detailed description of NMEA string here http://aprs.gids.nl/nmea/#gga
            if (type.startsWith("$GPGGA")) {
                if (!tokens[9].isEmpty()) {
                    mLastMslAltitude = Float.parseFloat(tokens[9]);
                }
            }
        }
    }
    private void fetchLocation() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE);

            return;
        }
        Task<Location> task = fusedLocationProviderClient.getLastLocation();
        task.addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    currentLocation = location;
                    lati = (float) currentLocation.getLatitude();
                    longi = (float) currentLocation.getLongitude();
                    Toast.makeText(getApplicationContext(), currentLocation.getLatitude() + "" + currentLocation.getLongitude(), Toast.LENGTH_SHORT).show();

                    latitudeTextView.setText(Float.toString(lati));
                    longitTextView.setText(Float.toString(longi));
                    eleTextView.setText(Float.toString(mLastMslAltitude));
                    if (currentLocation.hasAltitude()){
                        temp = new Float(transAltitude(new Float(currentLocation.getAltitude()), new Float(lati),new Float(longi)));
                        temp = new Float(currentLocation.getAltitude()-temp);
                        eleTextView.setText(Float.toString((float) temp));
                        MainActivity.temp = new Float(temp);

                    }
                    SupportMapFragment supportMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
                    assert supportMapFragment != null;
                    supportMapFragment.getMapAsync(MapsActivity.this);
                }
            }
        });
    }
    @Override
    public void onMapReady(GoogleMap googleMap) {
        LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
        MarkerOptions markerOptions = new MarkerOptions().position(latLng).title("I am here!");
        googleMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
        googleMap.addMarker(markerOptions);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fetchLocation();
                }
                break;
        }
    }
}
