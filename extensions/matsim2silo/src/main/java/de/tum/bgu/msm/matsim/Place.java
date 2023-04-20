package de.tum.bgu.msm.matsim;

import org.locationtech.jts.geom.Coordinate;

public class Place implements Comparable<Place> {
    private Coordinate coord;
    private double distance;

    public Place(Coordinate coord, double distance){
      super();
      this.coord=coord;
      this.distance=distance;
    }

  public Coordinate getCoord() {
    return coord;
  }

  @Override
  public int compareTo(Place o) {
    return (int) (this.distance - o.distance);
  }
}
