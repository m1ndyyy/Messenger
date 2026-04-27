package com.yourname.messenger.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.yourname.messenger.R
import com.yourname.messenger.models.User
import java.util.regex.Pattern

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var isLoginMode = true

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvToggleMode: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tilName: android.view.View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvToggleMode = findViewById(R.id.tvToggleMode)
        progressBar = findViewById(R.id.progressBar)
        tilName = findViewById(R.id.tilName)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        if (auth.currentUser != null) {
            goToChatList()
        }

        btnLogin.setOnClickListener {
            if (isLoginMode) loginUser() else registerUser()
        }

        tvToggleMode.setOnClickListener {
            isLoginMode = !isLoginMode
            if (isLoginMode) {
                btnLogin.text = "Войти"
                tilName.visibility = android.view.View.GONE
                tvToggleMode.text = "Нет аккаунта? Зарегистрироваться"
            } else {
                btnLogin.text = "Зарегистрироваться"
                tilName.visibility = android.view.View.VISIBLE
                tvToggleMode.text = "Уже есть аккаунт? Войти"
            }
        }
    }

    private fun loginUser() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                showLoading(false)
                goToChatList()
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Ошибка: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Функция проверки email (только латиница, цифры, . - _ @)
    private fun isValidEmail(email: String): Boolean {
        // Только английские буквы, цифры, и символы . - _ @
        val emailPattern = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return emailPattern.matcher(email).matches()
    }

    private fun registerUser() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return
        }

        // ПРОВЕРКА: только английские буквы в email
        if (!isValidEmail(email)) {
            Toast.makeText(this, "Email должен содержать только латинские буквы и цифры (a-z, 0-9)", Toast.LENGTH_LONG).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Пароль минимум 6 символов", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val userId = result.user?.uid ?: return@addOnSuccessListener
                val user = User(
                    id = userId,
                    name = name,
                    email = email,
                    status = "online"
                )
                firestore.collection("users").document(userId).set(user)
                    .addOnSuccessListener {
                        showLoading(false)
                        goToChatList()
                    }
                    .addOnFailureListener {
                        showLoading(false)
                        Toast.makeText(this, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Ошибка: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        btnLogin.isEnabled = !show
    }

    private fun saveFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "🔥 ТОКЕН ПОСЛЕ ВХОДА: $token")
                val userId = auth.currentUser?.uid
                if (userId != null && token != null) {
                    firestore.collection("users").document(userId)
                        .update("fcmToken", token)
                        .addOnFailureListener {
                            firestore.collection("users").document(userId)
                                .set(hashMapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                        }
                }
            } else {
                Log.e("FCM", "❌ Ошибка: ${task.exception?.message}")
            }
        }
    }

    private fun goToChatList() {
        saveFcmToken()
        checkNotificationPermission()
        startActivity(Intent(this, ChatListActivity::class.java))
        finish()
    }
}