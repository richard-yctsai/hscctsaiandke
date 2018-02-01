package preprocess;

import java.math.BigDecimal;
import java.util.ArrayList;

import data.Inertia;
import data.Skeleton;
import data.TurnList;
import data.MTurn;
import data.STurn;

/**
* This class is used to manage turns.
*
* @author  WeiChun
*/
public class  TurnMag {
	
	/**
	* generate a list of "Move" and "Stop" turns of skeleton data
	*   
	* @param: a list of Skeleton objects
	* 
	* @return: a TurnList object
	*/
	public static TurnList genKINECTTurnList(ArrayList<Skeleton> jointspos) {
		double dis[][] = SkeletonInfo.getDistanceList(jointspos);
		double[] sum_dis = new double[3];
		double mag_dis;
		
		int idx = 0;
		ArrayList<Integer> move_seg = new ArrayList<Integer>();
		ArrayList<MTurn> move_turns = new ArrayList<MTurn>();
		ArrayList<STurn> still_turns = new ArrayList<STurn>();
		
		// move detection and generate move turns
		for (int i = 0; i < dis[0].length; i+=5) {
			for (int axis = 0; axis < 3; axis++) {
				double sum = 0;
				for (int j = 0; j < 5; j++) {
					sum += dis[axis][i+j];
				}
				sum_dis[axis] = Math.abs(sum) * 100;
			}
			mag_dis = Math.pow(Math.pow(sum_dis[0], 2) + Math.pow(sum_dis[1], 2) + Math.pow(sum_dis[2], 2), 0.5);
				
			if (mag_dis >= 5) {
				move_seg.add(idx);
			} else {
				if (move_seg.size() > 1) {
					double st = new BigDecimal(move_seg.get(0) * 0.2).setScale(1, BigDecimal.ROUND_HALF_DOWN).doubleValue();
					double et = new BigDecimal(move_seg.get(move_seg.size()-1) * 0.2 + 0.2).setScale(1, BigDecimal.ROUND_HALF_DOWN).doubleValue();
					MTurn moveturn = new MTurn(st, et);
					move_turns.add(moveturn);
				}
				move_seg.clear();
			}
			
			idx++;
		}
		
		if (move_seg.size() > 1) {
			double st = new BigDecimal(move_seg.get(0) * 0.2).setScale(1, BigDecimal.ROUND_HALF_DOWN).doubleValue();
			double et = new BigDecimal(move_seg.get(move_seg.size()-1) * 0.2 + 0.2).setScale(1, BigDecimal.ROUND_HALF_DOWN).doubleValue();
			MTurn moveturn = new MTurn(st, et);
			move_turns.add(moveturn);
		}
		
		move_turns = mergeMoveTurn(move_turns, 0.2);
		
		double end = new BigDecimal(jointspos.size() / 30).setScale(1, BigDecimal.ROUND_HALF_DOWN).doubleValue();
		if (move_turns == null) {
			STurn stillturn = new STurn(0, end);
			still_turns.add(stillturn);
		} else {
			// generate still turns
			if (move_turns.get(0).getStartTime() != 0) {
				STurn stillturn = new STurn(0, move_turns.get(0).getStartTime());
				still_turns.add(stillturn);
			}
			for (int i = 0; i < move_turns.size() - 1; i++) {
				STurn stillturn = new STurn(move_turns.get(i).getEndTime(),
						move_turns.get(i + 1).getStartTime());
				still_turns.add(stillturn);
			}
			if (move_turns.get(move_turns.size() - 1).getEndTime() != end) {
				STurn stillturn = new STurn(move_turns.get(move_turns.size() - 1).getEndTime(), end);
				still_turns.add(stillturn);
			}
			
			// add acceleration values to move turns
			addAcceleration_kinect(move_turns, jointspos);
		}
		
		return new TurnList(still_turns, move_turns);
		
	}
	
