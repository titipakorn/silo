package de.tum.bgu.msm.matsim;

//import de.tum.bgu.msm.container.DataContainer;
import de.tum.bgu.msm.schools.DataContainerWithSchools;
import de.tum.bgu.msm.data.dwelling.Dwelling;
import de.tum.bgu.msm.data.dwelling.RealEstateDataManager;
import de.tum.bgu.msm.data.MicroLocation;
import de.tum.bgu.msm.data.household.Household;
import de.tum.bgu.msm.data.job.Job;
import de.tum.bgu.msm.data.job.JobDataManager;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.data.person.Person;
import de.tum.bgu.msm.schools.PersonWithSchool;
import de.tum.bgu.msm.schools.School;
import de.tum.bgu.msm.schools.SchoolData;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.data.vehicle.VehicleType;
import de.tum.bgu.msm.models.modeChoice.CommuteModeChoice;
import de.tum.bgu.msm.models.modeChoice.CommuteModeChoiceMapping;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.utils.SiloUtil;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.*;
import java.util.stream.Collectors;

public class SimpleCommuteModeChoiceMatsimScenarioAssembler implements MatsimScenarioAssembler {

  private final static Logger logger = Logger.getLogger(SimpleMatsimScenarioAssembler.class);

  private final DataContainerWithSchools dataContainer;
  private final Properties properties;
  private CommuteModeChoice commuteModeChoice;

  private Map<Integer, Boolean> selected_households;

  //for school time shift
  private static final int START_SCHOOL_TIME = 8*3600;
//  private static final int START_SCHOOL_TIME = 10*3600;
  private static final int END_SCHOOL_TIME = 16*3600;
//  private static final int END_SCHOOL_TIME = 18*3600;

  private static final int START_WORK_TIME = 8*3600+ (3600/2);
  private static final int END_WORK_TIME = 17*3600+ (3600/2);
  private static final double AVG_SPEED = 22.5/3.6;
  private static final double AVG_SPEED_PT = 10.4/3.6;

  public SimpleCommuteModeChoiceMatsimScenarioAssembler(DataContainerWithSchools dataContainer, Properties properties, CommuteModeChoice commuteModeChoice) {
    this.dataContainer = dataContainer;
    this.properties = properties;
    this.commuteModeChoice = commuteModeChoice;
    this.selected_households = new HashMap<>();
  }

