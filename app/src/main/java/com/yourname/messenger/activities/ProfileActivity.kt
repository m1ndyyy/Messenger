package com.yourname.messenger.activities

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.yourname.messenger.R
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var ivAvatar: ImageView
    private lateinit var tvEmail: TextView
    private lateinit var etName: EditText
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton

    private var selectedImageUri: Uri? = null
    private var isUploading = false

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            ivAvatar.setImageURI(selectedImageUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        ivAvatar = findViewById(R.id.ivAvatar)
        tvEmail = findViewById(R.id.tvEmail)
        etName = findViewById(R.id.etName)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        ivAvatar.setOnClickListener {
            checkPermissionAndPickImage()
        }

        loadUserData()

        btnSave.setOnClickListener {
            if (selectedImageUri != null && !isUploading) {
                uploadAvatarToImgBB()
            } else {
                saveUserName()
            }
        }
    }

    private fun checkPermissionAndPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                pickImageLauncher.launch("image/*")
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                pickImageLauncher.launch("image/*")
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            pickImageLauncher.launch("image/*")
        } else {
            Toast.makeText(this, "Нужно разрешение для выбора фото", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        val userEmail = auth.currentUser?.email ?: ""

        tvEmail.text = userEmail

        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val name = document.getString("name") ?: userEmail.split("@")[0]
                val avatarUrl = document.getString("avatarUrl") ?: ""
                etName.setText(name)

                if (avatarUrl.isNotEmpty()) {
                    Glide.with(this)
                        .load(avatarUrl)
                        .circleCrop()
                        .into(ivAvatar)
                }
            }
    }

    private fun uploadAvatarToImgBB() {
        isUploading = true
        btnSave.isEnabled = false
        Toast.makeText(this, "Загрузка...", Toast.LENGTH_SHORT).show()

        val stream = contentResolver.openInputStream(selectedImageUri!!)
        val bytes = stream?.readBytes()
        stream?.close()

        if (bytes == null) {
            runOnUiThread {
                Toast.makeText(this, "Ошибка чтения файла", Toast.LENGTH_SHORT).show()
                isUploading = false
                btnSave.isEnabled = true
            }
            return
        }

        val API_KEY = "4941164819b8858f8af559fcf36dcd24"

        val base64Image = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", base64Image)
            .build()

        val request = Request.Builder()
            .url("https://api.imgbb.com/1/upload?key=$API_KEY")
            .post(requestBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ProfileActivity, "Ошибка сети: ${e.message}", Toast.LENGTH_SHORT).show()
                    isUploading = false
                    btnSave.isEnabled = true
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    try {
                        val json = JSONObject(responseBody)
                        val imageUrl = json.getJSONObject("data").getString("url")

                        val userId = auth.currentUser?.uid ?: return
                        firestore.collection("users").document(userId)
                            .update("avatarUrl", imageUrl)
                            .addOnSuccessListener {
                                runOnUiThread {
                                    Toast.makeText(this@ProfileActivity, "Аватар обновлён!", Toast.LENGTH_SHORT).show()
                                    Glide.with(this@ProfileActivity)
                                        .load(imageUrl)
                                        .circleCrop()
                                        .into(ivAvatar)
                                    saveUserName()
                                }
                            }
                            .addOnFailureListener {
                                runOnUiThread {
                                    Toast.makeText(this@ProfileActivity, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                                    isUploading = false
                                    btnSave.isEnabled = true
                                }
                            }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@ProfileActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                            isUploading = false
                            btnSave.isEnabled = true
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@ProfileActivity, "Ошибка загрузки ${response.code}", Toast.LENGTH_SHORT).show()
                        isUploading = false
                        btnSave.isEnabled = true
                    }
                }
            }
        })
    }

    private fun saveUserName() {
        val newName = etName.text.toString().trim()
        if (newName.isEmpty()) {
            Toast.makeText(this, "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
            isUploading = false
            btnSave.isEnabled = true
            return
        }

        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users").document(userId)
            .update("name", newName)
            .addOnSuccessListener {
                Toast.makeText(this, "Сохранено!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                isUploading = false
                btnSave.isEnabled = true
            }
    }
}