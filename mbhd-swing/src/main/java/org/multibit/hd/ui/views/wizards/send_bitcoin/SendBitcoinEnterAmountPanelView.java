package org.multibit.hd.ui.views.wizards.send_bitcoin;

import com.google.bitcoin.core.Coin;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import net.miginfocom.swing.MigLayout;
import org.joda.time.DateTime;
import org.multibit.hd.core.dto.FiatPayment;
import org.multibit.hd.core.dto.Recipient;
import org.multibit.hd.core.dto.WalletSummary;
import org.multibit.hd.core.events.ExchangeRateChangedEvent;
import org.multibit.hd.core.exceptions.ExceptionHandler;
import org.multibit.hd.core.exceptions.PaymentsSaveException;
import org.multibit.hd.core.exchanges.ExchangeKey;
import org.multibit.hd.core.managers.InstallationManager;
import org.multibit.hd.core.managers.WalletManager;
import org.multibit.hd.core.services.ContactService;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.core.services.WalletService;
import org.multibit.hd.core.store.TemplateData;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.Components;
import org.multibit.hd.ui.views.components.ModelAndView;
import org.multibit.hd.ui.views.components.Panels;
import org.multibit.hd.ui.views.components.enter_amount.EnterAmountModel;
import org.multibit.hd.ui.views.components.enter_amount.EnterAmountView;
import org.multibit.hd.ui.views.components.enter_recipient.EnterRecipientModel;
import org.multibit.hd.ui.views.components.enter_recipient.EnterRecipientView;
import org.multibit.hd.ui.views.components.panels.PanelDecorator;
import org.multibit.hd.ui.views.components.wallet_detail.WalletDetail;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;
import org.multibit.hd.ui.views.wizards.AbstractWizard;
import org.multibit.hd.ui.views.wizards.AbstractWizardPanelView;
import org.multibit.hd.ui.views.wizards.WizardButton;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * <p>View to provide the following to UI:</p>
 * <ul>
 * <li>Send bitcoin: Enter amount</li>
 * </ul>
 *
 * @since 0.0.1
 *  
 */

public class SendBitcoinEnterAmountPanelView extends AbstractWizardPanelView<SendBitcoinWizardModel, SendBitcoinEnterAmountPanelModel> {

  // Panel specific components
  private ModelAndView<EnterRecipientModel, EnterRecipientView> enterRecipientMaV;
  private ModelAndView<EnterAmountModel, EnterAmountView> enterAmountMaV;

  /**
   * @param wizard    The wizard managing the states
   * @param panelName The panel name
   */
  public SendBitcoinEnterAmountPanelView(AbstractWizard<SendBitcoinWizardModel> wizard, String panelName) {

    super(wizard, panelName, MessageKey.SEND_BITCOIN_TITLE, AwesomeIcon.CLOUD_UPLOAD);

  }

  @Override
  public void newPanelModel() {

    enterRecipientMaV = Components.newEnterRecipientMaV(getPanelName());
    enterAmountMaV = Components.newEnterAmountMaV(getPanelName());

    // Configure the panel model
    final SendBitcoinEnterAmountPanelModel panelModel = new SendBitcoinEnterAmountPanelModel(
      getPanelName(),
      enterRecipientMaV.getModel(),
      enterAmountMaV.getModel()
    );
    setPanelModel(panelModel);

    // Bind it to the wizard model
    getWizardModel().setEnterAmountPanelModel(panelModel);

    // Register components
    registerComponents(enterAmountMaV, enterRecipientMaV);

  }

  @Override
  public void initialiseContent(JPanel contentPanel) {

    contentPanel.setLayout(new MigLayout(
      Panels.migXYLayout(),
      "[]", // Column constraints
      "[]10[]" // Row constraints
    ));

    // Apply any Bitcoin URI parameters
    if (getWizardModel().getBitcoinURI().isPresent()) {

      getWizardModel().handleBitcoinURI();

      Recipient recipient = getWizardModel().getRecipient();
      Coin amount = getWizardModel().getCoinAmount();

      enterRecipientMaV.getModel().setValue(recipient);
      enterAmountMaV.getModel().setCoinAmount(amount);

    }
      JButton saveTemplateButton = new JButton("Save as Template");
      final JLabel savedLabel = new JLabel("",JLabel.CENTER);
      savedLabel.setSize(350, 100);
      saveTemplateButton.addActionListener(new ActionListener() {

          public void actionPerformed(ActionEvent e) {
              //Execute when button is pressed
              System.out.println("here we go");
              log.debug("save called");
              Preconditions.checkNotNull(enterRecipientMaV, "you need at least one recipient for the template");
              Preconditions.checkNotNull(enterAmountMaV, "you need some amount for the template");
//          (enterRecipientMaV.getModel().getRecipient().get() == null)

              try {
                  saveTemplate();
                  savedLabel.setText("Template saved");
              } catch (Exception anything) {
                  savedLabel.setText("Template not saved due to missing entry");
              }
          }


      });

    contentPanel.add(saveTemplateButton);
    contentPanel.add(enterRecipientMaV.getView().newComponentPanel(), "wrap");
    contentPanel.add(enterAmountMaV.getView().newComponentPanel(), "wrap");

  }

