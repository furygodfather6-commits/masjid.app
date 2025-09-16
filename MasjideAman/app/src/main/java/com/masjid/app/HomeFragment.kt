package com.masjid.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.google.android.gms.location.LocationServices
import com.masjid.app.databinding.FragmentHomeBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var prayerPills: Map<Prayer, TextView>
    private lateinit var prayerTimesTextViews: Map<Prayer, TextView>
    private var countDownTimer: CountDownTimer? = null

    enum class Prayer { FAJR, DHUHR, ASR, MAGHRIB, ISHA, SUNRISE, NONE }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews()
        setupClickListeners()
        checkLocationPermission()
    }

    private fun initializeViews() {
        prayerPills = mapOf(
            Prayer.FAJR to binding.pillFajr,
            Prayer.DHUHR to binding.pillDhuhr,
            Prayer.ASR to binding.pillAsr,
            Prayer.MAGHRIB to binding.pillMaghrib,
            Prayer.ISHA to binding.pillIsha
        )
        prayerTimesTextViews = mapOf(
            Prayer.FAJR to binding.timeFajr,
            Prayer.DHUHR to binding.timeDhuhr,
            Prayer.ASR to binding.timeAsr,
            Prayer.MAGHRIB to binding.timeMaghrib,
            Prayer.ISHA to binding.timeIsha
        )
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
        } else {
            fetchLocationAndCalculateTimes()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndCalculateTimes()
        } else {
            Toast.makeText(requireContext(), "Sahi namaz time ke liye location permission zaroori hai", Toast.LENGTH_LONG).show()
            calculateAndDisplayPrayerTimes(Coordinates(28.9845, 77.7064))
        }
    }

    private fun fetchLocationAndCalculateTimes() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val coordinates = if (location != null) {
                Coordinates(location.latitude, location.longitude)
            } else {
                Coordinates(28.9845, 77.7064)
            }
            calculateAndDisplayPrayerTimes(coordinates)
        }
    }

    private fun calculateAndDisplayPrayerTimes(coordinates: Coordinates) {
        val date = DateComponents.from(Date())
        val params = CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
        val prayerTimes = PrayerTimes(coordinates, date, params)
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val prayerDateMap = sortedMapOf(
            Prayer.FAJR to prayerTimes.fajr,
            Prayer.SUNRISE to prayerTimes.sunrise,
            Prayer.DHUHR to prayerTimes.dhuhr,
            Prayer.ASR to prayerTimes.asr,
            Prayer.MAGHRIB to prayerTimes.maghrib,
            Prayer.ISHA to prayerTimes.isha
        )

        prayerTimesTextViews[Prayer.FAJR]?.text = timeFormat.format(prayerTimes.fajr)
        prayerTimesTextViews[Prayer.DHUHR]?.text = timeFormat.format(prayerTimes.dhuhr)
        prayerTimesTextViews[Prayer.ASR]?.text = timeFormat.format(prayerTimes.asr)
        prayerTimesTextViews[Prayer.MAGHRIB]?.text = timeFormat.format(prayerTimes.maghrib)
        prayerTimesTextViews[Prayer.ISHA]?.text = timeFormat.format(prayerTimes.isha)

        updateNextPrayerUI(prayerDateMap)
    }

    private fun updateNextPrayerUI(prayerDateMap: Map<Prayer, Date>) {
        val now = Date()
        var nextPrayer = Prayer.NONE
        var nextPrayerTime: Date? = null

        for ((prayer, time) in prayerDateMap) {
            if (now.before(time) && prayer != Prayer.SUNRISE) {
                nextPrayer = prayer
                nextPrayerTime = time
                break
            }
        }

        if (nextPrayer == Prayer.NONE) {
            nextPrayer = Prayer.FAJR
            nextPrayerTime = prayerDateMap[Prayer.FAJR]?.let { Date(it.time + 24 * 60 * 60 * 1000) }
        }

        prayerPills.forEach { (prayer, pill) ->
            val isActive = (prayer == nextPrayer)
            pill.setBackgroundResource(if (isActive) R.drawable.bg_prayer_pill_active else R.drawable.bg_prayer_pill)
            pill.setTextColor(ContextCompat.getColor(requireContext(), if (isActive) R.color.white else R.color.black))
        }

        if (nextPrayerTime != null) {
            val totalDurationBetweenPrayers = getTotalDuration(prayerDateMap, nextPrayer)
            val timeDiff = nextPrayerTime.time - now.time
            startCountdown(timeDiff, totalDurationBetweenPrayers, nextPrayer.name)
        } else {
            binding.tvNextPrayerLabel.text = "Next: Fajr"
            binding.tvNextPrayerTime.text = "Tomorrow"
        }
    }

    private fun getTotalDuration(prayerDateMap: Map<Prayer, Date>, nextPrayer: Prayer): Long {
        val prayerList = prayerDateMap.keys.filter { it != Prayer.SUNRISE }.toList()
        val currentIndex = prayerList.indexOf(nextPrayer)
        if (currentIndex == -1) return 0

        val previousPrayerIndex = if (currentIndex == 0) prayerList.size - 1 else currentIndex - 1
        val previousPrayer = prayerList[previousPrayerIndex]

        val previousPrayerTime = prayerDateMap[previousPrayer]
        val nextPrayerTime = prayerDateMap[nextPrayer]

        return if (previousPrayerTime != null && nextPrayerTime != null) {
            if (nextPrayer == Prayer.FAJR) {
                nextPrayerTime.time + (24 * 60 * 60 * 1000) - previousPrayerTime.time
            } else {
                nextPrayerTime.time - previousPrayerTime.time
            }
        } else {
            0
        }
    }

    private fun startCountdown(millisInFuture: Long, totalDuration: Long, nextPrayerName: String) {
        countDownTimer?.cancel()
        binding.tvNextPrayerLabel.text = "Next: $nextPrayerName"

        countDownTimer = object : CountDownTimer(millisInFuture, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                binding.tvNextPrayerTime.text = String.format("-%02d:%02d:%02d", hours, minutes, seconds)

                if (totalDuration > 0) {
                    val elapsedTime = totalDuration - millisUntilFinished
                    val progress = (100 * elapsedTime / totalDuration).toInt()
                    binding.prayerRing.progress = progress
                }
            }
            override fun onFinish() {
                binding.tvNextPrayerTime.text = "Azaan Time!"
                fetchLocationAndCalculateTimes()
            }
        }.start()
    }

    private fun setupClickListeners() {
        prayerPills.forEach { (prayer, pill) ->
            pill.setOnClickListener {
                Toast.makeText(requireContext(), "${prayer.name} par click kiya gaya", Toast.LENGTH_SHORT).show()
            }
        }

        prayerTimesTextViews.forEach { (prayer, timeView) ->
            timeView.setOnLongClickListener {
                showTimePickerDialog(timeView, prayer.name)
                true
            }
        }

        binding.btnQibla.setOnClickListener {
            startActivity(Intent(requireActivity(), QiblaActivity::class.java))
        }
        binding.btnShare.setOnClickListener { Toast.makeText(requireActivity(), "Hadith share hogi", Toast.LENGTH_SHORT).show() }
        binding.btnAlerts.setOnClickListener { view ->
            val popup = PopupMenu(requireContext(), view)
            popup.menu.add("Mute for today")
            popup.menu.add("Snooze for 15m")
            popup.setOnMenuItemClickListener {
                Toast.makeText(requireContext(), "${it.title} chuna gaya", Toast.LENGTH_SHORT).show()
                true
            }
            popup.show()
        }
    }

    private fun showTimePickerDialog(textView: TextView, prayerName: String) {
        val calendar = Calendar.getInstance()
        val timePickerDialog = TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            val newTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(calendar.time)
            textView.text = newTime
            Toast.makeText(requireContext(), "$prayerName ka waqt badal gaya hai", Toast.LENGTH_SHORT).show()
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)
        timePickerDialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        _binding = null
    }
}