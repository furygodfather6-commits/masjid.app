package com.masjid.app

import android.content.Context
import android.graphics.Typeface
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.masjid.app.databinding.ActivityHijriCalendarBinding
import java.util.Date
import java.util.Locale

// Data class to hold all information for a single day cell
data class HijriDay(
    val date: Date,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val eventText: String? = null,
    val moonPhase: MoonPhase? = null
)

enum class MoonPhase {
    NEW_MOON, FULL_MOON
}

// Data class for Hijri month details
data class HijriMonthInfo(
    val englishName: String,
    val hindiName: String,
    val importantDates: String
)

@RequiresApi(Build.VERSION_CODES.N)
class HijriCalendarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHijriCalendarBinding
    private val hijriCalendar = IslamicCalendar()

    // Data source for Hijri months with CORRECTED constants
    private val hijriMonthsInfo: Map<Int, HijriMonthInfo> = mapOf(
        IslamicCalendar.MUHARRAM to HijriMonthInfo("Muharram", "मुहर्रम", "1st: Islamic New Year\n10th: Day of Ashura"),
        IslamicCalendar.SAFAR to HijriMonthInfo("Safar", "सफ़र", "No specific global events this month."),
        IslamicCalendar.RABI_1 to HijriMonthInfo("Rabi' al-awwal", "रबी-उल-अव्वल", "12th: Mawlid an-Nabi (Birth of the Prophet)"),
        IslamicCalendar.RABI_2 to HijriMonthInfo("Rabi' al-thani", "रबी-उल-आखिर", "No specific global events this month."),
        IslamicCalendar.JUMADA_1 to HijriMonthInfo("Jumada al-awwal", "जमाद-उल-अव्वल", "No specific global events this month."),
        IslamicCalendar.JUMADA_2 to HijriMonthInfo("Jumada al-thani", "जमाद-उल-आखिर", "No specific global events this month."),
        IslamicCalendar.RAJAB to HijriMonthInfo("Rajab", "रजब", "27th: Isra and Mi'raj"),
        IslamicCalendar.SHABAN to HijriMonthInfo("Sha'ban", "शाबान", "15th: Night of Bara'at\nNote: Ayyam al-Beedh are the 13th, 14th, and 15th."),
        IslamicCalendar.RAMADAN to HijriMonthInfo("Ramadan", "रमज़ान", "Month of Fasting\nFinal 10 nights: Laylat al-Qadr"),
        IslamicCalendar.SHAWWAL to HijriMonthInfo("Shawwal", "शव्वाल", "1st: Eid al-Fitr"),
        IslamicCalendar.DHU_AL_QIDAH to HijriMonthInfo("Dhu al-Qi'dah", "ज़ु-अल-क़ादा", "Month of Hajj preparation."),
        IslamicCalendar.DHU_AL_HIJJAH to HijriMonthInfo("Dhu al-Hijjah", "ज़ु-अल-हिज्जा", "8th-13th: Hajj to Mecca\n9th: Day of Arafah\n10th: Eid al-Adha")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHijriCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupButtons()
        updateCalendar()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupButtons() {
        binding.previousMonthButton.setOnClickListener {
            hijriCalendar.add(IslamicCalendar.MONTH, -1)
            updateCalendar()
        }
        binding.nextMonthButton.setOnClickListener {
            hijriCalendar.add(IslamicCalendar.MONTH, 1)
            updateCalendar()
        }
    }

    private fun updateCalendar() {
        val currentMonthIndex = hijriCalendar.get(IslamicCalendar.MONTH)
        val monthInfo = hijriMonthsInfo[currentMonthIndex]

        // --- Header ---
        val hijriYear = hijriCalendar.get(IslamicCalendar.YEAR)
        binding.hijriMonthYearEnglish.text = "${monthInfo?.englishName ?: ""} (${monthInfo?.hindiName ?: ""}) $hijriYear"

        val arabicMonthFormat = SimpleDateFormat("MMMM", ULocale("ar"))
        val arabicYearFormat = SimpleDateFormat("yyyy", ULocale("ar"))
        val arabicYear = arabicYearFormat.format(hijriCalendar.time)
            .replace('١', '1').replace('٢', '2').replace('٣', '3').replace('٤', '4')
            .replace('٥', '5').replace('٦', '6').replace('٧', '7').replace('٨', '8')
            .replace('٩', '9').replace('٠', '0')
        binding.hijriMonthYearArabic.text = "${arabicMonthFormat.format(hijriCalendar.time)} - $arabicYear"

        val gregorianFormat = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
        val startGregorian = gregorianFormat.format(hijriCalendar.time)
        val tempCal = hijriCalendar.clone() as Calendar
        tempCal.add(Calendar.MONTH, 1)
        tempCal.add(Calendar.DAY_OF_MONTH, -1)
        val endGregorian = gregorianFormat.format(tempCal.time)
        binding.gregorianMonths.text = "(${startGregorian} - ${endGregorian})"

        // --- Important Dates ---
        binding.importantDatesText.text = "${monthInfo?.importantDates}\nNote: The Islamic day begins at sunset (Maghrib)."

        // --- Calendar Grid ---
        val days = createDayList()
        binding.calendarRecyclerView.layoutManager = GridLayoutManager(this, 7)
        binding.calendarRecyclerView.adapter = HijriCalendarAdapter(this, days)
    }

    private fun createDayList(): ArrayList<HijriDay> {
        val days = ArrayList<HijriDay>()
        val monthCalendar = hijriCalendar.clone() as IslamicCalendar
        monthCalendar.set(IslamicCalendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = Calendar.MONDAY
        val dayOfWeekOfFirst = monthCalendar.get(Calendar.DAY_OF_WEEK)
        val daysToSubtract = (dayOfWeekOfFirst - firstDayOfWeek + 7) % 7
        monthCalendar.add(IslamicCalendar.DAY_OF_MONTH, -daysToSubtract)

        val today = IslamicCalendar()

        while (days.size < 35) {
            val isTodayFlag = today.get(Calendar.YEAR) == monthCalendar.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == monthCalendar.get(Calendar.DAY_OF_YEAR)

            val (event, moonPhase) = getEventForDate(monthCalendar)

            days.add(
                HijriDay(
                    date = monthCalendar.time,
                    isCurrentMonth = monthCalendar.get(IslamicCalendar.MONTH) == hijriCalendar.get(IslamicCalendar.MONTH),
                    isToday = isTodayFlag,
                    eventText = event,
                    moonPhase = moonPhase
                )
            )
            monthCalendar.add(IslamicCalendar.DAY_OF_MONTH, 1)
        }
        return days
    }

    private fun getEventForDate(cal: IslamicCalendar): Pair<String?, MoonPhase?> {
        // You can add more specific day events here if needed
        when (cal.get(IslamicCalendar.DAY_OF_MONTH)) {
            1 -> return Pair("New Moon", MoonPhase.NEW_MOON)
            13, 14, 15 -> return Pair("Full Moon", MoonPhase.FULL_MOON)
        }
        return Pair(null, null)
    }
}

// --- ADAPTER ---
@RequiresApi(Build.VERSION_CODES.N)
class HijriCalendarAdapter(
    private val context: Context,
    private val days: List<HijriDay>
) : RecyclerView.Adapter<HijriCalendarAdapter.ViewHolder>() {

    private val gregorianDayFormat = SimpleDateFormat("d MMM", Locale.ENGLISH)

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayOfMonth: TextView = itemView.findViewById(R.id.hijriDayText)
        val gregorianDate: TextView = itemView.findViewById(R.id.gregorianDayText)
        val moonPhaseIcon: ImageView = itemView.findViewById(R.id.moonPhaseIcon)
        val eventText: TextView = itemView.findViewById(R.id.eventText)
        val dayCellContainer: FrameLayout = itemView.findViewById(R.id.day_cell_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val day = days[position]
        val tempCal = IslamicCalendar()
        tempCal.time = day.date

        holder.dayOfMonth.text = tempCal.get(IslamicCalendar.DAY_OF_MONTH).toString()
        holder.gregorianDate.text = gregorianDayFormat.format(day.date)

        holder.moonPhaseIcon.visibility = View.GONE
        holder.eventText.visibility = View.GONE

        if (day.isCurrentMonth) {
            holder.itemView.alpha = 1.0f
            holder.dayOfMonth.setTextColor(ContextCompat.getColor(context, R.color.black))
        } else {
            holder.itemView.alpha = 0.5f
        }

        if (tempCal.get(IslamicCalendar.DAY_OF_MONTH) in 13..15) { // Ayyam al-Beedh
            holder.dayOfMonth.setTypeface(null, Typeface.BOLD)
        } else {
            holder.dayOfMonth.setTypeface(null, Typeface.NORMAL)
        }

        day.moonPhase?.let {
            holder.moonPhaseIcon.visibility = View.VISIBLE
            val iconRes = if (it == MoonPhase.NEW_MOON) R.drawable.ic_new_moon else R.drawable.ic_full_moon
            holder.moonPhaseIcon.setImageResource(iconRes)
        }

        day.eventText?.let {
            if (it != "New Moon" && it != "Full Moon") {
                holder.eventText.visibility = View.VISIBLE
                holder.eventText.text = it
            }
        }

        val gregorianCal = java.util.Calendar.getInstance()
        gregorianCal.time = day.date
        if (gregorianCal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.FRIDAY) {
            holder.dayCellContainer.setBackgroundResource(R.drawable.glassmorphism_background_friday)
        } else {
            holder.dayCellContainer.setBackgroundResource(R.drawable.glassmorphism_background)
        }
    }

    override fun getItemCount(): Int = days.size
}