// Created by Heresy @ 2015 / 02 / 25
// Blog Page: https://kheresy.wordpress.com/2015/04/10/k4w-v2-part-7a-draw-user-skeleton/
// This sample is used to read information of body joint nad draw with OpenCV.
//
// Modified by WeiChun @ 2017 / 07 / 03
// Modified by Richard Yi-Chia Tsai @ 2017 / 11 / 17 and 2017 / 12 / 31 and 2018 / 01 / 03
// EyeOnYou Robot

// Standard Library
#include "stdafx.h"
#include "ServerSocket.h"
#include "ServerSocketRunPID.h"

#pragma comment(lib, "ws2_32.lib") // link winsock2
#include <thread>

#include <iostream>
#include <windows.h>
#include <fstream>
#include <string>
#include <string.h>
#include <sstream>
#include <cstring>
#include <cstdlib>

// OpenCV Header
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/highgui.hpp>

// Kinect for Windows SDK Header
#include <Kinect.h>

#include "MainActivity.h"

using namespace std;

// temporary coordinate
float tmpWR_JointPos[3];

const int Max_Char = 500;
char cmdLinePID[Max_Char] = "java -cp \"C:/Users/Richard Yi-Chia TSAI/Desktop/eclipse/JAR/*;C:/Users/Richard Yi-Chia TSAI/Desktop/EyeOnYouRobot_Backup01142018/EyeOnYouPID/bin\" pairing.Demo";
//char cmdLineRobot[Max_Char] = "java -cp \"C:/Users/Richard Yi-Chia TSAI/Desktop/eclipse/JAR/*;C:/Users/Richard Yi-Chia TSAI/Desktop/EyeOnYouiRobot/bin\" roombacomm.eyeonyourobot.EyeOnYouRobotTracking COM3 OI ";
//char cmdLineDriveRobot[Max_Char] = "";

void DrawLine(cv::Mat& rImg, const Joint& rJ1, const Joint& rJ2, ICoordinateMapper* pCMapper);
void DrawJoint(cv::Mat& rImg, const Joint& rJ1, ICoordinateMapper* pCMapper);
void robotTracking(string ID, cv::Mat& rImg, const Joint& rJ1, ICoordinateMapper* pCMapper);
void DrawIdentity(string ID, string NAME, cv::Mat& rImg, const Joint& rJ1, ICoordinateMapper* pCMapper);
void writeInfo(int, ofstream&, Joint*, ICoordinateMapper*, char*);

DWORD WINAPI DriveRobotThreadFunc(void* data) {
	cout << "Native ServerSocketDriveRobot server starting" << endl;

	// Start server socket listener
	ServerSocket* server = new ServerSocket();
	server->startThread();

	// Wait for server socket to terminate
	WaitForSingleObject(server->getThread(), INFINITE);
	return 0;
}

DWORD WINAPI RunPIDThreadFunc(void* data) {
	cout << "Native ServerSocketRunPID server starting" << endl;

	// Start server socket listener
	ServerSocketRunPID* server = new ServerSocketRunPID();
	server->startThread();

	// Wait for server socket to terminate
	WaitForSingleObject(server->getThread(), INFINITE);
	return 0;
}

