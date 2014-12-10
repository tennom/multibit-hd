/**
 * Copyright 2014 multibit.org
 *
 * Licensed under the MIT license (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://opensource.org/licenses/mit-license.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Based on the WalletProtobufSerialiser written by Miron Cuperman, copyright Google (also MIT licence)
 */

package org.multibit.hd.core.store;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.joda.time.DateTime;
import org.multibit.hd.core.dto.FiatPayment;
import org.multibit.hd.core.exceptions.PaymentsLoadException;
import org.multibit.hd.core.protobuf.MBHDTemplatesProtos;
import org.multibit.hd.core.utils.Addresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.List;

/**
 * <p>
 * Serialize and de-serialize contacts to a byte stream containing a
 * <a href="http://code.google.com/apis/protocolbuffers/docs/overview.html">protocol buffer</a>.</p>
 * <p/>
 * <p>Protocol buffers are a data interchange format developed by Google with an efficient binary representation, a type safe specification
 * language and compilers that generate code to work with those data structures for many languages. Protocol buffers
 * can have their format evolved over time: conceptually they represent data using (tag, length, value) tuples.</p>
 * <p/>
 * <p>The format is defined by the <tt>Templates.proto</tt> file in the MBHD source distribution.</p>
 * <p/>
 * <p>This class is used through its static methods. The most common operations are <code>writeTemplates</code> and <code>readTemplates</code>, which do
 * the obvious operations on Output/InputStreams. You can use a {@link java.io.ByteArrayInputStream} and equivalent
 * {@link java.io.ByteArrayOutputStream} if byte arrays are preferred. The protocol buffer can also be manipulated
 * in its object form if you'd like to modify the flattened data structure before serialization to binary.</p>
 * <p/>
 * <p>Based on the original work by Miron Cuperman for the Bitcoinj project</p>
 */
public class TemplatesProtobufSerializer {

  private static final long ABSENT_VALUE = -1;

  private static final String ABSENT_STRING = "absent";

  private static final Logger log = LoggerFactory.getLogger(TemplatesProtobufSerializer.class);

  public TemplatesProtobufSerializer() {
  }

  /**
   * Formats the given Templates to the given output stream in protocol buffer format.<p>
   */
  public void writeTemplates(Payments templates, OutputStream output) throws IOException {
    MBHDTemplatesProtos.Templates TemplatesProto = templatesToProto(templates);
    TemplatesProto.writeTo(output);
  }

  /**
   * Converts the given Templates to the object representation of the protocol buffers. This can be modified, or
   * additional data fields set, before serialization takes place.
   */
  public MBHDTemplatesProtos.Templates templatesToProto(Payments templates) {
    MBHDTemplatesProtos.Templates.Builder TemplatesBuilder = MBHDTemplatesProtos.Templates.newBuilder();

    Preconditions.checkNotNull(templates, "Templates must be specified");

    Collection<TemplateData> templateDatas = templates.getTemplateDatas();
    if (templateDatas != null) {
      for (TemplateData templateData : templateDatas) {
        MBHDTemplatesProtos.Template templateProto = makeTemplateProto(templateData);
        TemplatesBuilder.addTemplate(templateProto);
      }
    }

    return TemplatesBuilder.build();
  }

  /**
   * <p>Parses a Templates from the given stream, using the provided Templates instance to loadContacts data into.
   * <p>A Templates db can be unreadable for various reasons, such as inability to open the file, corrupt data, internally
   * inconsistent data, You should always
   * handle {@link org.multibit.hd.core.exceptions.PaymentsLoadException} and communicate failure to the user in an appropriate manner.</p>
   *
   * @throws org.multibit.hd.core.exceptions.PaymentsLoadException thrown in various error conditions (see description).
   */
  public Payments readTemplates(InputStream input) throws PaymentsLoadException {
    try {
      MBHDTemplatesProtos.Templates TemplatesProto = parseToProto(input);
      Payments templates = new Payments();
      readTemplates(TemplatesProto, templates);
      return templates;
    } catch (IOException e) {
      throw new PaymentsLoadException("Could not parse input stream to protobuf", e);
    }
  }

