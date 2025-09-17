package com.masjid.app

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
import com.masjid.app.databinding.FragmentHomeBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var prayerPills: Map<Prayer, MaterialButton>
    private lateinit var prayerTimeViews: Map<Prayer, TextView>
    private var countDownTimer: CountDownTimer? = null
    private lateinit var sharedPreferences: SharedPreferences

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
        sharedPreferences = requireActivity().getSharedPreferences("PrayerTimes", Context.MODE_PRIVATE)
        initializeViews()
        setupClickListeners()
        checkPermissions()
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
        // Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Exact Alarm Permission (Android 12+)
        val alarmManager = context?.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent().also { intent ->
                    intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    startActivity(intent)
                }
            }
        }

        // Location Permission
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
        if (context == null) return
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

        val finalPrayerDateMap = prayerDateMap.toMutableMap()
        val todayCalendar = Calendar.getInstance()
        val year = todayCalendar.get(Calendar.YEAR)
        val month = todayCalendar.get(Calendar.MONTH)
        val day = todayCalendar.get(Calendar.DAY_OF_MONTH)

        prayerTimeViews.forEach { (prayer, timeView) ->
            val prayerPill = prayerPills[prayer]
            if (prayer == Prayer.DHUHR && todayCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
                prayerPill?.text = "Jumma"
            } else {
                prayerPill?.text = prayer.name.capitalize(Locale.ROOT)
            }

            val savedTime = sharedPreferences.getString(prayer.name, null)
            val prayerDate = prayerDateMap[prayer]

            val displayTime = savedTime ?: prayerDate?.let { timeFormat.format(it) } ?: "N/A"
            timeView.text = displayTime

            if (savedTime != null) {
                try {
                    val savedTimeCalendar = Calendar.getInstance().apply { time = timeFormat.parse(savedTime)!! }
                    finalPrayerDateMap[prayer] = Calendar.getInstance().apply { set(year, month, day, savedTimeCalendar.get(Calendar.HOUR_OF_DAY), savedTimeCalendar.get(Calendar.MINUTE), 0) }.time
                } catch (e: Exception) {
                    // Ignore parsing error, use calculated time
                }
            }
        }
        updateNextPrayerUI(finalPrayerDateMap)
        schedulePrayerAlarms(finalPrayerDateMap)
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
            nextPrayerTime = prayerDateMap[Prayer.FAJR]?.let { Date(it.time + TimeUnit.DAYS.toMillis(1)) }
        }

        prayerPills.forEach { (prayer, pill) ->
            if (prayer == nextPrayer) {
                pill.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
                pill.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            } else {
                pill.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_green_bg))
                pill.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            }
        }

        if (nextPrayer != Prayer.NONE && nextPrayerTime != null) {
            val nextPrayerName = prayerPills[nextPrayer]?.text.toString()
            binding.tvNextPrayerName.text = nextPrayerName
            binding.tvNextPrayerTime.text = prayerTimeViews[nextPrayer]?.text.toString()

            val duration = nextPrayerTime.time - now.time
            startCountdown(duration)

            if (duration < 0) {
                binding.nextPrayerCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_pending))
            } else {
                binding.nextPrayerCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
            }

            val previousPrayerTime = getPreviousPrayerTime(prayerDateMap, nextPrayer)
            if (previousPrayerTime != null) {
                val totalDuration = nextPrayerTime.time - previousPrayerTime.time
                if (totalDuration > 0) {
                    val elapsedTime = now.time - previousPrayerTime.time
                    binding.prayerRing.progress = (100 * elapsedTime / totalDuration).toInt()
                }
            }
        }
    }

    private fun getPreviousPrayerTime(prayerDateMap: Map<Prayer, Date>, nextPrayer: Prayer): Date? {
        val prayerList = prayerDateMap.keys.filter { it != Prayer.SUNRISE }.toList()
        val currentIndex = prayerList.indexOf(nextPrayer)
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else prayerList.size - 1
        return prayerDateMap[prayerList[prevIndex]]
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
                fetchLocationAndCalculateTimes()
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
        val tunes = arrayOf("Default Azan (azan1)", "Azan 2", "Mute All")
        val tuneFiles = mapOf(
            tunes[0] to R.raw.azan1,
            tunes[1] to R.raw.azan2,
            tunes[2] to 0 // Mute
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Select Azan Tune")
            .setItems(tunes) { _, which ->
                val selectedTuneName = tunes[which]
                val selectedTuneId = tuneFiles[selectedTuneName] ?: 0

                with(sharedPreferences.edit()) {
                    putInt("SELECTED_TUNE_ID", selectedTuneId)
                    putString("SELECTED_TUNE_NAME", selectedTuneName)
                    apply()
                }
                Toast.makeText(requireContext(), "$selectedTuneName selected", Toast.LENGTH_SHORT).show()
                fetchLocationAndCalculateTimes()
            }
            .show()
    }

    private fun schedulePrayerAlarms(prayerDateMap: Map<Prayer, Date>) {
        val alarmManager = context?.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val selectedTuneId = sharedPreferences.getInt("SELECTED_TUNE_ID", R.raw.azan1)
        val now = System.currentTimeMillis()

        prayerDateMap.forEach { (prayer, date) ->
            val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                putExtra("PRAYER_NAME", prayerPills[prayer]?.text.toString())
                putExtra("TUNE_RESOURCE_ID", selectedTuneId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                prayer.ordinal,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // Cancel any existing alarm
            alarmManager.cancel(pendingIntent)

            if (date.time > now && selectedTuneId != 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    // Permission not granted
                    return@forEach
                }
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, date.time, pendingIntent)
            }
        }
    }

    private fun showTimePickerDialog(textView: TextView, prayer: Prayer) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            val newTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(calendar.time)

            with(sharedPreferences.edit()) {
                putString(prayer.name, newTime)
                apply()
            }

            fetchLocationAndCalculateTimes()
            Toast.makeText(requireContext(), "${prayerPills[prayer]?.text} time updated", Toast.LENGTH_SHORT).show()
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        _binding = null
    }
}