  @Override
  public Scenario assembleScenario(Config matsimConfig, int year, TravelTimes travelTimes) {
    logger.info("I SEE YOU PRINCE , Starting creating (mode-respecting, home-work-home) MATSim scenario.");
    double populationScalingFactor = properties.transportModel.matsimScaleFactor;
    SiloMatsimUtils.checkSiloPropertiesAndMatsimConfigConsistency(matsimConfig, properties);

    Scenario scenario = ScenarioUtils.loadScenario(matsimConfig);
    Population matsimPopulation = scenario.getPopulation();

    JobDataManager jobDataManager = dataContainer.getJobDataManager();
    RealEstateDataManager realEstateDataManager = dataContainer.getRealEstateDataManager();
    SchoolData schoolData = dataContainer.getSchoolData();
    for (Household household: dataContainer.getHouseholdDataManager().getHouseholds()) {
      if (SiloUtil.getRandomNumberAsDouble() > populationScalingFactor) {
            continue;
      }
//      int hhId = household.getId();
//      if(year == properties.main.baseYear) {
//        if (SiloUtil.getRandomNumberAsDouble() > populationScalingFactor) {
//          this.selected_households.put(hhId,false);
//          continue;
//        } else {
//          this.selected_households.put(hhId,true);
//        }
//      }else{
//        if(this.selected_households.get(hhId)!=null){
//          if(!this.selected_households.get(hhId)) {
//            continue;
//          }
//        }else{
//          if (SiloUtil.getRandomNumberAsDouble() > populationScalingFactor) {
//            this.selected_households.put(hhId,false);
//            continue;
//          } else {
//            this.selected_households.put(hhId,true);
//          }
//        }
//      }

//      Boolean isThereACarInTheHouse = household.getVehicles().stream().filter(vv -> vv.getType().equals(VehicleType.CAR)).count()>0;
//      Boolean isThereACarInTheHouse = false;
      long VehicleNumberInTheHouse = household.getVehicles().stream().filter(vv -> vv.getType().equals(VehicleType.CAR)).count();
      Boolean isSomeOneToEscorting = false;
      ArrayList<Coordinate> schoolCoords = new ArrayList<>();
      for (Person person : household.getPersons().values()) {
        if (person.getOccupation() == Occupation.EMPLOYED && person.getJobId() != -2 ) { // i.e. person does not work

          PopulationFactory populationFactory = matsimPopulation.getFactory();

          org.matsim.api.core.v01.population.Person matsimAlterEgo = SiloMatsimUtils.createMatsimAlterEgo(populationFactory, person, (int) household.getVehicles().stream().filter(vv -> vv.getType().equals(VehicleType.CAR)).count());
          matsimPopulation.addPerson(matsimAlterEgo);

          Dwelling dwelling = realEstateDataManager.getDwelling(household.getDwellingId());
          CommuteModeChoiceMapping commuteModeChoiceMapping = commuteModeChoice.assignCommuteModeChoice(dwelling, travelTimes, household);
          String mode = TransportMode.pt;
          CommuteModeChoiceMapping.CommuteMode hisMode = commuteModeChoiceMapping.getMode(person);
          if (hisMode != null) {
            mode = commuteModeChoiceMapping.getMode(person).mode;
          }
          if (person.getJobId() != -2 && person.getOccupation() == Occupation.EMPLOYED) {
            if ((mode.equals(TransportMode.car) || VehicleNumberInTheHouse>0) && person.hasDriverLicense()) {
              Coord dwellingCoord = getOrRandomlyChooseDwellingCoord(dwelling);

              Job job = jobDataManager.getJobFromId(person.getJobId());
              Coord jobCoord = getOrRandomlyChooseJobCoordinate(job);

              // With School Trips
              Coord schoolCoord = null;
              if (!household.getPersons().isEmpty()) {
                if (household.getPersons().values().stream().anyMatch(pp -> pp.getOccupation() == Occupation.STUDENT)) {
                  for (Person pp : household.getPersons().values().stream().filter(pp -> pp.getOccupation() == Occupation.STUDENT).collect(Collectors.toList())) {
                    if(pp.getAge()<15) {
                      School school = schoolData.getSchoolFromId(((PersonWithSchool) pp).getSchoolId());
                      if (school != null) {
                        Coordinate home_location = new Coordinate(dwellingCoord.getX(), dwellingCoord.getY());
                        Coordinate school_location = ((MicroLocation) school).getCoordinate();
                        double avg_travel_time = home_location.distance(school_location) / AVG_SPEED;
                        if (avg_travel_time <= (40 * 60)) {
                          schoolCoords.add(school_location);
                        }
                      }
                    }
                  }
                }
              }
              Coordinate home_location = new Coordinate(dwellingCoord.getX(), dwellingCoord.getY());
              Place[] schoolLocations = new Place[schoolCoords.size()];
              for(int i=0, n=schoolCoords.size(); i<n; i++) {
                schoolLocations[i]=new Place(schoolCoords.get(i),home_location.distance(schoolCoords.get(i)));
              }
              Arrays.sort(schoolLocations);
              //filter duplicate school places
              Set<Coordinate> datas = new HashSet<>();
              List<Place> tempPlaceList = new ArrayList<>();
              for(Place p: schoolLocations) if(datas.add(p.getCoord())) tempPlaceList.add(p);
              Place[] uniqueSchools = tempPlaceList.toArray(new Place[tempPlaceList.size()]);

              if (schoolCoords.size()>0 && isSomeOneToEscorting==false) {
                isSomeOneToEscorting=true;
                createHSWSHPlanAndAddToAlterEgo(populationFactory, matsimAlterEgo, dwellingCoord, job, jobCoord, uniqueSchools, TransportMode.car);
              } else {
                createHWHPlanAndAddToAlterEgo(populationFactory, matsimAlterEgo, dwellingCoord, job, jobCoord, TransportMode.car);
              }

              ///
//              createHWHPlanAndAddToAlterEgo(populationFactory, matsimAlterEgo, dwellingCoord, job, jobCoord, TransportMode.car);

              VehicleNumberInTheHouse-=1;
            } else {
              // TODO MATSim expects the plans to be there at least in VspPlansCleaner.notifyBeforeMobsim(VspPlansCleaner.java:54).
              // Therefore, the intended switch one line below may not work; maybe with other settings
              // if (!properties.transportModel.onlySimulateCarTrips) {
              Coord dwellingCoord = getOrRandomlyChooseDwellingCoord(dwelling);

              Job job = jobDataManager.getJobFromId(person.getJobId());
              Coord jobCoord = getOrRandomlyChooseJobCoordinate(job);

              createHWHPlanAndAddToAlterEgo(populationFactory, matsimAlterEgo, dwellingCoord, job, jobCoord, mode);
              // }
            }
          }
        }
      }
      for (Person person : household.getPersons().values()) {
        if (person.getOccupation() == Occupation.STUDENT && ((PersonWithSchool) person).getSchoolId() != -2) {

            School school = schoolData.getSchoolFromId(((PersonWithSchool) person).getSchoolId());
            if (school == null) {
              continue;
            }
          PopulationFactory populationFactory = matsimPopulation.getFactory();

          org.matsim.api.core.v01.population.Person matsimAlterEgo = SiloMatsimUtils.createMatsimAlterEgo(populationFactory, person, (int) household.getVehicles().stream().filter(vv -> vv.getType().equals(VehicleType.CAR)).count());
          matsimPopulation.addPerson(matsimAlterEgo);

          Dwelling dwelling = realEstateDataManager.getDwelling(household.getDwellingId());
          Coord dwellingCoord = getOrRandomlyChooseDwellingCoord(dwelling);
//          CommuteModeChoiceMapping commuteModeChoiceMapping = commuteModeChoice.assignCommuteModeChoice(dwelling, travelTimes, household);
          String mode = TransportMode.pt;
//          CommuteModeChoiceMapping.CommuteMode hisMode = commuteModeChoiceMapping.getMode(person);
//          if (hisMode != null) {
//            mode = commuteModeChoiceMapping.getMode(person).mode;
//          }

          Coordinate sLo = ((MicroLocation) school).getCoordinate();
          Coord schoolCoord = new Coord(sLo.x, sLo.y);
          if (isSomeOneToEscorting && person.getAge()<15 && schoolCoords.stream().filter(vv-> vv==sLo).count()>0) {
            createHSHPlanAndAddToAlterEgo(populationFactory, matsimAlterEgo, dwellingCoord, schoolCoord, TransportMode.ride);
          } else {
            createHSHPlanAndAddToAlterEgo(populationFactory, matsimAlterEgo, dwellingCoord, schoolCoord, mode);
          }
        }
      }
    }
    logger.info("Finished creating MATSim scenario.");
    return scenario;
  }

