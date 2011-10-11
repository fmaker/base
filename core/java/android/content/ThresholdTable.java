package android.content;

import java.util.ArrayList;

import android.util.Log;
import android.util.Pair;

/**
 * Threshold calculator
 * 
 * Generates the threshold table which determines the amount of time after the
 * last sync to sync again given the current state.
 * 
 * So time slot 0 in the threshold is the last time the phone is disconnected
 * from charger.
 * 
 * @author yichuan
 * 
 */

public class ThresholdTable {
	public Profile profile;
	private int horizon;
	private int maxBattery;

	/* Constants */
	public float rewardPerRemainEnergy = 0;
	public int energyPerSync;
	public float rewardPerOtherUse = 1;

	public ThresholdTable(Profile profile) {
		this.profile = profile;
		horizon = profile.getHorizon();

	}

	public float[] getThreshold() {
		Log.d("ThresholdTable", "new float["+horizon+"]");
		float[] threshold = new float[horizon];

		threshold[horizon-1]=0;
		for (int t = horizon - 2; t >= 0; t--) {
			ArrayList<Pair<Integer, Float>> thisSlot = profile.getEnergyUsed(t);
			
			float th = 0;
			for( Pair<Integer, Float> v: thisSlot){
				Log.d("ThresholdTable", "Value: "+v.first+" prob: "+ v.second);
				th += v.first * v.second;

			}
			th /= profile.chargeTimes.size();
			threshold[t] = threshold[t+1] + th;
			Log.d("ThresholdTable","----------------------th: "+th);
		}

		return threshold;
	}

}
