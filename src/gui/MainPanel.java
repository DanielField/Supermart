package gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpringLayout;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import delivery.Manifest;
import delivery.OrdinaryTruck;
import delivery.RefrigeratedTruck;
import delivery.Truck;
import exception.InvalidItemException;
import exception.StockException;
import stock.Item;
import stock.ItemStock;
import stock.OrdinaryItem;
import stock.PerishableItem;
import stock.Stock;
import supermart.Reader;
import supermart.Sale;
import supermart.SaleList;
import supermart.Store;
import supermart.Strings;
import supermart.Utils;
import supermart.Writer;

/**
 * This is the main panel. All of the other panels go inside this panel.
 * 
 * @author Daniel Field
 * @author Allen Basic
 *
 */
public class MainPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private SpringLayout layout;
	
	private static Store store = null;
	//private Stock inventory = null;
	private Object[][] inventory = null;
	Stock itemProperties = null;
	private Manifest manifest = null; 
	
	JLabel lblName, lblCapital;
	JButton btnStoreInfo, btnInventory, btnExportManifest, btnImportSalesLog, btnImportManifest;
	
	String status = "";
	JTextArea txtStatus;
	
	JScrollPane spInventory = null;
	JTable tblInventory = null;
	DefaultTableModel tblInventoryModel = null;
	String[] headings = {"Name", "Quantity", "Manufacturing Cost ($)", "Sell Price ($)", "Reorder Point", "Reorder Amount", "Temperature"};
	
	public MainPanel() {
		layout = new SpringLayout();
		setLayout(layout);
		
		store = Store.getInstance();
		
		try {
			LoadStoreInformation(Strings.STORE_INFO_CSV);
			status += "Store information loaded into memory.\r\n";
		} catch (IOException e) {
			status += "Unable to load the store information file. Setting the information to default.\\r\\n";
			
		}
		
		try {
			LoadEmptyInventory(Strings.ITEM_PROPERTIES_CSV);
			status += "Inventory loaded into memory.\r\n";
		} catch (IOException ioe) {
			status += "Unable to load the inventory.\r\n";
		} catch (InvalidItemException iie) {
			status += "One or more items in the inventory file are invalid.\\r\\n";
		}

		InitialiseLabels();
		InitialiseTable();
		InitialiseButtons();
		InitialiseStatusField();
		
		// This is the default display when the programme is loaded.
		DisplayStoreInformation();
		
		try {
			ExportManifest();
		} catch (IOException e) {
			status += "Unable to export the manifest to the specified file.\r\n";
		} catch (InvalidItemException e) {
			status += "There was an issue with one or more of the items.\r\n";
		} catch (StockException e) {
			status += "There was an issue regarding the cargo of one or more trucks.\r\n";
		}
		
		PopulateInventory();
		
		txtStatus.append("GUI loaded.\r\nDisplaying store information.\r\n");
	}
	
	private void LoadStoreInformation(String file) throws IOException {
		String[] info = Reader.ReadStoreInfoFromCSV(file);
		store.setName(info[0]);
		store.setCapital(Double.parseDouble(info[1]));
	}
	
	private void LoadEmptyInventory(String file) throws IOException, InvalidItemException {
		itemProperties = Reader.ReadItemPropertiesFromCSV(file);
		inventory = new Object[itemProperties.size()][7];

		for (int i = 0; i < itemProperties.size(); i++) {
			Item item = itemProperties.get(i).getItem();
			inventory[i][0] = item.getName();
			inventory[i][1] = itemProperties.get(i).getQuantity();
			inventory[i][2] = item.getManufacturingCost();
			inventory[i][3] = item.getSellPrice();
			inventory[i][4] = item.getReorderPoint();
			inventory[i][5] = item.getReorderAmount();
			
			if (item.getClass() == PerishableItem.class)
				inventory[i][6] = ((PerishableItem)item).getTemperature();
		}
	}
	
	private void LoadManifest(String file) throws IOException, NumberFormatException, InvalidItemException, StockException {
		manifest = Reader.ReadManifestFromCSV(file);
		
		// For each truck in the manifest
		for (Truck t : manifest) {
			// For each Item, quantity pair (ItemStock) in the cargo.
			for (ItemStock truckItemStock : t.getCargo()) {
				if (itemProperties.containsItem(truckItemStock.getItem().getName()))
					truckItemStock.getItem().setManufacturingCost(itemProperties.getItemStock(truckItemStock.getItem().getName()).getItem().getManufacturingCost());
			}
		}
		
		DecreaseCapital();
	}
	
	/**
	 * Populates the inventory with the information from the manifest file.
	 * The pre-condition of this is that the item properties have been loaded and the manifest has been loaded into memory.
	 */
	private void PopulateInventory() {
		// Loop through the inventory
		for (int i = 0; i < inventory.length; i++) {
			String itemName = (String)inventory[i][0];
			Item truckItem = null;
			
			// For each truck in the manifest
			for (Truck t : manifest) {
				// For each Item, quantity pair (ItemStock) in the cargo.
				for (ItemStock truckItemStock : t.getCargo()) {
					truckItem = truckItemStock.getItem();
					
					if (itemName.equals(truckItem.getName())) {
						inventory[i][1] = (int)inventory[i][1] + truckItemStock.getQuantity();
					}
				}
			}
		}
	}
	
	/**
	 * @return
	 * @throws InvalidItemException
	 * @throws StockException
	 */
	private Manifest ConstructManifestFromInventory() throws InvalidItemException, StockException {
		Manifest m = new Manifest();
		Truck ordinaryTruck = new OrdinaryTruck();
		Truck refrigeratedTruck = new RefrigeratedTruck();
		
		for (Object[] row : inventory) {
			// If the item is not perishable
			if (row[6] == null) {		
				// If the cargo plus the quantity being added is less than the maximum capacity
				if (ordinaryTruck.getTotalCargo() + (int)row[1] <= ordinaryTruck.getCapacity()) {
					// If the quantity is less than the reorder point, add the reorder amount to cargo
					if ((int)row[1] <= (int)row[4]) {
						Item item = new OrdinaryItem((String)row[0]);
						ordinaryTruck.addToCargo(item, (int)row[5]);
					}
				} else {
					// Create a new truck to fit the item that didn't fit.
					
					m.add(ordinaryTruck);
					ordinaryTruck = new OrdinaryTruck();
					
					// If the quantity is less than the reorder point, add the reorder amount to cargo
					if ((int)row[1] <= (int)row[4]) {
						Item item = new OrdinaryItem((String)row[0]);
						ordinaryTruck.addToCargo(item, (int)row[5]);
					}
				}
			} else {		
				// If the cargo plus the quantity being added is less than the maximum capacity
				if (refrigeratedTruck.getTotalCargo() + (int)row[1] < refrigeratedTruck.getCapacity()) {
					// If the quantity is less than the reorder point, add the reorder amount to cargo
					if ((int)row[1] <= (int)row[4]) {
						Item item = new PerishableItem((String)row[0]);
						refrigeratedTruck.addToCargo(item, (int)row[5]);
					}
				} else {
					// Create a new truck to fit the item that didn't fit.
					
					m.add(refrigeratedTruck);
					refrigeratedTruck = new RefrigeratedTruck();
					
					// If the quantity is less than the reorder point, add the reorder amount to cargo
					if ((int)row[1] <= (int)row[4]) {
						Item item = new PerishableItem((String)row[0]);
						refrigeratedTruck.addToCargo(item, (int)row[5]);
					}
				}
			}
		}
		
		if (ordinaryTruck.getTotalCargo() > 0)
			m.add(ordinaryTruck);
		if (refrigeratedTruck.getTotalCargo() > 0)
			m.add(refrigeratedTruck);
		
		return m;
	}
	
	/**
	 * Export a manifest based on the current inventory. This will automatically put the file in the application directory.
	 * 
	 * @throws IOException Throws if there is an error attempting to write the file
	 * @throws StockException Throws if there is an error relating to Stock
	 * @throws InvalidItemException Throws if there is an invalid item
	 */
	private void ExportManifest() throws IOException, InvalidItemException, StockException {
		Manifest m = ConstructManifestFromInventory();
		Writer.WriteManifestToCSV(Strings.MANIFEST_CSV, m);
	}
	
	/**
	 * Export a manifest based on the current inventory.
	 * 
	 * @param file The file to which the manifest is written.
	 * @throws IOException Throws if there is an error attempting to write the file
	 * @throws StockException Throws if there is an error relating to Stock
	 * @throws InvalidItemException Throws if there is an invalid item
	 */
	private void ExportManifest(String file) throws IOException, InvalidItemException, StockException {
		Manifest m = ConstructManifestFromInventory();
		Writer.WriteManifestToCSV(file, m);
	}
	
	/**
	 * ...
	 * The pre-condition of this is that the item properties have been loaded and the manifest has been loaded into memory.
	 */
	private void DecreaseCapital() {
		Item truckItem = null;
		double cost = 0;
		
		// For each truck in the manifest
		for (Truck t : manifest) {
			// For each Item, quantity pair (ItemStock) in the cargo.
			for (ItemStock truckItemStock : t.getCargo()) {
				truckItem = truckItemStock.getItem();

				// Increase the cost based on how large the quantity is.
				cost += truckItem.getManufacturingCost() * truckItemStock.getQuantity();
			}
		}
		
		// Subtract cost from capital
		store.setCapital(store.getCapital()-cost);
	}
	
	private void InitialiseButtons() {
		btnStoreInfo = Components.CreateButton(this, layout, "Store Info", 10, 10);
		layout.putConstraint(SpringLayout.EAST, btnStoreInfo, -10, SpringLayout.WEST, spInventory);
		btnStoreInfo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ClearScreen();
				DisplayStoreInformation();
				
				txtStatus.append("Displaying store information.\r\n");
				((JFrame)getTopLevelAncestor()).setTitle("SuperMart - Store Information");
			}
		});
		
		btnInventory = Components.CreateButton(this, layout, "Inventory", 10, 40);
		layout.putConstraint(SpringLayout.EAST, btnInventory, -10, SpringLayout.WEST, spInventory);
		btnInventory.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ClearScreen();
				DisplayInventory();
				txtStatus.append("Displaying store inventory.\r\n");
			}
		});
		
		//
		// Inventory-specific buttons
		//
		btnImportManifest = Components.CreateButton(this, layout, "Import manifest", 150, 0);
		layout.putConstraint(SpringLayout.NORTH, btnImportManifest, 10, SpringLayout.SOUTH, spInventory);
		btnImportManifest.setVisible(false);
		btnImportManifest.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Utils.DisplayMessage(MainPanel.this, "This will add to the currently loaded quantities. Use this only if you'd like an additional manifest file loaded.", "Important Note");
				
				// Choose a manifest file
				JFileChooser chooser = new JFileChooser();
				chooser.setDialogTitle("Choose a Manifest File");
				int result = chooser.showOpenDialog(MainPanel.this);
				
				// If the user chooses the file
				if (result == JFileChooser.APPROVE_OPTION) {
					// Attempt to load the manifest into memory.
					try {
						LoadManifest(chooser.getSelectedFile().getAbsolutePath());
						
						txtStatus.append("Populated inventory with the specified manifest.\r\n");
						txtStatus.append(String.format("Subtracted item costs from capital. Store capital is now %s\r\n", Utils.FormatDollars(store.getCapital())));
						
					} catch (NumberFormatException e1) {
						txtStatus.append("One or more of the quantities of the manifest file are invalid.\r\n");
					} catch (IOException e1) {
						txtStatus.append("There was an issue attempting to load the specified file.\r\n");
					} catch (InvalidItemException e1) {
						txtStatus.append("One or more of the items are invalid.\r\n");
					} catch (StockException e1) {
						txtStatus.append("There was an issue attempting to load the stock of a truck.\r\n");
					}
					
					PopulateInventory();
					DisplayInventory();
				} else {
					txtStatus.append("Import cancelled.\r\n");
				}
			}
		});
		
		btnExportManifest = Components.CreateButton(this, layout, "Export manifest", 150, 0);
		layout.putConstraint(SpringLayout.WEST, btnExportManifest, 10, SpringLayout.EAST, btnImportManifest);
		layout.putConstraint(SpringLayout.NORTH, btnExportManifest, 10, SpringLayout.SOUTH, spInventory);
		btnExportManifest.setVisible(false);
		btnExportManifest.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// Create a manifest file at the specified location
				JFileChooser chooser = new JFileChooser();
				chooser.setDialogTitle("Export New Manifest");
				int result = chooser.showSaveDialog(MainPanel.this);
	
				if (result == JFileChooser.APPROVE_OPTION) {
					
					try {
						ExportManifest(chooser.getSelectedFile().getAbsolutePath());
						txtStatus.append("Exported manifest based on current inventory.\r\n");
					} catch (IOException ex) {
						txtStatus.append("Unable to export the manifest to the specified file.\r\n");
					} catch (InvalidItemException ex) {
						txtStatus.append("There was an issue with one or more of the items.\r\n");
					} catch (StockException ex) {
						txtStatus.append("There was an issue regarding the cargo of one or more trucks.\r\n");
					}
					
				} else {
					txtStatus.append("Export cancelled.\r\n");
				}
			}
		});
		

		btnImportSalesLog = Components.CreateButton(this, layout, "Import sales log", 10, 100);
		layout.putConstraint(SpringLayout.WEST, btnImportSalesLog, 10, SpringLayout.EAST, btnExportManifest);
		layout.putConstraint(SpringLayout.NORTH, btnImportSalesLog, 10, SpringLayout.SOUTH, spInventory);
		btnImportSalesLog.setVisible(false);
		btnImportSalesLog.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// Create a manifest file at the specified location
				JFileChooser chooser = new JFileChooser();
				chooser.setDialogTitle("Import sales log");
				int result = chooser.showOpenDialog(MainPanel.this);
	
				if (result == JFileChooser.APPROVE_OPTION) {
					try {
						SaleList saleList = Reader.ReadSalesFromCSV(chooser.getSelectedFile().getAbsolutePath());
						
						for (Sale s : saleList.getList()) {
							for (int i = 0; i < inventory.length; i++) {
								String rowItemName = (String) inventory[i][0];
								
								if (s.getItemName().equals(rowItemName)) {
									double sellPrice = itemProperties.getItemStock(s.getItemName()).getItem().getSellPrice();
									store.setCapital(store.getCapital() + (sellPrice * s.getQuantity()));
									
									inventory[i][1] = (int) inventory[i][1] - s.getQuantity(); 
									
									break;
								}
							}
						}
						
						DisplayInventory();
						txtStatus.append("Imported store sales logs.\r\n");
						txtStatus.append("Inventory has been adjusted.\r\n");
						txtStatus.append(String.format("Added profits from sales log to the store capital. Capital is now %s\r\n", 
								Utils.FormatDollars(store.getCapital())));
					} catch (IOException ex) {
						txtStatus.append("There was an error attempting to read the specified sales log.\r\n");
					} catch (InvalidItemException ex) {
						txtStatus.append("One or more items are invalid.\r\n");
					}
				} else {
					txtStatus.append("Import cancelled.\r\n");
				}
			}
		});
		
		// Add the components to this JPanel
		Components.addComponents(this, btnStoreInfo, btnInventory, btnExportManifest, btnImportSalesLog, btnImportManifest);
	}
	
	private void InitialiseTable() {
		tblInventoryModel = new DefaultTableModel(inventory, headings);
		tblInventory = new JTable(tblInventoryModel) {
			private static final long serialVersionUID = 1L;
			
			// Disable user editing.
			@Override
			public boolean isCellEditable(int row, int column) {                
                return false;               
        };
		};
		
		spInventory = new JScrollPane(tblInventory);
		spInventory.setVisible(false);

		// Attempt to load the manifest into memory.
		try {
			LoadManifest("manifest.csv");
			
			status += "Populated inventory with the default manifest.\r\n";
			status += String.format("Subtracted item costs from capital. Store capital is now %s\r\n", Utils.FormatDollars(store.getCapital()));
			
		} catch (NumberFormatException e1) {
			status += "One or more of the quantities of the manifest file are invalid.\r\n";
		} catch (IOException e1) {
			status += "There was an issue attempting to load the default manifest file.\r\n";
		} catch (InvalidItemException e1) {
			status += "One or more of the items are invalid.\r\n";
		} catch (StockException e1) {
			status += "There was an issue attempting to load the stock of a truck.\r\n";
		}
		
		layout.putConstraint(SpringLayout.NORTH, spInventory, 10, SpringLayout.NORTH, this);
		layout.putConstraint(SpringLayout.WEST, spInventory, 150, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.EAST, spInventory, -10, SpringLayout.EAST, this);
		layout.putConstraint(SpringLayout.SOUTH, spInventory, -150, SpringLayout.SOUTH, this);
		
		add(spInventory);
	}
	
	private void InitialiseLabels() {
		// Initialise labels with default text
		lblName = Components.CreateLabel(this, layout, "Store name: Supermart", 150, 10);
		lblCapital = Components.CreateLabel(this, layout, "Store capital: $100,000.00", 150, 40);
		
		// Add the components to this JPanel
		Components.addComponents(this, lblName, lblCapital);
	}
	
	private void InitialiseStatusField() {
		txtStatus = Components.CreateTextArea(this, layout, status, 150, 0);
		txtStatus.setEditable(false);
		
		JScrollPane spStatus = new JScrollPane(txtStatus);
		spStatus.setVisible(true);
		
		txtStatus.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				JScrollBar v = spStatus.getVerticalScrollBar();
				v.setValue(v.getMaximum());
			}
			@Override
			public void removeUpdate(DocumentEvent e) {}
			@Override
			public void changedUpdate(DocumentEvent e) {}
		});
		
		layout.putConstraint(SpringLayout.NORTH, spStatus, 45, SpringLayout.SOUTH, spInventory);
		layout.putConstraint(SpringLayout.WEST, spStatus, 0, SpringLayout.WEST, spInventory);
		layout.putConstraint(SpringLayout.SOUTH, spStatus, -10, SpringLayout.SOUTH, this);
		layout.putConstraint(SpringLayout.EAST, spStatus, -10, SpringLayout.EAST, this);
	
		add(spStatus);
	}
	
	/**
	 * Clear the content from the JPanel such as the inventory table, sales log, manifest, or store info.
	 */
	private void ClearScreen() {
		HideInventory();
		HideStoreInformation();
	}
	
	private void DisplayStoreInformation() {
		lblName.setText(String.format("Store name: %s", store.getName()));
		String capital = Utils.FormatDollars(store.getCapital());
		lblCapital.setText(String.format("Store capital: %s", capital));

		lblName.setVisible(true);
		lblCapital.setVisible(true);
		
		btnStoreInfo.setEnabled(false);
	}
	
	private void HideStoreInformation() {
		lblName.setVisible(false);
		lblCapital.setVisible(false);
		
		btnStoreInfo.setEnabled(true);
	}
	
	private void DisplayInventory() {	
		tblInventoryModel = new DefaultTableModel(inventory, headings);
		tblInventory.setModel(tblInventoryModel);
		
		spInventory.setVisible(true);
		btnImportManifest.setVisible(true);
		btnExportManifest.setVisible(true);
		btnImportSalesLog.setVisible(true);
		
		btnInventory.setEnabled(false);
		
		((JFrame)getTopLevelAncestor()).setTitle("SuperMart - Inventory");
	}
	
	private void HideInventory() {
		spInventory.setVisible(false);
		btnImportManifest.setVisible(false);
		btnExportManifest.setVisible(false);
		btnImportSalesLog.setVisible(false);
		
		btnInventory.setEnabled(true);
	}
}
