#include <stdlib.h>
#include "PIDRun.h"

bool PIDRun::executePID = false;

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

