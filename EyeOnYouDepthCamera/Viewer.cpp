/*******************************************************************************
*                                                                              *
*   PrimeSense NiTE 2.0 - User Viewer Sample                                   *
*   Copyright (C) 2012 PrimeSense Ltd.                                         *
*                                                                              *
*******************************************************************************/
#if (defined _WIN32)
#define PRIu64 "llu"
#else
#define __STDC_FORMAT_MACROS
#include <inttypes.h>
#endif

#include "Viewer.h"

#if (ONI_PLATFORM == ONI_PLATFORM_MACOSX)
        #include <GLUT/glut.h>
#else
        #include <GL/glut.h>
#endif

#include <NiteSampleUtilities.h>

using namespace std;

#define GL_WIN_SIZE_X	1280
#define GL_WIN_SIZE_Y	1024
#define TEXTURE_SIZE	512

#define DEFAULT_DISPLAY_MODE	DISPLAY_MODE_OVERLAY

#define MIN_NUM_CHUNKS(data_size, chunk_size)	((((data_size)-1) / (chunk_size) + 1))
#define MIN_CHUNKS_SIZE(data_size, chunk_size)	(MIN_NUM_CHUNKS(data_size, chunk_size) * (chunk_size))

SampleViewer* SampleViewer::ms_self = NULL;

bool g_drawSkeleton = true;
bool g_drawCenterOfMass = false;
bool g_drawStatusLabel = true;
bool g_drawBoundingBox = false;
bool g_drawBackground = true;
bool g_drawDepth = true;
bool g_drawFrameId = true;
bool g_runRobotTracking = true;

int g_nXRes = 0, g_nYRes = 0;

// time to hold in pose to exit program. In milliseconds.
const int g_poseTimeoutToExit = 2000;

// Global Time
char time_str[13];
SYSTEMTIME st;

// File output stream
ofstream csvfile;
string csvfilename = "C:/Users/Public/Data/KINECTData/VSFile_Buffer.csv";

// Read id and pairing result
ifstream resultfile;
string resultfilename = "C:/Users/Public/Data/KINECTData/result.csv";
string line;

string FollowingTarget = "Tsai";
int LastMovingAction = 0; // 0: stop, 1: forward, 2: backward
int RobotVelocity = 0;
const int IntervalVelocity = 4;

void DrawUserColor(nite::UserTracker* pUserTracker, const nite::UserData& userData, float r, float g, float b);

void SampleViewer::glutIdle()
{
	glutPostRedisplay();
}
void SampleViewer::glutDisplay()
{
	SampleViewer::ms_self->Display();
}
void SampleViewer::glutKeyboard(unsigned char key, int x, int y)
{
	SampleViewer::ms_self->OnKey(key, x, y);
}

SampleViewer::SampleViewer(const char* strSampleName, openni::Device& device, openni::VideoStream& depth, openni::VideoStream& color) :
	m_device(device), m_depthStream(depth), m_colorStream(color), m_streams(NULL), m_eViewState(DEFAULT_DISPLAY_MODE), m_pTexMap(NULL)
{
	ms_self = this;
	strncpy(m_strSampleName, strSampleName, ONI_MAX_STR);
	m_pUserTracker = new nite::UserTracker;
}
SampleViewer::~SampleViewer()
{
	Finalize();

	delete[] m_pTexMap;

	ms_self = NULL;
}

void SampleViewer::Finalize()
{
	delete m_pUserTracker;
	nite::NiTE::shutdown();
	openni::OpenNI::shutdown();
}

openni::Status SampleViewer::Init(int argc, char **argv)
{
	nite::NiTE::initialize();

	if (m_pUserTracker->create(&m_device) != nite::STATUS_OK)
	{
		return openni::STATUS_ERROR;
	}

	// richardyctsai
	openni::VideoMode depthVideoMode;
	openni::VideoMode colorVideoMode;

	if (m_depthStream.isValid() && m_colorStream.isValid())
	{
		depthVideoMode = m_depthStream.getVideoMode();
		colorVideoMode = m_colorStream.getVideoMode();

		int depthWidth = depthVideoMode.getResolutionX();
		int depthHeight = depthVideoMode.getResolutionY();
		int colorWidth = colorVideoMode.getResolutionX();
		int colorHeight = colorVideoMode.getResolutionY();

		if (depthWidth == colorWidth &&
			depthHeight == colorHeight)
		{
			m_width = depthWidth;
			m_height = depthHeight;
		}
		else
		{
			printf("Error - expect color and depth to be in same resolution: D: %dx%d, C: %dx%d\n",
				depthWidth, depthHeight,
				colorWidth, colorHeight);
			return openni::STATUS_ERROR;
		}
	}
	else if (m_depthStream.isValid())
	{
		depthVideoMode = m_depthStream.getVideoMode();
		m_width = depthVideoMode.getResolutionX();
		m_height = depthVideoMode.getResolutionY();
	}
	else if (m_colorStream.isValid())
	{
		colorVideoMode = m_colorStream.getVideoMode();
		m_width = colorVideoMode.getResolutionX();
		m_height = colorVideoMode.getResolutionY();
	}
	else
	{
		printf("Error - expects at least one of the streams to be valid...\n");
		return openni::STATUS_ERROR;
	}

	m_streams = new openni::VideoStream*[2];
	m_streams[0] = &m_depthStream;
	m_streams[1] = &m_colorStream;

	// Texture map init
	m_nTexMapX = MIN_CHUNKS_SIZE(m_width, TEXTURE_SIZE);
	m_nTexMapY = MIN_CHUNKS_SIZE(m_height, TEXTURE_SIZE);
	m_pTexMap = new openni::RGB888Pixel[m_nTexMapX * m_nTexMapY];

	return InitOpenGL(argc, argv);

}
openni::Status SampleViewer::Run()	//Does not return
{
	glutMainLoop();

	return openni::STATUS_OK;
}

