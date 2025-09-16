package com.masjid.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.masjid.app.R
import com.masjid.app.databinding.ItemYearlyGridCompactBinding
import com.masjid.app.models.Member

class YearlyGridCompactAdapter(
    private var memberList: List<Member>,
    private val year: Int,
    private val listener: OnMonthClickListener
) : RecyclerView.Adapter<YearlyGridCompactAdapter.GridViewHolder>() {

    interface OnMonthClickListener {
        fun onMonthClick(member: Member, monthIndex: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
        val binding = ItemYearlyGridCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GridViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        holder.bind(memberList[position])
    }

    override fun getItemCount(): Int = memberList.size

    fun updateData(newMemberList: List<Member>) {
        memberList = newMemberList
        notifyDataSetChanged()
    }

    inner class GridViewHolder(private val binding: ItemYearlyGridCompactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(member: Member) {
            binding.memberName.text = member.name
            binding.monthsContainer.removeAllViews() // Purane views ko saaf karein

            val months = listOf("जन", "फ़र", "मार्च", "अप्रै", "मई", "जून", "जुला", "अग", "सितं", "अक्टू", "नवं", "दिसं")
            val context = itemView.context

            months.forEachIndexed { index, monthName ->
                val monthKey = "$year-${String.format("%02d", index + 1)}"
                val customFee = (member.customFees?.get(monthKey) as? Number)?.toDouble()
                val monthlyAmount = customFee ?: member.monthlyAmount ?: 0.0
                val paymentData = member.payments?.get(monthKey) as? Map<*, *>
                val paidAmount = (paymentData?.get("amount") as? Number)?.toDouble() ?: 0.0
                val pendingAmount = if (monthlyAmount > paidAmount) monthlyAmount - paidAmount else 0.0

                // Naye detailed layout ko inflate karein
                val cellView = LayoutInflater.from(context).inflate(R.layout.item_month_cell_detailed, binding.monthsContainer, false)

                val cellBackground = cellView.findViewById<LinearLayout>(R.id.cell_background)
                val tvMonthName = cellView.findViewById<TextView>(R.id.month_name)
                val tvPaidAmount = cellView.findViewById<TextView>(R.id.paid_amount)
                val tvPendingAmount = cellView.findViewById<TextView>(R.id.pending_amount)

                tvMonthName.text = monthName
                tvPaidAmount.text = "₹${paidAmount.toInt()}"
                tvPendingAmount.text = "₹${pendingAmount.toInt()}"

                // Status ke hisab se background color set karein
                when {
                    paidAmount >= monthlyAmount && monthlyAmount > 0 -> {
                        cellBackground.setBackgroundColor(ContextCompat.getColor(context, R.color.light_green_bg))
                    }
                    paidAmount > 0 -> {
                        cellBackground.setBackgroundColor(ContextCompat.getColor(context, R.color.light_orange_bg))
                    }
                    else -> {
                        cellBackground.setBackgroundColor(ContextCompat.getColor(context, R.color.light_red_bg))
                    }
                }

                cellView.setOnClickListener {
                    listener.onMonthClick(member, index)
                }

                binding.monthsContainer.addView(cellView)
            }
        }
    }
}