package com.masjid.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.masjid.app.R
import com.masjid.app.databinding.ItemYearlyGridBinding
import com.masjid.app.models.Member

class YearlyGridAdapter(
    private val memberList: List<Member>,
    private val year: Int,
    private val listener: OnCellClickListener,
    private val viewType: Int
) : RecyclerView.Adapter<YearlyGridAdapter.GridViewHolder>() {

    companion object {
        const val VIEW_TYPE_FIXED = 1
        const val VIEW_TYPE_SCROLLABLE = 2
    }

    interface OnCellClickListener {
        fun onCellClick(member: Member, monthIndex: Int)
    }

    override fun getItemViewType(position: Int): Int {
        // Yeh batata hai ki kaun sa view type (fixed ya scrollable) use karna hai
        return viewType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
        val binding = ItemYearlyGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GridViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        holder.bind(memberList[position])
    }

    override fun getItemCount(): Int = memberList.size

    inner class GridViewHolder(private val binding: ItemYearlyGridBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(member: Member) {
            // Adapter ke viewType ke hisab se data show/hide karein
            if (this@YearlyGridAdapter.viewType == VIEW_TYPE_FIXED) {
                binding.memberName.visibility = View.VISIBLE
                binding.monthsContainer.visibility = View.GONE
            } else { // VIEW_TYPE_SCROLLABLE
                binding.memberName.visibility = View.GONE
                binding.monthsContainer.visibility = View.VISIBLE
            }

            binding.memberName.text = member.name

            val monthTextViews = listOf(
                binding.month1, binding.month2, binding.month3, binding.month4,
                binding.month5, binding.month6, binding.month7, binding.month8,
                binding.month9, binding.month10, binding.month11, binding.month12
            )

            monthTextViews.forEachIndexed { index, textView ->
                textView.setOnClickListener { listener.onCellClick(member, index) }

                val monthKey = "$year-${String.format("%02d", index + 1)}"
                val customFee = (member.customFees?.get(monthKey) as? Number)?.toDouble()
                val monthlyAmount = customFee ?: member.monthlyAmount ?: 0.0

                val paymentData = member.payments?.get(monthKey) as? Map<*, *>
                val paidAmount = (paymentData?.get("amount") as? Number)?.toDouble() ?: 0.0
                val context = itemView.context

                when {
                    paidAmount >= monthlyAmount && monthlyAmount > 0 -> {
                        textView.text = "✔"
                        textView.setBackgroundColor(ContextCompat.getColor(context, R.color.light_green_bg))
                    }
                    paidAmount > 0 -> {
                        textView.text = "₹${(monthlyAmount - paidAmount).toInt()}"
                        textView.setBackgroundColor(ContextCompat.getColor(context, R.color.light_orange_bg))
                    }
                    else -> {
                        textView.text = "✘"
                        textView.setBackgroundColor(ContextCompat.getColor(context, R.color.light_red_bg))
                    }
                }
            }
        }
    }
}