float Colors[][3] = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {1, 1, 1}};
int colorCount = 3;

#define MAX_USERS 10
bool g_visibleUsers[MAX_USERS] = {false};
nite::SkeletonState g_skeletonStates[MAX_USERS] = {nite::SKELETON_NONE};
char g_userStatusLabels[MAX_USERS][100] = {{0}};
char g_userNameLabels[MAX_USERS][100] = {{0}};
char g_userColorLabels[MAX_USERS][100] = {{0}};

char g_generalMessage[100] = {0};

#define USER_COLOR(msg) {\
	sprintf(g_userColorLabels[userData.getId()], "%s", msg);\
	printf("[%08" PRIu64 "] User #%d:\t%s\n", ts, userData.getId(), msg);}

void updateUserColor(openni::VideoFrameRef arg_m_colorFrame, nite::UserTracker* pUserTracker, const nite::UserData& userData, uint64_t ts)
{
	// richardyctsai
	float clothPosX, clothPosY;
	pUserTracker->convertJointCoordinatesToDepth(userData.getCenterOfMass().x, userData.getCenterOfMass().y, userData.getCenterOfMass().z, &clothPosX, &clothPosY);

	const nite::SkeletonJoint& jointLK = userData.getSkeleton().getJoint(nite::JOINT_LEFT_KNEE);
	float PantsPosX, PantsPosY;
	pUserTracker->convertJointCoordinatesToDepth(jointLK.getPosition().x, jointLK.getPosition().y, jointLK.getPosition().z, &PantsPosX, &PantsPosY);


	const openni::RGB888Pixel* pImageRow = (const openni::RGB888Pixel*)arg_m_colorFrame.getData();
	int idx = ( (arg_m_colorFrame.getWidth() * (int)clothPosY+1) + (int)clothPosX);
	//if (idx < 0)
	//	idx = 0;
	//std::cout << "("
	//	<< pImageRow[idx].r << ","
	//	<< pImageRow[idx].g << ","
	//	<< pImageRow[idx].b << ")"
	//	<< std::endl;

	int idx2 = arg_m_colorFrame.getWidth() * (arg_m_colorFrame.getHeight() + 1) / 2;

	DrawUserColor(pUserTracker, userData, pImageRow[idx].r, pImageRow[idx].g, pImageRow[idx].b);

	char str[80];
	sprintf(str, "%d", userData.getId());
	strcat(str, ": ");
	strcat(str, "YourColor");

	USER_COLOR(str);

	//const openni::RGB888Pixel* pImageRow = (const openni::RGB888Pixel*)arg_m_colorFrame.getData();
	//openni::RGB888Pixel* pTexRow = arg_m_pTexMap + arg_m_colorFrame.getCropOriginY() * arg_m_nTexMapX;
	//int rowSize = arg_m_colorFrame.getStrideInBytes() / sizeof(openni::RGB888Pixel);
	//int idx = 0;

	//for (int y = 0; y < clothPosY; ++y)
	//{
	//	const openni::RGB888Pixel* pImage = pImageRow;
	//	openni::RGB888Pixel* pTex = pTexRow + arg_m_colorFrame.getCropOriginX();

	//	if (y >= clothPosY - 1)
	//	{
	//		for (int x = 0; x < clothPosX; ++x, ++pImage, ++pTex, ++idx)
	//		{
	//			*pTex = *pImage;
	//		}
	//		break;
	//	}
	//	else
	//	{
	//		for (int x = 0; x < arg_m_colorFrame.getWidth(); ++x, ++pImage, ++pTex, ++idx)
	//		{
	//			*pTex = *pImage;
	//		}

	//		pImageRow += rowSize;
	//		pTexRow += arg_m_nTexMapX;
	//	}
	//}
}

#define USER_NAME(msg) {\
	sprintf(g_userNameLabels[userData.getId()], "%s", msg);\
	printf("[%08" PRIu64 "] User #%d:\t%s\n", ts, userData.getId(), msg);}

void updateIdentity(const char *NAME, const nite::UserData& userData, uint64_t ts)
{
	char str[80];
	sprintf(str, "%d", userData.getId());
	strcat(str, ": ");
	strcat(str, NAME);

	USER_NAME(str);
}

#define USER_MESSAGE(msg) {\
	sprintf(g_userStatusLabels[user.getId()], "%s", msg);\
	printf("[%08" PRIu64 "] User #%d:\t%s\n", ts, user.getId(), msg);}

