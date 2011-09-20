package android.content;

import java.util.ArrayList;
import java.util.HashMap;

import android.os.BatteryStats;
import android.os.BatteryStats.HistoryItem;
import android.os.BatteryManager;
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
	private static final int MIN_HORIZON = SECS_IN_HOUR * 24; /* 1 Day */
	private static final float HORIZON_TOL = 1.10F; /* 110% */
	private static final int NINE_HOURS = 9 * 60;
	private static final int SEC_PER_HOUR = 60 * 60;
	private static final float LAST_VOLTAGE = 3.7F; /*
													 * TODO: Update with actual
													 * voltage
													 */
	private static final int BIN_WIDTH = 60 * 5;
	private static final float MILLI = 1000.0F;

	private BatteryStats mStats;

	// private BatteryStatsReceiver batteryStatsReceiver;
	public PowerProfile mPowerProfile;
	// public UserProfile mUserProfile;
	// public DeviceProfile mDeviceProfile;
	private PowerProfile mProfile;

	private float mPercent;
	private float mEnergy; /* In Joules */
	private IBatteryStats mBatteryInfo;

	public Profile(Context context) {
		Log.d(TAG, "Profile() START");

		Log.d(TAG, "new PowerProfile(context)");
		mProfile = new PowerProfile(context);
		Log.d(TAG, "capacity" + mProfile.getBatteryCapacity());

		Log.d(TAG, "getService(\"batteryinfo\")");
		mBatteryInfo = IBatteryStats.Stub.asInterface(ServiceManager
				.getService("batteryinfo"));

		Log.d(TAG, "try {");
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

		Log.d(TAG, "load()");

		load();

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

	private static final float MAX_VOLTAGE = 4.1F;

	public int getMaxLevel() {
		return (int) (mProfile.getBatteryCapacity() / MILLI * SECS_IN_HOUR * MAX_VOLTAGE);
	}

	/**
	 * Get the charging probability
	 * 
	 * @return probability of charging from 0 to 1 inclusive
	 */
	public double getChargeProb(int timeSinceSync) {
		return timeSinceSync >= NINE_HOURS ? 1.0 : 0.0;
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
	private HashMap<Integer, ArrayList<Float>> bins;
	private float voltage = 0.0F;

	public ArrayList<Pair<Integer, Double>> getEnergyUsed(int t) {

		final int scale = mProfile.getBatteryScale();

		float percent, lastPercent = 100.0F;
		long offset = 0;
		float energy = 0F;
		float fullBattery = 0.0F;
		boolean first = true;

		if (mStats.startIteratingHistoryLocked()) {
			final HistoryItem rec = new HistoryItem();

			while (mStats.getNextHistoryLocked(rec)) {

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

		return new ArrayList<Pair<Integer, Double>>();
	}

	/*
	 * Utility functions for getEnergyUsed()
	 */

	/* Return the bin number of the given time */
	private int getBinNum(int t) {
		return t / BIN_WIDTH;
	}

	/* Add value to appropriate bin */
	public void add(int t, float value) {
		int binNum = getBinNum(t);
		ArrayList<Float> bin;

		/* Make new bin if none exists */
		if (!bins.containsKey(binNum))
			bins.put(binNum, new ArrayList<Float>());

		/* Find appropriate bin and add */
		bin = bins.get(binNum);
		bin.add(value);

	}

	/* Gets the average value at t */
	public float get(int t) {
		final int binNum = getBinNum(t);

		if (bins.containsKey(binNum)) {
			ArrayList<Float> bin = bins.get(binNum);

			float sum = 0.0F;
			for (float f : bin)
				sum += f;

			return sum / bin.size();

		} else {
			return 0.0F;
		}
	}

	/**
	 * Get the maximum length of a discharging period.
	 * 
	 * @return length in seconds
	 */
	public int getHorizon() {
		return NINE_HOURS;
	}

	/**
	 * 
	 * @return Maximum battery energy (when fully charged) in Joules
	 */
	public int getMaxBattery() {
		Log.d(TAG, "getMaxBattery()");
		double mAh = mProfile.getBatteryCapacity();
		Log.d(TAG,
				String.format("battery capacity = %.2f",
						mProfile.getBatteryCapacity()));
		final int joules = (int) (mProfile.getBatteryCapacity() / MILLI
				* SEC_PER_HOUR * LAST_VOLTAGE);
		Log.d(TAG, String.format("battery energy = %d", joules));
		return (int) mAh;
	}

	/*
	 * @Override public String toString() { String s = "";
	 * 
	 * s +=
	 * String.format("Remaining Battery Energy:\n\t%.2f mAh, %.2f J, (%.2f %%)\n"
	 * , mPowerProfile.getBatteryCapacity() * mPercent, mEnergy, mPercent *
	 * 100);
	 * 
	 * s += "Discharge times:\n"; for(long i :
	 * mUserProfile.getDischargeTimes()){ s +=
	 * String.format("\t%d seconds = %.2f minutes = %.2f hours\n", i,
	 * (float)i/SECS_IN_MIN, (float)i/SECS_IN_HOUR); }
	 * 
	 * return s; }
	 */

}
