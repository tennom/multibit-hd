package org.multibit.hd.ui.views;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import net.miginfocom.swing.MigLayout;
import org.multibit.hd.core.config.Configurations;
import org.multibit.hd.core.managers.WalletManager;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.ui.MultiBitUI;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.languages.Languages;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.Panels;
import org.multibit.hd.ui.views.fonts.TitleFontDecorator;
import org.multibit.hd.ui.views.themes.Themes;
import org.multibit.hd.ui.views.wizards.Wizards;
import org.multibit.hd.ui.views.wizards.welcome.WelcomeWizardState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Locale;

/**
 * <p>View to provide the following to application:</p>
 * <ul>
 * <li>Provision of components and layout for the main frame</li>
 * </ul>
 *
 * @since 0.0.1
 *  
 */
public class MainView extends JFrame {

  private static final Logger log = LoggerFactory.getLogger(MainView.class);

  private HeaderView headerView;
  private SidebarView sidebarView;
  private DetailView detailView;
  private FooterView footerView;

  // Need to track if a wizard was showing before a refresh occurred
  private boolean showExitingWelcomeWizard = false;
  private boolean showExitingPasswordWizard = false;
  private boolean isCentered = false;

  public MainView() {

    // Ensure we can respond to UI events
    CoreServices.uiEventBus.register(this);

    // Define the minimum size for the frame
    setMinimumSize(new Dimension(MultiBitUI.UI_MIN_WIDTH, MultiBitUI.UI_MIN_HEIGHT));

    // Set the starting size
    setSize(new Dimension(MultiBitUI.UI_MIN_WIDTH, MultiBitUI.UI_MIN_HEIGHT));

    // Provide all panels with a reference to the main frame
    Panels.applicationFrame = this;

    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    addComponentListener(new ComponentAdapter() {

      @Override
      public void componentMoved(ComponentEvent e) {
        updateConfiguration();
      }

      @Override
      public void componentResized(ComponentEvent e) {
        updateConfiguration();
      }

      /**
       * Keep the current configuration updated
       */
      private void updateConfiguration() {

        Rectangle bounds = getBounds();
        String lastFrameBounds = String.format("%d,%d,%d,%d", bounds.x, bounds.y, bounds.width, bounds.height);

        Configurations.currentConfiguration.getAppearance().setLastFrameBounds(lastFrameBounds);

      }
    });

  }

  /**
   * <p>Rebuild the contents of the main view based on the current configuration and theme</p>
   */
  public void refresh() {

    Preconditions.checkState(SwingUtilities.isEventDispatchThread(), "Must be in the EDT. Check MainController.");

    Locale locale = Configurations.currentConfiguration.getLocale();

    log.debug("Refreshing MainView with locale '{}'", locale);

    // Ensure the title font is updated depending on the new locale
    TitleFontDecorator.refresh(locale);

    // Ensure the frame title matches the new language and wallet name
    if (WalletManager.INSTANCE.getCurrentWalletSummary().isPresent()) {
      setTitle(
        Languages.safeText(MessageKey.MULTIBIT_HD_TITLE)
          + " - "
          + WalletManager.INSTANCE.getCurrentWalletSummary().get().getName());

    } else {
      // Do not have a wallet yet
      setTitle(Languages.safeText(MessageKey.MULTIBIT_HD_TITLE));
    }

    // Parse the configuration
    resizeToLastFrameBounds();

    // Clear out all the old content
    getContentPane().removeAll();

    // Rebuild the main content
    getContentPane().add(createMainContent());

    // Catch up on recent events
    CoreServices.getApplicationEventService().repeatLatestEvents();

    // Check for any wizards that were showing before the refresh occurred
    if (showExitingWelcomeWizard) {

      // This section must come after a deferred hide has completed

      // Determine the appropriate starting screen for the welcome wizard
      if (Configurations.currentConfiguration.isLicenceAccepted()) {
        log.debug("Showing exiting welcome wizard (select language)");
        Panels.showLightBox(Wizards.newExitingWelcomeWizard(WelcomeWizardState.WELCOME_SELECT_LANGUAGE).getWizardScreenHolder());
      } else {
        log.debug("Showing exiting welcome wizard (licence agreement)");
        Panels.showLightBox(Wizards.newExitingWelcomeWizard(WelcomeWizardState.WELCOME_LICENCE).getWizardScreenHolder());
      }

    } else if (showExitingPasswordWizard) {

      // This section must come after a deferred hide has completed

      log.debug("Showing exiting password wizard");

      // Force an exit if the user can't get through
      Panels.showLightBox(Wizards.newExitingPasswordWizard().getWizardScreenHolder());

    } else {

      log.debug("Showing detail view");

      // No wizards so this reset is a wallet unlock or settings change
      // The AbstractWizard.handleHide password unlock thread will close the wizard later
      // to get the effect of everything happening behind the wizard
      detailViewAfterWalletOpened();

      // Show the header information dependent on the overall configuration settings
      ViewEvents.fireViewChangedEvent(ViewKey.HEADER, Configurations.currentConfiguration.getAppearance().isShowBalance());

    }

    log.debug("Pack and show UI");

    // Tidy up and show
    pack();

    if (isCentered) {

      GraphicsDevice defaultScreen = getGraphicsDevices().get(0);

      GraphicsConfiguration defaultConfiguration = defaultScreen.getDefaultConfiguration();

      Rectangle sb = defaultConfiguration.getBounds();
      setBounds(
        sb.x + sb.width / 2 - getWidth() / 2,
        sb.y + sb.height / 2 - getHeight() / 2,
        getWidth(),
        getHeight()
      );

    }

    setVisible(true);

    log.debug("Refresh complete");

  }

