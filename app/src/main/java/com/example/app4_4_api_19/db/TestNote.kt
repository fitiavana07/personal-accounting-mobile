package com.example.app4_4_api_19.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class TestNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String
)
