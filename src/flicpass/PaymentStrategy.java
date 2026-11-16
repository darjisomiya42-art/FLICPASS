package flicpass;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.*;

public interface PaymentStrategy {
    boolean pay(double amount, Scanner sc, int bookingId, Connection conn);
    String getModeName();
}

class UPIPayment implements PaymentStrategy {
    @Override
    public String getModeName() { return "UPI"; }

    @Override
    public boolean pay(double amount, Scanner sc, int bookingId, Connection conn) {
        if (sc != null) {
            while (true) {
                System.out.print("Enter UPI ID (e.g. user@upi / 9876543210@paytm): ");
                String upiId = sc.nextLine().trim();

                if (!upiId.contains("@") || upiId.length() < 5) {
                    System.out.println("Payment Failed: Invalid UPI ID format. Please try again.");
                } else {
                    break;
                }
            }
        }

        String txnId = "UPI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        System.out.println("Processing UPI Payment of Rs." + amount + "...");
        System.out.println("Transaction Successful! Txn ID: " + txnId);

        return savePaymentToDB(conn, bookingId, getModeName(), amount, txnId);
    }

    private boolean savePaymentToDB(Connection conn, int bookingId, String mode, double amount, String txnId) {
        String sql = "INSERT INTO payments (booking_id, payment_mode, amount, transaction_id, payment_status) VALUES (?, ?, ?, ?, 'Success')";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, bookingId);
            pstmt.setString(2, mode);
            pstmt.setDouble(3, amount);
            pstmt.setString(4, txnId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Payment DB Record Failed: " + e.getMessage());
            return false;
        }
    }
}

class CardPayment implements PaymentStrategy {
    @Override
    public String getModeName() { return "Credit/Debit Card"; }

    @Override
    public boolean pay(double amount, Scanner sc, int bookingId, Connection conn) {
        if (sc != null) {
            String cardNumber;
            while (true) {
                System.out.print("Enter 16-digit Card Number: ");
                cardNumber = sc.nextLine().trim();
                if (cardNumber.length() != 16) {
                    System.out.println("Payment Failed: Card number must be 16 digits. Please try again.");
                } else {
                    break;
                }
            }

            String expiry;
            while (true) {
                System.out.print("Enter Expiry Date (MM/YY): ");
                expiry = sc.nextLine().trim();

                try {
                    String[] parts = expiry.split("/");
                    if (parts.length != 2) throw new Exception();
                    int month = Integer.parseInt(parts[0]);
                    int year = Integer.parseInt(parts[1]) + 2000;

                    java.time.YearMonth cardExpiry = java.time.YearMonth.of(year, month);
                    if (cardExpiry.isBefore(java.time.YearMonth.now())) {
                        System.out.println("Payment Failed: Card is already expired! Please try again.");
                    } else {
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("Payment Failed: Invalid Expiry Date format. Please use MM/YY.");
                }
            }

            String cvv;
            while (true) {
                System.out.print("Enter 3-digit CVV: ");
                cvv = sc.nextLine().trim();

                if (cvv.length() != 3) {
                    System.out.println("Payment Failed: CVV must be 3 digits. Please try again.");
                } else {
                    break;
                }
            }
        }

        String txnId = "CARD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        System.out.println("Processing Card Payment of Rs." + amount + "...");
        System.out.println("Transaction Successful! Txn ID: " + txnId);

        return savePaymentToDB(conn, bookingId, getModeName(), amount, txnId);
    }

    private boolean savePaymentToDB(Connection conn, int bookingId, String mode, double amount, String txnId) {
        String sql = "INSERT INTO payments (booking_id, payment_mode, amount, transaction_id, payment_status) VALUES (?, ?, ?, ?, 'Success')";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, bookingId);
            pstmt.setString(2, mode);
            pstmt.setDouble(3, amount);
            pstmt.setString(4, txnId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Payment DB Record Failed: " + e.getMessage());
            return false;
        }
    }
}

class FlicPassWalletPayment implements PaymentStrategy {
    @Override
    public String getModeName() { return "FlicPass Wallet"; }

    @Override
    public boolean pay(double amount, Scanner sc, int bookingId, Connection conn) {
        try {
            String getUserSql = "SELECT user_id FROM bookings WHERE booking_id = ?";
            int userId = -1;
            try (PreparedStatement gStmt = conn.prepareStatement(getUserSql)) {
                gStmt.setInt(1, bookingId);
                try (ResultSet rs = gStmt.executeQuery()) {
                    if (rs.next()) userId = rs.getInt("user_id");
                }
            }

            if (userId == -1) {
                System.out.println("Wallet Payment Error: User not found for booking.");
                return false;
            }
            double balance = 0.0;
            String balSql = "SELECT wallet_balance FROM users WHERE user_id = ?";
            try (PreparedStatement bStmt = conn.prepareStatement(balSql)) {
                bStmt.setInt(1, userId);
                try (ResultSet rs = bStmt.executeQuery()) {
                    if (rs.next()) balance = rs.getDouble("wallet_balance");
                }
            }

            if (balance < amount) {
                System.out.println("\nPayment Failed: Insufficient FlicPass Wallet Balance!");
                System.out.println("Current Wallet Balance: Rs." + balance);
                System.out.println("Required Amount       : Rs." + amount);
                System.out.println("Please recharge your FlicPass Wallet from Account Menu first.");
                return false;
            }
            String dedSql = "UPDATE users SET wallet_balance = wallet_balance - ? WHERE user_id = ?";
            try (PreparedStatement dStmt = conn.prepareStatement(dedSql)) {
                dStmt.setDouble(1, amount);
                dStmt.setInt(2, userId);
                dStmt.executeUpdate();
            }

            String txnId = "FPWALL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            System.out.println("Processing FlicPass Wallet Payment of Rs." + amount + "...");
            System.out.println("Transaction Successful! Txn ID: " + txnId);
            System.out.println("Remaining Wallet Balance: Rs." + (balance - amount));

            return savePaymentToDB(conn, bookingId, getModeName(), amount, txnId);
        } catch (SQLException e) {
            System.out.println("Wallet Payment Exception: " + e.getMessage());
            return false;
        }
    }

    private boolean savePaymentToDB(Connection conn, int bookingId, String mode, double amount, String txnId) {
        String sql = "INSERT INTO payments (booking_id, payment_mode, amount, transaction_id, payment_status) VALUES (?, ?, ?, ?, 'Success')";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, bookingId);
            pstmt.setString(2, mode);
            pstmt.setDouble(3, amount);
            pstmt.setString(4, txnId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Payment DB Record Failed: " + e.getMessage());
            return false;
        }
    }
}

