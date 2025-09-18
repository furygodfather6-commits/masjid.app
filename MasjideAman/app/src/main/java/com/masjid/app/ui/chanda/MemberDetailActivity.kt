package com.masjid.app.ui.chanda

import android.R
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.masjid.app.databinding.ActivityMemberDetailBinding
import com.masjid.app.databinding.DialogEditPaymentBinding
import com.masjid.app.models.Member
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MemberDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemberDetailBinding
    private lateinit var db: FirebaseFirestore
    private var memberId: String? = null
    private var currentMember: Member? = null
    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemberDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        db = FirebaseFirestore.getInstance()
        memberId = intent.getStringExtra("MEMBER_ID")
        selectedYear = intent.getIntExtra("SELECTED_YEAR", Calendar.getInstance().get(Calendar.YEAR))

        if (memberId == null) {
            Toast.makeText(this, "सदस्य नहीं मिला", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        fetchMemberDetails()
    }

    private fun fetchMemberDetails() {
        db.collection("chanda_data/shared/members").document(memberId!!)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) {
                    Toast.makeText(this, "विवरण लाने में विफल", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                currentMember = snapshot.toObject(Member::class.java)?.copy(id = snapshot.id)
                currentMember?.let { displayMemberDetails(it) }
            }
    }

    private fun displayMemberDetails(member: Member) {
        binding.memberName.text = member.name
        binding.memberPhone.text = member.phone ?: "N/A"
        binding.memberMonthlyAmount.text = "मासिक चंदा: ₹${member.monthlyAmount?.toInt()}"
        supportActionBar?.title = "${member.name} - $selectedYear"
        populateMonthsTable(member)
    }

    private fun populateMonthsTable(member: Member) {
        binding.monthsTable.removeAllViews() // Purani rows ko saaf karein
        val months = listOf("जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")
        val monthlyAmount = member.monthlyAmount ?: 0.0

        months.forEachIndexed { index, monthName ->
            val monthKey = "$selectedYear-${String.format("%02d", index + 1)}"
            val paymentData = member.payments?.get(monthKey) as? Map<*, *>
            val paidAmount = (paymentData?.get("amount") as? Number)?.toDouble() ?: 0.0
            val pendingAmount = monthlyAmount - paidAmount

            val tableRow = TableRow(this).apply {
                isClickable = true
                setBackgroundResource(R.attr.selectableItemBackground)
                setOnClickListener { showEditPaymentDialog(member, monthKey, paidAmount) }
            }

            val monthTextView = createTableCell(monthName, isHeader = true)
            val statusTextView = createTableCell("")

            when {
                paidAmount >= monthlyAmount && monthlyAmount > 0 -> {
                    statusTextView.text = "✔ जमा (₹${paidAmount.toInt()})"
                    statusTextView.setTextColor(ContextCompat.getColor(this, com.masjid.app.R.color.green_paid))
                }
                paidAmount > 0 -> {
                    statusTextView.text = "₹${pendingAmount.toInt()} बकाया"
                    statusTextView.setTextColor(ContextCompat.getColor(this, com.masjid.app.R.color.orange_partial))
                }
                else -> {
                    statusTextView.text = "✘ बाकी है"
                    statusTextView.setTextColor(ContextCompat.getColor(this, com.masjid.app.R.color.red_pending))
                }
            }

            tableRow.addView(monthTextView)
            tableRow.addView(statusTextView)
            binding.monthsTable.addView(tableRow)
        }
    }

    private fun showEditPaymentDialog(member: Member, monthKey: String, currentAmount: Double) {
        val dialogBinding = DialogEditPaymentBinding.inflate(layoutInflater)

        val paymentData = member.payments?.get(monthKey) as? Map<*, *>
        val paymentDate = paymentData?.get("date") as? String

        dialogBinding.amountEditText.setText(if (currentAmount > 0) currentAmount.toString() else "")
        dialogBinding.dateEditText.setText(paymentDate ?: SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(Date()))

        AlertDialog.Builder(this)
            .setTitle("${monthKey} का भुगतान एडिट करें")
            .setView(dialogBinding.root)
            .setPositiveButton("सेव करें") { _, _ ->
                val amount = dialogBinding.amountEditText.text.toString().toDoubleOrNull() ?: 0.0
                val date = dialogBinding.dateEditText.text.toString()
                val newPaymentData = hashMapOf("amount" to amount, "date" to date)

                db.collection("chanda_data/shared/members").document(member.id!!)
                    .update("payments.$monthKey", newPaymentData)
                    .addOnSuccessListener { Toast.makeText(this, "भुगतान अपडेट हो गया", Toast.LENGTH_SHORT).show() }
                    .addOnFailureListener { Toast.makeText(this, "अपडेट करने में त्रुटि", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("रद्द करें", null)
            .setNeutralButton("भुगतान हटाएं") { _, _ ->
                db.collection("chanda_data/shared/members").document(member.id!!)
                    .update("payments.$monthKey", FieldValue.delete())
                    .addOnSuccessListener { Toast.makeText(this, "भुगतान हटा दिया गया", Toast.LENGTH_SHORT).show() }
            }
            .show()
    }

    private fun createTableCell(text: String, isHeader: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            setPadding(16, 8, 16, 8)
            gravity = if (isHeader) Gravity.START else Gravity.END
            if (isHeader) {
                setTypeface(null, Typeface.BOLD)
            }
        }
    }
}