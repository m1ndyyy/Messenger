package com.yourname.messenger.utils

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AppLifecycleObserver : DefaultLifecycleObserver {

    private val firestore = FirebaseFirestore.getInstance()

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d("LIFECYCLE", "Приложение в фоне? Нет, стало активным")
        setUserStatus("online")
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d("LIFECYCLE", "Приложение в фоне? Да, ушло в фон")
        if (!AppState.isChatOpen) {
            setUserStatus("offline")
        }
    }

    private fun setUserStatus(status: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        Log.d("LIFECYCLE", "Устанавливаем статус: $status")

        firestore.collection("users").document(userId)
            .update("status", status, "lastSeen", System.currentTimeMillis())
    }
}