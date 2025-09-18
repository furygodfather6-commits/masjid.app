package com.masjid.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.masjid.app.ui.main.HomeActivity
import com.masjid.app.R
import com.masjid.app.databinding.ActivityAuthBinding

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Default me login mode set karein
        setupTabs()
        updateUIForMode(isLoginMode = true)

        binding.authButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.enter_email_and_password), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedTabPosition = binding.tabLayout.selectedTabPosition
            if (selectedTabPosition == 0) { // Login Tab
                loginUser(email, password)
            } else { // Register Tab
                val inviteCode = binding.inviteCodeEditText.text.toString().trim()
                if (inviteCode.isEmpty()) {
                    binding.inviteCodeLayout.error = getString(R.string.secret_code_is_required)
                    return@setOnClickListener
                }
                registerUser(email, password, inviteCode)
            }
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateUIForMode(isLoginMode = tab?.position == 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateUIForMode(isLoginMode: Boolean) {
        if (isLoginMode) {
            binding.inviteCodeLayout.visibility = View.GONE
            binding.authButton.text = getString(R.string.login)
        } else {
            binding.inviteCodeLayout.visibility = View.VISIBLE
            binding.authButton.text = getString(R.string.create_new_account)
        }
        binding.emailEditText.text?.clear()
        binding.passwordEditText.text?.clear()
        binding.inviteCodeEditText.text?.clear()
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
        binding.inviteCodeLayout.error = null
    }

    private fun loginUser(email: String, password: String) {
        binding.progressBar.visibility = View.VISIBLE
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            binding.progressBar.visibility = View.GONE
            if (task.isSuccessful) {
                navigateToMain()
            } else {
                Toast.makeText(this, getString(R.string.login_failed, task.exception?.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun registerUser(email: String, password: String, inviteCode: String) {
        binding.progressBar.visibility = View.VISIBLE
        db.collection("app_config").document("invite_code").get().addOnSuccessListener { document ->
            val correctCode = document.getString("code")
            if (correctCode == inviteCode) {
                auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        db.collection("admins").document(user!!.uid).set(mapOf("ownerEmail" to email))
                            .addOnSuccessListener {
                                binding.progressBar.visibility = View.GONE
                                navigateToMain()
                            }
                            .addOnFailureListener { e ->
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this, getString(R.string.failed_to_create_admin, e.message), Toast.LENGTH_LONG).show()
                            }
                    } else {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, getString(R.string.registration_failed, task.exception?.message), Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                binding.progressBar.visibility = View.GONE
                binding.inviteCodeLayout.error = getString(R.string.incorrect_secret_code)
            }
        }.addOnFailureListener {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, getString(R.string.failed_to_check_secret_code), Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}