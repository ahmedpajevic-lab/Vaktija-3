package ba.vaktija.premium

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import ba.vaktija.premium.data.VaktijaRepository
import ba.vaktija.premium.service.VaktijaNotificationService

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var repository: VaktijaRepository
    private var webView: WebView? = null
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        startVaktijaService()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = VaktijaRepository(applicationContext)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startVaktijaService()
        }

        val wv = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(AndroidBridge(), "AndroidBridge")
            loadUrl("file:///android_asset/vaktija_app.html")
        }
        webView = wv
        setContentView(wv)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView?.evaluateJavascript(
                    "(function(){ if (typeof state !== 'undefined' && state.activeScreen !== 'vaktija') { switchPhoneScreen('vaktija'); return true; } return false; })()"
                ) { handled ->
                    if (handled != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        sensorManager?.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val azimuthRad = orientation[0]
            val azimuthDeg = ((Math.toDegrees(azimuthRad.toDouble()) + 360.0) % 360.0).toInt()
            webView?.evaluateJavascript(
                "if (window.updateNativeCompassHeading) window.updateNativeCompassHeading($azimuthDeg);",
                null
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startVaktijaService() {
        val intent = Intent(this, VaktijaNotificationService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun getSelectedLocationId(): Int = repository.getSelectedLocationId()

        @JavascriptInterface
        fun isNowBriefEnabled(): Boolean = repository.isNowBriefEnabled()

        @JavascriptInterface
        fun onCitySelected(locationId: Int) {
            repository.setSelectedLocationId(locationId)
            runOnUiThread { startVaktijaService() }
        }

        @JavascriptInterface
        fun onToggleNowBrief(enabled: Boolean) {
            repository.setNowBriefEnabled(enabled)
            runOnUiThread { startVaktijaService() }
        }

        @JavascriptInterface
        fun onRestartNotification() {
            runOnUiThread { startVaktijaService() }
        }
    }
}
