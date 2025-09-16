package com.masjid.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.masjid.app.R
import com.masjid.app.databinding.ItemTransactionBinding
import com.masjid.app.models.Transaction
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionAdapter(
    private var transactions: List<Transaction>,
    private val type: String // "income" ya "expense"
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    // Search ke liye list update karne ka function
    fun updateData(newTransactions: List<Transaction>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }

    inner class TransactionViewHolder(private val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(transaction: Transaction) {
            binding.textViewName.text = transaction.name
            binding.textViewDescription.text = transaction.description

            val formattedDate = try {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(transaction.date ?: "")
                SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date!!)
            } catch (e: Exception) {
                transaction.date ?: ""
            }
            binding.textViewDate.text = formattedDate

            binding.textViewAmount.text = "₹${transaction.amount.toInt()}"

            val context = itemView.context
            if (type == "expense") {
                binding.textViewAmount.setTextColor(ContextCompat.getColor(context, R.color.red_pending))
            } else {
                binding.textViewAmount.setTextColor(ContextCompat.getColor(context, R.color.green_paid))
            }
        }
    }
}