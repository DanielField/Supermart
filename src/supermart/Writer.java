/**
 * 
 */
package supermart;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import delivery.Manifest;
import delivery.Truck;
import stock.ItemStock;

/**
 * @author Daniel Field
 *
 */
public class Writer {
	public static void WriteManifestToCSV(String file, Manifest manifest) throws IOException {
		File f = new File(file);
		
		if (!f.exists()) {
			f.createNewFile();
		}
		
		FileWriter fw = new FileWriter(file);
		BufferedWriter bw = new BufferedWriter(fw);
		
		for (int y = 0; y < manifest.size(); y++) {
			Truck truck = manifest.get(y);
			bw.append(">" + truck.getClass().getName());
			bw.newLine();
			for (ItemStock is : truck.getCargo()) {
				bw.append(String.format("%s,%d", is.getItem().getName(), is.getQuantity()));
				bw.newLine();
			}
		}
		
		bw.close();
	}
}
