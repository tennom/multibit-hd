package org.multibit.hd.ui.fest.use_cases.tools.empty_wallet;

import org.fest.swing.fixture.FrameFixture;
import org.multibit.hd.ui.fest.use_cases.AbstractFestUseCase;
import org.multibit.hd.ui.languages.MessageKey;

import java.util.Map;

/**
 * <p>Use case to provide the following to FEST testing:</p>
 * <ul>
 * <li>Verify the "tools" screen verify message wizard shows</li>
 * </ul>
 * <p>Requires the "tools" screen to be showing</p>
 *
 * @since 0.0.1
 *  
 */
public class ShowThenCancelEmptyWalletUseCase extends AbstractFestUseCase {

  public ShowThenCancelEmptyWalletUseCase(FrameFixture window) {
    super(window);
  }

  @Override
  public void execute(Map<String, Object> parameters) {

    // Click on "empty wallet"
    window
      .button(MessageKey.SHOW_EMPTY_WALLET_WIZARD.getKey())
      .click();

    // Verify the "empty wallet" wizard appears
    assertLabelText(MessageKey.EMPTY_WALLET_TITLE);

    // Verify cancel is present
    window
      .button(MessageKey.CANCEL.getKey())
      .requireVisible()
      .requireEnabled();

    // Click Cancel
    window
      .button(MessageKey.CANCEL.getKey())
      .click();

    // Verify the underlying screen is back
    window
      .button(MessageKey.SHOW_EDIT_WALLET_WIZARD.getKey())
      .requireVisible()
      .requireEnabled();

  }

}