void updateUserState(const nite::UserData& user, uint64_t ts)
{
	if (user.isNew())
	{
		USER_MESSAGE("New");
	}
	else if (user.isVisible() && !g_visibleUsers[user.getId()])
		printf("[%08" PRIu64 "] User #%d:\tVisible\n", ts, user.getId());
	else if (!user.isVisible() && g_visibleUsers[user.getId()])
		printf("[%08" PRIu64 "] User #%d:\tOut of Scene\n", ts, user.getId());
	else if (user.isLost())
	{
		USER_MESSAGE("Lost");
	}
	g_visibleUsers[user.getId()] = user.isVisible();


	if(g_skeletonStates[user.getId()] != user.getSkeleton().getState())
	{
		switch(g_skeletonStates[user.getId()] = user.getSkeleton().getState())
		{
		case nite::SKELETON_NONE:
			USER_MESSAGE("Stopped tracking.")
			break;
		case nite::SKELETON_CALIBRATING:
			USER_MESSAGE("Calibrating...")
			break;
		case nite::SKELETON_TRACKED:
			USER_MESSAGE("Tracking!")
			break;
		case nite::SKELETON_CALIBRATION_ERROR_NOT_IN_POSE:
		case nite::SKELETON_CALIBRATION_ERROR_HANDS:
		case nite::SKELETON_CALIBRATION_ERROR_LEGS:
		case nite::SKELETON_CALIBRATION_ERROR_HEAD:
		case nite::SKELETON_CALIBRATION_ERROR_TORSO:
			USER_MESSAGE("Calibration Failed... :-|")
			break;
		}
	}
}

