import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;


// This template shows input parameters format.
// It is otherwise not mandatory to use, you can write everything from scratch if you wish.
public class TransactionProcessorSample {

    public static void main(final String[] args) throws IOException {

        System.out.println("Received arguments:");


        for (String arg : args) {
            System.out.println(arg);

            String filePath = arg; // Replace with your actual file path

            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                System.out.println("The file " + filePath + " exists.");
            } else {
                System.out.println("The file " + filePath + " does not exist.");
            }


        }





        //System.out.println("-------");
        //System.out.println(Paths.get(args[0]).getFileName());
        List<User> users = TransactionProcessorSample.readUsers(Paths.get(args[0]));
        List<Transaction> transactions = TransactionProcessorSample.readTransactions(Paths.get(args[1]));
        List<BinMapping> binMappings = TransactionProcessorSample.readBinMappings(Paths.get(args[2]));

        long startTime = System.nanoTime();

        List<Event> events = TransactionProcessorSample.processTransactions(users, transactions, binMappings);

        long endTime = System.nanoTime();
        long executionTime = (endTime - startTime) / 1000000;

        System.out.println("Function execution time: " + executionTime + "ms");

        System.out.println("----------------------------------------------------------------------------------------------------------------");


        TransactionProcessorSample.writeBalances(Paths.get(args[3]), users);
        TransactionProcessorSample.writeEvents(Paths.get(args[4]), events);
/*
for (int i = 0; i < events.size(); i++) {
            System.out.println(events.get(i).toString());
        }

        for (User t : users){
            System.out.println(t.toString());
        }
        int g = 1;
        for (Transaction t : transactions){
            System.out.println(t.toString());
            System.out.println(g);
            g+=1;
        }

        for (BinMapping t : binMappings){
            System.out.println(t.toString());

        }*/
    }

    private static List<User> readUsers(final Path filePath) throws IOException {

        List<User> users = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(filePath)) {
            //System.out.println(filePath.getFileName());
            br.readLine();
            String line;
            while ((line = br.readLine()) != null ) {
                //System.out.println(line);
                String[] fields = line.split(",");
                User user = new User(fields[0],fields[1],Double.parseDouble(fields[2]),fields[3],fields[4].equals("1"),Double.parseDouble(fields[5]),
                        Double.parseDouble(fields[6]),Double.parseDouble(fields[7]),Double.parseDouble(fields[8]) );

                users.add(user);

            }
        }
        return users;

    }

    private static List<Transaction> readTransactions(final Path filePath) throws  IOException {
        List<Transaction> trans = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(filePath)) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null ) {
                //System.out.println(line);
                String[] fields = line.split(",");
                Transaction tran = new Transaction(fields[0],fields[1],fields[2],Double.parseDouble(fields[3]),
                        fields[4],fields[5]);

                trans.add(tran);
            }
        }


        return trans;
    }

    private static List<BinMapping> readBinMappings(final Path filePath) throws IOException {
        List<BinMapping> bins = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(filePath)) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null ) {
                //System.out.println(line);
                String[] fields = line.split(",");
                BinMapping bi = new BinMapping(fields[0],Long.parseLong(fields[1]),Long.parseLong(fields[2]),
                        fields[3],fields[4] );

                bins.add(bi);
            }
        }


        return bins;
    }

    public static List<Event> processTransactions(final List<User> users, final List<Transaction> transactions, final List<BinMapping> binMappings) {
        List<Event> events = new ArrayList<>();
        Set<String> seenTransactionIds = new HashSet<>();
        Map<String, String> accountUsageMap = new HashMap<>();
        List<Transaction> transactionsApproved = new ArrayList<>();


        Map<String, User> userMap = users.stream().collect(Collectors.toMap(User::getUserId, user -> user));


        TreeMap<Long, BinMapping> binMap = new TreeMap<>();
        for (BinMapping bin : binMappings) {
            binMap.put(bin.getRangeFrom(), bin);
        }

        Map<String, Set<String>> successfulDeposits = new HashMap<>();

        try {
            for (Transaction transaction : transactions) {
                Event event = new Event(null, null, null);
                String transactionId = transaction.getTransactionId();

                //check if user exists
                User user = userMap.get(transaction.getUserId());
                if (user == null) {
                    event.setStatus(Event.STATUS_DECLINED);
                    event.setTransactionId(transactionId);
                    event.setMessage("User id not found among user data");
                    events.add(event);
                    continue;
                }

                //chekćk if unique
                if (!seenTransactionIds.add(transactionId)) {
                    event.setTransactionId(transactionId);
                    event.setStatus(Event.STATUS_DECLINED);
                    event.setMessage("Transaction ID is not unique.");
                    events.add(event);
                    continue;
                }

                //check if frozen
                if (user.isFrozen()) {
                    event.setStatus(Event.STATUS_DECLINED);
                    event.setTransactionId(transactionId);
                    event.setMessage("User " + user.getUsername() + " account is FROZEN.");
                    events.add(event);
                    continue;
                }


                if (transaction.getMethod().equals("TRANSFER")) {
                    if (!IbanValidator.validateIban(transaction.getAccountNumber())) {
                        event.setStatus(Event.STATUS_DECLINED);
                        event.setTransactionId(transactionId);
                        event.setMessage("Invalid IBAN.");
                        events.add(event);
                        continue;
                    }
                    if (!transaction.getAccountNumber().substring(0, 2).equals(user.getCountry())) {
                        event.setStatus(Event.STATUS_DECLINED);
                        event.setTransactionId(transactionId);
                        event.setMessage("Account country and user country don't match.");
                        events.add(event);
                        continue;
                    }
                } else if (transaction.getMethod().equals("CARD")) {
                    long cardPrefix = Long.parseLong(transaction.getAccountNumber().substring(0, 10));
                    Map.Entry<Long, BinMapping> binEntry = binMap.floorEntry(cardPrefix);
                    if (binEntry == null || cardPrefix > binEntry.getValue().getRangeTo()) {
                        continue;
                    }
                    BinMapping bin = binEntry.getValue();
                    if (!bin.getCardType().equals("DC")) {
                        event.setStatus(Event.STATUS_DECLINED);
                        event.setTransactionId(transactionId);
                        event.setMessage("Card is not a debit card.");
                        events.add(event);
                        continue;
                    }
                    if (!bin.getCountry().equals(IbanValidator.convertToAlpha3(user.getCountry()))) {
                        event.setStatus(Event.STATUS_DECLINED);
                        event.setTransactionId(transactionId);
                        event.setMessage("Card country does not match user country.");
                        events.add(event);
                        continue;
                    }
                } else {
                    event.setStatus(Event.STATUS_DECLINED);
                    event.setTransactionId(transactionId);
                    event.setMessage("Invalid payment method.");
                    events.add(event);
                    continue;
                }

                // Validate amount and limits
                if (transaction.getAmount() <= 0) {
                    event.setStatus(Event.STATUS_DECLINED);
                    event.setTransactionId(transactionId);
                    event.setMessage("Transaction amount is not valid.");
                    events.add(event);
                    continue;
                }
                if (transaction.getType().equals("WITHDRAW")) {
                    if (user.getWithdrawMin() >= transaction.getAmount() || user.getWithdrawMax() < transaction.getAmount() || user.getBalance() < transaction.getAmount()) {
                        event.setStatus(Event.STATUS_DECLINED);
                        event.setTransactionId(transactionId);
                        event.setMessage("Withdrawal amount is not valid.");
                        events.add(event);
                        continue;
                    }

                    // Check if the withdrawal account has had a successful deposit
                    Set<String> userDepositedAccounts = successfulDeposits.get(user.getUserId());
                    if (userDepositedAccounts == null || !userDepositedAccounts.contains(transaction.getAccountNumber())) {
                        event.setStatus(Event.STATUS_DECLINED);
                        event.setTransactionId(transactionId);
                        event.setMessage("Withdrawal not allowed: account has not had a successful deposit.");
                        events.add(event);
                        continue;
                    }
                } else if (transaction.getType().equals("DEPOSIT")) {
                    if (user.getDepositMin() > transaction.getAmount() || user.getDepositMax() < transaction.getAmount()) {
                        event.setStatus(Event.STATUS_DECLINED);
                        event.setTransactionId(transactionId);
                        event.setMessage("Deposit amount is not valid.");
                        events.add(event);
                        continue;
                    }
                    // Track successful deposit
                    successfulDeposits
                            .computeIfAbsent(user.getUserId(), k -> new HashSet<>())
                            .add(transaction.getAccountNumber());
                } else {
                    event.setStatus(Event.STATUS_DECLINED);
                    event.setTransactionId(transactionId);
                    event.setMessage("Transaction type is not valid.");
                    events.add(event);
                    continue;
                }

                // Validate account usage
                if (accountUsageMap.containsKey(transaction.getAccountNumber()) && !accountUsageMap.get(transaction.getAccountNumber()).equals(user.getUserId())) {
                    event.setStatus(Event.STATUS_DECLINED);
                    event.setTransactionId(transactionId);
                    event.setMessage("Account has already been used by another user.");
                    events.add(event);
                    continue;
                }

                // Update user balance and approve the transaction
                if (transaction.getType().equals("WITHDRAW")) {
                    user.setBalance(user.getBalance() - transaction.getAmount());
                } else {
                    user.setBalance(user.getBalance() + transaction.getAmount());
                }
                event.setStatus(Event.STATUS_APPROVED);
                event.setTransactionId(transactionId);
                event.setMessage("OK");
                events.add(event);

                transactionsApproved.add(transaction);
                accountUsageMap.put(transaction.getAccountNumber(), user.getUserId());
            }
        } catch (Exception ignored) {
            ;
        }

        return events;
    }



    private static boolean isHasDepositedBefore(List<Transaction> transactions_approved, Transaction transaction_Now) {
        boolean has_deposited_before = false;


        for (int k = 0; k < transactions_approved.size(); k++) {
            if (Objects.equals(transactions_approved.get(k).getAccountNumber(), transaction_Now.getAccountNumber()) && transactions_approved.get(k).getType().equals("DEPOSIT")) {
                has_deposited_before = true;
                //System.out.println(transactions_approved.get(k).transactionId + "   " + transactions_approved.get(k).type);

            }

        }
        return has_deposited_before;
    }

    private static void writeBalances(final Path filePath, final List<User> users) {

        try{
            FileWriter fileWriter = new FileWriter(String.valueOf(filePath));
            fileWriter.append("user_id").append(",").append("balance").append("\n");
            for (User user: users){
                fileWriter.append(user.getUserId()).append(",").append(String.format("%.2f", user.getBalance())).append("\n");
            }
            fileWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private static void writeEvents(final Path filePath, final List<Event> events) throws IOException {
        try (final FileWriter writer = new FileWriter(filePath.toFile(), false)) {
            writer.append("transaction_id,status,message\n");
            for (final var event : events) {
                writer.append(event.getTransactionId()).append(",").append(event.getStatus()).append(",").append(event.getMessage()).append("\n");
            }
        }
    }
}







class IbanValidator {

    private static final Map<String, Integer> IBAN_LENGTH_MAP = new HashMap<>();

    static {

        IBAN_LENGTH_MAP.put("AL", 28);
        IBAN_LENGTH_MAP.put("AD", 24);
        IBAN_LENGTH_MAP.put("AT", 20);
        IBAN_LENGTH_MAP.put("AZ", 28);
        IBAN_LENGTH_MAP.put("BH", 22);
        IBAN_LENGTH_MAP.put("BE", 16);
        IBAN_LENGTH_MAP.put("BA", 20);
        IBAN_LENGTH_MAP.put("BR", 29);
        IBAN_LENGTH_MAP.put("BG", 22);
        IBAN_LENGTH_MAP.put("CR", 22);
        IBAN_LENGTH_MAP.put("HR", 21);
        IBAN_LENGTH_MAP.put("CY", 28);
        IBAN_LENGTH_MAP.put("CZ", 24);
        IBAN_LENGTH_MAP.put("DK", 18);
        IBAN_LENGTH_MAP.put("DO", 28);
        IBAN_LENGTH_MAP.put("EG", 29);
        IBAN_LENGTH_MAP.put("EE", 20);
        IBAN_LENGTH_MAP.put("FI", 18);
        IBAN_LENGTH_MAP.put("FR", 27);
        IBAN_LENGTH_MAP.put("GE", 22);
        IBAN_LENGTH_MAP.put("DE", 22);
        IBAN_LENGTH_MAP.put("GI", 23);
        IBAN_LENGTH_MAP.put("GR", 27);
        IBAN_LENGTH_MAP.put("GT", 28);
        IBAN_LENGTH_MAP.put("HU", 28);
        IBAN_LENGTH_MAP.put("IS", 26);
        IBAN_LENGTH_MAP.put("IE", 22);
        IBAN_LENGTH_MAP.put("IL", 23);
        IBAN_LENGTH_MAP.put("IT", 27);
        IBAN_LENGTH_MAP.put("JO", 30);
        IBAN_LENGTH_MAP.put("KZ", 20);
        IBAN_LENGTH_MAP.put("XK", 20);
        IBAN_LENGTH_MAP.put("KW", 30);
        IBAN_LENGTH_MAP.put("LV", 21);
        IBAN_LENGTH_MAP.put("LB", 28);
        IBAN_LENGTH_MAP.put("LI", 21);
        IBAN_LENGTH_MAP.put("LT", 20);
        IBAN_LENGTH_MAP.put("LU", 20);
        IBAN_LENGTH_MAP.put("MK", 19);
        IBAN_LENGTH_MAP.put("MT", 31);
        IBAN_LENGTH_MAP.put("MR", 27);
        IBAN_LENGTH_MAP.put("MU", 30);
        IBAN_LENGTH_MAP.put("MC", 27);
        IBAN_LENGTH_MAP.put("MD", 24);
        IBAN_LENGTH_MAP.put("ME", 22);
        IBAN_LENGTH_MAP.put("NL", 18);
        IBAN_LENGTH_MAP.put("NO", 15);
        IBAN_LENGTH_MAP.put("PK", 24);
        IBAN_LENGTH_MAP.put("PS", 29);
        IBAN_LENGTH_MAP.put("PL", 28);
        IBAN_LENGTH_MAP.put("PT", 25);
        IBAN_LENGTH_MAP.put("QA", 29);
        IBAN_LENGTH_MAP.put("RO", 24);
        IBAN_LENGTH_MAP.put("SM", 27);
        IBAN_LENGTH_MAP.put("SA", 24);
        IBAN_LENGTH_MAP.put("RS", 22);
        IBAN_LENGTH_MAP.put("SK", 24);
        IBAN_LENGTH_MAP.put("SI", 19);
        IBAN_LENGTH_MAP.put("ES", 24);
        IBAN_LENGTH_MAP.put("SE", 24);
        IBAN_LENGTH_MAP.put("CH", 21);
        IBAN_LENGTH_MAP.put("TN", 24);
        IBAN_LENGTH_MAP.put("TR", 26);
        IBAN_LENGTH_MAP.put("AE", 23);
        IBAN_LENGTH_MAP.put("GB", 22);
        IBAN_LENGTH_MAP.put("VG", 24);
        IBAN_LENGTH_MAP.put("AO", 25);
        IBAN_LENGTH_MAP.put("BF", 27);
        IBAN_LENGTH_MAP.put("BI", 16);
        IBAN_LENGTH_MAP.put("BJ", 28);
        IBAN_LENGTH_MAP.put("CM", 27);
        IBAN_LENGTH_MAP.put("CV", 25);
        IBAN_LENGTH_MAP.put("DZ", 24);
        IBAN_LENGTH_MAP.put("GA", 27);
        IBAN_LENGTH_MAP.put("IR", 26);
        IBAN_LENGTH_MAP.put("MG", 27);
        IBAN_LENGTH_MAP.put("ML", 28);
        IBAN_LENGTH_MAP.put("MZ", 25);
        IBAN_LENGTH_MAP.put("SN", 28);
        IBAN_LENGTH_MAP.put("TL", 23);
        IBAN_LENGTH_MAP.put("UA", 29);
        IBAN_LENGTH_MAP.put("VA", 22);
        IBAN_LENGTH_MAP.put("SC", 31);
        IBAN_LENGTH_MAP.put("ST", 25);


    }

    public static boolean validateIban(String iban) {
        if (!isValidLength(iban)) {
            return false;
        }

        String rearrangedIban = rearrangeIban(iban);
        String numericIban = convertLettersToDigits(rearrangedIban);

        BigInteger ibanNumber = new BigInteger(numericIban);
        return ibanNumber.mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    private static boolean isValidLength(String iban) {

        String countryCode = iban.substring(0, 2).toUpperCase();

        Integer expectedLength = IBAN_LENGTH_MAP.get(countryCode);

        return expectedLength != null && iban.length() == expectedLength;
    }

    private static String rearrangeIban(String iban) {
        return iban.substring(4) + iban.substring(0, 4);
    }

    private static String convertLettersToDigits(String iban) {
        StringBuilder numericIban = new StringBuilder();

        for (char ch : iban.toCharArray()) {
            if (Character.isLetter(ch)) {
                int numericValue = Character.getNumericValue(ch);
                numericIban.append(numericValue);
            } else {
                numericIban.append(ch);
            }
        }

        return numericIban.toString();
    }
    public static String convertToAlpha3(String countryAlpha2) {
        Locale locale = new Locale("", countryAlpha2);
        return locale.getISO3Country();
    }

}