int _tmain(int argc, _TCHAR* argv[])
{
	//EyeOnYouRobot = new RobotDrive("", 0);
	//vector<string> myColumn = {};
	//VotingPID(myColumn, "", "");

	// 0. Initialize server socket to conduct interprocess communication with java-based robot
	HANDLE threadRobot = CreateThread(NULL, 0, DriveRobotThreadFunc, NULL, 0, NULL);
	HANDLE threadServer = CreateThread(NULL, 0, RunPIDThreadFunc, NULL, 0, NULL);
	int WaitForSocket = 3;
	while (WaitForSocket > 0) {
		cout << WaitForSocket << endl;
		WaitForSocket--;
		Sleep(1000);
	}

	// 1a. Get default Sensor
	cout << "Try to get default sensor" << endl;
	IKinectSensor* pSensor = nullptr;
	if (GetDefaultKinectSensor(&pSensor) != S_OK)
	{
		cerr << "Get Sensor failed" << endl;
		return -1;
	}

	// 1b. Open sensor
	cout << "Try to open sensor" << endl;
	if (pSensor->Open() != S_OK)
	{
		cerr << "Can't open sensor" << endl;
		return -1;
	}

	// 2. Color Related code
	IColorFrameReader* pColorFrameReader = nullptr;
	cv::Mat	mColorImg;
	UINT uBufferSize = 0;
	{
		// 2a. Get color frame source
		cout << "Try to get color source" << endl;
		IColorFrameSource* pFrameSource = nullptr;
		if (pSensor->get_ColorFrameSource(&pFrameSource) != S_OK)
		{
			cerr << "Can't get color frame source" << endl;
			return -1;
		}

		// 2b. Get frame description
		cout << "get color frame description" << endl;
		int		iWidth = 0;
		int		iHeight = 0;
		IFrameDescription* pFrameDescription = nullptr;
		if (pFrameSource->get_FrameDescription(&pFrameDescription) == S_OK)
		{
			pFrameDescription->get_Width(&iWidth);
			pFrameDescription->get_Height(&iHeight);
		}
		pFrameDescription->Release();
		pFrameDescription = nullptr;

		// 2c. get frame reader
		cout << "Try to get color frame reader" << endl;
		if (pFrameSource->OpenReader(&pColorFrameReader) != S_OK)
		{
			cerr << "Can't get color frame reader" << endl;
			return -1;
		}

		// 2d. release Frame source
		cout << "Release frame source" << endl;
		pFrameSource->Release();
		pFrameSource = nullptr;

		// Prepare OpenCV data
		mColorImg = cv::Mat(iHeight, iWidth, CV_8UC4);
		uBufferSize = iHeight * iWidth * 4 * sizeof(BYTE);
	}

	// 3. Body related code
	IBodyFrameReader* pBodyFrameReader = nullptr;
	IBody** aBodyData = nullptr;
	INT32 iBodyCount = 0;
	INT32 tempiBodyCount = 0, realiBodyCount = 0, lastiBodyCount = 0;
	{
		// 3a. Get frame source
		cout << "Try to get body source" << endl;
		IBodyFrameSource* pFrameSource = nullptr;
		if (pSensor->get_BodyFrameSource(&pFrameSource) != S_OK)
		{
			cerr << "Can't get body frame source" << endl;
			return -1;
		}

		// 3b. Get the number of body
		if (pFrameSource->get_BodyCount(&iBodyCount) != S_OK)
		{
			cerr << "Can't get body count" << endl;
			return -1;
		}
		cout << " > Can trace " << iBodyCount << " bodies" << endl;
		aBodyData = new IBody*[iBodyCount];
		for (int i = 0; i < iBodyCount; ++i)
			aBodyData[i] = nullptr;

		// 3c. get frame reader
		cout << "Try to get body frame reader" << endl;
		if (pFrameSource->OpenReader(&pBodyFrameReader) != S_OK)
		{
			cerr << "Can't get body frame reader" << endl;
			return -1;
		}

		// 3d. release Frame source
		cout << "Release frame source" << endl;
		pFrameSource->Release();
		pFrameSource = nullptr;
	}

	// 4. get CoordinateMapper
	ICoordinateMapper* pCoordinateMapper = nullptr;
	if (pSensor->get_CoordinateMapper(&pCoordinateMapper) != S_OK)
	{
		cout << "Can't get coordinate mapper" << endl;
		return -1;
	}

	// file output stream
	ofstream csvfile;
	string csvfilename = "C:/Users/Public/Data/KINECTData/VSFile.csv";
	csvfile.open(csvfilename);

	// read head coordinate and id
	ifstream resultfile;
	string resultfilename = "C:/Users/Public/Data/KINECTData/result.csv";
	string line;

	//// vector of frames
	//vector<cv::Mat> frames;
	//frames.reserve(150);

	// prepare timestamp
	char time_str[13];
	SYSTEMTIME st;
	char start_time_str[13];
	GetLocalTime(&st);
	memset(start_time_str, '\0', 13);
	sprintf_s(start_time_str, 13, "%02d:%02d:%02d:%03d", st.wHour, st.wMinute, st.wSecond, st.wMilliseconds);

	// count down to start
	int WaitForKinect = 3;
	while (WaitForKinect > 0) {
		cout << WaitForKinect << endl;
		WaitForKinect--;
		Sleep(1000);
	}

	// Enter main loop, step means frame of Kinect. Also, every 20 steps (frames) represent 1 sec in reality.
	int step = 0;
	cv::namedWindow("Video and Skeleton Recorder");
	while (step < 14400)
	{
		cv::Mat mImg;

		// Get latest BodyFrame
		IBodyFrame* pBodyFrame = nullptr;
		if (pBodyFrameReader->AcquireLatestFrame(&pBodyFrame) == S_OK)
		{
			step += 1;
			// Get last ColorFrame
			IColorFrame* pColorFrame = nullptr;
			if (pColorFrameReader->AcquireLatestFrame(&pColorFrame) == S_OK)
			{
				// Copy to OpenCV image
				if (pColorFrame->CopyConvertedFrameDataToArray(uBufferSize, mColorImg.data, ColorImageFormat_Bgra) != S_OK)
				{
					cerr << "Data copy error" << endl;
				}

				// release frame
				pColorFrame->Release();
			}
			mImg = mColorImg.clone();

			// Get Body data for each frame
			if (pBodyFrame->GetAndRefreshBodyData(iBodyCount, aBodyData) == S_OK)
			{

				GetLocalTime(&st);
				memset(time_str, '\0', 13);
				sprintf_s(time_str, 13, "%02d:%02d:%02d:%03d", st.wHour, st.wMinute, st.wSecond, st.wMilliseconds);
				putText(mImg, start_time_str, cv::Point(0, 30), 0, 1, cv::Scalar(0, 255, 0), 3);
				putText(mImg, time_str, cv::Point(0, 60), 0, 1, cv::Scalar(0, 0, 255), 3);
				putText(mImg, to_string(step), cv::Point(0, 90), 0, 1, cv::Scalar(255, 0, 0), 3);



				// For each body
				for (int i = 0; i < iBodyCount; ++i)
				{
					IBody* pBody = aBodyData[i];

					// check if is tracked
					BOOLEAN bTracked = false;
					if ((pBody->get_IsTracked(&bTracked) == S_OK) && bTracked)
					{
						// Accumulate body counts
						tempiBodyCount++;

						// get joint position
						Joint aJoints[JointType::JointType_Count];
						if (pBody->GetJoints(JointType::JointType_Count, aJoints) == S_OK)
						{
							DrawLine(mImg, aJoints[JointType_SpineBase], aJoints[JointType_SpineMid], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_SpineMid], aJoints[JointType_SpineShoulder], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_SpineShoulder], aJoints[JointType_Neck], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_Neck], aJoints[JointType_Head], pCoordinateMapper);

							DrawLine(mImg, aJoints[JointType_SpineShoulder], aJoints[JointType_ShoulderLeft], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_ShoulderLeft], aJoints[JointType_ElbowLeft], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_ElbowLeft], aJoints[JointType_WristLeft], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_WristLeft], aJoints[JointType_HandLeft], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_HandLeft], aJoints[JointType_HandTipLeft], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_HandLeft], aJoints[JointType_ThumbLeft], pCoordinateMapper);

							DrawLine(mImg, aJoints[JointType_SpineShoulder], aJoints[JointType_ShoulderRight], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_ShoulderRight], aJoints[JointType_ElbowRight], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_ElbowRight], aJoints[JointType_WristRight], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_WristRight], aJoints[JointType_HandRight], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_HandRight], aJoints[JointType_HandTipRight], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_HandRight], aJoints[JointType_ThumbRight], pCoordinateMapper);

							DrawLine(mImg, aJoints[JointType_SpineBase], aJoints[JointType_HipLeft], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_HipLeft], aJoints[JointType_KneeLeft], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_KneeLeft], aJoints[JointType_AnkleLeft], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_AnkleLeft], aJoints[JointType_FootLeft], pCoordinateMapper);

							DrawLine(mImg, aJoints[JointType_SpineBase], aJoints[JointType_HipRight], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_HipRight], aJoints[JointType_KneeRight], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_KneeRight], aJoints[JointType_AnkleRight], pCoordinateMapper);
							DrawLine(mImg, aJoints[JointType_AnkleRight], aJoints[JointType_FootRight], pCoordinateMapper);

							for (int j = 0; j < JointType::JointType_Count; ++j) {
								DrawJoint(mImg, aJoints[j], pCoordinateMapper);
							}

							writeInfo(i, csvfile, aJoints, pCoordinateMapper, time_str);

							robotTracking(to_string(i), mImg, aJoints[JointType::JointType_Head], pCoordinateMapper);

							DrawIdentity(to_string(i), VotingPID::getnameVotingWithIndex(i), mImg, aJoints[JointType::JointType_Head], pCoordinateMapper);
						}
					}

					if (i == iBodyCount - 1)
					{
					realiBodyCount = tempiBodyCount;
					tempiBodyCount = 0;
					}
				}

				if (step % 20 == 0 && realiBodyCount == 0)
				{
					RobotDrive::setDrivetowhere("stop");
					RobotDrive::setDriveunit(0);
				}

				/*if ((step % 20 == 0) && realiBodyCount != lastiBodyCount)
				{
				cout << "People counts CHANGE: right now ->" << realiBodyCount << " last time -> " << lastiBodyCount << endl;
				csvfile.close();
				systemCallCmd(cmdLinePID);
				csvfile.open(csvfilename);
				}*/

				//if (step != 0 && (step % 30 == 0) && PIDRun::getExecutePID() == true)
				if (PIDRun::getExecutePID() == true)
				{
					lastiBodyCount = realiBodyCount;
					cout << "People counts: " << realiBodyCount << endl;

					csvfile.close();
					PIDRun::systemCallCmd(cmdLinePID);
					PIDRun::setExecutePID(false);
					csvfile.open(csvfilename);

					resultfile.open(resultfilename);
					if (resultfile.is_open()) {
						// read ID and position of head joint and then putText
						getline(resultfile, line, '\n');
						resultfile.close();
					}

					istringstream templine(line);
					string data, nameReadFile;
					double x, y;
					int idx = 0;
					while (getline(templine, data, ',')) {
						if (idx % 4 == 0)
							VotingPID::setID(data.c_str());
						else if (idx % 4 == 1)
							nameReadFile = data.c_str();
						else if (idx % 4 == 2)
							x = atof(data.c_str());
						else
						{
							y = atof(data.c_str()); 
							VotingPID::setnameVotingWithIndex(VotingPID::getID(), VotingPID::votingOfPID(VotingPID::getID(), nameReadFile));
						}
						idx += 1;
					}
				}
			}
			else
			{
				cerr << "Can't read body data" << endl;
			}

			// release frame
			pBodyFrame->Release();
		}
		else
		{
			continue;
		}

		// show image
		cv::Mat frame;
		cv::resize(mImg, frame, cv::Size(mImg.cols / mImg.cols * 1366, mImg.rows / mImg.rows * 768), CV_INTER_LINEAR);
		cv::imshow("Video and Skeleton Recorder", frame);
		//frames.push_back(frame);

		// 4c. check keyboard input
		if (cv::waitKey(1) == VK_ESCAPE) {
			break;
		}
	}

	csvfile.close();
	resultfile.close();

	// 3. delete body data array
	delete[] aBodyData;

	// 3. release frame reader
	cout << "Release body frame reader" << endl;
	pBodyFrameReader->Release();
	pBodyFrameReader = nullptr;

	// 2. release color frame reader
	cout << "Release color frame reader" << endl;
	pColorFrameReader->Release();
	pColorFrameReader = nullptr;

	// 1c. Close Sensor
	cout << "close sensor" << endl;
	pSensor->Close();

	// 1d. Release Sensor
	cout << "Release sensor" << endl;
	pSensor->Release();
	pSensor = nullptr;

	////// save frames as video
	//cv::VideoWriter writer;
	//writer.open("C:/Users/Public/Data/KINECTData/Video_Demo.avi", CV_FOURCC('M', 'J', 'P', 'G'), 30, cv::Size(1366, 768), true);

	//for (int i = 0; i < frames.size(); i++) {
	//	cv::cvtColor(frames[i], frames[i], cv::COLOR_BGRA2BGR);
	//	writer.write(frames[i]);
	//}

	//writer.release();

	return 0;
}

