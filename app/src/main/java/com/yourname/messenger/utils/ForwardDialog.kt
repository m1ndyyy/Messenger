package com.yourname.messenger.utils

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.yourname.messenger.R
import com.yourname.messenger.models.User

object ForwardDialog {

    fun show(
        context: Context,
        currentUserId: String,
        onUserSelected: (selectedUserId: String, selectedUserName: String) -> Unit
    ) {
        val firestore = FirebaseFirestore.getInstance()
        val usersList = mutableListOf<User>()

        // Загружаем список пользователей (кроме текущего)
        firestore.collection("users")
            .whereNotEqualTo("id", currentUserId)
            .get()
            .addOnSuccessListener { snapshot ->
                usersList.clear()
                snapshot.documents.forEach { doc ->
                    val user = doc.toObject(User::class.java)
                    user?.let { usersList.add(it) }
                }

                showUserDialog(context, usersList, onUserSelected)
            }
            .addOnFailureListener {
                // Показываем диалог с ошибкой
                AlertDialog.Builder(context)
                    .setTitle("Ошибка")
                    .setMessage("Не удалось загрузить список пользователей")
                    .setPositiveButton("ОК", null)
                    .show()
            }
    }

    private fun showUserDialog(
        context: Context,
        users: List<User>,
        onUserSelected: (String, String) -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_forward_user, null)
        val rvUsers = dialogView.findViewById<RecyclerView>(R.id.rvForwardUsers)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tvEmptyUsers)

        rvUsers.layoutManager = LinearLayoutManager(context)

        if (users.isEmpty()) {
            rvUsers.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "Нет других пользователей"
        } else {
            rvUsers.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE

            val adapter = ForwardUserAdapter(users) { user ->
                onUserSelected(user.id, user.name)
            }
            rvUsers.adapter = adapter
        }

        AlertDialog.Builder(context)
            .setTitle("Переслать сообщение")
            .setView(dialogView)
            .setNegativeButton("Отмена", null)
            .show()
    }

    class ForwardUserAdapter(
        private val users: List<User>,
        private val onUserClick: (User) -> Unit
    ) : RecyclerView.Adapter<ForwardUserAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_forward_user, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = users[position]
            holder.bind(user)
        }

        override fun getItemCount(): Int = users.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvName: TextView = itemView.findViewById(R.id.tvUserName)
            private val tvEmail: TextView = itemView.findViewById(R.id.tvUserEmail)

            fun bind(user: User) {
                tvName.text = user.name
                tvEmail.text = user.email
                itemView.setOnClickListener { onUserClick(user) }
            }
        }
    }
}