package com.masjid.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ek achhi splash screen me koi layout nahi hota,
        // isliye humne setContentView() ko hata diya hai.

        Handler(Looper.getMainLooper()).postDelayed({
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                // Agar user login hai, to MainActivity par jayein
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                // Agar nahi, to login screen (AuthActivity) par jayein
                startActivity(Intent(this, AuthActivity::class.java))
            }
            finish() // Is activity ko band kar dein
        }, 1500) // 1.5 second ka delay
    }
}

