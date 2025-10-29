import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

public class ButtonColumn extends AbstractCellEditor implements TableCellRenderer, TableCellEditor, ActionListener, MouseListener {
    private JTable table;
    private Action action;
    private Border originalBorder;
    private Border focusBorder;

    private JButton renderButton;
    private JButton editButton;
    private Object editorValue;
    private boolean isButtonColumnEditor;

    public ButtonColumn(JTable table, Action action, int column) {
        this.table = table;
        this.action = action;

        renderButton = new JButton();
        editButton = new JButton();
        editButton.setFocusPainted(false);
        editButton.addActionListener(this);
        originalBorder = editButton.getBorder();
        setFocusBorder(new LineBorder(Color.BLUE));

        TableColumnModel columnModel = table.getColumnModel();
        columnModel.getColumn(column).setCellRenderer(this);
        columnModel.getColumn(column).setCellEditor(this);
        table.addMouseListener(this);
    }
    
    public void setFocusBorder(Border focusBorder) {
        this.focusBorder = focusBorder;
        editButton.setBorder(focusBorder);
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        if (value == null || value.toString().isEmpty()) {
            return null; 
        }
        editButton.setText(value.toString());
        this.editorValue = value;
        return editButton;
    }

    @Override
    public Object getCellEditorValue() {
        return editorValue;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value == null || value.toString().isEmpty()) {
            return null;
        }

        if (isSelected) {
            renderButton.setForeground(table.getSelectionForeground());
            renderButton.setBackground(table.getSelectionBackground());
        } else {
            renderButton.setForeground(table.getForeground());
            renderButton.setBackground(UIManager.getColor("Button.background"));
        }
        renderButton.setBorder(hasFocus ? focusBorder : originalBorder);
        renderButton.setText(value.toString());
        return renderButton;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        int row = table.convertRowIndexToModel(table.getEditingRow());
        fireEditingStopped();
        ActionEvent event = new ActionEvent(table, ActionEvent.ACTION_PERFORMED, "" + row);
        action.actionPerformed(event);
    }

    public void mousePressed(MouseEvent e) {
        if (table.isEditing() && table.getCellEditor() == this)
            isButtonColumnEditor = true;
    }

    public void mouseReleased(MouseEvent e) {
        if (isButtonColumnEditor && table.isEditing())
            table.getCellEditor().stopCellEditing();
        isButtonColumnEditor = false;
    }

    public void mouseClicked(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
}

