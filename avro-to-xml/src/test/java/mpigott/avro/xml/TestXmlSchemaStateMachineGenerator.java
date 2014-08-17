/**
 * Copyright 2014 Mike Pigott
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mpigott.avro.xml;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;

import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.junit.Test;

/**
 * Tests {@link XmlSchemaStateMachineGenerator}.
 *
 * @author  Mike Pigott
 */
public class TestXmlSchemaStateMachineGenerator {

  @Test
  public void test() throws Exception {
    XmlSchemaCollection collection = null;
    FileReader fileReader = null;

    File file =
        UtilsForTests.buildFile("src", "test", "resources", "test_schema.xsd");

    try {
      fileReader = new FileReader(file);

      collection = new XmlSchemaCollection();
      collection.setSchemaResolver(new XmlSchemaMultiBaseUriResolver());
      collection.read(new StreamSource(fileReader, file.getAbsolutePath()));

    } finally {
      if (fileReader != null) {
        try {
          fileReader.close();
        } catch (IOException ioe) {
          ioe.printStackTrace();
        }
      }
    }

    final XmlSchemaStateMachineGenerator smg =
        new XmlSchemaStateMachineGenerator(); 

    final XmlSchemaElement elem = getElementOf(collection, "record"); // root
    final XmlSchemaWalker walker = new XmlSchemaWalker(collection, smg);
    walker.walk(elem);

    assertNotNull( smg.getStartNode() );

    // 3. To replace with something more sophisticated: draw a graph of the state machine.
    StringTemplateGroup templates = null;
    FileReader fr = null;
    try {
      fr = new FileReader("C:\\Users\\Mike Pigott\\Google Drive\\workspace\\edgar_xbrl\\src\\main\\resources\\DOT.stg");
      templates = new StringTemplateGroup(fr);
    } finally {
      try {
        if (fr != null) {
          fr.close();
        }
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }

    ArrayList<StringTemplate> nodes = new ArrayList<StringTemplate>();
    ArrayList<StringTemplate> edges = new ArrayList<StringTemplate>();

    nextNode(smg.getStartNode(), 0, nodes, edges, templates, new HashMap<QName, Integer>());

    StringTemplate fileSt = templates.getInstanceOf("file");
    fileSt.setAttribute("gname", "state_machine");
    fileSt.setAttribute("nodes", nodes);
    fileSt.setAttribute("edges", edges);

    System.out.println( fileSt.toString() );

  }

  private int nextNode(XmlSchemaStateMachineNode currNode, int nodeNum, ArrayList<StringTemplate> nodes, ArrayList<StringTemplate> edges, StringTemplateGroup templates, Map<QName, Integer> nodeNums) {
    int nextNum = nodeNum + 1;

    nodes.add( getNodeSt(templates, "node" + nodeNum, currNode.toString()) );

    if ( currNode.getNodeType().equals(XmlSchemaStateMachineNode.Type.ELEMENT) ) {
      nodeNums.put(currNode.getElement().getQName(), nodeNum);
    }

    if (currNode.getPossibleNextStates() != null) {
      for (XmlSchemaStateMachineNode nextNode : currNode.getPossibleNextStates()) {
        int nextNodeNum = nextNum;

        if (nextNode.getNodeType().equals(XmlSchemaStateMachineNode.Type.ELEMENT)
            && nodeNums.containsKey( nextNode.getElement().getQName() )) {
          nextNodeNum = nodeNums.get( nextNode.getElement().getQName() );
        } else {
          nextNum = nextNode(nextNode, nextNum, nodes, edges, templates, nodeNums);
        }
        edges.add( getEdgeSt(templates, "node" + nodeNum, "node" + nextNodeNum) );
      }
    }

    return nextNum;
  }

  private StringTemplate getEdgeSt(StringTemplateGroup templates, String from, String to) {
    StringTemplate edgeSt = templates.getInstanceOf("edge");
    edgeSt.setAttribute("from", from);
    edgeSt.setAttribute("to", to);
    return edgeSt;
  }

  private StringTemplate getNodeSt(StringTemplateGroup templates, String name, String text) {
    StringTemplate tmpl = templates.getInstanceOf("node");
    tmpl.setAttribute("name", name);
    tmpl.setAttribute("text", text.replace('\"', '\''));
    return tmpl;
  }

  private static XmlSchemaElement getElementOf(XmlSchemaCollection collection, String name) {
    XmlSchemaElement elem = null;
    for (XmlSchema schema : collection.getXmlSchemas()) {
      elem = schema.getElementByName(name);
      if (elem != null) {
        break;
      }
    }
    return elem;
  }
}
