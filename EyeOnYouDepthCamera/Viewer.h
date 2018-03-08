/*******************************************************************************
*                                                                              *
*   PrimeSense NiTE 2.0 - User Viewer Sample                                   *
*   Copyright (C) 2012 PrimeSense Ltd.                                         *
*                                                                              *
*******************************************************************************/

// Standard Library
#include "stdafx.h"

#pragma comment(lib, "ws2_32.lib") // link winsock2
#include <thread>
#include <windows.h>

#ifndef _NITE_USER_VIEWER_H_
#define _NITE_USER_VIEWER_H_

#include <iostream>
#include <fstream>
#include <sstream>
#include <string>
#include <string.h>
#include <cstring>
#include <cstdlib>

#include "RobotDrive.h"
#include "PIDRun.h"
#include "VotingPID.h"
#include "ServerSocket.h"
#include "ServerSocketRunPID.h"

#include "NiTE.h"

#define MAX_DEPTH 10000

using namespace std;

class SampleViewer
{
public:
	SampleViewer(const char* strSampleName);
	virtual ~SampleViewer();

	virtual openni::Status Init(int argc, char **argv);
	virtual openni::Status Run();	//Does not return

protected:
	virtual void Display();
	virtual void DisplayPostDraw(){};	// Overload to draw over the screen image

	virtual void OnKey(unsigned char key, int x, int y);

	virtual openni::Status InitOpenGL(int argc, char **argv);
	void InitOpenGLHooks();

	void Finalize();

private:
	SampleViewer(const SampleViewer&);
	SampleViewer& operator=(SampleViewer&);

	static SampleViewer* ms_self;
	static void glutIdle();
	static void glutDisplay();
	static void glutKeyboard(unsigned char key, int x, int y);

	float				m_pDepthHist[MAX_DEPTH];
	char			m_strSampleName[ONI_MAX_STR];
	openni::RGB888Pixel*		m_pTexMap;
	unsigned int		m_nTexMapX;
	unsigned int		m_nTexMapY;

	openni::Device		m_device;
	nite::UserTracker* m_pUserTracker;

	nite::UserId m_poseUser;
	uint64_t m_poseTime;
};


#endif // _NITE_USER_VIEWER_H_