#ifndef USE_GLES
void glPrintString(void *font, const char *str)
{
	int i,l = (int)strlen(str);

	for(i=0; i<l; i++)
	{   
		glutBitmapCharacter(font,*str++);
	}   
}
#endif
void DrawStatusLabel(nite::UserTracker* pUserTracker, const nite::UserData& user)
{
	int color = user.getId() % colorCount;
	glColor3f(1.0f - Colors[color][0], 1.0f - Colors[color][1], 1.0f - Colors[color][2]);

	float x,y;
	pUserTracker->convertJointCoordinatesToDepth(user.getCenterOfMass().x, user.getCenterOfMass().y, user.getCenterOfMass().z, &x, &y);
	x *= GL_WIN_SIZE_X/(float)g_nXRes;
	y *= GL_WIN_SIZE_Y/(float)g_nYRes;
	char *msg = g_userStatusLabels[user.getId()];
	glRasterPos2i(x-((strlen(msg)/2)*8),y);
	glPrintString(GLUT_BITMAP_HELVETICA_18, msg);

}
void DrawIdentity(nite::UserTracker* pUserTracker, const nite::UserData& userData)
{
	int color = userData.getId() % colorCount;
	glColor3f(1.0f - Colors[color][0], 1.0f - Colors[color][1], 1.0f - Colors[color][2]);

	const nite::SkeletonJoint& jointHead = userData.getSkeleton().getJoint(nite::JOINT_HEAD);
	
	float x, y;
	pUserTracker->convertJointCoordinatesToDepth(jointHead.getPosition().x, jointHead.getPosition().y, jointHead.getPosition().z, &x, &y);
	x *= GL_WIN_SIZE_X / (float)g_nXRes;
	y *= GL_WIN_SIZE_Y / (float)g_nYRes;
	char *msg = g_userNameLabels[userData.getId()];
	glRasterPos2i(x-((strlen(msg)/2)*8),y-120);
	glPrintString(GLUT_BITMAP_TIMES_ROMAN_24, msg);

	//cout << "X: " << jointHead.getOrientation().x << "         Y:" << jointHead.getOrientation().y << "         Z:" << jointHead.getOrientation().z << endl;

}
void DrawUserColor(nite::UserTracker* pUserTracker, const nite::UserData& userData, float r, float g, float b)
{
	/*int color = userData.getId() % colorCount;
	glColor3f(1.0f - Colors[color][0], 1.0f - Colors[color][1], 1.0f - Colors[color][2]);*/
	glColor3f(1.0f - r, 1.0f - g, 1.0f - b);

	//std::cout << "( "
	//	<< 1.0f - r << ","
	//	<< 1.0f - g << ","
	//	<< std::endl;

	const nite::SkeletonJoint& jointHead = userData.getSkeleton().getJoint(nite::JOINT_HEAD);

	float x, y;
	pUserTracker->convertJointCoordinatesToDepth(jointHead.getPosition().x, jointHead.getPosition().y, jointHead.getPosition().z, &x, &y);
	x *= GL_WIN_SIZE_X / (float)g_nXRes;
	y *= GL_WIN_SIZE_Y / (float)g_nYRes;
	char *msg = g_userColorLabels[userData.getId()];
	glRasterPos2i(x-((strlen(msg)/2)*8), y+100);
	glPrintString(GLUT_BITMAP_TIMES_ROMAN_24, msg);

}
void DrawFrameId(int frameId)
{
	char buffer[80] = "";
	sprintf(buffer, "%d", frameId);
	glColor3f(1.0f, 0.0f, 0.0f);
	glRasterPos2i(20, 20);
	glPrintString(GLUT_BITMAP_HELVETICA_18, buffer);

}
void DrawGlobalTime()
{
	GetLocalTime(&st);
	memset(time_str, '\0', 13);
	sprintf_s(time_str, 13, "%02d:%02d:%02d:%03d", st.wHour, st.wMinute, st.wSecond, st.wMilliseconds);

	char buffer[80] = "";
	sprintf(buffer, "%s", time_str);
	glColor3f(0.0f, 0.0f, 1.0f);
	glRasterPos2i(20, 40);
	glPrintString(GLUT_BITMAP_HELVETICA_18, buffer);

}
void DrawCenterOfMass(nite::UserTracker* pUserTracker, const nite::UserData& user)
{
	glColor3f(1.0f, 1.0f, 1.0f);

	float coordinates[3] = {0};

	pUserTracker->convertJointCoordinatesToDepth(user.getCenterOfMass().x, user.getCenterOfMass().y, user.getCenterOfMass().z, &coordinates[0], &coordinates[1]);

	coordinates[0] *= GL_WIN_SIZE_X/(float)g_nXRes;
	coordinates[1] *= GL_WIN_SIZE_Y/(float)g_nYRes;
	glPointSize(8);
	glVertexPointer(3, GL_FLOAT, 0, coordinates);
	glDrawArrays(GL_POINTS, 0, 1);

}
void DrawBoundingBox(const nite::UserData& user)
{
	glColor3f(1.0f, 1.0f, 1.0f);

	float coordinates[] =
	{
		user.getBoundingBox().max.x, user.getBoundingBox().max.y, 0,
		user.getBoundingBox().max.x, user.getBoundingBox().min.y, 0,
		user.getBoundingBox().min.x, user.getBoundingBox().min.y, 0,
		user.getBoundingBox().min.x, user.getBoundingBox().max.y, 0,
	};
	coordinates[0]  *= GL_WIN_SIZE_X/(float)g_nXRes;
	coordinates[1]  *= GL_WIN_SIZE_Y/(float)g_nYRes;
	coordinates[3]  *= GL_WIN_SIZE_X/(float)g_nXRes;
	coordinates[4]  *= GL_WIN_SIZE_Y/(float)g_nYRes;
	coordinates[6]  *= GL_WIN_SIZE_X/(float)g_nXRes;
	coordinates[7]  *= GL_WIN_SIZE_Y/(float)g_nYRes;
	coordinates[9]  *= GL_WIN_SIZE_X/(float)g_nXRes;
	coordinates[10] *= GL_WIN_SIZE_Y/(float)g_nYRes;

	glPointSize(2);
	glVertexPointer(3, GL_FLOAT, 0, coordinates);
	glDrawArrays(GL_LINE_LOOP, 0, 4);

}
void RunRobotTracking(nite::UserTracker* pUserTracker, const nite::UserData& user)
{
	double thresholdMaxZ = 2000.0;
	double thresholdMinZ = 1000.0;
	double thresholdMaxX = 300.0;
	double thresholdMinX = -300.0;

	glColor3f(1.0f, 1.0f, 1.0f);

	float coordinates[3] = { 0 };
	coordinates[0] = user.getCenterOfMass().x;
	coordinates[1] = user.getCenterOfMass().y;
	coordinates[2] = user.getCenterOfMass().z;
	
	if (coordinates[0] <= thresholdMaxX && coordinates[0] >= thresholdMinX) {
		RobotDrive::setDrivetowhere("stop");
		RobotDrive::setDriveunit(0);

		if (coordinates[2] <= thresholdMaxZ && coordinates[2] >= thresholdMinZ) {
			if (LastMovingAction == 1) {
				RobotDrive::setDrivetowhere("forward");
				RobotDrive::setDriveunit(RobotVelocity);
			}
			else if (LastMovingAction == 2) {
				RobotDrive::setDrivetowhere("backward");
				RobotDrive::setDriveunit(RobotVelocity);
			}

			RobotVelocity = RobotVelocity - IntervalVelocity;
			if (RobotVelocity <= 0)
				RobotVelocity = 0;
		}
		else if (coordinates[2] > thresholdMaxZ) {
			LastMovingAction = 1;
			RobotDrive::setDrivetowhere("forward");
			RobotDrive::setDriveunit(RobotVelocity);
			RobotVelocity = RobotVelocity + IntervalVelocity;
			if (RobotVelocity > 500)
				RobotVelocity = 500;
		}
		else if (coordinates[2] < thresholdMinZ) {
			LastMovingAction = 2;
			RobotDrive::setDrivetowhere("backward");
			RobotDrive::setDriveunit(RobotVelocity);
			RobotVelocity = RobotVelocity + IntervalVelocity;
			if (RobotVelocity > 500)
				RobotVelocity = 500;
		}
	}
	else if (coordinates[0] < thresholdMinX) {
		if (coordinates[2] > thresholdMaxZ) {
			RobotDrive::setDrivetowhere("turnright");
			RobotDrive::setDriveunit(RobotVelocity);
		}
		else {
			RobotDrive::setDrivetowhere("spinright");
			RobotDrive::setDriveunit(50);
		}
	}
	else if (coordinates[0] > thresholdMaxX) {
		if (coordinates[2] > thresholdMaxZ) {
			RobotDrive::setDrivetowhere("turnleft");
			RobotDrive::setDriveunit(RobotVelocity);
		}
		else {
			RobotDrive::setDrivetowhere("spinleft");
			RobotDrive::setDriveunit(50);
		}
	}
}
void StopRobotTracking()
{
	if (LastMovingAction == 1) {
		RobotDrive::setDrivetowhere("forward");
		RobotDrive::setDriveunit(RobotVelocity);
	}
	else if (LastMovingAction == 2) {
		RobotDrive::setDrivetowhere("backward");
		RobotDrive::setDriveunit(RobotVelocity);
	}

	RobotVelocity = RobotVelocity - IntervalVelocity;
	if (RobotVelocity <= 0)
		RobotVelocity = 0;
}
void GetResultOfPID()
{
	// 1. csvfile.close();
	if (PIDRun::getKeepSkeleton() == true)
	{
		// Save buffer file
		csvfile.close();
		// Create new file and from vsfile_Buffer.csv
		ifstream fin(csvfilename);
		string csvfilenamepairing = "c:/users/public/data/kinectdata/VSFile.csv";
		ofstream fout(csvfilenamepairing);
		string line;
		while (getline(fin, line)) fout << line << '\n';
		cout << "buffer complete!!!!!!!!!!!!!" << endl;

		PIDRun::setKeepSkeleton(false);
		PIDRun::setExecutePID(true);
		csvfile.open(csvfilename);
	}

	// Read result.csv to tag profile on top of the head
	if (PIDRun::getTagProfile() == true) {
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
			if (idx % 2 == 0) {
				VotingPID::setID(data.c_str());
			}
			else if (idx % 2 == 1) {
				nameReadFile = data.c_str();
				VotingPID::setnameVotingWithIndex(VotingPID::getID(), VotingPID::votingOfPID(VotingPID::getID(), nameReadFile));
			}
			idx += 1;
		}
		PIDRun::setTagProfile(false);
	}
}
void WriteSkeletonInfo(int ID, ofstream& csvout, const nite::UserData& userData, char* time_str)
{
	const nite::SkeletonJoint& WH_JointPos = userData.getSkeleton().getJoint(nite::JOINT_RIGHT_HAND);

	csvout << WH_JointPos.getPosition().x / 1000.0 << "," << WH_JointPos.getPosition().y / 1000.0 << "," << WH_JointPos.getPosition().z / 1000.0 << ","
		<< WH_JointPos.getOrientation().x / 1000.0 << "," << WH_JointPos.getOrientation().y / 1000.0 << "," << WH_JointPos.getOrientation().z / 1000.0 << ","
		<< ID << "," << time_str << "\n";
}

