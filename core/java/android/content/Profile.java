package android.content;

import java.util.ArrayList;
import java.util.HashMap;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.BatteryManager;
import android.os.BatteryStats;
import android.os.BatteryStats.HistoryItem;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.Pair;

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
	private static final int SCALE = 100;
	private static final int BIN_WIDTH = 60 * 1; /* One minute */
	private static final float MILLI = 1000.0F;

	private BatteryStats mStats;
	public PowerProfile mPowerProfile;
	private PowerProfile mProfile;
	private IBatteryStats mBatteryInfo;

	
	/* 
	 * bins is used to keep a map from the time since sync (bin number)
	 * to a map with the energy values used and how many times that value
	 * was used in this bin 
	 * 
	 * HashMap <timeSinceSync>, HashMap<energyUsed, count>> 
	 */
	
	private HashMap<Integer, HashMap<Float,Integer>> bins;


	public Profile(Context context) {

		mProfile = new PowerProfile(context);

		mBatteryInfo = IBatteryStats.Stub.asInterface(ServiceManager
				.getService("batteryinfo"));

		byte[] data;
		try {
			Log.d(TAG, "getStatistics()");
			data = mBatteryInfo.getStatistics();
			Parcel parcel = Parcel.obtain();
			Log.d(TAG, "unmarshall()");
			parcel.unmarshall(data, 0, data.length);
			parcel.setDataPosition(0);
			Log.d(TAG, "createFromParcel()");
			mStats = com.android.internal.os.BatteryStatsImpl.CREATOR
					.createFromParcel(parcel);
		} catch (RemoteException e) {
			Log.e(TAG, "RemoteException:", e);
		}

		/* Load battery stats */
		load();
		
		/* Create and load bins */
		bins = new HashMap<Integer, HashMap<Float,Integer>>();
		fillBins();

		Log.d(TAG, "Profile() END");

	}

	private void load() {
		try {
			byte[] data = mBatteryInfo.getStatistics();
			Parcel parcel = Parcel.obtain();
			parcel.unmarshall(data, 0, data.length);
			parcel.setDataPosition(0);
			mStats = com.android.internal.os.BatteryStatsImpl.CREATOR
					.createFromParcel(parcel);
			// mStats.distributeWorkLocked(BatteryStats.STATS_SINCE_CHARGED);
		} catch (RemoteException e) {
			Log.e(TAG, "RemoteException:", e);
		}
	}
	
	private void fillBins(){
		final int scale = SCALE;

		float percent, lastPercent = 100.0F;
		long offset = 0;
		float energy = 0F;
		float fullBattery = 0.0F;
		boolean first = true;

		if (mStats.startIteratingHistoryLocked()) {
			final HistoryItem rec = new HistoryItem();

			while (mStats.getNextHistoryLocked(rec)) {
				addToDb(rec);

				/* Change percent to floating point */
				percent = (float) rec.batteryLevel / (float) scale;

				/* Get relative timestamp */
				final int timestamp = (int) ((rec.time - offset) / MILLI);

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
					final float voltage = (float) rec.batteryVoltage / MILLI;
					fullBattery = (float) (mProfile.getBatteryCapacity()
							/ MILLI * SECS_IN_HOUR * voltage);

					/*
					 * Next event will the first discharge so we need to
					 * remember the time offset
					 */
					offset = rec.time;

					/* Need to force initialization on the first run only */
					first = false;
				}

				/* Save for comparison next time */
				lastPercent = percent;

			}
		}
	}

	/**
	 * Get the charging probability
	 * 
	 * @return probability of charging from 0 to 1 inclusive
	 */
	public double getChargeProb(int timeSinceSync) {
		return timeSinceSync >= getMaxKey(bins,MIN_HORIZON) ? 1.0 : 0.0;
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
			return null;
		}
	}

	/**
	 * Get the maximum length of a discharging period.
	 * 
	 * @return length in seconds
	 */
	public int getHorizon() {
		return getMaxKey(bins, MIN_HORIZON);
	}

	/**
	 * 
	 * @return Maximum battery percentage
	 */
	public int getMaxBattery() {
		return SCALE;
	}
	
	/**
	 * HistoryItems are stored in a database to retain a longer history than
	 * the framework does
	 */
	
	private class BatteryHistoryOpenHelper extends SQLiteOpenHelper {
		private static final int VERSION = 1;
		private static final String DB_NAME = "battery_stats";
		private static final String TABLE_NAME = "battery_stats";
		private static final String KEY_ID = "id";
		private static final String KEY_TIME = "time";
		private static final String KEY_BATTERYLEVEL = "batteryLevel";
		private static final String KEY_BATTERYSTATUS = "batteryStatus";
		private static final String KEY_VOLTAGE = "voltage";
		
		private static final String TABLE_CREATE =
				"CREATE TABLE "+TABLE_NAME+" ("+
						KEY_ID + " INTEGER PRIMARY KEY,"+
						KEY_TIME + " INTEGER,"+
						KEY_BATTERYLEVEL + " INTEGER,"+
						KEY_BATTERYSTATUS + " INTEGER,"+
						KEY_VOLTAGE + " INTEGER"+
						");";

		public BatteryHistoryOpenHelper(Context context, String name,
				CursorFactory factory, int version) {
			super(context, DB_NAME, factory, VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(TABLE_CREATE);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		}
		
	}

	private void addToDb(HistoryItem rec) {
		
	}

	/**
	 * Utility functions
	 */
	
	private <V> Integer getMaxKey(HashMap<Integer,V> map, Integer minimum){
		Integer max = minimum;
		for(Integer key : map.keySet()){
			if(key > max)
				max = key;
		}
		return max;
	}

	/* Return the bin number of the given time */
	private int getBinNum(int t) {
		return t / BIN_WIDTH;
	}

	/* Add value to appropriate bin */
	public void add(int t, float value) {
		int binNum = getBinNum(t);
		HashMap<Float,Integer> bin;

		/* Make new bin if none exists */
		if (!bins.containsKey(binNum))
			bins.put(binNum, new HashMap<Float,Integer>());

		/* Find appropriate bin */
		bin = bins.get(binNum);

		/* Add new bin if none exists */
		if (!bin.containsKey(value)){
			bin.put(value, 1);
		}
		else{
			int count = bin.get(value);
			bin.put(value, count++);
		}

	}

}
