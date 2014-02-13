package org.multibit.hd.ui.models;

/**
 * <p>Value object to provide the following to Hardware Wallet API:</p>
 * <ul>
 * <li>Provision of state for a new hardware wallet</li>
 * </ul>
 *
 * @since 0.0.1
 *         
 */
public class HardwareWalletModel implements Model<String> {

  private String name;

  HardwareWalletModel(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  @Override
  public String getValue() {
    return name;
  }

  @Override
  public void setValue(String value) {
    this.name = value;
  }

}
