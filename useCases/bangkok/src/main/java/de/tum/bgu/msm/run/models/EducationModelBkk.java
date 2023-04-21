package de.tum.bgu.msm.run.models;

import de.tum.bgu.msm.data.dwelling.Dwelling;
import de.tum.bgu.msm.data.dwelling.RealEstateDataManager;
import de.tum.bgu.msm.schools.*;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.data.person.Person;
import de.tum.bgu.msm.run.data.person.PersonBkk;
import de.tum.bgu.msm.events.impls.person.EducationEvent;
import de.tum.bgu.msm.models.demography.education.EducationModel;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.utils.SiloUtil;
import de.tum.bgu.msm.models.AbstractModel;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Simulates if someone changes school
 * Author: Rolf Moeckel, TUM and Ana Moreno, TUM
 * Created on 13 October 2017 in Cape Town, South Africa
 * Edited on 05 October 2018 in Munich by Qin Zhang, TUM
 **/
public class EducationModelBkk extends AbstractModel implements EducationModel {

    private static final Logger logger = Logger.getLogger(EducationModelBkk.class);
    private static final int MIN_PRIMARY_AGE = 6;
    private static final int MIN_SECONDARY_AGE = 11;
    private static final int MIN_TERTIARY_AGE = 18;
    private static final int MAX_EDUCATION_AGE = 24;

    public EducationModelBkk(DataContainerWithSchools dataContainer, Properties properties, Random rnd) {
        super(dataContainer, properties, rnd);
    }

    @Override
    public void setup() {

    }

    @Override
    public void prepareYear(int year) {

    }

    @Override
    public void endYear(int year) {

    }

    @Override
    public void endSimulation() {

    }

    @Override
    public Collection<EducationEvent> getEventsForCurrentYear(int year) {
        //TODO: Realschuln and Gymnasien
        //TODO: Hard code age and probability or set in the properties?
        final List<EducationEvent> events = new ArrayList<>();
        for(Person p: dataContainer.getHouseholdDataManager().getPersons()) {
            Occupation occupation = p.getOccupation();
            if(!(occupation==Occupation.TODDLER || occupation==Occupation.STUDENT)){
              continue;
            }

            PersonBkk person = (PersonBkk) p;
            switch (occupation){
                case TODDLER:
                    if (person.getAge()>= MIN_PRIMARY_AGE){
                        events.add(new EducationEvent(person.getId()));
                    }
                    break;
                case STUDENT:
                    int oldSchoolType = -1;
                    if(person.getSchoolId()> 0){
                        oldSchoolType = ((DataContainerWithSchoolsImpl)dataContainer).getSchoolData().getSchoolFromId(person.getSchoolId()).getType();
                    }else if(person.getSchoolId()== -2){
                        oldSchoolType = SchoolDataImpl.guessSchoolType(person);
                    }else{
                        logger.warn("person id " + person.getId()+" has no school." + " Age: " +person.getAge()+" Occupation: "+ person.getOccupation().name());
                        continue;
                    }

                    if (person.getAge() > MIN_SECONDARY_AGE && oldSchoolType == 1) {
                      //highSchool
                        events.add(new EducationEvent(person.getId()));
                    }else if (person.getAge() > MIN_TERTIARY_AGE && oldSchoolType == 2) {
                      //university
                        events.add(new EducationEvent(person.getId()));
                    }else if (person.getAge() > MAX_EDUCATION_AGE && oldSchoolType == 3){
                      //work
                        events.add(new EducationEvent(person.getId()));
                    }
                    break;
            }
        }
        return events;
    }

