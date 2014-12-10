package org.multibit.hd.ui.views.screens.payments;

import com.google.common.base.Optional;
import net.miginfocom.swing.MigLayout;
import org.bitcoinj.uri.BitcoinURI;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.core.services.WalletService;
import org.multibit.hd.core.store.TemplateData;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.*;
import org.multibit.hd.ui.views.components.tables.TemplateTableModel;
import org.multibit.hd.ui.views.screens.AbstractScreenView;
import org.multibit.hd.ui.views.screens.Screen;
import org.multibit.hd.ui.views.wizards.Wizards;
import org.multibit.hd.ui.views.wizards.send_bitcoin.SendBitcoinParameter;
import org.multibit.hd.ui.views.wizards.send_bitcoin.SendBitcoinWizard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * <p>View to provide the following to application:</p>
 * <ul>
 * <li>Provision of components and layout for the payments detail display</li>
 * </ul>
 *
 * @since 0.0.1
 *
 */


public class TemplatesScreenView extends AbstractScreenView<TemplatesScreenModel> {

  private static final Logger log = LoggerFactory.getLogger(TemplatesScreenView.class);

  private JTable templatesTable;

  private JButton detailSendButton;




/*  // View components
  private ModelAndView<EnterSearchModel, EnterSearchView> enterSearchMaV;*/

  /**
   * @param panelModel The model backing this panel view
   * @param screen     The screen to filter events from components
   * @param title      The key to the main title of this panel view
   */
  public TemplatesScreenView(TemplatesScreenModel panelModel, Screen screen, MessageKey title) {
    super(panelModel, screen, title);
  }

  @Override
  public void newScreenModel() {

  }

  @Override
  public JPanel initialiseScreenViewPanel() {

    MigLayout layout = new MigLayout(
      Panels.migXYDetailLayout(),
      "[][][][][]push[]", // Column constraints
      "[shrink][shrink][grow]" // Row constraints
    );

    // Create view components
    JPanel contentPanel = Panels.newPanel(layout);

/**
 * function bellow calls the "Send" to add a template
 * it doesn't comply with the core developer's tidy structure
 * if time allows it should be aligned to their structure
 */

    JButton addTemplateButton = new JButton("Add Template");
    addTemplateButton.addActionListener(new ActionListener() {

        public void actionPerformed(ActionEvent e) {
            log.debug("getAddTemplateAction called");
            SendBitcoinParameter parameter = new SendBitcoinParameter(Optional.<BitcoinURI>absent(), false);

            Panels.showLightBox(Wizards.newSendBitcoinWizard(parameter).getWizardScreenHolder());
        }
    });
    detailSendButton = Buttons.newDetailsButton(getDetailSendAction());

/*    if (InstallationManager.unrestricted) {
      // Start with enabled buttons and use Bitcoin network status to modify
      addTemplateButton.setEnabled(true);

    } else {
      // Start with disabled button and use Bitcoin network status to modify
      addTemplateButton.setEnabled(false);

    }*/



      WalletService walletService = CoreServices.getCurrentWalletService();
    List<TemplateData> templateList = walletService.getTemplateDataList();

    templatesTable = Tables.newTemplatesTable(templateList, detailSendButton);

    // Create the scroll pane and add the table to it.
    JScrollPane scrollPane = new JScrollPane(templatesTable);
    scrollPane.setViewportBorder(null);

    // Detect double clicks on the table
    templatesTable.addMouseListener(getTableMouseListener());

    // Ensure we maintain the overall theme
    ScrollBarUIDecorator.apply(scrollPane, templatesTable);

    // Add to the panel
/*
    contentPanel.add(enterSearchMaV.getView().newComponentPanel(), "span 6,growx,push,wrap");
    contentPanel.add(detailsButton, "shrink");
*/

    contentPanel.add(addTemplateButton, "shrink");
    contentPanel.add(detailSendButton, "shrink");
    contentPanel.add(Labels.newBlankLabel(), "growx,push,wrap"); // Empty label to pack buttons

    contentPanel.add(scrollPane, "span 6, grow, push");

    return contentPanel;
  }


