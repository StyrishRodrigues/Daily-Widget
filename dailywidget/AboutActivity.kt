package com.example.dailywidget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val backBtn = findViewById(R.id.btn_back_about) as ImageView
        backBtn.setOnClickListener {
            finish()
        }

        val btn10 = findViewById(R.id.btn_pay_10) as Button
        btn10.setOnClickListener {
            launchUPIApp("10.00")
        }

        val btn100 = findViewById(R.id.btn_pay_100) as Button
        btn100.setOnClickListener {
            launchUPIApp("100.00")
        }

        val btnCustom = findViewById(R.id.btn_pay_custom) as Button
        btnCustom.setOnClickListener {
            launchUPIApp(null)
        }
    }

    private fun launchUPIApp(amount: String?) {
        val upiId = "styrish27@oksbi"
        val payeeName = "Styrish Loy Rodrigues"
        val transactionNote = "Thanks styrish for your app!"

        // BUG BYPASS: Using + instead of string templates so the clipboard doesn't mangle it
        var uriString = "upi://pay?pa=" + upiId + "&pn=" + Uri.encode(payeeName) + "&tn=" + Uri.encode(transactionNote) + "&cu=INR"

        if (amount != null) {
            uriString = uriString + "&am=" + amount
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
        val chooser = Intent.createChooser(intent, "Pay with...")

        try {
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "No UPI app found on this device!", Toast.LENGTH_SHORT).show()
        }
    }
}