  /**
   * @return True if the exiting welcome wizard will be shown on a reset
   */
  public boolean isShowExitingWelcomeWizard() {
    return showExitingWelcomeWizard;
  }


  /**
   * @param show True if the exiting welcome wizard should be shown during the next refresh
   */
  public void setShowExitingWelcomeWizard(boolean show) {

    showExitingWelcomeWizard = show;

  }


  /**
   * @return True if the exiting password wizard will be shown on a reset
   */
  public boolean isShowExitingPasswordWizard() {
    return showExitingPasswordWizard;
  }

  /**
   * @param show True if the exiting password wizard should be shown during the next refresh
   */
  public void setShowExitingPasswordWizard(boolean show) {

    showExitingPasswordWizard = show;

  }

  /**
   * Attempt to get focus to the sidebar
   */
  public void sidebarRequestFocus() {

    sidebarView.requestFocus();
  }

  /**
   * Update the sidebar wallet name tree node
   *
   * @param walletName The wallet name
   */
  public void sidebarWalletName(String walletName) {

    sidebarView.updateWalletTreeNode(walletName);

  }

  /**
   * Update the detail view to reflect the new wallet
   */
  public void detailViewAfterWalletOpened() {

    detailView.afterWalletOpened();
  }

  /**
   * @return The contents of the main panel (header, body and footer)
   */
  private JPanel createMainContent() {

    Preconditions.checkState(SwingUtilities.isEventDispatchThread(), "Must execute on the EDT");

    // Create the main panel and place it in this frame
    MigLayout layout = new MigLayout(
      Panels.migXYLayout(),
      "[]", // Columns
      "0[]0[]0[]"  // Rows
    );
    JPanel mainPanel = Panels.newPanel(layout);

    // Require opaque to ensure the color is shown
    mainPanel.setOpaque(true);

    // Deregister any previous references
    if (headerView != null) {

      log.debug("Deregister earlier views");
      CoreServices.uiEventBus.unregister(headerView);
      CoreServices.uiEventBus.unregister(sidebarView);
      CoreServices.uiEventBus.unregister(detailView);
      CoreServices.uiEventBus.unregister(footerView);

    }

    // Create supporting views (rebuild every time for language support)
    headerView = new HeaderView();
    // At present we are always in single wallet mode
    sidebarView = new SidebarView(false);
    detailView = new DetailView();
    footerView = new FooterView();

    // Create a splitter pane
    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

    // Set the divider width (3 is about right for a clean look)
    splitPane.setDividerSize(3);

    int sidebarWidth = MultiBitUI.SIDEBAR_LHS_PREF_WIDTH;
    try {
      sidebarWidth = Integer.valueOf(Configurations.currentConfiguration.getAppearance().getSidebarWidth());
    } catch (NumberFormatException e) {
      log.warn("Sidebar width configuration is not a number - using default");
    }

    if (Languages.isLeftToRight()) {
      splitPane.setLeftComponent(sidebarView.getContentPanel());
      splitPane.setRightComponent(detailView.getContentPanel());
      splitPane.setDividerLocation(sidebarWidth);
    } else {
      splitPane.setLeftComponent(detailView.getContentPanel());
      splitPane.setRightComponent(sidebarView.getContentPanel());
      splitPane.setDividerLocation(Panels.applicationFrame.getWidth() - sidebarWidth);
    }

    // Sets the colouring for divider and borders
    splitPane.setBackground(Themes.currentTheme.text());
    splitPane.setBorder(BorderFactory.createMatteBorder(
      1, 0, 1, 0,
      Themes.currentTheme.text()
    ));

    splitPane.applyComponentOrientation(Languages.currentComponentOrientation());

    splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,
      new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent pce) {

          // Keep the current configuration up to date
          Configurations.currentConfiguration.getAppearance().setSidebarWidth(String.valueOf(pce.getNewValue()));

        }
      }
    );

    // Add the supporting panels
    mainPanel.add(headerView.getContentPanel(), "growx,shrink,wrap"); // Ensure header size remains fixed
    mainPanel.add(splitPane, "grow,push,wrap");
    mainPanel.add(footerView.getContentPanel(), "growx,shrink"); // Ensure footer size remains fixed

    return mainPanel;
  }

  /**
   * <p>Resize the frame to the last bounds</p>
   */
  private void resizeToLastFrameBounds() {

    String frameDimension = Configurations.currentConfiguration.getAppearance().getLastFrameBounds();

    if (frameDimension != null) {

      String[] lastFrameDimension = frameDimension.split(",");
      if (lastFrameDimension.length == 4) {

        log.debug("Using absolute coordinates");

        try {
          int x = Integer.valueOf(lastFrameDimension[0]);
          int y = Integer.valueOf(lastFrameDimension[1]);
          int w = Integer.valueOf(lastFrameDimension[2]);
          int h = Integer.valueOf(lastFrameDimension[3]);
          Rectangle newBounds = new Rectangle(x, y, w, h);

          // Not centered
          isCentered = false;

          // Place the frame in the desired position (setBounds() does not work)
          setLocation(newBounds.x, newBounds.y);
          setPreferredSize(new Dimension(newBounds.width, newBounds.height));

          return;

        } catch (NumberFormatException e) {
          log.error("Incorrect format in configuration - using defaults", e);
        }

      } else if (lastFrameDimension.length == 2) {

        log.debug("Using partial coordinates");

        try {
          int w = Integer.valueOf(lastFrameDimension[0]);
          int h = Integer.valueOf(lastFrameDimension[1]);
          Dimension newBounds = new Dimension(w, h);

          // Center in main screen
          isCentered = true;

          // Place the frame in the desired position (setBounds() does not work)
          setPreferredSize(new Dimension(newBounds.width, newBounds.height));

          return;

        } catch (NumberFormatException e) {
          log.error("Incorrect format in configuration - using defaults", e);
        }

      }

    }

    log.debug("Using default coordinates");

    // By default center in main screen
    isCentered = true;

    // Set preferred size based on internal defaults
    setPreferredSize(new Dimension(MultiBitUI.UI_MIN_WIDTH, MultiBitUI.UI_MIN_HEIGHT));

  }

  /**
   * @return The available graphics devices with the default in position 0
   */
  private List<GraphicsDevice> getGraphicsDevices() {

    List<GraphicsDevice> devices = Lists.newArrayList();

    // Get the default screen device
    GraphicsDevice defaultScreenDevice = GraphicsEnvironment
      .getLocalGraphicsEnvironment()
      .getDefaultScreenDevice();

    for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {

      if (GraphicsDevice.TYPE_RASTER_SCREEN == gd.getType()) {

        if (defaultScreenDevice == gd) {
          devices.add(0, gd);
        } else {
          devices.add(gd);
        }
      }
    }

    Preconditions.checkState(!devices.isEmpty(), "'devices' must not be empty. Is machine in headless mode?");

    return devices;
  }

}