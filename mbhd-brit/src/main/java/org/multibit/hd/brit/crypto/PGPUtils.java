package org.multibit.hd.brit.crypto;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;

import javax.annotation.Nullable;
import java.io.*;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;
import java.util.Iterator;


/**
 * <p>Utility to provide the following to BRIT API:</p>
 * <ul>
 * <li>Access to PGP crypto functions in Bouncy Castle</li>
 * </ul>
 * <p>Derived from <code>org.bouncycastle.openpgp.examples</code> by seamans</p>
 *
 * @since 0.0.1
 */
public class PGPUtils {

  /**
   * Utilities have private constructors
   */
  private PGPUtils() {
  }

  /**
   * Load a PGP public key from a public keyring or ASCII armored text file
   *
   * @return key the first PGP public key in the found keyring/ ASCII armored text file
   */
  @SuppressWarnings("unchecked")
  public static PGPPublicKey readPublicKey(InputStream in) throws IOException, PGPException {
    in = org.bouncycastle.openpgp.PGPUtil.getDecoderStream(in);

    PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(in);

    // Loop through the collection until we find a key suitable for encryption
    // (in the real world you would probably want to be a bit smarter about this)
    PGPPublicKey key = null;

    // Iterate through the key rings
    Iterator<PGPPublicKeyRing> rIt = pgpPub.getKeyRings();

    while (key == null && rIt.hasNext()) {
      PGPPublicKeyRing kRing = rIt.next();
      Iterator<PGPPublicKey> kIt = kRing.getPublicKeys();
      while (key == null && kIt.hasNext()) {
        PGPPublicKey k = kIt.next();

        if (k.isEncryptionKey()) {
          key = k;
        }
      }
    }

    if (key == null) {
      throw new IllegalArgumentException("Can't find encryption key in key ring.");
    }

    return key;
  }

  /**
   * Load a secret key ring collection from keyIn and find the secret key corresponding to
   * keyID if it exists.
   *
   * @param keyIn input stream representing a key ring collection.
   * @param keyID keyID we want.
   * @param pass  password to decryptBytes secret key with.
   * @return The PGPPrivate key matching the keyID
   * @throws IOException
   * @throws PGPException
   * @throws NoSuchProviderException
   */
  public static PGPPrivateKey findPrivateKey(InputStream keyIn, long keyID, char[] pass)
          throws IOException, PGPException, NoSuchProviderException {

    PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(
            org.bouncycastle.openpgp.PGPUtil.getDecoderStream(keyIn));

    PGPSecretKey pgpSecKey = pgpSec.getSecretKey(keyID);

    if (pgpSecKey == null) {
      return null;
    }

    return pgpSecKey.extractPrivateKey(pass, "BC");
  }

  private static PGPPrivateKey findPrivateKey(
          PGPSecretKeyRingCollection pgpSec, long keyID, char[] pass)
          throws PGPException, NoSuchProviderException {
    PGPSecretKey pgpSecKey = pgpSec.getSecretKey(keyID);

    if (pgpSecKey == null) {
      return null;
    }

    return pgpSecKey.extractPrivateKey(pass, "BC");
  }

  /**
   * Decrypt the passed in message stream
   *
   * @param encryptedInputStream  The input stream
   * @param decryptedOutputStream The output stream
   * @param keyInputStream        The key input stream
   * @param password              The password
   * @throws IOException
   * @throws NoSuchProviderException
   * @throws PGPException
   */
  @SuppressWarnings("unchecked")
  public static void decrypt(InputStream encryptedInputStream, OutputStream decryptedOutputStream, InputStream keyInputStream, char[] password)
    throws IOException, NoSuchProviderException, PGPException {

    Security.addProvider(new BouncyCastleProvider());

    encryptedInputStream = PGPUtil.getDecoderStream(encryptedInputStream);

    final PGPObjectFactory pgpFactory = new PGPObjectFactory(encryptedInputStream);

    final PGPEncryptedDataList enc;

    final Object o = pgpFactory.nextObject();

    // The first object might be a PGP marker packet.
    if (o instanceof PGPEncryptedDataList) {
      enc = (PGPEncryptedDataList) o;
    } else {
      enc = (PGPEncryptedDataList) pgpFactory.nextObject();
    }

    // Find the private key matching the public key in the secret key ring
    final Iterator<PGPPublicKeyEncryptedData> it = enc.getEncryptedDataObjects();
    PGPPrivateKey privateKey = null;
    PGPPublicKeyEncryptedData pbe = null;

    while (privateKey == null && it.hasNext()) {
      pbe = it.next();

      privateKey = findPrivateKey(keyInputStream, pbe.getKeyID(), password);
    }

    if (privateKey == null) {
      throw new IllegalArgumentException("Secret key for message not found.");
    }

    final InputStream clear = pbe.getDataStream(privateKey, "BC");

    final PGPObjectFactory plainFact = new PGPObjectFactory(clear);

    Object message = plainFact.nextObject();

    if (message instanceof PGPCompressedData) {
      PGPCompressedData cData = (PGPCompressedData) message;
      PGPObjectFactory pgpFact = new PGPObjectFactory(cData.getDataStream());

      message = pgpFact.nextObject();
    }

    if (message instanceof PGPLiteralData) {
      PGPLiteralData ld = (PGPLiteralData) message;

      InputStream unc = ld.getInputStream();
      int ch;

      while ((ch = unc.read()) >= 0) {
        decryptedOutputStream.write(ch);
      }
    } else if (message instanceof PGPOnePassSignatureList) {
      throw new PGPException("Encrypted message contains a signed message - not literal data.");
    } else {
      throw new PGPException("Message is not a simple encrypted file - type unknown.");
    }

    if (pbe.isIntegrityProtected()) {
      if (!pbe.verify()) {
        throw new PGPException("Message failed integrity check");
      }
    }
  }

