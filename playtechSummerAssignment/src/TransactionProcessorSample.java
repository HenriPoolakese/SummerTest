import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


// This template shows input parameters format.
// It is otherwise not mandatory to use, you can write everything from scratch if you wish.
public class TransactionProcessorSample {

    public static void main(final String[] args) throws IOException {

        System.out.println("Received arguments:");
        for (String arg : args) {
            System.out.println(arg);
        }
        //System.out.println("-------");
        //System.out.println(Paths.get(args[0]).getFileName());
        List<User> users = TransactionProcessorSample.readUsers(Paths.get(args[0]));
        List<Transaction> transactions = TransactionProcessorSample.readTransactions(Paths.get(args[1]));
        List<BinMapping> binMappings = TransactionProcessorSample.readBinMappings(Paths.get(args[2]));

        List<Event> events = TransactionProcessorSample.processTransactions(users, transactions, binMappings);
        /*System.out.println(events.size());
        System.out.println("----------------------------------------------------------------------------------------------------------------");
        for (int i = 0; i < events.size(); i++) {
            System.out.println(events.get(i).toString());
        }*/
        TransactionProcessorSample.writeBalances(Paths.get(args[3]), users);
        TransactionProcessorSample.writeEvents(Paths.get(args[4]), events);
        /*
        for (User t : users){
            System.out.println(t.toString());
        }
        for (Transaction t : transactions){
            System.out.println(t.toString());
        }
        for (BinMapping t : binMappings){
            System.out.println(t.toString());

        }*/
    }

