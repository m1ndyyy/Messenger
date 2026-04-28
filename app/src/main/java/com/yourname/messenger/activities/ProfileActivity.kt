package com.yourname.messenger.activities

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.yourname.messenger.R

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var tvEmail: TextView
    private lateinit var etName: EditText
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        tvEmail = findViewById(R.id.tvEmail)
        etName = findViewById(R.id.etName)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)

        // Кнопка назад
        btnBack.setOnClickListener {
            finish()
        }

        loadUserData()

        btnSave.setOnClickListener {
            saveUserName()
        }
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        val userEmail = auth.currentUser?.email ?: ""

        tvEmail.text = userEmail

        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val name = document.getString("name") ?: userEmail.split("@")[0]
                etName.setText(name)
            }
    }

    private fun saveUserName() {
        val newName = etName.text.toString().trim()
        if (newName.isEmpty()) {
            Toast.makeText(this, "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users").document(userId)
            .update("name", newName)
            .addOnSuccessListener {
                Toast.makeText(this, "Имя изменено!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}