package pairing;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import data.HeadPos;
import data.Inertial;
import data.InertialAcc;
import data.InertialGyro;
import data.Skeleton;
import data.SkeletonAcc;
import data.SkeletonGyro;
import data.TurnList;
import preprocess.BodyExtraction;
import preprocess.Filter;
import preprocess.InertialInfo;
import preprocess.ProcessTool;
import preprocess.ReadData;
import preprocess.SkeletonInfo;
import preprocess.TurnMag;
import scoring.DTWSimilarity;
import scoring.FusionAlgo;

import eyeonyouserver.MainServerSocket;

public class PID {
	/***
	 * {ID class for 3AC Algo evaluation
	 * @author TingYuanKe on 2018/4/13
	 */
	
	// 1. DTW or directly check cross correlation.
	// 2. samplingRateOfSkn should be retrieved by file size.
	
	int framesOfSkeletonSegment = 20; //72
	int framesOfInnertialSegment = 100; //360
	public static int collectSeconds = 5;
	public static int sampleingRateOfSkn = 0;
	public static int sampleingRateOfItl = 0;
	public static int confidenceOfSimilarity = -1;
	public static double thresholdSD = 0.001;
	
	final static double thresholdSkeletonAcc = 2;
	final static double thresholdSkeletonGyro = 2;
	final static double thresholdInertialAcc = 5;
	final static double thresholdInertialGyro = 5;
//	final static double durationPerSeg = 0.2; // 0.2secs/segment
//	final static int frameSizePerSeg = (int)(sampleingRateOfSkn* durationPerSeg); // 18frames * 0.2secs = 3frames/segment
	
	public static void main(String[] args) throws IOException {
		startPairing();
	}
	