	/**
	* generate a list of "Move" and "Stop" turns of inertial data
	*   
	* @param: a list of Inertia objects
	* 
	* @return: a TurnList object
	*/
	public static TurnList genIMUTurnList(ArrayList<Inertia> imu_3) {
		int idx = 0;
		ArrayList<STurn> still_turns = new ArrayList<STurn>();
		ArrayList<Integer> move_seg = new ArrayList<Integer>();
		ArrayList<MTurn> move_turns = new ArrayList<MTurn>();
		
		double sum_accmags, mean_accmags;
		
		for (int i = 0; i < imu_3.size(); i+=10) {
			sum_accmags = 0;
			if (i + 10 >= imu_3.size()) {
				break;
			}
			
			for (int j = 0; j < 10; j++) {
				sum_accmags += Math.pow(Math.pow(imu_3.get(i+j).getAcc()[0], 2) + Math.pow(imu_3.get(i+j).getAcc()[1], 2) + Math.pow(imu_3.get(i+j).getAcc()[2], 2), 0.5);   
			}
			mean_accmags = sum_accmags / 10;
			
			if (mean_accmags >= 1.5) {
				move_seg.add(idx);
			} else {
				if (move_seg.size() > 2) {
					double st = new BigDecimal(move_seg.get(0) * 0.1).setScale(1, BigDecimal.ROUND_HALF_DOWN).doubleValue();
					double et = new BigDecimal(move_seg.get(move_seg.size()-1) * 0.1 + 0.1).setScale(1, BigDecimal.ROUND_HALF_DOWN).doubleValue();
					MTurn moveturn = new MTurn(st, et);
					move_turns.add(moveturn);
				}
				move_seg.clear();
			}
			
			idx++;
		}
		
		if (move_seg.size() > 2) {
			double st = new BigDecimal(move_seg.get(0) * 0.1).setScale(1, BigDecimal.ROUND_HALF_DOWN).doubleValue();
			double et = new BigDecimal(move_seg.get(move_seg.size()-1) * 0.1 + 0.1).setScale(1, BigDecimal.ROUND_HALF_DOWN).doubleValue();
			MTurn moveturn = new MTurn(st, et);
			move_turns.add(moveturn);
		}
		
		move_turns = mergeMoveTurn(move_turns, 0.2);
		
		// generate still turns
		double end = new BigDecimal(imu_3.size() / 100).setScale(1, BigDecimal.ROUND_HALF_DOWN).doubleValue();
		if (move_turns == null) {
			STurn stillturn = new STurn(0, end);
			still_turns.add(stillturn);
		} else {
			if (move_turns.get(0).getStartTime() != 0) {
				STurn stillturn = new STurn(0, move_turns.get(0).getStartTime());
				still_turns.add(stillturn);
			}
			for (int i = 0; i < move_turns.size() - 1; i++) {
				STurn stillturn = new STurn(move_turns.get(i).getEndTime(),
						move_turns.get(i + 1).getStartTime());
				still_turns.add(stillturn);
			}

			if (move_turns.get(move_turns.size() - 1).getEndTime() != end) {
				STurn stillturn = new STurn(move_turns.get(move_turns.size() - 1).getEndTime(), end);
				still_turns.add(stillturn);
			}

			addAcceleration_imu(move_turns, imu_3);
		}
		
		return new TurnList(still_turns, move_turns);
	
	}
	
	/**
	* merge "Move" turns with little gap into one turn
	*   
	* @param: a list of MTurn objects
	* @param: gap
	* 
	* @return: a list of MTurn objects
	*/
	private static ArrayList<MTurn> mergeMoveTurn(ArrayList<MTurn> Turns, double gap) {
		
		if(Turns.size() > 0) {
			ArrayList<MTurn> newTurns = new ArrayList<MTurn>();
			
			double startTime = 0, endTime = 0;
			for(int i = 0, flag = 0; i < Turns.size(); i++) {
				if (flag == 0) {
					startTime = Turns.get(i).getStartTime();
					endTime = Turns.get(i).getEndTime();
					flag = 1;
				} else {
					double tmpGap = new BigDecimal(Turns.get(i).getStartTime() - Turns.get(i-1).getEndTime()).setScale(1, BigDecimal.ROUND_HALF_DOWN).doubleValue();
				
					if(tmpGap <= gap) {
						endTime = Turns.get(i).getEndTime();
					} else {
						newTurns.add(new MTurn(startTime, endTime));
						flag = 0;
						i--;
					}
				}
			}
			newTurns.add(new MTurn(startTime, endTime));
			
			return newTurns;
		} else {
			return null;
		}
		
	}
	
