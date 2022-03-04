package com.ookla.speedtest.sampleapp

import android.Manifest
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.ookla.speedtest.sdk.SpeedtestSDK
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.concurrent.thread


/**
 * Entry point for the Speedtest SDK sample app
 *
 * This class initializes the SDK with the api key and contains links to all the different
 * tests that can be run via the SDK
 *
 * Look at this activity for an example on how to initialize the Speedtest SDK
 *
 */


class MainActivity : AppCompatActivity() {

    companion object {
        @kotlin.jvm.JvmField
        var temp: Float= 0.0f
//        @kotlin.jvm.JvmField
//        var flag: Boolean = false;
        // Use the key provided to you instead of the test key below
        const val SPEEDTEST_SDK_API_KEY = "d4yshimc47kvoe7l"
        const val SPEEDTEST_SDK_RESULT_KEY = "81su553x2no9c4g0"
        var lastTestGuid: String? = null
        var pre = 0;
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Toast.makeText(this, temp.toString()+"second", Toast.LENGTH_SHORT).show()
        checkPermissions()

        val availableTests = TestActivity.TestFunctionality.values()
        val arrayAdapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1,
                availableTests.map { it.title }.toList())

        actionList.adapter = arrayAdapter
        actionList.onItemClickListener = OnItemClickListener { _, _, position, _ ->
            startActivityWith(availableTests[position])
            for(i in 1..10){
            if(position!=9){
                pre = position
//                    startActivityWith(availableTests[position])
                thread {
                    Thread.sleep(40000)
                    println("10000")
                    Thread.sleep(10000)
                    startActivity(Intent(this@MainActivity, MapsActivity::class.java))
                    Thread.sleep(1000)
                    startActivityWith(availableTests[9])
                    }
                }else{
                Toast.makeText(this, pre.toString()+"sec", Toast.LENGTH_SHORT).show()
            }
            Toast.makeText(this, temp.toString()+"second", Toast.LENGTH_SHORT).show()
        }
    }

        foregroundSwitch.isChecked = SpeedtestSDK.getInstance().getSpeedtestSDKOptions().foregroundServiceOption.enabled
        foregroundSwitch.setOnCheckedChangeListener { _, enabled ->
            SpeedtestSDK.getInstance().updateForgroundServiceOption(SpeedtestSDK.ForegroundServiceOption(
                enabled,
                "com.ookla.speedtest.sampleapp.MainActivity",
                "Ookla Sample SDK", "Enabling foreground service to keep the sample SDK always running"))
        }

        locationSwitch.isChecked = SpeedtestSDK.getInstance().getSpeedtestSDKOptions().locationUpdateOption.enableActiveLocation
        locationSwitch.setOnCheckedChangeListener { _, enabled ->
            SpeedtestSDK.getInstance().updateActiveLocationOption(SpeedtestSDK.LocationUpdateOption(
                enabled,
                10
            ))
        }
    }

    private fun startActivityWith(functionality: TestActivity.TestFunctionality) {
        val intent = Intent(applicationContext, TestActivity::class.java)
        intent.putExtra("testFunctionality", functionality)

        val options =
            ActivityOptions.makeCustomAnimation(applicationContext, android.R.anim.fade_in, android.R.anim.fade_out)
        this@MainActivity.startActivity(intent, options.toBundle())
    }

    private fun checkPermissions() {
        var permissionsNeeded = SpeedtestSDK.getInstance().checkPermissions(applicationContext)
        if (permissionsNeeded.isNotEmpty()) {
            Dexter.withContext(this)
                .withPermissions(
                    permissionsNeeded
                ).withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (true == report?.deniedPermissionResponses?.any {
                            it.permissionName == Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        }) {
                            Dexter.withContext(this@MainActivity)
                                .withPermissions(
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                ).withListener(multiplePermissionsListener).check()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        p0: MutableList<PermissionRequest>?,
                        p1: PermissionToken?
                    ) {
                    }

                }).check()
        }
    }

    val multiplePermissionsListener = object: MultiplePermissionsListener {
        override fun onPermissionsChecked(p0: MultiplePermissionsReport?) {
        }

        override fun onPermissionRationaleShouldBeShown(
            p0: MutableList<PermissionRequest>?,
            p1: PermissionToken?
        ) {
            Toast.makeText(this@MainActivity,"Please enable background location from settings page", Toast.LENGTH_SHORT).show();
        }

    }
}