  private void createHSHPlanAndAddToAlterEgo(PopulationFactory populationFactory, org.matsim.api.core.v01.population.Person matsimAlterEgo,
                                             Coord dwellingCoord, Coord schoolCoord, String transportMode) {
    Plan matsimPlan = populationFactory.createPlan();
    matsimAlterEgo.addPlan(matsimPlan);

    Activity homeActivityMorning = populationFactory.createActivityFromCoord("home", dwellingCoord);

    Coordinate home_location = new Coordinate(dwellingCoord.getX(),dwellingCoord.getY());
    Coordinate job_location = new Coordinate(schoolCoord.getX(),schoolCoord.getY());
    double distance_toWork = home_location.distance(job_location);
    double travel_speed = AVG_SPEED;
    if(transportMode==TransportMode.pt){
      travel_speed=AVG_SPEED_PT;
    }
    double average_travel_time_to_work = distance_toWork/travel_speed;
    double homeDeparture=START_SCHOOL_TIME-average_travel_time_to_work;

//    Double departureTime = (8*3600) - (SiloUtil.getRandomNumberAsDouble() * 3600);
    homeActivityMorning.setEndTime(homeDeparture);
    matsimPlan.addActivity(homeActivityMorning);
    matsimPlan.addLeg(populationFactory.createLeg(transportMode));

    Activity takeToSchoolActivity = populationFactory.createActivityFromCoord("school", schoolCoord);
    //to-do define a proper time after school
//    Double departureFromTakeSchoolTime = departureTime + (9 * 3600  + SiloUtil.getRandomObject().nextGaussian() * 3600);
    takeToSchoolActivity.setEndTime(END_SCHOOL_TIME);
    matsimPlan.addActivity(takeToSchoolActivity);
    matsimPlan.addLeg(populationFactory.createLeg(transportMode));

    Activity homeActivityEvening = populationFactory.createActivityFromCoord("home", dwellingCoord);

    matsimPlan.addActivity(homeActivityEvening);
  }

