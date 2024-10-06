public class BinMapping {
    private String bankName;
    private long rangeFrom;
    private long rangeTo;
    private String cardType;
    private String country;

    // Constructor
    public BinMapping(String bankName, long rangeFrom, long rangeTo, String cardType, String country) {
        this.bankName = bankName;
        this.rangeFrom = rangeFrom;
        this.rangeTo = rangeTo;
        this.cardType = cardType;
        this.country = country;
    }

    // Getters and Setters
    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public long getRangeFrom() {
        return rangeFrom;
    }

    public void setRangeFrom(long rangeFrom) {
        this.rangeFrom = rangeFrom;
    }

    public long getRangeTo() {
        return rangeTo;
    }

    public void setRangeTo(long rangeTo) {
        this.rangeTo = rangeTo;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

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
