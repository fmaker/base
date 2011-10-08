package android.content;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

import android.os.BatteryManager;
import android.os.BatteryStats;
import android.os.BatteryStats.HistoryItem;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.app.IBatteryStats;
import com.android.internal.os.PowerProfile;

/**
 * Represents the current profile of the user, battery and device.
 * 
 * @author fmaker
 *
 */
/**
 * @author fmaker
 * 
 */
public class Profile {
	private static final String TAG = "Profile";
	private static final int SECS_IN_MIN = 60;
	private static final int SECS_IN_HOUR = 60 * SECS_IN_MIN;
	private static final int MIN_HORIZON = 9 * 60;
	private static final int MIN_CHARGE_DURATION = 5 * 60;
	private static final int SCALE = 100;
	private static final int BIN_WIDTH = 60 * 5;
	private static final float MILLI = 1000.0F;
	private static final long SECS_PER_MIN = 60;

	private BatteryStats mStats;
	private PowerProfile mProfile;
	private final boolean debug = true;
	public int energyPerSync = 1;

	
	/* 
	 * bins is used to keep a map from the time since sync (bin number)
	 * to a map with the energy values used and how many times that value
	 * was used in this bin 
	 * 
	 * HashMap <timeSinceSync>, HashMap<energyUsed, count>> 
	 */
	
	private HashMap<Integer, HashMap<Float,Integer>> bins;
	private ArrayList<Integer> chargeTimes;

	public Profile(Context context) {

		mProfile = new PowerProfile(context);

		/* Load battery stats */
		load();

		/* Create and load bins */
		bins = new HashMap<Integer, HashMap<Float,Integer>>();
		fillBins();

		/* Find length of discharge times */
		chargeTimes = new ArrayList<Integer>();
		findChargeTimes();

		if(debug){
			FileOutputStream f;
			try {
				f = new FileOutputStream(new File("/data/data/","history_items.dat"));
				debugHistoryItems(f);
				f.flush();
				f.close();

				f = new FileOutputStream(new File("/data/data/","bins.dat"));
				debugBins(f);
				f.flush();
				f.close();

				f = new FileOutputStream(new File("/data/data/","charge_prob.dat"));
				debugChargeProb(f);
				f.flush();
				f.close();

				f = new FileOutputStream(new File("/data/data/","energy_used.dat"));
				debugEnergyUsed(f);
				f.flush();
				f.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e){
				e.printStackTrace();
			}
		}

		Log.d(TAG, "Profile() END");

	}

	private void load() {

		Slog.d(TAG, "Get battery info service.");
		IBatteryStats mBatteryInfo = IBatteryStats.Stub.asInterface(ServiceManager.getService("batteryinfo"));

		try {
			Slog.d(TAG, "Get battery stats");
			byte[] data = mBatteryInfo.getStatistics();
			Parcel parcel = Parcel.obtain();
			parcel.unmarshall(data, 0, data.length);
			parcel.setDataPosition(0);
			Slog.d(TAG, "Create battery stats object from Parcel");
			mStats = com.android.internal.os.BatteryStatsImpl.CREATOR
					.createFromParcel(parcel);
		} catch (RemoteException e) {
			Slog.e(TAG, "RemoteException:", e);
		}
	}
	
	private void findChargeTimes(){
		Log.d(TAG, "Finding charge times");
		byte lastStatus = BatteryManager.BATTERY_STATUS_DISCHARGING, status;
		long startTime = 0, timestamp = 0;
		
		/* Iterate over HistoryItems to find all charge durations */
		if (mStats.startIteratingHistoryLocked()) {
			final HistoryItem rec = new HistoryItem();

			while (mStats.getNextHistoryLocked(rec)) {

				/* Get item properties */
				timestamp = rec.time;
				status = rec.batteryStatus;
				
				/* Started discharging */
				if(lastStatus == BatteryManager.BATTERY_STATUS_CHARGING &&
						status == BatteryManager.BATTERY_STATUS_DISCHARGING){
					startTime = timestamp;
				}

				/* Stopped discharging */
				if(lastStatus == BatteryManager.BATTERY_STATUS_DISCHARGING &&
						status == BatteryManager.BATTERY_STATUS_CHARGING){
					final long duration = timestamp - startTime;
					if(duration >= MIN_CHARGE_DURATION)
						chargeTimes.add((int) (duration));
				}
				
				/* Save state for next */
				lastStatus = status;
			}
		}
		Log.d(TAG, "Found "+chargeTimes.size()+" charge times");
	}
	
