package de.lmu.ifi.dbs.elki.database.relation;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2014
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

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;

import de.lmu.ifi.dbs.elki.data.FeatureVector;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.type.VectorFieldTypeInformation;
import de.lmu.ifi.dbs.elki.database.ids.ArrayDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBIDArrayIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;

/**
 * Utility functions for handling database relation.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.uses Relation oneway
 * @apiviz.has CollectionFromRelation
 * @apiviz.has RelationObjectIterator
 */
public final class RelationUtil {
  /**
   * Fake constructor: do not instantiate.
   */
  private RelationUtil() {
    // Do not instantiate!
  }

  /**
   * Get the vector field type information from a relation.
   * 
   * @param relation relation
   * @param <V> Vector type
   * @return Vector field type information
   */
  public static <V extends FeatureVector<?>> VectorFieldTypeInformation<V> assumeVectorField(Relation<V> relation) {
    try {
      return ((VectorFieldTypeInformation<V>) relation.getDataTypeInformation());
    }
    catch(Exception e) {
      throw new UnsupportedOperationException("Expected a vector field, got type information: " + relation.getDataTypeInformation().toString(), e);
    }
  }

  /**
   * Get the number vector factory of a database relation.
   * 
   * @param relation relation
   * @param <V> Vector type
   * @return Vector field type information
   */
  public static <V extends NumberVector> NumberVector.Factory<V> getNumberVectorFactory(Relation<V> relation) {
    final VectorFieldTypeInformation<V> type = assumeVectorField(relation);
    @SuppressWarnings("unchecked")
    final NumberVector.Factory<V> factory = (NumberVector.Factory<V>) type.getFactory();
    return factory;
  }

  /**
   * Get the dimensionality of a database relation.
   * 
   * @param relation relation
   * @return Database dimensionality
   */
  public static int dimensionality(Relation<? extends FeatureVector<?>> relation) {
    try {
      return ((VectorFieldTypeInformation<? extends FeatureVector<?>>) relation.getDataTypeInformation()).getDimensionality();
    }
    catch(Exception e) {
      return -1;
    }
  }

  /**
   * Determines the minimum and maximum values in each dimension of all objects
   * stored in the given database.
   * 
   * @param relation the database storing the objects
   * @return Minimum and Maximum vector for the hyperrectangle
   */
  public static double[][] computeMinMax(Relation<? extends NumberVector> relation) {
    int dim = RelationUtil.dimensionality(relation);
    double[] mins = new double[dim], maxs = new double[dim];
    for(int i = 0; i < dim; i++) {
      mins[i] = Double.MAX_VALUE;
      maxs[i] = -Double.MAX_VALUE;
    }
    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      final NumberVector o = relation.get(iditer);
      for(int d = 0; d < dim; d++) {
        final double v = o.doubleValue(d);
        mins[d] = (v < mins[d]) ? v : mins[d];
        maxs[d] = (v > maxs[d]) ? v : maxs[d];
      }
    }
    return new double[][] { mins, maxs };
  }

  /**
   * Determines the variances in each dimension of the specified objects stored
   * in the given database.
   * 
   * @param database the database storing the objects
   * @param ids the ids of the objects
   * @param centroid the centroid or reference vector of the ids
   * @return the variances in each dimension of the specified objects
   */
  public static double[] variances(Relation<? extends NumberVector> database, NumberVector centroid, DBIDs ids) {
    final int size = ids.size();
    double[] variances = new double[centroid.getDimensionality()];

    for(DBIDIter iter = ids.iter(); iter.valid(); iter.advance()) {
      NumberVector o = database.get(iter);
      for(int d = 0; d < centroid.getDimensionality(); d++) {
        final double diff = o.doubleValue(d) - centroid.doubleValue(d);
        variances[d] += diff * diff / size;
      }
    }
    return variances;
  }

  /**
   * <em>Copy</em> a relation into a double matrix.
   * 
   * This is <em>not recommended</em> unless you need to modify the data
   * temporarily.
   * 
   * @param relation Relation
   * @param ids IDs, with well-defined order (i.e. array)
   * @return Data matrix
   */
  public static double[][] relationAsMatrix(final Relation<? extends NumberVector> relation, ArrayDBIDs ids) {
    final int rowdim = ids.size();
    final int coldim = dimensionality(relation);
    double[][] mat = new double[rowdim][coldim];
    int r = 0;
    for(DBIDArrayIter iter = ids.iter(); iter.valid(); iter.advance(), r++) {
      NumberVector vec = relation.get(iter);
      double[] row = mat[r];
      for(int c = 0; c < coldim; c++) {
        row[c] = vec.doubleValue(c);
      }
    }
    assert (r == rowdim);
    return mat;
  }

  /**
   * Get the column name or produce a generic label "Column XY".
   * 
   * @param rel Relation
   * @param col Column
   * @param <V> Vector type
   * @return Label
   */
  public static <V extends FeatureVector<?>> String getColumnLabel(Relation<? extends V> rel, int col) {
    String lbl = assumeVectorField(rel).getLabel(col);
    if(lbl != null) {
      return lbl;
    }
    else {
      return "Column " + col;
    }
  }

  /**
   * An ugly vector type cast unavoidable in some situations due to Generics.
   * 
   * @param <V> Base vector type
   * @param <T> Derived vector type (is actually V, too)
   * @param database Database
   * @return Database
   */
  @SuppressWarnings("unchecked")
  public static <V extends NumberVector, T extends NumberVector> Relation<V> relationUglyVectorCast(Relation<T> database) {
    return (Relation<V>) database;
  }

  /**
   * Iterator class that retrieves the given objects from the database.
   * 
   * @author Erich Schubert
   */
  public static class RelationObjectIterator<O> implements Iterator<O> {
    /**
     * The real iterator.
     */
    final DBIDIter iter;

    /**
     * The database we use.
     */
    final Relation<? extends O> database;

    /**
     * Full Constructor.
     * 
     * @param iter Original iterator.
     * @param database Database
     */
    public RelationObjectIterator(DBIDIter iter, Relation<? extends O> database) {
      super();
      this.iter = iter;
      this.database = database;
    }

    /**
     * Simplified constructor.
     * 
     * @param database Database
     */
    public RelationObjectIterator(Relation<? extends O> database) {
      super();
      this.database = database;
      this.iter = database.iterDBIDs();
    }

    @Override
    public boolean hasNext() {
      return iter.valid();
    }

    @Override
    public O next() {
      O ret = database.get(iter);
      iter.advance();
      return ret;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Collection view on a database that retrieves the objects when needed.
   * 
   * @author Erich Schubert
   */
  public static class CollectionFromRelation<O> extends AbstractCollection<O> implements Collection<O> {
    /**
     * The database we query.
     */
    Relation<? extends O> db;

    /**
     * Constructor.
     * 
     * @param db Database
     */
    public CollectionFromRelation(Relation<? extends O> db) {
      super();
      this.db = db;
    }

    @Override
    public Iterator<O> iterator() {
      return new RelationObjectIterator<>(db);
    }

    @Override
    public int size() {
      return db.size();
    }
  }
}
