package org.acme.employeescheduling.solver;

import static ai.timefold.solver.core.api.score.stream.Joiners.equal;
import static ai.timefold.solver.core.api.score.stream.Joiners.lessThanOrEqual;
import static ai.timefold.solver.core.api.score.stream.Joiners.overlapping;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;

import ai.timefold.solver.core.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.common.LoadBalance;

import org.acme.employeescheduling.domain.Employee;
import org.acme.employeescheduling.domain.Shift;

public class EmployeeSchedulingConstraintProvider implements ConstraintProvider {

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
				carpoolGroup(constraintFactory)
		};
	}

	Constraint requiredSkill(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.filter(shift -> !shift.getEmployee().getSkills().containsAll(shift.getRequiredSkills()))
				.penalize(HardSoftBigDecimalScore.ONE_HARD)
				.asConstraint("Missing required skills");
	}

	Constraint noOverlappingShifts(ConstraintFactory constraintFactory) {
		return constraintFactory.forEachUniquePair(Shift.class, equal(Shift::getEmployee),
				overlapping(Shift::getStart, Shift::getEnd))
				.penalize(HardSoftBigDecimalScore.ONE_HARD,
						EmployeeSchedulingConstraintProvider::getMinuteOverlap)
				.asConstraint("Overlapping shift");
	}

	Constraint minimumBreak8Hours(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.join(Shift.class, equal(Shift::getEmployee), lessThanOrEqual(Shift::getEnd, Shift::getStart))
				.filter((firstShift,
						secondShift) -> Duration.between(firstShift.getEnd(), secondShift.getStart()).toHours() < 8)
				.penalize(HardSoftBigDecimalScore.ONE_HARD,
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
				.penalize(HardSoftBigDecimalScore.ONE_HARD, (firstShift, secondShift, thirdShift) -> {
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
				.penalize(HardSoftBigDecimalScore.ONE_HARD)
				.asConstraint("Max one shift per day");
	}

	Constraint unavailableEmployee(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.join(Employee.class, equal(Shift::getEmployee, Function.identity()))
				.flattenLast(Employee::getUnavailableDates)
				.filter(Shift::isOverlappingWithDate)
				.penalize(HardSoftBigDecimalScore.ONE_HARD, Shift::getOverlappingDurationInMinutes)
				.asConstraint("Unavailable employee");
	}

	Constraint undesiredDayForEmployee(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.join(Employee.class, equal(Shift::getEmployee, Function.identity()))
				.flattenLast(Employee::getUndesiredDates)
				.filter(Shift::isOverlappingWithDate)
				.penalize(HardSoftBigDecimalScore.ONE_SOFT, Shift::getOverlappingDurationInMinutes)
				.asConstraint("Undesired day for employee");
	}

	Constraint desiredDayForEmployee(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.join(Employee.class, equal(Shift::getEmployee, Function.identity()))
				.flattenLast(Employee::getDesiredDates)
				.filter(Shift::isOverlappingWithDate)
				.reward(HardSoftBigDecimalScore.ONE_SOFT, Shift::getOverlappingDurationInMinutes)
				.asConstraint("Desired day for employee");
	}

	Constraint balanceEmployeeShiftAssignments(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.groupBy(Shift::getEmployee, ConstraintCollectors.count())
				.complement(Employee.class, e -> 0) // Include all employees which are not assigned to any shift.c
				.groupBy(ConstraintCollectors.loadBalance((employee, shiftCount) -> employee,
						(employee, shiftCount) -> shiftCount))
				.penalizeBigDecimal(HardSoftBigDecimalScore.ONE_SOFT, LoadBalance::unfairness)
				.asConstraint("Balance employee shift assignments");
	}

	Constraint shortenedBreak(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.join(Shift.class, equal(Shift::getEmployee), lessThanOrEqual(Shift::getEnd, Shift::getStart))
				.filter((firstShift,
						secondShift) -> Duration.between(firstShift.getEnd(), secondShift.getStart()).toHours() < 11)
				.penalize(HardSoftBigDecimalScore.ONE_SOFT, (firstShift, secondShift) -> {
					int breakLength = (int) Duration.between(firstShift.getEnd(), secondShift.getStart()).toMinutes();
					return (11 * 60) - breakLength;
				})
				.asConstraint("Shortened break");
	}

	Constraint sameLocationAsYesterday(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				.join(Shift.class, equal(Shift::getEmployee), lessThanOrEqual(Shift::getEnd, Shift::getStart))
				.filter((firstShift,
						secondShift) -> Duration.between(firstShift.getEnd(), secondShift.getStart()).toHours() <= 18 &&
								firstShift.getLocation().equals(secondShift.getLocation()))
				.reward(HardSoftBigDecimalScore.ONE_SOFT)
				.asConstraint("Same location as yesterday");
	}

	Constraint carpoolGroup(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(Shift.class)
				// Group shifts by the carpool group of the employee
				.groupBy(shift -> shift.getEmployee().getCarpoolGroup(), ConstraintCollectors.toList())
				// Filter to ensure there are at least 2 employees in the group
				.filter((carpoolGroup, shifts) -> shifts.size() > 1)
				// Reward based on the number of employees that match the start and end times
				.reward(HardSoftBigDecimalScore.ONE_SOFT, (carpoolGroup, shifts) -> {
					long matchingShifts = shifts.stream()
							.filter(shift -> shift.getStart().equals(shifts.get(0).getStart())
									&& shift.getEnd().equals(shifts.get(0).getEnd()))
							.count();
					return (int) matchingShifts;
				})
				.asConstraint("Carpool group shifts start and end at the same time");
	}
}
