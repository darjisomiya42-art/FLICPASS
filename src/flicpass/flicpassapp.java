package flicpass;

import java.util.*;
import java.util.function.Predicate;
import java.sql.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.io.FileOutputStream;


public class flicpassapp {
    static Map<Integer, String> movieTitleCache = new HashMap<>();
    static Hashtable<Integer, Double> showPriceCache = new Hashtable<>();
    static Stack<String> recentStack = new Stack<>();
    static PriorityQueue<BookingRequest> priorityBookingQueue =
            new PriorityQueue<>(new Comparator<BookingRequest>() {
                @Override
                public int compare(BookingRequest b1, BookingRequest b2) {
                    return Double.compare(b2.getAmount(), b1.getAmount());
                }
            });
    static ArrayDeque<String> movieRequests = new ArrayDeque<>();
    static LinkedList<String> notificationLog = new LinkedList<>();
    static Vector<String> theaterCache = new Vector<>();
    static NotificationBuffer notificationBuffer = new NotificationBuffer();
    static Thread notificationWorker;

    static {
        notificationWorker = new Thread(new Runnable() {
            @Override
            public void run() {
                Notification notifier = new SMSNotification();
                while (true) {
                    String msg = notificationBuffer.consume();
                    if ("SHUTDOWN".equals(msg)) {
                        break; // Gracefully terminate the thread instead of using setDaemon(true)
                    }
                    if (msg != null && !msg.isEmpty()) {
                        notifier.send("Customer", msg);
                    }
                }
            }
        });
        notificationWorker.start(); // Thread started normally, NO Daemon
    }

    private static boolean isConnectionAvailable(Connection conn) {
        if (conn == null) {
            System.out.println("Database connection is not available. Please check MySQL and try again.");
            return false;
        }
        return true;
    }