    private void saveTemplate() {
        log.debug("Saving this template");
        WalletService walletService = CoreServices.getCurrentWalletService();
        // Fail fast
        Preconditions.checkNotNull(walletService, "'walletService' must be present");
        Preconditions.checkState(WalletManager.INSTANCE.getCurrentWalletSummary().isPresent(), "'currentWalletSummary' must be present");
        final TemplateData templateData = new TemplateData();
        templateData.setCreatedDate(DateTime.now());
        List<Address> recipients = new ArrayList();
        recipients.add(getWizardModel().getRecipient().getBitcoinAddress());

        templateData.setRecipients(recipients);
        templateData.setAmountBTC(enterAmountMaV.getModel().getCoinAmount());
        final FiatPayment fiatPayment = new FiatPayment();
        fiatPayment.setAmount(enterAmountMaV.getModel().getLocalAmount());
        final ExchangeKey exchangeKey = ExchangeKey.current();
        fiatPayment.setExchangeName(Optional.of(exchangeKey.getExchangeName()));

        final Optional<ExchangeRateChangedEvent> exchangeRateChangedEvent = CoreServices.getApplicationEventService().getLatestExchangeRateChangedEvent();
        if (exchangeRateChangedEvent.isPresent()) {
            fiatPayment.setRate(Optional.of(exchangeRateChangedEvent.get().getRate().toString()));
            fiatPayment.setCurrency(Optional.of(exchangeRateChangedEvent.get().getCurrency()));
        } else {
            fiatPayment.setRate(Optional.<String>absent());
            fiatPayment.setCurrency(Optional.<Currency>absent());
        }

        templateData.setAmountFiat(fiatPayment);
        // not sure if this will conflict itself as this feature expands(if it does)
        templateData.setHash(DateTime.now().toString());

        log.debug("this is the template data about to write: "+templateData);
        walletService.addTemplateData(templateData);
        try {
            walletService.writeTemplates();
        } catch (PaymentsSaveException pse) {
            log.debug("stick your finger in mouth. You have a problem writing template");
            ExceptionHandler.handleThrowable(pse);
        }

        // Ensure the views that display payments update through a "wallet detail changed" event
        final WalletDetail walletDetail = new WalletDetail();

        final File applicationDataDirectory = InstallationManager.getOrCreateApplicationDataDirectory();
        final File walletFile = WalletManager.INSTANCE.getCurrentWalletFile(applicationDataDirectory).get();

        final WalletSummary walletSummary = WalletManager.INSTANCE.getCurrentWalletSummary().get();
        ContactService contactService = CoreServices.getOrCreateContactService(walletSummary.getWalletId());

        walletDetail.setApplicationDirectory(applicationDataDirectory.getAbsolutePath());
        walletDetail.setWalletDirectory(walletFile.getParentFile().getName());
        walletDetail.setNumberOfContacts(contactService.allContacts().size());
        walletDetail.setNumberOfPayments(walletService.getPaymentDataList().size());
//        walletDetail.setNumberOfTemplates(walletService.getTemplateDataList().size());

        ViewEvents.fireWalletDetailChangedEvent(walletDetail);
    }

  @Override
  protected void initialiseButtons(AbstractWizard<SendBitcoinWizardModel> wizard) {

    PanelDecorator.addExitCancelNext(this, wizard);

  }

  @Override
  public void fireInitialStateViewEvents() {

    // Next button starts off disabled
    ViewEvents.fireWizardButtonEnabledEvent(getPanelName(), WizardButton.NEXT, false);

  }

  @Override
  public void afterShow() {

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {

        enterRecipientMaV.getView().requestInitialFocus();

      }
    });

  }

  @Override
  public void updateFromComponentModels(Optional componentModel) {

    // No need to update the panel model it already has the references

    // Determine any events
    ViewEvents.fireWizardButtonEnabledEvent(
      getPanelName(),
      WizardButton.NEXT,
      isNextEnabled()
    );

  }

  /**
   * @return True if the "next" button should be enabled
   */
  private boolean isNextEnabled() {

    boolean bitcoinAmountOK = !getPanelModel().get()
      .getEnterAmountModel()
      .getCoinAmount()
      .equals(Coin.ZERO);

    boolean recipientOK = getPanelModel().get()
      .getEnterRecipientModel()
      .getRecipient()
      .isPresent();

    return bitcoinAmountOK && recipientOK;
  }
}