    private static List<User> readUsers(final Path filePath) throws IOException {
        List<User> users = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(filePath.getFileName())) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null ) {
                //System.out.println(line);
                String[] fields = line.split(",");
                User user = new User();
                user.userId = fields[0];
                user.username = fields[1];
                user.balance = Double.parseDouble(fields[2]);
                user.country = fields[3];
                user.frozen = fields[4].equals("1");
                user.depositMin = Double.parseDouble(fields[5]);
                user.depositMax = Double.parseDouble(fields[6]);
                user.withdrawMin = Double.parseDouble(fields[7]);
                user.withdrawMax = Double.parseDouble(fields[8]);

                users.add(user);
            }
        }
        return users;

    }

    private static List<Transaction> readTransactions(final Path filePath) throws  IOException {
        List<Transaction> trans = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(filePath.getFileName())) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null ) {
                //System.out.println(line);
                String[] fields = line.split(",");
                Transaction tran = new Transaction();
                tran.transactionId = fields[0];
                tran.userId = fields[1];
                tran.type = fields[2];
                tran.amount = Double.parseDouble(fields[3]);
                tran.method = fields[4];
                tran.accountNumber = fields[5];
                trans.add(tran);
            }
        }


        return trans;
    }

    private static List<BinMapping> readBinMappings(final Path filePath) throws IOException {
        List<BinMapping> bins = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(filePath.getFileName())) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null ) {
                //System.out.println(line);
                String[] fields = line.split(",");
                BinMapping bi = new BinMapping();
                bi.bankName = fields[0];
                bi.rangeFrom = Long.parseLong(fields[1]);
                bi.rangeTo = Long.parseLong(fields[2]);
                bi.cardType = fields[3];
                bi.country = fields[4];

                bins.add(bi);
            }
        }


        return bins;
    }

    private static List<Event> processTransactions(final List<User> users, final List<Transaction> transactions, final List<BinMapping> binMappings) {
        List<Event> events = new ArrayList<>();
        Set<String> seenTransactionIds = new HashSet<>();
        Map<String, String> accountUsageMap = new HashMap<>();

        List<Transaction> transactions_approved = new ArrayList<>();
        try {
            for (int i = 0; i < transactions.size(); i++) {
                Event even = new Event();


                Transaction transaction_Now = transactions.get(i);
                String t = transaction_Now.transactionId;

                User user_Now = new User();
                for (int j = 0; j < users.size(); j++) {
                    if (Objects.equals(users.get(j).userId, transaction_Now.userId))
                        user_Now = users.get(j);
                    //System.out.println(users.get(j));
                }
                //System.out.println(user_Now.toString());

                //number of same transactions with same transactionID
                if (!seenTransactionIds.add(transaction_Now.transactionId)) {
                    even.status = Event.STATUS_DECLINED;
                    even.transactionId = transaction_Now.transactionId;
                    even.message = "Transaction ID is not unique.";
                    events.add(even);
                    continue;
                }




                //- Verify that the user exists and is not frozen (users are loaded from a file, see "inputs").
                if (user_Now.userId == null || user_Now.frozen) {
                    even.status = Event.STATUS_DECLINED;
                    even.transactionId = t;
                    if (user_Now.userId != null)
                        even.message = "User " + user_Now.username + " uses account for " + transaction_Now.type + ". " + transaction_Now.type + " gets declined due to Account being FROZEN.";
                    else
                        even.message = "User " + user_Now.username + " uses account for " + transaction_Now.type + ". " + transaction_Now.type + " gets declined because account doesnt exist.";
                    events.add(even);
                    continue;
                }

                //- Validate payment method:
                //  - For **TRANSFER** payment methods, validate the transfer account number's check digit validity (see details here [International Bank Account Number](https://en.wikipedia.org/wiki/International_Bank_Account_Number))
                //  - For **CARD** payment methods, only allow debit cards; validate that card type=DC (see bin mapping part of "inputs" section for details)
                //  - Other types must be declined
                //- Confirm that the country of the card or account used for the transaction matches the user's country


                if (Objects.equals(transaction_Now.method, "TRANSFER")) {
                    if (!IbanValidator.validateIban(transaction_Now.accountNumber)) {
                        even.status = Event.STATUS_DECLINED;
                        even.transactionId = transaction_Now.transactionId;
                        even.message = "User " + user_Now.username +" uses account "+ transaction_Now.accountNumber +". "+ transaction_Now.type  + " GETS DECLINED - IBAN wrong";
                        events.add(even);
                        continue;
                    }

                    if (!transaction_Now.accountNumber.substring(0, 2).equals(user_Now.country )) {
                        //System.out.println(IbanValidator.convertToAlpha3(user_Now.country));
                        even.status = Event.STATUS_DECLINED;
                        even.transactionId = transaction_Now.transactionId;
                        even.message = "User " + user_Now.username +" uses account "+ transaction_Now.accountNumber +". "+ transaction_Now.type  + " GETS DECLINED - account country and user county dont match";
                        events.add(even);
                        continue;
                    }
                }
                if (Objects.equals(transaction_Now.method, "CARD")) {
                    String card_number = transaction_Now.accountNumber;
                    long ten_numbers = Long.parseLong(card_number.substring(0, 10));
                    boolean isValidCard = false;
                    for (BinMapping bin : binMappings) {
                        if (bin.rangeFrom <= ten_numbers && ten_numbers <= bin.rangeTo) {
                            if (!bin.cardType.equals("DC")) {
                                even.status = Event.STATUS_DECLINED;
                                even.transactionId = transaction_Now.transactionId;
                                even.message = "Card is not a debit card.";
                                events.add(even);
                                break;
                            }
                            if (!bin.country.equals(IbanValidator.convertToAlpha3(user_Now.country))) {
                                even.status = Event.STATUS_DECLINED;
                                even.transactionId = transaction_Now.transactionId;
                                even.message = "User"+ transaction_Now.userId +" country:"+ user_Now.country +" card country:"+ bin.country  +" Card country does not match user country.";
                                events.add(even);
                                break;
                            }
                            isValidCard = true;
                        }
                    }
                    if (!isValidCard) continue;




                }

                //- Validate that the amount is a valid (positive) number and within deposit/withdraw limits.
                if (transaction_Now.amount <= 0) {
                    even.status = Event.STATUS_DECLINED;
                    even.transactionId = transaction_Now.transactionId;
                    even.message = "User " + user_Now.username +" uses account "+ transaction_Now.accountNumber +". "+ transaction_Now.type  + " GETS DECLINED - transaction amount is lower than 0.";
                    events.add(even);
                    continue;
                }

                if (transaction_Now.type.equals("WITHDRAW") && (user_Now.withdrawMin >= transaction_Now.amount || user_Now.withdrawMax < transaction_Now.amount)) {
                    //System.out.println(user_Now.toString() + "     "+ transaction_Now.amount + (user_Now.withdrawMin >= transaction_Now.amount || user_Now.withdrawMax < transaction_Now.amount));
                    //System.out.println(user_Now.withdrawMax < transaction_Now.amount);
                    even.status = Event.STATUS_DECLINED;
                    even.transactionId = transaction_Now.transactionId;
                    even.message = "User " + user_Now.username +" uses account "+ transaction_Now.accountNumber +". "+ transaction_Now.type  + " GETS DECLINED - withdrawal amount is lower or higher than allowed";
                    events.add(even);
                    continue;
                }

                if (transaction_Now.type.equals("DEPOSIT") && (user_Now.depositMin > transaction_Now.amount || user_Now.depositMax < transaction_Now.amount)) {
                    even.status = Event.STATUS_DECLINED;
                    even.transactionId = transaction_Now.transactionId;
                    even.message = "User " + user_Now.username +" uses account "+ transaction_Now.accountNumber +". "+ transaction_Now.type  + " GETS DECLINED - deposit amount is lower or higher than allowed";
                    events.add(even);
                    continue;
                }

                //- For withdrawals, validate that the user has a sufficient balance for a withdrawal.
                if (transaction_Now.type.equals("WITHDRAW") && user_Now.balance < transaction_Now.amount) {
                    even.status = Event.STATUS_DECLINED;
                    even.transactionId = transaction_Now.transactionId;
                    even.message = "User " + user_Now.username +" uses account "+ transaction_Now.accountNumber +". "+ transaction_Now.type  + " GETS DECLINED - withdrawal amount is  higher than Account bakance";
                    events.add(even);
                    continue;
                }
                //- Allow withdrawals only with the same payment account that has previously been successfully used for deposit (declined deposits with an account do not make it eligible for withdrawals; at least one approved deposit is needed).

                if (transaction_Now.type.equals("WITHDRAW")) {
                    String user_id_temp = transaction_Now.userId;

                    boolean has_deposited_before = false;



                    for (int k = 0; k < transactions_approved.size(); k++) {
                        if (Objects.equals(transactions_approved.get(k).accountNumber,transaction_Now.accountNumber ) && transactions_approved.get(k).type.equals("DEPOSIT")) {
                            has_deposited_before = true;
                            //System.out.println(transactions_approved.get(k).transactionId + "   " + transactions_approved.get(k).type);

                        }

                    }
                    //System.out.println(has_deposited_before);
                    if (!has_deposited_before) {


                        even.status = Event.STATUS_DECLINED;
                        even.transactionId = transaction_Now.transactionId;
                        even.message = "User " + user_Now.username +" uses account "+ transaction_Now.accountNumber +". "+ transaction_Now.type  + " GETS DECLINED - cant withdraw, beacuse account has now deposited before";
                        events.add(even);
                        continue;
                    }

                }

                //- Transaction type that isn't deposit or withdrawal should be declined
                if (!(transaction_Now.type.equals("WITHDRAW") || transaction_Now.type.equals("DEPOSIT"))) {
                    even.status = Event.STATUS_DECLINED;
                    even.transactionId = transaction_Now.transactionId;
                    even.message = "User " + user_Now.username +" uses account "+ transaction_Now.accountNumber +". "+ transaction_Now.type  + " GETS DECLINED - error, Transaction type  isn't deposit or withdrawal -  should be declined";
                    events.add(even);
                    continue;
                }




                if (accountUsageMap.containsKey(transaction_Now.accountNumber) && !accountUsageMap.get(transaction_Now.accountNumber).equals(user_Now.userId)) {
                    even.status = Event.STATUS_DECLINED;
                    even.transactionId = transaction_Now.transactionId;
                    even.message = "Account has already been used by another user.";
                    events.add(even);
                    continue;
                }





                if (transaction_Now.type.equals("WITHDRAW")){


                    int user_index =  users.indexOf(user_Now);
                    user_Now.balance = user_Now.balance - transaction_Now.amount;

                    users.set(user_index,user_Now);

                    even.status = Event.STATUS_APPROVED;
                    even.transactionId = transaction_Now.transactionId;
                    even.message = "User " + user_Now.username +" uses"+ transaction_Now.method  + " for WHIDRAWL. Gets APPROVED";
                    events.add(even);
                    transactions_approved.add(transaction_Now);
                    accountUsageMap.put(transaction_Now.accountNumber, user_Now.userId);
                    seenTransactionIds.add(transaction_Now.transactionId);

                }
                if (transaction_Now.type.equals("DEPOSIT")){


                    int user_index =  users.indexOf(user_Now);
                    user_Now.balance = user_Now.balance + transaction_Now.amount;

                    users.set(user_index,user_Now);

                    even.status = Event.STATUS_APPROVED;
                    even.transactionId = transaction_Now.transactionId;
                    even.message = "User " + user_Now.username +" uses"+ transaction_Now.method  + " for DEPOSIT. Gets APPROVED" ;
                    events.add(even);
                    transactions_approved.add(transaction_Now);
                    accountUsageMap.put(transaction_Now.accountNumber, user_Now.userId);
                    seenTransactionIds.add(transaction_Now.transactionId);

                }


            }

        }catch (Exception ignored){ }


        /*
        The processing should include the following steps:
- Users cannot share iban/card; payment account used by one user can no longer be used by another (Example Scenario for this validation provided below).
- In case of unexpected errors with processing transactions, skip the transaction. Do not interrupt processing of the remaining transactions

Transactions that fail any of the validations should be declined (i.e., the user's balance remains unchanged), and the decline reason should be saved in the events file.

         */


        return events;
    }

    private static void writeBalances(final Path filePath, final List<User> users) {

        try{
            FileWriter fileWriter = new FileWriter(String.valueOf(filePath));
            fileWriter.append("user_id").append(",").append("balance").append("\n");
            for (User user: users){
                fileWriter.append(user.userId).append(",").append(String.format("%.2f", user.balance)).append("\n");
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
                writer.append(event.transactionId).append(",").append(event.status).append(",").append(event.message).append("\n");
            }
        }
    }
}


