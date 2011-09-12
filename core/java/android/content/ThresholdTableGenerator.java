package android.content;
import java.util.ArrayList;
import java.util.Arrays;

import android.util.Log;
import android.util.Pair;

/**
 * Threshold calculator
 * 
 * Generates the threshold table which determines the amount of time after
 * the last sync to sync again given the current state.
 * 
 * So time slot 0 in the threshold is the last time the phone is disconnected
 * from charger.
 * 
 * @author yichuan
 * 
 */

public class ThresholdTableGenerator {
	private final static String TAG = "ThresholdTableGenerator";

	private Profile profile;
	private int getHorizon;
	private int maxBattery;
	private int secondsPerTimeslot;

	private int energyPerSync;

	private double rewardPerEo;
	private double rewardPerSync;

	private double[][][] vs;
	private double[][][] vi;

	/* Constants */

	// reward of 'unit' remaining energy at charging time
	private double Re;

	// charging time

	public ThresholdTableGenerator(Profile profile) {
		Log.d(TAG,"ThresholdTableGenerator()");
		this.profile = profile;
		getHorizon = profile.getHorizon();
		maxBattery = profile.getMaxBattery();
		Log.d(TAG,String.format("creating two arrays[%d][%d][%d]", getHorizon, maxBattery, getHorizon));
		vs = new double[getHorizon][maxBattery][getHorizon];
		vi = new double[getHorizon][maxBattery][getHorizon];
		Log.d(TAG,"filling two arrays");

		for (double[][] mat : vs)
			for (double[] row : mat)
				Arrays.fill(row, -1);

		for (double[][] mat : vi)
			for (double[] row : mat)
				Arrays.fill(row, -1);
	}

	public double[][][] getThreshold() {
		double[][][] threshold = new double[getHorizon][maxBattery][getHorizon];

		for (int i = 0; i < getHorizon; i++) {
			for (int j = 0; j < maxBattery; j++) {
				for (int k = 0; k < getHorizon; k++) {
					threshold[i][j][k] = V_star(i, j, k);
				}
			}
		}
		return threshold;
	}

	private double V_star(int t, int Er, int tau) {
		double p = this.profile.getChargeProb(t);
		return Er * Re * p + V(t, Er, tau) * (1 - p);
	}

	private double V(int t, int Er, int tau) {
		if (Er == 0) {
			return 0;
		}
		// else if (t == horizon) {
		// return Er * Re;
		// }
		else {
			if (vs[t][Er][tau] == -1) {
				vs[t][Er][tau] = reward(t, Er, tau, true);
			}
			if (vi[t][Er][tau] == -1) {
				vi[t][Er][tau] = reward(t, Er, tau, true);
			}

			double Vs = vs[t][Er][tau];
			double Vi = vi[t][Er][tau];
			return (Vs > Vi) ? Vs : Vi;
		}
	}

	private double reward(int t, int Er, int tau, boolean sync) {
		ArrayList<Pair<Integer, Double>> RV = profile.getEnergyUsed(t);

		double reward = 0;

		for (int i = 0; i < RV.size(); i++) {
			Pair<Integer, Double> thisSlot = RV.get(i);
			int used = thisSlot.first;
			double prob = thisSlot.second;
			int E = Er - used;
			if (E < 0) {
				used = Er;
				E = 0;
			}

			double syncReward = 0;

			if (sync) {
				E = E - energyPerSync;
				syncReward = this.rewardPerSync;
				if (E < 0) {
					syncReward = 0;
					E = 0;
				}
			}

			double v = V(t + 1, E, tau + 1) + rewardPerEo * used + syncReward;
			reward += prob * v;
		}

		return reward;
	}

}
