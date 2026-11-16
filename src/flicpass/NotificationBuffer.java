package flicpass;
public class NotificationBuffer {
    private String notificationMessage;
    private boolean empty = true;
    public synchronized String consume() {
        while (empty) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        empty = true;
        notifyAll();
        return notificationMessage;
    }
    public synchronized void produce(String msg) {
        while (!empty) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        empty = false;
        this.notificationMessage = msg;
        notifyAll();
    }
}