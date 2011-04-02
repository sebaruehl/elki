package experimentalcode.lucia;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import junit.framework.Assert;
import org.junit.Test;

import de.lmu.ifi.dbs.elki.JUnit4Test;
import de.lmu.ifi.dbs.elki.algorithm.outlier.SOD;
import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.evaluation.roc.ComputeROCCurve;
import de.lmu.ifi.dbs.elki.index.preprocessed.snn.SharedNearestNeighborPreprocessor;
import de.lmu.ifi.dbs.elki.result.AnnotationResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.utilities.ClassGenericsUtil;
import de.lmu.ifi.dbs.elki.utilities.exceptions.UnableToComplyException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.ListParameterization;
import de.lmu.ifi.dbs.elki.utilities.pairs.Pair;

/**
 * Tests the SOD algorithm. 
 * @author lucia
 * 
 */
public class TestSOD extends OutlierTest implements JUnit4Test{
	// the following values depend on the data set used!
  static String dataset = "src/experimentalcode/lucia/datensaetze/hochdimensional.csv";

	static int knn = 25;
	static int snn = 19;
	


	@Test
	public void testSOD() throws UnableToComplyException {
		ArrayList<Pair<Double, DBID>> pair_scoresIds = new ArrayList<Pair<Double, DBID>>();

		Database<DoubleVector> db = getDatabase(dataset);

		
		//Parameterization
		ListParameterization params = new ListParameterization();
		params.addParameter(SOD.KNN_ID, knn);
		params.addParameter(SharedNearestNeighborPreprocessor.Factory.NUMBER_OF_NEIGHBORS_ID, snn);
		params.addParameter(ComputeROCCurve.POSITIVE_CLASS_NAME_ID, "Noise");

		
		// run SOD
		OutlierResult result = runSOD(db, params);
		AnnotationResult<Double> scores = result.getScores();

		for(DBID id : db.getIDs()) {
			pair_scoresIds.add(new Pair<Double, DBID>(scores.getValueFor(id),id));
		}

		//get ROC AUC
		List<Double> auc = getROCAUC(db, result, params);
		Iterator<Double> iter = auc.listIterator();
		double actual;
		while(iter.hasNext()){
			actual = iter.next();
			System.out.println("SOD(knn="+ knn + " und snn="+ snn +") ROC AUC: " + actual);
			Assert.assertEquals("ROC AUC not right.", 0.95171989, actual, 0.00001);
		}
	}
	
	private static OutlierResult runSOD(Database<DoubleVector> db, ListParameterization params) {
		// setup algorithm
		SOD<DoubleVector, DoubleDistance> sod = null;
		Class<SOD<DoubleVector, DoubleDistance>> sodcls = ClassGenericsUtil.uglyCastIntoSubclass(SOD.class);
		sod = params.tryInstantiate(sodcls, sodcls);
		params.failOnErrors();

		// run SOD on database
		return sod.run(db);
	}
}
