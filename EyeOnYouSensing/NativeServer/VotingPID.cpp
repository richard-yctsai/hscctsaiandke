#include "VotingPID.h"

VotingPID::VotingPID(vector<string> init_myPIDVector, string init_id, string init_nameVoting)
{
	id = init_id;
	for (int i = 0; i < 6; i++) {	
		nameVoting[i] = init_nameVoting;
		myPIDVector.push_back(init_myPIDVector);
	}
}

pair<string, unsigned int> VotingPID::get_max(const map<string, unsigned int>& x)
{
	using pairtype = pair<string, unsigned int>;
	return *max_element(x.begin(), x.end(), [](const pairtype & p1, const pairtype & p2) {
		return p1.second < p2.second;
	});
}

string VotingPID::modeOfVector(const vector<string>& vals)
{
	map<string, unsigned int> rv;

	for (auto val = vals.begin(); val != vals.end(); ++val) {
		rv[*val]++;
	}

	auto max = get_max(rv);

	return max.first;
}

string VotingPID::votingOfPID(string ID, string NAME)
{
	int myPIDID = stoi(ID);

	if (myPIDVector[myPIDID].size() == votingLength)
		myPIDVector[myPIDID].erase(myPIDVector[myPIDID].begin());

	myPIDVector[myPIDID].push_back(NAME);

	cout << "myPIDVector[myPIDID].size()" << myPIDVector[myPIDID].size() << endl;
	return modeOfVector(myPIDVector[myPIDID]);
}