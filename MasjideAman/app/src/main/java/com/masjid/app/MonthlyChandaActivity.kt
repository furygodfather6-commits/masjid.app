package com.masjid.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.masjid.app.adapters.YearlyGridCompactAdapter
import com.masjid.app.databinding.ActivityMonthlyChandaBinding
import com.masjid.app.databinding.DialogAddMemberBinding
import com.masjid.app.databinding.DialogCustomizeFeeBinding
import com.masjid.app.databinding.DialogEditPaymentBinding
import com.masjid.app.models.Member
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MonthlyChandaActivity : AppCompatActivity(), YearlyGridCompactAdapter.OnMonthClickListener {

    private lateinit var binding: ActivityMonthlyChandaBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var yearlyGridAdapter: YearlyGridCompactAdapter
    private val fullMemberList = mutableListOf<Member>()
    private val filteredMemberList = mutableListOf<Member>()
    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonthlyChandaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupRecyclerView()
        setupYearSpinner()
        setupSearch()
        setupButtons()
        fetchMembers()
    }

    private fun setupButtons() {
        binding.buttonCustomizeFee.setOnClickListener { showCustomizeFeeDialog() }
        binding.fabAddMember.setOnClickListener { showAddOrEditMemberDialog(null) }
        binding.btnAnalysis.setOnClickListener { showAnalysisDialog() }
    }

    private fun setupRecyclerView() {
        yearlyGridAdapter = YearlyGridCompactAdapter(filteredMemberList, selectedYear, this)
        binding.yearlyGridRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MonthlyChandaActivity)
            adapter = yearlyGridAdapter
        }
    }

    private fun setupYearSpinner() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (2023..2050).map { it.toString() }.reversed().toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, years)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.yearSpinner.adapter = adapter
        binding.yearSpinner.setSelection(years.indexOf(currentYear.toString()))

        binding.yearSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedYear = years[position].toInt()
                yearlyGridAdapter = YearlyGridCompactAdapter(filteredMemberList, selectedYear, this@MonthlyChandaActivity)
                binding.yearlyGridRecyclerView.adapter = yearlyGridAdapter
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })
    }

    private fun fetchMembers() {
        db.collection("chanda_data/shared/members")
            .orderBy("name")
            .addSnapshotListener { snapshots, e ->
                if (e != null) { return@addSnapshotListener }
                fullMemberList.clear()
                snapshots?.forEach { document ->
                    val member = document.toObject(Member::class.java).copy(id = document.id)
                    fullMemberList.add(member)
                }
                filterList(binding.searchView.query.toString())
            }
    }

    private fun calculateStats(): Map<String, Double> {
        var yearlyPotential = 0.0
        var yearlyCollected = 0.0
        var monthlyCollected = 0.0
        var monthlyPotential = 0.0
        var prevMonthlyCollected = 0.0

        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
        val prevMonth = if (currentMonth == 1) 12 else currentMonth - 1
        val yearForPrevMonth = if (currentMonth == 1) selectedYear - 1 else selectedYear

        fullMemberList.forEach { member ->
            // Previous month collection calculation
            val prevMonthKey = "$yearForPrevMonth-${String.format("%02d", prevMonth)}"
            val prevPaymentData = member.payments?.get(prevMonthKey) as? Map<*,*>
            prevMonthlyCollected += (prevPaymentData?.get("amount") as? Number)?.toDouble() ?: 0.0

            for (i in 1..12) {
                val monthKey = "$selectedYear-${String.format("%02d", i)}"
                val customFee = (member.customFees?.get(monthKey) as? Number)?.toDouble()
                val monthlyFee = customFee ?: member.monthlyAmount ?: 0.0
                yearlyPotential += monthlyFee

                val paymentData = member.payments?.get(monthKey) as? Map<*, *>
                val paidAmount = (paymentData?.get("amount") as? Number)?.toDouble() ?: 0.0
                yearlyCollected += paidAmount

                if (i == currentMonth) {
                    monthlyPotential += monthlyFee
                    monthlyCollected += paidAmount
                }
            }
        }

        return mapOf(
            "totalMembers" to fullMemberList.size.toDouble(),
            "yearlyPotential" to yearlyPotential,
            "yearlyCollected" to yearlyCollected,
            "yearlyPending" to yearlyPotential - yearlyCollected,
            "monthlyCollected" to monthlyCollected,
            "monthlyPending" to monthlyPotential - monthlyCollected,
            "prevMonthlyCollected" to prevMonthlyCollected
        )
    }

    private fun showAnalysisDialog() {
        val stats = calculateStats()
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_yearly_analysis, null)

        dialogView.findViewById<TextView>(R.id.analysis_title).text = "Year $selectedYear Analysis"
        dialogView.findViewById<TextView>(R.id.stat_total_members).text = stats["totalMembers"]?.toInt().toString()
        dialogView.findViewById<TextView>(R.id.stat_yearly_potential).text = formatAmount(stats["yearlyPotential"] ?: 0.0)
        dialogView.findViewById<TextView>(R.id.stat_yearly_collected).text = formatAmount(stats["yearlyCollected"] ?: 0.0)
        dialogView.findViewById<TextView>(R.id.stat_yearly_pending).text = formatAmount(stats["yearlyPending"] ?: 0.0)
        dialogView.findViewById<TextView>(R.id.stat_monthly_collected).text = formatAmount(stats["monthlyCollected"] ?: 0.0)
        dialogView.findViewById<TextView>(R.id.stat_monthly_pending).text = formatAmount(stats["monthlyPending"] ?: 0.0)

        val comparisonStat = dialogView.findViewById<TextView>(R.id.stat_month_comparison)
        val monthlyCollected = stats["monthlyCollected"] ?: 0.0
        val prevMonthlyCollected = stats["prevMonthlyCollected"] ?: 0.0
        val diff = monthlyCollected - prevMonthlyCollected
        val percentage = if (prevMonthlyCollected > 0) (diff / prevMonthlyCollected) * 100 else 0.0

        comparisonStat.text = when {
            diff > 0 -> "+${formatAmount(diff)} (${String.format("%.1f", abs(percentage))}%)"
            else -> "${formatAmount(diff)} (${String.format("%.1f", abs(percentage))}%)"
        }
        comparisonStat.setTextColor(if (diff >= 0) ContextCompat.getColor(this, R.color.green_paid) else ContextCompat.getColor(this, R.color.red_pending))

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun formatAmount(amount: Double): String {
        return "₹${"%,d".format(amount.toInt())}"
    }

    private fun showAddOrEditMemberDialog(member: Member?) {
        val dialogBinding = DialogAddMemberBinding.inflate(layoutInflater)
        val isEditing = member != null
        if (isEditing) {
            dialogBinding.nameEditText.setText(member?.name)
            dialogBinding.phoneEditText.setText(member?.phone)
            dialogBinding.amountEditText.setText(member?.monthlyAmount.toString())
        }
        AlertDialog.Builder(this)
            .setTitle(if (isEditing) "Sadasya Edit Karein" else "Naya Sadasya Jodein")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val name = dialogBinding.nameEditText.text.toString().trim()
                val amountStr = dialogBinding.amountEditText.text.toString().trim()
                if (name.isEmpty() || amountStr.isEmpty()) { return@setPositiveButton }
                val amount = amountStr.toDoubleOrNull()
                if (amount == null) { return@setPositiveButton }
                val memberData = hashMapOf(
                    "name" to name,
                    "phone" to dialogBinding.phoneEditText.text.toString().trim(),
                    "monthlyAmount" to amount
                )
                if (isEditing) {
                    db.collection("chanda_data/shared/members").document(member!!.id!!).update(memberData as Map<String, Any>)
                } else {
                    db.collection("chanda_data/shared/members").add(memberData)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onMonthClick(member: Member, monthIndex: Int) {
        val monthKey = "$selectedYear-${String.format("%02d", monthIndex + 1)}"
        val paymentData = member.payments?.get(monthKey) as? Map<*, *>
        val currentAmount = (paymentData?.get("amount") as? Number)?.toDouble() ?: 0.0
        val currentDate = paymentData?.get("date") as? String ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val dialogBinding = DialogEditPaymentBinding.inflate(layoutInflater)
        dialogBinding.amountEditText.setText(if (currentAmount > 0) currentAmount.toString() else "")
        dialogBinding.dateEditText.setText(currentDate)

        AlertDialog.Builder(this)
            .setTitle("${member.name} - Payment for ${monthIndex + 1}/$selectedYear")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val amount = dialogBinding.amountEditText.text.toString().toDoubleOrNull() ?: 0.0
                val date = dialogBinding.dateEditText.text.toString()
                val newPaymentData = hashMapOf("amount" to amount, "date" to date)
                db.collection("chanda_data/shared/members").document(member.id!!)
                    .update("payments.$monthKey", newPaymentData)
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete Payment") { _, _ ->
                db.collection("chanda_data/shared/members").document(member.id!!)
                    .update("payments.$monthKey", FieldValue.delete())
            }
            .show()
    }

    private fun showCustomizeFeeDialog() {
        val dialogBinding = DialogCustomizeFeeBinding.inflate(layoutInflater)
        val months = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.monthSpinner.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("Customize Monthly Fee")
            .setView(dialogBinding.root)
            .setPositiveButton("Apply to All") { _, _ ->
                val monthIndex = dialogBinding.monthSpinner.selectedItemPosition + 1
                val amountStr = dialogBinding.amountEditText.text.toString().trim()
                if(amountStr.isEmpty()) return@setPositiveButton
                val amount = amountStr.toDoubleOrNull() ?: return@setPositiveButton

                val monthKey = "$selectedYear-${String.format("%02d", monthIndex)}"

                db.collection("chanda_data/shared/members").get().addOnSuccessListener {
                    val batch = db.batch()
                    for(doc in it.documents) {
                        val docRef = db.collection("chanda_data/shared/members").document(doc.id)
                        batch.update(docRef, "customFees.$monthKey", amount)
                    }
                    batch.commit().addOnSuccessListener {
                        Toast.makeText(this, "Fee for ${months[monthIndex-1]} updated for all members.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_search)?.isVisible = false
        menu.findItem(R.id.action_invite_code)?.isVisible = false
        menu.findItem(R.id.action_view_grid)?.isVisible = false
        menu.findItem(R.id.action_export_csv)?.isVisible = false
        menu.findItem(R.id.action_export_pdf)?.isVisible = false
        return true
    }

    private fun filterList(query: String?) {
        filteredMemberList.clear()
        if (query.isNullOrEmpty()) {
            filteredMemberList.addAll(fullMemberList)
        } else {
            val filtered = fullMemberList.filter {
                it.name?.contains(query, ignoreCase = true) == true
            }
            filteredMemberList.addAll(filtered)
        }
        yearlyGridAdapter.updateData(filteredMemberList)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                auth.signOut()
                startActivity(Intent(this, AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}