  /**
   * <p>Loads Templates data from the given protocol buffer and inserts it into the given Templates object.
   * <p/>
   * <p>A Templates db can be unreadable for various reasons, such as inability to open the file, corrupt data, internally
   * inconsistent data, a wallet extension marked as mandatory that cannot be handled and so on. You should always
   * handle {@link org.multibit.hd.core.exceptions.PaymentsLoadException} and communicate failure to the user in an appropriate manner.</p>
   *
   * @throws org.multibit.hd.core.exceptions.PaymentsLoadException thrown in various error conditions (see description).
   */
  private void readTemplates(MBHDTemplatesProtos.Templates TemplatesProto, Payments Templates) throws PaymentsLoadException {
    Collection<TemplateData> templateDatas = Lists.newArrayList();

    List<MBHDTemplatesProtos.Template> templateProtos = TemplatesProto.getTemplateList();
    if (templateProtos != null) {
      for (MBHDTemplatesProtos.Template templateProto : templateProtos) {

        TemplateData templateData = new TemplateData();
        templateData.setHash(templateProto.getHash());
//        Collection<Address> recipients = Lists.newArrayList();
        List<MBHDTemplatesProtos.Recipient> recipientProtos = templateProto.getAddressList();
//        strip off all the addresses
        if (recipientProtos != null) {
          List<Address> addresses = new ArrayList<Address>();
          for (MBHDTemplatesProtos.Recipient recipientProto : recipientProtos) {
            Optional<Address> address = Addresses.parse(recipientProto.getAddress());
            if (address.isPresent()) {
              addresses.add(address.get());
              log.warn("Failed to parse address: '{}'", recipientProto.getAddress());
            }
//            recipients.add(Collection<Address> recipientProto.getAddress());
          }
          templateData.setRecipients(addresses);
        }
 /*       Optional<> address = Addresses.parse(templateProto.getAddress());
        if (address.isPresent()) {
          paymentRequestData.setAddress(address.get());
          log.warn("Failed to parse address: '{}'", paymentRequestProto.getAddress());
        }*/

        if (templateProto.hasLabel()) {
          templateData.setLabel(templateProto.getLabel());
        }
        if (templateProto.hasNote()) {
          templateData.setNote(templateProto.getNote());
        }
        if (templateProto.hasDate()) {
          templateData.setCreatedDate(new DateTime(templateProto.getDate()));
        }
        if (templateProto.hasAmountBTC()) {
          templateData.setAmountBTC(Coin.valueOf(templateProto.getAmountBTC()));
        }

        if (templateProto.hasAmountFiat()) {

          FiatPayment fiatPayment = new FiatPayment();

          templateData.setAmountFiat(fiatPayment);
          MBHDTemplatesProtos.FiatPayment fiatTemplateProto = templateProto.getAmountFiat();

          if (fiatTemplateProto.hasCurrency()) {
            final String fiatCurrencyCode = fiatTemplateProto.getCurrency();
            final Optional<Currency> fiatCurrency;
            if (ABSENT_STRING.equals(fiatCurrencyCode)) {
              fiatCurrency = Optional.absent();
            } else {
              fiatCurrency = Optional.of(Currency.getInstance(fiatCurrencyCode));
            }
            fiatPayment.setCurrency(fiatCurrency);

            String fiatPaymentAmount = fiatTemplateProto.getAmount();
            Optional<BigDecimal> amountFiat;
            if (ABSENT_STRING.equals(fiatPaymentAmount)) {
              amountFiat = Optional.absent();
            } else {
              amountFiat = Optional.of(new BigDecimal(fiatPaymentAmount));
            }
            fiatPayment.setAmount(amountFiat);
          }

          if (fiatTemplateProto.hasExchange()) {
            if (ABSENT_STRING.equals(fiatTemplateProto.getExchange())) {
              fiatPayment.setExchangeName(Optional.<String>absent());
            } else {
              fiatPayment.setExchangeName(Optional.of(fiatTemplateProto.getExchange()));
            }
          }
          if (fiatTemplateProto.hasRate()) {
            fiatPayment.setRate(Optional.of(fiatTemplateProto.getRate()));
            if (ABSENT_STRING.equals(fiatTemplateProto.getRate())) {
              fiatPayment.setRate(Optional.<String>absent());
            } else {
              fiatPayment.setRate(Optional.of(fiatTemplateProto.getRate()));
            }
          }
        }

        templateDatas.add(templateData);
      }
    }


    Templates.setTemplateDatas(templateDatas);
  }

