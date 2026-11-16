package flicpass;

public class SMSNotification extends Notification {
    @Override
    void send(String recipient, String message) {
        System.out.println("[SMS Notification to " + recipient + "]: " + message);
    }
}
