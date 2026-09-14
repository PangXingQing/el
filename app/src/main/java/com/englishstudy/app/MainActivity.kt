package com.englishstudy.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.englishstudy.app.reader.ReaderActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_open_reader).setOnClickListener {
            startActivity(Intent(this, ReaderActivity::class.java))
        }
    }
}