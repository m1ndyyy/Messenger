package com.yourname.messenger.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.yourname.messenger.R
import com.yourname.messenger.models.User
import java.text.SimpleDateFormat
import java.util.*

class UserAdapter(
    private var users: List<User>,
    private val onUserClick: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    fun updateUsers(newUsers: List<User>) {
        users = newUsers
        notifyDataSetChanged()
    }

    private fun formatTime(timestamp: Timestamp): String {
        val date = timestamp.toDate()
        val calendar = Calendar.getInstance()
        calendar.time = date
        val now = Calendar.getInstance()

        val isToday = calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

        return if (isToday) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        } else {
            SimpleDateFormat("dd.MM", Locale.getDefault()).format(date)
        }
    }

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage)
        private val tvCheck: TextView = itemView.findViewById(R.id.tvCheck)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvUnreadCount: TextView = itemView.findViewById(R.id.tvUnreadCount)

        private var lastMessageListener: ListenerRegistration? = null
        private var unreadListener: ListenerRegistration? = null

        fun bind(user: User) {
            tvName.text = user.name

            // ЗАГРУЗКА АВАТАРКИ (ПРОСТАЯ РАБОЧАЯ ВЕРСИЯ)
            if (user.avatarUrl.isNotEmpty()) {
                try {
                    val urlWithTimestamp = user.avatarUrl + "?t=" + System.currentTimeMillis()
                    Glide.with(itemView.context)
                        .load(urlWithTimestamp)
                        .circleCrop()
                        .placeholder(R.drawable.ic_default_avatar)
                        .error(R.drawable.ic_default_avatar)
                        .into(ivAvatar)
                } catch (e: Exception) {
                    ivAvatar.setImageResource(R.drawable.ic_default_avatar)
                }
            } else {
                ivAvatar.setImageResource(R.drawable.ic_default_avatar)
            }

            // Статус онлайн/оффлайн
            firestore.collection("users").document(user.id)
                .addSnapshotListener { snapshot, _ ->
                    val status = snapshot?.getString("status") ?: "offline"
                    if (status == "online") {
                        tvStatus.text = "🟢 Онлайн"
                        tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                    } else {
                        tvStatus.text = "⚫ Оффлайн"
                        tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
                    }
                }

            // Загружаем последнее сообщение
            loadLastMessage(user)
            // Загружаем количество непрочитанных
            loadUnreadCount(user)

            itemView.setOnClickListener { onUserClick(user) }
        }

        private fun loadUnreadCount(user: User) {
            val chatId = if (currentUserId < user.id) "$currentUserId-${user.id}" else "${user.id}-$currentUserId"

            unreadListener?.remove()
            unreadListener = firestore.collection("chats")
                .document(chatId)
                .collection("messages")
                .whereEqualTo("receiverId", currentUserId)
                .whereEqualTo("isRead", false)
                .addSnapshotListener { snapshot, _ ->
                    val count = snapshot?.size() ?: 0
                    if (count > 0) {
                        tvUnreadCount.text = if (count > 99) "99+" else count.toString()
                        tvUnreadCount.visibility = View.VISIBLE
                    } else {
                        tvUnreadCount.visibility = View.GONE
                    }
                }
        }

        private fun loadLastMessage(user: User) {
            val chatId = if (currentUserId < user.id) "$currentUserId-${user.id}" else "${user.id}-$currentUserId"

            lastMessageListener?.remove()

            lastMessageListener = firestore.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snapshot, _ ->
                    val docs = snapshot?.documents
                    if (docs.isNullOrEmpty()) {
                        tvLastMessage.text = "Нет сообщений"
                        tvCheck.text = ""
                        tvTime.text = ""
                        return@addSnapshotListener
                    }

                    val msg = docs[0]
                    val text = msg.getString("text") ?: ""
                    val timestamp = msg.getTimestamp("timestamp")
                    val isRead = msg.getBoolean("isRead") ?: false
                    val sender = msg.getString("senderId") ?: ""

                    tvLastMessage.text = text

                    if (timestamp != null) {
                        tvTime.text = formatTime(timestamp)
                    } else {
                        tvTime.text = ""
                    }

                    if (sender == currentUserId) {
                        tvCheck.text = if (isRead) "✓✓" else "✓"
                        tvCheck.setTextColor(Color.parseColor("#4CAF50"))
                        tvCheck.visibility = View.VISIBLE
                        tvUnreadCount.visibility = View.GONE
                    } else {
                        tvCheck.text = ""
                        tvCheck.visibility = View.GONE
                    }
                }
        }

        fun unbind() {
            lastMessageListener?.remove()
            unreadListener?.remove()
        }
    }

    override fun onViewRecycled(holder: UserViewHolder) {
        super.onViewRecycled(holder)
        holder.unbind()
    }
}