package de.tum.bgu.msm.schools;

import de.tum.bgu.msm.data.person.Person;
import de.tum.bgu.msm.simulator.UpdateListener;

import java.util.Collection;

public interface SchoolData extends UpdateListener {
    void addSchool(School ss);

    School getSchoolFromId(int id);

    Collection<School> getSchools();

    School getClosestSchool(Person person, int schoolType);

    Collection<School> getNearSchool(Person person, int schoolType, double distance);

    void removeSchool(int id);
}
