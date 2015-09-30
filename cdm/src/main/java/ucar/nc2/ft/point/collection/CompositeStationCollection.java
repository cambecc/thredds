/*
 * Copyright 1998-2015 John Caron and University Corporation for Atmospheric Research/Unidata
 *
 *  Portions of this software were developed by the Unidata Program at the
 *  University Corporation for Atmospheric Research.
 *
 *  Access and use of this software shall impose the following obligations
 *  and understandings on the user. The user is granted the right, without
 *  any fee or cost, to use, copy, modify, alter, enhance and distribute
 *  this software, and any derivative works thereof, and its supporting
 *  documentation for any purpose whatsoever, provided that this entire
 *  notice appears in all copies of the software, derivative works and
 *  supporting documentation.  Further, UCAR requests that the user credit
 *  UCAR/Unidata in any publications that result from the use of this
 *  software or in any product that includes this software. The names UCAR
 *  and/or Unidata, however, may not be used in any advertising or publicity
 *  to endorse or promote any products or commercial entity unless specific
 *  written permission is obtained from UCAR/Unidata. The user also
 *  understands that UCAR/Unidata is not obligated to provide the user with
 *  any support, consulting, training or assistance of any kind with regard
 *  to the use, operation and performance of this software nor to provide
 *  the user with any updates, revisions, new versions or "bug fixes."
 *
 *  THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 *  INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 *  FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 *  NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 *  WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package ucar.nc2.ft.point.collection;

import com.google.common.base.Preconditions;
import thredds.inventory.TimedCollection;
import ucar.ma2.StructureData;
import ucar.nc2.Attribute;
import ucar.nc2.VariableSimpleIF;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.ft.*;
import ucar.nc2.ft.point.*;
import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.time.CalendarDateUnit;
import ucar.unidata.geoloc.LatLonRect;
import ucar.unidata.geoloc.Station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;

/**
 * StationTimeSeries composed of a collection of individual StationTimeSeries. "Composite" pattern.
 *
 * @author caron
 * @since May 19, 2009
 */
public class CompositeStationCollection extends StationTimeSeriesCollectionImpl implements UpdateableCollection {
  private TimedCollection dataCollection;
  protected List<VariableSimpleIF> dataVariables;
  protected List<Attribute> globalAttributes;

  protected CompositeStationCollection(
          String name, CalendarDateUnit timeUnit, String altUnits, TimedCollection dataCollection) throws IOException {
    super(name, timeUnit, altUnits);
    this.dataCollection = dataCollection;
    TimedCollection.Dataset td = dataCollection.getPrototype();
    if (td == null)
      throw new RuntimeException("No datasets in the collection");
  }

  @Override
  protected StationHelper createStationHelper() throws IOException {
    TimedCollection.Dataset td = dataCollection.getPrototype();
    if (td == null)
      throw new RuntimeException("No datasets in the collection");

    Formatter errlog = new Formatter();
    try (FeatureDatasetPoint openDataset = (FeatureDatasetPoint) FeatureDatasetFactoryManager.open(FeatureType.STATION, td.getLocation(), null, errlog)) {
      if (openDataset == null)
        throw new IllegalStateException("Cant open FeatureDatasetPoint " + td.getLocation());

      StationHelper stationHelper = new StationHelper();

      List<DsgFeatureCollection> fcList = openDataset.getPointFeatureCollectionList();
      StationTimeSeriesCollectionImpl openCollection = (StationTimeSeriesCollectionImpl) fcList.get(0);
      List<StationFeature> stns = openCollection.getStationFeatures();

      for (StationFeature stnFeature : stns) {
        stationHelper.addStation(new CompositeStationFeature(stnFeature, timeUnit, altUnits, stnFeature.getFeatureData(), this.dataCollection));
      }

      dataVariables = openDataset.getDataVariables();
      globalAttributes = openDataset.getGlobalAttributes();

      return stationHelper;
    }
  }

  public List<VariableSimpleIF> getDataVariables() {
    getStationHelper();  // dataVariables gets initialized when stationHelper does. Bit of a kludge.
    return dataVariables;
  }

  public List<Attribute> getGlobalAttributes() {
    getStationHelper();  // globalAttributes gets initialized when stationHelper does. Bit of a kludge.
    return globalAttributes;
  }

  @Override
  public CalendarDateRange update() throws IOException {
    return dataCollection.update();
  }

  // Must override default subsetting implementation for efficiency
  // StationTimeSeriesFeatureCollection

  @Override
  public StationTimeSeriesFeatureCollection subset(List<StationFeature> stations) throws IOException {
    if (stations == null) {
      return this;
    } else {
      // List<StationFeature> subset = getStationHelper().getStationFeatures(stations);
      return new CompositeStationCollectionSubset(this, stations);
    }
  }

  @Override
  public StationTimeSeriesFeatureCollection subset(ucar.unidata.geoloc.LatLonRect boundingBox) throws IOException {
    if (boundingBox == null) {
      return this;
    } else {
      List<StationFeature> subset = getStationHelper().getStationFeatures(boundingBox);
      return new CompositeStationCollectionSubset(this, subset);
    }
  }

