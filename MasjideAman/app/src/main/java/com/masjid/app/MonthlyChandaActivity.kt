package com.masjid.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.masjid.app.adapters.ChandaAdapter
import com.masjid.app.databinding.ActivityMonthlyChandaBinding
import com.masjid.app.databinding.DialogAddMemberBinding
import com.masjid.app.databinding.DialogCustomizeFeeBinding
import com.masjid.app.models.Member
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MonthlyChandaActivity : AppCompatActivity(), ChandaAdapter.OnItemClickListener {

    private lateinit var binding: ActivityMonthlyChandaBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var chandaAdapter: ChandaAdapter
    private val memberList = mutableListOf<Member>()
    private val filteredList = mutableListOf<Member>()
    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var currentFilter: String = "All"

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
        setupFilterChips()
        fetchMembers()

        binding.buttonViewGrid.setOnClickListener {
            startActivity(Intent(this, YearlyGridActivity::class.java).apply {
                putExtra("SELECTED_YEAR", selectedYear)
            })
        }

        binding.buttonCustomizeFee.setOnClickListener {
            showCustomizeFeeDialog()
        }

        binding.fabAddMember.setOnClickListener {
            showAddOrEditMemberDialog(null)
        }
    }

    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { group, checkedIds ->
            val chipId = checkedIds.firstOrNull() ?: R.id.chip_all
            val chip = group.findViewById<Chip>(chipId)
            currentFilter = when (chip.id) {
                R.id.chip_paid -> "Paid"
                R.id.chip_pending -> "Pending"
                R.id.chip_partial -> "Partial"
                else -> "All"
            }
            val searchView = binding.toolbar.menu?.findItem(R.id.action_search)?.actionView as? SearchView
            filterAndDisplayMembers(searchView?.query?.toString())
        }
    }


    private fun setupRecyclerView() {
        chandaAdapter = ChandaAdapter(filteredList, this, selectedYear)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MonthlyChandaActivity)
            adapter = chandaAdapter
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
                chandaAdapter.updateYear(selectedYear)
                val searchView = binding.toolbar.menu?.findItem(R.id.action_search)?.actionView as? SearchView
                filterAndDisplayMembers(searchView?.query?.toString())
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun fetchMembers() {
        db.collection("chanda_data/shared/members")
            .orderBy("name")
            .addSnapshotListener { snapshots, e ->
                if (e != null) { return@addSnapshotListener }
                memberList.clear()
                snapshots?.forEach { document ->
                    val member = document.toObject(Member::class.java).copy(id = document.id)
                    memberList.add(member)
                }
                val searchView = binding.toolbar.menu?.findItem(R.id.action_search)?.actionView as? SearchView
                filterAndDisplayMembers(searchView?.query?.toString())
            }
    }

    private fun filterAndDisplayMembers(query: String?) {
        val searchQuery = query?.trim() ?: ""

        var tempFilteredList = if (searchQuery.isEmpty()) {
            memberList
        } else {
            memberList.filter { it.name?.contains(searchQuery, ignoreCase = true) == true }
        }

        if (currentFilter != "All") {
            val calendar = Calendar.getInstance()
            // Important: We filter by the current month only if the selected year is the current year
            if (selectedYear == calendar.get(Calendar.YEAR)) {
                tempFilteredList = tempFilteredList.filter { member ->
                    val monthKey = "$selectedYear-${String.format("%02d", calendar.get(Calendar.MONTH) + 1)}"
                    val customFee = (member.customFees?.get(monthKey) as? Number)?.toDouble()
                    val monthlyAmount = customFee ?: member.monthlyAmount ?: 0.0
                    val paymentData = member.payments?.get(monthKey) as? Map<*, *>
                    val paidAmount = (paymentData?.get("amount") as? Number)?.toDouble() ?: 0.0

                    when (currentFilter) {
                        "Paid" -> paidAmount >= monthlyAmount && monthlyAmount > 0
                        "Pending" -> paidAmount == 0.0 && monthlyAmount > 0
                        "Partial" -> paidAmount > 0 && paidAmount < monthlyAmount
                        else -> true
                    }
                }
            }
        }

        filteredList.clear()
        filteredList.addAll(tempFilteredList)
        chandaAdapter.notifyDataSetChanged()
        updateDashboardStats()
    }


    private fun updateDashboardStats() {
        val calendar = Calendar.getInstance()

        // Only show monthly stats if the selected year is the current year
        if (selectedYear == calendar.get(Calendar.YEAR)) {
            binding.monthlyStatsContainer.visibility = View.VISIBLE
            val currentMonth = calendar.get(Calendar.MONTH) + 1
            val monthKey = "$selectedYear-${String.format("%02d", currentMonth)}"
            val monthName = SimpleDateFormat("MMMM", Locale("hi")).format(calendar.time)

            var monthlyPotential = 0.0
            var monthlyCollected = 0.0

            memberList.forEach { member ->
                val customFee = (member.customFees?.get(monthKey) as? Number)?.toDouble()
                val feeForThisMonth = customFee ?: member.monthlyAmount ?: 0.0
                monthlyPotential += feeForThisMonth
                val paymentData = member.payments?.get(monthKey) as? Map<*, *>
                monthlyCollected += (paymentData?.get("amount") as? Number)?.toDouble() ?: 0.0
            }

            val monthlyPending = monthlyPotential - monthlyCollected
            val progress = if (monthlyPotential > 0) (monthlyCollected / monthlyPotential * 100).toInt() else 0

            binding.textViewCurrentMonthTitle.text = "$monthName का हिसाब"
            binding.statMonthlyPotential.text = formatAmount(monthlyPotential)
            binding.statMonthlyCollected.text = formatAmount(monthlyCollected)
            binding.statMonthlyPending.text = formatAmount(monthlyPending)
            binding.progressBarMonthly.progress = progress
        } else {
            binding.monthlyStatsContainer.visibility = View.GONE
        }
    }

    private fun formatAmount(amount: Double): String {
        return if (amount >= 1000) {
            String.format("₹%.1fk", amount / 1000)
        } else {
            "₹${amount.toInt()}"
        }
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
            .setTitle(if (isEditing) "सदस्य एडिट करें" else "नया सदस्य जोड़ें")
            .setView(dialogBinding.root)
            .setPositiveButton("सेव करें") { _, _ ->
                val name = dialogBinding.nameEditText.text.toString().trim()
                val amountStr = dialogBinding.amountEditText.text.toString().trim()
                if (name.isEmpty() || amountStr.isEmpty()) {
                    return@setPositiveButton
                }
                val amount = amountStr.toDoubleOrNull()
                if (amount == null) {
                    return@setPositiveButton
                }
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
            .setNegativeButton("रद्द करें", null)
            .show()
    }

    private fun showCustomizeFeeDialog() {
        val dialogBinding = DialogCustomizeFeeBinding.inflate(layoutInflater)
        val months = listOf("जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.monthSpinner.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("मासिक फीस बदलें")
            .setView(dialogBinding.root)
            .setPositiveButton("सभी पर लागू करें") { _, _ ->
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
                        Toast.makeText(this, "${months[monthIndex-1]} की फीस सभी के लिए अपडेट हो गई", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("रद्द करें", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_invite_code)?.isVisible = false
        menu.findItem(R.id.action_view_grid)?.isVisible = false

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterAndDisplayMembers(newText)
                return true
            }
        })
        return true
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

    override fun onDeleteClick(member: Member) {
        AlertDialog.Builder(this)
            .setTitle("सदस्य हटाएं")
            .setMessage("क्या आप वाकई '${member.name}' को हटाना चाहते हैं?")
            .setPositiveButton("हाँ, हटाएं") { _, _ ->
                member.id?.let { db.collection("chanda_data/shared/members").document(it).delete() }
            }
            .setNegativeButton("नहीं", null)
            .show()
    }

    override fun onEditClick(member: Member) {
        showAddOrEditMemberDialog(member)
    }

    override fun onItemClick(member: Member) {
        startActivity(Intent(this, MemberDetailActivity::class.java).apply {
            putExtra("MEMBER_ID", member.id)
            putExtra("SELECTED_YEAR", selectedYear)
        })
    }
}