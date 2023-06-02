package com.example.polaris_mk2


import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Insets.add
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import android.text.method.TextKeyListener.clear
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.PolylineOptions

class MapsFragment : Fragment() , OnMapReadyCallback , LocationListener {

    private lateinit var map: GoogleMap
    val zoom = null//状況に応じて
    private lateinit var locationCallback: LocationCallback
    private val biwako = LatLng(35.329977, 136.159374)
    private val rectOptions = PolygonOptions()//飛行禁止エリアを描画したい
        .add(
            LatLng(35.3000,136.252448),
            LatLng(35.295,136.25492),
            LatLng(35.287519,136.246475),
            LatLng(35.295,136.25492),
            LatLng(35.3,136.252448)
        )
        .strokeColor(Color.RED)
    //val pattern= listOf(Dot(),Gap(0.01f),Dash(0.02f),Gap(0.01f))


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera.
     * In this case, we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to
     * install it inside the SupportMapFragment. This method will only be triggered once the
     * user has installed Google Play services and returned to the app.
     */
    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera.
     * In this case, we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to
     * install it inside the SupportMapFragment. This method will only be triggered once the
     * user has installed Google Play services and returned to the app.
     */

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(R.layout.fragment_maps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(map: GoogleMap) {
        map.addPolygon(rectOptions)
        //polyline.strokePattern=pattern
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(biwako, 9.8f))

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {

                locationResult ?: return
                for (location in locationResult.locations) {

                }
            }
        }

        map.isMyLocationEnabled = true
    }

    var oldLat: Double = 0.0
    var oldLon: Double = 0.0
    override fun onLocationChanged(location: Location) {
        var newLat = location.latitude
        var newLon = location.longitude
        if (oldLat != newLat || oldLon != newLon) {
            map.addPolyline(
                PolylineOptions()
                    .add(
                        LatLng(oldLat, oldLon),
                        LatLng(newLat, newLon)
                    )
            )
        }
    }
}