package org.acme.employeescheduling.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class Shift {
    @PlanningId
    private String id; // optional
    private LocalDateTime start;
    private LocalDateTime end;
    private String location;
	private String shiftType = null; // optional
    private Set<String> requiredSkills = Set.of(); // optional
    private Integer worktime = (int) Duration.between(start, end).toMinutes(); // optional

    @PlanningVariable
    private Employee employee;

    // Pin the shift to prevent it from being changed
    @PlanningPin
    private boolean pinned = false;

    public Shift() {
    }
	
	public Shift(LocalDateTime start, LocalDateTime end, String location) {
		this(start, end, location, Set.of());
	}

    public Shift(LocalDateTime start, LocalDateTime end, String location, Set<String> requiredSkills) {
        this(start, end, location, requiredSkills, null);
    }

    public Shift(LocalDateTime start, LocalDateTime end, String location, Set<String> requiredSkills, Employee employee) {
        this(null, start, end, location, requiredSkills, employee, (int) Duration.between(start, end).toMinutes());
    }

    public Shift(String id, LocalDateTime start, LocalDateTime end, String location, Set<String> requiredSkills, Employee employee) {
        this(id, start, end, location, requiredSkills, employee, (int) Duration.between(start, end).toMinutes());
    }

	public Shift(String id, LocalDateTime start, LocalDateTime end, String location, Set<String> requiredSkills, Employee employee, int worktime) {
		this(id, start, end, location, requiredSkills, employee, worktime, null);
	}
    public Shift(String id, LocalDateTime start, LocalDateTime end, String location, Set<String> requiredSkills, Employee employee, int worktime, String shiftType) {
        this.id = id;
        this.start = start;
        this.end = end;
        this.location = location;
        this.requiredSkills = requiredSkills;
        this.employee = employee;
        this.worktime = worktime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public void setStart(LocalDateTime start) {
        this.start = start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public void setEnd(LocalDateTime end) {
        this.end = end;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getShiftType() {
		return shiftType != null ? shiftType : "not defined";
    }

    public void setShiftType(String shiftType) {
        this.shiftType = shiftType;
    }

    public Set<String> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(Set<String> requiredSkills) {
        this.requiredSkills = requiredSkills;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Integer getWorktime() {
        return worktime;
    }

    public void setWorktime(Integer worktime) {
        this.worktime = worktime != null ? worktime : (int) Duration.between(start, end).toMinutes();
    }

    public boolean isOverlappingWithDate(LocalDate date) {
        return getStart().toLocalDate().equals(date) || getEnd().toLocalDate().equals(date);
    }

    public int getOverlappingDurationInMinutes(LocalDate date) {
        LocalDateTime startDateTime = LocalDateTime.of(date, LocalTime.MIN);
        LocalDateTime endDateTime = LocalDateTime.of(date, LocalTime.MAX);
        return getOverlappingDurationInMinutes(startDateTime, endDateTime, getStart(), getEnd());
    }

    private int getOverlappingDurationInMinutes(LocalDateTime firstStartDateTime, LocalDateTime firstEndDateTime,
            LocalDateTime secondStartDateTime, LocalDateTime secondEndDateTime) {
        LocalDateTime maxStartTime = firstStartDateTime.isAfter(secondStartDateTime) ? firstStartDateTime : secondStartDateTime;
        LocalDateTime minEndTime = firstEndDateTime.isBefore(secondEndDateTime) ? firstEndDateTime : secondEndDateTime;
        long minutes = maxStartTime.until(minEndTime, ChronoUnit.MINUTES);
        return minutes > 0 ? (int) minutes : 0;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    @Override
    public String toString() {
        return location + " " + start + "-" + end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Shift shift)) {
            return false;
        }
        return Objects.equals(getId(), shift.getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }
}
