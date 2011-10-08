package android.content;

import java.util.ArrayList;

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
		maxBattery = profile.getMaxBattery();
		energyPerSync = profile.energyPerSync;

	}

	public int[] getThreshold() {
		int[] threshold = new int[horizon];

		for (int t = horizon - 1; t >= 0; t--) {
			ArrayList<Pair<Integer, Double>> thisSlot = profile.getEnergyUsed(t);
			
			float th = 0;
			for( Pair<Integer, Double> v: thisSlot){
				th += v.first * v.second;

			}
			threshold[t] = (int)th;
		}

		return threshold;
	}

}
