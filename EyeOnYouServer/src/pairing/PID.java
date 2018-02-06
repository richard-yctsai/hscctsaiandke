package pairing;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import data.HeadPos;
import data.Inertia;
import data.Skeleton;
import data.TurnList;
import preprocess.BodyExtraction;
import preprocess.Filter;
import preprocess.ProcessTool;
import preprocess.ReadData;
import preprocess.TurnMag;
import scoring.FusionAlgo;

import eyeonyouserver.MainServerSocket;

public class PID {
	/***
	 * @author PID class is transformed from WeiChun's EyeOnYou Demo code.
	 */
	public static void startPairing() {
		int framesOfSkeletonSegment = 3*16*8/10; // 13 samples in 1 seconds
		int framesOfInnertialSegment = 3*100*8/10; // 100 samples in 1 seconds
		String rootDir = "C:/Users/Public/Data";
		ArrayList<String> users = new ArrayList<String>();
		ArrayList<Integer> usersID = new ArrayList<Integer>();
		
		File[] myFileName = Filter.finder(rootDir + "/IMUData/");
		for(int i=0; i < myFileName.length; i++) {
			String tempWithExtension = myFileName[i].getName();
			String temp = tempWithExtension.substring(0, tempWithExtension.lastIndexOf('.'));
			if(!temp.contains("_buffer")) {
				users.add(temp);
				System.out.println(temp);
			}
		}
		
		// separate different users in VSFile
		ArrayList<Integer> skeletonIDs;
		skeletonIDs = BodyExtraction.bodyCount(rootDir + "/KINECTData/VSFile.csv");
		
		for (int i = 0; i < skeletonIDs.size(); i++) {
			BodyExtraction.bodyWriter(rootDir + "/KINECTData/VSFile.csv", 
					rootDir + "/KINECTData/VSFile_" + i + ".csv", skeletonIDs.get(i));
		}
		
		// transform inertial data file from .txt to .csv
		for (int i = 0; i < users.size(); i++) {
			ProcessTool.reformat(rootDir + "/IMUData/" + users.get(i) + ".txt", rootDir + "/IMUData/" + users.get(i) + ".csv");
		}
		
		// read skeleton data and inertial data of each person
		ArrayList<ArrayList<Skeleton>> skeletons = new ArrayList<ArrayList<Skeleton>>();
		ArrayList<ArrayList<Inertia>> inertia_set = new ArrayList<ArrayList<Inertia>>();
		ArrayList<ArrayList<HeadPos>> bodyHead = new ArrayList<ArrayList<HeadPos>>();
		
		for (int i = 0; i < skeletonIDs.size(); i++) {
			ArrayList<Skeleton> jointspos = ReadData.readKinect_smooth(rootDir + "/KINECTData/VSFile_" + i + ".csv");
			ArrayList<HeadPos> HeadXY = ReadData.readHead(rootDir + "/KINECTData/VSFile_" + i + ".csv");
			skeletons.add(jointspos);
			bodyHead.add(HeadXY);
			usersID.add(ReadData.readID(rootDir + "/KINECTData/VSFile_" + i + ".csv"));
		}
		
		for (int i = 0; i < users.size(); i++) {
			// synchronize starter
//			int offset = ProcessTool.getStarter(rootDir + "/KINECTData/VSFile.csv", rootDir + "/IMUData/" + users.get(i) + ".csv");
			ArrayList<Inertia> inertia = ReadData.readIMU(rootDir + "/IMUData/" + users.get(i) + ".csv");
			inertia_set.add(inertia);
			
//			if ((offset+1) == inertia.size()) {
//				File idleTxtFile = new File(rootDir + "/IMUData/", users.get(i) + ".txt");
//				File idleCsvFile = new File(rootDir + "/IMUData/", users.get(i) + ".csv");
//				idleTxtFile.delete();
//				idleCsvFile.delete();
//				users.remove(i);
////				usersID.remove(i);
//			}
		}
		
		//pair skeleton data with users' IDs every 5 seconds
//		for (int t = 0; t < skeletons.get(0).size()/framesOfSkeletonSegment; t++) {
		for (int t = 0; t < 1; t++) {
			ArrayList<Double> scores = new ArrayList<Double>();
			for (int i = 0; i < skeletons.size(); i++) {
				for (int j = 0; j < inertia_set.size(); j++) {
					ArrayList<Skeleton> sub_jointspos = new ArrayList<Skeleton>(skeletons.get(i).subList(t * framesOfSkeletonSegment, (t + 1) * framesOfSkeletonSegment));	// 13 samples in 1 seconds
					ArrayList<Inertia> sub_inertia = new ArrayList<Inertia>(inertia_set.get(j).subList(t * framesOfInnertialSegment, (t + 1) * framesOfInnertialSegment));	// 100 samples in 1 seconds
					
					TurnList kinectTurns = TurnMag.genKINECTTurnList(sub_jointspos);
					TurnList imuTurns = TurnMag.genIMUTurnList(sub_inertia);
					scores.add(FusionAlgo.calResult_alg3(kinectTurns, imuTurns));
					System.out.println("Pairing scores!!!!!!!!!!!!!!!!: " + FusionAlgo.calResult_alg3(kinectTurns, imuTurns));
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
				String[] IDCoordinate = new String[skeletonIDs.size() * 2];
//				CSVWriter cw = new CSVWriter(new FileWriter(rootDir + "/KINECTData/result.csv", true), ',', CSVWriter.NO_QUOTE_CHARACTER);
				CSVWriter cw = new CSVWriter(new FileWriter(rootDir + "/KINECTData/result.csv"), ',', CSVWriter.NO_QUOTE_CHARACTER);
//				for (int k = t * framesOfSkeletonSegment; k < (t+1) * framesOfSkeletonSegment; k++) {
					for (int i = 0; i < skeletonIDs.size(); i++) {
						if (match[i] >= 0) {
							IDCoordinate[i*2] = String.valueOf(usersID.get(i));
							IDCoordinate[i*2 + 1] = users.get(match[i]);
						} else {
							IDCoordinate[i*2] = String.valueOf(usersID.get(i));
							IDCoordinate[i*2 + 1] = "Unknown";
						}
//						IDCoordinate[i*4 + 2] = String.valueOf(bodyHead.get(i).get(k).getX());
//						IDCoordinate[i*4 + 3] = String.valueOf(bodyHead.get(i).get(k).getY());
					}
					cw.writeNext(IDCoordinate);
//				}
				cw.close();
				// Complete yielding the pairing result.csv and request Kinect to perform tagging profile name.
				MainServerSocket.clientRunPID.requestKinectTagProfile();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		
	}
	
}