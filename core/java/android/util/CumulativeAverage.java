package android.util;


/* Holds a cumulative average using this formula:
 * 	Avg_i+1 = (x_i+1 + i * Avg_i) / (i + 1)
 */

public class CumulativeAverage{
	private int count = 0;
	private double average = 0.0;
	
	public void add(double value){
		average = (value + count*average)/(count+1);
		count++;
	}

	public double getAverage(){
		return average;
	}
	
}
