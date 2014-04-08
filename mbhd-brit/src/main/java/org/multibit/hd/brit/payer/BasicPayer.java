package org.multibit.hd.brit.payer;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import org.bouncycastle.openpgp.PGPException;
import org.multibit.hd.brit.crypto.AESUtils;
import org.multibit.hd.brit.crypto.PGPUtils;
import org.multibit.hd.brit.dto.*;
import org.multibit.hd.brit.exceptions.MatcherResponseException;
import org.multibit.hd.brit.exceptions.PayerRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Date;

/**
 * <p>Payer to provide the following to BRIT:</p>
 * <ul>
 * <li>Implementation of a basic Payer</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class BasicPayer implements Payer {

  private static final Logger log = LoggerFactory.getLogger(BasicPayer.class);

  private PayerConfig payerConfig;

  public BasicPayer(PayerConfig payerConfig) {
    this.payerConfig = payerConfig;
  }

  private BRITWalletId britWalletId;

  private byte[] sessionKey;

  @Override
  public PayerConfig getConfig() {
    return payerConfig;
  }

  @Override
  public PayerRequest newPayerRequest(BRITWalletId britWalletId, byte[] sessionKey, Optional<Date> firstTransactionDate) {

    this.britWalletId = britWalletId;
    this.sessionKey = sessionKey;
    return new PayerRequest(britWalletId, sessionKey, firstTransactionDate);

  }

  @Override
  public EncryptedPayerRequest encryptPayerRequest(PayerRequest payerRequest) throws PayerRequestException {
    try {
      // Serialise the contents of the payerRequest
      byte[] serialisedPayerRequest = payerRequest.serialise();

      // PGP encryptBytes the file
      byte[] encryptedBytes = PGPUtils.encryptBytes(serialisedPayerRequest, payerConfig.getMatcherPublicKey(), null);

      log.debug("Payload after encryption is :\n{}\n" ,new String(encryptedBytes, Charsets.UTF_8));

      return new EncryptedPayerRequest(encryptedBytes);
    } catch (IOException | NoSuchProviderException | PGPException e) {
      throw new PayerRequestException("Could not encryptBytes PayerRequest", e);
    }
  }

  @Override
  public MatcherResponse decryptMatcherResponse(EncryptedMatcherResponse encryptedMatcherResponse) throws MatcherResponseException {
    try {
      // Stretch the 20 byte britWalletId to 32 bytes (256 bits)
      byte[] stretchedBritWalletId = MessageDigest.getInstance("SHA-256").digest(britWalletId.getBytes());

      // Create an AES key from the stretchedBritWalletId and the sessionKey and decryptBytes the payload
      byte[] serialisedMatcherResponse = AESUtils.decrypt(encryptedMatcherResponse.getPayload(), new KeyParameter(stretchedBritWalletId), sessionKey);

      // Parse the serialised MatcherResponse
      return MatcherResponse.parse(serialisedMatcherResponse);
    } catch (NoSuchAlgorithmException | MatcherResponseException e) {
      throw new MatcherResponseException("Could not decryptBytes MatcherResponse", e);
    }
  }
}