void DrawLimb(nite::UserTracker* pUserTracker, const nite::SkeletonJoint& joint1, const nite::SkeletonJoint& joint2, int color)
{
	float coordinates[6] = {0};
	pUserTracker->convertJointCoordinatesToDepth(joint1.getPosition().x, joint1.getPosition().y, joint1.getPosition().z, &coordinates[0], &coordinates[1]);
	pUserTracker->convertJointCoordinatesToDepth(joint2.getPosition().x, joint2.getPosition().y, joint2.getPosition().z, &coordinates[3], &coordinates[4]);
	
	coordinates[0] *= GL_WIN_SIZE_X/(float)g_nXRes;
	coordinates[1] *= GL_WIN_SIZE_Y/(float)g_nYRes;
	coordinates[3] *= GL_WIN_SIZE_X/(float)g_nXRes;
	coordinates[4] *= GL_WIN_SIZE_Y/(float)g_nYRes;

	if (joint1.getPositionConfidence() == 1 && joint2.getPositionConfidence() == 1)
	{
		glColor3f(1.0f - Colors[color][0], 1.0f - Colors[color][1], 1.0f - Colors[color][2]);
	}
	else if (joint1.getPositionConfidence() < 0.5f || joint2.getPositionConfidence() < 0.5f)
	{
		//glColor3f(1.0f - Colors[color][0], 1.0f - Colors[color][1], 1.0f - Colors[color][2]); // richardyctsai
		return;
	}
	else
	{
		glColor3f(.5, .5, .5);
	}
	glPointSize(2);
	glVertexPointer(3, GL_FLOAT, 0, coordinates);
	glDrawArrays(GL_LINES, 0, 2);

	glPointSize(10);
	if (joint1.getPositionConfidence() == 1)
	{
		glColor3f(1.0f - Colors[color][0], 1.0f - Colors[color][1], 1.0f - Colors[color][2]);
	}
	else
	{
		glColor3f(.5, .5, .5);
	}
	glVertexPointer(3, GL_FLOAT, 0, coordinates);
	glDrawArrays(GL_POINTS, 0, 1);

	if (joint2.getPositionConfidence() == 1)
	{
		glColor3f(1.0f - Colors[color][0], 1.0f - Colors[color][1], 1.0f - Colors[color][2]);
	}
	else
	{
		glColor3f(.5, .5, .5);
	}
	glVertexPointer(3, GL_FLOAT, 0, coordinates+3);
	glDrawArrays(GL_POINTS, 0, 1);
}
void DrawSkeleton(nite::UserTracker* pUserTracker, const nite::UserData& userData)
{
	DrawLimb(pUserTracker, userData.getSkeleton().getJoint(nite::JOINT_HEAD), userData.getSkeleton().getJoint(nite::JOINT_NECK), userData.getId() % colorCount);

	DrawLimb(pUserTracker, userData.getSkeleton().getJoint(nite::JOINT_LEFT_SHOULDER), userData.getSkeleton().getJoint(nite::JOINT_LEFT_ELBOW), userData.getId() % colorCount);
	DrawLimb(pUserTracker, userData.getSkeleton().getJoint(nite::JOINT_LEFT_ELBOW), userData.getSkeleton().getJoint(nite::JOINT_LEFT_HAND), userData.getId() % colorCount);

	DrawLimb(pUserTracker, userData.getSkeleton().getJoint(nite::JOINT_RIGHT_SHOULDER), userData.getSkeleton().getJoint(nite::JOINT_RIGHT_ELBOW), userData.getId() % colorCount);
	DrawLimb(pUserTracker, userData.getSkeleton().getJoint(nite::JOINT_RIGHT_ELBOW), userData.getSkeleton().getJoint(nite::JOINT_RIGHT_HAND), userData.getId() % colorCount);

	DrawLimb(pUserTracker, userData.getSkeleton().getJoint(nite::JOINT_LEFT_SHOULDER), userData.getSkeleton().getJoint(nite::JOINT_RIGHT_SHOULDER), userData.getId() % colorCount);

	DrawLimb(pUserTracker, userData.getSkeleton().getJoint(nite::JOINT_LEFT_SHOULDER), userData.getSkeleton().getJoint(nite::JOINT_TORSO), userData.getId() % colorCount);
	DrawLimb(pUserTracker, userData.getSkeleton().getJoint(nite::JOINT_RIGHT_SHOULDER), userData.getSkeleton().getJoint(nite::JOINT_TORSO), userData.getId() % colorCount);

	DrawLimb(pUserTracker, userData.getSkeleton().getJoint(nite::JOINT_TORSO), userData.getSkeleton().getJoint(nite::JOINT_LEFT_HIP), userData.getId() % colorCount);
	DrawLimb(pUserTracker, userData.getSkeleton().getJoint(nite::JOINT_TORSO), userData.getSkeleton().getJoint(nite::JOINT_RIGHT_HIP), userData.getId() % colorCount);

	DrawLimb(pUserTracker, userData.getSkeleton().getJoint(nite::JOINT_LEFT_HIP), userData.getSkeleton().getJoint(nite::JOINT_RIGHT_HIP), userData.getId() % colorCount);


	DrawLimb(pUserTracker, userData.getSkeleton().getJoint(nite::JOINT_LEFT_HIP), userData.getSkeleton().getJoint(nite::JOINT_LEFT_KNEE), userData.getId() % colorCount);
	DrawLimb(pUserTracker, userData.getSkeleton().getJoint(nite::JOINT_LEFT_KNEE), userData.getSkeleton().getJoint(nite::JOINT_LEFT_FOOT), userData.getId() % colorCount);

	DrawLimb(pUserTracker, userData.getSkeleton().getJoint(nite::JOINT_RIGHT_HIP), userData.getSkeleton().getJoint(nite::JOINT_RIGHT_KNEE), userData.getId() % colorCount);
	DrawLimb(pUserTracker, userData.getSkeleton().getJoint(nite::JOINT_RIGHT_KNEE), userData.getSkeleton().getJoint(nite::JOINT_RIGHT_FOOT), userData.getId() % colorCount);
}


