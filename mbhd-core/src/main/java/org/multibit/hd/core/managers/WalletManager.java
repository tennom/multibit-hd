package org.multibit.hd.core.managers;

import com.google.bitcoin.core.*;
import com.google.bitcoin.crypto.KeyCrypter;
import com.google.bitcoin.crypto.KeyCrypterScrypt;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.store.WalletProtobufSerializer;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import org.bitcoinj.wallet.Protos;
import org.multibit.hd.brit.crypto.AESUtils;
import org.multibit.hd.brit.extensions.MatcherResponseWalletExtension;
import org.multibit.hd.brit.extensions.SendFeeDtoWalletExtension;
import org.multibit.hd.core.config.Configurations;
import org.multibit.hd.core.dto.WalletData;
import org.multibit.hd.core.dto.WalletId;
import org.multibit.hd.core.events.CoreEvents;
import org.multibit.hd.core.events.TransactionSeenEvent;
import org.multibit.hd.core.exceptions.WalletLoadException;
import org.multibit.hd.core.exceptions.WalletVersionException;
import org.multibit.hd.core.services.BitcoinNetworkService;
import org.multibit.hd.core.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.asn1.sec.SECNamedCurves;
import org.spongycastle.asn1.x9.X9ECParameters;
import org.spongycastle.crypto.params.KeyParameter;

import java.io.*;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 *  <p>Manager to provide the following to core users:</p>
 *  <ul>
 *  <li>create wallet</li>
 *  <li>save wallet wallet</li>
 *  <li>load wallet wallet</li>
 * <li>tracks the current wallet and the list of wallet directories</li>
 *  </ul>
 */
public enum WalletManager implements WalletEventListener {
  INSTANCE {
    @Override
    public void onCoinsReceived(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance) {
      // Emit an event so that GUI elements can update as required
      CoreEvents.fireTransactionSeenEvent(new TransactionSeenEvent(tx));
    }

    @Override
    public void onCoinsSent(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance) {
      // Emit an event so that GUI elements can update as required
      CoreEvents.fireTransactionSeenEvent(new TransactionSeenEvent(tx));
    }

    @Override
    public void onReorganize(Wallet wallet) {

    }

    @Override
    public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
      // Emit an event so that GUI elements can update as required
      CoreEvents.fireTransactionSeenEvent(new TransactionSeenEvent(tx));
    }

    @Override
    public void onWalletChanged(Wallet wallet) {

    }

    @Override
    public void onKeysAdded(Wallet wallet, List<ECKey> keys) {

    }