	/**
	* add acceleration values to each "Move" turn which processed from skeleton data
	*   
	* @param: a list of MTurn objects
	* @param: a list of Skeleton objects
	*/
	private static void addAcceleration_kinect(ArrayList<MTurn> Turns, ArrayList<Skeleton> jointspos) {
		ArrayList<Skeleton> kmove;
		double[][] acc;
		
		for (int i = 0; i < Turns.size(); i++) {
			int head = new BigDecimal(Turns.get(i).getStartTime() / 0.2).setScale(1, BigDecimal.ROUND_HALF_DOWN).intValue();
			int tail = new BigDecimal(Turns.get(i).getEndTime() / 0.2).setScale(1, BigDecimal.ROUND_HALF_DOWN).intValue();
			
			kmove = new ArrayList<Skeleton>(jointspos.subList(head * 6, tail * 6));
			acc = SkeletonInfo.getAccList(kmove);
			for (int j = 0; j < acc[0].length; j++) {
				Turns.get(i).addAcc(Math.pow(Math.pow(acc[0][j], 2) + Math.pow(acc[1][j], 2) + Math.pow(acc[2][j], 2), 0.5));
				Turns.get(i).addAcc_x(acc[0][j]);
				Turns.get(i).addAcc_y(acc[1][j]);
				Turns.get(i).addAcc_z(acc[2][j]);
			}
		}
		
	}
	
	/**
	* add acceleration values to each "Move" turn which processed from inertial data
	*   
	* @param: a list of MTurn objects
	* @param: a list of inertia objects
	*/
	private static void addAcceleration_imu(ArrayList<MTurn> Turns, ArrayList<Inertia> imu_6) {
		ArrayList<Inertia> imu_move;
		
		for (int i = 0; i < Turns.size(); i++) {
			int head = new BigDecimal(Turns.get(i).getStartTime() / 0.1).setScale(1, BigDecimal.ROUND_HALF_DOWN).intValue();
			int tail = new BigDecimal(Turns.get(i).getEndTime() / 0.1).setScale(1, BigDecimal.ROUND_HALF_DOWN).intValue();
			
			imu_move = new ArrayList<Inertia>(imu_6.subList(head * 10, tail * 10));
			calAcceleration(Turns.get(i), imu_move);
		}
		
	}
	
	/**
	* downsample acceleration values in each "Move" turn which processed from inertial data
	*   
	* @param: a MTurn object
	* @param: a list of inertia objects
	*/
	private static void calAcceleration(MTurn MoveTurn, ArrayList<Inertia> imu_move) {
		double tmpAcc = 0, tmpAccX = 0, tmpAccY = 0, tmpAccZ = 0;
		int cnt = 0;
		
		for (Inertia imu:imu_move) {
			tmpAcc += Math.pow(Math.pow(imu.getAcc()[0], 2) + Math.pow(imu.getAcc()[1], 2) + Math.pow(imu.getAcc()[2], 2), 0.5);
			tmpAccX += imu.getAcc()[0];
			tmpAccY += imu.getAcc()[1];
			tmpAccZ += imu.getAcc()[2];
			cnt++;
			if (cnt == 5) {
				MoveTurn.addAcc(tmpAcc/5);
				MoveTurn.addAcc_x(tmpAccX/5);
				MoveTurn.addAcc_y(tmpAccY/5);
				MoveTurn.addAcc_z(tmpAccZ/5);
				tmpAcc = 0;
				tmpAccX = 0;
				tmpAccY = 0;
				tmpAccZ = 0;
				cnt = 0;
			}
		}
		if (cnt != 0) {
			MoveTurn.addAcc(tmpAcc/cnt);
			MoveTurn.addAcc_x(tmpAccX/cnt);
			MoveTurn.addAcc_y(tmpAccY/cnt);
			MoveTurn.addAcc_z(tmpAccZ/cnt);
		}
	}

}
