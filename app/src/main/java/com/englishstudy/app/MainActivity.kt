package com.englishstudy.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.englishstudy.app.databinding.ActivityMainBinding
import com.englishstudy.app.reader.ReaderActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySystemBarInsets(binding.root)

        binding.btnOpenReader.setOnClickListener {
            startActivity(Intent(this, ReaderActivity::class.java))
        }
    }

    /**
     * 把状态栏/导航栏/刘海区域的高度作为根布局的内边距，避免系统栏压住界面。
     * targetSdk 35 起 Android 15 强制边到边显示，必须自行处理。
     */
    private fun applySystemBarInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
    }
}
