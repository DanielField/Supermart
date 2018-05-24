/**
 * 
 */
package delivery;

import exception.InvalidItemException;
import exception.StockException;
import stock.Item;
import stock.ItemStock;
import stock.PerishableItem;
import stock.Stock;

/**
 * Class which extends Truck to create a specific truck for perishable items.
 * 
 * @author Daniel Field
 *
 */
public class RefrigeratedTruck extends Truck {

	private double temperature;
	private int capacity = 800;
	private Stock cargo = new Stock();
	
	/**
	 * Construct a RefrigeratedTruck.
	 */
	public RefrigeratedTruck() {
		this.temperature = 0;
	}
	
	/**
	 * Construct a RefrigeratedTruck with the specified temperature.
	 * 
	 * @param temperature
	 */
	public RefrigeratedTruck(double temperature) {
		this.temperature = temperature;
	}
	
	/**
	 * Get the truck temperature.
	 * 
	 * @return temperature
	 */
	public double getTemperature() {
		return temperature;
	}
	
	/**
	 * Set the truck temperature.
	 * 
	 * @param temperature
	 */
	public void setTemperature(double temperature) {
		this.temperature = temperature;
	}

	/* (non-Javadoc)
	 * @see delivery.Truck#getCost()
	 */
	@Override
	public double getCost() {
		// 900 + 200 * 0.7^(T/5)
		return (900 + (200 * (Math.pow(0.7, temperature/5))));
	}

	/* (non-Javadoc)
	 * @see delivery.Truck#getCapacity()
	 */
	@Override
	public int getCapacity() {
		return capacity;
	}

	/* (non-Javadoc)
	 * @see delivery.Truck#addToCargo(stock.Stock)
	 */
	@Override
	public void addToCargo(Stock cargo) throws InvalidItemException, StockException {
		for (ItemStock is : cargo) {
			addToCargo(is.getItem(), is.getQuantity());
		}
	}

	/* (non-Javadoc)
	 * @see delivery.Truck#addToCargo(stock.Item, int)
	 */
	@Override
	public void addToCargo(Item item, int quantity) throws InvalidItemException, StockException {
		if (item.getClass() == PerishableItem.class) {
			if ((((PerishableItem)item).getTemperature() >= temperature && ((PerishableItem)item).getTemperature() <= 10) == false)
				throw new InvalidItemException("Item temperature is not valid for this truck.");
		} 
		if (cargo.containsItem(item)) {
			ItemStock is = cargo.getItemStock(item);
			cargo.increaseQuantity(is.getItemID(), quantity);
		}
		else cargo.addNewItem(item, quantity);
	}

	/* (non-Javadoc)
	 * @see delivery.Truck#removeFromCargo(stock.Item)
	 */
	@Override
	public void removeFromCargo(Item item) throws InvalidItemException {
		if (cargo.containsItem(item)) {
			cargo.remove(cargo.getItemStock(item));
		}
		else throw new InvalidItemException("Item does not exist.");
	}

	/* (non-Javadoc)
	 * @see delivery.Truck#removeFromCargo(stock.Item, int)
	 */
	@Override
	public void removeFromCargo(Item item, int quantity) throws StockException, InvalidItemException {
		if (cargo.containsItem(item)) {
			ItemStock is = cargo.getItemStock(item);
			cargo.decreaseQuantity(is.getItemID(), quantity);
		}
		else throw new InvalidItemException("Item does not exist.");
	}

	/* (non-Javadoc)
	 * @see delivery.Truck#removeFromCargo(int, int)
	 */
	@Override
	public void removeFromCargo(int itemID, int quantity) throws StockException, InvalidItemException {
		if (cargo.containsItem(itemID)) {
			cargo.decreaseQuantity(itemID, quantity);
		}
		else throw new InvalidItemException("Item does not exist.");
	}

	/* (non-Javadoc)
	 * @see delivery.Truck#getTotalCargo()
	 */
	@Override
	public int getTotalCargo() {
		return cargo.stockTotal();
	}

	/* (non-Javadoc)
	 * @see delivery.Truck#getCargo()
	 */
	@Override
	public Stock getCargo() {
		return cargo;
	}
}