  private void createHSHSHPlanAndAddToAlterEgo(PopulationFactory populationFactory, org.matsim.api.core.v01.population.Person matsimAlterEgo,
                                               Coord dwellingCoord, Coord schoolCoord, String transportMode) {
    Plan matsimPlan = populationFactory.createPlan();
    matsimAlterEgo.addPlan(matsimPlan);

    Activity homeActivityMorning = populationFactory.createActivityFromCoord("home", dwellingCoord);
    Double departureTime = (8*3600) - (SiloUtil.getRandomNumberAsDouble() * 3600);
    homeActivityMorning.setEndTime(departureTime);
    matsimPlan.addActivity(homeActivityMorning);
    matsimPlan.addLeg(populationFactory.createLeg(transportMode));

    Activity takeToSchoolActivity = populationFactory.createActivityFromCoord("accompanyingToSchool", schoolCoord);
    //to-do define a proper time after school
    Double departureFromTakeSchoolTime = departureTime + SiloUtil.getRandomObject().nextGaussian() * 1800;
    takeToSchoolActivity.setEndTime(departureFromTakeSchoolTime);
    matsimPlan.addActivity(takeToSchoolActivity);
    matsimPlan.addLeg(populationFactory.createLeg(transportMode));

    Activity homeActivityAfterSchool = populationFactory.createActivityFromCoord("home", dwellingCoord);
    Double departureFromHomeEvening = departureTime + 9*3600 + SiloUtil.getRandomObject().nextGaussian() * 1800;
    homeActivityAfterSchool.setEndTime(departureFromHomeEvening);
    matsimPlan.addActivity(homeActivityAfterSchool);
    matsimPlan.addLeg(populationFactory.createLeg(transportMode));

    Activity pickupFromSchoolActivity = populationFactory.createActivityFromCoord("accompanyingFromSchool", schoolCoord);
    //to-do define a proper time after school
    Double departureFromPickupSchoolTime = departureFromHomeEvening + SiloUtil.getRandomObject().nextGaussian() * 1800;
    takeToSchoolActivity.setEndTime(departureFromPickupSchoolTime);
    matsimPlan.addActivity(pickupFromSchoolActivity);
    matsimPlan.addLeg(populationFactory.createLeg(transportMode));

    Activity homeActivityEvening = populationFactory.createActivityFromCoord("home", dwellingCoord);

    matsimPlan.addActivity(homeActivityEvening);
  }
  private void createHSWSHPlanAndAddToAlterEgo(PopulationFactory populationFactory, org.matsim.api.core.v01.population.Person matsimAlterEgo,
                                               Coord dwellingCoord, Job job, Coord jobCoord, Place[] schoolCoords, String transportMode) {
    Plan matsimPlan = populationFactory.createPlan();
    matsimAlterEgo.addPlan(matsimPlan);

    Activity homeActivityMorning = populationFactory.createActivityFromCoord("home", dwellingCoord);
    Integer jobStartTime = defineDepartureFromHome(job);
    //school shift for parent to escorting
//    jobStartTime+=2*3600;

    Coordinate home_location = new Coordinate(dwellingCoord.getX(),dwellingCoord.getY());
    Coordinate job_location = new Coordinate(jobCoord.getX(),jobCoord.getY());
    double distance_toWork = home_location.distance(job_location);
    double travel_time_to_school = 0;
    boolean dropOffChild=false;
//    ArrayList<Coord> school_locations = new ArrayList<>();
    ArrayList<Double> school_times = new ArrayList<>();

    //shift between 1-2 hours
//    Integer rand_diff = Math.max((int)(SiloUtil.getRandomObject().nextDouble()*2*3600),3600);

//    if(Math.abs((jobStartTime+rand_diff)-START_SCHOOL_TIME)<=2*3600){
      for(int i=0, n=schoolCoords.length; i<n; i++) {
        Coordinate school_location = schoolCoords[i].getCoord();
        double distance_toSchool = 0;
        if(i>0) {
          Coordinate lastVisit_location = schoolCoords[i-1].getCoord();
          distance_toSchool = lastVisit_location.distance(school_location);
        }else{
          distance_toSchool = home_location.distance(school_location);
        }
        travel_time_to_school += distance_toSchool / AVG_SPEED;
        school_times.add(distance_toSchool / AVG_SPEED);
      }
      //send kids
      dropOffChild=true;
//    }

//    if(dropOffChild){
//        jobStartTime=START_SCHOOL_TIME;
//    }

    Integer jobEndTime = defineWorkEndTime(job, jobStartTime);
    double average_travel_time_to_work = distance_toWork/AVG_SPEED;
    double homeDeparture=jobStartTime-average_travel_time_to_work;
    homeDeparture-=travel_time_to_school;

    homeActivityMorning.setEndTime(homeDeparture);
    matsimPlan.addActivity(homeActivityMorning);
    matsimPlan.addLeg(populationFactory.createLeg(transportMode));

    if(dropOffChild) {
      for(int i=schoolCoords.length-1; i>=0; i--) {
        Coordinate sLo = schoolCoords[i].getCoord();
        Coord schoolCo = new Coord(sLo.x,sLo.y);
        Activity takeToSchoolActivity = populationFactory.createActivityFromCoord("accompanyingToSchool", schoolCo);
        takeToSchoolActivity.setMaximumDuration(3*60);
        takeToSchoolActivity.setEndTimeUndefined();
//        homeDeparture += school_times.get(i);
//        takeToSchoolActivity.setEndTime(homeDeparture);
        matsimPlan.addActivity(takeToSchoolActivity);
        matsimPlan.addLeg(populationFactory.createLeg(transportMode));
      }
    }
    double jobDeparture=jobEndTime;
    Activity workActivity = populationFactory.createActivityFromCoord("work", jobCoord);
    workActivity.setEndTime(jobDeparture);
    matsimPlan.addActivity(workActivity);
    matsimPlan.addLeg(populationFactory.createLeg(transportMode));

    //Math.abs(jobEndTime-END_SCHOOL_TIME)<=1*3600 || (jobEndTime>START_SCHOOL_TIME && jobEndTime<=18*3600)

    if(dropOffChild){
      for(int i=0, n=schoolCoords.length; i<n; i++) {
        Coordinate sLo = schoolCoords[i].getCoord();
        Coord schoolCo = new Coord(sLo.x,sLo.y);
        //pickup kids
        Activity pickupFromSchoolActivity = populationFactory.createActivityFromCoord("accompanyingFromSchool", schoolCo);
        //to-do define a proper time after school
        pickupFromSchoolActivity.setEndTimeUndefined();
        pickupFromSchoolActivity.setMaximumDuration(5*60);
//        jobDeparture+=school_times.get(i);
//        pickupFromSchoolActivity.setEndTime(jobDeparture);
        matsimPlan.addActivity(pickupFromSchoolActivity);
        matsimPlan.addLeg(populationFactory.createLeg(transportMode));
      }
    }

    Activity homeActivityEvening = populationFactory.createActivityFromCoord("home", dwellingCoord);

    matsimPlan.addActivity(homeActivityEvening);
  }

