package com.simple.iptv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etServer = findViewById<EditText>(R.id.etServer)
        val etUser   = findViewById<EditText>(R.id.etUser)
        val etPass   = findViewById<EditText>(R.id.etPass)
        val btnGo    = findViewById<Button>(R.id.btnGo)
        val tvMsg    = findViewById<TextView>(R.id.tvMsg)

        // Saved prefs
        val p = getSharedPreferences("iptv", MODE_PRIVATE)
        etServer.setText(p.getString("s",""))
        etUser.setText(p.getString("u",""))
        etPass.setText(p.getString("p",""))

        btnGo.setOnClickListener {
            val s = etServer.text.toString().trim().trimEnd('/')
            val u = etUser.text.toString().trim()
            val pw = etPass.text.toString().trim()

            if (s.isEmpty() || u.isEmpty() || pw.isEmpty()) {
                tvMsg.text = "Bitte alle Felder ausfüllen!"
                return@setOnClickListener
            }

            tvMsg.text = "Verbinde..."
            btnGo.isEnabled = false

            val url = "$s/player_api.php?username=$u&password=$pw"

            try {
                OkHttpClient().newCall(Request.Builder().url(url).build())
                    .enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            runOnUiThread {
                                tvMsg.text = "Fehler: ${e.message}"
                                btnGo.isEnabled = true
                            }
                        }
                        override fun onResponse(call: Call, response: Response) {
                            val body = response.body?.string() ?: ""
                            runOnUiThread {
                                try {
                                    val json = JSONObject(body)
                                    if (json.has("user_info")) {
                                        p.edit()
                                            .putString("s", s)
                                            .putString("u", u)
                                            .putString("p", pw)
                                            .apply()
                                        loadChannels(s, u, pw)
                                    } else {
                                        tvMsg.text = "Falsche Anmeldedaten!"
                                        btnGo.isEnabled = true
                                    }
                                } catch (e: Exception) {
                                    tvMsg.text = "Fehler: ${e.message}"
                                    btnGo.isEnabled = true
                                }
                            }
                        }
                    })
            } catch (e: Exception) {
                tvMsg.text = "Fehler: ${e.message}"
                btnGo.isEnabled = true
            }
        }
    }

    private fun loadChannels(s: String, u: String, pw: String) {
        val tvMsg = findViewById<TextView>(R.id.tvMsg)
        tvMsg.text = "Lade Kanäle..."

        val url = "$s/player_api.php?username=$u&password=$pw&action=get_live_streams"

        try {
            OkHttpClient().newCall(Request.Builder().url(url).build())
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        runOnUiThread { tvMsg.text = "Fehler: ${e.message}" }
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string() ?: "[]"
                        runOnUiThread {
                            try {
                                val arr = JSONArray(body)
                                val names = ArrayList<String>()
                                val urls   = ArrayList<String>()

                                for (i in 0 until arr.length()) {
                                    val obj = arr.getJSONObject(i)
                                    names.add(obj.optString("name","Kanal $i"))
                                    urls.add("$s/live/$u/$pw/${obj.optInt("stream_id")}.m3u8")
                                }

                                tvMsg.text = "${names.size} Kanäle geladen ✅"

                                // Show channel list dialog
                                val dialog = android.app.AlertDialog.Builder(this@MainActivity)
                                    .setTitle("Kanäle (${names.size})")
                                    .setItems(names.toTypedArray()) { _, pos ->
                                        // Open stream in external player (VLC etc.)
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW)
                                            intent.setDataAndType(Uri.parse(urls[pos]), "video/*")
                                            startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(this@MainActivity,
                                                "Kein Video-Player gefunden! Bitte VLC installieren.",
                                                Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    .setNegativeButton("Schließen", null)
                                    .create()
                                dialog.show()

                            } catch (e: Exception) {
                                tvMsg.text = "Fehler: ${e.message}"
                            }
                        }
                    }
                })
        } catch (e: Exception) {
            tvMsg.text = "Fehler: ${e.message}"
        }
    }
}
