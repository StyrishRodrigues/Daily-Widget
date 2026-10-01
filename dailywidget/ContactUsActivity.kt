package com.example.dailywidget

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class ContactUsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_us)

        val btnWhatsapp = findViewById<CardView>(R.id.card_whatsapp)
        val btnInsta = findViewById<CardView>(R.id.card_insta)
        val btnEmail = findViewById<CardView>(R.id.card_email)

        btnWhatsapp.setOnClickListener {
            // Added 91 country code for routing
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=919945188964")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }

        btnInsta.setOnClickListener {
            val uri = Uri.parse("http://instagram.com/_u/styrishrodrigues")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.instagram.android")
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // If they don't have the Instagram app installed, open it in Chrome
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/styrishrodrigues")))
            }
        }

        btnEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:styrish27@gmail.com")
            }
            startActivity(intent)
        }
    }
}