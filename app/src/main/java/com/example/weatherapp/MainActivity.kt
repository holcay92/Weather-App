package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.weatherapp.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.example.weatherapp.models.WeatherResponse
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mProgressDialog : Dialog? = null
    private var binding: ActivityMainBinding? = null
    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize the SharedPreferences variable
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        setUpUI()
        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()
            // if not enabled, then open the location settings
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withContext(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {

                            requestLocationData()
                        }
                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission. Please allow it is mandatory.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
        }
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            val latitude = mLastLocation?.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation?.longitude
            Log.i("Current Longitude", "$longitude")
            getLocationWeatherDetails(latitude!!,longitude!!)
        }
    }
    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){
        if(Constants.isNetworkAvailable(this)){
            val retrofit = RetrofitInstance.getRetrofitInstance().create(WeatherService::class.java)
            val listCall: Call<WeatherResponse> = retrofit.getWeather(
                latitude,
                longitude,
                Constants.METRIC_UNIT,
                Constants.APP_ID
            )
            showCustomProgressDialog()
            listCall.enqueue(object: Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful) {
                        hideProgressDialog()
                        /** The de-serialized response body of a successful response. */
                        val weatherList: WeatherResponse = response.body()!!
                        Log.i("Response Result", "$weatherList")

                        // Here we convert the response object to string and store the string in the SharedPreference.)

                        // Here we have converted the model class in to Json String to store it in the SharedPreferences.
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        // Save the converted string to shared preferences
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        // Remove the weather detail object as we will be getting
                        //  the object in form of a string in the setup UI method.

                        setUpUI()

                    }
                    else {
                        // If the response is not success then we check the response code.
                        val sc = response.code()
                        when (sc) {
                            400 -> {
                                Log.e("MyError 400", "Bad Request")
                            }
                            404 -> {
                                Log.e("MyError 404", "Not Found")
                            }
                            else -> {
                                Log.e("MyError", "Generic Error")
                            }
                        }
                    }
                }
                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Errorrrrr :D ", t.message.toString())
                        hideProgressDialog()
                }
            })
        }else {
            Toast.makeText(this,"Internet is not available",Toast.LENGTH_SHORT).show()
        }
    }
    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton("GO TO SETTINGS") { _, _ ->
                try {
                    // When the user taps on "Settings" button, open the settings screen.
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)

                    // This is used to identify the specific application to open
                    // when we will be redirected to the settings screen !!!
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri

                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }
    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority =  Priority.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }
    /**
     * A location callback object of fused location provider client where we will get the current location details.
     */
    // This function checks if the location services are enabled or not.GENERIC FUNCTION
    private fun isLocationEnabled(): Boolean {
        // This provides access to the system location services.
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }
    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setUpUI(){
        val weatherResponseJsonString =
            mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")
        if (!weatherResponseJsonString.isNullOrEmpty()) {
            val weatherList =
                Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            for (i in weatherList.weather.indices){
                Log.i("Weather Name",weatherList.weather.toString())
                binding!!.tvMain.text = weatherList.weather[i].main
                binding!!.tvMainDescription.text = weatherList.weather[i].description
                binding!!.tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                binding!!.tvSunriseTime.text = unixTime(weatherList.sys.sunrise.toLong())
                binding!!.tvSunsetTime.text = unixTime(weatherList.sys.sunset.toLong())
                binding!!.tvHumidity.text = weatherList.main.humidity.toString() + " per cent"
                binding!!.tvMin.text = weatherList.main.temp_min.toString() + " min"
                binding!!.tvMax.text = weatherList.main.temp_max.toString() + " max"
                binding!!.tvSpeed.text = weatherList.wind.speed.toString()
                binding!!.tvName.text = weatherList.name
                binding!!.tvCountry.text = weatherList.sys.country
                when(weatherList.weather[i].icon){
                    "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                    "01n" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                    "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                    "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                    "50d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                }



            }
        }


    }
    private fun getUnit(value:String):String?{
        var value = "°C"
        if("US" == value || "LR" == value || "MM" == value){
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex:Long):String{
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm",Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                requestLocationData()
                    println("hello you did it")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return true
    }
 }