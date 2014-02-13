package org.multibit.hd.ui.events.view;

/**
 * <p>Event to provide the following to the View Event API:</p>
 * <ul>
 * <li>Remove the given hardware wallet from the controller</li>
 * </ul>
 *
 * @since 0.0.1
 *  
 */
public class HardwareWalletRemovedEvent implements ViewEvent {

  private final String name;

  public HardwareWalletRemovedEvent(String name) {
    this.name = name;
  }

  /**
   * TODO Consider WalletId with name provided later
   *
   * @return The wallet name
   */
  public String getName() {
    return name;
  }
}
