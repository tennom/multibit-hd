package org.multibit.hd.ui.events.view;

import com.google.common.base.Preconditions;
import org.multibit.hd.ui.models.HardwareWalletModel;

/**
 * <p>Event to provide the following to View Event API:</p>
 * <ul>
 * <li>Adding a hardware wallet model to the controller</li>
 * </ul>
 *
 * @since 0.0.1
 *         
 */
public class HardwareWalletAddedEvent implements ViewEvent {

  private final HardwareWalletModel hardwareWalletModel;

  /**
   * @param hardwareWalletModel The hardware wallet model
   */
  public HardwareWalletAddedEvent(HardwareWalletModel hardwareWalletModel) {

    Preconditions.checkNotNull(hardwareWalletModel,"'hardwareWalletModel' must be present");

    this.hardwareWalletModel = hardwareWalletModel;
  }

  /**
   * @return The hardware wallet model
   */
  public HardwareWalletModel getHardwareWalletModel() {
    return hardwareWalletModel;
  }
}