void SampleViewer::Display()
{
	// richardyctsai
	int changedIndex;
	openni::Status rc = openni::OpenNI::waitForAnyStream(m_streams, 2, &changedIndex);
	if (rc != openni::STATUS_OK)
	{
		printf("Wait failed\n");
		return;
	}

	switch (changedIndex)
	{
	case 0:
		m_depthStream.readFrame(&m_depthFrame); break;
	case 1:
		m_colorStream.readFrame(&m_colorFrame); break;
	default:
		printf("Error in wait\n");
	}


	nite::UserTrackerFrameRef userTrackerFrame;
	openni::VideoFrameRef depthFrame;

	nite::Status rcUserTracker = m_pUserTracker->readFrame(&userTrackerFrame);
	if (rcUserTracker != nite::STATUS_OK)
	{
		printf("GetNextData failed\n");
		return;
	}

	depthFrame = userTrackerFrame.getDepthFrame();

	if (m_pTexMap == NULL)
	{
		// Texture map init
		m_nTexMapX = MIN_CHUNKS_SIZE(depthFrame.getVideoMode().getResolutionX(), TEXTURE_SIZE);
		m_nTexMapY = MIN_CHUNKS_SIZE(depthFrame.getVideoMode().getResolutionY(), TEXTURE_SIZE);
		m_pTexMap = new openni::RGB888Pixel[m_nTexMapX * m_nTexMapY];
	}

	const nite::UserMap& userLabels = userTrackerFrame.getUserMap();


	glClear (GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

	glMatrixMode(GL_PROJECTION);
	glPushMatrix();
	glLoadIdentity();
	glOrtho(0, GL_WIN_SIZE_X, GL_WIN_SIZE_Y, 0, -1.0, 1.0);

	if (depthFrame.isValid() && g_drawDepth)
	{
		calculateHistogram(m_pDepthHist, MAX_DEPTH, depthFrame);
	}

	memset(m_pTexMap, 0, m_nTexMapX*m_nTexMapY*sizeof(openni::RGB888Pixel));

	// richardyctsai
	// check if we need to draw image frame to texture
	if ((m_eViewState == DISPLAY_MODE_OVERLAY ||
		m_eViewState == DISPLAY_MODE_IMAGE) && m_colorFrame.isValid())
	{
		const openni::RGB888Pixel* pImageRow = (const openni::RGB888Pixel*)m_colorFrame.getData();
		openni::RGB888Pixel* pTexRow = m_pTexMap + m_colorFrame.getCropOriginY() * m_nTexMapX;
		int rowSize = m_colorFrame.getStrideInBytes() / sizeof(openni::RGB888Pixel);

		for (int y = 0; y < m_colorFrame.getHeight(); ++y)
		{
			const openni::RGB888Pixel* pImage = pImageRow;
			openni::RGB888Pixel* pTex = pTexRow + m_colorFrame.getCropOriginX();

			for (int x = 0; x < m_colorFrame.getWidth(); ++x, ++pImage, ++pTex)
			{
				*pTex = *pImage;
			}

			pImageRow += rowSize;
			pTexRow += m_nTexMapX;
		}
	}

	
	float factor[3] = {1, 1, 1};
	// check if we need to draw depth frame to texture
	if (depthFrame.isValid() && g_drawDepth)
	{
		const nite::UserId* pLabels = userLabels.getPixels();

		const openni::DepthPixel* pDepthRow = (const openni::DepthPixel*)depthFrame.getData();
		openni::RGB888Pixel* pTexRow = m_pTexMap + depthFrame.getCropOriginY() * m_nTexMapX;
		int rowSize = depthFrame.getStrideInBytes() / sizeof(openni::DepthPixel);

		for (int y = 0; y < depthFrame.getHeight(); ++y)
		{
			const openni::DepthPixel* pDepth = pDepthRow;
			openni::RGB888Pixel* pTex = pTexRow + depthFrame.getCropOriginX();

			for (int x = 0; x < depthFrame.getWidth(); ++x, ++pDepth, ++pTex, ++pLabels)
			{
				if (*pDepth != 0)
				{
					if (*pLabels == 0)
					{
						if (!g_drawBackground)
						{
							factor[0] = factor[1] = factor[2] = 0;

						}
						else
						{
							factor[0] = Colors[colorCount][0];
							factor[1] = Colors[colorCount][1];
							factor[2] = Colors[colorCount][2];
						}
					}
					else
					{
						factor[0] = Colors[*pLabels % colorCount][0];
						factor[1] = Colors[*pLabels % colorCount][1];
						factor[2] = Colors[*pLabels % colorCount][2];
					}
//					// Add debug lines - every 10cm
// 					else if ((*pDepth / 10) % 10 == 0)
// 					{
// 						factor[0] = factor[2] = 0;
// 					}

					int nHistValue = m_pDepthHist[*pDepth];
					pTex->r = nHistValue*factor[0];
					pTex->g = nHistValue*factor[1];
					pTex->b = nHistValue*factor[2];

					factor[0] = factor[1] = factor[2] = 1;
				}
			}

			pDepthRow += rowSize;
			pTexRow += m_nTexMapX;
		}
	}

	glTexParameteri(GL_TEXTURE_2D, GL_GENERATE_MIPMAP_SGIS, GL_TRUE);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, m_nTexMapX, m_nTexMapY, 0, GL_RGB, GL_UNSIGNED_BYTE, m_pTexMap);

	// Display the OpenGL texture map
	glColor4f(1,1,1,1);

	glEnable(GL_TEXTURE_2D);
	glBegin(GL_QUADS);

	g_nXRes = depthFrame.getVideoMode().getResolutionX();
	g_nYRes = depthFrame.getVideoMode().getResolutionY();

	// upper left
	glTexCoord2f(0, 0);
	glVertex2f(0, 0);
	// upper right
	glTexCoord2f((float)g_nXRes/(float)m_nTexMapX, 0);
	glVertex2f(GL_WIN_SIZE_X, 0);
	// bottom right
	glTexCoord2f((float)g_nXRes/(float)m_nTexMapX, (float)g_nYRes/(float)m_nTexMapY);
	glVertex2f(GL_WIN_SIZE_X, GL_WIN_SIZE_Y);
	// bottom left
	glTexCoord2f(0, (float)g_nYRes/(float)m_nTexMapY);
	glVertex2f(0, GL_WIN_SIZE_Y);

	glEnd();
	glDisable(GL_TEXTURE_2D);

	const nite::Array<nite::UserData>& users = userTrackerFrame.getUsers();

	if (users.getSize() == 0)
	{
		StopRobotTracking();
	}

	// Read result.csv to tag profile on top of the head
	GetResultOfPID();
	
	for (int i = 0; i < users.getSize(); ++i)
	{
		const nite::UserData& user = users[i];
		
		updateUserState(user, userTrackerFrame.getTimestamp());
		updateIdentity(VotingPID::getnameVotingWithIndex(user.getId()).c_str(), user, userTrackerFrame.getTimestamp());
		updateUserColor(m_colorFrame, m_pUserTracker, user, userTrackerFrame.getTimestamp());

		if (user.isNew())
		{
			m_pUserTracker->startSkeletonTracking(user.getId());
			m_pUserTracker->startPoseDetection(user.getId(), nite::POSE_CROSSED_HANDS);
		}
		else if (!user.isLost())
		{
			if (g_drawStatusLabel)
			{
				DrawStatusLabel(m_pUserTracker, user);
				DrawIdentity(m_pUserTracker, user);
				//DrawUserColor(m_pUserTracker, user);
			}
			if (g_drawCenterOfMass)
			{
				DrawCenterOfMass(m_pUserTracker, user);
			}
			if (g_drawBoundingBox)
			{
				DrawBoundingBox(user);
			}

			if (users[i].getSkeleton().getState() == nite::SKELETON_TRACKED && g_drawSkeleton)
			{
				DrawSkeleton(m_pUserTracker, user);

				// WriteSkeletonInfo when they are detected
				GetLocalTime(&st);
				memset(time_str, '\0', 13);
				sprintf_s(time_str, 13, "%02d:%02d:%02d:%03d", st.wHour, st.wMinute, st.wSecond, st.wMilliseconds);
				WriteSkeletonInfo(user.getId(), csvfile, user, time_str);
			}

			if (g_runRobotTracking)
			{
				RunRobotTracking(m_pUserTracker, user);
			}
		}

		if (m_poseUser == 0 || m_poseUser == user.getId())
		{
			const nite::PoseData& pose = user.getPose(nite::POSE_CROSSED_HANDS);

			if (pose.isEntered())
			{
				// Start timer
				sprintf(g_generalMessage, "In exit pose. Keep it for %d second%s to exit\n", g_poseTimeoutToExit/1000, g_poseTimeoutToExit/1000 == 1 ? "" : "s");
				printf("Counting down %d second to exit\n", g_poseTimeoutToExit/1000);
				m_poseUser = user.getId();
				m_poseTime = userTrackerFrame.getTimestamp();
			}
			else if (pose.isExited())
			{
				memset(g_generalMessage, 0, sizeof(g_generalMessage));
				printf("Count-down interrupted\n");
				m_poseTime = 0;
				m_poseUser = 0;
			}
			else if (pose.isHeld())
			{
				// tick
				if (userTrackerFrame.getTimestamp() - m_poseTime > g_poseTimeoutToExit * 1000)
				{
					printf("Count down complete. Exit...\n");
					Finalize();
					exit(2);
				}
			}
		}
	}

	if (g_drawFrameId)
	{
		DrawFrameId(userTrackerFrame.getFrameIndex());
		DrawGlobalTime();
	}

	if (g_generalMessage[0] != '\0')
	{
		char *msg = g_generalMessage;
		glColor3f(1.0f, 0.0f, 0.0f);
		glRasterPos2i(100, 20);
		glPrintString(GLUT_BITMAP_HELVETICA_18, msg);
	}



	// Swap the OpenGL display buffers
	glutSwapBuffers();

}