    @Override
    public void onScriptsAdded(Wallet wallet, List<Script> scripts) {

    }
  };

  private static final int AUTOSAVE_DELAY = 20000; // millisecond

  private static WalletProtobufSerializer walletProtobufSerializer;

  static {
    walletProtobufSerializer = new WalletProtobufSerializer();
    // TODO was originally multibit protobuf serializer - ok ?
  }

  private static final Logger log = LoggerFactory.getLogger(WalletManager.class);

  public static final String WALLET_DIRECTORY_PREFIX = "mbhd";

  public static final String SEPARATOR = "-";

  // The format of the wallet directories is WALLET_DIRECTORY_PREFIX + a wallet id.
  // A walletid is 5 groups of 4 bytes in lowercase hex, with a "-' separator e.g. mbhd-11111111-22222222-33333333-44444444-55555555
  public static final String REGEX_FOR_WALLET_DIRECTORY = "^" + WALLET_DIRECTORY_PREFIX + SEPARATOR + "[0-9a-f]{8}"
          + SEPARATOR + "[0-9a-f]{8}" + SEPARATOR + "[0-9a-f]{8}" + SEPARATOR + "[0-9a-f]{8}" + SEPARATOR + "[0-9a-f]{8}$";

  /**
   * The wallet version number for protobuf encrypted wallets - compatible with MultiBit
   */
  public static final int ENCRYPTED_WALLET_VERSION = 3; // TODO - need a new version when the wallet HD format is created

  public static final String MBHD_WALLET_PREFIX = "mbhd";
  public static final String MBHD_WALLET_SUFFIX = ".wallet";
  public static final String MBHD_AES_SUFFIX = ".aes";
  public static final String MBHD_WALLET_NAME = MBHD_WALLET_PREFIX + MBHD_WALLET_SUFFIX;

  private File applicationDataDirectory;

  private Optional<WalletData> currentWalletData = Optional.absent();

  /**
   * The initialisation vector to use for AES encryption of output files (such as wallets)
   * There is no particular significance to the value of these bytes
   */
  public static final byte[] AES_INITIALISATION_VECTOR = new byte[]{(byte) 0xa3, (byte) 0x44, (byte) 0x39, (byte) 0x1f, (byte) 0x53, (byte) 0x83, (byte) 0x11,
          (byte) 0xb3, (byte) 0x29, (byte) 0x54, (byte) 0x86, (byte) 0x16, (byte) 0xc4, (byte) 0x89, (byte) 0x72, (byte) 0x3e};

  /**
   * The salt used for deriving the KeyParameter from the password in AES encryption
   */
  public static final byte[] SCRYPT_SALT = new byte[]{(byte) 0x35, (byte) 0x51, (byte) 0x03, (byte) 0x80, (byte) 0x75, (byte) 0xa3, (byte) 0xb0, (byte) 0xc5};

  /**
   * Initialise enum, loadContacts up the available wallets and find the current wallet
   *
   * @param applicationDataDirectory The directory in which to writeContacts and read wallets.
   */
  public void initialiseAndLoadWalletFromConfig(File applicationDataDirectory, CharSequence password) {
    Preconditions.checkNotNull(password);
    this.applicationDataDirectory = applicationDataDirectory;

    // Work out the list of available wallets in the application data directory
    List<File> walletDirectories = findWalletDirectories(applicationDataDirectory);

    // TODO enable user to switch wallets - currently using the first

    // If a wallet directory is present try to load the wallet
    if (!walletDirectories.isEmpty()) {

      String walletFilename = walletDirectories.get(0) + File.separator + MBHD_WALLET_NAME + MBHD_AES_SUFFIX;
      WalletData walletData = loadFromFile(new File(walletFilename), password);
      currentWalletData = Optional.of(walletData);
    } else {
      currentWalletData = Optional.absent();
    }

  }

  /**
   * Create a wallet that contains only a single, random private key.
   * This is stored in the MultiBitHD application data directory
   * The name of the wallet file is derived from the seed.
   * If the wallet file already exists it is loaded and returned (and the input password is not used)
   *
   * @param seed     the seed used to initialiseAndLoadWalletFromConfig the wallet
   * @param password to use to encryptBytes the wallet
   * @return WalletData containing the wallet object and the walletId (used in storage etc)
   * @throws IllegalStateException  if applicationDataDirectory is incorrect
   * @throws WalletLoadException    if there is already a simple wallet created but it could not be loaded
   * @throws WalletVersionException if there is already a simple wallet but the wallet version cannot be understood
   */
  public WalletData createWallet(byte[] seed, CharSequence password) throws WalletLoadException, WalletVersionException, IOException {

    File applicationDataDirectory = InstallationManager.getOrCreateApplicationDataDirectory();
    return createWallet(applicationDataDirectory.getAbsolutePath(), seed, password);

  }

  /**
   * Create a wallet that contains only a single, random private key.
   * This is stored in the specified directory.
   * The name of the wallet file is derived from the seed.
   * <p/>
   * If the wallet file already exists it is loaded and returned (and the input password is not used)
   * <p/>
   * Autosave is hooked up so that the wallet is changed on modification
   *
   * @param parentDirectoryName the name of the directory in which the wallet directory will be created (normally the application data directory)
   * @param seed                the seed used to initialiseAndLoadWalletFromConfig the wallet
   * @param password            to use to encryptBytes the wallet
   * @return WalletData containing the wallet object and the walletId (used in storage etc)
   * @throws IllegalStateException  if applicationDataDirectory is incorrect
   * @throws WalletLoadException    if there is already a wallet created but it could not be loaded
   * @throws WalletVersionException if there is already a wallet but the wallet version cannot be understood
   */
  public WalletData createWallet(String parentDirectoryName, byte[] seed, CharSequence password) throws WalletLoadException, WalletVersionException, IOException {
    Preconditions.checkNotNull(parentDirectoryName);
    Preconditions.checkNotNull(seed);
    Preconditions.checkNotNull(password);

    WalletData walletDataToReturn;

    // Create a wallet id from the seed to work out the wallet root directory
    WalletId walletId = new WalletId(seed);
    String walletRoot = createWalletRoot(walletId);

    File walletDirectory = WalletManager.getWalletDirectory(parentDirectoryName, walletRoot);
    File walletFile = new File(walletDirectory.getAbsolutePath() + File.separator + MBHD_WALLET_NAME);
    File walletFileWithAES = new File(walletDirectory.getAbsolutePath() + File.separator + MBHD_WALLET_NAME + MBHD_AES_SUFFIX);
    if (walletFileWithAES.exists()) {
      // There is already a wallet created with this root - if so load it and return that
      walletDataToReturn = loadFromFile(walletFileWithAES, password);
      if (Configurations.currentConfiguration != null) {
        Configurations.currentConfiguration.getApplicationConfiguration().setCurrentWalletRoot(walletRoot);
      }
      setCurrentWalletData(walletDataToReturn);
    } else {
      // Create the containing directory if it does not exist
      if (!walletDirectory.exists()) {
        if (!walletDirectory.mkdir()) {
          throw new IllegalStateException("The directory for the wallet '" + walletDirectory.getAbsoluteFile() + "' could not be created");
        }
      }
      // Create a wallet with a single private key using the seed (modulo-ed), encrypted with the password
      KeyCrypter keyCrypter = new KeyCrypterScrypt(makeScryptParameters());

      Wallet walletToReturn = new Wallet(BitcoinNetworkService.NETWORK_PARAMETERS, keyCrypter);
      walletToReturn.setVersion(ENCRYPTED_WALLET_VERSION);

      // Add the 'zero index' key into the wallet
      // Ensure that the seed is within the Bitcoin EC group.
      BigInteger privateKeyToUse = moduloSeedByECGroupSize(new BigInteger(1, seed));

      ECKey newKey = new ECKey(privateKeyToUse);
      newKey = newKey.encrypt(walletToReturn.getKeyCrypter(), walletToReturn.getKeyCrypter().deriveKey(password));
      walletToReturn.addKey(newKey);

      // Set up autosave on the wallet.
      // This ensures the wallet is saved on modification
      // The listener has a 'after save' callback which ensures rolling backups and local/ cloud backups are also saved where necessary
      walletToReturn.autosaveToFile(walletFile, AUTOSAVE_DELAY, TimeUnit.MILLISECONDS, new WalletAutoSaveListener());

      // Save it now to ensure it is on the disk
      walletToReturn.saveToFile(walletFile);
      makeAESEncryptedCopyAndDeleteOriginal(walletFile, password);

      if (Configurations.currentConfiguration != null) {
        Configurations.currentConfiguration.getApplicationConfiguration().setCurrentWalletRoot(walletRoot);
      }
      walletDataToReturn = new WalletData(walletId, walletToReturn);
      walletDataToReturn.setPassword(password);
      setCurrentWalletData(walletDataToReturn);
    }

    // See if there is a checkpoints file - if not then get the InstallationManager to copy one in
    String checkpointsFilename = walletDirectory.getAbsolutePath() + File.separator + InstallationManager.MBHD_PREFIX + InstallationManager.CHECKPOINTS_SUFFIX;
    InstallationManager.copyCheckpointsTo(checkpointsFilename);

    // Create an initial rolling backup and zip backup
    BackupManager.INSTANCE.createRollingBackup(currentWalletData.get(), password);
    BackupManager.INSTANCE.createLocalAndCloudBackup(currentWalletData.get().getWalletId());

    return walletDataToReturn;
  }

  /**
   * Create a new key for the wallet using the seed of the wallet and an index 0, 1,2,3,4 etc.
   * <p/>
   * TODO this will be replaced by the proper HD wallet algorithm when it is added to bitcoinj
   * Note that the "last used index"needs to be persisted - this is currently stored in the top level of the payments db
   * but it would be better in the wallet itself
   */
  public ECKey createAndAddNewWalletKey(Wallet wallet, CharSequence walletPassword, int indexToCreate) {

    Preconditions.checkState(wallet.getKeychainSize() > 0, "There is no 'first key' to derive subsequent keys from");

    // Get the private key from the first private key in the wallet - subsequent keys are derived from this.
    ECKey firstKey = wallet.getKeys().get(0);
    KeyParameter aesKey = wallet.getKeyCrypter().deriveKey(walletPassword);
    ECKey decryptedFirstKey = firstKey.decrypt(wallet.getKeyCrypter(), aesKey);

    // Ensure that the seed combined with the index is within the Bitcoin EC group.
    BigInteger privateKeyToUse = moduloSeedByECGroupSize(new BigInteger(1, decryptedFirstKey.getPrivKeyBytes()).add(BigInteger.valueOf(indexToCreate)));

    ECKey newKey = new ECKey(privateKeyToUse);
    newKey = newKey.encrypt(wallet.getKeyCrypter(), aesKey);
    wallet.addKey(newKey);

    return newKey;
  }

  /**
   * Ensure that the seed is within the range of the bitcoin EC group
   *
   * @param seedAsBigInteger the seed - converted to a BigInteger
   * @return the seed, guaranteed to be within the Bitcoin EC group range
   */
  private BigInteger moduloSeedByECGroupSize(BigInteger seedAsBigInteger) {

    X9ECParameters params = SECNamedCurves.getByName("secp256k1");
    BigInteger sizeOfGroup = params.getN();

    return seedAsBigInteger.mod(sizeOfGroup);
  }

  /**
   * Load up a Wallet from a specified wallet file.
   *
   * @param walletFile The file containing the wallet to load
   * @return Wallet - the loaded wallet
   * @throws IllegalArgumentException if wallet file is null
   * @throws WalletLoadException      if wallet does not load
   * @throws WalletVersionException   if the wallet is a version that is not supported by MultiBit HD
   */
  public WalletData loadFromFile(File walletFile, CharSequence password) throws WalletLoadException, WalletVersionException {
    Preconditions.checkNotNull(walletFile);
    Preconditions.checkNotNull(password);

    String walletFilename = walletFile.getAbsolutePath();
    String walletFilenameNoAESSuffix = walletFilename;

    WalletId walletId = WalletId.parseWalletFilename(walletFilename);

    try {
      if (isWalletSerialised(walletFile)) {
        // Serialised wallets are no longer supported.
        throw new WalletLoadException("Could not loadContacts wallet '" + walletFilename
                + "'. Serialized wallets are no longer supported.");
      }

      Wallet wallet;
      FileInputStream fileInputStream = null;
      try {
        InputStream inputStream;

        if (walletFilename.endsWith(MBHD_AES_SUFFIX)) {
          walletFilenameNoAESSuffix = walletFilename.substring(0, walletFilename.length() - MBHD_AES_SUFFIX.length());
          // Read the encrypted file in and decrypt it.
          byte[] encryptedWalletBytes = org.multibit.hd.brit.utils.FileUtils.readFile(new File(walletFilename));
          log.debug("Encrypted wallet bytes after load:\n" + Utils.bytesToHexString(encryptedWalletBytes));

          KeyCrypterScrypt keyCrypterScrypt = new KeyCrypterScrypt(makeScryptParameters());
          KeyParameter keyParameter = keyCrypterScrypt.deriveKey(password);

          // Decrypt the wallet bytes
          byte[] decryptedBytes = AESUtils.decrypt(encryptedWalletBytes, keyParameter, WalletManager.AES_INITIALISATION_VECTOR);

          inputStream = new ByteArrayInputStream(decryptedBytes);
        } else {
          // Not encrypted wallet
          fileInputStream = new FileInputStream(walletFile);
          inputStream = new BufferedInputStream(fileInputStream);
        }

        Protos.Wallet walletProto = WalletProtobufSerializer.parseToProto(inputStream);

        wallet = new Wallet(NetworkParameters.fromID(NetworkParameters.ID_MAINNET));
        wallet.addExtension(new SendFeeDtoWalletExtension());
        wallet.addExtension(new MatcherResponseWalletExtension());
        new WalletProtobufSerializer().readWallet(walletProto, wallet);

        log.debug("Wallet at read in from file:\n" + wallet.toString());
      } catch (WalletVersionException wve) {
        // We want this exception to propagate out.
        throw wve;
      } catch (Exception e) {
        log.error(e.getClass().getCanonicalName() + " " + e.getMessage());
        throw new WalletLoadException(e.getMessage(), e);
      } finally {
        if (fileInputStream != null) {
          fileInputStream.close();
        }
      }

      WalletData walletData = new WalletData(walletId, wallet);
      walletData.setPassword(password);
      setCurrentWalletData(walletData);

      // Set up autosave on the wallet.
      // This ensures the wallet is saved on modification
      // The listener has a 'post save' callback which:
      // + encrypts the wallet
      // + ensures rolling backups
      // + local/ cloud backups are also saved where necessary
      wallet.autosaveToFile(new File(walletFilenameNoAESSuffix), AUTOSAVE_DELAY, TimeUnit.MILLISECONDS, new WalletAutoSaveListener());

      return walletData;

    } catch (WalletVersionException wve) {
      // We want this to propagate out as is
      throw wve;
    } catch (Exception e) {
      log.error(e.getClass().getCanonicalName() + " " + e.getMessage());
      throw new WalletLoadException(e.getMessage(), e);
    }
  }

  /**
   * @param walletFile the wallet to test serialisation for
   * @return true if the wallet file specified is serialised (this format is no longer supported)
   */
  private boolean isWalletSerialised(File walletFile) {

    boolean isWalletSerialised = false;
    InputStream stream = null;
    try {
      // Determine what kind of wallet stream this is: Java Serialization
      // or protobuf format.
      stream = new BufferedInputStream(new FileInputStream(walletFile));
      isWalletSerialised = stream.read() == 0xac && stream.read() == 0xed;
    } catch (IOException e) {
      log.error(e.getClass().getCanonicalName() + " " + e.getMessage());
    } finally {
      if (stream != null) {
        try {
          stream.close();
        } catch (IOException e) {
          log.error(e.getClass().getCanonicalName() + " " + e.getMessage());
        }
      }
    }
    return isWalletSerialised;
  }

  /**
   * Create the name of the directory in which the wallet is stored
   *
   * @param walletId The wallet id to use
   * @return directoryName in which the wallet is stored.
   */
  public static String createWalletRoot(WalletId walletId) {
    return WALLET_DIRECTORY_PREFIX + SEPARATOR + walletId.toFormattedString();
  }

  /**
   * Create a directory composed of the root directory and a subdirectory.
   * The subdirectory is created if it does not exist
   *
   * @param parentDirectory The root directory in which to create the directory
   * @param walletRoot      The name of the wallet which will be used to create a subdirectory
   * @return The directory composed of parentDirectory plus the walletRoot
   * @throws IllegalStateException if wallet could not be created
   */
  public static File getWalletDirectory(String parentDirectory, String walletRoot) {
    String fullWalletDirectoryName = parentDirectory + File.separator + walletRoot;
    File walletDirectory = new File(fullWalletDirectoryName);

    if (!walletDirectory.exists()) {
      // Create the wallet directory.
      if (!walletDirectory.mkdir()) {
        throw new IllegalStateException("Could not create missing wallet directory '" + walletRoot + "'");
      }
    }

    if (!walletDirectory.isDirectory()) {
      throw new IllegalStateException("Wallet directory '" + walletRoot + "' is not actually a directory");
    }

    return walletDirectory;
  }

  /**
   * <p>Work out what wallets are available in a directory (typically the user data directory).
   * This is achieved by looking for directories with a name like <code>"mbhd-walletId"</code>
   *
   * @param directoryToSearch The directory to search
   * @return A list of files of wallet directories
   */
  public List<File> findWalletDirectories(File directoryToSearch) {
    Preconditions.checkNotNull(directoryToSearch);

    File[] files = directoryToSearch.listFiles();
    List<File> walletDirectories = Lists.newArrayList();

    // Look for file names with format "mbhd"-"walletid" and are not empty.
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          String filename = file.getName();
          if (filename.matches(REGEX_FOR_WALLET_DIRECTORY)) {
            // The name matches so add it
            walletDirectories.add(file);
          }
        }
      }
    }

    return walletDirectories;
  }

  /**
   * @return The current wallet data
   */
  public Optional<WalletData> getCurrentWalletData() {
    return currentWalletData;
  }

  /**
   * @param currentWalletData The current wallet data
   */
  public void setCurrentWalletData(WalletData currentWalletData) {
    if (currentWalletData.getWallet() != null) {

      // Remove the previous WalletEventListener
      currentWalletData.getWallet().removeEventListener(this);

      // Add the wallet event listener
      currentWalletData.getWallet().addEventListener(this);
    }

    this.currentWalletData = Optional.of(currentWalletData);
  }

  /**
   * @return The current wallet file
   */
  public Optional<File> getCurrentWalletFile() {
    if (applicationDataDirectory != null && currentWalletData.isPresent()) {

      String walletFilename = applicationDataDirectory + File.separator + WALLET_DIRECTORY_PREFIX + SEPARATOR +
              currentWalletData.get().getWalletId().toFormattedString() + File.separator + MBHD_WALLET_NAME;
      return Optional.of(new File(walletFilename));

    } else {
      return Optional.absent();
    }
  }

  public static File makeAESEncryptedCopyAndDeleteOriginal(File fileToEncrypt, CharSequence password) throws IOException {
    Preconditions.checkNotNull(password);
    KeyCrypterScrypt keyCrypterScrypt = new KeyCrypterScrypt(makeScryptParameters());
    KeyParameter keyParameter = keyCrypterScrypt.deriveKey(password);
    // TODO - cache keyParameter

    // Read in the newlySavedFile
    byte[] walletBytes = org.multibit.hd.brit.utils.FileUtils.readFile(fileToEncrypt);

    // Create an AES encoded version of the newlySavedFile, using the wallet password
    byte[] encryptedWalletBytes = AESUtils.encrypt(walletBytes, keyParameter, WalletManager.AES_INITIALISATION_VECTOR);

    log.debug("Encrypted wallet bytes (original):\n" + Utils.bytesToHexString(encryptedWalletBytes));

    // Check that the encryption is reversible
    byte[] rebornBytes = AESUtils.decrypt(encryptedWalletBytes, keyParameter, WalletManager.AES_INITIALISATION_VECTOR);

    if (Arrays.equals(walletBytes, rebornBytes)) {
      // Save encrypted bytes
      File encryptedWalletFilename = new File(fileToEncrypt.getAbsoluteFile() + WalletManager.MBHD_AES_SUFFIX);
      ByteArrayInputStream encryptedWalletByteArrayInputStream = new ByteArrayInputStream(encryptedWalletBytes);
      FileOutputStream encryptedWalletOutputStream = new FileOutputStream(encryptedWalletFilename);
      FileUtils.writeFile(encryptedWalletByteArrayInputStream, encryptedWalletOutputStream);

      if (encryptedWalletFilename.length() == encryptedWalletBytes.length) {
        FileUtils.secureDelete(fileToEncrypt);
      } else {
        // The saved file isn't the correct size - do not delete the original
        return null;
      }

      return encryptedWalletFilename;
    } else {
      log.error("The wallet encryption was not reversible. Aborting. This means your wallet is being stored unencrypted");
      return null;
    }
  }

  public static Protos.ScryptParameters makeScryptParameters() {
    Protos.ScryptParameters.Builder scryptParametersBuilder = Protos.ScryptParameters.newBuilder().setSalt(ByteString.copyFrom(SCRYPT_SALT));
    return scryptParametersBuilder.build();
  }
}
