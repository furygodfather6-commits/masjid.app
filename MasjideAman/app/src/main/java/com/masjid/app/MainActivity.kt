package com.masjid.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.masjid.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "मस्जिद-ए-अमन डैशबोर्ड"

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.cardMonthlyChanda.setOnClickListener {
            startActivity(Intent(this, MonthlyChandaActivity::class.java))
        }

        binding.cardAnyaChanda.setOnClickListener {
            startActivity(Intent(this, AnyaChandaActivity::class.java))
        }
    }

    // Menu ko update kiya gaya hai
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu?.findItem(R.id.action_search)?.isVisible = false
        menu?.findItem(R.id.action_view_grid)?.isVisible = false
        menu?.findItem(R.id.action_export_csv)?.isVisible = false
        menu?.findItem(R.id.action_export_pdf)?.isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                auth.signOut()
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
                true
            }
            R.id.action_invite_code -> {
                showInviteCodeDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showInviteCodeDialog() {
        val inviteCodeRef = db.collection("app_config").document("invite_code")
        inviteCodeRef.get().addOnSuccessListener { document ->
            val currentCode = document.getString("code") ?: ""

            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_invite_code, null)
            val editText = dialogView.findViewById<EditText>(R.id.editTextInviteCode)
            editText.setText(currentCode)

            AlertDialog.Builder(this)
                .setTitle("Invite Code")
                .setView(dialogView)
                .setPositiveButton("सेव करें") { _, _ ->
                    val newCode = editText.text.toString().trim()
                    if (newCode.isNotEmpty()) {
                        inviteCodeRef.set(mapOf("code" to newCode))
                            .addOnSuccessListener {
                                Toast.makeText(this, "Invite Code अपडेट हो गया", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .setNegativeButton("रद्द करें", null)
                .show()
        }
    }
}

