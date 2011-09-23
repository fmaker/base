package android.content;

import java.io.File;
import java.util.ArrayList;

import android.util.Log;
import android.util.Pair;

import com.android.internal.util.FloatMemoryMappedArray;

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

public class ThresholdTableGenerator implements Runnable{
	private final static String TAG = "ThresholdTableGenerator";
	private Profile profile;
	private int horizon;
	private int maxBattery;

	private FloatMemoryMappedArray V;


	/* Constants */
	private float rewardPerRemainEnergy = 0;
	private int energyPerSync = 1;
	private float rewardPerOtherUse = 2;

	public ThresholdTableGenerator(Profile profile, File tmp) {
		this.profile = profile;
		horizon = profile.getHorizon();
		maxBattery = profile.getMaxBattery();

		Log.d(TAG, String.format("new float[%d][%d][%d]",horizon, maxBattery, horizon));
		Log.d(TAG, "Creating table");
		V = new FloatMemoryMappedArray(horizon, maxBattery, horizon, tmp);
		Log.d(TAG, "Created table");

		new Thread(this).start();
	}

	@Override
	public void run() {
		Log.d(TAG, "Filling table");
		V.fill(-1.0F);
		Log.d(TAG, "Filled table");
	}

	public int[][] getThreshold() {
		int[][] threshold = new int[horizon][maxBattery];

		for (int t = horizon - 1; t >= 0; t--) {
			for (int Er = 0; Er < maxBattery; Er++) {
				threshold[t][Er] = horizon;
				for (int tau = 0; tau <= t; tau++) {
					float Vs = reward(t, Er, tau, true);
					float Vi = reward(t, Er, tau, false);
					if (Vs > Vi) {
						threshold[t][Er] = tau;
						break;
					}
				}
			}
		}
		return threshold;
	}

	private float V(int t, int Er, int tau) {
		float Vs = reward(t, Er, tau, true);
		float Vi = reward(t, Er, tau, false);

		return (Vs > Vi) ? Vs : Vi;
	}

	private float reward(int t, int Er, int tau, boolean sync) {
		if (Er == 0) {
			return 0;
		} else if (t == horizon - 1) {
			return Er * rewardPerRemainEnergy;
		}

		ArrayList<Pair<Integer, Double>> RV = profile.getEnergyUsed(t);

		float reward = 0;
		for (int i = 0; i < RV.size(); i++) {
			Pair<Integer, Double> thisSlot = RV.get(i);
			int used = thisSlot.first;
			float prob = thisSlot.second.floatValue();
			int E = Er - used;
			if (E < 0) {
				used = Er;
				E = 0;
			}

			float syncReward = 0;
			if (sync) {
				E = E - energyPerSync;
				syncReward = (float) Math.sqrt(tau + 1);
				if (E < 0) {
					syncReward = 0;
					E = 0;
				}
			}

			float v;
			if (syncReward == 0) {
				if (V.get(t + 1, E, tau + 1) == -1)
					V.put(t + 1, E, tau + 1,V(t + 1, E, tau + 1));
				v = V.get(t + 1, E, tau + 1) + rewardPerOtherUse * used
						+ syncReward;
			} else {
				if (V.get(t + 1, E, 0) == -1)
					V.put(t + 1, E, 0, V(t + 1, E, 0));
				v = V.get(t + 1, E, 0) + rewardPerOtherUse * used + syncReward;
			}

			reward += prob * v;
		}

		return reward;
	}

	/*public static void main(String[] args) {
		ThresholdTableGenerator th = new ThresholdTableGenerator(
				new SynthProfile());

		int[][] threshold = th.getThreshold();

		int i = 50;
		for (int j = 0; j < th.maxBattery; j++) {
			System.out.print("t:" + i + " ");
			System.out.format("Er: %2d ", j);
			System.out.print(threshold[i][j]);
			System.out.println();
		}

		System.out.println();
	}*/


}
