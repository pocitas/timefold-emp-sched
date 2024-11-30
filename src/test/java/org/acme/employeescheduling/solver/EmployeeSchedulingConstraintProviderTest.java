package org.acme.employeescheduling.solver;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Set;

import jakarta.inject.Inject;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;

import org.acme.employeescheduling.domain.Employee;
import org.acme.employeescheduling.domain.EmployeeSchedule;
import org.acme.employeescheduling.domain.Shift;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class EmployeeSchedulingConstraintProviderTest {
    private static final LocalDate DAY_1 = LocalDate.of(2025, 2, 1);
	private static final LocalDate DAY_2 = LocalDate.of(2025, 2, 2);
    private static final LocalDate DAY_3 = LocalDate.of(2025, 2, 3);

    private static final LocalDateTime DAY_START_TIME = DAY_1.atTime(LocalTime.of(9, 0));
    private static final LocalDateTime DAY_END_TIME = DAY_1.atTime(LocalTime.of(17, 0));
    private static final LocalDateTime AFTERNOON_START_TIME = DAY_1.atTime(LocalTime.of(13, 0));
    private static final LocalDateTime AFTERNOON_END_TIME = DAY_1.atTime(LocalTime.of(21, 0));
	private static final LocalDateTime A_START_TIME = DAY_1.atTime(LocalTime.of(10, 0));
	private static final LocalDateTime A_END_TIME = DAY_1.atTime(LocalTime.of(22, 0));
	private static final LocalDateTime D_START_TIME = DAY_1.atTime(LocalTime.of(7, 0));
	private static final LocalDateTime D_END_TIME = DAY_1.atTime(LocalTime.of(19, 0));
	private static final LocalDateTime N_START_TIME = DAY_1.atTime(LocalTime.of(19, 0));
	private static final LocalDateTime N_END_TIME = DAY_2.atTime(LocalTime.of(7, 0));
	

    @Inject
    ConstraintVerifier<EmployeeSchedulingConstraintProvider, EmployeeSchedule> constraintVerifier;

    @Test
    void requiredSkill() {
	// Employee without required skills
	Employee employeeWithoutSkill = new Employee("Amy", Set.of(), null, null, null);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::requiredSkill)
		.given(employeeWithoutSkill,
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employeeWithoutSkill))
		.penalizes(1);

	// Employee with required skills
	Employee employeeWithSkill = new Employee("Bob", Set.of("Skill"), null, null, null);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::requiredSkill)
		.given(employeeWithSkill,
			new Shift("2", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employeeWithSkill))
		.penalizes(0);

	// Employee with only one of the required skills
	Employee employeeWithOneSkill = new Employee("Charlie", Set.of("Skill1"), null, null, null);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::requiredSkill)
		.given(employeeWithOneSkill,
			new Shift("3", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill1", "Skill2"), employeeWithOneSkill))
		.penalizes(1);

	// Employee with more skills than required
	Employee employeeWithMoreSkills = new Employee("David", Set.of("Skill1", "Skill2", "Skill3"), null, null, null);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::requiredSkill)
		.given(employeeWithMoreSkills,
			new Shift("4", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill1", "Skill2"), employeeWithMoreSkills))
		.penalizes(0);
    }

    @Test
    void overlappingShifts() {
	Employee employee1 = new Employee("Amy", null, null, null, null);
	Employee employee2 = new Employee("Beth", null, null, null, null);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::noOverlappingShifts)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee1),
			new Shift("2", DAY_START_TIME, DAY_END_TIME, "Location 2", Set.of("Skill"), employee1))
		.penalizesBy((int) Duration.ofHours(8).toMinutes());

	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::noOverlappingShifts)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee1),
			new Shift("2", DAY_START_TIME, DAY_END_TIME, "Location 2", Set.of("Skill"), employee2))
		.penalizes(0);

	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::noOverlappingShifts)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee1),
			new Shift("2", AFTERNOON_START_TIME, AFTERNOON_END_TIME, "Location 2", Set.of("Skill"), employee1))
		.penalizesBy((int) Duration.ofHours(4).toMinutes());
    }

    @Test
    void oneShiftPerDay() {
	Employee employee1 = new Employee("Amy", null, null, null, null);
	Employee employee2 = new Employee("Beth", null, null, null, null);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::noOverlappingShifts)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee1),
			new Shift("2", DAY_START_TIME, DAY_END_TIME, "Location 2", Set.of("Skill"), employee1))
		.penalizes(1);

	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::noOverlappingShifts)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee1),
			new Shift("2", DAY_START_TIME, DAY_END_TIME, "Location 2", Set.of("Skill"), employee2))
		.penalizes(0);

	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::noOverlappingShifts)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee1),
			new Shift("2", AFTERNOON_START_TIME, AFTERNOON_END_TIME, "Location 2", Set.of("Skill"), employee1))
		.penalizes(1);

	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::noOverlappingShifts)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee1),
			new Shift("2", DAY_START_TIME.plusDays(1), DAY_END_TIME.plusDays(1), "Location 2", Set.of("Skill"), employee1))
		.penalizes(0);
    }

    @Test
    void minimumBreak8Hours() {
	Employee employee1 = new Employee("Amy", null, null, null, null);
	Employee employee2 = new Employee("Beth", null, null, null, null);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::minimumBreak8Hours)
		.given(employee1, employee2,
			// Break 3 hours
			new Shift("1", N_START_TIME, N_END_TIME, "Location", Set.of("Skill"), employee1),
			new Shift("2", A_START_TIME.plusDays(1), A_END_TIME.plusDays(1), "Location 2", Set.of("Skill"), employee1))
		.penalizesBy(300);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::minimumBreak8Hours)
		// Break 0 hours
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee1),
			new Shift("2", DAY_END_TIME, DAY_START_TIME.plusDays(1), "Location 2", Set.of("Skill"), employee1))
		.penalizesBy(480);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::minimumBreak8Hours)
		.given(employee1, employee2,
			// Break 0 hours, switched order
			new Shift("1", DAY_END_TIME, DAY_START_TIME.plusDays(1), "Location", Set.of("Skill"), employee1),
			new Shift("2", DAY_START_TIME, DAY_END_TIME, "Location 2", Set.of("Skill"), employee1))
		.penalizesBy(480);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::minimumBreak8Hours)
		.given(employee1, employee2,
			// Break 8 hours
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee1),
			new Shift("2", DAY_END_TIME.plusHours(8), DAY_START_TIME.plusDays(1), "Location 2", Set.of("Skill"),
				employee1))
		.penalizes(0);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::minimumBreak8Hours)
		.given(employee1, employee2,
			// Different employees
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee1),
			new Shift("2", AFTERNOON_END_TIME, DAY_START_TIME.plusDays(1), "Location 2", Set.of("Skill"), employee2))
		.penalizes(0);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::minimumBreak8Hours)
		.given(employee1, employee2,
			// Break 16 hours
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee1),
			new Shift("2", DAY_START_TIME.plusDays(1), DAY_END_TIME.plusDays(1), "Location 2", Set.of("Skill"), employee1))
		.penalizes(0);
    }

    @Test
    void unavailableEmployee() {
	Employee employee1 = new Employee("Amy", null, Set.of(DAY_1, DAY_3), null, null);
	Employee employee2 = new Employee("Beth", null, Set.of(), null, null);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::unavailableEmployee)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee1))
		.penalizesBy((int) Duration.ofHours(8).toMinutes());
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::unavailableEmployee)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME.minusDays(1), DAY_END_TIME, "Location", Set.of("Skill"), employee1))
		.penalizesBy((int) Duration.ofHours(17).toMinutes());
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::unavailableEmployee)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME.plusDays(1), DAY_END_TIME.plusDays(1), "Location", Set.of("Skill"), employee1))
		.penalizes(0);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::unavailableEmployee)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee2))
		.penalizes(0);
    }

    @Test
    void undesiredDayForEmployee() {
	Employee employee1 = new Employee("Amy", null, null, Set.of(DAY_1, DAY_3), null);
	Employee employee2 = new Employee("Beth", null, null, Set.of(), null);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::undesiredDayForEmployee)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee1))
		.penalizesBy((int) Duration.ofHours(8).toMinutes());
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::undesiredDayForEmployee)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME.minusDays(1), DAY_END_TIME, "Location", Set.of("Skill"), employee1))
		.penalizesBy((int) Duration.ofHours(17).toMinutes());
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::undesiredDayForEmployee)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME.plusDays(1), DAY_END_TIME.plusDays(1), "Location", Set.of("Skill"), employee1))
		.penalizes(0);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::undesiredDayForEmployee)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee2))
		.penalizes(0);
    }

    @Test
    void desiredDayForEmployee() {
	Employee employee1 = new Employee("Amy", null, null, null, Set.of(DAY_1, DAY_3));
	Employee employee2 = new Employee("Beth", null, null, null, Set.of());
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::desiredDayForEmployee)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee1))
		.rewardsWith((int) Duration.ofHours(8).toMinutes());
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::desiredDayForEmployee)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME.minusDays(1), DAY_END_TIME, "Location", Set.of("Skill"), employee1))
		.rewardsWith((int) Duration.ofHours(17).toMinutes());
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::desiredDayForEmployee)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME.plusDays(1), DAY_END_TIME.plusDays(1), "Location", Set.of("Skill"), employee1))
		.rewards(0);
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::desiredDayForEmployee)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME, DAY_END_TIME, "Location", Set.of("Skill"), employee2))
		.rewards(0);
    }

    @Test
    void balanceEmployeeShiftAssignments() {
	Employee employee1 = new Employee("Amy", null, null, null, Collections.emptySet());
	Employee employee2 = new Employee("Beth", null, null, null, Collections.emptySet());
	// No employees have shifts assigned; the schedule is perfectly balanced.
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::balanceEmployeeShiftAssignments)
		.given(employee1, employee2)
		.penalizesBy(0);
	// Only one employee has shifts assigned; the schedule is less balanced.
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::balanceEmployeeShiftAssignments)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME.minusDays(1), DAY_END_TIME, "Location", Set.of("Skill"), employee1))
		.penalizesByMoreThan(0);
	// Every employee has a shift assigned; the schedule is once again perfectly balanced.
	constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::balanceEmployeeShiftAssignments)
		.given(employee1, employee2,
			new Shift("1", DAY_START_TIME.minusDays(1), DAY_END_TIME, "Location", Set.of("Skill"), employee1),
			new Shift("2", DAY_START_TIME.minusDays(1), DAY_END_TIME, "Location", Set.of("Skill"), employee2))
		.penalizesBy(0);

    }
}
