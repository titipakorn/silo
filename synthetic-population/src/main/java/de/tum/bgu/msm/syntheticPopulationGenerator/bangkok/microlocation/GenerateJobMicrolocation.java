package de.tum.bgu.msm.syntheticPopulationGenerator.bangkok.microlocation;

import de.tum.bgu.msm.container.DataContainer;
import de.tum.bgu.msm.data.Zone;
import de.tum.bgu.msm.data.job.Job;
import de.tum.bgu.msm.data.job.JobImpl;
import de.tum.bgu.msm.syntheticPopulationGenerator.DataSetSynPop;
import de.tum.bgu.msm.syntheticPopulationGenerator.properties.PropertiesSynPop;
import de.tum.bgu.msm.utils.SiloUtil;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.osgeo.proj4j.BasicCoordinateTransform;
import org.osgeo.proj4j.CRSFactory;
import org.osgeo.proj4j.CoordinateReferenceSystem;
import org.osgeo.proj4j.ProjCoordinate;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class GenerateJobMicrolocation {

    private static final Logger logger = Logger.getLogger(GenerateJobMicrolocation.class);

    private final DataContainer dataContainer;
    private final DataSetSynPop dataSetSynPop;
    private Map<Integer, Float> jobX = new HashMap<>();
    private Map<Integer, Float> jobY = new HashMap<>();
    Map<Integer, Integer> jobZone = new HashMap<Integer, Integer>();
    Map<Integer, Map<String,Map<Integer,Float>>> zoneJobTypeJobLocationArea = new HashMap<>();
    Map<Integer, Map<String,Float>> zoneJobTypeDensity = new HashMap<>();
    Map<Integer, Map<String,Integer>> jobsByJobTypeInTAZ = new HashMap<>();

    public GenerateJobMicrolocation(DataContainer dataContainer, DataSetSynPop dataSetSynPop){
        this.dataSetSynPop = dataSetSynPop;
        this.dataContainer = dataContainer;
    }

    public void run() {
        logger.info("   Running module: job microlocation");
        logger.info("   Start parsing jobs information to hashmap");
        readJobFile();
        calculateDensity();
        logger.info("   Start Selecting the job to allocate the job");
        //Select the job to allocate the job
        int errorjob = 0;
        CoordinateReferenceSystem fromCRS = new CRSFactory().createFromName("EPSG:4326");
        CoordinateReferenceSystem toCRS = new CRSFactory().createFromName("EPSG:32647");
        BasicCoordinateTransform transformer = new BasicCoordinateTransform(fromCRS,toCRS);
        for (Job jj: dataContainer.getJobDataManager().getJobs()) {
            int zoneID = jj.getZoneId();
            String jobType = jj.getType();
            Zone zone = dataContainer.getGeoData().getZones().get(zoneID);
            if (zoneJobTypeDensity.get(zoneID).get(jobType)==0.0){
                Coordinate coordinate = zone.getRandomCoordinate(SiloUtil.getRandomObject());
              ProjCoordinate result = new ProjCoordinate();
              result = transformer.transform(new ProjCoordinate(coordinate.x,coordinate.y),result);
              Coordinate coordinate1= new Coordinate(result.x,result.y);
                ((JobImpl)jj).setCoordinate(coordinate1);
//                System.out.println("RANDOMLY GENERATE POINT FROM ZONE: "+zoneID + " , coordinates: "+coordinate.x+","+coordinate.y);
                errorjob++;
                continue;
            }
            int selectedJobID = SiloUtil.select(zoneJobTypeJobLocationArea.get(zoneID).get(jobType));
            float remainingArea = zoneJobTypeJobLocationArea.get(zoneID).get(jobType).get(selectedJobID)- zoneJobTypeDensity.get(zoneID).get(jobType);
            if (remainingArea > 0) {
                zoneJobTypeJobLocationArea.get(zoneID).get(jobType).put(selectedJobID, remainingArea);
            } else {
                zoneJobTypeJobLocationArea.get(zoneID).get(jobType).put(selectedJobID, 0.0f);
            }
            ((JobImpl)jj).setCoordinate(new Coordinate(jobX.get(selectedJobID),jobY.get(selectedJobID)));
        }
        logger.warn( errorjob +"   Dwellings cannot find specific building location. Their coordinates are assigned randomly in TAZ" );
        logger.info("   Finished job microlocation.");
    }



    private void readJobFile() {

        for (int zone : dataSetSynPop.getTazs()){
            Map<String,Map<Integer,Float>> jobLocationListForThisJobType = new HashMap<>();
            for (String jobType : PropertiesSynPop.get().main.jobStringType){
                Map<Integer,Float> jobLocationAndArea = new HashMap<>();
                jobLocationListForThisJobType.put(jobType,jobLocationAndArea);
            }
            zoneJobTypeJobLocationArea.put(zone,jobLocationListForThisJobType);
        }

        for (int row = 1; row <= PropertiesSynPop.get().main.jobLocationlist.getRowCount(); row++) {

            int id = (int) PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"OBJECTID");
            int zone = (int) PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"zoneID");
            float xCoordinate = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"x");
            float yCoordinate = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"y");
            float priArea = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"pri");
            float secArea = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"sec");
            float terArea = PropertiesSynPop.get().main.jobLocationlist.getValueAt(row,"ter");
            jobZone.put(id,zone);
            jobX.put(id,xCoordinate);
            jobY.put(id,yCoordinate);


            if (zoneJobTypeJobLocationArea.get(zone) != null){
                zoneJobTypeJobLocationArea.get(zone).get("pri").put(id,priArea);
                zoneJobTypeJobLocationArea.get(zone).get("sec").put(id,secArea);
                zoneJobTypeJobLocationArea.get(zone).get("ter").put(id,terArea);
            }

        }
    }

    private void calculateDensity() {
        for (int zone : dataSetSynPop.getTazs()){
            Map<String,Integer> jobsByJobType = new HashMap<>();
            Map<String,Float> densityByJobType = new HashMap<>();
            jobsByJobTypeInTAZ.put(zone,jobsByJobType);
            zoneJobTypeDensity.put(zone,densityByJobType);
        }

        for (Job jj: dataContainer.getJobDataManager().getJobs()) {
            int zoneID = jj.getZoneId();
            String jobType = jj.getType();

            jobsByJobTypeInTAZ.get(zoneID).putIfAbsent(jobType, 0);

            int numberOfJobs = jobsByJobTypeInTAZ.get(zoneID).get(jobType);
            jobsByJobTypeInTAZ.get(zoneID).put(jobType,numberOfJobs+1);

        }

        for (int zone : dataSetSynPop.getTazs()){
            if((jobsByJobTypeInTAZ.get(zone) == null)||(zoneJobTypeJobLocationArea.get(zone) == null)){
                continue;
            }
            for (String jobType : PropertiesSynPop.get().main.jobStringType){
                if ((zoneJobTypeJobLocationArea.get(zone).get(jobType) != null)&(jobsByJobTypeInTAZ.get(zone).get(jobType) != null)) {
                    float density = getSum(zoneJobTypeJobLocationArea.get(zone).get(jobType).values())/jobsByJobTypeInTAZ.get(zone).get(jobType);
                    zoneJobTypeDensity.get(zone).put(jobType, density);
                }
            }
        }
    }


    private static float getSum(Collection<? extends Number> values) {
        float sm = 0.f;
        for (Number value : values) {
            sm += value.doubleValue();
        }
        return sm;
    }
}
