#include <stdlib.h>
#include "PIDRun.h"

bool PIDRun::keepSkeleton = false;
bool PIDRun::executePID = false;

bool PIDRun::getKeepSkeleton()
{
	return keepSkeleton;
}

void PIDRun::setKeepSkeleton(bool rec_keepSkeleton)
{
	keepSkeleton = rec_keepSkeleton;
}


bool PIDRun::getExecutePID()
{
	return executePID;
}

void PIDRun::setExecutePID(bool rec_executePID)
{
	executePID = rec_executePID;
}

void PIDRun::systemCallCmd(char* cmd)
{
	system(cmd);
}

