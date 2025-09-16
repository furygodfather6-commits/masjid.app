package com.masjid.app

import android.content.Intent // <-- YEH LINE ADD KI GAYI HAI
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.masjid.app.adapters.YearlyGridAdapter
import com.masjid.app.databinding.ActivityYearlyGridBinding
import com.masjid.app.databinding.DialogEditPaymentBinding
import com.masjid.app.models.Member
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class YearlyGridActivity : AppCompatActivity(), YearlyGridAdapter.OnCellClickListener {

    private lateinit var binding: ActivityYearlyGridBinding
    private lateinit var db: FirebaseFirestore
    private val memberList = mutableListOf<Member>()
    private var selectedYear: Int = 0

    private lateinit var fixedAdapter: YearlyGridAdapter
    private lateinit var scrollableAdapter: YearlyGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYearlyGridBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedYear = intent.getIntExtra("SELECTED_YEAR", 0)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "वार्षिक ग्रिड - $selectedYear"
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        db = FirebaseFirestore.getInstance()
        setupRecyclerViews()
        fetchMembers()
    }

    private fun setupRecyclerViews() {
        fixedAdapter = YearlyGridAdapter(memberList, selectedYear, this, YearlyGridAdapter.VIEW_TYPE_FIXED)
        scrollableAdapter = YearlyGridAdapter(memberList, selectedYear, this, YearlyGridAdapter.VIEW_TYPE_SCROLLABLE)

        binding.recyclerViewFixed.apply {
            layoutManager = LinearLayoutManager(this@YearlyGridActivity)
            adapter = fixedAdapter
        }
        binding.recyclerViewScrollable.apply {
            layoutManager = LinearLayoutManager(this@YearlyGridActivity)
            adapter = scrollableAdapter
        }

        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val otherRecyclerView = if (recyclerView == binding.recyclerViewFixed) {
                    binding.recyclerViewScrollable
                } else {
                    binding.recyclerViewFixed
                }
                otherRecyclerView.scrollBy(dx, dy)
            }
        }

        binding.recyclerViewFixed.addOnScrollListener(scrollListener)
        binding.recyclerViewScrollable.addOnScrollListener(scrollListener)
    }


    private fun fetchMembers() {
        db.collection("chanda_data/shared/members").orderBy("name")
            .addSnapshotListener { snapshots, e ->
                if (e != null) { return@addSnapshotListener }
                memberList.clear()
                snapshots?.forEach { doc -> memberList.add(doc.toObject(Member::class.java).copy(id = doc.id)) }

                fixedAdapter.notifyDataSetChanged()
                scrollableAdapter.notifyDataSetChanged()
            }
    }

    override fun onCellClick(member: Member, monthIndex: Int) {
        val monthKey = "$selectedYear-${String.format("%02d", monthIndex + 1)}"
        val paymentData = member.payments?.get(monthKey) as? Map<*, *>
        val currentAmount = (paymentData?.get("amount") as? Number)?.toDouble() ?: 0.0
        val currentDate = paymentData?.get("date") as? String ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val dialogBinding = DialogEditPaymentBinding.inflate(layoutInflater)
        dialogBinding.amountEditText.setText(if (currentAmount > 0) currentAmount.toString() else "")
        dialogBinding.dateEditText.setText(currentDate)

        AlertDialog.Builder(this)
            .setTitle("${member.name} - ${monthKey} का भुगतान")
            .setView(dialogBinding.root)
            .setPositiveButton("सेव करें") { _, _ ->
                val amount = dialogBinding.amountEditText.text.toString().toDoubleOrNull() ?: 0.0
                val date = dialogBinding.dateEditText.text.toString()
                val newPaymentData = hashMapOf("amount" to amount, "date" to date)
                db.collection("chanda_data/shared/members").document(member.id!!)
                    .update("payments.$monthKey", newPaymentData)
            }
            .setNegativeButton("रद्द करें", null)
            .setNeutralButton("भुगतान हटाएं") { _, _ ->
                db.collection("chanda_data/shared/members").document(member.id!!)
                    .update("payments.$monthKey", FieldValue.delete())
            }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.yearly_grid_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_csv -> {
                exportGridToCSV()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportGridToCSV() {
        val csvData = StringBuilder()
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        csvData.append("Name,${months.joinToString(",")}\n")

        memberList.forEach { member ->
            val monthlyPayments = months.mapIndexed { index, _ ->
                val monthKey = "$selectedYear-${String.format("%02d", index + 1)}"
                val paymentData = member.payments?.get(monthKey) as? Map<*, *>
                ((paymentData?.get("amount") as? Number)?.toDouble() ?: 0.0).toString()
            }.joinToString(",")
            csvData.append("\"${member.name}\",$monthlyPayments\n")
        }

        try {
            val file = File(cacheDir, "yearly_grid_${selectedYear}.csv")
            file.writeText(csvData.toString())
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/csv")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(Intent.createChooser(intent, "Open CSV with"))
        } catch (e: Exception) {
            Toast.makeText(this, "CSV बनाने में त्रुटि हुई", Toast.LENGTH_LONG).show()
        }
    }
}