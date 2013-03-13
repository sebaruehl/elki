package de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.mtree;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2013
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.ArrayList;
import java.util.List;

import de.lmu.ifi.dbs.elki.data.FeatureVector;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.query.DatabaseQuery;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.query.knn.KNNQuery;
import de.lmu.ifi.dbs.elki.database.query.range.RangeQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.database.relation.RelationUtil;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.index.DynamicIndex;
import de.lmu.ifi.dbs.elki.index.KNNIndex;
import de.lmu.ifi.dbs.elki.index.RangeIndex;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.MTreeEntry;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.MTreeLeafEntry;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.MTreeSettings;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.query.MTreeQueryUtil;
import de.lmu.ifi.dbs.elki.persistent.ByteArrayUtil;
import de.lmu.ifi.dbs.elki.persistent.PageFile;
import de.lmu.ifi.dbs.elki.utilities.exceptions.ExceptionMessages;

/**
 * Class for using an m-tree as database index.
 * 
 * @author Erich Schubert
 * 
 * @param <O> Object type
 * @param <D> Distance type
 */
public class MTreeIndex<O, D extends NumberDistance<D, ?>> extends MTree<O, D> implements RangeIndex<O>, KNNIndex<O>, DynamicIndex {
  /**
   * The relation indexed.
   */
  private Relation<O> relation;

  /**
   * The distance query.
   */
  protected DistanceQuery<O, D> distanceQuery;

  /**
   * Constructor.
   * 
   * @param relation Relation indexed
   * @param pagefile Page file
   * @param settings Tree settings
   */
  public MTreeIndex(Relation<O> relation, PageFile<MTreeNode<O, D>> pagefile, MTreeSettings<O, D, MTreeNode<O, D>, MTreeEntry> settings) {
    super(pagefile, settings);
    this.relation = relation;
    this.distanceQuery = getDistanceFunction().instantiate(relation);
  }

  @Override
  public D distance(DBIDRef id1, DBIDRef id2) {
    if (id1 == null || id2 == null) {
      return getDistanceFactory().undefinedDistance();
    }
    statistics.countDistanceCalculation();
    return distanceQuery.distance(id1, id2);
  }

  @Override
  protected void initializeCapacities(MTreeEntry exampleLeaf) {
    int distanceSize = ByteArrayUtil.SIZE_DOUBLE; // exampleLeaf.getParentDistance().externalizableSize();

    // FIXME: simulate a proper feature size!
    @SuppressWarnings("unchecked")
    int featuresize = 8 * RelationUtil.dimensionality((Relation<? extends FeatureVector<?>>) relation);
    if (featuresize <= 0) {
      getLogger().warning("Relation does not have a dimensionality -- simulating M-tree as external index!");
      featuresize = 0;
    }

    // overhead = index(4), numEntries(4), id(4), isLeaf(0.125)
    double overhead = 12.125;
    if (getPageSize() - overhead < 0) {
      throw new RuntimeException("Node size of " + getPageSize() + " Bytes is chosen too small!");
    }

    // dirCapacity = (pageSize - overhead) / (nodeID + objectID + coveringRadius
    // + parentDistance) + 1
    // dirCapacity = (int) (pageSize - overhead) / (4 + 4 + distanceSize +
    // distanceSize) + 1;

    // dirCapacity = (pageSize - overhead) / (nodeID + **object feature size** +
    // coveringRadius + parentDistance) + 1
    dirCapacity = (int) (getPageSize() - overhead) / (4 + featuresize + distanceSize + distanceSize) + 1;

    if (dirCapacity <= 2) {
      throw new RuntimeException("Node size of " + getPageSize() + " Bytes is chosen too small!");
    }

    if (dirCapacity < 10) {
      getLogger().warning("Page size is choosen too small! Maximum number of entries " + "in a directory node = " + (dirCapacity - 1));
    }
    // leafCapacity = (pageSize - overhead) / (objectID + parentDistance) + 1
    // leafCapacity = (int) (pageSize - overhead) / (4 + distanceSize) + 1;
    // leafCapacity = (pageSize - overhead) / (objectID + ** object size ** +
    // parentDistance) + 1
    leafCapacity = (int) (getPageSize() - overhead) / (4 + featuresize + distanceSize) + 1;

    if (leafCapacity <= 1) {
      throw new RuntimeException("Node size of " + getPageSize() + " Bytes is chosen too small!");
    }

    if (leafCapacity < 10) {
      getLogger().warning("Page size is choosen too small! Maximum number of entries " + "in a leaf node = " + (leafCapacity - 1));
    }
  }

