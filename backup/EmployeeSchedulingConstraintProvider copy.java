package org.acme.employeescheduling.solver;

import static ai.timefold.solver.core.api.score.stream.Joiners.equal;
import static ai.timefold.solver.core.api.score.stream.Joiners.lessThanOrEqual;
import static ai.timefold.solver.core.api.score.stream.Joiners.overlapping;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.math.BigDecimal;

import ai.timefold.solver.core.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.common.LoadBalance;

import org.acme.employeescheduling.domain.Employee;
import org.acme.employeescheduling.domain.Shift;

public class EmployeeSchedulingConstraintProvider implements ConstraintProvider {

	private static final ConstraintConfig config = new ConstraintConfig();

	private static int getMinuteOverlap(Shift shift1, Shift shift2) {
		// The overlap of two timeslot occurs in the range common to both timeslots.
		// Both timeslots are active after the higher of their two start times,
		// and before the lower of their two end times.
		LocalDateTime shift1Start = shift1.getStart();
		LocalDateTime shift1End = shift1.getEnd();
		LocalDateTime shift2Start = shift2.getStart();
		LocalDateTime shift2End = shift2.getEnd();
		return (int) Duration.between((shift1Start.isAfter(shift2Start)) ? shift1Start : shift2Start,
				(shift1End.isBefore(shift2End)) ? shift1End : shift2End).toMinutes();
	}