void DrawLine(cv::Mat& rImg, const Joint& rJ1, const Joint& rJ2, ICoordinateMapper* pCMapper)
{
	if (rJ1.TrackingState == TrackingState_NotTracked || rJ2.TrackingState == TrackingState_NotTracked)
		return;

	ColorSpacePoint ptJ1, ptJ2;
	pCMapper->MapCameraPointToColorSpace(rJ1.Position, &ptJ1);
	pCMapper->MapCameraPointToColorSpace(rJ2.Position, &ptJ2);

	if ((ptJ1.X >= 0 && ptJ1.X <= rImg.cols) && (ptJ2.X >= 0 && ptJ2.X <= rImg.cols) && (ptJ1.Y >= 0 && ptJ1.Y <= rImg.rows) && (ptJ2.Y >= 0 && ptJ2.Y <= rImg.rows)) {
		cv::line(rImg, cv::Point(ptJ1.X, ptJ1.Y), cv::Point(ptJ2.X, ptJ2.Y), cv::Vec3b(0, 255, 0), 5);
	}
}

void DrawJoint(cv::Mat& rImg, const Joint& rJ1, ICoordinateMapper* pCMapper)
{
	if (rJ1.TrackingState == TrackingState_NotTracked)
		return;

	ColorSpacePoint ptJ1;
	pCMapper->MapCameraPointToColorSpace(rJ1.Position, &ptJ1);

	if ((ptJ1.X >= 0 && ptJ1.X <= rImg.cols) && (ptJ1.Y >= 0 && ptJ1.Y <= rImg.rows)) {
		cv::circle(rImg, cv::Point(ptJ1.X, ptJ1.Y), 6, cv::Vec3b(0, 0, 255), -1);
	}
}

