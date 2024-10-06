public class User {
    private String userId;
    private String username;
    private double balance;
    private String country;
    private boolean frozen;
    private double depositMin;
    private double depositMax;
    private double withdrawMin;
    private double withdrawMax;

    // Constructor
    public User(String userId, String username, double balance, String country, boolean frozen,
                double depositMin, double depositMax, double withdrawMin, double withdrawMax) {
        this.userId = userId;
        this.username = username;
        this.balance = balance;
        this.country = country;
        this.frozen = frozen;
        this.depositMin = depositMin;
        this.depositMax = depositMax;
        this.withdrawMin = withdrawMin;
        this.withdrawMax = withdrawMax;
    }



    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public double getDepositMin() {
        return depositMin;
    }

    public void setDepositMin(double depositMin) {
        this.depositMin = depositMin;
    }

    public double getDepositMax() {
        return depositMax;
    }

    public void setDepositMax(double depositMax) {
        this.depositMax = depositMax;
    }

    public double getWithdrawMin() {
        return withdrawMin;
    }

    public void setWithdrawMin(double withdrawMin) {
        this.withdrawMin = withdrawMin;
    }

    public double getWithdrawMax() {
        return withdrawMax;
    }

    public void setWithdrawMax(double withdrawMax) {
        this.withdrawMax = withdrawMax;
    }

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