    @Override
    public boolean handleEvent(EducationEvent event) {
        Person p = dataContainer.getHouseholdDataManager().getPersonFromId(event.getPersonId());
        if (p != null) {
        Occupation occupation = p.getOccupation();
        if(!(occupation==Occupation.TODDLER || occupation==Occupation.STUDENT)){
          return false;
        }
        PersonBkk pp = (PersonBkk) p;
            School newSchool = null;
            switch (occupation) {
                case TODDLER:
                    newSchool = findSchool(pp, 1);
                    break;
                case STUDENT:
                    int currentSchoolType = -1;
                    if(pp.getSchoolId()== -2) {
                        currentSchoolType = pp.getSchoolType();
                    }else{
                        currentSchoolType = ((DataContainerWithSchoolsImpl)dataContainer).getSchoolData().getSchoolFromId(pp.getSchoolId()).getType();
                    }

                    if (currentSchoolType == 3) {
                        return leaveSchoolToWork(pp);
                    } else {
                        School selectedSchool = findSchool(pp, currentSchoolType + 1);
                        newSchool = selectedSchool;
//                        if(selectedSchool.getOccupancy()<selectedSchool.getCapacity()){
//                            newSchool = selectedSchool;
//                        }else{
//                          RealEstateDataManager realEstateData = dataContainer.getRealEstateDataManager();
//                          double nearest_distance = 99999;
//                          double searchDistance= 30000; //30km
//                          double criteriaDistance= 10000; //10km
//                          if(currentSchoolType+1==3){
//                            searchDistance=100000; //100km
//                            criteriaDistance=20000;
//                          }
//                          for (School ss : findSchools(pp, currentSchoolType+1, searchDistance)) {
//                            Coordinate schoolCoordinate = ((SchoolImpl) ss).getCoordinate();
//                            Dwelling dwelling = realEstateData.getDwelling(pp.getHousehold().getDwellingId());
//                            Coordinate dwellingCoordinate = dwelling.getCoordinate();
//                            double distance = dwellingCoordinate.distance(schoolCoordinate);
//                            int remainingCapacity = (int)Math.round(ss.getCapacity()+(ss.getCapacity()*0.1)) -ss.getOccupancy();
//                            if (remainingCapacity > 0) {
//                              if(distance < nearest_distance){
//                                nearest_distance=distance;
//                                newSchool = ss;
//                              }
//                              Random rand = SiloUtil.getRandomObject();
//                              if((distance<=criteriaDistance && rand.nextDouble()<=0.8)  || rand.nextDouble()<=0.2){
//                                break;
//                              }
//                            }
//                          }
//                        }
                    }
                    break;
                default:
                    //logger.warn("person id " + pp.getId() + " couldn't handle update education event, because occupation doesn't fit anymore." +
                    //        " Age: " + pp.getAge() + " Occupation: " + pp.getOccupation().name());
                    return false;
            }

            if (newSchool != null) {
                return updateEducation(pp, newSchool);
            } else {
                logger.warn("person id " + pp.getId() + " cannot find a new school." +
                        " Age: " + pp.getAge() + " Current school id: " +
                        pp.getSchoolId() + " Home zone id: " +
                        dataContainer.getRealEstateDataManager().getDwelling(pp.getHousehold().getDwellingId()).getZoneId());
            }

        }
        return false;
    }

    boolean updateEducation(PersonBkk person, School school) {
        if(person.getSchoolId()>0){
          School oldSchool = ((DataContainerWithSchoolsImpl)dataContainer).getSchoolData().getSchoolFromId(person.getSchoolId());
          if(oldSchool!=null) {
            oldSchool.setOccupancy(oldSchool.getOccupancy() - 1);
          }
        }
        person.setSchoolId(school.getId());
        person.setOccupation(Occupation.STUDENT);
        school.setOccupancy(school.getOccupancy()+1);

        if (person.getId() == SiloUtil.trackPp) {
            SiloUtil.trackWriter.println("Person " + person.getId() +
                    " changed school. New school id " + school.getId());
        }
        return true;
    }

    boolean leaveSchoolToWork(PersonBkk person) {

        person.setOccupation(Occupation.UNEMPLOYED);

        if(person.getSchoolId()== -2) {
            person.setSchoolId(-1);
            person.setSchoolType(-1);
        }else{
            School school = ((DataContainerWithSchoolsImpl)dataContainer).getSchoolData().getSchoolFromId(person.getSchoolId());
            school.setOccupancy(school.getOccupancy() - 1);
            person.setSchoolId(-1);
        }

        if (person.getId() == SiloUtil.trackPp) {
            SiloUtil.trackWriter.println("Person " + person.getId() +
                    " leaved from school to job market. ");
        }
        return true;
    }

    public School findSchool(Person person, int schoolType) {
        return ((DataContainerWithSchoolsImpl)dataContainer).getSchoolData().getClosestSchool(person, schoolType);
    }

    public Collection<School> findSchools(Person person, int schoolType, double distance) {
      return ((DataContainerWithSchoolsImpl)dataContainer).getSchoolData().getNearSchool(person, schoolType, distance);
    }
}
