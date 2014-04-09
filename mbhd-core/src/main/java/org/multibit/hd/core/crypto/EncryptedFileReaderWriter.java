package org.multibit.hd.core.crypto;

import com.google.bitcoin.core.Utils;
import com.google.bitcoin.crypto.KeyCrypterScrypt;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import org.bitcoinj.wallet.Protos;
import org.multibit.hd.brit.crypto.AESUtils;
import org.multibit.hd.core.exceptions.EncryptedFileReaderWriterException;
import org.multibit.hd.core.managers.WalletManager;
import org.multibit.hd.core.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.io.*;
import java.util.Arrays;

/**
 *  <p>Reader / Writer to provide the following to Services:<br>
 *  <ul>
 *  <li>load an AES encrypted file</li>
 * <li>write an AES encrypted file</li>
 *  </ul>
 *  Example:<br>
 *  <pre>
 *  </pre>
 *  </p>
 *  
 */
public class EncryptedFileReaderWriter {
  private static final Logger log = LoggerFactory.getLogger(WalletManager.class);


  /**
   * Decrypt an AES encrypted file and return it as an input Stream
   */
  public static ByteArrayInputStream readAndDecrypt(File encryptedProtobufFile, CharSequence password) throws EncryptedFileReaderWriterException {
    Preconditions.checkNotNull(encryptedProtobufFile);
    Preconditions.checkNotNull(password);
    try {
      // Read the encrypted file in and decrypt it.
      byte[] encryptedWalletBytes = org.multibit.hd.brit.utils.FileUtils.readFile(encryptedProtobufFile);
      log.debug("Encrypted wallet bytes after load:\n" + Utils.bytesToHexString(encryptedWalletBytes));

      KeyCrypterScrypt keyCrypterScrypt = new KeyCrypterScrypt(makeScryptParameters());
      KeyParameter keyParameter = keyCrypterScrypt.deriveKey(password);

      // Decrypt the wallet bytes
      byte[] decryptedBytes = AESUtils.decrypt(encryptedWalletBytes, keyParameter, WalletManager.AES_INITIALISATION_VECTOR);

      return new ByteArrayInputStream(decryptedBytes);
    } catch (IOException ioe) {
      throw new EncryptedFileReaderWriterException("Cannot read and decrypt the file '" + encryptedProtobufFile.getAbsolutePath() + "'", ioe);
    }
  }

  /**
   * Encrypt a byte array and output to a file output stream
   */
  public static void encryptAndWrite(byte[] unencryptedBytes, CharSequence password, FileOutputStream fileOutputStream) throws EncryptedFileReaderWriterException {
    try {
      KeyCrypterScrypt keyCrypterScrypt = new KeyCrypterScrypt(makeScryptParameters());
       KeyParameter keyParameter = keyCrypterScrypt.deriveKey(password);

      // Create an AES encoded version of the unencryptedBytes, using the password
      byte[] encryptedBytes = AESUtils.encrypt(unencryptedBytes, keyParameter, WalletManager.AES_INITIALISATION_VECTOR);

      log.debug("Encrypted wallet bytes (original):\n" + Utils.bytesToHexString(encryptedBytes));

      // Check that the encryption is reversible
      byte[] rebornBytes = AESUtils.decrypt(encryptedBytes, keyParameter, WalletManager.AES_INITIALISATION_VECTOR);

      if (Arrays.equals(unencryptedBytes, rebornBytes)) {
        // Save encrypted bytes

        ByteArrayInputStream encryptedWalletByteArrayInputStream = new ByteArrayInputStream(encryptedBytes);
        FileUtils.writeFile(encryptedWalletByteArrayInputStream, fileOutputStream);
      } else {
        throw new EncryptedFileReaderWriterException("The encryption was not reversible so aborting.");
      }
    } catch (IOException ioe) {
      throw new EncryptedFileReaderWriterException("Cannot encryptAndWrite", ioe);
    }
  }

  public static File makeAESEncryptedCopyAndDeleteOriginal(File fileToEncrypt, CharSequence password) throws EncryptedFileReaderWriterException {
    Preconditions.checkNotNull(fileToEncrypt);
    Preconditions.checkNotNull(password);
    try {
      KeyCrypterScrypt keyCrypterScrypt = new KeyCrypterScrypt(makeScryptParameters());
      KeyParameter keyParameter = keyCrypterScrypt.deriveKey(password);
      // TODO - cache keyParameter

      // Read in the file
      byte[] unencryptedBytes = org.multibit.hd.brit.utils.FileUtils.readFile(fileToEncrypt);

      // Create an AES encoded version of the newlySavedFile, using the wallet password
      byte[] encryptedBytes = AESUtils.encrypt(unencryptedBytes, keyParameter, WalletManager.AES_INITIALISATION_VECTOR);

      log.debug("Encrypted wallet bytes (original):\n" + Utils.bytesToHexString(encryptedBytes));

      // Check that the encryption is reversible
      byte[] rebornBytes = AESUtils.decrypt(encryptedBytes, keyParameter, WalletManager.AES_INITIALISATION_VECTOR);

      if (Arrays.equals(unencryptedBytes, rebornBytes)) {
        // Save encrypted bytes
        File encryptedFilename = new File(fileToEncrypt.getAbsoluteFile() + WalletManager.MBHD_AES_SUFFIX);
        ByteArrayInputStream encryptedWalletByteArrayInputStream = new ByteArrayInputStream(encryptedBytes);
        FileOutputStream encryptedWalletOutputStream = new FileOutputStream(encryptedFilename);
        FileUtils.writeFile(encryptedWalletByteArrayInputStream, encryptedWalletOutputStream);

        if (encryptedFilename.length() == encryptedBytes.length) {
          FileUtils.secureDelete(fileToEncrypt);
        } else {
          // The saved file isn't the correct size - do not delete the original
          return null;
        }

        return encryptedFilename;
      } else {
        log.error("The file encryption was not reversible. Aborting. This means the file {} is being stored unencrypted", fileToEncrypt.getAbsolutePath());
        return null;
      }
    } catch (IOException ioe) {
      throw new EncryptedFileReaderWriterException("Cannot makeAESEncryptedCopyAndDeleteOriginal for file '" + fileToEncrypt.getAbsolutePath() + "'", ioe);
    }
  }

  public static Protos.ScryptParameters makeScryptParameters() {
    Protos.ScryptParameters.Builder scryptParametersBuilder = Protos.ScryptParameters.newBuilder().setSalt(ByteString.copyFrom(WalletManager.SCRYPT_SALT));
    return scryptParametersBuilder.build();
  }
}