class User {
    String userId;
    String username;
    double balance;
    String country;
    boolean frozen;
    double depositMin;
    double depositMax;
    double withdrawMin;
    double withdrawMax;

    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", balance=" + balance +
                ", country='" + country + '\'' +
                ", frozen=" + frozen +
                ", depositMin=" + depositMin +
                ", depositMax=" + depositMax +
                ", withdrawMin=" + withdrawMin +
                ", withdrawMax=" + withdrawMax +
                '}';
    }
}

class Transaction {
    String transactionId;
    String userId;
    String type;
    double amount;
    String method;
    String accountNumber;

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId='" + transactionId + '\'' +
                ", userId='" + userId + '\'' +
                ", type='" + type + '\'' +
                ", amount=" + amount +
                ", method='" + method + '\'' +
                ", accountNumber='" + accountNumber + '\'' +
                '}';
    }
}

class BinMapping {
    String bankName;
    long rangeFrom;
    long rangeTo;
    String cardType;
    String country;

    @Override
    public String toString() {
        return "BinMapping{" +
                "bankName='" + bankName + '\'' +
                ", rangeFrom=" + rangeFrom +
                ", rangeTo=" + rangeTo +
                ", cardType='" + cardType + '\'' +
                ", country='" + country + '\'' +
                '}';
    }
}

class Event {
    public static final String STATUS_DECLINED = "DECLINED";
    public static final String STATUS_APPROVED = "APPROVED";

    public String transactionId;
    public String status;
    public String message;

    @Override
    public String toString() {
        return "Event{" +
                "transactionId='" + transactionId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                '}';
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


