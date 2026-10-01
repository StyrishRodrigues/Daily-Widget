package com.example.dailywidget

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ReviewsActivity : AppCompatActivity() {

    // PASTE YOUR FIREBASE URL HERE (Leave the "/reviews.json" at the end!)
    private val firebaseUrl = "https://dailywidget-865ec-default-rtdb.asia-southeast1.firebasedatabase.app/reviews.json"

    private lateinit var containerReviews: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reviews)

        val etName = findViewById<EditText>(R.id.et_reviewer_name)
        val etReview = findViewById<EditText>(R.id.et_review_text)
        val ratingBar = findViewById<RatingBar>(R.id.rating_bar)
        val btnSubmit = findViewById<Button>(R.id.btn_submit_review)
        containerReviews = findViewById(R.id.container_reviews)

        // Load existing reviews from the cloud
        fetchReviews()

        btnSubmit.setOnClickListener {
            val name = etName.text.toString().trim()
            val text = etReview.text.toString().trim()
            val rating = ratingBar.rating.toInt()

            if (name.isEmpty() || text.isEmpty()) {
                Toast.makeText(this, "Please fill out all fields!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSubmit.text = "Submitting..."
            btnSubmit.isEnabled = false

            // Post to Firebase in a background thread
            Thread {
                try {
                    val url = URL(firebaseUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true

                    val json = JSONObject()
                    json.put("name", name)
                    json.put("text", text)
                    json.put("rating", rating)

                    val outputStream = OutputStreamWriter(conn.outputStream)
                    outputStream.write(json.toString())
                    outputStream.flush()

                    if (conn.responseCode in 200..299) {
                        runOnUiThread {
                            etName.text.clear()
                            etReview.text.clear()
                            ratingBar.rating = 5f
                            btnSubmit.text = "SUBMIT REVIEW"
                            btnSubmit.isEnabled = true
                            Toast.makeText(this, "Review Submitted!", Toast.LENGTH_SHORT).show()
                            fetchReviews() // Reload the list
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        btnSubmit.text = "SUBMIT REVIEW"
                        btnSubmit.isEnabled = true
                        Toast.makeText(this, "Network Error!", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    private fun fetchReviews() {
        containerReviews.removeAllViews()
        val loadingText = TextView(this).apply {
            text = "Loading cloud reviews..."
            setTextColor(Color.parseColor("#8B949E"))
        }
        containerReviews.addView(loadingText)

        Thread {
            try {
                val url = URL(firebaseUrl)
                val conn = url.openConnection() as HttpURLConnection
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    runOnUiThread {
                        containerReviews.removeAllViews()
                        if (response == "null") {
                            loadingText.text = "No reviews yet. Be the first!"
                            containerReviews.addView(loadingText)
                            return@runOnUiThread
                        }

                        val jsonResponse = JSONObject(response)
                        val keys = jsonResponse.keys()

                        while (keys.hasNext()) {
                            val key = keys.next()
                            val reviewObj = jsonResponse.getJSONObject(key)

                            val name = reviewObj.getString("name")
                            val text = reviewObj.getString("text")
                            val rating = reviewObj.getInt("rating")

                            // Build a simple visual card for each review
                            val reviewCard = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(0, 0, 0, 40)
                            }

                            val nameStars = TextView(this).apply {
                                val stars = "⭐".repeat(rating)
                                this.text = "$name  $stars"
                                setTextColor(Color.parseColor("#A5D6A7"))
                                textSize = 16f
                                setTypeface(null, android.graphics.Typeface.BOLD)
                            }

                            val reviewText = TextView(this).apply {
                                this.text = text
                                setTextColor(Color.parseColor("#C9D1D9"))
                                textSize = 14f
                                setPadding(0, 8, 0, 0)
                            }

                            reviewCard.addView(nameStars)
                            reviewCard.addView(reviewText)

                            // Add newer reviews to the top
                            containerReviews.addView(reviewCard, 0)
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loadingText.text = "Could not connect to server."
                }
            }
        }.start()
    }
}