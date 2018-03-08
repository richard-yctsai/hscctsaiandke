/*******************************************************************************
*                                                                              *
*   PrimeSense NiTE 2.0 - User Viewer Sample                                   *
*   Copyright (C) 2012 PrimeSense Ltd.                                         *
*                                                                              *
*******************************************************************************/
// Modified by Richard Yi-Chia Tsai @ 2017/11/17 and 2017/12/31 and 2018/01/03 and 2018/03/08
// EyeOnYou Robot

#include "Viewer.h"

DWORD WINAPI DriveRobotThreadFunc(void* data);
DWORD WINAPI RunPIDThreadFunc(void* data);

int main(int argc, char** argv)
{
	// 0. Initialize server socket to conduct interprocess communication with java-based robot
	HANDLE threadRobot = CreateThread(NULL, 0, DriveRobotThreadFunc, NULL, 0, NULL);
	HANDLE threadServer = CreateThread(NULL, 0, RunPIDThreadFunc, NULL, 0, NULL);
	int WaitForSocket = 1;
	while (WaitForSocket > 0) {
		cout << WaitForSocket << endl;
		WaitForSocket--;
		Sleep(1000);
	}

	openni::Status rc = openni::STATUS_OK;

	SampleViewer sampleViewer("EyeOnYou Depth Camera Sensing");

	rc = sampleViewer.Init(argc, argv);
	if (rc != openni::STATUS_OK)
	{
		return 1;
	}
	sampleViewer.Run();
}

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