package org.exist.indexing.spatial;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.StoredNode;
import org.exist.indexing.spatial.AbstractGMLJDBCIndex.SpatialOperator;
import org.exist.numbering.DLN;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.StringValue;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.w3c.dom.Document;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;

/**
 *
 * Each index entry maps a key (collectionId, ngram) to a list of occurrences, which has the
 * following structure:
 *
 * <pre>[docId : int, nameType: byte, occurrenceCount: int, entrySize: long, [id: NodeId, offset: int, ...]* ]</pre>
 */
public class GMLHSQLIndexWorker extends AbstractGMLJDBCIndexWorker {
	
	public final static String ID = GMLHSQLIndexWorker.class.getName();	
	
	private static final Logger LOG = Logger.getLogger(GMLHSQLIndexWorker.class);
	
    public GMLHSQLIndexWorker(GMLHSQLIndex index, DBBroker broker) {
    	super(index, broker);        
        /*
        try {
	        getConnection() = DriverManager.getConnection("jdbc:hsqldb:" + index.getDataDir() + "/" + 
					index.db_file_name_prefix + ";shutdown=true", "sa", "");
        } catch (SQLException e) {
        	LOG.error(e);
        }
        */
    }
    
    public String getID() {
    	return ID;
    }
    
    protected boolean saveGeometryNode(Geometry geometry, String srsName, DocumentImpl doc, NodeId nodeId, Connection conn) throws SQLException {
    	PreparedStatement ps = conn.prepareStatement("INSERT INTO " + GMLHSQLIndex.TABLE_NAME + "(" +
        		/*1*/ "DOCUMENT_URI, " +            		
    			//TODO : use binary format ?
        		/*2*/ "NODE_ID, " +        			
        		/*3*/ "GEOMETRY_TYPE, " +
        		/*4*/ "SRS_NAME, " +
        		/*5*/ "WKT, " +
    			//TODO : use binary format ?
        		/*6*/ "BASE64_WKB, " +
    			/*7*/ "MINX, " +
    			/*8*/ "MAXX, " +
    			/*9*/ "MINY, " +
    			/*10*/ "MAXY, " +
    			/*11*/ "CENTROID_X, " +
    			/*12*/ "CENTROID_Y, " +
    			/*13*/ "AREA, " +
    			//Boundary ?        		
        		/*14*/ "EPSG4326_WKT, " +
    			//TODO : use binary format ?
        		/*15*/ "EPSG4326_BASE64_WKB, " +
        		/*16*/ "EPSG4326_MINX, " +
    			/*17*/ "EPSG4326_MAXX, " +
    			/*18*/ "EPSG4326_MINY, " +
    			/*19*/ "EPSG4326_MAXY, " +
    			/*20*/ "EPSG4326_CENTROID_X, " +
    			/*21*/ "EPSG4326_CENTROID_Y, " +
    			/*22*/ "EPSG4326_AREA," +
    			//Boundary ?
    			/*23*/ "IS_CLOSED, " +
    			/*24*/ "IS_SIMPLE, " +
    			/*25*/ "IS_VALID" +    			
        		") VALUES (" +
        			"?, ?, ?, ?, ?, " +
        			"?, ?, ?, ?, ?, " +
        			"?, ?, ?, ?, ?, " +
        			"?, ?, ?, ?, ?, " +
        			"?, ?, ?, ?, ?"	            		
        		+ ")"
            );       
    	try {
    		//TODO : use a default SRS from the config file ?
            if (srsName == null) {
        		LOG.error("Geometry has a null SRS");
        		return false;                    	
            }
            MathTransform mathTransform = getTransform(srsName, "EPSG:4326");
            if (mathTransform == null) {
        		LOG.error("Unable to get a transformation from '" + srsName + "' to 'EPSG:4326'");
        		return false;              	
            }
            coordinateTransformer.setMathTransform(mathTransform);        
            Geometry EPSG4326_geometry = null;
            try {
            	EPSG4326_geometry = coordinateTransformer.transform(geometry);
            } catch (TransformException e) {
        		LOG.error(e);
        		return false;
            }
            /*DOCUMENT_URI*/ ps.setString(1, doc.getURI().toString());		          
            /*NODE_ID*/ ps.setString(2, nodeId.toString());
            /*GEOMETRY_TYPE*/ ps.setString(3, geometry.getGeometryType());
            /*SRS_NAME*/ ps.setString(4, srsName);
            /*WKT*/ ps.setString(5, wktWriter.write(geometry));
            base64Encoder.reset();
            base64Encoder.translate(wkbWriter.write(geometry));
            /*BASE64_WKB*/ ps.setString(6, new String(base64Encoder.getCharArray()));
            /*MINX*/ ps.setDouble(7, geometry.getEnvelopeInternal().getMinX());
        	/*MAXX*/ ps.setDouble(8, geometry.getEnvelopeInternal().getMaxX());
        	/*MINY*/ ps.setDouble(9, geometry.getEnvelopeInternal().getMinY());
        	/*MAXY*/ ps.setDouble(10, geometry.getEnvelopeInternal().getMaxY());
        	/*CENTROID_X*/ ps.setDouble(11, geometry.getCentroid().getCoordinate().x);   
        	/*CENTROID_Y*/ ps.setDouble(12, geometry.getCentroid().getCoordinate().y);  
            //geometry.getRepresentativePoint()
        	/*AREA*/ ps.setDouble(13, geometry.getArea());
        	//Boundary ?
            /*EPSG4326_WKT*/ ps.setString(14, wktWriter.write(EPSG4326_geometry));
            base64Encoder.reset();
            base64Encoder.translate(wkbWriter.write(EPSG4326_geometry));
            /*EPSG4326_BASE64_WKB*/ ps.setString(15, new String(base64Encoder.getCharArray()));		
        	/*EPSG4326_MINX*/ ps.setDouble(16, EPSG4326_geometry.getEnvelopeInternal().getMinX());
        	/*EPSG4326_MAXX*/ ps.setDouble(17, EPSG4326_geometry.getEnvelopeInternal().getMaxX());
        	/*EPSG4326_MINY*/ ps.setDouble(18, EPSG4326_geometry.getEnvelopeInternal().getMinY());
        	/*EPSG4326_MAXY*/ ps.setDouble(19, EPSG4326_geometry.getEnvelopeInternal().getMaxY());
        	/*EPSG4326_CENTROID_X*/ ps.setDouble(20, EPSG4326_geometry.getCentroid().getCoordinate().x);   
        	/*EPSG4326_CENTROID_Y*/ ps.setDouble(21, EPSG4326_geometry.getCentroid().getCoordinate().y);  
            //EPSG4326_geometry.getRepresentativePoint()
        	/*EPSG4326_AREA*/ ps.setDouble(22, EPSG4326_geometry.getArea());
			//Boundary ?
        	//As discussed earlier, all instances of SFS geometry classes
        	//are topologically closed by definition.
        	//For empty Curves, isClosed is defined to have the value false.
        	/*IS_CLOSED*/ ps.setBoolean(23, !geometry.isEmpty());
			/*IS_SIMPLE*/ ps.setBoolean(24, geometry.isSimple());
			//Should always be true (the GML SAX parser makes a too severe check)
			/*IS_VALID*/ ps.setBoolean(25, geometry.isValid());
        	return (ps.executeUpdate() == 1);
    	} finally {
        	if (ps != null)
        		ps.close();
            //Let's help the garbage collector...
        	geometry = null;
    	}    	
    }
   
