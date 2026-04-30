package com.yourname.messenger.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
    private var isUpdating = false

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // ДЛЯ ПЛАВНОГО ОБНОВЛЕНИЯ
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private val UPDATE_DELAY = 200L

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

        setUserStatus("online")
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
                setUserStatus("offline")
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                Toast.makeText(this, "Вы вышли", Toast.LENGTH_SHORT).show()
            }
            R.id.action_theme -> {
                val isDark = !loadThemePreference()
                if (isDark) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    menu?.findItem(R.id.action_theme)?.title = "Светлая тема"
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    menu?.findItem(R.id.action_theme)?.title = "Тёмная тема"
                }
                saveThemePreference(isDark)
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

    private fun setUserStatus(status: String) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId)
            .update("status", status)
            .addOnFailureListener {
                firestore.collection("users").document(userId)
                    .set(hashMapOf("id" to userId, "status" to status))
            }
    }

    private fun loadUsers() {
        firestore.collection("users")
            .whereNotEqualTo("id", currentUserId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || isUpdating) return@addSnapshotListener

                val newUsersList = mutableListOf<User>()
                snapshot.documents.forEach { document ->
                    val user = document.toObject(User::class.java)
                    user?.let { newUsersList.add(it) }
                }

                // ПРОВЕРКА: если списки одинаковые — НЕ ОБНОВЛЯЕМ
                if (areListsEqual(allUsersList, newUsersList)) {
                    return@addSnapshotListener
                }

                // Откладываем обновление
                updateRunnable?.let { handler.removeCallbacks(it) }
                updateRunnable = Runnable {
                    isUpdating = true
                    allUsersList.clear()
                    allUsersList.addAll(newUsersList)
                    filteredUsersList.clear()
                    filteredUsersList.addAll(allUsersList)
                    adapter.updateUsers(filteredUsersList)
                    isUpdating = false
                }
                handler.postDelayed(updateRunnable!!, UPDATE_DELAY)
            }
    }

    // Функция сравнения списков (чтобы не обновлять без изменений)
    private fun areListsEqual(oldList: List<User>, newList: List<User>): Boolean {
        if (oldList.size != newList.size) return false
        for (i in oldList.indices) {
            if (oldList[i].id != newList[i].id) return false
            if (oldList[i].status != newList[i].status) return false
            if (oldList[i].name != newList[i].name) return false
        }
        return true
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