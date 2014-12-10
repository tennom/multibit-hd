package org.multibit.hd.core.store;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.joda.time.DateTime;
import org.multibit.hd.core.dto.FiatPayment;

import java.util.Collection;

/**
 * <p>DTO to provide the following to WalletService:</p>
 * <ul>
 * <li>Additional information related to a transaction that is not stored in the bitcoinj transaction</li>
 * </ul>
 * </p>
 *
 */
public class TemplateData {
  /*
  *
  * */
  private String hash;
  private Collection<Address> recipients;
  private FiatPayment amountFiat;
  private Coin amountBTC;
  private String label;
  private DateTime createdDate;
  private String note;


  public String getHash() {
    return hash;
  }

  public void setHash(String hash) {
    this.hash = hash;
  }

  /*
  * @return the recipients
  *
  */
  public Collection<Address> getRecipients() {
    return recipients;
  }

  public void setRecipients(Collection<Address> recipients) {
    this.recipients = recipients;
  }
  /**
   * @return The amount in fiat
   */
  public FiatPayment getAmountFiat() {
    return amountFiat;
  }

  public void setAmountFiat(FiatPayment amountFiat) {
    this.amountFiat = amountFiat;
  }

  /*
  * @return the amount in bitcoin
  * */
  public Coin getAmountBTC() {
    return amountBTC;
  }

  public void setAmountBTC(Coin amountBTC) {
    this.amountBTC = amountBTC;
  }

  /**
   * @return the label containing the title of a payment
   */
  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }


  public DateTime getCreatedDate() {
    return createdDate;
  }

  public void setCreatedDate(DateTime createdDate) {
    this.createdDate = createdDate;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }



  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TemplateData that = (TemplateData) o;

    if (recipients != null ? !recipients.equals(that.recipients) : that.recipients !=null) return false;
    if (amountFiat != null ? !amountFiat.equals(that.amountFiat) : that.amountFiat != null) return false;
    if (amountBTC != null ? !amountBTC.equals(that.amountBTC) : that.amountBTC != null) return false;
    if (label != null ? !label.equals(that.label) : that.label != null) return false;
    if (hash != null ? !hash.equals(that.hash) : that.hash != null) return false;
    if (createdDate != null ? !createdDate.equals(that.createdDate) : that.createdDate != null) return false;
    if (note != null ? !note.equals(that.note) : that.note != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = hash != null ? hash.hashCode() : 0;
    result = 31 * result + (recipients != null ? recipients.hashCode() : 0);
    result = 31 * result + (amountFiat != null ? amountFiat.hashCode() : 0);
    result = 31 * result + (amountBTC !=null ? amountBTC.hashCode() : 0);
    result = 31 * result + (label != null ? label.hashCode() : 0);
//    result = 31 * result + (sentBySelf ? 1 : 0);
    result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
    result = 31 * result + (note != null ? note.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "TransactionInfo{" +
      "hash='" + hash + '\'' +
      ", recipeint='" + recipients + '\'' +
      ", amountFiat=" + amountFiat + '\'' +
      ", amountBTC=" + amountBTC + '\'' +
      ", label=" + label +
      ", createdDate=" + createdDate +
      ", note=" + note +
      '}';
  }
}
