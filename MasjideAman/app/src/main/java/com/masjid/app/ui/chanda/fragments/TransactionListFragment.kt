package com.masjid.app.ui.chanda.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.masjid.app.adapters.TransactionAdapter
import com.masjid.app.databinding.FragmentTransactionListBinding
import com.masjid.app.models.Transaction

class TransactionListFragment : Fragment() {

    private var _binding: FragmentTransactionListBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: FirebaseFirestore
    private lateinit var transactionAdapter: TransactionAdapter
    private val fullTransactionList = mutableListOf<Transaction>()

    private var collectionName: String? = null
    private var transactionType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fragment banate waqt bheje gaye arguments yahan receive karein
        arguments?.let {
            collectionName = it.getString("collectionName")
            transactionType = it.getString("type")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = FirebaseFirestore.getInstance()
        setupRecyclerView()
        fetchTransactions()
    }

    private fun setupRecyclerView() {
        // Adapter ko transaction type batayein (income ya expense)
        transactionAdapter = TransactionAdapter(fullTransactionList, transactionType ?: "income")
        binding.recyclerViewTransactions.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = transactionAdapter
        }
    }

    private fun fetchTransactions() {
        if (collectionName == null) return

        // Sahi collection se data layein aur date ke hisaab se sort karein
        db.collection("chanda_data/shared/${collectionName}")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) { return@addSnapshotListener }

                fullTransactionList.clear()
                snapshots?.forEach { doc ->
                    val transaction = doc.toObject(Transaction::class.java)
                    fullTransactionList.add(transaction)
                }
                transactionAdapter.updateData(fullTransactionList)
            }
    }

    // Memory leaks se bachne ke liye
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Fragment banane ka standard aur sahi tareeka
    companion object {
        fun newInstance(collectionName: String, type: String) =
            TransactionListFragment().apply {
                arguments = Bundle().apply {
                    putString("collectionName", collectionName)
                    putString("type", type)
                }
            }
    }
}