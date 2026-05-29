package com.example.app4_4_api_19

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.app4_4_api_19.db.AppDatabase
import com.example.app4_4_api_19.db.TestNote

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val statusView = TextView(this).apply {
            text = "Checking Room…"
            textSize = 20f
        }
        layout.addView(statusView)
        setContentView(layout)

        Thread {
            val db = AppDatabase.getInstance(this)
            val dao = db.testNoteDao()

            dao.insert(TestNote(content = "Room works!"))
            val notes = dao.getAll()
            val result = notes.firstOrNull()?.content ?: "No data found"

            runOnUiThread { statusView.text = result }
        }.start()
    }
}