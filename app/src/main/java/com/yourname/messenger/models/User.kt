package com.yourname.messenger.models

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val status: String = "offline",
    val fcmToken: String = "",
    val avatarUrl: String = ""
)