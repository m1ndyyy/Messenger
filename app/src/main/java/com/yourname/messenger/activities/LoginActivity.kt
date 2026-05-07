package com.yourname.messenger.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

    companion object {
        private const val REQUEST_PERMISSIONS = 100
    }

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

        // Запрашиваем разрешения при запуске
        checkAndRequestPermissions()

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

    // Проверка и запрос разрешений (без диалогов)
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 1. Уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Микрофон (для голосовых сообщений)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // 3. Доступ к хранилищу для старых Android
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        // 4. Доступ к медиафайлам для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }

        // Запрашиваем разрешения (без объяснений и диалогов)
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS
            )
        }
    }

    // ❌ Убрали весь диалог - просто ничего не делаем
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Ничего не показываем, просто игнорируем
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

    private fun isValidEmail(email: String): Boolean {
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

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        btnLogin.isEnabled = !show
    }

    private fun saveFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                val userId = auth.currentUser?.uid
                if (userId != null && token != null) {
                    firestore.collection("users").document(userId)
                        .update("fcmToken", token)
                        .addOnFailureListener {
                            firestore.collection("users").document(userId)
                                .set(hashMapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                        }
                }
            }
        }
    }

    private fun goToChatList() {
        saveFcmToken()
        startActivity(Intent(this, ChatListActivity::class.java))
        finish()
    }
}