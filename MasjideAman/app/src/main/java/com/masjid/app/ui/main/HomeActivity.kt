package com.masjid.app.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.masjid.app.ui.main.HomeFragment
import com.masjid.app.QuranFragment
import com.masjid.app.R
import com.masjid.app.SettingFragment
import com.masjid.app.databinding.ActivityHomeBinding
import com.masjid.app.ui.chanda.fragments.DonationFragment
import com.masjid.app.ui.chanda.fragments.FundFragment

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            var selectedFragment: Fragment? = null
            when (item.itemId) {
                R.id.nav_home -> {
                    selectedFragment = HomeFragment()
                }
                R.id.nav_quran -> {
                    selectedFragment = QuranFragment()
                }
                R.id.nav_fund -> {
                    selectedFragment = FundFragment()
                }
                R.id.nav_donation -> {
                    selectedFragment = DonationFragment()
                }
                R.id.nav_settings -> {
                    selectedFragment = SettingFragment()
                }
            }
            if (selectedFragment != null) {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, selectedFragment).commit()
            }
            true
        }

        // Set default fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(
                R.id.fragment_container,
                HomeFragment()
            ).commit()
        }
    }
}