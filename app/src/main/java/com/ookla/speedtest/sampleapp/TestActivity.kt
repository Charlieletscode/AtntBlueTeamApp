package com.ookla.speedtest.sampleapp

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Parcelable
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dandan.jsonhandleview.library.JsonViewLayout
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.ookla.speedtest.sdk.*
import com.ookla.speedtest.sdk.config.*
import com.ookla.speedtest.sdk.handler.MainThreadTestHandler
import com.ookla.speedtest.sdk.handler.TaskManagerController
import com.ookla.speedtest.sdk.model.LatencyResult
import com.ookla.speedtest.sdk.model.ThroughputStage
import com.ookla.speedtest.sdk.model.TransferResult
import com.ookla.speedtest.sdk.result.OoklaError
import kotlinx.android.synthetic.main.activity_test.*
import java.lang.RuntimeException
import java.time.format.DateTimeFormatter
import com.google.firebase.firestore.auth.User
import com.ookla.speedtest.sampleapp.MainActivity.Companion


/**
 * Contains the logic to execute the different tests available as part of the Speedtest SDK
 *
 * Look at this class for examples of how to use [TaskManager] to run tests as
 * well as how to pass in [TestHandler][com.ookla.speedtest.sdk.handler.TestHandler] to listen to progress updates and get the results
 */
class TestActivity : AppCompatActivity() {
    private val testConfigBgSpeed = "defaultTriggeredTest"
    private val testConfigBgScan = "defaultSignalScan"
    private val testConfigCITest = "test1"
    private lateinit var database: DatabaseReference


    enum class TestFunctionality(val title: String) {
        ServerSelection("Run Server Selection"),
        SingleTest("Start Single Test"),
        SingleTestWithAutoServerSelection("Start Test With Auto Server Selection"),
        ShortTest("Start Short Test"),
        BackgroundCaptureDeviceTask("Start Background Scan"),
        BackgroundThroughputTask("Start Background Speedtest"),
        Traceroute("Run Multiple Traceroutes"),
        UploadAndTraceroute("Run Upload and Traceroute Test"),
        TestWithSupplementalData("Test With Supplemental Data"),
        FetchStoredResult("Fetch Stored Test Result"),
        DeleteSavedConfigs("Remove Saved Config"),
        ReinitSDK("Reinitialize SDK")
    }

    private var wakeLock : PowerManager.WakeLock? = null
    private var wifiLock : WifiManager.WifiLock? = null
    private var taskManager: TaskManager? = null

    companion object {
     var testFinished: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        val powerManager =
            getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SDK:SDKWakeLock")

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "SDK:SDKWifiLock")

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        val inst = SpeedtestSDK.getInstance()
        val testFunctionality = intent.getSerializableExtra("testFunctionality") as TestFunctionality
        if(testFunctionality != TestFunctionality.FetchStoredResult) {
            output.text = "Start fetching config ...\n"
        }

