package com.masjid.app.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Transaction(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    @ServerTimestamp
    val createdAt: Date? = null
)

