package org.basex.index.spatial;

import java.io.*;

import javax.xml.parsers.*;

import org.basex.core.*;
import org.basex.data.*;
import org.basex.index.*;
import org.basex.io.out.*;
import org.basex.io.serial.*;
import org.basex.query.value.node.*;
import org.basex.util.*;
import org.xml.sax.*;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.index.strtree.*;
import com.vividsolutions.jts.io.gml2.*;

/**
 * This class contains common methods for spatial index builders.
 *
 * @author Masoumeh Seydi
 *
 */
public class SpatialBuilder extends IndexBuilder {

  /** STRTree. */
  STRtree str;

  /**
   * Constructor.
   * @param d Data
   */
  public SpatialBuilder(final Data d) {
    super(d, d.meta.prop.num(Prop.SPINDEXSPLITSIZE));
  }

  @Override
  public Index build() throws IOException {
    byte[] GML_URI = Token.token("http://www.opengis.net/gml");
    byte[] POINT = Token.token("Point");
    byte[] LINESTRING = Token.token("LineString");
    byte[] LINEARRING = Token.token("LinearRing");
    byte[] POLYGON = Token.token("Polygon");
//    byte[] MULTIPOINT = Token.token("MultiPoint");
//    byte[] MULTILINESTRING = Token.token("MultiLineString");
//    byte[] MULTIPOLYGON = Token.token("MultiPolygon");

    ArrayOutput ao = new ArrayOutput();
    Serializer ser = Serializer.get(ao);
    GMLReader gmlr = new GMLReader();
    GeometryFactory geometryFactory = new GeometryFactory();

    str = new STRtree();
    for(int pre = 0; pre < data.meta.size; pre++) {
      final int kind = data.kind(pre);
      if(kind != Data.ELEM) continue;

      byte[] name = Token.local(data.name(pre, kind));
      byte[] uri = data.nspaces.uri(data.uri(pre, kind));
      if(!Token.eq(uri, GML_URI)) continue;

      if(!Token.eq(name, POINT, LINESTRING, LINEARRING, POLYGON)) continue;

      ao.reset();
      ser.serialize(new DBNode(data, pre));

      try {
        Geometry geom = gmlr.read(ao.toString(), geometryFactory);
        str.insert(geom.getEnvelopeInternal(), pre);
       // if (!Token.eq(name, MULTIPOINT, MULTILINESTRING, MULTIPOLYGON))
          pre += data.size(pre, kind) - 1;
      } catch(SAXException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch(ParserConfigurationException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    str.build();
    ObjectOutputStream oos = new ObjectOutputStream(
        new BufferedOutputStream(
            new FileOutputStream(data.meta.dbfile("STRTreeIndex").file())));
    oos.writeObject(str);
    oos.close();
    return new SpatialIndex(data);
  }
}