	public static void startPairing() throws IOException {
		
		int framesOfSkeleton = 10000; // 80 samples within 5 seconds
		int framesOfInnertial = 10000; // 500 samples wihtin 5 seconds
		String rootDir = "C:/Users/Public/Data";
		ArrayList<String> I_usersName = new ArrayList<String>();
		ArrayList<Integer> S_skeletonsID = new ArrayList<Integer>();
		
		// Read VSFile.cvs to separate different users in VSFile
		S_skeletonsID = BodyExtraction.bodyCount(rootDir + "/KINECTData/VSFile.csv");
		for (int i = 0; i < S_skeletonsID.size(); i++) {
			BodyExtraction.bodyWriter(rootDir + "/KINECTData/VSFile.csv", 
					rootDir + "/KINECTData/VSFile_" + i + ".csv", S_skeletonsID.get(i));
		}
		
		// Read all inertial.txt from each UEs and accumulate file's name into I_userName
		File[] myFileName = Filter.finder(rootDir + "/IMUData/");
		for(int i=0; i < myFileName.length; i++) {
			String tempWithExtension = myFileName[i].getName();
			String temp = tempWithExtension.substring(0, tempWithExtension.lastIndexOf('.'));
			
			// Ignore buffer file to prevent it from seeing as the duplicate I_usersName.
			if(!temp.contains("_buffer")) {
				I_usersName.add(temp);
			}
		}
		
		// Transform inertial data file from .txt to .csv
		for (int i = 0; i < I_usersName.size(); i++) {
			ProcessTool.reformat(rootDir + "/IMUData/" + I_usersName.get(i) + ".txt", rootDir + "/IMUData/" + I_usersName.get(i) + ".csv");
		}
		
		// Read skeleton data and inertial data of each person
		ArrayList<ArrayList<Skeleton>> skeletons_set = new ArrayList<ArrayList<Skeleton>>();
		ArrayList<ArrayList<SkeletonAcc>> skeletons_acc_set = new ArrayList<ArrayList<SkeletonAcc>>();
		ArrayList<ArrayList<SkeletonGyro>> skeletons_gyro_set = new ArrayList<ArrayList<SkeletonGyro>>();
		ArrayList<ArrayList<Inertial>> inertials_set = new ArrayList<ArrayList<Inertial>>();
		ArrayList<ArrayList<InertialAcc>> inertials_acc_set = new ArrayList<ArrayList<InertialAcc>>();
		ArrayList<ArrayList<InertialGyro>> inertials_gyro_set = new ArrayList<ArrayList<InertialGyro>>();
		for (int i = 0; i < S_skeletonsID.size(); i++) {
			ArrayList<Skeleton> jointsKinect = ReadData.readKinect(rootDir + "/KINECTData/VSFile_" + i + ".csv");
			skeletons_set.add(jointsKinect);
			
			int tempFramesOfSkeleton = ReadData.countFileLine(rootDir + "/KINECTData/VSFile_" + i + ".csv");
			if (tempFramesOfSkeleton > collectSeconds*30*(8.0/10) && framesOfSkeleton > tempFramesOfSkeleton) {
				framesOfSkeleton = tempFramesOfSkeleton;
			}
		}
		sampleingRateOfSkn = framesOfSkeleton / collectSeconds;
		System.out.println("framesOfSkeleton: " + framesOfSkeleton + ", sampleingRateOfSkn: " + sampleingRateOfSkn);
		// Calculate Skeleton Acceleration based on Read Skeleton Position
		for (int i = 0; i < S_skeletonsID.size(); i++) {
			ArrayList<SkeletonAcc> skeletons_acc= new ArrayList<SkeletonAcc>();;
			SkeletonInfo.setAccelerateion(skeletons_set.get(i), skeletons_acc, sampleingRateOfSkn, thresholdSkeletonAcc);
			skeletons_acc_set.add(skeletons_acc);
			
			ArrayList<SkeletonGyro> skeletons_gyro= new ArrayList<SkeletonGyro>();;
			SkeletonInfo.setGyroscope(skeletons_set.get(i), skeletons_gyro, sampleingRateOfSkn, thresholdSkeletonGyro);
			skeletons_gyro_set.add(skeletons_gyro);
		}
		
		
		for (int i = 0; i < I_usersName.size(); i++) {
			ArrayList<Inertial> jointsIMU = ReadData.readIMU(rootDir + "/IMUData/" + I_usersName.get(i) + ".csv", thresholdInertialAcc);
			inertials_set.add(jointsIMU);
			
			int tempFramesOfInnertial = ReadData.countFileLine(rootDir + "/IMUData/" + I_usersName.get(i) + ".csv");
			if (tempFramesOfInnertial > collectSeconds*100*(8.0/10) && framesOfInnertial > tempFramesOfInnertial) {
				framesOfInnertial = tempFramesOfInnertial;
			}
		}
		sampleingRateOfItl = framesOfInnertial / collectSeconds;
		System.out.println("framesOfInnertial: " + framesOfInnertial + ", sampleingRateOfItl: " + sampleingRateOfItl);
		for (int i = 0; i < I_usersName.size(); i++) {
			ArrayList<InertialAcc> inertial_acc= new ArrayList<InertialAcc>();
			InertialInfo.setAcceleration(inertials_set.get(i), inertial_acc, thresholdInertialAcc);
			inertials_acc_set.add(inertial_acc);

			ArrayList<InertialGyro> inertial_gyro= new ArrayList<InertialGyro>();
			InertialInfo.setGyroscope(inertials_set.get(i), inertial_gyro, thresholdInertialGyro);
			inertials_gyro_set.add(inertial_gyro);
		}
		
//		System.out.println("0-inertial");
//		for( int i = 0; i <inertials_set.get(0).size();i++) {
//			System.out.println(inertials_set.get(0).get(i).getAcc()[0]);
//		}
//		System.out.println("0-skeleton");
//		for( int i = 0; i <skeletons_acc_set.get(0).size();i++) {
//			System.out.println(skeletons_acc_set.get(0).get(i).getAccRight_wrist()[0]);
//		}
//		System.out.println("1-inertial");
//		for( int i = 0; i <inertials_set.get(1).size();i++) {
//			System.out.println(inertials_set.get(1).get(i).getAcc()[0]);
//		}
//		System.out.println("1-skeleton");
//		for( int i = 0; i <skeletons_acc_set.get(1).size();i++) {
//			System.out.println(skeletons_acc_set.get(1).get(i).getAccRight_wrist()[0]);
//		}
		
		//pair skeleton data with users' IDs every 5 seconds
//		for (int t = 0; t < skeletons.get(0).size()/framesOfSkeletonSegment; t++) {
		for (int t = 0; t < 1; t++) {
			ArrayList<Double> scores = new ArrayList<Double>();
			for (int i = 0; i < skeletons.size(); i++) {
				for (int j = 0; j < inertia_set.size(); j++) {
					ArrayList<Skeleton> sub_jointspos = new ArrayList<Skeleton>(skeletons.get(i).subList(t * framesOfSkeletonSegment, (t + 1) * framesOfSkeletonSegment));	// 20 samples in 1 seconds
					ArrayList<Inertia> sub_inertia = new ArrayList<Inertia>(inertia_set.get(j).subList(t * framesOfInnertialSegment, (t + 1) * framesOfInnertialSegment));	// 100 samples in 1 seconds
					
					TurnList kinectTurns = TurnMag.genKINECTTurnList(sub_jointspos);
					TurnList imuTurns = TurnMag.genIMUTurnList(sub_inertia);		
					scores.add(FusionAlgo.calResult_alg3(kinectTurns, imuTurns));
				}
			}
			int[][] result = IDPairing.pairing(scores, skeletonIDs.size(), users.size());
			
			// write pairing results of each frame every 1 seconds
			int[] match = new int[skeletonIDs.size()];
			for (int i = 0; i < skeletonIDs.size(); i++) {
				match[i] = -1;
			}
			for (int i = 0; i < skeletons.size(); i++) {
				for (int j = 0; j < inertia_set.size(); j++) {
//					System.out.printf("i=" + i + " j=" + j);
//					System.out.printf(" result[i][j]=%d || ", result[i][j]);
					if (result[i][j] == 1) {
						match[i] = j;
					}
				}
//				System.out.println("");
			}
			
			try {
				String[] IDCoordinate = new String[skeletonIDs.size() * 4];
//				CSVWriter cw = new CSVWriter(new FileWriter(rootDir + "/KINECTData/result.csv", true), ',', CSVWriter.NO_QUOTE_CHARACTER);
				CSVWriter cw = new CSVWriter(new FileWriter(rootDir + "/KINECTData/result.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
				for (int k = t * framesOfSkeletonSegment; k < (t+1) * framesOfSkeletonSegment; k++) {
					for (int i = 0; i < skeletonIDs.size(); i++) {
						if (match[i] >= 0) {
							IDCoordinate[i*4] = String.valueOf(usersID.get(i));
							IDCoordinate[i*4 + 1] = users.get(match[i]);
						} else {
							IDCoordinate[i*4] = String.valueOf(usersID.get(i));
							IDCoordinate[i*4 + 1] = "Unknown";
						}
						IDCoordinate[i*4 + 2] = String.valueOf(bodyHead.get(i).get(k).getX());
						IDCoordinate[i*4 + 3] = String.valueOf(bodyHead.get(i).get(k).getY());
					}
					cw.writeNext(IDCoordinate);
				}
				cw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		
	}
	
}