  private Coord getOrRandomlyChooseDwellingCoord(Dwelling dwelling) {
    Coordinate dwellingCoordinate;
    if (dwelling != null && dwelling.getCoordinate() != null) {
      dwellingCoordinate = dwelling.getCoordinate();
    } else {
      // TODO This step should not be done (again) if a random coordinate for the same dwelling has been chosen before, dz 10/20
      dwellingCoordinate = dataContainer.getGeoData().getZones().get(dwelling.getZoneId()).getRandomCoordinate(SiloUtil.getRandomObject());
    }
    return new Coord(dwellingCoordinate.x, dwellingCoordinate.y);
  }

  private Coord getOrRandomlyChooseJobCoordinate(Job job) {
    Coordinate jobCoordinate;
    if (job != null && job.getCoordinate() != null) {
      jobCoordinate = job.getCoordinate();
    } else {
      // TODO This step should not be done (again) if a random coordinate for the same job has been chosen before, dz 10/20
      jobCoordinate = dataContainer.getGeoData().getZones().get(job.getZoneId()).getRandomCoordinate(SiloUtil.getRandomObject());
    }
    return new Coord(jobCoordinate.x, jobCoordinate.y);
  }

  private void createHWHPlanAndAddToAlterEgo(PopulationFactory populationFactory, org.matsim.api.core.v01.population.Person matsimAlterEgo,
                                             Coord dwellingCoord, Job job, Coord jobCoord, String transportMode) {
    Plan matsimPlan = populationFactory.createPlan();
    matsimAlterEgo.addPlan(matsimPlan);

    Activity homeActivityMorning = populationFactory.createActivityFromCoord("home", dwellingCoord);
    Integer jobStartTime = defineDepartureFromHome(job);

    // work time shift
//    if (SiloUtil.getRandomObject().nextDouble() <= 0.3) {
//      Integer rand_diff = (int)((SiloUtil.getRandomObject().nextDouble() * 2 - 1)*2*3600);
//      jobStartTime+=rand_diff;
//    }

    Integer jobEndTime = defineWorkEndTime(job, jobStartTime);

    Coordinate home_location = new Coordinate(dwellingCoord.getX(),dwellingCoord.getY());
    Coordinate job_location = new Coordinate(jobCoord.getX(),jobCoord.getY());
    double distance_toWork = home_location.distance(job_location);
    double travel_speed = AVG_SPEED;
    if(transportMode==TransportMode.pt){
      travel_speed=AVG_SPEED_PT;
    }
    double average_travel_time_to_work = distance_toWork/travel_speed;
    double homeDeparture=jobStartTime-average_travel_time_to_work;

    homeActivityMorning.setEndTime(homeDeparture);
    matsimPlan.addActivity(homeActivityMorning);
    matsimPlan.addLeg(populationFactory.createLeg(transportMode));

    Activity workActivity = populationFactory.createActivityFromCoord("work", jobCoord);
    workActivity.setEndTime(jobEndTime);
    matsimPlan.addActivity(workActivity);
    matsimPlan.addLeg(populationFactory.createLeg(transportMode));

    Activity homeActivityEvening = populationFactory.createActivityFromCoord("home", dwellingCoord);

    matsimPlan.addActivity(homeActivityEvening);
  }

