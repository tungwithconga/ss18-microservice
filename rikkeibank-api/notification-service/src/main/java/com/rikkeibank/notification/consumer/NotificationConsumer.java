package com.rikkeibank.notification.consumer;

import com.rikkeibank.notification.event.TransferFailedEvent;
import com.rikkeibank.notification.event.TransferSuccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * NotificationConsumer — Kafka consumer nhận events từ transaction-service.
 *
 * Event-driven Architecture: Loose coupling giữa transaction-service và notification-service.
 * notification-service chỉ biết về Kafka topics, không biết gì về transaction-service.
 *
 * Trong thực tế, consumer này sẽ:
 * - Gửi SMS qua Twilio/VNPT
 * - Gửi Email qua SendGrid/JavaMail
 * - Gửi Push Notification qua FCM
 */
@Slf4j
@Component
public class NotificationConsumer {

    @KafkaListener(
            topics = "transfer.success",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleTransferSuccess(TransferSuccessEvent event) {
        log.info("═══════════════════════════════════════════════════");
        log.info("📲 [NOTIFICATION] Chuyển khoản THÀNH CÔNG");
        log.info("   Ref     : {}", event.getTransactionRef());
        log.info("   Từ TK   : {}", event.getSourceAccountNo());
        log.info("   Đến TK  : {}", event.getTargetAccountNo());
        log.info("   Số tiền : {:,} {}", event.getAmount(), "VND");
        log.info("   Thời gian: {}", event.getCompletedAt());
        log.info("   → Gửi SMS/Email thông báo biến động số dư...");
        log.info("═══════════════════════════════════════════════════");

        // TODO: Tích hợp gửi SMS/Email thực tế
        sendSmsNotification(event.getSourceAccountNo(),
                String.format("TK %s: -%.0f VND | Ref: %s",
                        event.getSourceAccountNo(), event.getAmount(), event.getTransactionRef()));
        sendSmsNotification(event.getTargetAccountNo(),
                String.format("TK %s: +%.0f VND | Ref: %s",
                        event.getTargetAccountNo(), event.getAmount(), event.getTransactionRef()));
    }

    @KafkaListener(
            topics = "transfer.failed",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleTransferFailed(TransferFailedEvent event) {
        log.warn("═══════════════════════════════════════════════════");
        log.warn("❌ [NOTIFICATION] Chuyển khoản THẤT BẠI / HOÀN TIỀN");
        log.warn("   Ref    : {}", event.getTransactionRef());
        log.warn("   Từ TK  : {}", event.getSourceAccountNo());
        log.warn("   Lý do  : {}", event.getReason());
        log.warn("   Thời gian: {}", event.getFailedAt());
        log.warn("   → Gửi thông báo thất bại tới khách hàng...");
        log.warn("═══════════════════════════════════════════════════");

        sendSmsNotification(event.getSourceAccountNo(),
                String.format("Chuyển khoản THẤT BẠI. Ref: %s | Lý do: %s",
                        event.getTransactionRef(), event.getReason()));
    }

    /**
     * Giả lập gửi SMS (thay bằng Twilio/VNPT API thực tế)
     */
    private void sendSmsNotification(String accountNo, String message) {
        log.info("[SMS] → Account {}: {}", accountNo, message);
        // Twilio.sendSms(phoneNumber, message);
    }
}
