package com.masjid.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import com.masjid.app.databinding.FragmentFundBinding
import com.masjid.app.databinding.ItemNamazRowBinding
import java.text.SimpleDateFormat
import java.util.*

class FundFragment : Fragment() {

    private var _binding: FragmentFundBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFundBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()

        setupButtons()
        fetchNamazTimes()
    }

    private fun setupButtons() {
        binding.btnMonthlyChanda.setOnClickListener {
            startActivity(Intent(activity, MonthlyChandaActivity::class.java))
        }
        binding.btnAnyaAmdani.setOnClickListener {
            startActivity(Intent(activity, AnyaChandaActivity::class.java))
        }
    }

    private fun fetchNamazTimes() {
        db.collection("namaz_times").document("masjid_e_aman")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) {
                    return@addSnapshotListener
                }
                val data = snapshot.data
                data?.let {
                    setNamazTime(binding.rowFajr, "Fajr", it["fajr"] as? String)
                    setNamazTime(binding.rowDhuhr, "Dhuhr", it["dhuhr"] as? String)
                    setNamazTime(binding.rowAsr, "Asr", it["asr"] as? String)
                    setNamazTime(binding.rowMaghrib, "Maghrib", it["maghrib"] as? String)
                    setNamazTime(binding.rowIsha, "Isha", it["isha"] as? String)
                    setNamazTime(binding.rowJumma, "Jumma", it["jumma"] as? String)
                }
            }
    }

    private fun setNamazTime(namazRow: ItemNamazRowBinding, name: String, time: String?) {
        namazRow.namazName.text = name
        namazRow.namazTime.text = formatTime(time)
    }

    private fun formatTime(time: String?): String {
        return try {
            val sdf24 = SimpleDateFormat("HH:mm", Locale.getDefault())
            val sdf12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = sdf24.parse(time!!)
            sdf12.format(date!!)
        } catch (e: Exception) {
            time ?: "Not Set"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}