	private void fillBins(){

		float percent, lastPercent = 100.0F;
		long offset = 0;
		float energy = 0F;
		float fullBattery = 0.0F;
		boolean first = true;
		int i = 0;

		if (mStats.startIteratingHistoryLocked()) {
			final HistoryItem rec = new HistoryItem();

			while (mStats.getNextHistoryLocked(rec)) {

				/* Change percent to floating point */
				percent = (float) rec.batteryLevel / (float) SCALE;

				/* Get relative timestamp */
				final int timestamp = (int) (rec.time - offset);
				Log.d(TAG, timestamp+" ("+rec.time+"),"+rec.batteryLevel);
				
				if(timestamp > 0){

					/* Check if operating on battery and that percent is decreasing */
					if (rec.batteryStatus == BatteryManager.BATTERY_STATUS_DISCHARGING
							&& lastPercent > percent && !first) {
	
						/* Add energy used to bin if level changed */
						energy = (float) (fullBattery * (lastPercent - percent));
						add(timestamp, energy);
	
					}
					/* Otherwise charging */
					else if (percent > lastPercent || first) {
	
						/* Calculate full battery based on current voltage level */
						//final float voltage = (float) rec.batteryVoltage / MILLI;
						final float voltage = 3.7F;
						fullBattery = (float) (mProfile.getBatteryCapacity()
								/ MILLI * SECS_IN_HOUR * voltage);
	
						/*
						 * Next event could be first discharge so we need to
						 * remember the time offset
						 */
						offset = rec.time;
	
						/* Need to force initialization on the first run only */
						first = false;
					}
				}

				/* Save for comparison next time */
				lastPercent = percent;
				i++;
			}
		}
		Log.d(TAG, "Loaded "+i+" HistoryItems");
	}
	
	/**
	 * Outputs history items to file for debugging
	 * 
	 * @param f - file to write data to
	 */
	private void debugHistoryItems(OutputStream f){
		PrintWriter out = new PrintWriter(f);
		float percent;
		long timestamp;
		
		/* Header */
		out.write("#number\ttimestamp\tpercent\n");

		if (mStats.startIteratingHistoryLocked()) {
			final HistoryItem rec = new HistoryItem();

			while (mStats.getNextHistoryLocked(rec)) {
 
				percent = (float) rec.batteryLevel / (float) SCALE;
				timestamp = rec.time;
					
				out.write(timestamp+"\t"+percent+"\n");
			}
		}
		out.close();
	}

	private void debugBins(OutputStream f){
		PrintWriter out = new PrintWriter(f);

		/* Header */
		out.write("#t\tvalues\n");
		
		for(int i : bins.keySet()){
			out.format("%d\t%s\n", i, bins.get(i).toString());
		}
		out.close();

	}
	
	private void debugChargeProb(OutputStream f){

		PrintWriter out = new PrintWriter(f);

		/* Header */
		out.write("#t\tprobability\n");
		
		for(int t=0;t<60*60*16;t+=15*60){
			out.format("%d\t%s\n", t, getChargeProb(t));
		}
		out.close();

	}
	
	private void debugEnergyUsed(OutputStream f){

		PrintWriter out = new PrintWriter(f);

		/* Header */
		out.write("#t\tprobability\n");

		for(int t : bins.keySet()){
			for(float v : bins.get(t).keySet())
				out.format("%d\t%s\n", t, v);
		}
		out.close();

	}

	/**
	 * Get the charging probability
	 * 
	 * @return probability of charging from 0 to 1 inclusive
	 */
	public double getChargeProb(int timeSinceSync) {
		int i = 0;

		/* Calculate pseudo CDF from known charge times */
		for(long l : chargeTimes){
			if(timeSinceSync >= l)
				i++;
		}

		final float probability = (float) i / (float)chargeTimes.size();
		
		/*if(debug)
			Log.d(TAG, i+"/"+chargeTimes.size());*/

		return probability;
	}