  /* @Override
  public StationTimeSeriesFeature getStationFeature(Station s) throws IOException {
    StationFeature stnFeature = getStationHelper().getStationFeature(s);
    return new CompositeStationFeature(stnFeature, timeUnit, altUnits, stnFeature.getFeatureData(), dataCollection);
  } */

  @Override
  public PointFeatureCollection flatten(LatLonRect boundingBox, CalendarDateRange dateRange) throws IOException {
    TimedCollection subsetCollection = (dateRange != null) ? dataCollection.subset(dateRange) : dataCollection;
    return new CompositeStationCollectionFlattened(getName(), getTimeUnit(), getAltUnits(), boundingBox, dateRange, subsetCollection);

    //return flatten(stationHelper.getStations(boundingBox), dateRange, null);
  }

  @Override
  public PointFeatureCollection flatten(List<String> stations, CalendarDateRange dateRange, List<VariableSimpleIF> varList) throws IOException {
    TimedCollection subsetCollection = (dateRange != null) ? dataCollection.subset(dateRange) : dataCollection;
    return new CompositeStationCollectionFlattened(getName(), getTimeUnit(), getAltUnits(), stations, dateRange, varList, subsetCollection);
  }


  private static class CompositeStationCollectionSubset extends CompositeStationCollection {
    private final CompositeStationCollection from;
    private final List<StationFeature> stationFeats;

    private CompositeStationCollectionSubset(CompositeStationCollection from, List<StationFeature> stationFeats)
            throws IOException {
      super(from.getName(), from.getTimeUnit(), from.getAltUnits(), from.dataCollection);
      this.from = Preconditions.checkNotNull(from, "from == null");

      Preconditions.checkArgument(stationFeats != null && !stationFeats.isEmpty(),
              "stationFeats == null || stationFeats.isEmpty(): %s", stationFeats);
      this.stationFeats = stationFeats;
    }

    @Override
    protected StationHelper createStationHelper() throws IOException {
      StationHelper stationHelper = new StationHelper();

      for (StationFeature stationFeat : this.stationFeats) {
        stationHelper.addStation(new CompositeStationFeature(
                stationFeat, timeUnit, altUnits, stationFeat.getFeatureData(), from.dataCollection));
      }

      return stationHelper;
    }
  }

  //////////////////////////////////////////////////////////
  // the iterator over StationTimeSeriesFeature objects
  // problematic - since each station will independently iterate over the datasets.
  // betterto use the flatten() method, then reconstitute the station with getStation(pointFeature)

  @Override
  public PointFeatureCollectionIterator getPointFeatureCollectionIterator(int bufferSize) throws IOException {

    // an anonymous class iterating over the stations
    return new PointFeatureCollectionIterator() {
      Iterator<Station> stationIter = getStationHelper().getStations().iterator();

      @Override
      public boolean hasNext() throws IOException {
        return stationIter.hasNext();
      }

      @Override
      public PointFeatureCollection next() throws IOException {
        return (PointFeatureCollection) stationIter.next();
      }

      @Override
      public void setBufferSize(int bytes) {
      }

      @Override
      public void close() {
      }
    };
  }

  // the StationTimeSeriesFeature

  private class CompositeStationFeature extends StationTimeSeriesFeatureImpl {
    private TimedCollection collForFeature;
    private StructureData sdata;

    CompositeStationFeature(StationFeature s, CalendarDateUnit timeUnit, String altUnits, StructureData sdata, TimedCollection collForFeature) {
      super(s, timeUnit, altUnits, -1);
      this.sdata = sdata;
      this.collForFeature = collForFeature;
      CalendarDateRange cdr = collForFeature.getDateRange();
      if (cdr != null) {
        getInfo();
        info.setCalendarDateRange(cdr);
      }
    }

    // an iterator over the observations for this station

    @Override
    public PointFeatureIterator getPointFeatureIterator(int bufferSize) throws IOException {
      return new CompositeStationFeatureIterator();
    }

    /*
    public StationTimeSeriesFeature subset(DateRange dateRange) throws IOException {
      return subset(CalendarDateRange.of(dateRange));  // Handles dateRange == null.
    } */

