package com.yourname.messenger

import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class MyApplication : Application() {

    private lateinit var firestore: FirebaseFirestore
    private var activityCount = 0

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        firestore = FirebaseFirestore.getInstance()

        // Принудительный запрос токена
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "🔥 ТОКЕН: $token")
                saveTokenToFirestore(token)
            } else {
                Log.e("FCM", "❌ Ошибка: ${task.exception?.message}")
            }
        }

        // Глобальное отслеживание активности приложения
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                activityCount++
                if (activityCount == 1) {
                    // Приложение стало видимым (любая Activity открыта)
                    setUserStatus("online")
                }
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                activityCount--
                if (activityCount == 0) {
                    // Приложение полностью закрыто или свернуто
                    setUserStatus("offline")
                }
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }

    private fun saveTokenToFirestore(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            firestore.collection("users").document(userId)
                .update("fcmToken", token)
                .addOnFailureListener {
                    firestore.collection("users").document(userId)
                        .set(hashMapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                }
        }
    }

    private fun setUserStatus(status: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        firestore.collection("users").document(userId)
            .update("status", status, "lastSeen", System.currentTimeMillis())
            .addOnFailureListener {
                val userData = hashMapOf<String, Any>(
                    "id" to userId,
                    "status" to status,
                    "lastSeen" to System.currentTimeMillis(),
                    "name" to (FirebaseAuth.getInstance().currentUser?.email ?: "User"),
                    "email" to (FirebaseAuth.getInstance().currentUser?.email ?: "")
                )
                firestore.collection("users").document(userId).set(userData)
            }
    }
}