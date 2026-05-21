package com.yourname.messenger.activities

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
import com.google.firebase.firestore.SetOptions
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
    private lateinit var btnScrollToBottom: ImageButton
    private lateinit var adapter: MessageAdapter
    private lateinit var currentChatId: String
    private lateinit var tvName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var ivAvatar: ImageView
    private var wasAtBottom = true
    private var isUserScrolling = false
    private var isFirstLoad = true
    private lateinit var prefs: SharedPreferences

    private val messagesList = mutableListOf<Message>()
    private lateinit var receiverId: String
    private lateinit var receiverName: String
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val markedAsRead = mutableSetOf<String>()

    // Для ответа на сообщение
    private var replyToMessage: Message? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        prefs = getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = null
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

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
            saveScrollPosition()
            finish()
        }

        receiverId = intent.getStringExtra("receiverId") ?: ""
        receiverName = intent.getStringExtra("receiverName") ?: "Пользователь"

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        currentChatId = if (currentUserId < receiverId) "$currentUserId-$receiverId" else "$receiverId-$currentUserId"

        AppState.isChatOpen = true
        AppState.currentChatId = currentChatId

        // Сохраняем currentChatId в Firestore
        firestore.collection("users").document(currentUserId)
            .set(hashMapOf("currentChatId" to currentChatId), SetOptions.merge())

        // Загружаем данные собеседника
        firestore.collection("users").document(receiverId)
            .addSnapshotListener { snapshot, _ ->
                if (!isFinishing && !isDestroyed) {
                    val name = snapshot?.getString("name") ?: receiverName
                    val status = snapshot?.getString("status") ?: "offline"
                    val avatarUrl = snapshot?.getString("avatarUrl") ?: ""

                    tvName.text = name

                    if (status == "online") {
                        tvStatus.text = "🟢"
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                    } else {
                        tvStatus.text = "⚫"
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                    }

                    if (avatarUrl.isNotEmpty()) {
                        Glide.with(this)
                            .load(avatarUrl)
                            .circleCrop()
                            .into(ivAvatar)
                    } else {
                        ivAvatar.setImageResource(R.drawable.ic_default_avatar)
                    }
                }
            }

        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnScrollToBottom = findViewById(R.id.btnScrollToBottom)

        rvMessages.layoutManager = LinearLayoutManager(this)

        // Создаем адаптер
        adapter = MessageAdapter(
            messagesList,
            currentUserId,
            currentChatId,
            { loadMessages() },  // onMessageEdited
            { message -> showReplyInput(message) },  // onMessageReplied
            { messageId -> scrollToMessage(messageId) }  // onMessageClicked
        )
        rvMessages.adapter = adapter

        loadMessages()

        // СЛУШАТЕЛЬ ДЛЯ СТРЕЛКИ
        rvMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                isUserScrolling = true

                val layoutManager = rvMessages.layoutManager as LinearLayoutManager
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val isAtBottom = lastVisible >= messagesList.size - 1

                if (isAtBottom) {
                    btnScrollToBottom.visibility = View.GONE
                } else {
                    btnScrollToBottom.visibility = View.VISIBLE
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    markVisibleMessagesAsRead()
                    checkIfAtBottom()
                    isUserScrolling = false
                    saveScrollPosition()
                }
            }
        })

        btnScrollToBottom.setOnClickListener {
            if (messagesList.isNotEmpty()) {
                rvMessages.smoothScrollToPosition(messagesList.size - 1)
                btnScrollToBottom.visibility = View.GONE
            }
        }

        btnSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun saveScrollPosition() {
        val layoutManager = rvMessages.layoutManager as LinearLayoutManager
        val position = layoutManager.findFirstVisibleItemPosition()
        val firstView = rvMessages.getChildAt(0)
        val offset = if (firstView != null) firstView.top - layoutManager.paddingTop else 0

        prefs.edit().apply {
            putInt("${currentChatId}_position", position)
            putInt("${currentChatId}_offset", offset)
            apply()
        }
    }

    private fun getSavedPosition(): Int {
        return prefs.getInt("${currentChatId}_position", -1)
    }

    private fun getSavedOffset(): Int {
        return prefs.getInt("${currentChatId}_offset", 0)
    }

    override fun onPause() {
        super.onPause()
        saveScrollPosition()
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
                val oldSize = messagesList.size

                messagesList.clear()
                snapshot.documents.forEach { doc ->
                    val message = doc.toObject(Message::class.java)
                    message?.let { messagesList.add(it) }
                }

                adapter.updateMessages(messagesList.toList())

                if (isFirstLoad && messagesList.isNotEmpty()) {
                    val savedPosition = getSavedPosition()
                    val savedOffset = getSavedOffset()

                    if (savedPosition != -1 && savedPosition < messagesList.size) {
                        rvMessages.layoutManager?.scrollToPosition(savedPosition)
                        rvMessages.post {
                            val layoutManager = rvMessages.layoutManager as LinearLayoutManager
                            if (savedPosition >= 0 && savedPosition < messagesList.size) {
                                layoutManager.scrollToPositionWithOffset(savedPosition, savedOffset)
                            }
                        }
                    } else {
                        rvMessages.scrollToPosition(messagesList.size - 1)
                    }
                    isFirstLoad = false
                } else {
                    if (wasAtBottomBefore && messagesList.size > oldSize && messagesList.isNotEmpty()) {
                        rvMessages.scrollToPosition(messagesList.size - 1)
                        btnScrollToBottom.visibility = View.GONE
                    }
                }

                rvMessages.post {
                    markVisibleMessagesAsRead()
                    checkIfAtBottom()
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

    private fun showReplyInput(message: Message) {
        replyToMessage = message
        val senderName = if (message.senderId == currentUserId) "Вы" else receiverName
        etMessage.hint = "Ответ $senderName..."

        val btnCancelReply = findViewById<ImageButton>(R.id.btnCancelReply)
        btnCancelReply?.visibility = View.VISIBLE
        btnCancelReply?.setOnClickListener {
            cancelReply()
        }
    }

    private fun cancelReply() {
        replyToMessage = null
        etMessage.hint = "Сообщение"
        findViewById<ImageButton>(R.id.btnCancelReply)?.visibility = View.GONE
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return

        val messageId = UUID.randomUUID().toString()
        val chatId = if (currentUserId < receiverId) "$currentUserId-$receiverId" else "$receiverId-$currentUserId"

        val messageMap = mutableMapOf<String, Any>(
            "id" to messageId,
            "senderId" to currentUserId,
            "receiverId" to receiverId,
            "text" to text,
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "isEdited" to false,
            "isLiked" to false,
            "isDeleted" to false,
            "isForwarded" to false,
            "forwardedFrom" to "",
            "senderName" to receiverName
        )

        // Добавляем информацию об ответе, если есть
        if (replyToMessage != null) {
            messageMap["replyToId"] = replyToMessage!!.id
            messageMap["replyToText"] = replyToMessage!!.text
            messageMap["replyToSenderName"] = if (replyToMessage!!.senderId == currentUserId) "Вы" else receiverName
            cancelReply()
        }

        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)
            .set(messageMap)
            .addOnSuccessListener {
                etMessage.text.clear()
                if (wasAtBottom) {
                    rvMessages.scrollToPosition(messagesList.size)
                    btnScrollToBottom.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка отправки", Toast.LENGTH_SHORT).show()
            }
    }

    private fun scrollToMessage(messageId: String) {
        val position = messagesList.indexOfFirst { it.id == messageId }
        if (position != -1) {
            val recyclerViewHeight = rvMessages.height
            rvMessages.scrollToPosition(position)

            rvMessages.postDelayed({
                val holder = rvMessages.findViewHolderForAdapterPosition(position)
                val viewHeight = holder?.itemView?.height ?: 200
                val offset = (recyclerViewHeight / 2) - (viewHeight / 2)
                (rvMessages.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, offset)

                if (holder != null) {
                    holder.itemView.setBackgroundColor(ContextCompat.getColor(this, R.color.highlight_color))
                    holder.itemView.animate()?.scaleX(1.03f)?.scaleY(1.03f)?.setDuration(100)?.withEndAction {
                        holder.itemView.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(100)?.start()
                    }?.start()

                    Handler(Looper.getMainLooper()).postDelayed({
                        holder.itemView.setBackgroundColor(0)
                    }, 1000)
                }
            }, 100)
        } else {
            Toast.makeText(this, "Сообщение не найдено", Toast.LENGTH_SHORT).show()
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