  /**
   * <p>Encrypt a file</p>
   *
   * @param armoredOut The output stream
   * @param inputFile  The input file
   * @param encKey     The PGP public key for encrypting
   * @throws IOException
   * @throws NoSuchProviderException
   * @throws PGPException
   */
  public static void encryptFile(OutputStream armoredOut,
                                 File inputFile,
                                 PGPPublicKey encKey)
          throws IOException, NoSuchProviderException, PGPException {

    Security.addProvider(new BouncyCastleProvider());

    // Armored output
    armoredOut = new ArmoredOutputStream(armoredOut);

    final ByteArrayOutputStream baos = new ByteArrayOutputStream();

    final PGPCompressedDataGenerator comData = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);

    PGPUtil.writeFileToLiteralData(
            comData.open(baos),
            PGPLiteralData.BINARY,
            inputFile
    );

    comData.close();

    final PGPEncryptedDataGenerator encryptedDataGenerator = new PGPEncryptedDataGenerator(
            PGPEncryptedData.CAST5,
            // Always perform an integrity check
            true,
            new SecureRandom(),
            "BC"
    );

    encryptedDataGenerator.addMethod(encKey);

    byte[] bytes = baos.toByteArray();

    OutputStream os = encryptedDataGenerator.open(armoredOut, bytes.length);

    os.write(bytes);

    os.close();

    armoredOut.close();
  }

  /**
   * Simple PGP encryption between byte[].
   *
   * @param clearData  The test to be encrypted
   * @param encKey     The PGP public key to use for encryption
   * @param fileName   File name. This is used in the Literal Data Packet (tag 11)
   *                   which is really only important if the data is to be related to
   *                   a file to be recovered later. Because this routine does not
   *                   know the source of the information, the caller can set
   *                   something here for file name use that will be carried. If this
   *                   routine is being used to encryptBytes SOAP MIME bodies, for
   *                   example, use the file name from the MIME type, if applicable.
   *                   Or anything else appropriate.
   * @return encrypted data.
   * @throws IOException
   * @throws PGPException
   * @throws NoSuchProviderException
   */
  public static byte[] encryptBytes(byte[] clearData, PGPPublicKey encKey,
                                    @Nullable String fileName)
          throws IOException, PGPException, NoSuchProviderException {
    if (fileName == null) {
      fileName = PGPLiteralData.CONSOLE;
    }

    Security.addProvider(new BouncyCastleProvider());

    ByteArrayOutputStream encOut = new ByteArrayOutputStream();

    OutputStream out = new ArmoredOutputStream(encOut);

    ByteArrayOutputStream bOut = new ByteArrayOutputStream();

    PGPCompressedDataGenerator comData = new PGPCompressedDataGenerator(
            PGPCompressedDataGenerator.ZIP);
    OutputStream cos = comData.open(bOut); // open it with the final
    // destination
    PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();

    // we want to generate compressed data. This might be a user option
    // later,
    // in which case we would pass in bOut.
    OutputStream pOut = lData.open(cos, // the compressed output stream
            PGPLiteralData.BINARY, fileName, // "filename" to store
            clearData.length, // length of clear data
            new Date() // current time
    );
    pOut.write(clearData);

    lData.close();
    comData.close();

    PGPEncryptedDataGenerator cPk = new PGPEncryptedDataGenerator(
            PGPEncryptedData.CAST5, true, new SecureRandom(),
            "BC");

    cPk.addMethod(encKey);

    byte[] bytes = bOut.toByteArray();

    OutputStream cOut = cPk.open(out, bytes.length);

    cOut.write(bytes); // obtain the actual bytes from the compressed stream

    cOut.close();

    out.close();

    return encOut.toByteArray();
  }
}