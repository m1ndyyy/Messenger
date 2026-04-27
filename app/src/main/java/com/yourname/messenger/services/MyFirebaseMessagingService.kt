package com.yourname.messenger.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.yourname.messenger.R
import com.yourname.messenger.activities.ChatActivity

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "messages_channel"
        private const val CHANNEL_NAME = "Сообщения"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "🔥 НОВЫЙ ТОКЕН: $token")
        saveTokenToFirestore(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM", "📩 ПОЛУЧЕНО СООБЩЕНИЕ: ${remoteMessage.data}")

        val data = remoteMessage.data
        val senderName = data["senderName"] ?: "Новое сообщение"
        val messageText = data["message"] ?: "Новое сообщение"
        val senderId = data["senderId"] ?: ""

        showNotification(senderName, messageText, senderId)
    }

    private fun showNotification(title: String, message: String, senderId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых сообщениях"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("receiverId", senderId)
            putExtra("receiverName", title)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)

        Log.d("FCM", "✅ УВЕДОМЛЕНИЕ ПОКАЗАНО: $title - $message")
    }

    private fun saveTokenToFirestore(token: String) {
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        Log.d("FCM", "💾 Сохраняем токен для пользователя: $userId")

        if (userId != null) {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            firestore.collection("users").document(userId)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d("FCM", "✅ Токен сохранён в Firestore")
                }
                .addOnFailureListener {
                    Log.e("FCM", "❌ Ошибка сохранения токена: ${it.message}")
                    // Создаём документ, если его нет
                    firestore.collection("users").document(userId)
                        .set(hashMapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                }
        } else {
            Log.e("FCM", "❌ Пользователь не авторизован, токен не сохранён")
        }
    }
}