void robotTracking(string ID, cv::Mat& rImg, const Joint& rJ1, ICoordinateMapper* pCMapper)
{
	if (rJ1.TrackingState == TrackingState_NotTracked)
		return;

	ColorSpacePoint ptJ1;
	pCMapper->MapCameraPointToColorSpace(rJ1.Position, &ptJ1);

	if ((ptJ1.X >= 0 && ptJ1.X <= rImg.cols) && (ptJ1.Y >= 0 && ptJ1.Y <= rImg.rows)) {
		putText(rImg, ID, cv::Point(ptJ1.X, ptJ1.Y - 50), 0, 3, cv::Scalar(255, 0, 0), 6);	// draw IDs of each user
		putText(rImg, to_string(rJ1.Position.X), cv::Point(ptJ1.X, ptJ1.Y - 300), 0, 3, cv::Scalar(255, 255, 0), 6);	// draw X axis of each user
		putText(rImg, to_string(rJ1.Position.Y), cv::Point(ptJ1.X, ptJ1.Y - 200), 0, 3, cv::Scalar(255, 255, 0), 6);	// draw Y axis of each user
		putText(rImg, to_string(rJ1.Position.Z), cv::Point(ptJ1.X, ptJ1.Y - 100), 0, 3, cv::Scalar(255, 255, 0), 6);	// draw Z axis of each user
	}

	if ( (rJ1.Position.Z <= 4 && rJ1.Position.Z >= 3) || (rJ1.Position.X <= 0.5 && rJ1.Position.X >= -0.5) ) {
		RobotDrive::setDrivetowhere("stop");
		RobotDrive::setDriveunit(0);
	}
	if (rJ1.Position.Z > 4) {
		RobotDrive::setDrivetowhere("forward");
		RobotDrive::setDriveunit(300);
	}
	if (rJ1.Position.Z < 3) {
		RobotDrive::setDrivetowhere("backward");
		RobotDrive::setDriveunit(300);
	}
	if (rJ1.Position.X < -0.5) {
		RobotDrive::setDrivetowhere("spinright");
		RobotDrive::setDriveunit(15);
	}
	if (rJ1.Position.X > 0.5) {
		RobotDrive::setDrivetowhere("spinleft");
		RobotDrive::setDriveunit(15);
	}
}

