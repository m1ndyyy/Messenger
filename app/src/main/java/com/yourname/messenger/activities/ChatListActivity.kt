package com.yourname.messenger.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.yourname.messenger.R
import com.yourname.messenger.adapters.UserAdapter
import com.yourname.messenger.models.User

class ChatListActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var rvUsers: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var adapter: UserAdapter
    private var menu: Menu? = null

    private val allUsersList = mutableListOf<User>()
    private val filteredUsersList = mutableListOf<User>()

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    companion object {
        private const val REQUEST_PERMISSIONS = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val isDark = loadThemePreference()
        if (isDark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_list)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Чаты"

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Запрашиваем разрешения при запуске
        checkAndRequestPermissions()

        rvUsers = findViewById(R.id.rvUsers)
        etSearch = findViewById(R.id.etSearch)

        rvUsers.layoutManager = LinearLayoutManager(this)

        adapter = UserAdapter(filteredUsersList) { user ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("receiverId", user.id)
            intent.putExtra("receiverName", user.name)
            startActivity(intent)
        }
        rvUsers.adapter = adapter

        loadUsers()
        setupSearch()
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_list_menu, menu)
        this.menu = menu
        val isDark = loadThemePreference()
        if (isDark) {
            menu?.findItem(R.id.action_theme)?.title = "Светлая тема"
        } else {
            menu?.findItem(R.id.action_theme)?.title = "Тёмная тема"
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
            }
            R.id.action_logout -> {
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                Toast.makeText(this, "Вы вышли", Toast.LENGTH_SHORT).show()
            }
            R.id.action_theme -> {
                val isDark = !loadThemePreference()
                saveThemePreference(isDark)

                if (isDark) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    item.title = "Светлая тема"
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    item.title = "Тёмная тема"
                }
                    recreate()
            }
        }
        return true
    }

    private fun saveThemePreference(isDark: Boolean) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        prefs.edit().putBoolean("dark_theme", isDark).apply()
    }

    private fun loadThemePreference(): Boolean {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        return prefs.getBoolean("dark_theme", false)
    }

    private fun loadUsers() {
        firestore.collection("users")
            .whereNotEqualTo("id", currentUserId)
            .addSnapshotListener { snapshot, _ ->
                allUsersList.clear()
                snapshot?.documents?.forEach { document ->
                    val user = document.toObject(User::class.java)
                    user?.let { allUsersList.add(it) }
                }
                filteredUsersList.clear()
                filteredUsersList.addAll(allUsersList)
                adapter.updateUsers(filteredUsersList)
            }
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterUsers(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterUsers(query: String) {
        filteredUsersList.clear()
        if (query.isEmpty()) {
            filteredUsersList.addAll(allUsersList)
        } else {
            for (user in allUsersList) {
                if (user.name.lowercase().contains(query.lowercase())) {
                    filteredUsersList.add(user)
                }
            }
        }
        adapter.updateUsers(filteredUsersList)
    }
}