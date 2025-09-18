package com.masjid.app.ui.chanda

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.masjid.app.adapters.ViewPagerAdapter
import com.masjid.app.databinding.ActivityAnyaChandaBinding
import com.masjid.app.ui.chanda.fragments.TransactionListFragment

class AnyaChandaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnyaChandaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnyaChandaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        setupViewPager()
    }

    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this)

        // Har fragment ko newInstance ke zariye banayein aur sahi collection/type batayein
        adapter.addFragment(TransactionListFragment.Companion.newInstance("juma_collections", "income"), "जुमा")
        adapter.addFragment(TransactionListFragment.Companion.newInstance("festival_funds", "income"), "त्योहार")
        adapter.addFragment(TransactionListFragment.Companion.newInstance("repair_funds", "income"), "मरम्मत")
        adapter.addFragment(TransactionListFragment.Companion.newInstance("general_income", "income"), "अन्य आमदनी")
        adapter.addFragment(TransactionListFragment.Companion.newInstance("expenses", "expense"), "खर्च")
        adapter.addFragment(TransactionListFragment.Companion.newInstance("salaries", "expense"), "तनख्वाह")

        binding.viewPager.adapter = adapter
        // ViewPager ko TabLayout ke saath jodein
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = adapter.getPageTitle(position)
        }.attach()
    }
}