package android.content;

import java.util.ArrayList;

import android.os.BatteryStats;
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
public class Profile{
	private static final String TAG = "ThresholdTable";
	private static final int SECS_IN_MIN = 60;
	private static final int SECS_IN_HOUR = 60*SECS_IN_MIN;
	private static final int MIN_HORIZON = SECS_IN_HOUR*24; /* 1 Day */
	private static final float HORIZON_TOL = 1.10F; /* 110% */
	private static final int NINE_HOURS = 9*60;
	private static final int SEC_PER_HOUR = 60*60;
	private static final int MA_PER_AMP = 1000;
	private static final float NOMINAL_VOLTAGE = 3.7F;
	
    private BatteryStats mStats;

	//private BatteryStatsReceiver batteryStatsReceiver;
	public PowerProfile mPowerProfile;
	//public UserProfile mUserProfile;
	//public DeviceProfile mDeviceProfile;
	private PowerProfile mProfile;
	
	private float mPercent;
	private float mEnergy; /* In Joules */
	private IBatteryStats mBatteryInfo;

	public Profile(Context context) {
		Log.d(TAG, "Profile() START");

		Log.d(TAG, "new PowerProfile(context)");
		mProfile = new PowerProfile(context);
		Log.d(TAG, "capacity"+mProfile.getBatteryCapacity());
		
		Log.d(TAG, "getService(\"batteryinfo\")");
        mBatteryInfo = IBatteryStats.Stub.asInterface(
                ServiceManager.getService("batteryinfo"));

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
        	mStats = com.android.internal.os.BatteryStatsImpl.CREATOR.createFromParcel(parcel);
		}
        catch (RemoteException e) {
            Log.e(TAG, "RemoteException:", e);
        }

		Log.d(TAG, "load()");
		
        load();
		
		Log.d(TAG, "Profile() END");

		/* Setup and register battery information receiver */
        //IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		//batteryStatsReceiver = new BatteryStatsReceiver();
		//context.registerReceiver(batteryStatsReceiver, filter);
		
        //mPowerProfile = new PowerProfile(context);        
        //mUserProfile = new UserProfile(context);
        //mDeviceProfile = new DeviceProfile(context);

	}

    private void load() {
        try {
            byte[] data = mBatteryInfo.getStatistics();
            Parcel parcel = Parcel.obtain();
            parcel.unmarshall(data, 0, data.length);
            parcel.setDataPosition(0);
            mStats = com.android.internal.os.BatteryStatsImpl.CREATOR
                    .createFromParcel(parcel);
            //mStats.distributeWorkLocked(BatteryStats.STATS_SINCE_CHARGED);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException:", e);
        }
    }

	/* Determines the charge probability by taking the count of all the
	 * previous discharge durations less than or equal to timeSinceSync divided
	 * by the total number discharge durations. */

	public double getChargeProb(int timeSinceSync) {
		/*int count = 0;
		List<Integer> d = mUserProfile.getDischargeTimes();
		
		for(int i : d){
			if(i <= timeSinceSync)
				count++;
		}
		return (double) count/d.size();*/
		return 1.0;
	}

	public ArrayList<Pair<Integer, Double>> getEnergyUsed(int t) {
		
		return new ArrayList<Pair<Integer, Double>>();
	}

	public int getHorizon() {
		/*int max = MIN_HORIZON;
		List<Integer> d = mUserProfile.getDischargeTimes();
		
		for(int i : d){
			if(i > max)
				max = i;
		}
*/
		/* Return max times tolerance in case duration even larger */
		//return (int) (max * HORIZON_TOL);
		return NINE_HOURS;
	}

	/* 
	 * Returns maximum battery in mAh
	 */
	public int getMaxBattery() {
		Log.d(TAG, "getMaxBattery()");
		double mAh = mProfile.getBatteryCapacity();
		Log.d(TAG, String.format("battery capacity = %.2f",mProfile.getBatteryCapacity()));
		//final int joules =  (int) (mProfile.getBatteryCapacity() / SEC_PER_HOUR * NOMINAL_VOLTAGE);
		//Log.d(TAG, String.format("battery energy = %d",joules));
		return (int) mAh;
	}

	/*public class BatteryStatsReceiver extends BroadcastReceiver {
		private static final float MA_IN_AMP = 1000;
		private static final float MV_IN_VOLT = 1000;

		@Override
		public void onReceive(Context context, Intent intent) {
			int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
			int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
			float voltage = (float) intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / (float) MV_IN_VOLT;
			mPercent = (float) level / (float)scale;
			
			mEnergy = (float) ((mPowerProfile.getBatteryCapacity() / MA_IN_AMP) * SECS_IN_HOUR * voltage * mPercent);

		}

	}*/
/*
	@Override
	public String toString() {
        String s = "";
		
        s += String.format("Remaining Battery Energy:\n\t%.2f mAh, %.2f J, (%.2f %%)\n", 
        		mPowerProfile.getBatteryCapacity() * mPercent,
        		mEnergy,
        		mPercent * 100);

        s += "Discharge times:\n";
		for(long i : mUserProfile.getDischargeTimes()){
			s += String.format("\t%d seconds = %.2f minutes = %.2f hours\n", i, (float)i/SECS_IN_MIN, (float)i/SECS_IN_HOUR);
		}
		
		return s;
	}
	*/
	

}