    private static Integer readInt(Scanner sc, String prompt) {
        System.out.print(prompt);
        if (!sc.hasNextLine()) {
            System.out.println("\nNo input available.");
            System.exit(0);
        }
        String input = sc.nextLine().trim();
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a whole number.");
            return null;
        }
    }

    private static Integer readPositiveInt(Scanner sc, String prompt) {
        Integer value = readInt(sc, prompt);
        if (value == null) return null;
        if (value <= 0) {
            System.out.println("Value must be greater than 0.");
            return null;
        }
        return value;
    }

    private static Double readPositiveDouble(Scanner sc, String prompt) {
        System.out.print(prompt);
        if (!sc.hasNextLine()) {
            System.out.println("\nNo input available.");
            System.exit(0);
        }
        String input = sc.nextLine().trim();
        try {
            double value = Double.parseDouble(input);
            if (value <= 0) {
                System.out.println("Value must be greater than 0.");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a valid number.");
            return null;
        }
    }

    private static LocalDate readDate(Scanner sc, String prompt) {
        System.out.print(prompt);
        if (!sc.hasNextLine()) {
            System.out.println("\nNo input available.");
            System.exit(0);
        }
        String input = sc.nextLine().trim();
        try {
            return LocalDate.parse(input);
        } catch (DateTimeParseException e) {
            System.out.println("Invalid date. Use YYYY-MM-DD.");
            return null;
        }
    }

    private static LocalTime readTime(Scanner sc, String prompt) {
        System.out.print(prompt);
        if (!sc.hasNextLine()) {
            System.out.println("\nNo input available.");
            System.exit(0);
        }
        String input = sc.nextLine().trim();
        try {
            return LocalTime.parse(input);
        } catch (DateTimeParseException e) {
            System.out.println("Invalid time. Use HH:MM or HH:MM:SS.");
            return null;
        }
    }
    public static void registerUser(Scanner sc) {
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;

            String username = "";
            while (true) {
                System.out.print("Enter Username: ");
                username = sc.nextLine().trim();
                if (!isUniqueInDB(conn, "username", username)) {
                    System.out.println("Invalid: Username '" + username + "' is already taken. Try another.");
                    continue;
                }
                break;
            }

            String email = "";
            while (true) {
                System.out.print("Enter Email Address: ");
                email = sc.nextLine().trim().toLowerCase();
                boolean hasAt = email.contains("@");
                boolean hasDot = email.contains(".");
                boolean validDomainEnding = email.endsWith(".com") || email.endsWith(".yahoo") || email.endsWith(".in") || email.endsWith(".org");

                if (!hasAt || !hasDot || !validDomainEnding) {
                    System.out.println("Invalid Email format! Must contain '@' and end with a valid domain (e.g. .com, .in).");
                    continue;
                }

                if (!isUniqueInDB(conn, "email", email)) {
                    System.out.println("Invalid: Email address '" + email + "' is already registered. Try another.");
                    continue;
                }
                break;
            }

            String mobileNumber = "";
            while (true) {
                System.out.print("Enter Mobile Number: ");
                mobileNumber = sc.nextLine().trim();

                if (mobileNumber.length() != 10) {
                    System.out.println("Invalid: Mobile number must be exactly 10 digits.");
                    continue;
                }

                char firstChar = mobileNumber.charAt(0);
                if (firstChar < '6' || firstChar > '9') {
                    System.out.println("Invalid: Mobile number must start with 6, 7, 8, or 9.");
                    continue;
                }

                boolean isNumeric = true;
                for (int i = 0; i < mobileNumber.length(); i++) {
                    char ch = mobileNumber.charAt(i);
                    if (ch < '0' || ch > '9') {
                        isNumeric = false;
                        break;
                    }
                }

                if (!isNumeric) {
                    System.out.println("Invalid: Mobile number must contain only numeric digits (0-9).");
                    continue;
                }

                if (!isUniqueInDB(conn, "mobile_number", mobileNumber)) {
                    System.out.println("Invalid: Mobile number '" + mobileNumber + "' is already registered. Try another.");
                    continue;
                }
                break;
            }

            System.out.print("Enter Password: ");
            String password = sc.nextLine().trim();

            String insertQuery = "INSERT INTO users (username, email, mobile_number, password_hash, role, wallet_balance) " +
                    "VALUES (?, ?, ?, ?, 'Customer', 0.00)";

            try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
                pstmt.setString(1, username);
                pstmt.setString(2, email);
                pstmt.setString(3, mobileNumber);
                pstmt.setString(4, password);

                int rowsInserted = pstmt.executeUpdate();
                if (rowsInserted > 0) {
                    System.out.println("Registration Successful for " + username + "! You can now Login.");
                }
            }

        } catch (SQLException e) {
            System.out.println("Registration Failed: " + e.getMessage());
        }
    }

    private static boolean isUniqueInDB(Connection conn, String column, String value) {
        String query = "SELECT 1 FROM users WHERE " + column + " = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, value);
            try (ResultSet rs = pstmt.executeQuery()) {
                return !rs.next(); // Returns true if no duplicate found
            }
        } catch (SQLException e) {
            return false;
        }
    }
    public static void confirmBooking(Scanner sc, String username, int showId, int[] showSeatIds, int coinsToRedeem, String format, String language) {
        Connection conn = null;
        int bookingId = -1;
        try {
            conn = DBConnection.getConnection();
            if (!isConnectionAvailable(conn)) return;
            conn.setAutoCommit(false); // Transaction start

            int userId = getUserId(conn, username);
            if (userId == -1) {
                System.out.println("User not found: " + username);
                conn.rollback();
                return;
            }

            double basePrice = getShowPrice(conn, showId);
            if (basePrice <= 0) {
                System.out.println("Invalid show selected.");
                conn.rollback();
                return;
            }
            double totalAmount = basePrice * showSeatIds.length;
            int coinBalance = getFlicpassCoins(conn, userId);
            if (coinsToRedeem > 0 && coinBalance < 50) {
                System.out.println("Booking Failed: You need at least 50 FlicPass Coins to use a discount.");
                conn.rollback();
                return;
            }
            if (coinsToRedeem > coinBalance || coinsToRedeem > totalAmount) {
                System.out.println("Booking Failed: Invalid FlicPass Coin discount.");
                conn.rollback();
                return;
            }
            double payableAmount = totalAmount - coinsToRedeem;
            int earnedCoins = showSeatIds.length * 10;
            String checkQuery = "SELECT is_booked FROM show_seats WHERE seat_id = ? AND show_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                for (int seatId : showSeatIds) {
                    checkStmt.setInt(1, seatId);
                    checkStmt.setInt(2, showId);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Seat ID " + seatId + " does not belong to this show.");
                        }
                        if (rs.getBoolean("is_booked")) {
                            throw new SeatAlreadyBookedException("Seat ID " + seatId + " is already booked!");
                        }
                    }
                }
            }

            String insertBooking = "INSERT INTO bookings (user_id, show_id, total_tickets, total_amount, booking_status) " +
                    "VALUES (?, ?, ?, ?, 'Confirmed')";
            try (PreparedStatement bStmt = conn.prepareStatement(insertBooking, Statement.RETURN_GENERATED_KEYS)) {
                bStmt.setInt(1, userId);
                bStmt.setInt(2, showId);
                bStmt.setInt(3, showSeatIds.length);
                bStmt.setDouble(4, payableAmount);
                bStmt.executeUpdate();

                ResultSet keys = bStmt.getGeneratedKeys();
                if (keys.next()) {
                    bookingId = keys.getInt(1);
                }
            }

            PaymentStrategy paymentMethod = null;
            while (true) {
                if (sc != null) {
                    System.out.println("\n========== PAYMENT GATEWAY ==========");
                    System.out.println("Payable Amount: Rs." + payableAmount);
                    System.out.println("1. UPI Payment (GPay, PhonePe, Paytm)");
                    System.out.println("2. Credit / Debit Card");
                    System.out.println("3. FlicPass Wallet");
                    System.out.println("4. Cancel & Back to Dashboard");
                    Integer payChoiceInput = readPositiveInt(sc, "Select Payment Method: ");
                    if (payChoiceInput == null || payChoiceInput == 4) {
                        System.out.println("Payment cancelled. Returning to Dashboard...");
                        conn.rollback();
                        return;
                    }

                    switch (payChoiceInput) {
                        case 1: paymentMethod = new UPIPayment(); break;
                        case 2: paymentMethod = new CardPayment(); break;
                        case 3: paymentMethod = new FlicPassWalletPayment(); break;
                        default:
                            System.out.println("Invalid payment option. Try again.");
                            continue;
                    }
                } else {
                    String topupSql = "UPDATE users SET wallet_balance = GREATEST(wallet_balance, 5000.00) WHERE user_id = ?";
                    try (PreparedStatement tStmt = conn.prepareStatement(topupSql)) {
                        tStmt.setInt(1, userId);
                        tStmt.executeUpdate();
                    }
                    paymentMethod = new FlicPassWalletPayment();
                }

                boolean paymentSuccess = paymentMethod.pay(payableAmount, sc, bookingId, conn);
                if (paymentSuccess) {
                    break;
                } else {
                    if (sc == null) {
                        System.out.println("Payment failed. Booking cancelled.");
                        conn.rollback();
                        return;
                    }
                    System.out.println("Payment failed. Let's try again.");
                }
            }

            String updateSeat = "UPDATE show_seats SET is_booked = TRUE, booking_id = ? " +
                    "WHERE seat_id = ? AND show_id = ? AND is_booked = FALSE";
            try (PreparedStatement uStmt = conn.prepareStatement(updateSeat)) {
                for (int seatId : showSeatIds) {
                    uStmt.setInt(1, bookingId);
                    uStmt.setInt(2, seatId);
                    uStmt.setInt(3, showId);
                    uStmt.addBatch();
                }
                int[] updatedSeats = uStmt.executeBatch();
                for (int i = 0; i < updatedSeats.length; i++) {
                    if (updatedSeats[i] == 0) {
                        throw new SeatAlreadyBookedException("One of the selected seats was just booked by someone else.");
                    }
                }
            }

            String updateCoins = "UPDATE users SET flicpass_coins = flicpass_coins - ? + ? WHERE user_id = ?";
            try (PreparedStatement cStmt = conn.prepareStatement(updateCoins)) {
                cStmt.setInt(1, coinsToRedeem);
                cStmt.setInt(2, earnedCoins);
                cStmt.setInt(3, userId);
                cStmt.executeUpdate();
            }

            conn.commit(); // Transaction commit

            priorityBookingQueue.offer(new BookingRequest(username, showId, payableAmount));
            notificationLog.addLast(username + " booked " + showSeatIds.length + " seat(s) for show " + showId);

            notificationBuffer.produce("Booking (ID: " + bookingId + ") confirmed! Amount paid via " +
                    paymentMethod.getModeName() + ": Rs." + payableAmount);
            generateAndDownloadTicketFile(bookingId, username, showId, showSeatIds.length, payableAmount, paymentMethod.getModeName(), format, language);


            System.out.println("\nBooking & Payment Confirmed!");
            System.out.println("Booking ID  : " + bookingId);
            System.out.println("Customer    : " + username);
            System.out.println("Total Seats : " + showSeatIds.length);
            System.out.println("Payment Mode: " + paymentMethod.getModeName());
            System.out.println("Discount    : Rs." + coinsToRedeem);
            System.out.println("Paid Amount : Rs." + payableAmount);
            System.out.println("Coins Earned: " + earnedCoins);

        } catch (SeatAlreadyBookedException se) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.out.println("Booking Failed for [" + username + "]: " + se.getMessage());
        } catch (SQLException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.out.println("Booking Failed for [" + username + "]: " + e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }
    private static void autoGenerateShowsForMovie(Connection conn, int movieId, String selectedFormat) {
        String insertSql = "INSERT INTO shows (movie_id, screen_id, show_date, show_time, base_price) VALUES (?, ?, ?, ?, ?)";
        String[] showTimes = {"10:30:00", "15:15:00", "20:00:00"};
        double[] prices = {220.00, 240.00, 260.00};

        List<Integer> screenIds = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT screen_id FROM screens")) {
            while (rs.next()) {
                screenIds.add(rs.getInt("screen_id"));
            }
        } catch (SQLException e) {
            System.out.println("Error fetching screens for auto-generation: " + e.getMessage());
        }

        LocalDate today = LocalDate.now();
        String seatQuery = "INSERT INTO show_seats (show_id, seat_id, is_booked) SELECT ?, seat_id, FALSE FROM seats WHERE screen_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement seatStmt = conn.prepareStatement(seatQuery)) {
            for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
                LocalDate showDate = today.plusDays(dayOffset);
                for (int screenId : screenIds) {
                    for (int i = 0; i < showTimes.length; i++) {
                        pstmt.setInt(1, movieId);
                        pstmt.setInt(2, screenId);
                        pstmt.setDate(3, java.sql.Date.valueOf(showDate));
                        pstmt.setTime(4, java.sql.Time.valueOf(showTimes[i]));
                        pstmt.setDouble(5, prices[i]);
                        pstmt.executeUpdate();

                        try (ResultSet rs = pstmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                int showId = rs.getInt(1);
                                seatStmt.setInt(1, showId);
                                seatStmt.setInt(2, screenId);
                                seatStmt.executeUpdate();
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
        }
    }
    public static void generateAndDownloadTicketFile(int bookingId, String username, int showId, int seats, double amount, String paymentMode, String format, String language) {
        String movieTitle = "Unknown";
        String showDate = "Unknown";
        String showTime = "Unknown";
        String theaterName = "Unknown";
        String theaterCity = "Unknown";
        String screenName = "Unknown";

        String query = "SELECT m.title, s.show_date, s.show_time, t.name AS t_name, t.city, sc.screen_number " +
                "FROM shows s " +
                "JOIN movies m ON s.movie_id = m.movie_id " +
                "JOIN screens sc ON s.screen_id = sc.screen_id " +
                "JOIN theaters t ON sc.theater_id = t.theater_id " +
                "WHERE s.show_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, showId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    movieTitle = rs.getString("title");
                    showDate = rs.getDate("show_date").toString();
                    showTime = rs.getTime("show_time").toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a"));
                    theaterName = rs.getString("t_name");
                    theaterCity = rs.getString("city");
                    screenName = "Screen " + rs.getInt("screen_number");
                }
            }
        } catch (SQLException e) {
            System.out.println("Could not fetch show details for ticket: " + e.getMessage());
        }
        String seatNumbers = "Unknown";
        String seatQuery = "SELECT s.seat_number FROM show_seats ss JOIN seats s ON ss.seat_id = s.seat_id WHERE ss.booking_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(seatQuery)) {
            pstmt.setInt(1, bookingId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<String> seatsList = new ArrayList<>();
                while (rs.next()) {
                    seatsList.add(rs.getString("seat_number"));
                }
                if (!seatsList.isEmpty()) {
                    seatNumbers = String.join(", ", seatsList);
                }
            }
        } catch (SQLException e) {
            System.out.println("Could not fetch seat numbers for ticket: " + e.getMessage());
        }

        String fileName = "Ticket_Booking_" + bookingId + ".txt";
        File file = new File(fileName);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write("=================================================");
            bw.newLine();
            bw.write("             FLICKPASS MOVIE TICKET              ");
            bw.newLine();
            bw.write("=================================================");
            bw.newLine();
            bw.write("Booking ID   : " + bookingId);
            bw.newLine();
            bw.write("Customer Name: " + username);
            bw.newLine();
            bw.write("Movie        : " + movieTitle);
            bw.newLine();
            bw.write("Show Date    : " + showDate);
            bw.newLine();
            bw.write("Show Time    : " + showTime);
            bw.newLine();
            bw.write("Theater      : " + theaterName + " (" + theaterCity + ")");
            bw.newLine();
            bw.write("Screen       : " + screenName);
            bw.newLine();
            bw.write("Format       : " + format);
            bw.newLine();
            bw.write("Language     : " + language);
            bw.newLine();
            bw.write("Seats Booked : " + seats + " (Seats: " + seatNumbers + ")");
            bw.newLine();
            bw.write("Amount Paid  : Rs." + amount);
            bw.newLine();
            bw.write("Payment Mode : " + paymentMode);
            bw.newLine();
            bw.write("Date & Time  : " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            bw.newLine();
            bw.write("=================================================");
            bw.newLine();

            writeBookingReceipt(bookingId, username, showId, seats, amount);

            System.out.println("\n[DOWNLOAD] Your Ticket File has been downloaded to disk:");
            System.out.println("--> File Path: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.out.println("Error generating ticket file: " + e.getMessage());
        }
    }

    public static void writeBookingReceipt(int bookingId, String username, int showId, int seats, double amount) {
        File file = new File("booking_receipts.txt");
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
                bw.write("BookingID:" + bookingId + " | User:" + username + " | ShowID:" + showId +
                        " | Seats:" + seats + " | Amount:" + amount + " | Time:" + LocalDateTime.now());
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("Could not write receipt to file: " + e.getMessage());
        }
    }
    public static void viewBookingLogsFromFile(Scanner sc) {
        File file = new File("booking_receipts.txt");
        if (!file.exists()) {
            System.out.println("No booking logs found yet.");
            return;
        }

        System.out.println("\n----- FILTER BOOKING LOGS (FROM TXT FILE) -----");
        System.out.println("1. All Time Logs");
        System.out.println("2. Month-wise Logs (Filter by Specific Year & Month)");
        System.out.println("3. Year-wise Logs (Filter by Specific Year)");
        Integer filterChoice = null;
        while (filterChoice == null || filterChoice < 1 || filterChoice > 3) {
            filterChoice = readPositiveInt(sc, "Select Option: ");
            if (filterChoice != null && (filterChoice < 1 || filterChoice > 3)) {
                System.out.println("Invalid option. Please select 1, 2, or 3.");
                filterChoice = null;
            }
        }

        String targetPattern = "";
        String filterHeader = "ALL TIME LOGS";

        if (filterChoice == 2) {
            Integer yearInput = null;
            while (yearInput == null) {
                yearInput = readPositiveInt(sc, "Enter Year (YYYY, e.g. 2026): ");
            }
            Integer monthInput = null;
            while (monthInput == null || monthInput < 1 || monthInput > 12) {
                monthInput = readPositiveInt(sc, "Enter Month (1 to 12): ");
                if (monthInput != null && (monthInput < 1 || monthInput > 12)) {
                    System.out.println("Invalid month.");
                    monthInput = null;
                }
            }
            String monthStr = (monthInput < 10) ? ("0" + monthInput) : String.valueOf(monthInput);
            targetPattern = yearInput + "-" + monthStr; // e.g. "2026-08"
            filterHeader = "MONTH-WISE LOGS FOR " + yearInput + "-" + monthStr;
        } else if (filterChoice == 3) {
            Integer yearInput = null;
            while (yearInput == null) {
                yearInput = readPositiveInt(sc, "Enter Year (YYYY, e.g. 2026): ");
            }
            targetPattern = String.valueOf(yearInput); // e.g. "2026"
            filterHeader = "YEAR-WISE LOGS FOR YEAR " + yearInput;
        }

        String reportFileName = "Admin_Report_" + (targetPattern.isEmpty() ? "All_Time" : targetPattern) + ".txt";
        File reportFile = new File(reportFileName);

        try (BufferedReader br = new BufferedReader(new FileReader(file));
             BufferedWriter bw = new BufferedWriter(new FileWriter(reportFile))) {

            String line;
            System.out.println("\n----- BOOKING LOGS REPORT (" + filterHeader + ") -----");
            System.out.println("ID   | User            | Show   | Seats | Amount      | Time");
            System.out.println("----------------------------------------------------------------------------------");
            bw.write("----- BOOKING LOGS REPORT (" + filterHeader + ") -----\n");
            bw.write("ID   | User            | Show   | Seats | Amount      | Time\n");
            bw.write("----------------------------------------------------------------------------------\n");

            int totalRecords = 0;
            int totalSeatsBooked = 0;
            double totalRevenue = 0.0;

            while ((line = br.readLine()) != null) {
                if (targetPattern.isEmpty() || line.contains("Time:" + targetPattern) || line.contains("| Time:" + targetPattern)) {
                    totalRecords++;

                    try {
                        String bIdStr = line.substring(line.indexOf("BookingID:") + 10, line.indexOf(" | User:")).trim();
                        String userStr = line.substring(line.indexOf("User:") + 5, line.indexOf(" | ShowID:")).trim();
                        String showStr = line.substring(line.indexOf("ShowID:") + 7, line.indexOf(" | Seats:")).trim();
                        String seatsStr = line.substring(line.indexOf("Seats:") + 6, line.indexOf(" | Amount:")).trim();
                        String amtStr = line.substring(line.indexOf("Amount:") + 7, line.indexOf(" | Time:")).trim();
                        String timeStr = line.substring(line.indexOf("Time:") + 5).trim();
                        if (timeStr.length() > 19) timeStr = timeStr.substring(0, 19).replace("T", " ");

                        String formattedLine = bIdStr + getSpaces(4 - bIdStr.length()) + " | " +
                                userStr + getSpaces(15 - userStr.length()) + " | " +
                                showStr + getSpaces(6 - showStr.length()) + " | " +
                                seatsStr + getSpaces(5 - seatsStr.length()) + " | Rs." +
                                amtStr + getSpaces(8 - amtStr.length()) + " | " + timeStr;

                        System.out.println(formattedLine);
                        bw.write(formattedLine + "\n");
                    } catch (Exception e) {
                        System.out.println(line);
                        bw.write(line + "\n");
                    }
                    try {
                        if (line.contains("Seats:") && line.contains("Amount:")) {
                            String seatsPart = line.substring(line.indexOf("Seats:") + 6);
                            seatsPart = seatsPart.substring(0, seatsPart.indexOf("|")).trim();
                            totalSeatsBooked += Integer.parseInt(seatsPart);

                            String amountPart = line.substring(line.indexOf("Amount:") + 7);
                            amountPart = amountPart.substring(0, amountPart.indexOf("|")).trim();
                            totalRevenue += Double.parseDouble(amountPart);
                        }
                    } catch (Exception ex) {
                    }
                }
            }

            if (totalRecords == 0) {
                System.out.println("No booking records found for the selected filter.");
                bw.write("No booking records found for the selected filter.\n");
            } else {
                System.out.println("\n================ SUMMARY REPORT ================");
                System.out.println("Total Bookings Found : " + totalRecords);
                System.out.println("Total Seats Booked   : " + totalSeatsBooked);
                System.out.println("Total Revenue Earned : Rs." + totalRevenue);
                System.out.println("================================================");

                bw.write("\n================ SUMMARY REPORT ================\n");
                bw.write("Total Bookings Found : " + totalRecords + "\n");
                bw.write("Total Seats Booked   : " + totalSeatsBooked + "\n");
                bw.write("Total Revenue Earned : Rs." + totalRevenue + "\n");
                bw.write("================================================\n");
                if (filterChoice == 2 && targetPattern.contains("-")) {
                    try {
                        String[] parts = targetPattern.split("-");
                        int spYear = Integer.parseInt(parts[0]);
                        int spMonth = Integer.parseInt(parts[1]);

                        try (Connection conn = DBConnection.getConnection()) {
                            if (isConnectionAvailable(conn)) {
                                try (java.sql.CallableStatement cstmt = conn.prepareCall("{CALL GetMonthlyRevenue(?, ?, ?)}")) {
                                    cstmt.setInt(1, spYear);
                                    cstmt.setInt(2, spMonth);
                                    cstmt.registerOutParameter(3, java.sql.Types.DOUBLE);
                                    cstmt.execute();
                                    double dbRevenue = cstmt.getDouble(3);

                                    System.out.println("\n[Database Stored Procedure Result]");
                                    System.out.println("Revenue calculated directly by DB Trigger/Procedure: Rs." + dbRevenue);

                                    bw.write("\n[Database Stored Procedure Result]\n");
                                    bw.write("Revenue calculated directly by DB Trigger/Procedure: Rs." + dbRevenue + "\n");
                                } catch (SQLException ex) {
                                    System.out.println("\n(Note: Stored Procedure 'GetMonthlyRevenue' not found in DB. Import the SQL file to see DB-level calculations.)");
                                }
                            }
                        } catch (SQLException e) {
                        }
                    } catch (Exception e) {
                    }
                }

                System.out.println("\n[DOWNLOAD] Report generated successfully:");
                System.out.println("--> File Path: " + reportFile.getAbsolutePath());
            }
        } catch (IOException e) {
            System.out.println("Error reading/writing log file: " + e.getMessage());
        }
    }

    public static int getUserId(Connection conn, String username) throws SQLException {
        String query = "SELECT user_id FROM users WHERE username = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("user_id");
            }
        }
        return -1;
    }

    public static int getFlicpassCoins(Connection conn, int userId) throws SQLException {
        String query = "SELECT flicpass_coins FROM users WHERE user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("flicpass_coins");
            }
        }
        return 0;
    }

    public static void viewFlicpassCoins(String username) {
        String query = "SELECT flicpass_coins FROM users WHERE username = ?";
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, username);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        int coins = rs.getInt("flicpass_coins");
                        System.out.println("\nFlicPass Coins: " + coins);
                        if (coins >= 50) {
                            System.out.println("Discount available: Rs." + coins + " (1 coin = Rs.1)");
                        } else {
                            System.out.println("Earn " + (50 - coins) + " more coin(s) to unlock discounts.");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching FlicPass Coins: " + e.getMessage());
        }
    }
    public static void viewWalletBalance(String username) {
        String query = "SELECT wallet_balance FROM users WHERE username = ?";
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, username);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        double balance = rs.getDouble("wallet_balance");
                        System.out.println("\nFlicPass Wallet Balance: Rs." + balance);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching Wallet Balance: " + e.getMessage());
        }
    }
    public static void rechargeWallet(Scanner sc, String username) {
        Double amountInput = readPositiveDouble(sc, "\nEnter amount to recharge in FlicPass Wallet (Rs.): ");
        if (amountInput == null) return;
        double amount = amountInput;

        System.out.println("\nSelect Payment Source for Wallet Recharge:");
        System.out.println("1. UPI Payment (GPay / PhonePe / Paytm)");
        System.out.println("2. Credit / Debit Card");
        Integer choice = readPositiveInt(sc, "Enter Choice: ");
        if (choice == null || (choice != 1 && choice != 2)) {
            System.out.println("Recharge cancelled.");
            return;
        }

        if (choice == 1) {
            while (true) {
                System.out.print("Enter UPI ID: ");
                String upi = sc.nextLine().trim();
                if (!upi.contains("@")) {
                    System.out.println("Invalid UPI ID. Please try again.");
                } else {
                    break;
                }
            }
        } else {
            while (true) {
                System.out.print("Enter 16-digit Card Number: ");
                String card = sc.nextLine().trim();
                if (card.length() != 16 || !card.matches("\\d+")) {
                    System.out.println("Invalid Card Number. It must be exactly 16 digits. Please try again.");
                } else {
                    break;
                }
            }
        }

        String updateSql = "UPDATE users SET wallet_balance = wallet_balance + ? WHERE username = ?";
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setDouble(1, amount);
                pstmt.setString(2, username);
                int rows = pstmt.executeUpdate();
                if (rows > 0) {
                    System.out.println("Recharge Successful! Rs." + amount + " credited to your FlicPass Wallet.");
                    viewWalletBalance(username);
                }
            }
        } catch (SQLException e) {
            System.out.println("Recharge Error: " + e.getMessage());
        }
    }

    public static double getShowPrice(Connection conn, int showId) throws SQLException {
        if (showPriceCache.containsKey(showId)) {
            return showPriceCache.get(showId);
        }

        String query = "SELECT base_price FROM shows WHERE show_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, showId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    double price = rs.getDouble("base_price");
                    showPriceCache.put(showId, price);
                    return price;
                }
            }
        }
        return 0.0;
    }

    public static String getMovieTitle(int movieId) {
        if (movieTitleCache.containsKey(movieId)) {
            return movieTitleCache.get(movieId);
        }

        String query = "SELECT title FROM movies WHERE movie_id = ?";
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return null;
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, movieId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String title = rs.getString("title");
                        movieTitleCache.put(movieId, title);
                        return title;
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching movie title: " + e.getMessage());
        }
        return null;
    }

    public static void selectSeats(Scanner sc, String username, int showId, String format, String language) {
        String seatQuery = "SELECT MIN(ss.seat_id) AS show_seat_id, s.seat_number, MAX(s.seat_type) AS seat_type, MAX(CAST(ss.is_booked AS UNSIGNED)) AS is_booked " +
                "FROM show_seats ss " +
                "JOIN shows sh ON ss.show_id = sh.show_id " +
                "JOIN seats s ON ss.seat_id = s.seat_id AND s.screen_id = sh.screen_id " +
                "WHERE ss.show_id = ? " +
                "GROUP BY s.seat_number " +
                "ORDER BY SUBSTRING(s.seat_number, 1, 1), CAST(SUBSTRING(s.seat_number, 2) AS UNSIGNED)";

        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;

            PreparedStatement pstmt = conn.prepareStatement(seatQuery);
            try (pstmt) {
                pstmt.setInt(1, showId);

                boolean hasSeats = false;
                int availableSeats = 0;
                List<String> seatNumbers = new ArrayList<>();
                List<Boolean> bookedStatus = new ArrayList<>();

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        hasSeats = true;
                        seatNumbers.add(rs.getString("seat_number"));
                        boolean booked = rs.getInt("is_booked") > 0;
                        bookedStatus.add(booked);
                        if (!booked) availableSeats++;
                    }
                }

                if (!hasSeats) {
                    int screenId = -1;
                    try (PreparedStatement screenStmt = conn.prepareStatement("SELECT screen_id FROM shows WHERE show_id = ?")) {
                        screenStmt.setInt(1, showId);
                        try (ResultSet srs = screenStmt.executeQuery()) {
                            if (srs.next()) screenId = srs.getInt("screen_id");
                        }
                    }

                    if (screenId != -1) {
                        boolean screenHasSeats = false;
                        try (PreparedStatement checkStmt = conn.prepareStatement("SELECT 1 FROM seats WHERE screen_id = ? LIMIT 1")) {
                            checkStmt.setInt(1, screenId);
                            try (ResultSet srs = checkStmt.executeQuery()) {
                                if (srs.next()) screenHasSeats = true;
                            }
                        }
                        if (!screenHasSeats) {
                            String genSeats = "INSERT INTO seats (screen_id, seat_number, seat_type) VALUES (?, ?, 'Silver')";
                            try (PreparedStatement genStmt = conn.prepareStatement(genSeats)) {
                                char[] rows = {'A', 'B', 'C', 'D', 'E'};
                                for (char r : rows) {
                                    for (int c = 1; c <= 10; c++) {
                                        genStmt.setInt(1, screenId);
                                        genStmt.setString(2, r + String.valueOf(c));
                                        genStmt.addBatch();
                                    }
                                }
                                genStmt.executeBatch();
                            }
                        }
                        String fixSeatsQuery = "INSERT IGNORE INTO show_seats (show_id, seat_id, is_booked) " +
                                "SELECT ?, seat_id, FALSE FROM seats WHERE screen_id = ?";
                        try (PreparedStatement fixStmt = conn.prepareStatement(fixSeatsQuery)) {
                            fixStmt.setInt(1, showId);
                            fixStmt.setInt(2, screenId);
                            fixStmt.executeUpdate();
                        }
                        try (ResultSet rs2 = pstmt.executeQuery()) {
                            while (rs2.next()) {
                                hasSeats = true;
                                seatNumbers.add(rs2.getString("seat_number"));
                                boolean booked = rs2.getInt("is_booked") > 0;
                                bookedStatus.add(booked);
                                if (!booked) availableSeats++;
                            }
                        }
                    }

                    if (!hasSeats) {
                        System.out.println("Seats not configured for this show. Please ask Admin to configure screen seats.");
                        return;
                    }
                }


                if (availableSeats == 0) {
                    System.out.println("All seats are booked for this show.");
                    return;
                }

                int ticketCount;
                while (true) {
                    Integer ticketCountInput = readPositiveInt(sc, "\nHow many tickets do you want? ");
                    if (ticketCountInput != null) {
                        ticketCount = ticketCountInput;
                        break;
                    }
                }

                if (ticketCount > availableSeats) {
                    System.out.println("Only " + availableSeats + " seat(s) are available for this show.");
                    return;
                }

                System.out.println("\n==================== SCREEN ====================");
                System.out.println("                    ALL EYES HERE               ");
                System.out.println("================================================");

                String currentRow = "";
                for (int i = 0; i < seatNumbers.size(); i++) {
                    String seat = seatNumbers.get(i);
                    boolean booked = bookedStatus.get(i);
                    String row = seat.substring(0, 1);

                    if (!row.equals(currentRow)) {
                        if (!currentRow.equals("")) System.out.println();
                        System.out.print(row + "  ");
                        currentRow = row;
                    }

                    String display = booked ? "XX" : seat;
                    System.out.print("[" + display + "] ");
                }

                System.out.println();
                System.out.println("\nLegend: [A1] Available   [XX] Booked");
                System.out.println("Available seats: " + availableSeats);

                int[] showSeatIds;
                LinkedHashSet<String> uniqueSeats;
                String seatQuery2 =
                        "SELECT ss.seat_id AS show_seat_id, ss.is_booked " +
                                "FROM show_seats ss " +
                                "JOIN shows sh ON ss.show_id = sh.show_id " +
                                "JOIN seats s ON ss.seat_id = s.seat_id AND s.screen_id = sh.screen_id " +
                                "WHERE ss.show_id=? AND UPPER(s.seat_number)=?";

                while (true) {
                    System.out.println();
                    System.out.print("Enter exactly " + ticketCount + " seat number(s) (Example: A1 A2), or D for Dashboard: ");

                    String input = sc.nextLine().trim();
                    if (input.equalsIgnoreCase("D")) {
                        System.out.println("Returning to dashboard.");
                        return;
                    }
                    if (input.isEmpty()) {
                        System.out.println("No seats selected. Please select seats again or enter D for Dashboard.");
                        continue;
                    }

                    String[] selectedSeats = input.split("\\s+");
                    uniqueSeats = new LinkedHashSet<>();
                    for (String seat : selectedSeats) {
                        uniqueSeats.add(seat.toUpperCase());
                    }
                    if (uniqueSeats.size() != selectedSeats.length) {
                        System.out.println("Duplicate seat numbers are not allowed. Please select seats again or enter D for Dashboard.");
                        continue;
                    }

                    if (uniqueSeats.size() != ticketCount) {
                        System.out.println("You selected " + uniqueSeats.size() + " seat(s), but ticket count is " + ticketCount + ".");
                        System.out.println("Please select exactly " + ticketCount + " seat(s), or enter D for Dashboard.");
                        continue;
                    }

                    showSeatIds = new int[uniqueSeats.size()];
                    boolean validSeats = true;
                    try (PreparedStatement seatStmt = conn.prepareStatement(seatQuery2)) {
                        int i = 0;
                        for (String seatNumber : uniqueSeats) {
                            seatStmt.setInt(1, showId);
                            seatStmt.setString(2, seatNumber);

                            try (ResultSet seatRs = seatStmt.executeQuery()) {
                                if (seatRs.next()) {
                                    if (seatRs.getBoolean("is_booked")) {
                                        System.out.println("Seat " + seatNumber + " is already booked. Please select seats again or enter D for Dashboard.");
                                        validSeats = false;
                                        break;
                                    }

                                    showSeatIds[i++] = seatRs.getInt("show_seat_id");
                                } else {
                                    System.out.println("Seat " + seatNumber + " not found. Please select seats again or enter D for Dashboard.");
                                    validSeats = false;
                                    break;
                                }
                            }
                        }
                    }

                    if (validSeats) break;
                }

                double totalAmount = getShowPrice(conn, showId) * ticketCount;
                int coinsToRedeem = 0;
                int userId = getUserId(conn, username);
                int coinBalance = userId == -1 ? 0 : getFlicpassCoins(conn, userId);
                System.out.println("\nBooking Summary");
                System.out.println("Show ID: " + showId);
                System.out.println("Seats: " + String.join(", ", uniqueSeats));
                System.out.println("Tickets: " + ticketCount);
                System.out.println("Total Amount: Rs." + totalAmount);
                System.out.println("FlicPass Coins: " + coinBalance + " (1 coin = Rs.1)");
                if (coinBalance >= 50) {
                    int maxRedeemableCoins = (int) Math.min(coinBalance, totalAmount);
                    System.out.print("Use FlicPass Coins for discount? You can use up to " +
                            maxRedeemableCoins + " coin(s). (y/n): ");
                    String useCoins = sc.nextLine().trim();
                    if (useCoins.equalsIgnoreCase("y")) {
                        Integer coinsInput = readPositiveInt(sc, "Enter coins to redeem: ");
                        if (coinsInput == null) return;
                        if (coinsInput > maxRedeemableCoins) {
                            System.out.println("You can redeem only up to " + maxRedeemableCoins + " coin(s).");
                            return;
                        }
                        coinsToRedeem = coinsInput;
                        System.out.println("Discount Applied: Rs." + coinsToRedeem);
                        System.out.println("Payable Amount: Rs." + (totalAmount - coinsToRedeem));
                    }
                } else {
                    System.out.println("Discount unlocks at 50 FlicPass Coins.");
                }
                System.out.print("Proceed to Payment? (y/n): ");
                String confirm = sc.nextLine().trim();
                if (!confirm.equalsIgnoreCase("y")) {
                    System.out.println("Booking cancelled before payment.");
                    return;
                }

                confirmBooking(sc, username, showId, showSeatIds, coinsToRedeem, format, language);
            }

        } catch (SQLException e) {
            System.out.println("Error fetching seats: " + e.getMessage());
        }
    }

    public static String loginUser(Scanner sc) {
        System.out.print("Enter Username: ");
        String username = sc.nextLine().trim();
        System.out.print("Enter Password: ");
        String password = sc.nextLine().trim();

        String query = "SELECT * FROM users WHERE username = ? AND password_hash = ? AND role = 'Customer'";

        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return null;
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, username);
                pstmt.setString(2, password);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("Login Successful! Welcome back, " + username + ".");
                        return username;
                    } else {
                        System.out.println("Invalid Username or Password.");
                        return null;
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Login Error: " + e.getMessage());
            return null;
        }
    }
    public static void bookTickets(Scanner sc, String username) {
        if (!viewMovies()) return;

        int movieId;
        while (true) {
            Integer movieIdInput = readPositiveInt(sc, "\nEnter Movie ID to book: ");
            if (movieIdInput == null) continue;
            movieId = movieIdInput;

            String movieTitle = getMovieTitle(movieId);
            if (movieTitle != null) {
                recentStack.remove(movieTitle);
                recentStack.push(movieTitle);
                if (recentStack.size() > 5) recentStack.remove(0);
                break;
            } else {
                System.out.println("Invalid movie selected. Try again.");
            }
        }
        String movieTitle = getMovieTitle(movieId);

        String selectedLanguage = "Unknown";
        String selectedFormat;

        while (true) {
            System.out.println("\n----- SELECT FORMAT -----");
            System.out.println("1. 2D");
            System.out.println("2. 3D");
            System.out.println("3. IMAX");
            Integer formatInput = readPositiveInt(sc, "Select Format Option (1-3): ");
            if (formatInput == null || formatInput < 1 || formatInput > 3) {
                System.out.println("Invalid format choice. Try again.");
                continue;
            }
            String[] formats = {"2D", "3D", "IMAX"};
            selectedFormat = formats[formatInput - 1];
            break;
        }

        List<LocalDate> availableDates = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            String langQuery = "SELECT DISTINCT m2.language FROM movies m1 " +
                    "JOIN movies m2 ON LOWER(m1.title) = LOWER(m2.title) " +
                    "JOIN shows s ON s.movie_id = m2.movie_id " +
                    "WHERE m1.movie_id = ? AND m2.language IS NOT NULL " +
                    "AND (s.show_date > ? OR (s.show_date = ? AND s.show_time > ?))";
            List<String> availLangs = new ArrayList<>();
            try (PreparedStatement langStmt = conn.prepareStatement(langQuery)) {
                langStmt.setInt(1, movieId);
                langStmt.setDate(2, java.sql.Date.valueOf(LocalDate.now()));
                langStmt.setDate(3, java.sql.Date.valueOf(LocalDate.now()));
                langStmt.setTime(4, java.sql.Time.valueOf(LocalTime.now()));
                try (ResultSet rs = langStmt.executeQuery()) {
                    while (rs.next()) availLangs.add(rs.getString("language"));
                }
            }
            if (!availLangs.isEmpty()) {
                while (true) {
                    System.out.println("\n----- SELECT LANGUAGE -----");
                    for (int i = 0; i < availLangs.size(); i++) {
                        System.out.println((i + 1) + ". " + availLangs.get(i));
                    }
                    Integer langInput = readPositiveInt(sc, "Select Language Option (1 to " + availLangs.size() + "): ");
                    if (langInput == null || langInput < 1 || langInput > availLangs.size()) {
                        System.out.println("Invalid language choice. Try again.");
                        continue;
                    }
                    selectedLanguage = availLangs.get(langInput - 1);
                    break;
                }
                String getCorrectMovieIdQuery = "SELECT m2.movie_id FROM movies m1 JOIN movies m2 ON LOWER(m1.title) = LOWER(m2.title) WHERE m1.movie_id = ? AND m2.language = ?";
                try (PreparedStatement gStmt = conn.prepareStatement(getCorrectMovieIdQuery)) {
                    gStmt.setInt(1, movieId);
                    gStmt.setString(2, selectedLanguage);
                    try (ResultSet grs = gStmt.executeQuery()) {
                        if (grs.next()) {
                            movieId = grs.getInt("movie_id");
                        }
                    }
                }
            }
            String dateQuery = "SELECT DISTINCT s.show_date FROM shows s " +
                    "WHERE s.movie_id = ? AND (s.show_date > ? OR (s.show_date = ? AND s.show_time > ?)) " +
                    "ORDER BY s.show_date";

            try (PreparedStatement dStmt = conn.prepareStatement(dateQuery)) {
                dStmt.setInt(1, movieId);
                dStmt.setDate(2, java.sql.Date.valueOf(LocalDate.now()));
                dStmt.setDate(3, java.sql.Date.valueOf(LocalDate.now()));
                dStmt.setTime(4, java.sql.Time.valueOf(LocalTime.now()));
                try (ResultSet rs = dStmt.executeQuery()) {
                    while (rs.next()) {
                        availableDates.add(rs.getDate("show_date").toLocalDate());
                    }
                }
            }
            if (availableDates.isEmpty()) {
                autoGenerateShowsForMovie(conn, movieId, selectedFormat);
                try (PreparedStatement dStmt = conn.prepareStatement(dateQuery)) {
                    dStmt.setInt(1, movieId);
                    dStmt.setDate(2, java.sql.Date.valueOf(LocalDate.now()));
                    dStmt.setDate(3, java.sql.Date.valueOf(LocalDate.now()));
                    dStmt.setTime(4, java.sql.Time.valueOf(LocalTime.now()));
                    try (ResultSet rs = dStmt.executeQuery()) {
                        while (rs.next()) {
                            availableDates.add(rs.getDate("show_date").toLocalDate());
                        }
                    }
                }
            }

        } catch (SQLException e) {
            System.out.println("Error fetching show dates: " + e.getMessage());
            return;
        }

        if (availableDates.isEmpty()) {
            System.out.println("No upcoming dates or shows available for " + movieTitle + ".");
            return;
        }

        System.out.println("\n----- SELECT DATE TO WATCH MOVIE (" + movieTitle + ") -----");
        for (int i = 0; i < availableDates.size(); i++) {
            LocalDate d = availableDates.get(i);
            String dayTag = "";
            if (d.equals(LocalDate.now())) dayTag = " (Today)";
            else if (d.equals(LocalDate.now().plusDays(1))) dayTag = " (Tomorrow)";

            System.out.println((i + 1) + ". " + d + dayTag);
        }

        LocalDate selectedDate;
        while (true) {
            Integer dateChoiceInput = readPositiveInt(sc, "Select Date Option (1 to " + availableDates.size() + "): ");
            if (dateChoiceInput == null || dateChoiceInput < 1 || dateChoiceInput > availableDates.size()) {
                System.out.println("Invalid date choice. Try again.");
                continue;
            }
            selectedDate = availableDates.get(dateChoiceInput - 1);
            break;
        }
        System.out.println("\nSelected Date: " + selectedDate);
        String theaterQuery = "SELECT DISTINCT t.theater_id, t.name, t.city, t.address " +
                "FROM shows s " +
                "JOIN screens sc ON s.screen_id = sc.screen_id " +
                "JOIN theaters t ON sc.theater_id = t.theater_id " +
                "WHERE s.movie_id = ? AND s.show_date = ? " +
                "ORDER BY t.name";

        class TheaterOption {
            int id;
            String name;
            String city;
            String address;
            TheaterOption(int id, String name, String city, String address) {
                this.id = id; this.name = name; this.city = city; this.address = address;
            }
        }

        List<TheaterOption> availableTheaters = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement tStmt = conn.prepareStatement(theaterQuery)) {
                tStmt.setInt(1, movieId);
                tStmt.setDate(2, java.sql.Date.valueOf(selectedDate));
                try (ResultSet rs = tStmt.executeQuery()) {
                    while (rs.next()) {
                        availableTheaters.add(new TheaterOption(
                                rs.getInt("theater_id"),
                                rs.getString("name"),
                                rs.getString("city"),
                                rs.getString("address")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching theaters: " + e.getMessage());
            return;
        }

        if (availableTheaters.isEmpty()) {
            System.out.println("No theaters available on " + selectedDate + " for " + movieTitle + ".");
            return;
        }

        System.out.println("\n----- SELECT THEATER / CINEMA FOR " + selectedDate + " -----");
        for (int i = 0; i < availableTheaters.size(); i++) {
            TheaterOption t = availableTheaters.get(i);
            String addr = (t.address != null && !t.address.isEmpty()) ? " - " + t.address : "";
            System.out.println((i + 1) + ". " + t.name + " (" + t.city + addr + ")");
        }

        TheaterOption selectedTheater;
        while (true) {
            Integer theaterChoiceInput = readPositiveInt(sc, "Select Theater Option (1 to " + availableTheaters.size() + "): ");
            if (theaterChoiceInput == null || theaterChoiceInput < 1 || theaterChoiceInput > availableTheaters.size()) {
                System.out.println("Invalid theater choice. Try again.");
                continue;
            }
            selectedTheater = availableTheaters.get(theaterChoiceInput - 1);
            break;
        }
        System.out.println("\nSelected Theater: " + selectedTheater.name);
        String query = "SELECT s.show_id, s.show_date, s.show_time, s.base_price, " +
                "m.language, t.name AS theater_name, sc.screen_number " +
                "FROM shows s " +
                "JOIN movies m ON s.movie_id = m.movie_id " +
                "JOIN screens sc ON s.screen_id = sc.screen_id " +
                "JOIN theaters t ON sc.theater_id = t.theater_id " +
                "WHERE s.movie_id = ? AND s.show_date = ? AND t.theater_id = ? " +
                "AND (s.show_date > ? OR (s.show_date = ? AND s.show_time > ?)) " +
                "ORDER BY s.show_time";

        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, movieId);
                pstmt.setDate(2, java.sql.Date.valueOf(selectedDate));
                pstmt.setInt(3, selectedTheater.id);
                pstmt.setDate(4, java.sql.Date.valueOf(LocalDate.now()));
                pstmt.setDate(5, java.sql.Date.valueOf(LocalDate.now()));
                pstmt.setTime(6, java.sql.Time.valueOf(LocalTime.now()));
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

                System.out.println("\n----- SHOW TIMES AT " + selectedTheater.name.toUpperCase() + " FOR " + selectedDate + " -----");
                System.out.println("No. | Time       | Screen     | Price");
                System.out.println("---------------------------------------");

                List<Integer> availableShowIds = new ArrayList<>();
                try (ResultSet rs = pstmt.executeQuery()) {
                    int count = 1;
                    while (rs.next()) {
                        availableShowIds.add(rs.getInt("show_id"));
                        LocalTime showTime = rs.getTime("show_time").toLocalTime();

                        String cStr = String.valueOf(count);
                        String tStr = showTime.format(timeFormatter);
                        String sStr = "Screen " + rs.getInt("screen_number");
                        String pStr = "Rs." + rs.getDouble("base_price");
                        System.out.println(cStr + getSpaces(3 - cStr.length()) + " | " +
                                tStr + getSpaces(10 - tStr.length()) + " | " +
                                sStr + getSpaces(10 - sStr.length()) + " | " + pStr);
                        count++;
                    }
                }

                if (availableShowIds.isEmpty()) {
                    System.out.println("No shows available at " + selectedTheater.name + " on " + selectedDate + ".");
                    return;
                }

                int showId;
                while (true) {
                    Integer showChoiceInput = readPositiveInt(sc, "\nSelect Show Time Option (1 to " + availableShowIds.size() + "): ");
                    if (showChoiceInput == null || showChoiceInput < 1 || showChoiceInput > availableShowIds.size()) {
                        System.out.println("Invalid show choice. Try again.");
                        continue;
                    }
                    showId = availableShowIds.get(showChoiceInput - 1);
                    break;
                }
                selectSeats(sc, username, showId, selectedFormat, selectedLanguage);
            }

        } catch (SQLException e) {
            System.out.println("Error fetching show times: " + e.getMessage());
        }
    }
    public static void cancelBooking(Scanner sc, String username) {
        viewMyBookings(username);

        Integer bookingIdInput = readPositiveInt(sc, "\nEnter Booking ID to cancel: ");
        if (bookingIdInput == null) return;
        int bookingId = bookingIdInput;

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            if (!isConnectionAvailable(conn)) return;
            try (Statement fixStmt = conn.createStatement()) {
                fixStmt.executeUpdate("ALTER TABLE payments MODIFY COLUMN payment_status VARCHAR(20) DEFAULT 'Success'");
            } catch (SQLException ex) {
            }

            conn.setAutoCommit(false);

            String checkQuery = "SELECT b.booking_status, b.total_tickets, b.total_amount, u.user_id, s.show_date, s.show_time " +
                    "FROM bookings b " +
                    "JOIN users u ON b.user_id = u.user_id " +
                    "JOIN shows s ON b.show_id = s.show_id " +
                    "WHERE b.booking_id = ? AND u.username = ?";
            String status = null;
            int totalTickets = 0;
            double paidAmount = 0.0;
            int userId = -1;
            LocalDate showDate = null;
            LocalTime showTime = null;

            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                checkStmt.setInt(1, bookingId);
                checkStmt.setString(2, username);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        status = rs.getString("booking_status");
                        totalTickets = rs.getInt("total_tickets");
                        paidAmount = rs.getDouble("total_amount");
                        userId = rs.getInt("user_id");
                        showDate = rs.getDate("show_date").toLocalDate();
                        showTime = rs.getTime("show_time").toLocalTime();
                    }
                }
            }

            if (status == null) {
                System.out.println("Booking not found or doesn't belong to you.");
                conn.rollback();
                return;
            }
            if (status.equals("Cancelled")) {
                System.out.println("This booking is already cancelled.");
                conn.rollback();
                return;
            }
            LocalDateTime showDateTime = LocalDateTime.of(showDate, showTime);
            LocalDateTime now = LocalDateTime.now();

            boolean eligibleForRefund = now.isBefore(showDateTime.minusHours(2));
            double refundAmount = 0.0;

            if (eligibleForRefund) {
                refundAmount = paidAmount * 0.75; // 75% Refund Policy
            }
            String updateBooking = "UPDATE bookings SET booking_status = 'Cancelled' WHERE booking_id = ?";
            try (PreparedStatement uStmt = conn.prepareStatement(updateBooking)) {
                uStmt.setInt(1, bookingId);
                uStmt.executeUpdate();
            }
            String freeSeat = "UPDATE show_seats SET is_booked = FALSE, booking_id = NULL WHERE booking_id = ?";
            try (PreparedStatement fStmt = conn.prepareStatement(freeSeat)) {
                fStmt.setInt(1, bookingId);
                fStmt.executeUpdate();
            }
            int coinsToDeduct = totalTickets * 10;
            String deductCoins = "UPDATE users SET flicpass_coins = GREATEST(flicpass_coins - ?, 0) WHERE user_id = ?";
            try (PreparedStatement coinStmt = conn.prepareStatement(deductCoins)) {
                coinStmt.setInt(1, coinsToDeduct);
                coinStmt.setInt(2, userId);
                coinStmt.executeUpdate();
            }
            if (refundAmount > 0) {
                String creditWallet = "UPDATE users SET wallet_balance = wallet_balance + ? WHERE user_id = ?";
                try (PreparedStatement wStmt = conn.prepareStatement(creditWallet)) {
                    wStmt.setDouble(1, refundAmount);
                    wStmt.setInt(2, userId);
                    wStmt.executeUpdate();
                }
                String refundPay = "INSERT INTO payments (booking_id, payment_mode, amount, transaction_id, payment_status) VALUES (?, 'FlicPass Wallet Refund', ?, ?, 'Refunded')";
                try (PreparedStatement pStmt = conn.prepareStatement(refundPay)) {
                    pStmt.setInt(1, bookingId);
                    pStmt.setDouble(2, refundAmount);
                    pStmt.setString(3, "REFUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                    pStmt.executeUpdate();
                }
            }

            int updatedCoinBalance = getFlicpassCoins(conn, userId);

            conn.commit(); // Transaction Commit

            notificationLog.addLast(username + " cancelled booking " + bookingId);
            notificationBuffer.produce("Booking ID " + bookingId + " cancelled. Refund credited: Rs." + refundAmount);

            System.out.println("\nBooking Cancelled Successfully!");
            System.out.println("Seats Freed        : " + totalTickets + " seat(s) are available again.");
            System.out.println("FlicPass Coins     : -" + coinsToDeduct + " coins deducted.");

            if (refundAmount > 0) {
                System.out.println("75% Refund Status  : SUCCESS! Rs." + refundAmount + " has been credited to your FlicPass Wallet.");
            } else {
                System.out.println("75% Refund Status  : NO REFUND CREDITED (Cancellation requested within 2 hours of show time).");
            }

        } catch (SQLException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.out.println("Cancellation Failed: " + e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    public static void showCustomerDashboard(Scanner sc, String username) {
        while (true) {
            System.out.println("\n----- CUSTOMER DASHBOARD -----");
            System.out.println("1. Booking");
            System.out.println("2. Movies & Analytics");
            System.out.println("3. Account & Wallet");
            System.out.println("4. Support");
            System.out.println("5. Logout");
            Integer choiceInput = readInt(sc, "Enter your choice: ");
            if (choiceInput == null) continue;

            switch (choiceInput) {
                case 1: showBookingMenu(sc, username); break;
                case 2: showMovieMenu(sc, username); break;
                case 3: showAccountMenu(sc, username); break;
                case 4: showSupportMenu(sc, username); break;
                case 5:
                    System.out.println("Logging out... See you soon!");
                    return;
                default: System.out.println("Invalid choice! Try again.");
            }
        }
    }

    private static void showBookingMenu(Scanner sc, String username) {
        while (true) {
            System.out.println("\n----- BOOKING MENU -----");
            System.out.println("1. Book Tickets");
            System.out.println("2. View My Bookings");
            System.out.println("3. Cancel Booking (75% Refund to Wallet)");
            System.out.println("4. Back");
            Integer choiceInput = readInt(sc, "Enter your choice: ");
            if (choiceInput == null) continue;
            switch (choiceInput) {
                case 1: bookTickets(sc, username); break;
                case 2: viewMyBookings(username); break;
                case 3: cancelBooking(sc, username); break;
                case 4: return;
                default: System.out.println("Invalid choice!");
            }
        }
    }

    private static void showMovieMenu(Scanner sc, String username) {
        while (true) {
            System.out.println("\n----- MOVIE MENU -----");
            System.out.println("1. View Movies");
            System.out.println("2. Search Movies by Genre");
            System.out.println("3. Sort & Analyze Movies (Collections Utility)");
            System.out.println("4. Write Review");
            System.out.println("5. View Movie Reviews");
            System.out.println("6. Movie Details TXT");
            System.out.println("7. Unique Genres");
            System.out.println("8. Most Booked Movie");
            System.out.println("9. Recently Viewed Movies");
            System.out.println("10. Back");
            Integer choiceInput = readInt(sc, "Enter your choice: ");
            if (choiceInput == null) continue;
            switch (choiceInput) {
                case 1: viewMovies(); break;
                case 2: searchMoviesByGenre(sc); break;
                case 3: sortAndAnalyzeMovies(); break;
                case 4: giveReview(sc, username); break;
                case 5:
                    viewMovies();
                    Integer movieIdInput = readPositiveInt(sc, "\nEnter Movie ID to View Reviews: ");
                    if (movieIdInput != null) viewMovieReviews(movieIdInput);
                    break;
                case 6: createAndViewMovieDetailsFile(sc); break;
                case 7: viewUniqueGenres(); break;
                case 8: mostBookedMovie(); break;
                case 9: viewRecentlyViewedMovies(); break;
                case 10: return;
                default: System.out.println("Invalid choice!");
            }
        }
    }

    public static void sortAndAnalyzeMovies() {
        String query = "SELECT MIN(movie_id) as movie_id, title, MAX(genre) as genre, GROUP_CONCAT(DISTINCT language SEPARATOR ', ') as language, MAX(duration_minutes) as duration_minutes, MAX(release_date) as release_date FROM movies GROUP BY title";
        List<Movie> movieList = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement pstmt = conn.prepareStatement(query);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    movieList.add(new Movie(rs.getInt("movie_id"), rs.getString("title"),
                            rs.getString("genre"), rs.getString("language"),
                            rs.getInt("duration_minutes"), rs.getDate("release_date").toLocalDate()));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching movies: " + e.getMessage());
            return;
        }

        if (movieList.isEmpty()) {
            System.out.println("No movies available.");
            return;
        }
        System.out.println("\n----- MOVIES SORTED BY DURATION (Ascending) -----");
        System.out.println("Title                                    | Duration   | Release Date");
        System.out.println("----------------------------------------------------------------------");
        Collections.sort(movieList, Comparator.comparing(Movie::getDuration));
        for (Movie m : movieList) {
            String titleStr = m.title;
            if (titleStr.length() > 40) titleStr = titleStr.substring(0, 37) + "...";
            String durStr = m.duration + " mins";
            String relStr = m.releaseDate.toString();
            System.out.println(titleStr + getSpaces(40 - titleStr.length()) + " | " +
                    durStr + getSpaces(10 - durStr.length()) + " | " + relStr);
        }

        if (!showPriceCache.isEmpty()) {
            double maxPrice = Collections.max(showPriceCache.values());
            double minPrice = Collections.min(showPriceCache.values());
            System.out.println("\n----- SHOW PRICE CACHE ANALYTICS -----");
            System.out.println("Maximum Show Base Price: Rs." + maxPrice);
            System.out.println("Minimum Show Base Price: Rs." + minPrice);
        }
    }

    private static void showAccountMenu(Scanner sc, String username) {
        while (true) {
            System.out.println("\n----- ACCOUNT & WALLET MENU -----");
            System.out.println("1. View FlicPass Coins");
            System.out.println("2. View FlicPass Wallet Balance");
            System.out.println("3. Recharge FlicPass Wallet");
            System.out.println("4. Back");
            Integer choiceInput = readInt(sc, "Enter your choice: ");
            if (choiceInput == null) continue;
            switch (choiceInput) {
                case 1: viewFlicpassCoins(username); break;
                case 2: viewWalletBalance(username); break;
                case 3: rechargeWallet(sc, username); break;
                case 4: return;
                default: System.out.println("Invalid choice!");
            }
        }
    }

    private static void showSupportMenu(Scanner sc, String username) {
        while (true) {
            System.out.println("\n----- SUPPORT MENU -----");
            System.out.println("1. View Theaters");
            System.out.println("2. Request a Movie");
            System.out.println("3. Back");
            Integer choiceInput = readInt(sc, "Enter your choice: ");
            if (choiceInput == null) continue;
            switch (choiceInput) {
                case 1: viewTheaters(); break;
                case 2: requestMovie(sc, username); break;
                case 3: return;
                default: System.out.println("Invalid choice!");
            }
        }
    }

    private static void viewRecentlyViewedMovies() {
        if (recentStack.empty()) {
            System.out.println("No recently viewed movies yet.");
            return;
        }

        System.out.println("\n----- RECENTLY VIEWED MOVIES -----");
        System.out.println("Most recent: " + recentStack.peek());
        for (int i = recentStack.size() - 1; i >= 0; i--) {
            System.out.println((recentStack.size() - i) + ". " + recentStack.get(i));
        }
    }

    public static void searchMoviesByGenre(Scanner sc) {
        System.out.print("Enter Genre to search: ");
        String genre = sc.nextLine();

        String query = "SELECT MIN(movie_id) as movie_id, title, MAX(genre) as genre, GROUP_CONCAT(DISTINCT language SEPARATOR ', ') as language, MAX(duration_minutes) as duration_minutes FROM movies GROUP BY title";
        List<Movie> allMovies = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement pstmt = conn.prepareStatement(query);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    allMovies.add(new Movie(rs.getInt("movie_id"), rs.getString("title"),
                            rs.getString("genre"), rs.getString("language"), rs.getInt("duration_minutes")));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }
        Predicate<Movie> genreFilter = m -> m.genre != null && m.genre.equalsIgnoreCase(genre);

        System.out.println("\n----- MOVIES IN GENRE: " + genre + " -----");
        System.out.println("ID   | Title                                    | Languages                      | Duration");
        System.out.println("-----------------------------------------------------------------------------------------");
        boolean found = false;
        for (Movie m : allMovies) {
            if (genreFilter.test(m)) {
                found = true;
                String idStr = String.valueOf(m.movieId);
                String titleStr = m.title;
                if (titleStr.length() > 40) titleStr = titleStr.substring(0, 37) + "...";
                String langStr = m.language;
                String durStr = m.duration + " mins";
                System.out.println(idStr + getSpaces(4 - idStr.length()) + " | " +
                        titleStr + getSpaces(40 - titleStr.length()) + " | " +
                        langStr + getSpaces(30 - langStr.length()) + " | " + durStr);
            }
        }
        if (!found) System.out.println("No movies found in this genre.");
    }

    public static void requestMovie(Scanner sc, String username) {
        System.out.print("Enter the movie name you want us to add: ");
        String movieName = sc.nextLine();
        movieRequests.offerLast(username + " requested: " + movieName);
        System.out.println("Thanks! Your request has been submitted to the admin.");
    }

    public static void giveReview(Scanner sc, String username) {
        if (!viewMovies()) return;

        Integer movieIdInput = readPositiveInt(sc, "\nEnter Movie ID to review: ");
        if (movieIdInput == null) return;
        int movieId = movieIdInput;

        System.out.println("\n========== REVIEWER TYPE ==========");
        System.out.println("1. Normal Audience");
        System.out.println("2. Cinephile");
        Integer reviewerChoiceInput = readPositiveInt(sc, "Enter Choice: ");
        if (reviewerChoiceInput == null) return;

        String reviewerType = reviewerChoiceInput == 1 ? "Normal Audience" : "Cinephile";

        System.out.println("\n========== MOVIE RATING ==========");
        System.out.println("1. Skip  2. Watchable  3. Good  4. Definitely Watch  5. True Masterpiece");
        Integer ratingChoiceInput = readPositiveInt(sc, "Enter Choice: ");
        if (ratingChoiceInput == null) return;

        String[] ratings = {"Skip", "Watchable", "Good", "Definitely Watch", "True Masterpiece"};
        if (ratingChoiceInput < 1 || ratingChoiceInput > 5) {
            System.out.println("Invalid Rating!");
            return;
        }
        String rating = ratings[ratingChoiceInput - 1];

        try {
            System.out.print("Enter your review: ");
            String reviewText = sc.nextLine();

            try (Connection conn = DBConnection.getConnection()) {
                if (!isConnectionAvailable(conn)) return;
                int userId = getUserId(conn, username);
                if (userId == -1) return;

                String query = "INSERT INTO reviews_ratings (movie_id, user_id, reviewer_type, rating, review_text) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setInt(1, movieId);
                    pstmt.setInt(2, userId);
                    pstmt.setString(3, reviewerType);
                    pstmt.setDouble(4, ratingChoiceInput);
                    pstmt.setString(5, reviewText);
                    pstmt.executeUpdate();
                    System.out.println("Review submitted successfully!");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error submitting review: " + e.getMessage());
        }
    }

    public static void viewMovieReviews(int movieId) {
        String query = "SELECT u.username, r.reviewer_type, r.rating, r.review_text " +
                "FROM reviews_ratings r " +
                "JOIN users u ON r.user_id = u.user_id " +
                "WHERE r.movie_id = ?";
        List<ReviewData> reviewList = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, movieId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        reviewList.add(new ReviewData(
                                rs.getString("username"),
                                rs.getString("reviewer_type"),
                                rs.getString("rating"),
                                rs.getString("review_text")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching reviews: " + e.getMessage());
            return;
        }

        if (reviewList.isEmpty()) {
            System.out.println("No reviews available for this movie.");
            return;
        }

        System.out.println("\n============== USER REVIEWS ==============");
        for (ReviewData r : reviewList) {
            System.out.println("Reviewer: " + r.username + " | Category: " + r.reviewerType + " | Rating: " + r.rating);
            System.out.println("Review  : " + r.reviewText);
            System.out.println("--------------------------------------------");
        }
    }

    public static void createAndViewMovieDetailsFile(Scanner sc) {
        viewAllMoviesForAdmin();
        Integer movieIdInput = readPositiveInt(sc, "\nEnter Movie ID for details file: ");
        if (movieIdInput == null) return;
        int movieId = movieIdInput;

        String movieQuery = "SELECT title, genre, language, duration_minutes, release_date, story FROM movies WHERE movie_id = ?";
        File file = new File("movie_details_" + movieId + ".txt");

        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement movieStmt = conn.prepareStatement(movieQuery)) {
                movieStmt.setInt(1, movieId);
                try (ResultSet movieRs = movieStmt.executeQuery()) {
                    if (!movieRs.next()) {
                        System.out.println("Movie not found.");
                        return;
                    }

                    try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                        bw.write("============== MOVIE DETAILS ==============");
                        bw.newLine();
                        bw.write("Title        : " + movieRs.getString("title"));
                        bw.newLine();
                        bw.write("Genre        : " + movieRs.getString("genre"));
                        bw.newLine();
                        bw.write("Language     : " + movieRs.getString("language"));
                        bw.newLine();
                        bw.write("Runtime      : " + movieRs.getInt("duration_minutes") + " minutes");
                        bw.newLine();
                        bw.write("Release Date : " + movieRs.getDate("release_date"));
                        bw.newLine();
                    }
                }
            }

            System.out.println("\nMovie details saved to: " + file.getAbsolutePath());
            System.out.println("----- READING FROM TXT FILE -----");
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                }
            }

        } catch (SQLException | IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public static void viewMyBookings(String username) {
        String query = "SELECT b.booking_id, m.title, s.show_date, s.show_time, " +
                "b.total_tickets, b.total_amount, b.booking_status, b.booking_time " +
                "FROM bookings b " +
                "JOIN users u ON b.user_id = u.user_id " +
                "JOIN shows s ON b.show_id = s.show_id " +
                "JOIN movies m ON s.movie_id = m.movie_id " +
                "WHERE u.username = ? ORDER BY b.booking_time DESC";

        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, username);
                System.out.println("\n----- MY BOOKINGS -----");
                System.out.println("ID   | Movie                          | Date       | Time     | Tickets | Amount   | Status");
                System.out.println("-----------------------------------------------------------------------------------------------");

                boolean hasBookings = false;
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        hasBookings = true;
                        String idStr = String.valueOf(rs.getInt("booking_id"));
                        String titleStr = rs.getString("title");
                        if (titleStr.length() > 30) titleStr = titleStr.substring(0, 27) + "...";
                        String dateStr = rs.getDate("show_date").toString();
                        String timeStr = rs.getTime("show_time").toString();
                        String tktStr = String.valueOf(rs.getInt("total_tickets"));
                        String amtStr = "Rs." + rs.getDouble("total_amount");
                        String statStr = rs.getString("booking_status");

                        System.out.println(idStr + getSpaces(4 - idStr.length()) + " | " +
                                titleStr + getSpaces(30 - titleStr.length()) + " | " +
                                dateStr + getSpaces(10 - dateStr.length()) + " | " +
                                timeStr + getSpaces(8 - timeStr.length()) + " | " +
                                tktStr + getSpaces(7 - tktStr.length()) + " | " +
                                amtStr + getSpaces(8 - amtStr.length()) + " | " + statStr);
                    }
                }

                if (!hasBookings) System.out.println("You have no bookings yet.");
            }
        } catch (SQLException e) {
            System.out.println("Error fetching bookings: " + e.getMessage());
        }
    }

    public static void mostBookedMovie() {
        String query = "SELECT m.title FROM bookings b " +
                "JOIN shows s ON b.show_id = s.show_id " +
                "JOIN movies m ON s.movie_id = m.movie_id " +
                "WHERE b.booking_status = 'Confirmed'";
        List<String> movieTitles = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement pstmt = conn.prepareStatement(query);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    movieTitles.add(rs.getString("title"));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        if (movieTitles.isEmpty()) {
            System.out.println("No confirmed bookings yet.");
            return;
        }

        Set<String> uniqueTitles = new HashSet<>(movieTitles);
        String topMovie = null;
        int maxCount = 0;

        for (String title : uniqueTitles) {
            int count = Collections.frequency(movieTitles, title);
            if (count > maxCount) {
                maxCount = count;
                topMovie = title;
            }
        }

        if (topMovie != null) {
            System.out.println("Most Booked Movie: " + topMovie + " (" + maxCount + " bookings)");
        }
    }

    public static boolean viewMovies() {
        String query = "SELECT MIN(movie_id) as movie_id, title, MAX(genre) as genre, GROUP_CONCAT(DISTINCT language SEPARATOR ', ') as language, MAX(duration_minutes) as duration_minutes, MAX(release_date) as release_date FROM movies GROUP BY title ORDER BY title";

        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return false;
            try (PreparedStatement pstmt = conn.prepareStatement(query);
                 ResultSet rs = pstmt.executeQuery()) {

                System.out.println("\n----- MOVIES AVAILABLE FOR BOOKING -----");
                System.out.println("ID   | Title                                    | Genre            | Languages                      | Duration     | Release Date");
                System.out.println("----------------------------------------------------------------------------------------------------------------------------------");

                boolean hasMovies = false;
                while (rs.next()) {
                    hasMovies = true;
                    String idStr = String.valueOf(rs.getInt("movie_id"));
                    String titleStr = rs.getString("title");
                    String genreStr = rs.getString("genre");
                    String langStr = rs.getString("language");
                    String durStr = rs.getInt("duration_minutes") + " mins";
                    String dateStr = rs.getDate("release_date").toString();

                    System.out.println(idStr + getSpaces(4 - idStr.length()) + " | " +
                            titleStr + getSpaces(40 - titleStr.length()) + " | " +
                            genreStr + getSpaces(16 - genreStr.length()) + " | " +
                            langStr + getSpaces(30 - langStr.length()) + " | " +
                            durStr + getSpaces(12 - durStr.length()) + " | " +
                            dateStr);
                }

                if (!hasMovies) System.out.println("No movies available right now.");
                return hasMovies;
            }
        } catch (SQLException e) {
            System.out.println("Error fetching movies: " + e.getMessage());
            return false;
        }
    }

    public static void viewAllMoviesForAdmin() {
        String query = "SELECT MIN(movie_id) as movie_id, title, MAX(genre) as genre, GROUP_CONCAT(DISTINCT language SEPARATOR ', ') as language, MAX(duration_minutes) as duration_minutes, MAX(release_date) as release_date FROM movies GROUP BY title ORDER BY MIN(movie_id)";
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement pstmt = conn.prepareStatement(query);
                 ResultSet rs = pstmt.executeQuery()) {
                System.out.println("\n----- ALL MOVIES -----");
                System.out.println("ID   | Title                                    | Genre            | Languages                      | Duration     | Release Date");
                System.out.println("----------------------------------------------------------------------------------------------------------------------------------");

                while (rs.next()) {
                    String idStr = String.valueOf(rs.getInt("movie_id"));
                    String titleStr = rs.getString("title");
                    String genreStr = rs.getString("genre");
                    String langStr = rs.getString("language");
                    String durStr = rs.getInt("duration_minutes") + " mins";
                    String dateStr = rs.getDate("release_date").toString();

                    System.out.println(idStr + getSpaces(4 - idStr.length()) + " | " +
                            titleStr + getSpaces(40 - titleStr.length()) + " | " +
                            genreStr + getSpaces(16 - genreStr.length()) + " | " +
                            langStr + getSpaces(30 - langStr.length()) + " | " +
                            durStr + getSpaces(12 - durStr.length()) + " | " +
                            dateStr);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching movies: " + e.getMessage());
        }
    }

    public static void viewUniqueGenres() {
        String query = "SELECT DISTINCT genre FROM movies";
        Set<String> genreSet = new HashSet<>();
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement pstmt = conn.prepareStatement(query);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    if (rs.getString("genre") != null) genreSet.add(rs.getString("genre"));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }

        System.out.println("\n----- AVAILABLE GENRES -----");
        for (String genre : genreSet) System.out.println(genre);
    }

    public static void viewTheaters() {
        if (theaterCache.isEmpty()) {
            String query = "SELECT name FROM theaters";
            try (Connection conn = DBConnection.getConnection()) {
                if (!isConnectionAvailable(conn)) return;
                try (PreparedStatement pstmt = conn.prepareStatement(query);
                     ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) theaterCache.add(rs.getString("name"));
                }
            } catch (SQLException e) {
                System.out.println("Error fetching theaters: " + e.getMessage());
                return;
            }
        }

        System.out.println("\n----- THEATERS (cached, capacity=" + theaterCache.capacity() + ") -----");
        for (int i = 0; i < theaterCache.size(); i++) {
            System.out.println((i + 1) + ". " + theaterCache.elementAt(i));
        }
    }

    public static void adminLogin(Scanner sc) {
        System.out.print("Enter Username: ");
        String username = sc.nextLine().trim();
        System.out.print("Enter Password: ");
        String password = sc.nextLine().trim();

        String query = "SELECT * FROM users WHERE username = ? AND password_hash = ? AND role = 'Admin'";
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, username);
                pstmt.setString(2, password);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("Admin Login Successful!");
                        showAdminDashboard(sc);
                    } else {
                        System.out.println("Invalid Admin Credentials.");
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Login Error: " + e.getMessage());
        }
    }

    public static void addShow(Scanner sc) {
        viewAllMoviesForAdmin();
        Integer movieIdInput = readPositiveInt(sc, "Enter Movie ID: ");
        if (movieIdInput == null) return;
        int movieId = movieIdInput;

        viewScreensForAdmin();
        Integer screenIdInput = readPositiveInt(sc, "Enter Screen ID: ");
        if (screenIdInput == null) return;
        int screenId = screenIdInput;

        LocalDate showDate = readDate(sc, "Enter Show Date (YYYY-MM-DD): ");
        if (showDate == null) return;

        LocalTime showTime = readTime(sc, "Enter Show Time (HH:MM or HH:MM:SS): ");
        if (showTime == null) return;

        System.out.print("Enter Format (2D/3D/IMAX): ");
        String format = sc.nextLine().trim();

        Double priceInput = readPositiveDouble(sc, "Enter Base Price: ");
        if (priceInput == null) return;
        double price = priceInput;

        String query = "INSERT INTO shows (movie_id, screen_id, show_date, show_time, base_price) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;

            try (PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, movieId);
                pstmt.setInt(2, screenId);
                pstmt.setDate(3, java.sql.Date.valueOf(showDate));
                pstmt.setTime(4, java.sql.Time.valueOf(showTime));
                pstmt.setDouble(5, price);
                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int newShowId = rs.getInt(1);
                        String seatQuery = "INSERT INTO show_seats (show_id, seat_id, is_booked) SELECT ?, seat_id, FALSE FROM seats WHERE screen_id = ?";
                        try (PreparedStatement seatStmt = conn.prepareStatement(seatQuery)) {
                            seatStmt.setInt(1, newShowId);
                            seatStmt.setInt(2, screenId);
                            seatStmt.executeUpdate();
                        }
                    }
                }

                System.out.println("Show added successfully!");
            }
        } catch (SQLException e) {
            System.out.println("Error adding show: " + e.getMessage());
        }
    }

    private static void viewScreensForAdmin() {
        String query = "SELECT sc.screen_id, t.name, t.city, sc.screen_number, sc.seat_capacity FROM screens sc JOIN theaters t ON sc.theater_id = t.theater_id";
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement pstmt = conn.prepareStatement(query);
                 ResultSet rs = pstmt.executeQuery()) {
                System.out.println("\n----- AVAILABLE SCREENS -----");
                System.out.println("ID   | Theater Name              | Screen No");
                System.out.println("--------------------------------------------");
                while (rs.next()) {
                    String idStr = String.valueOf(rs.getInt("screen_id"));
                    String tName = rs.getString("name");
                    if (tName.length() > 25) tName = tName.substring(0, 22) + "...";
                    String sNo = "Screen " + rs.getInt("screen_number");

                    System.out.println(idStr + getSpaces(4 - idStr.length()) + " | " +
                            tName + getSpaces(25 - tName.length()) + " | " + sNo);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching screens: " + e.getMessage());
        }
    }

    public static void addMovie(Scanner sc) {
        System.out.print("Enter Movie Title: ");
        String title = sc.nextLine().trim();
        System.out.print("Enter Genre: ");
        String genre = sc.nextLine().trim();
        System.out.print("Enter Language: ");
        String language = sc.nextLine().trim();
        Integer durationInput = readPositiveInt(sc, "Enter Duration (minutes): ");
        if (durationInput == null) return;
        LocalDate releaseDate = readDate(sc, "Enter Release Date (YYYY-MM-DD): ");
        if (releaseDate == null) return;
        System.out.print("Enter Story / Description: ");
        String story = sc.nextLine().trim();

        String query = "INSERT INTO movies (title, genre, language, duration_minutes, release_date, story) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, title);
                pstmt.setString(2, genre);
                pstmt.setString(3, language);
                pstmt.setInt(4, durationInput);
                pstmt.setDate(5, java.sql.Date.valueOf(releaseDate));
                pstmt.setString(6, story);
                pstmt.executeUpdate();
                System.out.println("Movie added successfully!");
                movieTitleCache.clear();
            }
        } catch (SQLException e) {
            System.out.println("Error adding movie: " + e.getMessage());
        }
    }

    public static void viewAllBookings() {
        String query = "SELECT b.booking_id, u.username, m.title, s.show_date, b.total_tickets, b.total_amount, b.booking_status " +
                "FROM bookings b JOIN users u ON b.user_id = u.user_id JOIN shows s ON b.show_id = s.show_id JOIN movies m ON s.movie_id = m.movie_id ORDER BY b.booking_time DESC";
        try (Connection conn = DBConnection.getConnection()) {
            if (!isConnectionAvailable(conn)) return;
            try (PreparedStatement pstmt = conn.prepareStatement(query);
                 ResultSet rs = pstmt.executeQuery()) {
                System.out.println("\n----- ALL BOOKINGS -----");
                System.out.println("ID   | User            | Movie                          | Amount     | Status");
                System.out.println("---------------------------------------------------------------------------------");
                while (rs.next()) {
                    String idStr = String.valueOf(rs.getInt("booking_id"));
                    String uName = rs.getString("username");
                    if (uName.length() > 15) uName = uName.substring(0, 12) + "...";
                    String tName = rs.getString("title");
                    if (tName.length() > 30) tName = tName.substring(0, 27) + "...";
                    String amtStr = "Rs." + rs.getDouble("total_amount");
                    String statStr = rs.getString("booking_status");

                    System.out.println(idStr + getSpaces(4 - idStr.length()) + " | " +
                            uName + getSpaces(15 - uName.length()) + " | " +
                            tName + getSpaces(30 - tName.length()) + " | " +
                            amtStr + getSpaces(10 - amtStr.length()) + " | " + statStr);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching bookings: " + e.getMessage());
        }
    }
    public static void inspectDatabaseStructure(Scanner sc) {
        while (true) {
            System.out.print("Enter Table Name to Inspect (e.g. users, bookings, movies, payments) or 'cancel' to exit: ");
            String tableName = sc.nextLine().trim();

            if (tableName.equalsIgnoreCase("cancel")) {
                break;
            }

            String query = "SELECT * FROM " + tableName + " LIMIT 1";
            try (Connection conn = DBConnection.getConnection()) {
                if (!isConnectionAvailable(conn)) return;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {

                    ResultSetMetaData rsmd = rs.getMetaData();
                    int colCount = rsmd.getColumnCount();
                    System.out.println("\n----- METADATA FOR TABLE: " + tableName + " -----");
                    System.out.println("Total Columns: " + colCount);
                    System.out.println("No. | Column Name          | Type           | Display Size");
                    System.out.println("----------------------------------------------------------");
                    for (int i = 1; i <= colCount; i++) {
                        String noStr = String.valueOf(i);
                        String colName = rsmd.getColumnName(i);
                        String colType = rsmd.getColumnTypeName(i);
                        String dispSize = String.valueOf(rsmd.getColumnDisplaySize(i));
                        System.out.println(noStr + getSpaces(3 - noStr.length()) + " | " +
                                colName + getSpaces(20 - colName.length()) + " | " +
                                colType + getSpaces(14 - colType.length()) + " | " + dispSize);
                    }
                    break; // Success, exit loop
                } catch (SQLException e) {
                    System.out.println("Error inspecting metadata: " + e.getMessage());
                    System.out.println("Let's try again.\n");
                }
            } catch (SQLException e) {
                System.out.println("Database connection error: " + e.getMessage());
                return;
            }
        }
    }
    public static void unusedUtilityMethod() {
        System.out.println("Placeholder for additional utilities.");
    }
    public static String getSpaces(int count) {
        String spaces = "";
        for (int i = 0; i < count; i++) {
            spaces += " ";
        }
        return spaces;
    }
    public static String padRight(String s, int n) {
        if (s == null) {
            s = "null";
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) {
            sb.append(" ");
        }
        return sb.toString();
    }
    public static void systemDiagnostics() {
        System.out.println("\n----- SYSTEM & FILE HEALTH CHECK -----");
        File file = new File("booking_receipts.txt");
        System.out.println("File Name: " + file.getName());
        System.out.println("File Exists: " + file.exists());
        System.out.println("File Size: " + file.length() + " bytes");
        System.out.println("Absolute Path: " + file.getAbsolutePath());
        System.out.println("Can Read File: " + file.canRead());

        Notification notifier = new SMSNotification();
        if (notifier instanceof SMSNotification) {
            System.out.println("Polymorphic Check: Notifier is an instance of SMSNotification.");
        }
    }

    public static void viewPriorityBookingQueue() {
        if (priorityBookingQueue.isEmpty()) {
            System.out.println("Queue is empty.");
            return;
        }
        PriorityQueue<BookingRequest> copy = new PriorityQueue<>(Comparator.comparingDouble(BookingRequest::getAmount).reversed());
        copy.addAll(priorityBookingQueue);

        System.out.println("\n----- VIP SUPPORT QUEUE (highest amount first) -----");
        int rank = 1;
        while (!copy.isEmpty()) System.out.println(rank++ + ". " + copy.poll());
    }

    public static void viewMovieRequests(Scanner sc) {
        if (movieRequests.isEmpty()) {
            System.out.println("No pending movie requests.");
            return;
        }
        System.out.println("\n----- PENDING MOVIE REQUESTS (oldest first) -----");
        int i = 1;
        for (String req : movieRequests) System.out.println(i++ + ". " + req);
        System.out.print("\nMark the oldest request as processed? (y/n): ");
        if (sc.nextLine().equalsIgnoreCase("y")) {
            System.out.println("Processed: " + movieRequests.pollFirst());
        }
    }

    public static void viewNotificationLog() {
        if (notificationLog.isEmpty()) {
            System.out.println("No notifications yet.");
            return;
        }
        System.out.println("\n----- NOTIFICATION LOG -----");
        int i = 1;
        for (String note : notificationLog) System.out.println(i++ + ". " + note);
    }

    public static void showAdminDashboard(Scanner sc) {
        while (true) {
            System.out.println("\n----- ADMIN DASHBOARD -----");
            System.out.println("1. Add New Movie");
            System.out.println("2. Add New Show");
            System.out.println("3. View All Bookings");
            System.out.println("4. View VIP Support Queue");
            System.out.println("5. View Movie Requests");
            System.out.println("6. View Notification Log");
            System.out.println("7. Inspect DB Table Structure (ResultSetMetaData)");
            System.out.println("8. System & File Health Diagnostics (File & instanceof)");
            System.out.println("9. View Booking Logs (from file)");
            System.out.println("10. Logout");
            Integer choiceInput = readInt(sc, "Enter your choice: ");
            if (choiceInput == null) continue;

            switch (choiceInput) {
                case 1: addMovie(sc); break;
                case 2: addShow(sc); break;
                case 3: viewAllBookings(); break;
                case 4: viewPriorityBookingQueue(); break;
                case 5: viewMovieRequests(sc); break;
                case 6: viewNotificationLog(); break;
                case 7: inspectDatabaseStructure(sc); break;
                case 8: systemDiagnostics(); break;
                case 9: viewBookingLogsFromFile(sc); break;
                case 10:
                    System.out.println("Admin Logged out.");
                    return;
                default: System.out.println("Invalid choice!");
            }
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("=========================================");
        System.out.println("      WELCOME TO FLICKPASS APPLICATION   ");
        System.out.println("=========================================");

        while (true) {
            System.out.println("\n--- MAIN MENU ---");
            System.out.println("1. Register New User");
            System.out.println("2. User Login & Book Tickets");
            System.out.println("3. Admin Login");
            System.out.println("4. Exit");
            Integer choiceInput = readInt(sc, "Enter your choice: ");
            if (choiceInput == null) continue;

            switch (choiceInput) {
                case 1: registerUser(sc); break;
                case 2:
                    String loggedInUser = loginUser(sc);
                    if (loggedInUser != null) showCustomerDashboard(sc, loggedInUser);
                    break;
                case 3: adminLogin(sc); break;
                case 4:
                    System.out.println("Shutting down background services...");
                    notificationBuffer.produce("SHUTDOWN"); // Tells the thread to stop gracefully
                    try { notificationWorker.join(); } catch (InterruptedException e) { e.printStackTrace(); }
                    System.out.println("Thank you for using FlickPass! Goodbye.");
                    sc.close();
                    System.exit(0);
                default: System.out.println("Invalid choice!");
            }
        }
    }
}


