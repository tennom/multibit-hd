package org.multibit.hd.ui.views.components.tables;

import org.joda.time.DateTime;
import org.multibit.hd.core.config.Configurations;
import org.multibit.hd.core.dto.FiatPayment;
import org.multibit.hd.core.store.TemplateData;
import org.multibit.hd.ui.languages.Languages;
import org.multibit.hd.ui.languages.MessageKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * <p>TableModel to provide the following to contact JTable:</p>
 * <ul>
 * <li>Adapts a list of payments into a table model</li>
 * </ul>
 *
 * @since 0.0.1
 *
 */
public class TemplateTableModel extends AbstractTableModel {

  public static final int DATE_COLUMN_INDEX = 0;
  public static final int LABEL_COLUMN_INDEX = 1;
  public static final int RECIPIENTS_COLUMN_INDEX = 2;
  public static final int AMOUNT_BTC_COLUMN_INDEX = 3;
  public static final int AMOUNT_FIAT_COLUMN_INDEX = 4;
  public static final int NOTE_COLUMN_INDEX = 5;

  private static final Logger log = LoggerFactory.getLogger(TemplateTableModel.class);

  private String[] columnNames = {
          Languages.safeText(MessageKey.DATE),
          Languages.safeText(MessageKey.LABEL),
          Languages.safeText(MessageKey.RECIPIENT),
          Languages.safeText(MessageKey.LOCAL_AMOUNT) + " ", // BTC symbol added later
          Languages.safeText(MessageKey.LOCAL_AMOUNT) + " " + Configurations.currentConfiguration.getBitcoin().getLocalCurrencySymbol(),
          Languages.safeText(MessageKey.PRIVATE_NOTES),
  };

  private Object[][] data;

  private List<TemplateData> templateData;

  public TemplateTableModel(List<TemplateData> templateData) {
    setTemplateData(templateData, false);
  }

  /**
   * Set the template data into the table
   *
   * @param templateData The templateData to show in the table
   */
  public void setTemplateData(List<TemplateData> templateData, boolean fireTableDataChanged) {

    this.templateData = templateData;

    data = new Object[templateData.size()][];

    int row = 0;
    for (TemplateData template : templateData) {

      Object[] rowData = new Object[]{
              template.getCreatedDate(),
              template.getLabel(),
              template.getRecipients(),
              template.getAmountBTC(),
              template.getAmountFiat(),
              template.getNote(),
      };

      data[row] = rowData;

      row++;
    }
    if (fireTableDataChanged) {
      fireTableDataChanged();
    }
  }

  public int getColumnCount() {
    return columnNames.length;
  }

  public int getRowCount() {
    return data.length;
  }

  public String getColumnName(int col) {
    return columnNames[col];
  }

  public Object getValueAt(int row, int col) {
    if (data.length == 0) {
      return "";
    }
    try {
      return data[row][col];
    } catch (NullPointerException npe) {
      log.error("NullPointerException reading row = " + row + ", column = " + col);
      return "";
    }
  }

  /**
   * JTable uses this method to determine the default renderer/
   * editor for each cell.  If we didn't implement this method,
   * then the last column would contain text ("true"/"false"),
   * rather than a check box.
   */
  public Class getColumnClass(int c) {
    switch (c) {
      case DATE_COLUMN_INDEX : return DateTime.class;
      case RECIPIENTS_COLUMN_INDEX : return TemplateData.class;
      case LABEL_COLUMN_INDEX : return String.class;
      case AMOUNT_BTC_COLUMN_INDEX : return String.class;
      case AMOUNT_FIAT_COLUMN_INDEX : return FiatPayment.class;
      case NOTE_COLUMN_INDEX : return String.class;
      default: return String.class;
     }
  }

  /**
   * Handle changes to the data
   */
  public void setValueAt(Object value, int row, int col) {
    // No table updates allowed
  }

  public List<TemplateData> getTemplateData() {
    return templateData;
  }

}