    @Override
    public StationTimeSeriesFeature subset(CalendarDateRange dateRange) throws IOException {
      if (dateRange == null) return this;

      // Extract the collection members that intersect dateRange. For example, if we have:
      //     collForFeature == CollectionManager{
      //         Dataset{location='foo_20141015.nc', dateRange=2014-10-15T00:00:00Z - 2014-10-16T00:00:00Z}
      //         Dataset{location='foo_20141016.nc', dateRange=2014-10-16T00:00:00Z - 2014-10-17T00:00:00Z}
      //         Dataset{location='foo_20141017.nc', dateRange=2014-10-17T00:00:00Z - 2014-10-18T00:00:00Z}
      //         Dataset{location='foo_20141018.nc', dateRange=2014-10-18T00:00:00Z - 2014-10-19T00:00:00Z}
      //     }
      // and we want to subset it with:
      //     dateRange == 2014-10-16T12:00:00Z - 2014-10-17T12:00:00Z
      // the result will be:
      //     collectionSubset == CollectionManager{
      //         Dataset{location='foo_20141016.nc', dateRange=2014-10-16T00:00:00Z - 2014-10-17T00:00:00Z}
      //         Dataset{location='foo_20141017.nc', dateRange=2014-10-17T00:00:00Z - 2014-10-18T00:00:00Z}
      //     }
      TimedCollection collectionSubset = collForFeature.subset(dateRange);

      // Create a new CompositeStationFeature from the subsetted collection.
      CompositeStationFeature compStnFeatSubset =
              new CompositeStationFeature(s, getTimeUnit(), getAltUnits(), sdata, collectionSubset);

      // We're not done yet! While compStnFeatSubset has been limited to only include datasets that intersect dateRange,
      // it'll often be the case that those datasets contain some times that we don't want. In the example above,
      // we only want:
      //     dateRange == 2014-10-16T12:00:00Z - 2014-10-17T12:00:00Z
      // but the range of compStnFeatSubset is:
      //     2014-10-16T00:00:00Z - 2014-10-18T00:00:00Z
      // We don't want the first or last 12 hours! So, wrap in a StationFeatureSubset to do per-feature filtering.
      // Note that the TimedCollection.subset() call above is still worth doing, as it limits the number of features
      // we must iterate over.
      return new StationFeatureSubset(compStnFeatSubset, dateRange);
    }

    @Nonnull
    @Override
    public StructureData getFeatureData() throws IOException {
      return sdata;
    }

    @Override
    @Nullable
    public PointFeatureCollection subset(LatLonRect boundingBox, CalendarDateRange dateRange) throws IOException {
      if (boundingBox != null) {
        if (!boundingBox.contains(s.getLatLon())) return null;
        if (dateRange == null) return this;
      }
      return subset(dateRange);
    }

    // the iterator over PointFeature - an iterator over iterators, one for each dataset

    private class CompositeStationFeatureIterator extends PointIteratorAbstract {
      private int bufferSize = -1;
      private Iterator<TimedCollection.Dataset> iter;
      private FeatureDatasetPoint currentDataset;
      private PointFeatureIterator pfIter = null;
      private boolean finished = false;

      CompositeStationFeatureIterator() {
        iter = collForFeature.getDatasets().iterator();
      }

      private PointFeatureIterator getNextIterator() throws IOException {
        if (!iter.hasNext()) return null;
        TimedCollection.Dataset td = iter.next();
        Formatter errlog = new Formatter();
        currentDataset = (FeatureDatasetPoint) FeatureDatasetFactoryManager.open(FeatureType.STATION, td.getLocation(), null, errlog);
        if (currentDataset == null)
          throw new IllegalStateException("Cant open FeatureDatasetPoint " + td.getLocation());

        List<DsgFeatureCollection> fcList = currentDataset.getPointFeatureCollectionList();
        StationTimeSeriesFeatureCollection stnCollection = (StationTimeSeriesFeatureCollection) fcList.get(0);
        StationFeature s = stnCollection.findStationFeature(getName());
        if (s == null) {
          System.out.printf("CompositeStationFeatureIterator dataset: %s missing station %s%n",
                  td.getLocation(), getName());
          return getNextIterator();
        }

        StationTimeSeriesFeature stnFeature = stnCollection.getStationTimeSeriesFeature(s);
        if (CompositeDatasetFactory.debug)
          System.out.printf("CompositeStationFeatureIterator open dataset: %s for %s%n", td.getLocation(), s.getName());
        return stnFeature.getPointFeatureIterator(bufferSize);
      }

      public boolean hasNext() {
        try {
          if (pfIter == null) {
            pfIter = getNextIterator();
            if (pfIter == null) {
              close();
              return false;
            }
          }

          if (!pfIter.hasNext()) {
            pfIter.close();
            currentDataset.close();
            if (CompositeDatasetFactory.debug)
              System.out.printf("CompositeStationFeatureIterator close dataset: %s%n", currentDataset.getLocation());
            pfIter = getNextIterator();
            return hasNext();
          }

          return true;
        } catch (IOException ioe) {
          throw new RuntimeException(ioe);
        }
      }

      public PointFeature next() {
        PointFeature pf =  pfIter.next();
        calcBounds(pf);
        return pf;
      }

      public void close() {
        if (finished) return;

        if (pfIter != null)
          pfIter.close();

        if (currentDataset != null)
          try {
            currentDataset.close();
            if (CompositeDatasetFactory.debug)
              System.out.printf("CompositeStationFeatureIterator close dataset: %s%n", currentDataset.getLocation());
          } catch (IOException e) {
            throw new RuntimeException(e);
          }

        finishCalcBounds();
        finished = true;
      }

      public void setBufferSize(int bytes) {
        bufferSize = bytes;
      }
    }
  }

}
