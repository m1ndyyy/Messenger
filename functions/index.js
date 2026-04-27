const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

exports.onNewMessage = functions.firestore
    .document('chats/{chatId}/messages/{messageId}')
    .onCreate(async (snapshot, context) => {
        const message = snapshot.data();
        
        console.log('📩 Новое сообщение от:', message.senderId);
        
        if (message.senderId === message.receiverId) return null;
        
        try {
            const senderDoc = await admin.firestore().collection('users').doc(message.senderId).get();
            const senderName = senderDoc.data()?.name || 'Пользователь';
            
            const receiverDoc = await admin.firestore().collection('users').doc(message.receiverId).get();
            const fcmToken = receiverDoc.data()?.fcmToken;
            
            if (!fcmToken) return null;
            
            const payload = {
                data: {
                    senderName: senderName,
                    message: message.text,
                    senderId: message.senderId,
                    chatId: context.params.chatId
                }
            };
            
            await admin.messaging().sendToDevice(fcmToken, payload);
            console.log('✅ Уведомление отправлено!');
            
        } catch (error) {
            console.error('❌ Ошибка:', error);
        }
        
        return null;
    });