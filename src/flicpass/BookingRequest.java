package flicpass;

public class BookingRequest {
    String username;
    int showId;
    double amount;

    public BookingRequest(String username, int showId, double amount) {
        this.username = username;
        this.showId = showId;
        this.amount = amount;
    }

    public double getAmount() { return amount; }

    @Override
    public String toString() {
        return username + " - Show ID:" + showId + " - Amount: Rs." + amount;
    }
}