void DrawIdentity(string ID, string NAME, cv::Mat& rImg, const Joint& rJ1, ICoordinateMapper* pCMapper)
{
	if (rJ1.TrackingState == TrackingState_NotTracked)
		return;

	ColorSpacePoint ptJ1;
	pCMapper->MapCameraPointToColorSpace(rJ1.Position, &ptJ1);

	if ((ptJ1.X >= 0 && ptJ1.X <= rImg.cols) && (ptJ1.Y >= 0 && ptJ1.Y <= rImg.rows)) {
		putText(rImg, ID, cv::Point(ptJ1.X, ptJ1.Y - 150), 0, 3, cv::Scalar(255, 0, 0), 6);	// draw IDs of each user
		putText(rImg, NAME, cv::Point(ptJ1.X, ptJ1.Y - 200), 0, 3, cv::Scalar(255, 255, 0), 6);	// draw NAMEs of each user
		putText(rImg, to_string(ptJ1.X), cv::Point(0, 120), 0, 1, cv::Scalar(0, 255, 255), 3);
		putText(rImg, to_string(ptJ1.Y - 200), cv::Point(0, 150), 0, 1, cv::Scalar(0, 255, 255), 3);
	}
}

// write joint information to file in csv format
void writeInfo(int ID, ofstream& csvout, Joint *aJoints, ICoordinateMapper* pCMapper, char* time_str) {
	const Joint& WR_JointPos = aJoints[JointType::JointType_WristRight];
	const Joint& WL_JointPos = aJoints[JointType::JointType_WristLeft];
	const Joint& ER_JointPos = aJoints[JointType::JointType_ElbowRight];
	const Joint& HR_JointPos = aJoints[JointType::JointType_HandRight];
	const Joint& HTR_JointPos = aJoints[JointType::JointType_HandTipRight];
	const Joint& TR_JointPos = aJoints[JointType::JointType_ThumbRight];
	const Joint& HEAD_JointPos = aJoints[JointType::JointType_Head];

	ColorSpacePoint ptJ_Head;
	pCMapper->MapCameraPointToColorSpace(HEAD_JointPos.Position, &ptJ_Head);

	if (WR_JointPos.TrackingState == TrackingState_NotTracked) {
		csvout << tmpWR_JointPos[0] << "," << tmpWR_JointPos[1] << "," << tmpWR_JointPos[2] << ","
			<< tmpWR_JointPos[0] << "," << tmpWR_JointPos[1] << "," << tmpWR_JointPos[2] << ","
			<< tmpWR_JointPos[0] << "," << tmpWR_JointPos[1] << "," << tmpWR_JointPos[2] << ","
			<< tmpWR_JointPos[0] << "," << tmpWR_JointPos[1] << "," << tmpWR_JointPos[2] << ","
			<< tmpWR_JointPos[0] << "," << tmpWR_JointPos[1] << "," << tmpWR_JointPos[2] << ","
			<< tmpWR_JointPos[0] << "," << tmpWR_JointPos[1] << "," << tmpWR_JointPos[2] << ","
			<< ID << "," << ptJ_Head.X / 1920 * 1366 << "," << ptJ_Head.Y / 1080 * 768 << "," << time_str << "\n"; //Oringla resolution pixel is 1920*1080, and openCV frame is 1366*768
	}
	else {
		tmpWR_JointPos[0] = WR_JointPos.Position.X;
		tmpWR_JointPos[1] = WR_JointPos.Position.Y;
		tmpWR_JointPos[2] = WR_JointPos.Position.Z;
		csvout << WR_JointPos.Position.X << "," << WR_JointPos.Position.Y << "," << WR_JointPos.Position.Z << ","
			<< WL_JointPos.Position.X << "," << WL_JointPos.Position.Y << "," << WL_JointPos.Position.Z << ","
			<< ER_JointPos.Position.X << "," << ER_JointPos.Position.Y << "," << ER_JointPos.Position.Z << ","
			<< HR_JointPos.Position.X << "," << HR_JointPos.Position.Y << "," << HR_JointPos.Position.Z << ","
			<< HTR_JointPos.Position.X << "," << HTR_JointPos.Position.Y << "," << HTR_JointPos.Position.Z << ","
			<< TR_JointPos.Position.X << "," << TR_JointPos.Position.Y << "," << TR_JointPos.Position.Z << ","
			<< ID << "," << ptJ_Head.X / 1920 * 1366 << "," << ptJ_Head.Y / 1080 * 768 << "," << time_str << "\n";
	}
}