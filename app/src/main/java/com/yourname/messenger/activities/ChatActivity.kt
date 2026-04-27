package com.yourname.messenger.activities

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.yourname.messenger.R
import com.yourname.messenger.adapters.MessageAdapter
import com.yourname.messenger.models.Message
import java.util.*

class ChatActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var adapter: MessageAdapter

    private val messagesList = mutableListOf<Message>()
    private lateinit var receiverId: String
    private lateinit var receiverName: String
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        receiverId = intent.getStringExtra("receiverId") ?: ""
        receiverName = intent.getStringExtra("receiverName") ?: "Пользователь"
        supportActionBar?.title = receiverName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        rvMessages.layoutManager = LinearLayoutManager(this)
        adapter = MessageAdapter(messagesList, currentUserId)
        rvMessages.adapter = adapter

        loadMessages()

        btnSend.setOnClickListener {
            sendMessage()
        }
    }

    override fun onStart() {
        super.onStart()
        val chatId = if (currentUserId < receiverId) "$currentUserId-$receiverId" else "$receiverId-$currentUserId"
        markMessagesAsRead(chatId)
    }

    private fun loadMessages() {
        val chatId = if (currentUserId < receiverId) "$currentUserId-$receiverId" else "$receiverId-$currentUserId"

        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener

                messagesList.clear()
                snapshot.documents.forEach { doc ->
                    val message = doc.toObject(Message::class.java)
                    message?.let { messagesList.add(it) }
                }

                adapter.updateMessages(messagesList.toList())

                if (messagesList.isNotEmpty()) {
                    rvMessages.scrollToPosition(messagesList.size - 1)
                }
            }
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
            }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка отправки", Toast.LENGTH_SHORT).show()
            }
    }

    private fun markMessagesAsRead(chatId: String) {
        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .whereEqualTo("receiverId", currentUserId)
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    doc.reference.update("isRead", true)
                }
            }
    }
}