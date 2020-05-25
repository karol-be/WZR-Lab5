import java.io.Serializable;

public class SellerOffer implements Serializable {
    public String Title;
    public double Price;

    public SellerOffer(String title, double price) {
        Title = title;
        Price = price;
    }
}