    protected boolean removeDocumentNode(DocumentImpl doc, NodeId nodeID, Connection conn) throws SQLException {   
        PreparedStatement ps = conn.prepareStatement(
        		"DELETE FROM " + GMLHSQLIndex.TABLE_NAME + " WHERE DOCUMENT_URI = ? AND NODE_ID = ?;"
        	); 
        ps.setString(1, doc.getURI().toString());	   
        ps.setString(2, nodeID.toString());
        try {	 
	        return (ps.executeUpdate() == 1);
    	} finally {
    		if (ps != null)
    			ps.close();
    	}       
    }
    
    protected int removeDocument(DocumentImpl doc, Connection conn) throws SQLException {    	
    	PreparedStatement ps = conn.prepareStatement(
    		"DELETE FROM " + GMLHSQLIndex.TABLE_NAME + " WHERE DOCUMENT_URI = ?;"
    	); 
        ps.setString(1, doc.getURI().toString());
        try {
	        return ps.executeUpdate();	 
    	} finally {
    		if (ps != null)
    			ps.close();
    	}       
    }    

    protected int removeCollection(Collection collection, Connection conn) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
    		"DELETE FROM " + GMLHSQLIndex.TABLE_NAME + " WHERE SUBSTRING(DOCUMENT_URI, 1, ?) = ?;"
    	); 
        ps.setInt(1, collection.getURI().toString().length());
        ps.setString(2, collection.getURI().toString());
        try {
        	return ps.executeUpdate();
    	} finally {
    		if (ps != null)
    			ps.close();
    	}
    }
    
    //Since an embedded HSQL has only one connection aailable (unless I'm totally dumb)
    //acquire and release the connection from the index, which is *the* connection's owner 
    
    protected Connection acquireConnection() {   
    	return index.acquireConnection(this.broker);
    }
    
    protected void releaseConnection(Connection conn) {   
    	index.releaseConnection(this.broker);
    } 

    
    protected boolean checkIndex(DBBroker broker, Connection conn) throws SQLException {
    	PreparedStatement ps = conn.prepareStatement(
	    		"SELECT * FROM " + GMLHSQLIndex.TABLE_NAME + ";"
	    	);
    	ResultSet rs = null;
    	try {
    		rs = ps.executeQuery();
	        while (rs.next()) {	        	
	        	base64Decoder.reset();
	        	base64Decoder.translate(rs.getString("BASE64_WKB"));
	        	Geometry original_geometry = wkbReader.read(base64Decoder.getByteArray());		        	
	            if (! original_geometry.equals(wktReader.read(rs.getString("WKT")))) {
	            	LOG.info("Inconsistent WKT : " + rs.getString("WKT"));
	    			return false;
	        	}		            	
	        	base64Decoder.reset();
	        	base64Decoder.translate(rs.getString("EPSG4326_BASE64_WKB"));
	        	Geometry EPSG4326_geometry = wkbReader.read(base64Decoder.getByteArray());		        	
	            if (!EPSG4326_geometry.equals(wktReader.read(rs.getString("EPSG4326_WKT")))) {
	            	LOG.info("Inconsistent WKT : " + rs.getString("EPSG4326_WKT"));
	    			return false;
	        	}
	            
	        	if (!original_geometry.getGeometryType().equals(rs.getString("GEOMETRY_TYPE"))) {
	        		LOG.info("Inconsistent geometry type: " + rs.getDouble("GEOMETRY_TYPE"));
	    			return false;
	        	}
	        	
	            if (original_geometry.getEnvelopeInternal().getMinX() != rs.getDouble("MINX")) {
	            	LOG.info("Inconsistent MinX: " + rs.getDouble("MINX"));
	    			return false;
	        	}
	            if (original_geometry.getEnvelopeInternal().getMaxX() != rs.getDouble("MAXX")) {
	            	LOG.info("Inconsistent MaxX: " + rs.getDouble("MAXX"));
	    			return false;
	        	}
	            if (original_geometry.getEnvelopeInternal().getMinY() != rs.getDouble("MINY")) {
	            	LOG.info("Inconsistent MinY: " + rs.getDouble("MINY"));
	    			return false;
	        	}
	            if (original_geometry.getEnvelopeInternal().getMaxY() != rs.getDouble("MAXY")) {
	            	LOG.info("Inconsistent MaxY: " + rs.getDouble("MAXY"));
	    			return false;
	        	}
	            if (original_geometry.getCentroid().getCoordinate().x != rs.getDouble("CENTROID_X")) {
	            	LOG.info("Inconsistent X for centroid : " + rs.getDouble("CENTROID_X"));
	    			return false;
	        	}
	            if (original_geometry.getCentroid().getCoordinate().y != rs.getDouble("CENTROID_Y")) {
	            	LOG.info("Inconsistent Y for centroid : " + rs.getDouble("CENTROID_Y"));
	    			return false;
	        	}
	            if (original_geometry.getArea() != rs.getDouble("AREA")) {
	            	LOG.info("Inconsistent area: " + rs.getDouble("AREA"));
	    			return false;
	            }	        	
	        	
	        	String srsName = rs.getString("SRS_NAME");
	            MathTransform mathTransform = getTransform(srsName, "EPSG:4326");
	            if (mathTransform == null) {
	        		LOG.error("Unable to get a transformation from '" + srsName + "' to 'EPSG:4326'");
	        		return false;              	
	            }
	            getCoordinateTransformer().setMathTransform(mathTransform);
	            try {
	            	if (!getCoordinateTransformer().transform(original_geometry).equals(EPSG4326_geometry)) {
		        		LOG.info("Transformed original geometry inconsistent with stored tranformed one");
	            		return false;
	            	}
	            } catch (TransformException e) {
	        		LOG.error(e);
	        		return false;
	            }
	
	            if (EPSG4326_geometry.getEnvelopeInternal().getMinX() != rs.getDouble("EPSG4326_MINX")) {
	            	LOG.info("Inconsistent MinX: " + rs.getDouble("EPSG4326_MINX"));
	    			return false;
	        	}
	            if (EPSG4326_geometry.getEnvelopeInternal().getMaxX() != rs.getDouble("EPSG4326_MAXX")) {
	            	LOG.info("Inconsistent MaxX: " + rs.getDouble("EPSG4326_MAXX"));
	    			return false;
	        	}
	            if (EPSG4326_geometry.getEnvelopeInternal().getMinY() != rs.getDouble("EPSG4326_MINY")) {
	            	LOG.info("Inconsistent MinY: " + rs.getDouble("EPSG4326_MINY"));
	    			return false;
	        	}
	            if (EPSG4326_geometry.getEnvelopeInternal().getMaxY() != rs.getDouble("EPSG4326_MAXY")) {
	            	LOG.info("Inconsistent MaxY: " + rs.getDouble("EPSG4326_MAXY"));
	    			return false;
	        	}
	            if (EPSG4326_geometry.getCentroid().getCoordinate().x != rs.getDouble("EPSG4326_CENTROID_X")) {
	            	LOG.info("Inconsistent X for centroid : " + rs.getDouble("EPSG4326_CENTROID_X"));
	    			return false;
	        	}
	            if (EPSG4326_geometry.getCentroid().getCoordinate().y != rs.getDouble("EPSG4326_CENTROID_Y")) {
	            	LOG.info("Inconsistent Y for centroid : " + rs.getDouble("EPSG4326_CENTROID_Y"));
	    			return false;
	        	}
	            if (EPSG4326_geometry.getArea() != rs.getDouble("EPSG4326_AREA")) {
	            	LOG.info("Inconsistent area: " + rs.getDouble("EPSG4326_AREA"));
	    			return false;
	            }
	            
	            if (original_geometry.isEmpty() == rs.getBoolean("IS_CLOSED")) {
	            	LOG.info("Inconsistent area: " + rs.getBoolean("IS_CLOSED"));
	    			return false;
	            }
	            if (original_geometry.isSimple() != rs.getBoolean("IS_SIMPLE")) {
	            	LOG.info("Inconsistent area: " + rs.getBoolean("IS_SIMPLE"));
	    			return false;
	            }
	            if (original_geometry.isValid() != rs.getBoolean("IS_VALID")) {
	            	LOG.info("Inconsistent area: " + rs.getBoolean("IS_VALID"));
	    			return false;
	            }
	            
	            Document doc = broker.getXMLResource(XmldbURI.create(rs.getString("DOCUMENT_URI")));
	    		NodeId nodeId = new DLN(rs.getString("NODE_ID")); 
	    			           
	        	StoredNode node = broker.objectWith(new NodeProxy((DocumentImpl)doc, nodeId));
	        	if (!GMLHSQLIndexWorker.GML_NS.equals(node.getNamespaceURI())) {
	        		LOG.info("GML indexed node (" + node.getNodeId()+ ") is in the '" + 
	        				node.getNamespaceURI() + "' namespace. '" + 
	        				GMLHSQLIndexWorker.GML_NS + "' was expected !");
	        		return false;
	        	}
	        	if (!original_geometry.getGeometryType().equals(node.getLocalName())) {
	        		if ("Box".equals(node.getLocalName()) && "Polygon".equals(original_geometry.getGeometryType())) {
	        			LOG.debug("GML indexed node (" + node.getNodeId() + ") is a gml:Box indexed as a polygon");
	        		} else {
	        			LOG.info("GML indexed node (" + node.getNodeId() + ") has '" + 
	        					node.getLocalName() + "' as its local name. '" + 
	        					original_geometry.getGeometryType() + "' was expected !");
	        			return false;
	        		}
	        	}
	        	
	    		LOG.info(node);	        		
	        }
	        return true;
	        
        } catch (ParseException e) {
        	LOG.error(e);
        	return false;
        } catch (PermissionDeniedException e) {
        	LOG.error(e);
        	return false;
		} finally {   
			if (rs != null)
				rs.close();
			if (ps != null)
				ps.close();	
	    }
    }
    
    protected Map getGeometriesForDocument(DocumentImpl doc, Connection conn) throws SQLException {       	
        PreparedStatement ps = conn.prepareStatement(
    		"SELECT EPSG4326_BASE64_WKB, EPSG4326_WKT FROM " + GMLHSQLIndex.TABLE_NAME + " WHERE DOCUMENT_URI = ?;"
    	); 
        ps.setString(1, doc.getURI().toString());
        //TODO : better a List with a end of process string transformation ?
    	Map map = null;
        ResultSet rs = null;
        try {	 
	        rs = ps.executeQuery();
	        map = new TreeMap();
	        while (rs.next()) {
	        	base64Decoder.reset();
	        	base64Decoder.translate(rs.getString("EPSG4326_BASE64_WKB"));
	        	Geometry geometry = wkbReader.read(base64Decoder.getByteArray());
	        	map.put(geometry, rs.getString("EPSG4326_WKT"));
	        }
	        return map;
        } catch (ParseException e) {
        	LOG.error(e);
        	return null;
    	} finally {   
    		if (rs != null)
    			rs.close();
    		if (ps != null)
    			ps.close();
    	}
    } 
    
    protected Geometry getGeometryForNode(DBBroker broker, NodeProxy p, Connection conn) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
    		"SELECT EPSG4326_BASE64_WKB" +
    		" FROM " + GMLHSQLIndex.TABLE_NAME + 
    		" WHERE DOCUMENT_URI = ? AND NODE_ID = ?;"
    	);
        ps.setString(1, p.getDocument().getURI().toString());
    	ps.setString(2, p.getNodeId().toString());   
    	ResultSet rs = null;    	
    	try {
    		rs = ps.executeQuery();
    		if (!rs.next())
    			//Nothing returned
    			return null;    		
			base64Decoder.reset();
        	base64Decoder.translate(rs.getString("EPSG4326_BASE64_WKB"));
        	Geometry geometry = wkbReader.read(base64Decoder.getByteArray());        			
        	if (rs.next()) {   	
    			//Should be impossible    		
    			throw new SQLException("More than one geometry for node " + p);
    		}
        	return geometry;    
        } catch (ParseException e) {
        	LOG.error(e); 
        	return null;
    	} finally {   
    		if (rs != null)
    			rs.close();
    		if (ps != null)
    			ps.close();
        }
    }    
    
    protected AtomicValue getGeometricPropertyForNode(DBBroker broker, NodeProxy p, Connection conn, String propertyName) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
    		"SELECT " + propertyName + 
    		" FROM " + GMLHSQLIndex.TABLE_NAME + 
    		" WHERE DOCUMENT_URI = ? AND NODE_ID = ?;"
    	);
        ps.setString(1, p.getDocument().getURI().toString());
    	ps.setString(2, p.getNodeId().toString());   
    	ResultSet rs = null;    	
    	try {
    		rs = ps.executeQuery();
    		if (!rs.next())
    			//Nothing returned
    			return null;
    		AtomicValue result = null;
    		if (rs.getMetaData().getColumnClassName(1).equals(Boolean.class.getName())) {
    			result = new BooleanValue(rs.getBoolean(1));
    		} else if (rs.getMetaData().getColumnClassName(1).equals(Double.class.getName())) {
    			result = new DoubleValue(rs.getDouble(1));
    		} else if (rs.getMetaData().getColumnClassName(1).equals(String.class.getName())) {
    			result = new StringValue(rs.getString(1));
    		} else 
    			throw new SQLException("Uniable to make an atomic value from '" + rs.getMetaData().getColumnClassName(1) + "'");		
        	if (rs.next()) {   	
    			//Should be impossible    		
    			throw new SQLException("More than one geometry for node " + p);
    		}
        	return result;    
    	} finally {   
    		if (rs != null)
    			rs.close();
    		if (ps != null)
    			ps.close();
        }
    }    

    protected NodeSet search(DBBroker broker, NodeSet contextSet, Geometry EPSG4326_geometry, int spatialOp, Connection conn) throws SQLException {
    	String extraSelection = null;
    	String bboxConstraint = null;    	
        switch (spatialOp) {
        	//BBoxes are equal
        	case SpatialOperator.EQUALS:
        		bboxConstraint = "(EPSG4326_MINX = ? AND EPSG4326_MAXX = ?)" +
    				" AND (EPSG4326_MINY = ? AND EPSG4326_MAXY = ?)";
        		break;
        	case SpatialOperator.DISJOINT:
        		//Nothing much we can do with the BBox at this stage
        		extraSelection = ", EPSG4326_MINX, EPSG4326_MAXX, EPSG4326_MINY, EPSG4326_MAXY";
        		break;
       		//BBoxes intersect themselves
        	case SpatialOperator.INTERSECTS:        		
        	case SpatialOperator.TOUCHES:        		   		
        	case SpatialOperator.CROSSES:        		      		
        	case SpatialOperator.OVERLAPS: 
        		bboxConstraint = "(EPSG4326_MAXX >= ? AND EPSG4326_MINX <= ?)" +
				" AND (EPSG4326_MAXY >= ? AND EPSG4326_MINY <= ?)";
        		break;
        	//BBoxe is fully within
        	case SpatialOperator.WITHIN:   
        		bboxConstraint = "(EPSG4326_MINX >= ? AND EPSG4326_MAXX <= ?)" +
				" AND (EPSG4326_MINY >= ? AND EPSG4326_MAXY <= ?)";
        		break;        		
        	case SpatialOperator.CONTAINS: 
        		bboxConstraint = "(EPSG4326_MINX <= ? AND EPSG4326_MAXX >= ?)" +
				" AND (EPSG4326_MINY <= ? AND EPSG4326_MAXY >= ?)";
        		break;             		
        	default:
        		throw new IllegalArgumentException("Unsupported spatial operator:" + spatialOp);
        }
        PreparedStatement ps = conn.prepareStatement(
    		"SELECT EPSG4326_BASE64_WKB, DOCUMENT_URI, NODE_ID" + (extraSelection == null ? "" : extraSelection) +
    		" FROM " + GMLHSQLIndex.TABLE_NAME + 
    		(bboxConstraint == null ? "" : " WHERE " + bboxConstraint) + ";"
    	);
        if (bboxConstraint != null) {
	        ps.setDouble(1, EPSG4326_geometry.getEnvelopeInternal().getMinX());
	    	ps.setDouble(2, EPSG4326_geometry.getEnvelopeInternal().getMaxX());
	    	ps.setDouble(3, EPSG4326_geometry.getEnvelopeInternal().getMinY());
	    	ps.setDouble(4, EPSG4326_geometry.getEnvelopeInternal().getMaxY());
        }
    	ResultSet rs = null;
    	NodeSet result = null;
    	try { 
    		int disjointPostFiltered = 0;
    		rs = ps.executeQuery();
    		result = new ExtArrayNodeSet(); //new ExtArrayNodeSet(docs.getLength(), 250)
    		while (rs.next()) {
    			NodeProxy p = null;	    		
        		XmldbURI documentURI = XmldbURI.create(rs.getString("DOCUMENT_URI"));
        		try {
        			Document doc = broker.getXMLResource(documentURI);
	        		NodeId nodeId = new DLN(rs.getString("NODE_ID")); 
	        		p = new NodeProxy((DocumentImpl)doc, nodeId);		        		
        		} catch (PermissionDeniedException e) {
        			LOG.debug(e);
        		}        		
        		//Node is in the context : check if it is accurate
        		//contextSet.contains(p) would have made more sense but there is a problem with
        		//VirtualNodeSet when on the DESCENDANT_OR_SELF axis
        		if (contextSet == null || contextSet.get(p) != null) {	
        			
		        	boolean geometryMatches = false;
		        	if (spatialOp == SpatialOperator.DISJOINT) {
		        		//Obviously disjoint
		        		if (rs.getDouble("EPSG4326_MAXX") < EPSG4326_geometry.getEnvelopeInternal().getMinX() ||	        			
			        		rs.getDouble("EPSG4326_MINX") > EPSG4326_geometry.getEnvelopeInternal().getMaxX() ||	        			
			        		rs.getDouble("EPSG4326_MAXY") < EPSG4326_geometry.getEnvelopeInternal().getMinY() ||	        			
			        		rs.getDouble("EPSG4326_MINY") > EPSG4326_geometry.getEnvelopeInternal().getMaxY()) {
			        			geometryMatches = true;
					        		disjointPostFiltered++;	
		        		}
		        	}
		        	//Check the geometry
		        	if (!geometryMatches) {	
		        		try {			        	
			    			base64Decoder.reset();
				        	base64Decoder.translate(rs.getString("EPSG4326_BASE64_WKB"));
				        	Geometry geometry = wkbReader.read(base64Decoder.getByteArray());			        	
				        	switch (spatialOp) {
				        	case SpatialOperator.EQUALS:	        		        		
				        		geometryMatches = geometry.equals(EPSG4326_geometry);
				        		break;
				        	case SpatialOperator.DISJOINT:        		
				        		geometryMatches = geometry.disjoint(EPSG4326_geometry);
				        		break;	        		
				        	case SpatialOperator.INTERSECTS:        		
				        		geometryMatches = geometry.intersects(EPSG4326_geometry);
				        		break;	        		
				        	case SpatialOperator.TOUCHES:
					        	geometryMatches = geometry.touches(EPSG4326_geometry);
				        		break;	        		
				        	case SpatialOperator.CROSSES:
					        	geometryMatches = geometry.crosses(EPSG4326_geometry);
				        		break;	        		
				        	case SpatialOperator.WITHIN:        		
				        		geometryMatches = geometry.within(EPSG4326_geometry);
				        		break;	        		
				        	case SpatialOperator.CONTAINS:	        		
				        		geometryMatches = geometry.contains(EPSG4326_geometry);
				        		break;	        		
				        	case SpatialOperator.OVERLAPS:	        		
				        		geometryMatches = geometry.overlaps(EPSG4326_geometry);
				        		break;	        		
				        	}
		    	        } catch (ParseException e) {
		    	        	LOG.error(e); 
		    	        	return NodeSet.EMPTY_SET;
		    	        }
		        	}
		        	if (geometryMatches)        	
			        	result.add(p);
        		}
    		}
    		if (LOG.isDebugEnabled()) {
    			LOG.debug(rs.getRow() + " eligible geometries, " + result.getItemCount() + "selected" +
    				(spatialOp == SpatialOperator.DISJOINT ? "(" + disjointPostFiltered + " post filtered)" : ""));
    		}
    		return result;	    	
    	} finally {   
    		if (rs != null)
    			rs.close();
    		if (ps != null)
    			ps.close();	    		
    	}
    } 
    
    protected NodeSet isIndexed(DBBroker broker, Geometry EPSG4326_geometry, Connection conn) throws SQLException {    	
    	String bboxConstraint = "(EPSG4326_MINX = ? AND EPSG4326_MAXX = ?)" +
    				" AND (EPSG4326_MINY = ? AND EPSG4326_MAXY = ?)"; 
    	NodeSet result = null;
        PreparedStatement ps = conn.prepareStatement(
    		"SELECT EPSG4326_BASE64_WKB, DOCUMENT_URI, NODE_ID" +
    		" FROM " + GMLHSQLIndex.TABLE_NAME + 
    		" WHERE " + bboxConstraint + ";"
    	);
        ps.setDouble(1, EPSG4326_geometry.getEnvelopeInternal().getMinX());
    	ps.setDouble(2, EPSG4326_geometry.getEnvelopeInternal().getMaxX());
    	ps.setDouble(3, EPSG4326_geometry.getEnvelopeInternal().getMinY());
    	ps.setDouble(4, EPSG4326_geometry.getEnvelopeInternal().getMaxY());      
    	ResultSet rs = null;
    	result = new ExtArrayNodeSet(); //new ExtArrayNodeSet(docs.getLength(), 250)
    	try {
    		rs = ps.executeQuery();
    		while (rs.next()) {    			
    			base64Decoder.reset();
	        	base64Decoder.translate(rs.getString("EPSG4326_BASE64_WKB"));
	        	Geometry geometry = wkbReader.read(base64Decoder.getByteArray());	
	        	boolean geometryMatches = geometry.equals(EPSG4326_geometry);	        	
	        	if (geometryMatches) {	        	
	        		XmldbURI documentURI = XmldbURI.create(rs.getString("DOCUMENT_URI"));
	        		try {
	        			Document doc = broker.getXMLResource(documentURI);
		        		NodeId nodeId = new DLN(rs.getString("NODE_ID")); 
		        		NodeProxy p = new NodeProxy((DocumentImpl)doc, nodeId);
		        		result.add(p);
	        		} catch (PermissionDeniedException e) {
	        			LOG.debug(e);
	        		}
	        	}
    		}
    		if (LOG.isDebugEnabled()) {
    			LOG.debug(rs.getRow() + " eligible geometries, " + result.getItemCount() + "selected");
    		}
    		return result;
        } catch (ParseException e) {
        	LOG.error(e);
        	return null;
    	} finally {   
    		if (rs != null)
    			rs.close();
    		if (ps != null)
    			ps.close();
    	}    	
    }
 
}