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
    var isRead: Boolean = false,

    @get:PropertyName("isEdited") @set:PropertyName("isEdited")
    var isEdited: Boolean = false,

    @get:PropertyName("isLiked") @set:PropertyName("isLiked")
    var isLiked: Boolean = false,

    @get:PropertyName("replyToId") @set:PropertyName("replyToId")
    var replyToId: String = "",

    @get:PropertyName("replyToText") @set:PropertyName("replyToText")
    var replyToText: String = "",

    @get:PropertyName("replyToSenderName") @set:PropertyName("replyToSenderName")
    var replyToSenderName: String = "",

    @get:PropertyName("forwardedFrom") @set:PropertyName("forwardedFrom")
    var forwardedFrom: String = "",

    @get:PropertyName("isForwarded") @set:PropertyName("isForwarded")
    var isForwarded: Boolean = false,

    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted")
    var isDeleted: Boolean = false,

    @get:PropertyName("senderName") @set:PropertyName("senderName")
    var senderName: String = ""
)