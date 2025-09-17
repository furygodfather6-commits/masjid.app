package com.masjid.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.masjid.app.databinding.ActivityQiblaBinding

class QiblaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQiblaBinding
    private val QIBLA_FINDER_URL = "https://qiblafinder.withgoogle.com/"
    private val LOCATION_PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQiblaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        checkAndLoadWebView()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun checkAndLoadWebView() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            setupWebView()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupWebView()
            } else {
                showError("Qibla Finder ke liye location permission zaroori hai. Kripya permission dekar dobara koshish karein.")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webviewQibla

        // Saari zaroori settings ko enable karein
        webView.settings.apply {
            javaScriptEnabled = true
            setGeolocationEnabled(true) // GPS ko istemal karne ki anumati
            javaScriptCanOpenWindowsAutomatically = true
            domStorageEnabled = true // Website ko data store karne ki anumati
        }

        // Page load hone tak progress bar dikhayein
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
            }
        }

        // Yeh WebChromeClient ka advance version hai
        webView.webChromeClient = object : WebChromeClient() {
            // Jab website location maangegi, to yeh function chalega
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                // Hum website ko turant location istemal karne ki anumati de denge
                callback.invoke(origin, true, false)
            }
        }

        webView.loadUrl(QIBLA_FINDER_URL)
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        val webView = binding.webviewQibla
        val htmlMessage = "<html><body style='display:flex; justify-content:center; align-items:center; height:100vh;'><h2 style='text-align:center; font-family:sans-serif;'>$message</h2></body></html>"
        webView.loadData(htmlMessage, "text/html", "UTF-8")
    }
}