 /* *//**
   * @param transactionSeenEvent The event (very high frequency during synchronisation)
   *//*
  @Subscribe
  public void onTransactionSeenEvent(TransactionSeenEvent transactionSeenEvent) {

    log.trace("Received a TransactionSeenEvent: {}", transactionSeenEvent);

    if (transactionSeenEvent.isFirstAppearanceInWallet()) {
      Sounds.playPaymentReceived();
      AlertModel alertModel = Models.newPaymentReceivedAlertModel(transactionSeenEvent);
      ControllerEvents.fireAddAlertEvent(alertModel);
    }
  }

  *//**
   * Update the payments when a slowTransactionSeenEvent occurs
   *//*
  @Subscribe
  public void onSlowTransactionSeenEvent(SlowTransactionSeenEvent slowTransactionSeenEvent) {
    log.trace("Received a SlowTransactionSeenEvent.");

    update(true);
  }

  *//**
   * Update the payments when a walletDetailsChangedEvent occurs
   *//*
  @Subscribe
  public void onWalletDetailChangedEvent(WalletDetailChangedEvent walletDetailChangedEvent) {
    log.trace("Received a WalletDetailsChangedEvent.");

    update(true);
  }

  *//**
   * <p>Called when the search box is updated</p>
   *
   * @param event The "component changed" event
   *//*
  @Subscribe
  public void onComponentChangedEvent(ComponentChangedEvent event) {

    // Check if this event applies to us
    if (event.getPanelName().equals(getScreen().name())) {
      update(false);
    }

  }*/

/*  private void update(final boolean refreshData) {

    if (templatesTable != null) {

      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {

          try {
            // Remember the selected row
            int selectedTableRow = templatesTable.getSelectedRow();

            WalletService walletService = CoreServices.getCurrentWalletService();

            // Refresh the wallet payment list if asked
            if (refreshData) {
              walletService.getPaymentDataList();
            }
            // Check the search MaV model for a query and apply it
            List<PaymentData> filteredPaymentDataList = walletService.filterPaymentsByContent(enterSearchMaV.getModel().getValue());

            ((PaymentTableModel) templatesTable.getModel()).setPaymentData(filteredPaymentDataList, true);

            // Reselect the selected row if possible
            if (selectedTableRow != -1 && selectedTableRow < templatesTable.getModel().getRowCount()) {
              templatesTable.changeSelection(selectedTableRow, 0, false, false);
            }
          } catch (IllegalStateException ise) {
            // No wallet is open - nothing to do
          }
        }
      });
    }

  }*/

  /**
   * @return The show transaction details action
   */
  private Action getDetailSendAction() {

    return new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {

//        WalletService walletService = CoreServices.getCurrentWalletService();

        int selectedTableRow = templatesTable.getSelectedRow();

        if (selectedTableRow == -1) {
          // No row selected
          return;
        }
        int selectedModelRow = templatesTable.convertRowIndexToModel(selectedTableRow);
        TemplateData templateData = ((TemplateTableModel) templatesTable.getModel()).getTemplateData().get(selectedModelRow);
        log.debug("template Data sssss: "+templateData);
        //log.debug("getDetailsAction : selectedTableRow = " + selectedTableRow + ", selectedModelRow = " + selectedModelRow + ", paymentData = " + paymentData.toString());
        SendBitcoinParameter parameter = new SendBitcoinParameter(Optional.<BitcoinURI>absent(), false);
        SendBitcoinWizard wizard = Wizards.newSendBitcoinWizard(parameter, templateData);

        Panels.showLightBox(wizard.getWizardScreenHolder());
      }
    };
  }

    /**
     * @return The add template action
     */
/*    private Action getAddAction() {

        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {

                // Get the currently selected template
                final List<TemplateData> templates = Lists.newArrayList();

                Contact contact = getScreenModel().getContactService().newContact(Languages.safeText(MessageKey.NAME));

                contacts.add(contact);

                Collections.sort(contacts, new ContactNameComparator());

                // Fire up a wizard in new mode
                Panels.showLightBox(Wizards.newEditContactWizard(contacts, EnterContactDetailsMode.NEW).getWizardScreenHolder());

            }
        };
    }
    */
  /**
    * @return The table mouse listener
    */
   private MouseAdapter getTableMouseListener() {

     return new MouseAdapter() {

       public void mousePressed(MouseEvent e) {

         if (e.getClickCount() == 2) {

             detailSendButton.doClick();
         }
       }

     };
   }



/*  private void fireWalletDetailsChanged() {

    // Ensure the views that display payments update
    WalletDetail walletDetail = new WalletDetail();
    if (WalletManager.INSTANCE.getCurrentWalletSummary().isPresent()) {
      WalletSummary walletSummary = WalletManager.INSTANCE.getCurrentWalletSummary().get();
      walletDetail.setApplicationDirectory(InstallationManager.getOrCreateApplicationDataDirectory().getAbsolutePath());

      File applicationDataDirectory = InstallationManager.getOrCreateApplicationDataDirectory();
      File walletFile = WalletManager.INSTANCE.getCurrentWalletFile(applicationDataDirectory).get();
      walletDetail.setWalletDirectory(walletFile.getParentFile().getName());

      ContactService contactService = CoreServices.getOrCreateContactService(walletSummary.getWalletId());
      walletDetail.setNumberOfContacts(contactService.allContacts().size());

      walletDetail.setNumberOfPayments(CoreServices.getCurrentWalletService().getPaymentDataList().size());
      ViewEvents.fireWalletDetailChangedEvent(walletDetail);

    }
  }*/
}
