package com.masjid.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.masjid.app.R
import com.masjid.app.databinding.ItemChandaMemberBinding
import com.masjid.app.models.Member
import java.util.Calendar

class ChandaAdapter(
    private val memberList: List<Member>,
    private val listener: OnItemClickListener,
    private var year: Int // Saal ko constructor me shamil karein
) : RecyclerView.Adapter<ChandaAdapter.MemberViewHolder>() {

    interface OnItemClickListener {
        fun onDeleteClick(member: Member)
        fun onEditClick(member: Member)
        fun onItemClick(member: Member)
    }

    // Naya function saal update karne ke liye
    fun updateYear(newYear: Int) {
        year = newYear
        notifyDataSetChanged() // List ko refresh karein
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemChandaMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(memberList[position])
    }

    override fun getItemCount(): Int = memberList.size

    inner class MemberViewHolder(private val binding: ItemChandaMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            // Poori row par click karne se detail screen khulegi
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(memberList[position])
                }
            }
        }

        fun bind(member: Member) {
            binding.textViewName.text = member.name
            binding.textViewPhone.text = member.phone ?: "N/A"

            val calendar = Calendar.getInstance()
            // Vartaman mahine ka key banane ke liye selected year ka istemal karein
            val currentMonthKey = "$year-${String.format("%02d", calendar.get(Calendar.MONTH) + 1)}"

            // customFees check karein, agar nahi hai to monthlyAmount istemal karein
            val customFee = (member.customFees?.get(currentMonthKey) as? Number)?.toDouble()
            val monthlyAmount = customFee ?: member.monthlyAmount ?: 0.0

            val paymentData = member.payments?.get(currentMonthKey) as? Map<*, *>
            val paidAmount = (paymentData?.get("amount") as? Number)?.toDouble() ?: 0.0
            val pendingAmount = monthlyAmount - paidAmount

            // UI me values set karein
            binding.textViewCurrentFee.text = "₹${monthlyAmount.toInt()}"
            binding.textViewPaidAmount.text = "₹${paidAmount.toInt()}"
            binding.textViewPendingAmount.text = "₹${pendingAmount.toInt()}"

            val context = itemView.context
            when {
                paidAmount >= monthlyAmount && monthlyAmount > 0 -> {
                    // Poora jama
                    binding.textViewPaidAmount.setTextColor(ContextCompat.getColor(context, R.color.green_paid))
                    binding.textViewPendingAmount.setTextColor(ContextCompat.getColor(context, R.color.green_paid))
                }
                paidAmount > 0 -> {
                    // Aanshik bhugtan
                    binding.textViewPaidAmount.setTextColor(ContextCompat.getColor(context, R.color.orange_partial))
                    binding.textViewPendingAmount.setTextColor(ContextCompat.getColor(context, R.color.orange_partial))
                }
                else -> {
                    // Poora bakaaya
                    binding.textViewPaidAmount.setTextColor(ContextCompat.getColor(context, R.color.red_pending))
                    binding.textViewPendingAmount.setTextColor(ContextCompat.getColor(context, R.color.red_pending))
                }
            }

            binding.buttonDelete.setOnClickListener { listener.onDeleteClick(member) }
            binding.buttonEdit.setOnClickListener { listener.onEditClick(member) }
        }
    }
}

