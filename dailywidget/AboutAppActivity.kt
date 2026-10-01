package com.example.dailywidget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutAppActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_app)

        // Close the page when the back arrow is tapped
        findViewById<ImageView>(R.id.btn_back_about_app).setOnClickListener {
            finish()
        }

        // Make the Support Text clickable and route to your personal GitHub
        findViewById<TextView>(R.id.tv_support_link).setOnClickListener {
            // Replace "Styrish" with your exact GitHub username if it is different
            val githubUrl = "https://github.com/StyrishRodrigues"
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
            startActivity(browserIntent)
        }
    }
}