void SampleViewer::OnKey(unsigned char key, int /*x*/, int /*y*/)
{
	switch (key)
	{
	case 27:
		Finalize();
		exit (1);
	case 's':
		// Draw skeleton?
		g_drawSkeleton = !g_drawSkeleton;
		break;
	case 'l':
		// Draw user status label?
		g_drawStatusLabel = !g_drawStatusLabel;
		break;
	case 'c':
		// Draw center of mass?
		g_drawCenterOfMass = !g_drawCenterOfMass;
		break;
	case 'x':
		// Draw bounding box?
		g_drawBoundingBox = !g_drawBoundingBox;
		break;
	case 'b':
		// Draw background?
		g_drawBackground = !g_drawBackground;
		break;
	case 'd':
		// Draw depth?
		g_drawDepth = !g_drawDepth;
		break;
	case 'f':
		// Draw frame ID
		g_drawFrameId = !g_drawFrameId;
		break;
	case 'r':
		// Draw frame ID
		g_runRobotTracking = !g_runRobotTracking;
		break;
	case '1':
		m_eViewState = DISPLAY_MODE_OVERLAY;
		m_device.setImageRegistrationMode(openni::IMAGE_REGISTRATION_DEPTH_TO_COLOR);
		break;
	case '2':
		m_eViewState = DISPLAY_MODE_DEPTH;
		m_device.setImageRegistrationMode(openni::IMAGE_REGISTRATION_OFF);
		break;
	case '3':
		m_eViewState = DISPLAY_MODE_IMAGE;
		m_device.setImageRegistrationMode(openni::IMAGE_REGISTRATION_OFF);
		break;
	case 'm':
		m_depthStream.setMirroringEnabled(!m_depthStream.getMirroringEnabled());
		m_colorStream.setMirroringEnabled(!m_colorStream.getMirroringEnabled());
		break;
	}

}