	@Override
	public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
		return new Constraint[] {
				// Hard constraints
				requiredSkill(constraintFactory),
				noOverlappingShifts(constraintFactory),
				minimumBreak8Hours(constraintFactory),
				shortenedBreakCompensated(constraintFactory),
				oneShiftPerDay(constraintFactory),
				unavailableEmployee(constraintFactory),
				// Soft constraints
				undesiredDayForEmployee(constraintFactory),
				desiredDayForEmployee(constraintFactory),
				balanceEmployeeShiftAssignments(constraintFactory),
				shortenedBreak(constraintFactory),
				sameLocationAsYesterday(constraintFactory),
				carpoolGroup(constraintFactory),
				balanceShiftTypes(constraintFactory)
		};
	}

	Constraint requiredSkill(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.filter(shift -> !shift.getEmployee().getSkills().containsAll(shift.getRequiredSkills()))
				.penalize(HardSoftBigDecimalScore.ofHard(BigDecimal.valueOf(Math.pow(2, config.getRequiredSkillPenalty()))))
				.asConstraint("Missing required skills");
	}

	Constraint noOverlappingShifts(ConstraintFactory constraintFactory) {
		return constraintFactory.forEachUniquePair(Shift.class, equal(Shift::getEmployee),
				overlapping(Shift::getStart, Shift::getEnd))
				.penalize(HardSoftBigDecimalScore.ofHard(BigDecimal.valueOf(Math.pow(2, config.getNoOverlappingShiftsPenalty()))),
						EmployeeSchedulingConstraintProvider::getMinuteOverlap)
				.asConstraint("Overlapping shift");
	}

	Constraint minimumBreak8Hours(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.join(Shift.class, equal(Shift::getEmployee), lessThanOrEqual(Shift::getEnd, Shift::getStart))
				.filter((firstShift,
						secondShift) -> Duration.between(firstShift.getEnd(), secondShift.getStart()).toHours() < 8)
				.penalize(HardSoftBigDecimalScore.ofHard(BigDecimal.valueOf(Math.pow(2, config.getMinimumBreak8HoursPenalty()))),
						(firstShift, secondShift) -> {
							int breakLength = (int) Duration.between(firstShift.getEnd(), secondShift.getStart())
									.toMinutes();
							return (8 * 60) - breakLength;
						})
				.asConstraint("At least 8 hours between 2 shifts");
	}

	Constraint shortenedBreakCompensated(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.join(Shift.class, equal(Shift::getEmployee), lessThanOrEqual(Shift::getEnd, Shift::getStart))
				.join(Shift.class, equal((firstShift, secondShift) -> firstShift.getEmployee(), Shift::getEmployee),
						lessThanOrEqual((firstShift, secondShift) -> secondShift.getEnd(), Shift::getStart))
				.filter((firstShift, secondShift, thirdShift) -> {
					long firstBreakMinutes = Duration.between(firstShift.getEnd(), secondShift.getStart()).toMinutes();
					long secondBreakMinutes = Duration.between(secondShift.getEnd(), thirdShift.getStart()).toMinutes();
					long shortage = (11 * 60) - firstBreakMinutes;
					return firstBreakMinutes < 11 * 60 && firstBreakMinutes >= 8 * 60
							&& secondBreakMinutes < (11 * 60 + shortage);
				})
				.penalize(HardSoftBigDecimalScore.ofHard(BigDecimal.valueOf(Math.pow(2, config.getShortenedBreakCompensatedPenalty()))), (firstShift, secondShift, thirdShift) -> {
					long firstBreakMinutes = Duration.between(firstShift.getEnd(), secondShift.getStart()).toMinutes();
					long secondBreakMinutes = Duration.between(secondShift.getEnd(), thirdShift.getStart()).toMinutes();
					long shortage = (11 * 60) - firstBreakMinutes;
					return (int) (shortage - (secondBreakMinutes - 11 * 60));
				})
				.asConstraint("Shortened break must be compensated by next break");
	}

	Constraint oneShiftPerDay(ConstraintFactory constraintFactory) {
		return constraintFactory.forEachUniquePair(Shift.class, equal(Shift::getEmployee),
				equal(shift -> shift.getStart().toLocalDate()))
				.penalize(HardSoftBigDecimalScore.ofHard(BigDecimal.valueOf(Math.pow(2, config.getOneShiftPerDayPenalty()))))
				.asConstraint("Max one shift per day");
	}

	Constraint unavailableEmployee(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.join(Employee.class, equal(Shift::getEmployee, Function.identity()))
				.flattenLast(Employee::getUnavailableDates)
				.filter(Shift::isOverlappingWithDate)
				.penalize(HardSoftBigDecimalScore.ofHard(BigDecimal.valueOf(Math.pow(2, config.getUnavailableEmployeePenalty()))), Shift::getOverlappingDurationInMinutes)
				.asConstraint("Unavailable employee");
	}

	Constraint undesiredDayForEmployee(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.join(Employee.class, equal(Shift::getEmployee, Function.identity()))
				.flattenLast(Employee::getUndesiredDates)
				.filter(Shift::isOverlappingWithDate)
				.penalize(HardSoftBigDecimalScore.ofSoft(BigDecimal.valueOf(Math.pow(2, config.getUndesiredDayForEmployeePenalty()))), Shift::getOverlappingDurationInMinutes)
				.asConstraint("Undesired day for employee");
	}

	Constraint desiredDayForEmployee(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.join(Employee.class, equal(Shift::getEmployee, Function.identity()))
				.flattenLast(Employee::getDesiredDates)
				.filter(Shift::isOverlappingWithDate)
				.reward(HardSoftBigDecimalScore.ofSoft(BigDecimal.valueOf(Math.pow(2, config.getDesiredDayForEmployeeReward()))), Shift::getOverlappingDurationInMinutes)
				.asConstraint("Desired day for employee");
	}

	Constraint balanceEmployeeShiftAssignments(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.groupBy(Shift::getEmployee, ConstraintCollectors.sum(Shift::getWorktime))
				.complement(Employee.class, e -> 0) // Include all employees which are not assigned to any shift.
				.groupBy(ConstraintCollectors.loadBalance((employee, totalWorktime) -> employee,
						(employee, totalWorktime) -> totalWorktime))
				.penalizeBigDecimal(HardSoftBigDecimalScore.ofSoft(BigDecimal.valueOf(Math.pow(2, config.getBalanceEmployeeShiftAssignmentsPenalty()))), LoadBalance::unfairness)
				.asConstraint("Balance employee work time assignments");
	}

	Constraint shortenedBreak(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.join(Shift.class, equal(Shift::getEmployee), lessThanOrEqual(Shift::getEnd, Shift::getStart))
				.filter((firstShift,
						secondShift) -> Duration.between(firstShift.getEnd(), secondShift.getStart()).toHours() < 11)
				.penalize(HardSoftBigDecimalScore.ofSoft(BigDecimal.valueOf(Math.pow(2, config.getShortenedBreakPenalty()))), (firstShift, secondShift) -> {
					int breakLength = (int) Duration.between(firstShift.getEnd(), secondShift.getStart()).toMinutes();
					return (11 * 60) - breakLength;
				})
				.asConstraint("Shortened break");
	}

	Constraint sameLocationAsYesterday(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.join(Shift.class, equal(Shift::getEmployee), lessThanOrEqual(Shift::getEnd, Shift::getStart))
				.filter((firstShift, secondShift) -> Duration.between(firstShift.getEnd(), secondShift.getStart()).toHours() <= 18 &&
						firstShift.getLocation().equals(secondShift.getLocation()))
				.reward(HardSoftBigDecimalScore.ofSoft(BigDecimal.valueOf(Math.pow(2, config.getSameLocationAsYesterdayReward()))), 
						(firstShift, secondShift) -> (int) Duration.between(firstShift.getStart(), firstShift.getEnd()).toMinutes() +
						(int) Duration.between(secondShift.getStart(), secondShift.getEnd()).toMinutes())
				.asConstraint("Same location as yesterday");
	}

	Constraint carpoolGroup(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				// Group shifts by the carpool group of the employee
				.groupBy(shift -> shift.getEmployee().getCarpoolGroup(), ConstraintCollectors.toList())
				// Filter to ensure there are at least 2 employees in the group and carpoolGroup is not null
				.filter((carpoolGroup, shifts) -> carpoolGroup != null && shifts.size() > 1)
				// Reward based on the total minutes of matched shifts duration
				.reward(HardSoftBigDecimalScore.ofSoft(BigDecimal.valueOf(Math.pow(2, config.getCarpoolGroupReward()))), (carpoolGroup, shifts) -> {
					// Calculate the total duration of shifts that start and end at the same time
					long totalMatchingMinutes = shifts.stream()
							.filter(shift -> shift.getStart().equals(shifts.get(0).getStart())
									&& shift.getEnd().equals(shifts.get(0).getEnd()))
							.mapToLong(shift -> Duration.between(shift.getStart(), shift.getEnd()).toMinutes())
							.sum();
					return (int) totalMatchingMinutes;
				})
				.asConstraint("Carpool group shifts start and end at the same time");
	}

	Constraint balanceShiftTypes(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.groupBy(Shift::getShiftType, ConstraintCollectors.count())
				.join(constraintFactory.forEach(Employee.class))
				.groupBy((shiftType, count, employee) -> employee, ConstraintCollectors.toMap((shiftType, count, employee) -> shiftType, (shiftType, count, employee) -> count, Integer::sum))
				.penalizeBigDecimal(HardSoftBigDecimalScore.ofSoft(BigDecimal.valueOf(Math.pow(2, config.getBalanceShiftTypesPenalty()))), 
						(employee, shiftTypeCount) -> {
							long totalShifts = shiftTypeCount.values().stream().mapToLong(Integer::longValue).sum();
							long expectedShiftsPerType = totalShifts / shiftTypeCount.size();
							long imbalance = shiftTypeCount.values().stream()
									.mapToLong(count -> Math.abs(count - expectedShiftsPerType))
									.sum();
							return BigDecimal.valueOf(imbalance);
						})
				.asConstraint("Balance shift types among employees");
	}
}
