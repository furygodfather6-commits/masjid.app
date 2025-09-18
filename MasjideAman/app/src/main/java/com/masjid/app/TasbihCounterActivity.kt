package com.masjid.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.masjid.app.databinding.ActivityTasbihCounterBinding
import com.masjid.app.models.Tasbih

class TasbihCounterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTasbihCounterBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var currentCount = 0
    private lateinit var currentTasbih: Tasbih

    // Your predefined list of tasbihs
    private val tasbihList = listOf(
        Tasbih("subhanallah", "SubhanAllah", 33),
        Tasbih("alhamdulillah", "Alhamdulillah", 33),
        Tasbih("allahuakbar", "Allahu Akbar", 34)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTasbihCounterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("TasbihCounter", Context.MODE_PRIVATE)

        setupToolbar()
        setupSpinner()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupSpinner() {
        val tasbihNames = tasbihList.map { "${it.name} - ${it.target}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tasbihNames)
        binding.tasbihSpinner.adapter = adapter

        binding.tasbihSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentTasbih = tasbihList[position]
                loadCount()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupClickListeners() {
        binding.counterTapZone.setOnClickListener {
            if (currentCount < currentTasbih.target) {
                currentCount++
                updateUI()
                vibrate()
            } else {
                showTargetReachedDialog()
            }
        }

        binding.resetButton.setOnClickListener {
            showResetConfirmationDialog()
        }

        //
        // You can add listeners for the new buttons here (e.g., increase, decrease)
        //
    }

    private fun loadCount() {
        currentCount = sharedPreferences.getInt(currentTasbih.id, 0)
        updateUI()
    }

    private fun saveCount() {
        sharedPreferences.edit().putInt(currentTasbih.id, currentCount).apply()
    }

    private fun updateUI() {
        binding.countTextView.text = currentCount.toString()
        binding.progressRing.max = currentTasbih.target
        binding.progressRing.progress = currentCount
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Counter?")
            .setMessage("Are you sure you want to reset the count for ${currentTasbih.name} to 0?")
            .setPositiveButton("Reset") { _, _ ->
                currentCount = 0
                updateUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTargetReachedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Masha'Allah!")
            .setMessage("You have completed the target for ${currentTasbih.name}.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    override fun onPause() {
        super.onPause()
        saveCount()
    }
}