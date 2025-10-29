import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;

public class AdminGUI extends JFrame {
    private final AuctionManager auctionManager;
    private final JTable auctionTable;
    private final DefaultTableModel tableModel;

    public AdminGUI(AuctionManager manager) {
        this.auctionManager = manager;
        
        setTitle("Auctionaire Admin Panel");
        setSize(850, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JTextField itemNameField = new JTextField(15);
        JTextField timeField = new JTextField(5);
        JTextField startPriceField = new JTextField(8);
        JButton addButton = new JButton("Add Auction");

        topPanel.add(new JLabel("Item Name:"));
        topPanel.add(itemNameField);
        topPanel.add(new JLabel("Duration (s):"));
        topPanel.add(timeField);
        topPanel.add(new JLabel("Start Price:"));
        topPanel.add(startPriceField);
        topPanel.add(addButton);

        String[] columnNames = {"ID", "Item Name", "Start Price", "Highest Bid", "Highest Bidder", "Time Left", "Status", "Action"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == getColumnCount() - 1;
            }
        };
        auctionTable = new JTable(tableModel);
        auctionTable.setRowHeight(25);
        JScrollPane scrollPane = new JScrollPane(auctionTable);
        
        Action stopAction = new AbstractAction("Stop") {
            public void actionPerformed(ActionEvent e) {
                int modelRow = Integer.parseInt(e.getActionCommand());
                int auctionId = (int) tableModel.getValueAt(modelRow, 0);
                auctionManager.stopAuction(auctionId);
                updateAuctionList();
            }
        };
        new ButtonColumn(auctionTable, stopAction, columnNames.length - 1);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        addButton.addActionListener(e -> {
            try {
                String name = itemNameField.getText();
                int time = Integer.parseInt(timeField.getText());
                double price = Double.parseDouble(startPriceField.getText());
                if(name.trim().isEmpty() || time <= 0 || price < 0){
                    JOptionPane.showMessageDialog(this, "Please enter valid auction details.", "Input Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                auctionManager.addAuction(name, time, price, this);
                itemNameField.setText("");
                timeField.setText("");
                startPriceField.setText("");
                updateAuctionList();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Please enter valid numbers for time and price.", "Input Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void updateAuctionList() {
        tableModel.setRowCount(0);
        for (Auction a : auctionManager.getAllAuctions()) {
            Object[] row = {
                a.getId(),
                a.getItemName(),
                String.format("%.2f", a.getStartPrice()),
                String.format("%.2f", a.getHighestBid()),
                a.getHighestBidderName(),
                a.getRemainingTime(),
                a.getStatus(),
                a.isActive() ? "Stop" : ""
            };
            tableModel.addRow(row);
        }
    }
}

