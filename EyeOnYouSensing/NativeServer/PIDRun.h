#ifndef PIDRUN_H    // To make sure you don't declare the function more than once by including the header multiple times.
#define PIDRUN_H

using namespace std;

class PIDRun {
protected:
	static bool keepSkeleton;
	static bool executePID;

public:
	static bool getKeepSkeleton();
	static void setKeepSkeleton(bool rec_keepSkeleton);

	static bool getExecutePID();
	static void setExecutePID(bool rec_executePID);
	
	static void systemCallCmd(char* cmd);
};

#endif