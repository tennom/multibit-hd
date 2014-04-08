package org.multibit.hd.core.managers;

import com.google.bitcoin.crypto.KeyCrypterScrypt;
import com.google.bitcoin.wallet.WalletFiles;
import com.google.common.base.Optional;
import org.multibit.hd.brit.crypto.AESUtils;
import org.multibit.hd.brit.utils.FileUtils;
import org.multibit.hd.core.dto.WalletData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 *  <p>Listener to provide the following to WalletManager:</p>
 *  <ul>
 *  <li>Saving of rolling wallet backups and zip backups</li>
 *  </ul>
 *  </p>
 *  
 */
public class WalletAutoSaveListener implements WalletFiles.Listener {
  private static final Logger log = LoggerFactory.getLogger(WalletManager.class);

  @Override
  public void onBeforeAutoSave(File tempFile) {
    log.debug("Just about to save wallet to tempFile '" + tempFile.getAbsolutePath() + "'");
  }

  @Override
  public void onAfterAutoSave(File newlySavedFile) {
    log.debug("Have just saved wallet to newlySavedFile '" + newlySavedFile.getAbsolutePath() + "'");

    Optional<WalletData> walletData = WalletManager.INSTANCE.getCurrentWalletData();
    if (walletData.isPresent()) {
      try {
        CharSequence password = walletData.get().getPassword();
        if (password == null) {
          log.warn("There is no password specified - the wallet is stored unencrypted");
        } else {
          KeyCrypterScrypt keyCrypterScrypt = new KeyCrypterScrypt();
          KeyParameter keyParameter = keyCrypterScrypt.deriveKey(password);
          // TODO - cache keyParameter

          // Read in the newlySavedFile
          byte[] walletBytes = FileUtils.readFile(newlySavedFile);

          // Create an AES encoded version of the newlySavedFile, using the wallet password
          byte[] encryptedWalletBytes = AESUtils.encrypt(walletBytes, keyParameter, WalletManager.AES_INITIALISATION_VECTOR);

          // Check that the encryption is reversible
          byte[] rebornBytes = AESUtils.decrypt(encryptedWalletBytes, keyParameter, WalletManager.AES_INITIALISATION_VECTOR);

          if (Arrays.equals(walletBytes, rebornBytes)) {
            // Save encrypted bytes
            File encryptedWalletFilename = new File(newlySavedFile.getAbsoluteFile() + WalletManager.MBHD_AES_SUFFIX);
            ByteArrayInputStream encryptedWalletByteArrayInputStream = new ByteArrayInputStream(encryptedWalletBytes);
            FileOutputStream encryptedWalletOutputStream = new FileOutputStream(encryptedWalletFilename);
            FileUtils.writeFile(encryptedWalletByteArrayInputStream, encryptedWalletOutputStream);

            // TODO check file has written ok and delete unencrypted wallet
          } else {
            log.error("The wallet encryption was not reversible. Aborting. This means your wallet is being stored unencrypted");
          }
        }
        BackupManager.INSTANCE.createRollingBackup(walletData.get());

        BackupManager.INSTANCE.createLocalAndCloudBackup(walletData.get().getWalletId());
        // TODO save the cloud backups at a slower rate than the local backups to save bandwidth - say a factor of 2 or 3
      } catch (IOException ioe) {
        log.error("No backups created. The error was '" + ioe.getMessage() + "'.");
      }
    } else {
      log.error("No AES wallet encryption nor backups created as there was no wallet data to backup.");
    }
  }
}
