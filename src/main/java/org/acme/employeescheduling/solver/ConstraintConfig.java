package org.acme.employeescheduling.solver;

public class ConstraintConfig {

	// Hard penalties
	private final int requiredSkillPenalty = 1;
	private final int noOverlappingShiftsPenalty = 1;
	private final int minimumBreak8HoursPenalty = 1;
	private final int shortenedBreakCompensatedPenalty = 1;
	private final int oneShiftPerDayPenalty = 1;
	private final int unavailableEmployeePenalty = 1;
	
	// Soft penalties
	private final int undesiredDayForEmployeePenalty = 1;
	private final int balanceEmployeeShiftAssignmentsPenalty = 1;
	private final int shortenedBreakPenalty = 1;
	private final int desiredDayForEmployeeReward = 1;
	private final int sameLocationAsYesterdayReward = 1;
	private final int carpoolGroupReward = 1;
	private final int balanceShiftTypesPenalty = 1;

	public int getRequiredSkillPenalty() {
		return requiredSkillPenalty;
	}

	public int getNoOverlappingShiftsPenalty() {
		return noOverlappingShiftsPenalty;
	}

	public int getMinimumBreak8HoursPenalty() {
		return minimumBreak8HoursPenalty;
	}

	public int getShortenedBreakCompensatedPenalty() {
		return shortenedBreakCompensatedPenalty;
	}

	public int getOneShiftPerDayPenalty() {
		return oneShiftPerDayPenalty;
	}

	public int getUnavailableEmployeePenalty() {
		return unavailableEmployeePenalty;
	}

	public int getUndesiredDayForEmployeePenalty() {
		return undesiredDayForEmployeePenalty;
	}

	public int getDesiredDayForEmployeeReward() {
		return desiredDayForEmployeeReward;
	}

	public int getBalanceEmployeeShiftAssignmentsPenalty() {
		return balanceEmployeeShiftAssignmentsPenalty;
	}

	public int getShortenedBreakPenalty() {
		return shortenedBreakPenalty;
	}

	public int getSameLocationAsYesterdayReward() {
		return sameLocationAsYesterdayReward;
	}

	public int getCarpoolGroupReward() {
		return carpoolGroupReward;
	}

	public int getBalanceShiftTypesPenalty() {
		return balanceShiftTypesPenalty;
	}

}