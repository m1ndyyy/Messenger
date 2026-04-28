package com.yourname.messenger.models

import com.google.firebase.firestore.PropertyName

data class User(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String = "",

    @get:PropertyName("name") @set:PropertyName("name")
    var name: String = "",

    @get:PropertyName("email") @set:PropertyName("email")
    var email: String = "",

    @get:PropertyName("status") @set:PropertyName("status")
    var status: String = "offline",

    @get:PropertyName("fcmToken") @set:PropertyName("fcmToken")
    var fcmToken: String = "",

    @get:PropertyName("avatarUrl") @set:PropertyName("avatarUrl")
    var avatarUrl: String = "",

    @get:PropertyName("lastSeen") @set:PropertyName("lastSeen")
    var lastSeen: Long = System.currentTimeMillis()  // ← ИЗМЕНИЛ НА Long
)