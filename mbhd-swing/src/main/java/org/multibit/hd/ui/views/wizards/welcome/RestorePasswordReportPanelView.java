package org.multibit.hd.ui.views.wizards.welcome;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import net.miginfocom.swing.MigLayout;
import org.multibit.hd.brit.seed_phrase.Bip39SeedPhraseGenerator;
import org.multibit.hd.brit.seed_phrase.SeedPhraseGenerator;
import org.multibit.hd.core.crypto.AESUtils;
import org.multibit.hd.core.dto.WalletId;
import org.multibit.hd.core.dto.WalletSummary;
import org.multibit.hd.core.managers.InstallationManager;
import org.multibit.hd.core.managers.WalletManager;
import org.multibit.hd.ui.MultiBitUI;
import org.multibit.hd.ui.languages.Languages;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.AccessibilityDecorator;
import org.multibit.hd.ui.views.components.Labels;
import org.multibit.hd.ui.views.components.Panels;
import org.multibit.hd.ui.views.components.panels.PanelDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeDecorator;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;
import org.multibit.hd.ui.views.themes.Themes;
import org.multibit.hd.ui.views.wizards.AbstractWizard;
import org.multibit.hd.ui.views.wizards.AbstractWizardPanelView;
import org.spongycastle.crypto.params.KeyParameter;

import javax.swing.*;
import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * <p>View to provide the following to UI:</p>
 * <ul>
 * <li>Show password recovery progress report</li>
 * </ul>
 *
 * @since 0.0.1
 *  
 */
public class RestorePasswordReportPanelView extends AbstractWizardPanelView<WelcomeWizardModel, Boolean> {

  private JLabel passwordRecoveryStatus;

  /**
   * @param wizard The wizard managing the states
   */
  public RestorePasswordReportPanelView(AbstractWizard<WelcomeWizardModel> wizard, String panelName) {

    super(wizard, panelName, MessageKey.RESTORE_PASSWORD_REPORT_TITLE, AwesomeIcon.MAGIC);

  }

  @Override
  public void newPanelModel() {

    // Nothing to bind

  }

  @Override
  public void initialiseContent(JPanel contentPanel) {

    contentPanel.setLayout(new MigLayout(
      Panels.migXYLayout(),
      "[][][]", // Column constraints
      "[]10[]10[]" // Row constraints
    ));

    // Apply the theme
    contentPanel.setBackground(Themes.currentTheme.detailPanelBackground());

    passwordRecoveryStatus = Labels.newStatusLabel(Optional.<MessageKey>absent(), null, Optional.<Boolean>absent());

    contentPanel.add(passwordRecoveryStatus, "wrap");

    recoverPassword();

  }

  @Override
  protected void initialiseButtons(AbstractWizard<WelcomeWizardModel> wizard) {

    PanelDecorator.addExitCancelPreviousFinish(this, wizard);

  }

  @Override
  public void afterShow() {

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        getFinishButton().requestFocusInWindow();
      }
    });

  }

  @Override
  public void updateFromComponentModels(Optional componentModel) {
    // Do nothing - panel model is updated via an action and wizard model is not applicable
  }

  /**
   * Attempt to recover the password and display it to the user
   */
  private void recoverPassword() {

    WelcomeWizardModel model = getWizardModel();

    // Locate the installation directory
    File applicationDataDirectory = InstallationManager.getOrCreateApplicationDataDirectory();

    // Work out the seed, wallet id and wallet directory
    List<String> seedPhrase = model.getRestorePasswordEnterSeedPhraseModel().getSeedPhrase();
    SeedPhraseGenerator seedPhraseGenerator = new Bip39SeedPhraseGenerator();
    byte[] seed = seedPhraseGenerator.convertToSeed(seedPhrase);
    WalletId walletId = new WalletId(seed);

    String walletRoot = applicationDataDirectory.getAbsolutePath() + File.separator + WalletManager.createWalletRoot(walletId);
    File walletDirectory = new File(walletRoot);

    WalletSummary walletSummary;
    if (walletDirectory.isDirectory()) {
      walletSummary = WalletManager.getOrCreateWalletSummary(walletDirectory, walletId);
    } else {
      // Failed
      passwordRecoveryStatus.setText(Languages.safeText(MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL));
      AccessibilityDecorator.apply(passwordRecoveryStatus, MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL);
      AwesomeDecorator.applyIcon(AwesomeIcon.TIMES, passwordRecoveryStatus, true, MultiBitUI.NORMAL_ICON_SIZE);
      return;
    }

    // Check for present but empty wallet directory
    if (walletSummary.getEncryptedPassword() == null) {
      // Failed
      passwordRecoveryStatus.setText(Languages.safeText(MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL));
      AccessibilityDecorator.apply(passwordRecoveryStatus, MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL);
      AwesomeDecorator.applyIcon(AwesomeIcon.TIMES, passwordRecoveryStatus, true, MultiBitUI.NORMAL_ICON_SIZE);
      return;
    }

    // Read the encrypted wallet password and decrypt with an AES key derived from the seed
    KeyParameter backupAESKey;
    try {
      backupAESKey = AESUtils.createAESKey(seed, WalletManager.SCRYPT_SALT);
    } catch (NoSuchAlgorithmException e) {
      // Failed
      log.error(e.getMessage(), e);
      passwordRecoveryStatus.setText(Languages.safeText(MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL));
      AccessibilityDecorator.apply(passwordRecoveryStatus, MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL);
      AwesomeDecorator.applyIcon(AwesomeIcon.TIMES, passwordRecoveryStatus, true, MultiBitUI.NORMAL_ICON_SIZE);
      return;
    }

    byte[] decryptedPaddedWalletPasswordBytes = org.multibit.hd.brit.crypto.AESUtils.decrypt(
      // Get the padded password out of the wallet summary. This is put in when a wallet is created.
      walletSummary.getEncryptedPassword(),
      backupAESKey,
      WalletManager.AES_INITIALISATION_VECTOR
    );

    try {
      byte[] decryptedWalletPasswordBytes = WalletManager.unpadPasswordBytes(decryptedPaddedWalletPasswordBytes);

      // Check the result
      if (decryptedWalletPasswordBytes == null || decryptedWalletPasswordBytes.length == 0) {
        // Failed
        passwordRecoveryStatus.setText(Languages.safeText(MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL));
        AccessibilityDecorator.apply(passwordRecoveryStatus, MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL);
        AwesomeDecorator.applyIcon(AwesomeIcon.TIMES, passwordRecoveryStatus, true, MultiBitUI.NORMAL_ICON_SIZE);
        return;
      }

      // Must be OK to be here
      String decryptedWalletPassword = new String(decryptedWalletPasswordBytes, Charsets.UTF_8);
      passwordRecoveryStatus.setText(Languages.safeText(MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_SUCCESS, decryptedWalletPassword));
      AccessibilityDecorator.apply(passwordRecoveryStatus, MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_SUCCESS);
      AwesomeDecorator.applyIcon(AwesomeIcon.CHECK, passwordRecoveryStatus, true, MultiBitUI.NORMAL_ICON_SIZE);

    } catch (IllegalStateException ise) {
      // Probably the unpad failed
      passwordRecoveryStatus.setText(Languages.safeText(MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL));
      AccessibilityDecorator.apply(passwordRecoveryStatus, MessageKey.RESTORE_PASSWORD_REPORT_MESSAGE_FAIL);
      AwesomeDecorator.applyIcon(AwesomeIcon.TIMES, passwordRecoveryStatus, true, MultiBitUI.NORMAL_ICON_SIZE);
      return;
    }
  }

}
