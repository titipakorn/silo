package de.tum.bgu.msm.syntheticPopulationGenerator.bangkok.microlocation;

import de.tum.bgu.msm.data.Zone;
import de.tum.bgu.msm.data.dwelling.Dwelling;
import de.tum.bgu.msm.data.dwelling.RealEstateDataManager;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.data.person.Person;
import de.tum.bgu.msm.run.data.person.PersonBkk;
import de.tum.bgu.msm.schools.*;
import de.tum.bgu.msm.syntheticPopulationGenerator.DataSetSynPop;
import de.tum.bgu.msm.syntheticPopulationGenerator.properties.PropertiesSynPop;
import de.tum.bgu.msm.utils.SiloUtil;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class GenerateSchoolMicrolocation {

    private static final Logger logger = Logger.getLogger(GenerateSchoolMicrolocation.class);

    private final DataContainerWithSchools dataContainer;
    private final DataSetSynPop dataSetSynPop;
    Map<Integer, Map<Integer,Map<Integer,Integer>>> zoneSchoolTypeSchoolLocationCapacity = new HashMap<>();


    public GenerateSchoolMicrolocation(DataContainerWithSchools dataContainer, DataSetSynPop dataSetSynPop){
        this.dataSetSynPop = dataSetSynPop;
        this.dataContainer = dataContainer;
    }

    public void run() {
        logger.info("   Running module: school microlocation");
        logger.info("   Start creating school objects from school location list");
        createSchools();
        logger.info("   Start Selecting the school to allocate the student");
        //Select the school to allocate the student
        int errorSchool12 = 0;
        int errorSchool3 = 0;
        int totalStudents = 0;
        RealEstateDataManager realEstateData = dataContainer.getRealEstateDataManager();
        for (Person p : dataContainer.getHouseholdDataManager().getPersons()) {
            PersonBkk pp = (PersonBkk) p;
            int age = pp.getAge();
            final Random pRandom = SiloUtil.getRandomObject();
            if(age>=18 && age<=22 && pRandom.nextDouble()<=0.3){
              pp.setOccupation(Occupation.STUDENT);
            }
            if (pp.getOccupation() == Occupation.STUDENT) {

//                int zoneID = realEstateData.getDwelling(pp.getHousehold().getDwellingId()).getZoneId();
//                int schoolType = pp.getSchoolType();
//                Zone zone = dataContainer.getGeoData().getZones().get(zoneID);
//
//                if (zoneSchoolTypeSchoolLocationCapacity.get(zoneID).get(schoolType) == null){
//                	School school = dataContainer.getSchoolData().getClosestSchool(pp,pp.getSchoolType());
//                    pp.setSchoolId(school.getId());
//                	errorSchool++;
//                    continue;
//                }
//                int selectedSchoolID = SiloUtil.select(zoneSchoolTypeSchoolLocationCapacity.get(zoneID).get(schoolType));
//                School school = dataContainer.getSchoolData().getSchoolFromId(selectedSchoolID);
                int schoolType = 1;
                if(age>=12 && age<18) {
                  schoolType = 2;
                }
                if(age>=18) {
                  schoolType = 3;
                }
                pp.setSchoolType(schoolType);
                totalStudents+=1;
                int zoneID = realEstateData.getDwelling(pp.getHousehold().getDwellingId()).getZoneId();
//                School school = dataContainer.getSchoolData().getClosestSchool(pp,schoolType);

                int selectedSchoolID=-1;
                int schoolZoneID = zoneID;
                int remainingCapacity;
//                int selectedSchoolID = school.getId();
//                int schoolZoneID = school.getZoneId();
//                int remainingCapacity = zoneSchoolTypeSchoolLocationCapacity.get(schoolZoneID).get(schoolType).get(selectedSchoolID) - 1;
                double nearest_distance = 99999;
//                if(remainingCapacity<0) {
                  //within 20 kms
                  if(schoolType!=3) {
//                    int counter_i=0;
                    for (School ss : dataContainer.getSchoolData().getNearSchool(pp, schoolType, 30000)) {

                      Coordinate schoolCoordinate = ((SchoolImpl) ss).getCoordinate();
                      Dwelling dwelling = realEstateData.getDwelling(pp.getHousehold().getDwellingId());
                      Coordinate dwellingCoordinate = dwelling.getCoordinate();
                      double distance = dwellingCoordinate.distance(schoolCoordinate);
                      remainingCapacity = zoneSchoolTypeSchoolLocationCapacity.get(ss.getZoneId()).get(schoolType).get(ss.getId()) - 1;
                      if (remainingCapacity > 0) {
//                          nearest_distance=distance;
//                          selectedSchoolID = ss.getId();
//                          schoolZoneID = ss.getZoneId();
//                          break;
                        if(distance < nearest_distance){
                          nearest_distance=distance;
                          selectedSchoolID = ss.getId();
                          schoolZoneID = ss.getZoneId();
                          Random rand = SiloUtil.getRandomObject();
                          if(distance<=10000 && rand.nextDouble()<=0.8){
                            break;
                          }
                        }
                      }
                    }

                    if(selectedSchoolID!=-1){
                      remainingCapacity = zoneSchoolTypeSchoolLocationCapacity.get(schoolZoneID).get(schoolType).get(selectedSchoolID) - 1;
                      zoneSchoolTypeSchoolLocationCapacity.get(schoolZoneID).get(schoolType).put(selectedSchoolID, remainingCapacity);
                      pp.setSchoolId(selectedSchoolID);
                      pp.setSchoolDistance(nearest_distance);
                    }else{
                      errorSchool12++;
                    }
                  }else{
                    //within 100 kms
                    for (School ss : dataContainer.getSchoolData().getNearSchool(pp, schoolType, 100000)) {

                      Coordinate schoolCoordinate = ((SchoolImpl) ss).getCoordinate();
                      Dwelling dwelling = realEstateData.getDwelling(pp.getHousehold().getDwellingId());
                      Coordinate dwellingCoordinate = dwelling.getCoordinate();
                      double distance = dwellingCoordinate.distance(schoolCoordinate);
                      remainingCapacity = zoneSchoolTypeSchoolLocationCapacity.get(ss.getZoneId()).get(schoolType).get(ss.getId()) - 1;
                      if (remainingCapacity > 0) {
                        if(distance < nearest_distance){
                          nearest_distance=distance;
                          selectedSchoolID = ss.getId();
                          schoolZoneID = ss.getZoneId();
                          Random rand = SiloUtil.getRandomObject();
                          if((distance<=20000 && rand.nextDouble()<=0.8)  || rand.nextDouble()<=0.2){
                            break;
                          }
                        }
                      }
                    }

                    if(selectedSchoolID!=-1){
                      remainingCapacity = zoneSchoolTypeSchoolLocationCapacity.get(schoolZoneID).get(schoolType).get(selectedSchoolID) - 1;
                      zoneSchoolTypeSchoolLocationCapacity.get(schoolZoneID).get(schoolType).put(selectedSchoolID, remainingCapacity);
                      pp.setSchoolId(selectedSchoolID);
                      pp.setSchoolDistance(nearest_distance);
                    }else{
                      errorSchool3++;
                    }
                  }

//                }else{
//                  zoneSchoolTypeSchoolLocationCapacity.get(schoolZoneID).get(schoolType).put(selectedSchoolID, remainingCapacity);
//                  Coordinate schoolCoordinate = ((SchoolImpl) school).getCoordinate();
//                  Dwelling dwelling = realEstateData.getDwelling(pp.getHousehold().getDwellingId());
//                  Coordinate dwellingCoordinate = dwelling.getCoordinate();
//                  double distance = dwellingCoordinate.distance(schoolCoordinate);
//                  pp.setSchoolId(school.getId());
//                  pp.setSchoolDistance(distance);
//                }
            }
        }

        for (School ss : dataContainer.getSchoolData().getSchools()){
            int finalRemainingCapacity = zoneSchoolTypeSchoolLocationCapacity.get(ss.getZoneId()).get(ss.getType()).get(ss.getId());
            ss.setOccupancy(ss.getCapacity()-finalRemainingCapacity);
        }

        logger.warn( errorSchool12 +"   1-2 Students cannot find specific school location. Their coordinates are assigned randomly in TAZ" );
        logger.warn( errorSchool3 +"   3 Students cannot find specific school location. Their coordinates are assigned randomly in TAZ" );
        logger.info(totalStudents+" students, Finished school microlocation.");
    }



    private void createSchools() {

        for (int zone : dataSetSynPop.getTazs()){
            Map<Integer,Map<Integer,Integer>> schoolLocationListForThisSchoolType = new HashMap<>();
            for (int type = 1 ; type <= 3; type++){
                Map<Integer,Integer> schoolCapacity = new HashMap<>();
                schoolLocationListForThisSchoolType.put(type,schoolCapacity);
            }
            zoneSchoolTypeSchoolLocationCapacity.put(zone,schoolLocationListForThisSchoolType);
        }

        SchoolData schoolData = dataContainer.getSchoolData();

        for (int row = 1; row <= PropertiesSynPop.get().main.schoolLocationlist.getRowCount(); row++) {

            int id = (int) PropertiesSynPop.get().main.schoolLocationlist.getValueAt(row,"OBJECTID");
            int zone = (int) PropertiesSynPop.get().main.schoolLocationlist.getValueAt(row,"zoneID");
            float xCoordinate = PropertiesSynPop.get().main.schoolLocationlist.getValueAt(row,"x");
            float yCoordinate = PropertiesSynPop.get().main.schoolLocationlist.getValueAt(row,"y");
            int schoolCapacity = (int) PropertiesSynPop.get().main.schoolLocationlist.getValueAt(row,"schoolCapacity");
            int schoolType = (int) PropertiesSynPop.get().main.schoolLocationlist.getValueAt(row,"schoolType");

            Coordinate coordinate = new Coordinate(xCoordinate,yCoordinate);
            //double size capacity
            if(schoolCapacity<2000){
              schoolCapacity=2000;
            }
            schoolData.addSchool(SchoolUtils.getFactory().createSchool(id, schoolType, schoolCapacity,0,coordinate, zone));

            if (zoneSchoolTypeSchoolLocationCapacity.get(zone) != null){
                zoneSchoolTypeSchoolLocationCapacity.get(zone).get(schoolType).put(id,schoolCapacity);
            }else{
                logger.info("Error zoneID" + zone);
            }

        }
        schoolData.setup();
    }
}