  /**
   * Returns the loaded protocol buffer from the given byte stream. This method is designed for low level work involving the
   * wallet file format itself.
   */
  public static MBHDTemplatesProtos.Templates parseToProto(InputStream input) throws IOException {
    return MBHDTemplatesProtos.Templates.parseFrom(input);
  }

  private static MBHDTemplatesProtos.Template makeTemplateProto(TemplateData templateData) {
    MBHDTemplatesProtos.Template.Builder templateBuilder = MBHDTemplatesProtos.Template.newBuilder();

    if (templateData != null) {
//      here is the mismatch again
//        this is not called
      templateBuilder.setHash(templateData.getHash());
      Collection<Address> recipients = templateData.getRecipients();
      if (recipients != null) {
        for (Address recipient : recipients) {
          MBHDTemplatesProtos.Recipient recipientProto = makeRecipientProto(recipient);
          templateBuilder.addAddress(recipientProto);
        }

      }
      templateBuilder.setNote(templateData.getNote() == null ? "" : templateData.getNote());
      templateBuilder.setAmountBTC(templateData.getAmountBTC() == null ? 0 : templateData.getAmountBTC().longValue());

      if (templateData.getCreatedDate() != null) {
        templateBuilder.setDate(templateData.getCreatedDate().getMillis());
      }
      templateBuilder.setLabel(templateData.getLabel() == null ? "" : templateData.getLabel());

      FiatPayment fiatPayment = templateData.getAmountFiat();
      if (fiatPayment != null) {

        MBHDTemplatesProtos.FiatPayment.Builder fiatPaymentBuilder = MBHDTemplatesProtos.FiatPayment.newBuilder();

        // Amount
        if (fiatPayment.getAmount() != null && fiatPayment.getAmount().isPresent()
                && fiatPayment.getCurrency() != null && fiatPayment.getCurrency().isPresent()) {
          fiatPaymentBuilder.setAmount(fiatPayment.getAmount().get().stripTrailingZeros().toPlainString());
          fiatPaymentBuilder.setCurrency(fiatPayment.getCurrency().get().getCurrencyCode());
        } else {
          fiatPaymentBuilder.setAmount(ABSENT_STRING);
          fiatPaymentBuilder.setCurrency(ABSENT_STRING);
        }

        if (fiatPayment.getExchangeName().isPresent()) {
          fiatPaymentBuilder.setExchange(fiatPayment.getExchangeName().get());
        } else {
          fiatPaymentBuilder.setExchange(ABSENT_STRING);
        }
        if (fiatPayment.getRate().isPresent()) {
          fiatPaymentBuilder.setRate(fiatPayment.getRate().get());
        } else {
          fiatPaymentBuilder.setRate(ABSENT_STRING);
        }

        templateBuilder.setAmountFiat(fiatPaymentBuilder);
      }
    }

    return templateBuilder.build();
  }
private static MBHDTemplatesProtos.Recipient makeRecipientProto(Address recipient) {
  MBHDTemplatesProtos.Recipient.Builder recipientBuilder = MBHDTemplatesProtos.Recipient.newBuilder();
  recipientBuilder.setAddress(recipient.toString());
  return recipientBuilder.build();
}

}