  /**
   * @return a new MTreeLeafEntry representing the specified data object
   */
  protected MTreeEntry createNewLeafEntry(DBID id, O object, double parentDistance) {
    return new MTreeLeafEntry(id, parentDistance);
  }

  @Override
  public void initialize() {
    super.initialize();
    insertAll(relation.getDBIDs());
  }

  @Override
  public void insert(DBIDRef id) {
    insert(createNewLeafEntry(DBIDUtil.deref(id), relation.get(id), Double.NaN), false);
  }

  @Override
  public void insertAll(DBIDs ids) {
    List<MTreeEntry> objs = new ArrayList<>(ids.size());
    for (DBIDIter iter = ids.iter(); iter.valid(); iter.advance()) {
      DBID id = DBIDUtil.deref(iter);
      final O object = relation.get(id);
      objs.add(createNewLeafEntry(id, object, Double.NaN));
    }
    insertAll(objs);
  }

  /**
   * Throws an UnsupportedOperationException since deletion of objects is not
   * yet supported by an M-Tree.
   * 
   * @throws UnsupportedOperationException thrown, since deletions aren't
   *         implemented yet.
   */
  @Override
  public final boolean delete(DBIDRef id) {
    throw new UnsupportedOperationException(ExceptionMessages.UNSUPPORTED_NOT_YET);
  }

  /**
   * Throws an UnsupportedOperationException since deletion of objects is not
   * yet supported by an M-Tree.
   * 
   * @throws UnsupportedOperationException thrown, since deletions aren't
   *         implemented yet.
   */
  @Override
  public void deleteAll(DBIDs ids) {
    throw new UnsupportedOperationException(ExceptionMessages.UNSUPPORTED_NOT_YET);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <S extends Distance<S>> KNNQuery<O, S> getKNNQuery(DistanceQuery<O, S> distanceQuery, Object... hints) {
    // Query on the relation we index
    if (distanceQuery.getRelation() != relation) {
      return null;
    }
    DistanceFunction<? super O, D> distanceFunction = (DistanceFunction<? super O, D>) distanceQuery.getDistanceFunction();
    if (!this.getDistanceFunction().equals(distanceFunction)) {
      if (getLogger().isDebugging()) {
        getLogger().debug("Distance function not supported by index - or 'equals' not implemented right!");
      }
      return null;
    }
    // Bulk is not yet supported
    for (Object hint : hints) {
      if (hint == DatabaseQuery.HINT_BULK) {
        return null;
      }
    }
    DistanceQuery<O, D> dq = distanceFunction.instantiate(relation);
    return (KNNQuery<O, S>) MTreeQueryUtil.getKNNQuery(this, dq, hints);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <S extends Distance<S>> RangeQuery<O, S> getRangeQuery(DistanceQuery<O, S> distanceQuery, Object... hints) {
    // Query on the relation we index
    if (distanceQuery.getRelation() != relation) {
      return null;
    }
    DistanceFunction<? super O, D> distanceFunction = (DistanceFunction<? super O, D>) distanceQuery.getDistanceFunction();
    if (!this.getDistanceFunction().equals(distanceFunction)) {
      if (getLogger().isDebugging()) {
        getLogger().debug("Distance function not supported by index - or 'equals' not implemented right!");
      }
      return null;
    }
    // Bulk is not yet supported
    for (Object hint : hints) {
      if (hint == DatabaseQuery.HINT_BULK) {
        return null;
      }
    }
    DistanceQuery<O, D> dq = distanceFunction.instantiate(relation);
    return (RangeQuery<O, S>) MTreeQueryUtil.getRangeQuery(this, dq);
  }

  @Override
  public String getLongName() {
    return "M-Tree";
  }

  @Override
  public String getShortName() {
    return "mtree";
  }
}
