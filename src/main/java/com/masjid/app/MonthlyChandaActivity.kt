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
import java.util.Calendar

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
            val chip = group.findViewById<Chip>(checkedIds.firstOrNull() ?: R.id.chip_all)
            currentFilter = when (chip.id) {
                R.id.chip_paid -> "Paid"
                R.id.chip_pending -> "Pending"
                R.id.chip_partial -> "Partial"
                else -> "All"
            }
            val searchView = binding.toolbar.menu?.findItem(R.id.action_search)?.actionView as? SearchView
            filterAndDisplayMembers(searchView)
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
                filterAndDisplayMembers(searchView)
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
                filterAndDisplayMembers(searchView)
            }
    }

    private fun filterAndDisplayMembers(searchView: SearchView?) {
        val query = searchView?.query?.toString()?.trim() ?: ""

        var tempFilteredList = if (query.isEmpty()) {
            memberList
        } else {
            memberList.filter { it.name?.contains(query, ignoreCase = true) == true }
        }

        if (currentFilter != "All") {
            val calendar = Calendar.getInstance()

            tempFilteredList = tempFilteredList.filter { member ->
                val monthKey = "$selectedYear-${String.format("%02d", calendar.get(Calendar.MONTH) + 1)}"
                val customFee = (member.customFees?.get(monthKey) as? Number)?.toDouble()
                val monthlyAmount = customFee ?: member.monthlyAmount ?: 0.0

                val paymentData = member.payments?.get(monthKey) as? Map<*, *>
                val paidAmount = (paymentData?.get("amount") as? Number)?.toDouble() ?: 0.0

                if (calendar.get(Calendar.YEAR) == selectedYear) {
                    when (currentFilter) {
                        "Paid" -> paidAmount >= monthlyAmount && monthlyAmount > 0
                        "Pending" -> paidAmount == 0.0 && monthlyAmount > 0
                        "Partial" -> paidAmount > 0 && paidAmount < monthlyAmount
                        else -> true
                    }
                } else {
                    true
                }
            }
        }

        filteredList.clear()
        filteredList.addAll(tempFilteredList)
        chandaAdapter.notifyDataSetChanged()
        updateDashboardStats()
    }

    private fun updateDashboardStats() {
        val listForStats = filteredList
        val totalMembers = listForStats.size
        var yearlyPotential = 0.0
        var yearlyCollected = 0.0

        listForStats.forEach { member ->
            for (i in 1..12) {
                val monthKey = "$selectedYear-${String.format("%02d", i)}"
                val customFee = (member.customFees?.get(monthKey) as? Number)?.toDouble()
                yearlyPotential += customFee ?: member.monthlyAmount ?: 0.0
            }
            member.payments?.forEach { (key, value) ->
                if (key.startsWith(selectedYear.toString())) {
                    val paymentData = value as? Map<*, *>
                    yearlyCollected += (paymentData?.get("amount") as? Number)?.toDouble() ?: 0.0
                }
            }
        }

        val yearlyPending = yearlyPotential - yearlyCollected
        binding.statTotalMembers.text = totalMembers.toString()
        binding.statYearlyPotential.text = "₹${yearlyPotential.toInt()}"
        binding.statYearlyCollected.text = "₹${yearlyCollected.toInt()}"
        binding.statYearlyPending.text = "₹${yearlyPending.toInt()}"
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
        // Dashboard se alag menu items
        menu.findItem(R.id.action_invite_code)?.isVisible = false
        menu.findItem(R.id.action_view_grid)?.isVisible = false // Button se handle ho raha hai

        val searchItem = menu.findItem(R.id.action_search)
        (searchItem.actionView as SearchView).setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterAndDisplayMembers(searchItem.actionView as SearchView)
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

