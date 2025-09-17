package com.masjid.app

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.*
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.masjid.app.databinding.FragmentHomeBinding
import java.util.*
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var prayerPills: Map<Prayer, MaterialButton>
    private lateinit var prayerTimeViews: Map<Prayer, TextView>
    private var countDownTimer: CountDownTimer? = null
    private lateinit var prayerPrefs: SharedPreferences
    private lateinit var appPrefs: SharedPreferences
    private lateinit var db: FirebaseFirestore
    private var lastCheckedDate: Long = 0

    enum class Prayer { FAJR, DHUHR, ASR, MAGHRIB, ISHA, SUNRISE, NONE }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(requireContext(), "Notifications permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prayerPrefs = requireActivity().getSharedPreferences("PrayerTimes", Context.MODE_PRIVATE)
        appPrefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        db = FirebaseFirestore.getInstance()

        lastCheckedDate = appPrefs.getLong("last_date_change", 0)

        initializeViews()
        setupClickListeners()
        checkPermissions()
        displayHijriDate()
    }

    override fun onResume() {
        super.onResume()
        val lastDateChange = appPrefs.getLong("last_date_change", 0)
        if (lastDateChange > lastCheckedDate) {
            lastCheckedDate = lastDateChange
            fetchPrayerTimesFromFirebase()
            displayHijriDate()
        }
    }


    private fun initializeViews() {
        prayerPills = mapOf(
            Prayer.FAJR to binding.pillFajr,
            Prayer.DHUHR to binding.pillDhuhr,
            Prayer.ASR to binding.pillAsr,
            Prayer.MAGHRIB to binding.pillMaghrib,
            Prayer.ISHA to binding.pillIsha
        )
        prayerTimeViews = mapOf(
            Prayer.FAJR to binding.timeFajr,
            Prayer.DHUHR to binding.timeDhuhr,
            Prayer.ASR to binding.timeAsr,
            Prayer.MAGHRIB to binding.timeMaghrib,
            Prayer.ISHA to binding.timeIsha
        )
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val alarmManager = context?.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent().also { intent ->
                    intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    startActivity(intent)
                }
            }
        }
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
        } else {
            fetchPrayerTimesFromFirebase()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchPrayerTimesFromFirebase()
        } else {
            Toast.makeText(requireContext(), "Sahi namaz time ke liye location permission zaroori hai", Toast.LENGTH_LONG).show()
            fetchPrayerTimesFromFirebase()
        }
    }

    private fun fetchPrayerTimesFromFirebase() {
        db.collection("namaz_times").document("masjid_e_aman")
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    displayPrayerTimes(document.data)
                } else {
                    fetchLocationAndCalculateTimes()
                }
            }
            .addOnFailureListener {
                fetchLocationAndCalculateTimes()
            }
    }

    private fun fetchLocationAndCalculateTimes() {
        if (context == null) return
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            calculateAndDisplayPrayerTimes(Coordinates(28.9845, 77.7064))
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
        val timeFormat = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault())

        val calculatedTimes = mapOf(
            "fajr" to timeFormat.format(prayerTimes.fajr),
            "dhuhr" to timeFormat.format(prayerTimes.dhuhr),
            "asr" to timeFormat.format(prayerTimes.asr),
            "maghrib" to timeFormat.format(prayerTimes.maghrib),
            "isha" to timeFormat.format(prayerTimes.isha),
            "sunrise" to timeFormat.format(prayerTimes.sunrise)
        )
        displayPrayerTimes(calculatedTimes)
    }

    private fun displayPrayerTimes(times: Map<String, Any>?) {
        val timeFormat = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault())
        val prayerDateMap = mutableMapOf<Prayer, Date>()

        val prayerMap = mapOf(
            Prayer.FAJR to times?.get("fajr").toString(),
            Prayer.DHUHR to times?.get("dhuhr").toString(),
            Prayer.ASR to times?.get("asr").toString(),
            Prayer.MAGHRIB to times?.get("maghrib").toString(),
            Prayer.ISHA to times?.get("isha").toString()
        )

        binding.tvSunriseTime.text = "Sunrise: ${times?.get("sunrise")}"

        prayerMap.forEach { (prayer, timeStr) ->
            val timeView = prayerTimeViews[prayer]
            timeView?.text = timeStr
            try {
                val timeCal = Calendar.getInstance().apply { time = timeFormat.parse(timeStr)!! }
                val todayCal = Calendar.getInstance()
                todayCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                todayCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                todayCal.set(Calendar.SECOND, 0)
                prayerDateMap[prayer] = todayCal.time
            } catch (e: Exception) {
                // Handle exception
            }
        }
        updateNextPrayerUI(prayerDateMap)
    }

    private fun updateNextPrayerUI(prayerDateMap: Map<Prayer, Date>) {
        val now = Date()
        var nextPrayer = Prayer.NONE
        var nextPrayerTime: Date? = null
        val sortedPrayers = prayerDateMap.entries.sortedBy { it.value }

        for ((prayer, time) in sortedPrayers) {
            if (now.before(time)) {
                nextPrayer = prayer
                nextPrayerTime = time
                break
            }
        }

        if (nextPrayer == Prayer.NONE) {
            nextPrayer = sortedPrayers.firstOrNull()?.key ?: Prayer.FAJR
            nextPrayerTime = sortedPrayers.firstOrNull()?.value?.let { Date(it.time + TimeUnit.DAYS.toMillis(1)) }
        }

        prayerPills.forEach { (prayer, pill) ->
            pill.isSelected = prayer == nextPrayer
        }

        if (nextPrayer != Prayer.NONE && nextPrayerTime != null) {
            binding.tvNextPrayerName.text = prayerPills[nextPrayer]?.text.toString()
            binding.tvNextPrayerTime.text = prayerTimeViews[nextPrayer]?.text.toString()
            val duration = nextPrayerTime.time - now.time
            startCountdown(duration)
            binding.nextPrayerCard.setCardBackgroundColor(if (duration < 0) ContextCompat.getColor(requireContext(), R.color.red_pending) else ContextCompat.getColor(requireContext(), R.color.colorPrimary))
            getPreviousPrayerTime(prayerDateMap, nextPrayer)?.let {
                val totalDuration = nextPrayerTime.time - it.time
                if (totalDuration > 0) {
                    val elapsedTime = now.time - it.time
                    binding.prayerRing.progress = (100 * elapsedTime / totalDuration).toInt()
                }
            }
        }
    }

    private fun getPreviousPrayerTime(prayerDateMap: Map<Prayer, Date>, nextPrayer: Prayer): Date? {
        val prayerList = prayerDateMap.keys.filter { it != Prayer.SUNRISE }.sortedBy { prayerDateMap[it] }.toList()
        val currentIndex = prayerList.indexOf(nextPrayer)
        return if (currentIndex > 0) {
            prayerDateMap[prayerList[currentIndex - 1]]
        } else {
            prayerDateMap[prayerList.last()]?.let { Date(it.time - TimeUnit.DAYS.toMillis(1)) }
        }
    }

    private fun startCountdown(millisInFuture: Long) {
        countDownTimer?.cancel()
        binding.prayerRing.visibility = View.VISIBLE
        binding.prayerRingLabel.visibility = View.VISIBLE
        binding.btnPauseAzan.visibility = View.GONE

        countDownTimer = object : CountDownTimer(millisInFuture, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                binding.tvNextPrayerCountdown.text = String.format("-%02d:%02d:%02d", hours, minutes, seconds)
            }

            override fun onFinish() {
                binding.tvNextPrayerCountdown.text = "Azaan Time!"
                binding.prayerRing.visibility = View.GONE
                binding.prayerRingLabel.visibility = View.GONE
                binding.btnPauseAzan.visibility = View.VISIBLE
                fetchPrayerTimesFromFirebase()
            }
        }.start()
    }

    private fun setupClickListeners() {
        prayerPills.forEach { (prayer, pill) ->
            pill.setOnLongClickListener {
                prayerTimeViews[prayer]?.let { timeView ->
                    showTimePickerDialog(timeView, prayer)
                }
                true
            }
        }

        prayerTimeViews.forEach { (prayer, timeView) ->
            timeView.setOnLongClickListener {
                showTimePickerDialog(timeView, prayer)
                true
            }
        }

        binding.btnQibla.setOnClickListener {
            startActivity(Intent(requireActivity(), QiblaActivity::class.java))
        }

        binding.tasbihCounterCard.setOnClickListener {
            startActivity(Intent(requireActivity(), TasbihCounterActivity::class.java))
        }

        binding.hijriCalendarCard.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                startActivity(Intent(requireActivity(), HijriCalendarActivity::class.java))
            } else {
                Toast.makeText(requireContext(), "Hijri Calendar requires Android 7.0 or higher.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAlerts.setOnClickListener {
            showAlarmsDialog()
        }

        binding.btnPauseAzan.setOnClickListener {
            val intent = Intent(context, AzanPlaybackService::class.java).apply {
                action = AzanPlaybackService.ACTION_PAUSE_RESUME
            }
            context?.startService(intent)
        }
    }

    private fun showAlarmsDialog() {
        // ... (existing alarm dialog code remains the same)
    }

    private fun schedulePrayerAlarms(prayerDateMap: Map<Prayer, Date>) {
        // ... (existing alarm scheduling code remains the same)
    }

    private fun showTimePickerDialog(textView: TextView, prayer: Prayer) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            val newTime = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(calendar.time)

            with(prayerPrefs.edit()) {
                putString(prayer.name, newTime)
                apply()
            }

            fetchPrayerTimesFromFirebase()
            Toast.makeText(requireContext(), "${prayerPills[prayer]?.text} time updated", Toast.LENGTH_SHORT).show()
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }

    private fun displayHijriDate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val islamicCalendar = IslamicCalendar()
            val format = SimpleDateFormat("d MMMM yyyy", ULocale("en_SA@calendar=islamic"))
            binding.tvHijriDate.text = format.format(islamicCalendar.time)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        _binding = null
    }
}