        when (testFunctionality) {
            TestFunctionality.SingleTest -> {
                runSingleTest(inst)
            }
            TestFunctionality.SingleTestWithAutoServerSelection -> {
                runSingleTestWithAutoServerSelection(inst)
//                runFetchStoredResult(inst)
            }
            TestFunctionality.ShortTest -> {
                runShortTest(inst)
            }
            TestFunctionality.BackgroundCaptureDeviceTask -> {
                runBackgroundCaptureDeviceTask(inst)
            }
            TestFunctionality.BackgroundThroughputTask -> {
                runBackgroundThroughputTask(inst)
            }
            TestFunctionality.ServerSelection -> {
                runServerSelection()
            }
            TestFunctionality.UploadAndTraceroute -> {
                runTestWithUploadAndTraceroute(inst)
            }
            TestFunctionality.Traceroute -> {
                runTestWithTraceroute(inst)
            }
            TestFunctionality.TestWithSupplementalData -> {
                runTestWithSupplementalData(inst)
            }
            TestFunctionality.FetchStoredResult -> {
                runFetchStoredResult(inst)
            }
            TestFunctionality.DeleteSavedConfigs -> {
                deleteNamedConfigs(inst)
            }
            TestFunctionality.ReinitSDK -> {
                reinitSDK(inst)
            }
        }
    }

    data class Salad(
        val ispName: Array<String>,
        val latency: Array<Float>,
        val startLatitude: Array<Float>,
        val startLongitude: Array<Float>,
        val endLatitude: Array<Float>,
        val endLongitude: Array<Float>,
        val ipAddress: Array<String>,
        val jitter: Array<Float>,
        val regionName: Array<String>,
        val serverName: Array<String>,
        val downloadMbps: Array<Float>,
        val uploadMbps: Array<Float>,
        val placeName: Array<String>,
        val placeType: Array<String>,
        val subregion: Array<String>,
        val postalCode: Array<String>,
        val altitude: Array<Float>,
        var uuid: Array<String>) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Salad

            if (!ispName.contentEquals(other.ispName)) return false
            if (!latency.contentEquals(other.latency)) return false
            if (!startLatitude.contentEquals(other.startLatitude)) return false
            if (!startLongitude.contentEquals(other.startLongitude)) return false
            if (!endLatitude.contentEquals(other.endLatitude)) return false
            if (!endLongitude.contentEquals(other.endLongitude)) return false
            if (!ipAddress.contentEquals(other.ipAddress)) return false
            if (!jitter.contentEquals(other.jitter)) return false
            if (!regionName.contentEquals(other.regionName)) return false
            if (!serverName.contentEquals(other.serverName)) return false
            if (!downloadMbps.contentEquals(other.downloadMbps)) return false
            if (!uploadMbps.contentEquals(other.uploadMbps)) return false
            if (!placeName.contentEquals(other.placeName)) return false
            if (!placeType.contentEquals(other.placeType)) return false
            if (!subregion.contentEquals(other.subregion)) return false
            if (!postalCode.contentEquals(other.postalCode)) return false
            if (!altitude.contentEquals(other.altitude)) return false
            if (!uuid.contentEquals(other.uuid)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = ispName.contentHashCode()
            result = 31 * result + latency.contentHashCode()
            result = 31 * result + startLatitude.contentHashCode()
            result = 31 * result + startLongitude.contentHashCode()
            result = 31 * result + endLatitude.contentHashCode()
            result = 31 * result + endLongitude.contentHashCode()
            result = 31 * result + ipAddress.contentHashCode()
            result = 31 * result + jitter.contentHashCode()
            result = 31 * result + regionName.contentHashCode()
            result = 31 * result + serverName.contentHashCode()
            result = 31 * result + downloadMbps.contentHashCode()
            result = 31 * result + uploadMbps.contentHashCode()
            result = 31 * result + placeName.contentHashCode()
            result = 31 * result + placeType.contentHashCode()
            result = 31 * result + subregion.contentHashCode()
            result = 31 * result + postalCode.contentHashCode()
            result = 31 * result + altitude.contentHashCode()
            result = 31 * result + uuid.contentHashCode()
            return result
        }
    }

    private fun runFetchStoredResult(speedtestSDK: SpeedtestSDK) {
        var fbFirestore : FirebaseFirestore? = null
        fbFirestore = FirebaseFirestore.getInstance()

        database = Firebase.database.reference.child("user");

        val guid = MainActivity.lastTestGuid
        val logger = LoggingTestHandler(output, jsonView)
        if(guid == null) {
            logger.log("A test needs to be run first to fetch the stored result.")
            return
        }
        logger.log("Fetching stored result for guid $guid...")
        speedtestSDK.fetchResult(guid, MainActivity.SPEEDTEST_SDK_RESULT_KEY) { result, error ->
            if(result != null) {
                logger.log("Got result")
                try {
                    jsonView.bindJson(result.toJson())
//                    val city = hashMapOf(
//                        "name" to "Los Angeles",
//                        "State" to "MA"
//                    )
//                    fbFirestore.collection("users")?.document("test")?.set(city)
                    val currentTimestamp = System.currentTimeMillis()
                    val data = hashMapOf(
                        "ispName" to result.ispName,
                        "latency" to result.latency,
                        "startLatitude" to result.startLatitude,
                        "startLongitude" to result.startLongitude,
                        "endLatitude" to result.endLatitude,
                        "endLongitude" to result.endLongitude,
                        "ipAddress" to result.ipAddress,
                        "jitter" to result.jitter,
                        "regionName" to result.regionName,
                        "serverName" to result.serverName,
                        "downloadMbps" to result.downloadMbps,
                        "uploadMbps" to result.uploadMbps,
                        "placeName" to result.placeName,
                        "placeType" to result.placeType,
                        "subregion" to result.subregion,
                        "postalCode" to result.postalCode,
                        "altitude" to MainActivity.temp

                    )
                    database.child(currentTimestamp.toString()).setValue(data)

//                    println(result.endLatitude.toString())
//                    println("Minseok")
//                    MainActivity.flag = true;
                } catch(exc: RuntimeException) {
                    logger.log("Failed to convert result to json: ${exc.localizedMessage}")
                }
            } else if(error != null) {
                logger.log("Failed to fetch result: ${error.message} (${error.type})")
            }
        }

    }

    private fun runSingleTest(speedtestSDK: SpeedtestSDK) {
        //val db = Firebase.firestore
        //db.collection("users").document("Test")
        val config = Config.newConfig(testConfigCITest)
        config?.tasks = arrayListOf(Task.newThroughputTask(), Task.newServerTracerouteTask(), Task.newPacketlossTask())
        // test against a single server
        config?.serverIdForTesting = 6029
        val configHandler = object : ConfigHandlerBase() {
            override fun onConfigFetchFinished(validatedConfig: ValidatedConfig?) {
                if (testFinished) {
                    return;
                }

                val handlerWithStageProgression =
                    UiTestHandlerWithStageProgression(
                        output, jsonView
                    )
                val handler = MainThreadTestHandler(
                    handlerWithStageProgression
                )

                output.text = "Config retrieved over connection type ${validatedConfig?.connectionType.toString()}\n"
                taskManager = speedtestSDK.newTaskManager(handler, validatedConfig)
                taskManager?.start()
            }

            override fun onConfigFetchFailed(error: OoklaError) {
                output.append("Config fetch failed with ${error.message}\n")
            }
        }

        ValidatedConfig.validate(config, MainThreadConfigHandler(configHandler))
    }

    private fun runSingleTestWithAutoServerSelection(speedtestSDK: SpeedtestSDK) {
        val config = Config.newConfig(testConfigCITest)
        val logger = LoggingTestHandler(output, jsonView)
        val configHandler = object : ConfigHandlerBase() {
            override fun onConfigFetchFinished(validatedConfig: ValidatedConfig?) {
                if (testFinished) {
                    logger.log("Test Finished")
//                    runFetchStoredResult(SpeedtestSDK.getInstance())
                    return;
                }
                val handlerWithStageProgression =
                    UiTestHandlerWithStageProgression(
                        output, jsonView
                    )
                val handler = MainThreadTestHandler(
                    handlerWithStageProgression
                )
                taskManager = speedtestSDK.newTaskManager(handler, validatedConfig)
                taskManager?.start()
            }

            override fun onConfigFetchFailed(error: OoklaError) {
                output.append("Config fetch failed with ${error.message}\n")
            }
        }
        ValidatedConfig.validate(config, MainThreadConfigHandler(configHandler))
    }

    private fun runShortTest(speedtestSDK: SpeedtestSDK) {
        val config = Config.newConfig(testConfigCITest)
        config?.transferTestDurationSeconds = 5
        val configHandler = object : ConfigHandlerBase() {
            override fun onConfigFetchFinished(validatedConfig: ValidatedConfig?) {
                if (testFinished) {
                    return;
                }
                val handlerWithStageProgression =
                    UiTestHandlerWithStageProgression(
                        output, jsonView
                    )
                val handler = MainThreadTestHandler(
                    handlerWithStageProgression
                )
                taskManager = speedtestSDK.newTaskManager(handler, validatedConfig)
                taskManager?.start()
            }

            override fun onConfigFetchFailed(error: OoklaError) {
                output.append("Config fetch failed with ${error.message}\n")
            }
        }
        ValidatedConfig.validate(config, MainThreadConfigHandler(configHandler))
    }

    private fun runServerSelection() {
        val config = Config.newConfig(testConfigCITest)
        val configHandler: ConfigHandler
        configHandler = MainThreadConfigHandler(LoggingConfigHandler(output))
        ValidatedConfig.validate(config, MainThreadConfigHandler(configHandler))
    }

    private fun runTestWithUploadAndTraceroute(speedtestSDK: SpeedtestSDK) {
        val config = Config.newConfig(testConfigCITest)
        config?.tasks = arrayListOf(Task.newCustomThroughputTask(hashSetOf(ThroughputStage.UPLOAD)),
            Task.newServerTracerouteTask())
        val configHandler = object : ConfigHandlerBase() {
            override fun onConfigFetchFinished(validatedConfig: ValidatedConfig?) {
                if (testFinished) {
                    return;
                }
                val handlerWithStageProgression =
                    UiTestHandlerWithStageProgression(output, jsonView)
                val handler = MainThreadTestHandler(handlerWithStageProgression)
                taskManager = speedtestSDK.newTaskManager(handler, validatedConfig)
                taskManager?.start()
            }

            override fun onConfigFetchFailed(error: OoklaError) {
                output.append("Config fetch failed with ${error.message}\n")
            }
        }
        ValidatedConfig.validate(config, MainThreadConfigHandler(configHandler))
    }

    private fun runTestWithTraceroute(speedtestSDK: SpeedtestSDK) {
        val config = Config.newConfig(testConfigCITest)
        val configHandler: ConfigHandler
        config?.tasks = arrayListOf(Task.newTracerouteTask("www.speedtest.com"),
            Task.newTracerouteTask("www.ookla.com"),
            Task.newServerTracerouteTask(),
            Task.newTimeoutTask(5)
        )
        config?.disableResultUpload = true
        configHandler = object : ConfigHandlerBase() {
            override fun onConfigFetchFinished(validatedConfig: ValidatedConfig?) {
                if (testFinished) {
                    return;
                }
                val handlerWithStageProgression =
                    UiTestHandlerWithStageProgression(output, jsonView)
                val handler = MainThreadTestHandler(handlerWithStageProgression)
                taskManager = speedtestSDK.newTaskManager(handler, validatedConfig)
                taskManager?.start()
            }

            override fun onConfigFetchFailed(error: OoklaError) {
                output.append("Config fetch failed with ${error.message}\n")
            }
        }
        ValidatedConfig.validate(config, MainThreadConfigHandler(configHandler))
    }

    private fun runTestWithSupplementalData(speedtestSDK: SpeedtestSDK) {
        val config = Config.newConfig(testConfigCITest)
        val configHandler: ConfigHandler
        configHandler = object : ConfigHandlerBase() {
            override fun onConfigFetchFinished(validatedConfig: ValidatedConfig?) {
                if (testFinished) {
                    return;
                }
                val handlerWithStageProgression =
                    UiTestHandlerWithStageProgression(
                        output, jsonView
                    )
                val handler = MainThreadTestHandler(
                    handlerWithStageProgression
                )

                val extraFields = arrayListOf(DataPairs("field", "8918"), DataPairs("otherField", "value"));

                val supplementalData = SupplementalData("1128", "1.0.10", extraFields);
                taskManager = speedtestSDK.newTaskManager(handler, validatedConfig)
                taskManager?.setSupplementalData(supplementalData.toJson().toByteArray())
                taskManager?.start()
            }

            override fun onConfigFetchFailed(error: OoklaError) {
                output.append("Config fetch failed with ${error.message}\n")
            }
        }

        ValidatedConfig.validate(config, MainThreadConfigHandler(configHandler))
    }

    private fun runBackgroundCaptureDeviceTask(speedtestSDK: SpeedtestSDK) {
        val data = SupplementalData("123", "1.0", arrayListOf())
        val config = Config.newConfig(testConfigBgScan)

        // background
        config?.tasks = arrayListOf(Task.newCaptureDeviceStateTask())

        var backgroundScanTaskManager : TaskManager?
        val configHandler = object : ConfigHandlerBase() {
            override fun onConfigFetchFinished(validatedConfig: ValidatedConfig?) {

                if (testFinished) {
                    return
                }
                val handlerWithStageProgression =
                    UiTestHandlerWithStageProgression(
                        output, jsonView
                    )
                val handler = MainThreadTestHandler(
                    handlerWithStageProgression
                )

                output.text = "Config retrieved over connection type ${validatedConfig?.connectionType.toString()}\n"
                val backgroundScanTaskManagerStatus = speedtestSDK.newTaskManagerWithCreateStatus(handler, validatedConfig)
                backgroundScanTaskManager = backgroundScanTaskManagerStatus.taskManager
                backgroundScanTaskManager?.setSupplementalData(data.toJson().toByteArray())

                if (backgroundScanTaskManagerStatus.didExist()) {
                    output.append("Background task is already running.\n")
                    logLastRunTime(speedtestSDK, testConfigBgScan)
                } else {
                    backgroundScanTaskManager?.start()
                    output.append("Started background task.\n");
                }
            }

            override fun onConfigFetchFailed(error: OoklaError) {
                output.append("Config fetch failed with ${error.message}\n")
            }
        }

        ValidatedConfig.validate(config, MainThreadConfigHandler(configHandler))
    }

    private fun runBackgroundThroughputTask(speedtestSDK: SpeedtestSDK) {
        val config = Config.newConfig(testConfigBgSpeed)

        // background
        config?.tasks = arrayListOf(Task.newThroughputTask())

        var backgroundThroughputTaskManager : TaskManager?
        val configHandler = object : ConfigHandlerBase() {
            override fun onConfigFetchFinished(validatedConfig: ValidatedConfig?) {
                if (testFinished) {
                    return;
                }
                val handler = MainThreadTestHandler(
                    LoggingTestHandler(output, jsonView)
                )

                output.text = "Config retrieved over connection type ${validatedConfig?.connectionType.toString()}\n"

                val backgroundThroughputTaskManagerStatus = speedtestSDK.newTaskManagerWithAutoAdvance(handler, validatedConfig)
                backgroundThroughputTaskManager = backgroundThroughputTaskManagerStatus.taskManager

                if (backgroundThroughputTaskManagerStatus.didExist()) {
                    output.append("Background task is already running.\n")
                    logLastRunTime(speedtestSDK, testConfigBgScan)
                } else {
                    backgroundThroughputTaskManager?.start()
                    output.append("Started background task.\n");
                }
            }

            override fun onConfigFetchFailed(error: OoklaError) {
                output.append("Config fetch failed with ${error.message}\n")
            }
        }

        ValidatedConfig.validate(config, MainThreadConfigHandler(configHandler))
    }

    private fun logLastRunTime(speedtestSDK: SpeedtestSDK, name: String) {
        val config = speedtestSDK.configManager
        config.getNamedConfig(name)?.let {
            output.append("\nLast ran at ${it.lastRunTime}\n\n")
        }
    }

    private fun deleteNamedConfigs(speedtestSDK: SpeedtestSDK) {
        val config = speedtestSDK.configManager
        val configs = config.savedConfigs
        configs.forEach {
            output.append("Found saved config ${it.name}\n")
            output.append("Deleting saved config ${it.name}\n")
            config.removeSavedConfig(it.name)
        }

        val configsAfterRemoval = config.savedConfigs
        if (configsAfterRemoval.size == 0) {
            output.append("No saved configs found!\n")
        } else {
            configs.forEach {
                output.append("Found saved config ${it.name}\n")
            }
        }
    }

    private fun reinitSDK(speedtestSDK: SpeedtestSDK) {
        speedtestSDK.terminate()
        SpeedtestSDK.initSDK(application, MainActivity.SPEEDTEST_SDK_API_KEY)

        runShortTest(speedtestSDK)
    }

    override fun onBackPressed() {
        testFinished = true;

        taskManager?.cancel()
        taskManager = null

        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()

        wifiLock?.isHeld?.let {
            if (it) {
                wifiLock?.release()
            }
        }
        wakeLock?.isHeld?.let {
            if (it) {
                wakeLock?.release()
            }
        }
    }

    inner class UiTestHandlerWithStageProgression (output: TextView, jsonView: JsonViewLayout) : LoggingTestHandler(output, jsonView) {
        override fun onLatencyFinished(taskController: TaskManagerController?, result: LatencyResult) {
            super.onLatencyFinished(taskController, result)
            taskManager?.startNextStage()
        }

        override fun onDownloadFinished(taskController: TaskManagerController?, result: TransferResult) {
            super.onDownloadFinished(taskController, result)
            taskManager?.startNextStage()
        }
    }

}
