package de.tum.bgu.msm.run.models;

import de.tum.bgu.msm.container.DataContainer;
import de.tum.bgu.msm.data.dwelling.Dwelling;
import de.tum.bgu.msm.data.dwelling.RealEstateDataManager;
import de.tum.bgu.msm.data.household.Household;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.data.person.Person;
import de.tum.bgu.msm.run.data.person.PersonBkk;
import de.tum.bgu.msm.events.impls.household.MigrationEvent;
import de.tum.bgu.msm.models.autoOwnership.CreateCarOwnershipModel;
import de.tum.bgu.msm.models.demography.driversLicense.DriversLicenseModel;
import de.tum.bgu.msm.models.demography.employment.EmploymentModel;
import de.tum.bgu.msm.models.relocation.migration.InOutMigration;
import de.tum.bgu.msm.models.relocation.migration.InOutMigrationImpl;
import de.tum.bgu.msm.models.relocation.moves.MovesModelImpl;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.schools.*;
import de.tum.bgu.msm.utils.SiloUtil;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;

import java.util.Collection;
import java.util.Random;

public class InOutMigrationBkk implements InOutMigration {

  private static final Logger logger = Logger.getLogger(InOutMigrationBkk.class);
  private InOutMigrationBkkImpl delegate;
  private DataContainerWithSchoolsImpl dataContainerWithSchoolsImpl;

  public InOutMigrationBkk(DataContainer dataContainer, EmploymentModel employment,
                           MovesModelImpl movesModel, CreateCarOwnershipModel carOwnership,
                           DriversLicenseModel driversLicense, Properties properties) {
    delegate = new InOutMigrationBkkImpl(dataContainer, employment, movesModel,
      carOwnership, driversLicense, properties, SiloUtil.provideNewRandom());
    dataContainerWithSchoolsImpl = (DataContainerWithSchoolsImpl) dataContainer;
  }


  @Override
  public void setup() {
    delegate.setup();
  }

  @Override
  public void prepareYear(int year) {
    delegate.prepareYear(year);
  }

  @Override
  public Collection<MigrationEvent> getEventsForCurrentYear(int year) {
    return delegate.getEventsForCurrentYear(year);
  }

  @Override
  public boolean handleEvent(MigrationEvent event) {
    boolean success = delegate.handleEvent(event);
    if(success) {
      Household Household = event.getHousehold();
      for (Person person : Household.getPersons().values()) {
        if (person.getOccupation().equals(Occupation.STUDENT)) {
//          logger.warn("person id " + person.getId() + " Age: " +person.getAge()+" Occupation: "+ person.getOccupation().name());
          if (event.getType().equals(MigrationEvent.Type.IN)) {
            //SchoolType is duplicated from original person
            int SchoolType = -1;
            School oldSchool = null;
            if(((PersonBkk)person).getSchoolId()> 0){
              oldSchool = dataContainerWithSchoolsImpl.getSchoolData().getSchoolFromId(((PersonBkk) person).getSchoolId());
              SchoolType = oldSchool.getType();
            }else if(((PersonBkk)person).getSchoolId()== -2){
              SchoolType = SchoolDataImpl.guessSchoolType((PersonWithSchool) person);
            }
            if(SchoolType>0) {
              School newSchool = dataContainerWithSchoolsImpl.getSchoolData().getClosestSchool(person, SchoolType);
              if (newSchool.getCapacity() - newSchool.getOccupancy() <= 0) {
                RealEstateDataManager realEstateData = dataContainerWithSchoolsImpl.getRealEstateDataManager();
                double nearest_distance = 99999;
                double searchDistance= 30000; //30km
                double criteriaDistance= 10000; //10km
                if(SchoolType==3){
                  searchDistance=100000; //100km
                  criteriaDistance=20000;
                }
                for (School ss : dataContainerWithSchoolsImpl.getSchoolData().getNearSchool(person, SchoolType, searchDistance)) {
                  Coordinate schoolCoordinate = ((SchoolImpl) ss).getCoordinate();
                  Dwelling dwelling = realEstateData.getDwelling(person.getHousehold().getDwellingId());
                  Coordinate dwellingCoordinate = dwelling.getCoordinate();
                  double distance = dwellingCoordinate.distance(schoolCoordinate);
                  int remainingCapacity = (int)Math.round(ss.getCapacity()+ss.getCapacity()*0.1) -ss.getOccupancy();
                  if (remainingCapacity > 0) {
                    if (distance < nearest_distance) {
                      nearest_distance = distance;
                      newSchool = ss;
                      Random rand = SiloUtil.getRandomObject();
                      if((distance<=criteriaDistance && rand.nextDouble()<=0.8)  || rand.nextDouble()<=0.2){
                        break;
                      }
                    }

                  }
                }
              }
              if(oldSchool!=null){
                oldSchool.setOccupancy(oldSchool.getOccupancy() - 1);
              }
              ((PersonBkk) person).setSchoolId(newSchool.getId());
              newSchool.setOccupancy(newSchool.getOccupancy() + 1);
            }
          } else if (event.getType().equals(MigrationEvent.Type.OUT)) {
            if(((PersonBkk)person).getSchoolId()> 0) {
              School school = dataContainerWithSchoolsImpl.getSchoolData().getSchoolFromId(((PersonBkk) person).getSchoolId());
              school.setOccupancy(school.getOccupancy() - 1);
            }else{
              logger.info("person id " + person.getId()+" has school id: " + ((PersonBkk) person).getSchoolId() + ". Person has a school outside study area or has no school assigned. " +person.getAge()+" Occupation: "+ person.getOccupation().name());
            }
          }
        }
      }
    }
    return success;
  }

  @Override
  public boolean outMigrateHh(int hhId, boolean overwriteEventRules) {
    return delegate.outMigrateHh(hhId, overwriteEventRules);
  }

  @Override
  public void endYear(int year) {
    delegate.endYear(year);
  }

  @Override
  public void endSimulation() {
    delegate.endSimulation();
  }
}
