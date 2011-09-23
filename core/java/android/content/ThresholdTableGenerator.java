import java.util.ArrayList;
import java.util.Arrays;

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

public class ThresholdTableGenerator {
	private IProfile profile;
	private int horizon;
	private int maxBattery;

	private float[][][] V;

	/* Constants */
	private float rewardPerRemainEnergy = 0;
	private int energyPerSync = 1;
	private float rewardPerOtherUse = 2;

	public ThresholdTableGenerator(IProfile profile) {
		this.profile = profile;
		horizon = profile.getHorizon();
		maxBattery = profile.getMaxBattery();

		V = new float[horizon][maxBattery][horizon];
		for (float[][] mat : V)
			for (float[] row : mat)
				Arrays.fill(row, -1);
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
				if (V[t + 1][E][tau + 1] == -1)
					V[t + 1][E][tau + 1] = V(t + 1, E, tau + 1);
				v = V[t + 1][E][tau + 1] + rewardPerOtherUse * used
						+ syncReward;
			} else {
				if (V[t + 1][E][0] == -1)
					V[t + 1][E][0] = V(t + 1, E, 0);
				v = V[t + 1][E][0] + rewardPerOtherUse * used + syncReward;
			}

			reward += prob * v;
		}

		return reward;
	}

	public static void main(String[] args) {
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
	}

}