openni::Status SampleViewer::InitOpenGL(int argc, char **argv)
{
	glutInit(&argc, argv);
	glutInitDisplayMode(GLUT_RGB | GLUT_DOUBLE | GLUT_DEPTH);
	glutInitWindowSize(GL_WIN_SIZE_X, GL_WIN_SIZE_Y);
	glutCreateWindow (m_strSampleName);
	// 	glutFullScreen();
	glutSetCursor(GLUT_CURSOR_NONE);

	InitOpenGLHooks();

	glDisable(GL_DEPTH_TEST);
	glEnable(GL_TEXTURE_2D);

	glEnableClientState(GL_VERTEX_ARRAY);
	glDisableClientState(GL_COLOR_ARRAY);

	return openni::STATUS_OK;

}
void SampleViewer::InitOpenGLHooks()
{
	glutKeyboardFunc(glutKeyboard);
	glutDisplayFunc(glutDisplay);
	glutIdleFunc(glutIdle);
}

//// richardyctsai
//// 5. create OpenCV Window
//cv::namedWindow("Color Image", CV_WINDOW_AUTOSIZE); 
//
//// 6. start
//openni::VideoFrameRef colorFrame;

//// 7. check is color stream is available
//if (mColorStream.isValid())
//{
//	// 7a. get color frame
//	if (mColorStream.readFrame(&colorFrame) == STATUS_OK)
//	{
//		// 7b. convert data to OpenCV format
//		const cv::Mat mImageRGB(
//			colorFrame.getHeight(), colorFrame.getWidth(),
//			CV_8UC3, (void*)colorFrame.getData());
//		// 7c. convert form RGB to BGR
//		cv::Mat cImageBGR;
//		cv::cvtColor(mImageRGB, cImageBGR, CV_RGB2BGR);
//		// 7d. show image
//		cv::imshow("Color Image", cImageBGR);
//	}
//}