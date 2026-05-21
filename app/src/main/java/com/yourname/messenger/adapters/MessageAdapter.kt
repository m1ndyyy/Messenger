package com.yourname.messenger.adapters

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.yourname.messenger.R
import com.yourname.messenger.models.Message
import com.yourname.messenger.utils.ForwardDialog
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private var messages: List<Message>,
    private val currentUserId: String,
    private val chatId: String,
    private val onMessageEdited: () -> Unit,
    private val onMessageReplied: (Message) -> Unit,
    private val onMessageClicked: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val firestore = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private val pendingLikes = mutableMapOf<String, Runnable>()

    companion object {
        private const val TYPE_INCOMING = 1
        private const val TYPE_OUTGOING = 2
        private const val LIKE_DELAY = 300L
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.senderId == currentUserId) TYPE_OUTGOING else TYPE_INCOMING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_INCOMING -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_incoming, parent, false)
                IncomingViewHolder(view, onMessageReplied, ::toggleLike, ::deleteMessage, ::showForwardDialog, onMessageClicked)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_outgoing, parent, false)
                OutgoingViewHolder(view, onMessageReplied, ::toggleLike, ::deleteMessage, ::updateMessageInFirestore, ::showForwardDialog, onMessageClicked)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is IncomingViewHolder -> holder.bind(message, position)
            is OutgoingViewHolder -> holder.bind(message, position)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun updateMessages(newMessages: List<Message>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    private fun formatTime(timestamp: Any?): String {
        if (timestamp == null) return "..."
        return try {
            val ts = timestamp as Timestamp
            val date = ts.toDate()
            val calendar = Calendar.getInstance()
            calendar.time = date
            val now = Calendar.getInstance()

            val isToday = calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

            if (isToday) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            } else {
                SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(date)
            }
        } catch (e: Exception) {
            "..."
        }
    }

    private fun updateMessageInFirestore(messageId: String, newText: String) {
        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)
            .update(
                mapOf(
                    "text" to newText,
                    "isEdited" to true
                )
            )
            .addOnSuccessListener {
                onMessageEdited()
            }
    }

    private fun deleteMessage(message: Message) {
        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(message.id)
            .update(
                mapOf(
                    "text" to "",
                    "isDeleted" to true
                )
            )
            .addOnSuccessListener {
                onMessageEdited()
            }
    }

    private fun toggleLike(message: Message, position: Int) {
        val newLikeState = !message.isLiked
        val messageId = message.id

        pendingLikes[messageId]?.let { handler.removeCallbacks(it) }

        val runnable = Runnable {
            firestore.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .update("isLiked", newLikeState)
            pendingLikes.remove(messageId)
        }

        pendingLikes[messageId] = runnable
        handler.postDelayed(runnable, LIKE_DELAY)

        val updatedMessage = message.copy(isLiked = newLikeState)
        val newMessages = messages.toMutableList()
        newMessages[position] = updatedMessage
        messages = newMessages
        notifyItemChanged(position)
    }

    private fun showForwardDialog(message: Message, context: Context) {
        ForwardDialog.show(
            context = context,
            currentUserId = currentUserId,
            onUserSelected = { selectedUserId, _ ->
                val newChatId = if (currentUserId < selectedUserId) {
                    "$currentUserId-$selectedUserId"
                } else {
                    "$selectedUserId-$currentUserId"
                }

                val forwardedMessage = hashMapOf(
                    "id" to UUID.randomUUID().toString(),
                    "senderId" to currentUserId,
                    "receiverId" to selectedUserId,
                    "text" to message.text,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "isRead" to false,
                    "isEdited" to false,
                    "isLiked" to false,
                    "isForwarded" to true,
                    "forwardedFrom" to "",
                    "senderName" to ""
                )

                firestore.collection("chats")
                    .document(newChatId)
                    .collection("messages")
                    .add(forwardedMessage)

                android.widget.Toast.makeText(context, "Сообщение переслано", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun isDarkTheme(context: Context): Boolean {
        return when (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    inner class IncomingViewHolder(
        itemView: View,
        private val onReplyClick: (Message) -> Unit,
        private val onLikeToggle: (Message, Int) -> Unit,
        private val onDeleteClick: (Message) -> Unit,
        private val onForwardClick: (Message, Context) -> Unit,
        private val onMessageClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvEdited: TextView = itemView.findViewById(R.id.tvEdited)
        private val tvLike: TextView = itemView.findViewById(R.id.tvLike)
        private val tvDeleted: TextView = itemView.findViewById(R.id.tvDeleted)
        private val layoutReply: View = itemView.findViewById(R.id.layoutReply)
        private val tvReplySender: TextView = itemView.findViewById(R.id.tvReplySender)
        private val tvReplyText: TextView = itemView.findViewById(R.id.tvReplyText)
        private val layoutForward: View = itemView.findViewById(R.id.layoutForward)
        private val tvForwardedText: TextView = itemView.findViewById(R.id.tvForwardedText)

        private var currentMessage: Message? = null
        private var currentPosition: Int = -1

        init {
            itemView.setOnClickListener {
                currentMessage?.let { msg ->
                    onLikeToggle(msg, currentPosition)
                }
            }

            itemView.setOnLongClickListener {
                currentMessage?.let { msg ->
                    showContextMenu(msg)
                }
                true
            }

            layoutReply.setOnClickListener {
                currentMessage?.let { msg ->
                    if (msg.replyToId.isNotEmpty()) {
                        onMessageClick(msg.replyToId)
                    }
                }
            }
        }

        fun bind(message: Message, position: Int) {
            currentMessage = message
            currentPosition = position

            if (message.isDeleted) {
                tvMessage.visibility = View.GONE
                tvDeleted.visibility = View.VISIBLE
                tvDeleted.text = "Сообщение удалено"
                layoutReply.visibility = View.GONE
                layoutForward.visibility = View.GONE
                return
            } else {
                tvMessage.visibility = View.VISIBLE
                tvDeleted.visibility = View.GONE
                tvMessage.text = message.text
            }

            tvTime.text = formatTime(message.timestamp)
            tvEdited.visibility = if (message.isEdited) View.VISIBLE else View.GONE
            tvLike.visibility = if (message.isLiked) View.VISIBLE else View.GONE

            if (message.isForwarded) {
                layoutForward.visibility = View.VISIBLE
                tvForwardedText.text = "📩 Пересланное сообщение"
            } else {
                layoutForward.visibility = View.GONE
            }

            if (message.replyToId.isNotEmpty() && message.replyToText.isNotEmpty()) {
                layoutReply.visibility = View.VISIBLE
                tvReplySender.text = message.replyToSenderName
                tvReplyText.text = message.replyToText
                tvReplyText.maxLines = Int.MAX_VALUE
                tvReplyText.ellipsize = null
            } else {
                layoutReply.visibility = View.GONE
            }
        }

        private fun showContextMenu(message: Message) {
            val items = mutableListOf("Ответить", "Переслать", "Удалить")

            val builder = AlertDialog.Builder(itemView.context)
            builder.setTitle("Действия")
            builder.setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "Ответить" -> onReplyClick(message)
                    "Переслать" -> onForwardClick(message, itemView.context)
                    "Удалить" -> showDeleteConfirmDialog(message)
                }
            }

            val dialog = builder.create()
            dialog.show()

            try {
                val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

                if (isDarkTheme(itemView.context)) {
                    positiveButton?.setTextColor(Color.parseColor("#64B5F6"))
                    negativeButton?.setTextColor(Color.parseColor("#EF5350"))
                } else {
                    positiveButton?.setTextColor(Color.parseColor("#2196F3"))
                    negativeButton?.setTextColor(Color.parseColor("#F44336"))
                }
            } catch (e: Exception) {
                // Игнорируем
            }
        }

        private fun showDeleteConfirmDialog(message: Message) {
            val builder = AlertDialog.Builder(itemView.context)
            builder.setTitle("Удалить сообщение")
            builder.setMessage("Вы уверены, что хотите удалить это сообщение?")
            builder.setPositiveButton("Удалить") { _, _ ->
                onDeleteClick(message)
            }
            builder.setNegativeButton("Отмена", null)

            val dialog = builder.create()
            dialog.show()

            try {
                val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

                if (isDarkTheme(itemView.context)) {
                    positiveButton?.setTextColor(Color.parseColor("#EF5350"))
                    negativeButton?.setTextColor(Color.parseColor("#64B5F6"))
                } else {
                    positiveButton?.setTextColor(Color.parseColor("#F44336"))
                    negativeButton?.setTextColor(Color.parseColor("#2196F3"))
                }
            } catch (e: Exception) {
                // Игнорируем
            }
        }
    }

    inner class OutgoingViewHolder(
        itemView: View,
        private val onReplyClick: (Message) -> Unit,
        private val onLikeToggle: (Message, Int) -> Unit,
        private val onDeleteClick: (Message) -> Unit,
        private val onEditMessage: (String, String) -> Unit,
        private val onForwardClick: (Message, Context) -> Unit,
        private val onMessageClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvEdited: TextView = itemView.findViewById(R.id.tvEdited)
        private val tvLike: TextView = itemView.findViewById(R.id.tvLike)
        private val tvDeleted: TextView = itemView.findViewById(R.id.tvDeleted)
        private val layoutReply: View = itemView.findViewById(R.id.layoutReply)
        private val tvReplySender: TextView = itemView.findViewById(R.id.tvReplySender)
        private val tvReplyText: TextView = itemView.findViewById(R.id.tvReplyText)
        private val layoutForward: View = itemView.findViewById(R.id.layoutForward)
        private val tvForwardedText: TextView = itemView.findViewById(R.id.tvForwardedText)

        private var currentMessage: Message? = null
        private var currentPosition: Int = -1

        init {
            itemView.setOnClickListener {
                currentMessage?.let { msg ->
                    onLikeToggle(msg, currentPosition)
                }
            }

            itemView.setOnLongClickListener {
                currentMessage?.let { msg ->
                    showContextMenu(msg)
                }
                true
            }

            layoutReply.setOnClickListener {
                currentMessage?.let { msg ->
                    if (msg.replyToId.isNotEmpty()) {
                        onMessageClick(msg.replyToId)
                    }
                }
            }
        }

        fun bind(message: Message, position: Int) {
            currentMessage = message
            currentPosition = position

            if (message.isDeleted) {
                tvMessage.visibility = View.GONE
                tvDeleted.visibility = View.VISIBLE
                tvDeleted.text = "Вы удалили сообщение"
                tvStatus.visibility = View.GONE
                layoutReply.visibility = View.GONE
                layoutForward.visibility = View.GONE
                return
            } else {
                tvMessage.visibility = View.VISIBLE
                tvDeleted.visibility = View.GONE
                tvMessage.text = message.text
                tvStatus.visibility = View.VISIBLE
            }

            tvTime.text = formatTime(message.timestamp)
            tvStatus.text = if (message.isRead) "✓✓" else "✓"
            tvStatus.setTextColor(if (message.isRead) Color.parseColor("#4CAF50") else Color.parseColor("#9E9E9E"))
            tvEdited.visibility = if (message.isEdited) View.VISIBLE else View.GONE
            tvLike.visibility = if (message.isLiked) View.VISIBLE else View.GONE

            if (message.isForwarded) {
                layoutForward.visibility = View.VISIBLE
                tvForwardedText.text = "📩 Пересланное сообщение"
            } else {
                layoutForward.visibility = View.GONE
            }

            if (message.replyToId.isNotEmpty() && message.replyToText.isNotEmpty()) {
                layoutReply.visibility = View.VISIBLE
                tvReplySender.text = message.replyToSenderName
                tvReplyText.text = message.replyToText
                tvReplyText.maxLines = Int.MAX_VALUE
                tvReplyText.ellipsize = null
            } else {
                layoutReply.visibility = View.GONE
            }
        }

        private fun showContextMenu(message: Message) {
            val items = mutableListOf("Ответить", "Переслать", "Редактировать", "Удалить")

            val builder = AlertDialog.Builder(itemView.context)
            builder.setTitle("Действия")
            builder.setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "Ответить" -> onReplyClick(message)
                    "Переслать" -> onForwardClick(message, itemView.context)
                    "Редактировать" -> showEditDialog(message)
                    "Удалить" -> showDeleteConfirmDialog(message)
                }
            }

            val dialog = builder.create()
            dialog.show()

            try {
                val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                val neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)

                if (isDarkTheme(itemView.context)) {
                    positiveButton?.setTextColor(Color.parseColor("#64B5F6"))
                    negativeButton?.setTextColor(Color.parseColor("#EF5350"))
                    neutralButton?.setTextColor(Color.parseColor("#AAAAAA"))
                } else {
                    positiveButton?.setTextColor(Color.parseColor("#2196F3"))
                    negativeButton?.setTextColor(Color.parseColor("#F44336"))
                    neutralButton?.setTextColor(Color.parseColor("#757575"))
                }
            } catch (e: Exception) {
                // Игнорируем
            }
        }

        private fun showDeleteConfirmDialog(message: Message) {
            val builder = AlertDialog.Builder(itemView.context)
            builder.setTitle("Удалить сообщение")
            builder.setMessage("Вы уверены, что хотите удалить это сообщение?")
            builder.setPositiveButton("Удалить") { _, _ ->
                onDeleteClick(message)
            }
            builder.setNegativeButton("Отмена", null)

            val dialog = builder.create()
            dialog.show()

            try {
                val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

                if (isDarkTheme(itemView.context)) {
                    positiveButton?.setTextColor(Color.parseColor("#EF5350"))
                    negativeButton?.setTextColor(Color.parseColor("#64B5F6"))
                } else {
                    positiveButton?.setTextColor(Color.parseColor("#F44336"))
                    negativeButton?.setTextColor(Color.parseColor("#2196F3"))
                }
            } catch (e: Exception) {
                // Игнорируем
            }
        }

        private fun showEditDialog(message: Message) {
            val context = itemView.context
            val input = EditText(context)
            input.setText(message.text)
            input.setSelection(message.text.length)

            val builder = AlertDialog.Builder(context)
            builder.setTitle("Редактировать сообщение")
            builder.setView(input)
            builder.setPositiveButton("Сохранить") { _, _ ->
                val newText = input.text.toString().trim()
                if (newText.isNotEmpty() && newText != message.text) {
                    onEditMessage(message.id, newText)
                }
            }
            builder.setNegativeButton("Отмена", null)

            val dialog = builder.create()
            dialog.show()

            try {
                val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

                if (isDarkTheme(itemView.context)) {
                    positiveButton?.setTextColor(Color.parseColor("#64B5F6"))
                    negativeButton?.setTextColor(Color.parseColor("#EF5350"))
                } else {
                    positiveButton?.setTextColor(Color.parseColor("#2196F3"))
                    negativeButton?.setTextColor(Color.parseColor("#F44336"))
                }
            } catch (e: Exception) {
                // Игнорируем
            }
        }
    }
}