	/**
	 * We assume that cell phone is active (from charging to next charging)
	 * while the user is 'on the move'. This time is relatively predictable. So
	 * instead of average over absolute charging time, the calculation average
	 * over the active period.
	 * 
	 * For profile, each discharge period is a unit. We don't care about when
	 * discharging starts, but instead we care only about length of the
	 * discharging time. So we 'align' each period by their starting time. E.g.
	 * (assume time slot is 1 hour)
	 * 
	 * Time slot: 1 2 3...
	 * 
	 * Discharging Period 1: 8am 9am 10am...
	 * 
	 * Remaining: 100 90 80...
	 * 
	 * Used: 10 10 15...
	 * 
	 * ChargeProb: 0.000 0.000 0.001...
	 * 
	 * -----------------------------------------
	 * 
	 * Day 2: 9am 10am 11am...
	 * 
	 * -----------------------------------------
	 * 
	 * Profile:
	 * 
	 * Used 5 8 20...
	 * 
	 * CallProb: 0.001 0.001 0.002...
	 * 
	 * ChargeProb: 0.000 0.001 0.003...
	 * 
	 * @author yichuan
	 * 
	 */

	/**
	 * Get the energy consumption in a time slot. Energy used by everything
	 * except sync we scheduled. (Which can be phone call only). This value is a
	 * random variable based on energy consumption from all the time periods
	 * logged in the past.
	 * 
	 * The returned array has each value seen at that time slot and then the
	 * percent (or probability) of the total seen at that time slot. For example
	 * if we saw 1,1,1,2,2,3 at time slot t, then we return [(1,3/7), (2,2/7),
	 * (3/7)].
	 * 
	 * @return <EnergyUsed, Probability>
	 */

	public ArrayList<Pair<Integer, Double>> getEnergyUsed(int t) {
		return getBin(t);
	}

	/** 
	 * Gets a list of values and counts for each bin 
	 */
	public ArrayList<Pair<Integer, Double>> getBin(int t) {
		final int binNum = getBinNum(t);

		if (bins.containsKey(binNum)) {
			ArrayList<Pair<Integer, Double>> array = new ArrayList<Pair<Integer, Double>>();
			
			HashMap<Float,Integer> bin = bins.get(binNum);
			for(Float key : bin.keySet()){
				array.add(new Pair<Integer, Double>(bin.get(key),(double) key));
			}
			return array;

		} else {
			return new ArrayList<Pair<Integer, Double>>();
		}
	}

	/**
	 * Get the maximum length of a discharging period.
	 * 
	 * @return length in seconds
	 */
	public int getHorizon() {
		return getMax(chargeTimes, MIN_HORIZON);
	}

	/**
	 * 
	 * @return Maximum battery percentage
	 */
	public int getMaxBattery() {
		return SCALE;
	}

	/**
	 * Utility functions
	 */
	
	/*private <V> Integer getMaxKey(HashMap<Integer,V> map, Integer minimum){
		Integer max = minimum;
		for(Integer key : map.keySet()){
			if(key > max)
				max = key;
		}
		return max;
	}*/
	
	private Integer getMax(ArrayList<Integer> array, Integer minimum){
		Integer max = minimum;
		for(Integer l : array){
			if(l > max)
				max = l;
		}
		return max;
	}

	/* Return the bin number of the given time */
	private int getBinNum(int t) {
		return t / BIN_WIDTH;
	}

	/* Add value to appropriate bin */
	private void add(int t, float value) {
		Log.d(TAG, "\tadd("+t+","+value+")");

		int binNum = getBinNum(t);
		Log.d(TAG, "\tbin number: "+binNum);
		HashMap<Float,Integer> bin;

		/* Make new bin if none exists */
		if (!bins.containsKey(binNum)){
			Log.d(TAG, "\tmaking new bin: "+binNum);
			bins.put(binNum, new HashMap<Float,Integer>());
		}

		/* Find appropriate bin */
		bin = bins.get(binNum);

		/* Add new bin if none exists */
		if (!bin.containsKey(value)){
			Log.d(TAG, "\tbin does not contain key: "+value);
			bin.put(value, 1);
		}
		else{
			int count = bin.get(value) + 1;
			bin.put(value, count);
			Log.d(TAG, "\tbin DOES     contain key: "+value+" count="+count);
		}

	}

}
