package com.yourname.messenger.models

import com.google.firebase.firestore.PropertyName

data class Message(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String = "",

    @get:PropertyName("senderId") @set:PropertyName("senderId")
    var senderId: String = "",

    @get:PropertyName("receiverId") @set:PropertyName("receiverId")
    var receiverId: String = "",

    @get:PropertyName("text") @set:PropertyName("text")
    var text: String = "",

    @get:PropertyName("timestamp") @set:PropertyName("timestamp")
    var timestamp: Any? = null,

    @get:PropertyName("isRead") @set:PropertyName("isRead")
    var isRead: Boolean = false
)