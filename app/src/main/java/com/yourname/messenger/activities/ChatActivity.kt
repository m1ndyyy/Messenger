package com.yourname.messenger.activities

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.yourname.messenger.R
import com.yourname.messenger.adapters.MessageAdapter
import com.yourname.messenger.models.Message
import com.yourname.messenger.utils.AppState
import java.util.*

class ChatActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var adapter: MessageAdapter
    private lateinit var currentChatId: String
    private lateinit var tvName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var ivAvatar: ImageView
    private var wasAtBottom = true

    private val messagesList = mutableListOf<Message>()
    private lateinit var receiverId: String
    private lateinit var receiverName: String
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val markedAsRead = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = null
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        // Кастомный тулбар
        val customView = layoutInflater.inflate(R.layout.custom_toolbar_chat, null)
        ivAvatar = customView.findViewById(R.id.ivChatAvatar)
        tvName = customView.findViewById(R.id.tvChatName)
        tvStatus = customView.findViewById(R.id.tvChatStatus)
        val btnBack = customView.findViewById<ImageButton>(R.id.btnChatBack)

        toolbar.removeAllViews()
        toolbar.addView(customView, androidx.appcompat.widget.Toolbar.LayoutParams(
            androidx.appcompat.widget.Toolbar.LayoutParams.MATCH_PARENT,
            androidx.appcompat.widget.Toolbar.LayoutParams.MATCH_PARENT
        ))

        btnBack.setOnClickListener {
            finish()
        }

        receiverId = intent.getStringExtra("receiverId") ?: ""
        receiverName = intent.getStringExtra("receiverName") ?: "Пользователь"

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        currentChatId = if (currentUserId < receiverId) "$currentUserId-$receiverId" else "$receiverId-$currentUserId"

        AppState.isChatOpen = true
        AppState.currentChatId = currentChatId

        firestore.collection("users").document(currentUserId)
            .update("currentChatId", currentChatId)

        // Загружаем данные собеседника
        firestore.collection("users").document(receiverId)
            .addSnapshotListener { snapshot, _ ->
                val name = snapshot?.getString("name") ?: receiverName
                val status = snapshot?.getString("status") ?: "offline"
                val avatarUrl = snapshot?.getString("avatarUrl") ?: ""

                tvName.text = name

                if (status == "online") {
                    tvStatus.text = "🟢 Онлайн"
                    tvStatus.setTextColor(ContextCompat.getColor(this@ChatActivity, android.R.color.holo_green_light))
                } else {
                    tvStatus.text = "⚫ Оффлайн"
                    tvStatus.setTextColor(ContextCompat.getColor(this@ChatActivity, android.R.color.darker_gray))
                }

                if (avatarUrl.isNotEmpty()) {
                    Glide.with(this).load(avatarUrl).circleCrop().into(ivAvatar)
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_default_avatar)
                }
            }

        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        rvMessages.layoutManager = LinearLayoutManager(this)
        adapter = MessageAdapter(messagesList, currentUserId)
        rvMessages.adapter = adapter

        loadMessages()

        rvMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                markVisibleMessagesAsRead()
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    markVisibleMessagesAsRead()
                    checkIfAtBottom()
                }
            }
        })

        btnSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun loadMessages() {
        val chatId = if (currentUserId < receiverId) "$currentUserId-$receiverId" else "$receiverId-$currentUserId"

        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener

                val wasAtBottomBefore = wasAtBottom

                messagesList.clear()
                snapshot.documents.forEach { doc ->
                    val message = doc.toObject(Message::class.java)
                    message?.let { messagesList.add(it) }
                }

                adapter.updateMessages(messagesList.toList())

                if (wasAtBottomBefore && messagesList.isNotEmpty()) {
                    rvMessages.scrollToPosition(messagesList.size - 1)
                }

                rvMessages.post {
                    markVisibleMessagesAsRead()
                }
            }
    }

    private fun checkIfAtBottom() {
        if (rvMessages.layoutManager == null) return
        val layoutManager = rvMessages.layoutManager as LinearLayoutManager
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        wasAtBottom = lastVisible >= messagesList.size - 1
    }

    private fun markVisibleMessagesAsRead() {
        val layoutManager = rvMessages.layoutManager as LinearLayoutManager
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (firstVisible == -1) return

        for (i in firstVisible..lastVisible) {
            val message = messagesList.getOrNull(i) ?: continue
            if (message.receiverId == currentUserId && !message.isRead && !markedAsRead.contains(message.id)) {
                markedAsRead.add(message.id)
                markSingleMessageAsRead(message.id)
            }
        }
    }

    private fun markSingleMessageAsRead(messageId: String) {
        val chatId = if (currentUserId < receiverId) "$currentUserId-$receiverId" else "$receiverId-$currentUserId"
        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)
            .update("isRead", true)
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return

        val messageId = UUID.randomUUID().toString()
        val chatId = if (currentUserId < receiverId) "$currentUserId-$receiverId" else "$receiverId-$currentUserId"

        val messageMap = hashMapOf(
            "id" to messageId,
            "senderId" to currentUserId,
            "receiverId" to receiverId,
            "text" to text,
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false
        )

        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)
            .set(messageMap)
            .addOnSuccessListener {
                etMessage.text.clear()
                rvMessages.scrollToPosition(messagesList.size)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка отправки", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppState.isChatOpen = false
        AppState.currentChatId = ""
        firestore.collection("users").document(currentUserId)
            .update("currentChatId", FieldValue.delete())
    }
}