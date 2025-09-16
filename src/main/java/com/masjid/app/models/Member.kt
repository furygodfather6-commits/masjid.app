package com.masjid.app.models

data class Member(
    val id: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val monthlyAmount: Double? = 0.0,
    val payments: Map<String, Any>? = null,
    // ERROR FIX: Yeh naya field yahan add kiya gaya hai
    val customFees: Map<String, Any>? = null
)