  /**
   * Defines departure time from home. Note that it actually tries to use job start times if defined. Otherwise
   * randomly draws from a normal distribution around the peak hour with 1 hour standard deviation.
   */
  private Integer defineDepartureFromHome(Job job) {
    //work time shift 30% for two hours before or after
    Integer jobStartTime=START_WORK_TIME;
//    if (SiloUtil.getRandomObject().nextDouble() <= 0.3) {
//      Integer rand_diff = (int)((SiloUtil.getRandomObject().nextDouble() * 2 - 1)*2*3600);
//      jobStartTime+=rand_diff;
//    }
    return jobStartTime;
//    if (SiloUtil.getRandomNumberAsDouble() > 0.8) {
//      return START_WORK_TIME;
//    }
//    return (int) (properties.transportModel.peakHour_s + SiloUtil.getRandomObject().nextGaussian() * 3600);
//    return job.getStartTimeInSeconds().orElse(Math.max(0, (int) (properties.transportModel.peakHour_s + SiloUtil.getRandomObject().nextGaussian() * 3600)));
  }


  /**
   * Defines departure time from work. Note that it actually tries to use job duration times if defined. Otherwise
   * randomly draws from a normal distribution with mean of 8 hours with 1 hour standard deviation. The duration
   * is then added to the job starting time.
   */
  private int defineWorkEndTime(Job job, int departureTime) {
    //((SiloUtil.getRandomObject().nextDouble() * 2 - 1) * 3600))
    return departureTime + (9*3600);
//    return departureTime + job.getWorkingTimeInSeconds().orElse(Math.max(0, (int) (8*3600 + SiloUtil.getRandomObject().nextGaussian() * 3600)));
  }
}
